/*
 * Copyright the bpmn-backlink authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.jakobarndt.bpmnbacklink.core.write;

import net.jakobarndt.bpmnbacklink.core.kotlin.KotlinDeclaration;
import net.jakobarndt.bpmnbacklink.core.kotlin.KotlinDeclarations;
import net.jakobarndt.bpmnbacklink.core.kotlin.KotlinSanitizer;
import net.jakobarndt.bpmnbacklink.core.kotlin.SanitizedSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads and rewrites the {@code @CalledFrom} annotation of a Kotlin delegate
 * type by editing the original text through the offsets of the
 * {@linkplain KotlinSanitizer sanitized} source.
 *
 * <p>Only the annotation and its import are touched; every other byte of the
 * file, including its line endings, indentation, KDoc and comments, is copied
 * through unchanged. An existing annotation is replaced exactly where it
 * stands, so its indentation survives; a new one is inserted above the first
 * modifier or annotation of the declaration, hence below the KDoc.
 *
 * <p>The annotation is written in Kotlin vararg form,
 * {@code @CalledFrom("a.bpmn", "b.bpmn")}, and read in both accepted forms, the
 * vararg form and the array form {@code @CalledFrom(["a.bpmn"])}, with or
 * without a package qualifier, so a check run does not report drift on source
 * that was written by hand.
 *
 * <p>The import is inserted at its alphabetical position among the existing
 * imports. If the file has no import at all, the statement goes below the
 * package declaration, or, if there is none either, directly above the
 * declaration being annotated.
 */
public final class KotlinAnnotationWriter implements AnnotationEditor {

    private static final String KOTLIN_SUFFIX = ".kt";

    private static final String IMPORT_KEYWORD = "import ";

    private static final String PACKAGE_KEYWORD = "package ";

    private static final String STAR_IMPORT = "net.jakobarndt.bpmnbacklink.annotation.*";

    private static final String LINE_FEED = "\n";

    private static final String CARRIAGE_RETURN_LINE_FEED = "\r\n";

    /** Blanks left behind on a line after an annotation has been cut out of it. */
    private static final Pattern LEADING_BLANKS = Pattern.compile("^[ \\t]+");

    /** Kotlin escape sequence for the backspace character. */
    private static final String BACKSPACE_ESCAPE = "\\b";

    /**
     * Matches any remaining use of the annotation, with or without a qualifier.
     * The trailing word boundary keeps a longer name from matching.
     */
    private static final Pattern REMAINING_USE =
        Pattern.compile("@(?:[\\p{L}_][\\p{L}\\p{N}_]*\\.)*" + ANNOTATION_SIMPLE_NAME + "\\b");

    @Override
    public boolean supports(Path sourceFile) {
        return sourceFile.getFileName().toString().endsWith(KOTLIN_SUFFIX);
    }

    @Override
    public List<String> readCurrentValues(Path sourceFile, String typeName) throws IOException {
        SanitizedSource source = KotlinSanitizer.sanitize(read(sourceFile));
        return declaration(source, typeName)
            .flatMap(type -> type.annotation(ANNOTATION_SIMPLE_NAME))
            .map(annotation -> source.literalValuesIn(annotation.argumentsStart(), annotation.argumentsEnd()))
            .orElseGet(ArrayList::new);
    }

    @Override
    public void write(Path sourceFile, String typeName, List<String> expected) throws IOException {
        String original = read(sourceFile);
        SanitizedSource source = KotlinSanitizer.sanitize(original);
        KotlinDeclaration type = declaration(source, typeName)
            .orElseThrow(() -> new IllegalStateException("No type " + typeName + " in " + sourceFile));

        String separator = lineSeparatorOf(original);
        Optional<KotlinDeclaration.AnnotationRef> current = type.annotation(ANNOTATION_SIMPLE_NAME);

        String updated;
        if (expected.isEmpty()) {
            updated = removeImportIfUnused(current
                .map(annotation -> removeAnnotation(original, annotation))
                .orElse(original));
        } else {
            String rendered = renderAnnotation(expected);
            updated = ensureImport(current
                .map(annotation -> original.substring(0, annotation.start())
                    + rendered + original.substring(annotation.end()))
                .orElseGet(() -> insertAnnotation(original, type.headerStart(), rendered, separator)),
                separator);
        }
        Files.writeString(sourceFile, updated, StandardCharsets.UTF_8);
    }

    private static String lineSeparatorOf(String text) {
        if (text.contains(CARRIAGE_RETURN_LINE_FEED)) {
            return CARRIAGE_RETURN_LINE_FEED;
        }
        return LINE_FEED;
    }

    private static String read(Path sourceFile) throws IOException {
        return Files.readString(sourceFile, StandardCharsets.UTF_8);
    }

    private static Optional<KotlinDeclaration> declaration(SanitizedSource source, String typeName) {
        return KotlinDeclarations.parse(source).stream()
            .filter(type -> type.name().equals(typeName))
            .findFirst();
    }

    // -----------------------------------------------------------------
    // Annotation editing.
    // -----------------------------------------------------------------

    private static String renderAnnotation(List<String> values) {
        return values.stream()
            .map(KotlinAnnotationWriter::literal)
            .collect(Collectors.joining(", ", "@" + ANNOTATION_SIMPLE_NAME + "(", ")"));
    }

