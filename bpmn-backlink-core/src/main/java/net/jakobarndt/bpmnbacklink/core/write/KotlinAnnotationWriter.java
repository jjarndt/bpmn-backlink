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
import net.jakobarndt.bpmnbacklink.core.util.Names;

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
 * <p>The type is addressed by its simple name across the whole file, so a
 * nested type and every top-level type are reachable. If several declarations
 * of a file share the simple name, the first one in source order is used; any
 * further declaration of that name is then left untouched and reported as
 * already correct, matching the Java editor.
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

    /** Separates the imported path from the name an aliased import binds. */
    private static final String ALIAS_KEYWORD = " as ";

    private static final String STAR_IMPORT = "net.jakobarndt.bpmnbacklink.annotation.*";

    private static final String LINE_FEED = "\n";

    private static final String CARRIAGE_RETURN_LINE_FEED = "\r\n";

    /** Blanks left behind on a line after an annotation has been cut out of it. */
    private static final Pattern LEADING_BLANKS = Pattern.compile("^[ \\t]+");

    /** Blanks in front of an annotation that ended its line. */
    private static final Pattern TRAILING_BLANKS = Pattern.compile("[ \\t]+$");

    /** Kotlin escape sequence for the backspace character. */
    private static final String BACKSPACE_ESCAPE = "\\b";

    /**
     * Matches any remaining use of the annotation name outside the import
     * block, be it an annotation or a plain type reference such as
     * {@code CalledFrom::class}. The word boundaries keep a longer name from
     * matching. Erring towards keeping the import costs an unused import at
     * worst, while dropping a needed one breaks the file.
     */
    private static final Pattern REMAINING_USE =
        Pattern.compile("\\b" + ANNOTATION_SIMPLE_NAME + "\\b");

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

    /**
     * Takes the separator from the first line break of the file. Scanning for
     * any {@code \r\n} instead would let a raw string that merely carries
     * Windows line endings as data decide how the file itself is written.
     */
    private static String lineSeparatorOf(String text) {
        if (text.startsWith(CARRIAGE_RETURN_LINE_FEED, text.indexOf('\n') - 1)) {
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
        boolean endsItsLine = isBlank(text, annotation.end(), lineEnd);
        if (isBlank(text, lineStart, annotation.start()) && endsItsLine) {
            return text.substring(0, lineStart) + text.substring(lineEnd);
        }
        String head = text.substring(0, annotation.start());
        String tail = LEADING_BLANKS.matcher(text.substring(annotation.end())).replaceFirst("");
        if (endsItsLine) {
            // The blank that separated the annotation goes with it, so no line
            // is left with trailing whitespace.
            head = TRAILING_BLANKS.matcher(head).replaceFirst("");
        }
        return head + tail;
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
            if (importedName(line).path().compareTo(ANNOTATION_FQN) > 0) {
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
        if (REMAINING_USE.matcher(codeOutsideImports(text)).find()) {
            return text;
        }
        return importLines(text).stream()
            .filter(line -> bindsTheAnnotation(importedName(line)))
            .findFirst()
            .map(line -> text.substring(0, line.start()) + text.substring(line.end()))
            .orElse(text);
    }

    /**
     * The sanitized text with the import statements blanked out, so the import
     * of the annotation is not mistaken for a use of it.
     */
    private static String codeOutsideImports(String text) {
        StringBuilder code = new StringBuilder(KotlinSanitizer.sanitize(text).text());
        for (SourceLine line : importLines(text)) {
            for (int index = line.start(); index < line.end(); index++) {
                code.setCharAt(index, ' ');
            }
        }
        return code.toString();
    }

    /**
     * An import statement, split into the path it names and the name it binds.
     * An aliased import binds another name, so it neither makes the simple name
     * available nor may it be removed when the simple name falls out of use.
     *
     * @param path the imported path, a trailing semicolon already stripped
     * @param boundName the name the statement makes available
     */
    private record ImportedName(String path, String boundName) {
    }

    private static boolean coversAnnotation(SourceLine line) {
        ImportedName imported = importedName(line);
        return imported.path().equals(STAR_IMPORT) || bindsTheAnnotation(imported);
    }

    private static boolean bindsTheAnnotation(ImportedName imported) {
        return imported.path().equals(ANNOTATION_FQN)
            && imported.boundName().equals(ANNOTATION_SIMPLE_NAME);
    }

    private static ImportedName importedName(SourceLine line) {
        String statement = line.content().substring(IMPORT_KEYWORD.length()).trim();
        if (statement.endsWith(";")) {
            statement = statement.substring(0, statement.length() - 1).trim();
        }
        int alias = statement.indexOf(ALIAS_KEYWORD);
        if (alias >= 0) {
            return new ImportedName(statement.substring(0, alias).trim(),
                statement.substring(alias + ALIAS_KEYWORD.length()).trim());
        }
        return new ImportedName(statement, Names.simpleName(statement));
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
