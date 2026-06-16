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

import com.github.javaparser.ParseProblemException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adversarial edge-case coverage for {@link AnnotationWriter}: realistic Java
 * source shapes that could break setting, updating or removing the annotation,
 * or that could silently corrupt foreign production code.
 */
class AnnotationWriterHardeningTest {

    private final AnnotationWriter writer = new AnnotationWriter();

    private Path copy(Path root, String fixture, String target) {
        Path file = root.resolve(target);
        try (InputStream in = getClass().getClassLoader()
            .getResourceAsStream("delegates/" + fixture + ".java.txt")) {
            Files.write(file, in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    private String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private byte[] readBytes(Path file) throws IOException {
        return Files.readAllBytes(file);
    }

    private int occurrences(String haystack, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int idx = haystack.indexOf(needle, from);
            if (idx < 0) {
                return count;
            }
            count++;
            from = idx + needle.length();
        }
    }

    // ---------------------------------------------------------------------
    // Value escaping: writing a path with special characters must produce
    // compiling source and round-trip back to the exact logical value.
    // ---------------------------------------------------------------------

    @Test
    void writesBackslashPathWithForwardSlashesIntactAndEscaped(@TempDir Path root) throws IOException {
        Path file = copy(root, "OrderDelegate", "OrderDelegate.java");

        // A defensive case: even if a backslash slips in, it must not break the file.
        writer.write(file, List.of("bpmn\\processes\\order.bpmn"));

        String content = read(file);
        assertTrue(content.contains("@CalledFrom(\"bpmn\\\\processes\\\\order.bpmn\")"),
            "backslashes must be escaped in source, was:\n" + content);
        assertEquals(List.of("bpmn\\processes\\order.bpmn"), writer.readCurrentValues(file),
            "the exact logical value must round-trip");
    }

    @Test
    void writesValueWithQuotesTabAndUnicodeWithoutBreakingTheFile(@TempDir Path root) throws IOException {
        Path file = copy(root, "OrderDelegate", "OrderDelegate.java");
        String tricky = "bpmn/\"quoted\"/with\ttab/ünïcödé.bpmn";

        writer.write(file, List.of(tricky));

        // The decisive proof: the file still parses and the value round-trips.
        assertEquals(List.of(tricky), writer.readCurrentValues(file),
            "value with quotes/tab/unicode must round-trip exactly");
        String content = read(file);
        assertTrue(content.contains("\\\""), "embedded quotes must be escaped:\n" + content);
        assertTrue(content.contains("\\t"), "tab must be escaped:\n" + content);
        assertTrue(content.contains("implements JavaDelegate"), "class must remain intact");
    }

    @Test
    void writesMultiValueWithSpecialCharactersRoundTrips(@TempDir Path root) throws IOException {
        Path file = copy(root, "OrderDelegate", "OrderDelegate.java");
        List<String> values = List.of("bpmn/a\"b.bpmn", "bpmn/c\\d.bpmn");

        writer.write(file, values);

        assertEquals(values, writer.readCurrentValues(file),
            "multi value set with special characters must round-trip");
    }

    @Test
    void readsBackEscapedValueAsDecodedLogicalValue(@TempDir Path root) throws IOException {
        Path file = copy(root, "EscapedValueDelegate", "EscapedValueDelegate.java");

        assertEquals(List.of("bpmn/a\\b\"q\".bpmn"), writer.readCurrentValues(file),
            "reader must decode escape sequences to the logical value");
    }

    // ---------------------------------------------------------------------
    // Line endings and surrounding bytes.
    // ---------------------------------------------------------------------

    @Test
    void preservesCrlfLineEndingsOnEveryLineIncludingInsertedOnes(@TempDir Path root) throws IOException {
        Path file = copy(root, "CrlfDelegate", "CrlfDelegate.java");

        writer.write(file, List.of("bpmn/processes/order.bpmn"));

        byte[] bytes = readBytes(file);
        String text = new String(bytes, StandardCharsets.UTF_8);
        // No lone LF: removing all CRLF must leave no remaining LF.
        String withoutCrlf = text.replace("\r\n", "");
        assertFalse(withoutCrlf.contains("\n"), "no line may end with a lone LF");
        assertFalse(withoutCrlf.contains("\r"), "no line may end with a lone CR");
        assertTrue(text.contains("@CalledFrom(\"bpmn/processes/order.bpmn\")\r\n"),
            "the inserted annotation line must also end with CRLF");
        assertTrue(text.contains("import net.jakobarndt.bpmnbacklink.annotation.CalledFrom;\r\n"),
            "the inserted import line must also end with CRLF");
    }

    @Test
    void preservesLicenseHeaderAndJavadocWhenWriting(@TempDir Path root) throws IOException {
        Path file = copy(root, "HandFormattedDelegate", "HandFormattedDelegate.java");

        writer.write(file, List.of("bpmn/processes/new.bpmn"));

        String content = read(file);
        assertTrue(content.contains("Hand-written annotation with unusual whitespace"),
            "Javadoc above the type must survive");
        assertEquals(List.of("bpmn/processes/new.bpmn"), writer.readCurrentValues(file));
    }

    // ---------------------------------------------------------------------
    // Idempotency: byte-identical on the second identical update.
    // ---------------------------------------------------------------------

    @Test
    void secondIdenticalUpdateIsByteIdentical(@TempDir Path root) throws IOException {
        Path file = copy(root, "PreAnnotatedOrderDelegate", "OrderDelegate.java");
        List<String> values = List.of("bpmn/processes/order.bpmn", "bpmn/processes/sub/shipping.bpmn");

        writer.write(file, values);
        byte[] afterFirst = readBytes(file);
        writer.write(file, values);
        byte[] afterSecond = readBytes(file);

        assertEquals(new String(afterFirst, StandardCharsets.UTF_8),
            new String(afterSecond, StandardCharsets.UTF_8),
            "a second identical update must be byte-identical");
    }

    @Test
    void normalizingHandFormattedAnnotationIsIdempotent(@TempDir Path root) throws IOException {
        Path file = copy(root, "HandFormattedDelegate", "HandFormattedDelegate.java");

        writer.write(file, List.of("bpmn/processes/order.bpmn"));
        byte[] afterFirst = readBytes(file);
        writer.write(file, List.of("bpmn/processes/order.bpmn"));
        byte[] afterSecond = readBytes(file);

        assertEquals(new String(afterFirst, StandardCharsets.UTF_8),
            new String(afterSecond, StandardCharsets.UTF_8),
            "normalizing a hand-formatted annotation must be idempotent");
    }

    // ---------------------------------------------------------------------
    // Type-shape variations: the annotation must land on the right type.
    // ---------------------------------------------------------------------

    @Test
    void annotatesOnlyThePrimaryTypeWhenMultipleTopLevelTypesExist(@TempDir Path root) throws IOException {
        Path file = copy(root, "TwoTopLevelTypesDelegate", "TwoTopLevelTypesDelegate.java");

        writer.write(file, List.of("bpmn/processes/order.bpmn"));

        String content = read(file);
        assertEquals(1, occurrences(content, "@CalledFrom"),
            "exactly one annotation must be written:\n" + content);
        int annotationIndex = content.indexOf("@CalledFrom");
        int primaryIndex = content.indexOf("class TwoTopLevelTypesDelegate");
        int companionIndex = content.indexOf("class CompanionHelper");
        assertTrue(annotationIndex < primaryIndex,
            "annotation must precede the primary type");
        assertTrue(primaryIndex < companionIndex, "companion type must remain after the primary");
        // Since there is exactly one annotation and it precedes the primary type,
        // the companion necessarily carries none.
    }

    @Test
    void annotatesGenericClassWithoutLosingTypeParameter(@TempDir Path root) throws IOException {
        Path file = copy(root, "GenericDelegate", "GenericDelegate.java");

        writer.write(file, List.of("bpmn/processes/order.bpmn"));

        String content = read(file);
        assertTrue(content.contains("class GenericDelegate<T extends Number> implements JavaDelegate"),
            "generic type parameter must survive:\n" + content);
        int annotationIndex = content.indexOf("@CalledFrom");
        int classIndex = content.indexOf("class GenericDelegate");
        assertTrue(annotationIndex >= 0 && annotationIndex < classIndex,
            "annotation must sit on the type declaration");
    }

    /**
     * Defined behaviour: {@link AnnotationWriter} uses {@code StaticJavaParser}
     * with its default language level, which does not enable records. A record
     * source is therefore rejected up front with a parse problem and the file
     * is never touched. This documents the boundary rather than silently
     * corrupting a record header.
     */
    @Test
    void recordSourceIsRejectedUpFrontAndLeftUntouched(@TempDir Path root) throws IOException {
        Path file = copy(root, "RecordDelegate", "RecordDelegate.java");
        byte[] before = readBytes(file);

        assertThrows(ParseProblemException.class,
            () -> writer.write(file, List.of("bpmn/processes/order.bpmn")));

        assertArrayEquals(before, readBytes(file),
            "the source file must be left byte-for-byte untouched on a parse failure");
    }

    @Test
    void enumImplementingJavaDelegateGetsAnnotatedWithoutCorruptingConstants(@TempDir Path root)
        throws IOException {
        Path file = copy(root, "EnumDelegate", "EnumDelegate.java");

        writer.write(file, List.of("bpmn/processes/order.bpmn"));

        String content = read(file);
        assertTrue(content.contains("enum EnumDelegate implements JavaDelegate"),
            "enum header must remain intact:\n" + content);
        assertTrue(content.contains("INSTANCE;"), "enum constant must remain:\n" + content);
        assertEquals(List.of("bpmn/processes/order.bpmn"), writer.readCurrentValues(file));
    }

    @Test
    void annotatesTopLevelTypeEvenWhenOnlyANestedClassImplementsJavaDelegate(@TempDir Path root)
        throws IOException {
        Path file = copy(root, "InnerClassDelegate", "InnerClassDelegate.java");

        writer.write(file, List.of("bpmn/processes/order.bpmn"));

        String content = read(file);
        int annotationIndex = content.indexOf("@CalledFrom");
        int outerIndex = content.indexOf("class InnerClassDelegate");
        int innerIndex = content.indexOf("class Inner ");
        assertTrue(annotationIndex >= 0 && annotationIndex < outerIndex,
            "annotation must land on the top-level type, not the nested one:\n" + content);
        assertTrue(outerIndex < innerIndex, "nested class must remain nested");
        assertEquals(1, occurrences(content, "@CalledFrom"), "only one annotation expected");
    }

    // ---------------------------------------------------------------------
    // Package and import handling.
    // ---------------------------------------------------------------------

    @Test
    void writesAnnotationAndImportWhenFileHasNoPackageDeclaration(@TempDir Path root) throws IOException {
        Path file = copy(root, "NoPackageDelegate", "NoPackageDelegate.java");

        writer.write(file, List.of("bpmn/processes/order.bpmn"));

        String content = read(file);
        assertTrue(content.contains("import net.jakobarndt.bpmnbacklink.annotation.CalledFrom;"),
            "import must be added even without a package declaration:\n" + content);
        assertTrue(content.contains("@CalledFrom(\"bpmn/processes/order.bpmn\")"),
            "annotation must be written:\n" + content);
        assertEquals(List.of("bpmn/processes/order.bpmn"), writer.readCurrentValues(file));
    }

    @Test
    void updatingFullyQualifiedTypeAnnotationReplacesItWithoutDuplicating(@TempDir Path root)
        throws IOException {
        Path file = copy(root, "FqnTypeAnnotationDelegate", "FqnTypeAnnotationDelegate.java");

        writer.write(file, List.of("bpmn/processes/new.bpmn"));

        String content = read(file);
        assertFalse(content.contains("old.bpmn"), "old value must be gone:\n" + content);
        assertEquals(1, occurrences(content, "CalledFrom("),
            "the FQN annotation must be replaced, not duplicated:\n" + content);
        assertEquals(List.of("bpmn/processes/new.bpmn"), writer.readCurrentValues(file));
    }

    @Test
    void readsFullyQualifiedTypeAnnotationValue(@TempDir Path root) throws IOException {
        Path file = copy(root, "FqnTypeAnnotationDelegate", "FqnTypeAnnotationDelegate.java");

        assertEquals(List.of("bpmn/processes/old.bpmn"), writer.readCurrentValues(file),
            "a fully qualified type annotation must be readable too");
    }

    @Test
    void updatingWithWildcardImportPresentDoesNotAddARedundantImport(@TempDir Path root)
        throws IOException {
        Path file = copy(root, "WildcardImportDelegate", "WildcardImportDelegate.java");

        writer.write(file, List.of("bpmn/processes/new.bpmn"));

        String content = read(file);
        assertTrue(content.contains("import net.jakobarndt.bpmnbacklink.annotation.*;"),
            "wildcard import must remain:\n" + content);
        assertFalse(content.contains("import net.jakobarndt.bpmnbacklink.annotation.CalledFrom;"),
            "no redundant explicit import may be added alongside the wildcard:\n" + content);
        assertEquals(List.of("bpmn/processes/new.bpmn"), writer.readCurrentValues(file));
    }

    @Test
    void removingAnnotationWithWildcardImportLeavesWildcardUntouched(@TempDir Path root)
        throws IOException {
        Path file = copy(root, "WildcardImportDelegate", "WildcardImportDelegate.java");

        writer.write(file, List.of());

        String content = read(file);
        assertFalse(content.contains("@CalledFrom"), "annotation must be gone:\n" + content);
        assertTrue(content.contains("import net.jakobarndt.bpmnbacklink.annotation.*;"),
            "wildcard import must stay (the filter only drops the exact non-asterisk import):\n" + content);
    }
}