    private static String literal(String value) {
        StringBuilder rendered = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '\\' -> rendered.append("\\\\");
                case '"' -> rendered.append("\\\"");
                case '$' -> rendered.append("\\$");
                case '\t' -> rendered.append("\\t");
                case '\b' -> rendered.append(BACKSPACE_ESCAPE);
                case '\n' -> rendered.append("\\n");
                case '\r' -> rendered.append("\\r");
                default -> rendered.append(current);
            }
        }
        return rendered.append('"').toString();
    }

    private static String insertAnnotation(String text, int at, String rendered, String separator) {
        int lineStart = startOfLine(text, at);
        if (isBlank(text, lineStart, at)) {
            String indent = text.substring(lineStart, at);
            return text.substring(0, at) + rendered + separator + indent + text.substring(at);
        }
        return text.substring(0, at) + rendered + " " + text.substring(at);
    }

    private static String removeAnnotation(String text, KotlinDeclaration.AnnotationRef annotation) {
        int lineStart = startOfLine(text, annotation.start());
        int lineEnd = endOfLine(text, annotation.end());
        if (isBlank(text, lineStart, annotation.start()) && isBlank(text, annotation.end(), lineEnd)) {
            return text.substring(0, lineStart) + text.substring(lineEnd);
        }
        String tail = text.substring(annotation.end());
        return text.substring(0, annotation.start()) + LEADING_BLANKS.matcher(tail).replaceFirst("");
    }

    // -----------------------------------------------------------------
    // Import handling.
    // -----------------------------------------------------------------

    private static String ensureImport(String text, String separator) {
        List<SourceLine> imports = importLines(text);
        if (imports.stream().anyMatch(KotlinAnnotationWriter::coversAnnotation)) {
            return text;
        }
        String statement = IMPORT_KEYWORD + ANNOTATION_FQN;
        for (SourceLine line : imports) {
            if (importPath(line).compareTo(ANNOTATION_FQN) > 0) {
                return insertAt(text, line.start(), statement + separator);
            }
        }
        if (!imports.isEmpty()) {
            return insertAt(text, imports.get(imports.size() - 1).end(), statement + separator);
        }
        int packageEnd = packageLineEnd(text);
        if (packageEnd >= 0) {
            return insertAt(text, packageEnd, separator + statement + separator);
        }
        return insertAt(text, firstCodeLineStart(text), statement + separator + separator);
    }

    private static String removeImportIfUnused(String text) {
        SanitizedSource source = KotlinSanitizer.sanitize(text);
        if (REMAINING_USE.matcher(source.text()).find()) {
            return text;
        }
        return importLines(text).stream()
            .filter(line -> importPath(line).equals(ANNOTATION_FQN))
            .findFirst()
            .map(line -> text.substring(0, line.start()) + text.substring(line.end()))
            .orElse(text);
    }

    private static boolean coversAnnotation(SourceLine line) {
        String path = importPath(line);
        return path.equals(ANNOTATION_FQN) || path.equals(STAR_IMPORT);
    }

    private static String importPath(SourceLine line) {
        return line.content().substring(IMPORT_KEYWORD.length()).trim().split("\\s+", 2)[0];
    }

    private static List<SourceLine> importLines(String text) {
        return lines(text).stream()
            .filter(line -> line.content().startsWith(IMPORT_KEYWORD))
            .toList();
    }

    private static int packageLineEnd(String text) {
        return lines(text).stream()
            .filter(line -> line.content().startsWith(PACKAGE_KEYWORD))
            .findFirst()
            .map(SourceLine::end)
            .orElse(-1);
    }

    /**
     * A physical line, addressed on the original text but described by its
     * sanitized content, so a commented-out import is never mistaken for one.
     *
     * @param start the offset of the first character of the line
     * @param end the offset after the line separator
     * @param content the trimmed sanitized content of the line
     */
    private record SourceLine(int start, int end, String content) {
    }

    private static List<SourceLine> lines(String text) {
        String sanitized = KotlinSanitizer.sanitize(text).text();
        List<SourceLine> lines = new ArrayList<>();
        int index = 0;
        while (index < sanitized.length()) {
            int end = endOfLine(sanitized, index);
            lines.add(new SourceLine(index, end, sanitized.substring(index, end).trim()));
            index = end;
        }
        return lines;
    }

    // -----------------------------------------------------------------
    // Text helpers.
    // -----------------------------------------------------------------

    private static String insertAt(String text, int at, String inserted) {
        return text.substring(0, at) + inserted + text.substring(at);
    }

    private static int endOfLine(String text, int position) {
        int newline = text.indexOf('\n', position);
        if (newline < 0) {
            return text.length();
        }
        return newline + 1;
    }

    private static int startOfLine(String text, int position) {
        return text.lastIndexOf('\n', position - 1) + 1;
    }

    private static int firstCodeLineStart(String text) {
        String sanitized = KotlinSanitizer.sanitize(text).text();
        return startOfLine(sanitized, sanitized.length() - sanitized.stripLeading().length());
    }

    private static boolean isBlank(String text, int from, int to) {
        for (int index = from; index < to; index++) {
            if (!Character.isWhitespace(text.charAt(index))) {
                return false;
            }
        }
        return true;
    }
}
