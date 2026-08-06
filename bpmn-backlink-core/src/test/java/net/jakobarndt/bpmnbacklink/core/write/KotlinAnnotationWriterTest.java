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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the everyday behaviour of the Kotlin annotation writer: reading both
 * accepted annotation forms, writing the vararg form, placing the import and
 * staying idempotent.
 */
class KotlinAnnotationWriterTest {

    private final KotlinAnnotationWriter writer = new KotlinAnnotationWriter();

    static Path copy(Object test, Path root, String fixture, String target) {
        Path file = root.resolve(target);
        try (InputStream in = test.getClass().getClassLoader()
            .getResourceAsStream("delegates/" + fixture + ".kt.txt")) {
            Files.write(file, in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    private Path copy(Path root, String fixture, String target) {
        return copy(this, root, fixture, target);
    }

    private String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------------
    // Language dispatch.
    // ---------------------------------------------------------------------

    @Test
    void claimsKotlinSourcesOnly() {
        assertTrue(writer.supports(Path.of("a", "Delegate.kt")));
        assertFalse(writer.supports(Path.of("a", "Delegate.java")));
    }

    // ---------------------------------------------------------------------
    // Reading.
    // ---------------------------------------------------------------------

    @Test
    void readsTheArrayForm(@TempDir Path root) throws IOException {
        Path file = copy(root, "KotlinPreAnnotatedOrderDelegate", "KotlinOrderDelegate.kt");

        assertEquals(List.of("bpmn/processes/order.bpmn", "bpmn/processes/sub/shipping.bpmn"),
            writer.readCurrentValues(file, "KotlinOrderDelegate"));
    }

    @Test
    void readsTheFullyQualifiedForm(@TempDir Path root) throws IOException {
        Path file = copy(root, "KotlinFqnAnnotationDelegate", "KotlinFqnAnnotationDelegate.kt");

        assertEquals(List.of("bpmn/processes/old.bpmn"),
            writer.readCurrentValues(file, "KotlinFqnAnnotationDelegate"));
    }

    @Test
    void readsNothingFromAnUnannotatedType(@TempDir Path root) throws IOException {
        Path file = copy(root, "KotlinOrderDelegate", "KotlinOrderDelegate.kt");

        assertTrue(writer.readCurrentValues(file, "KotlinOrderDelegate").isEmpty());
    }

    @Test
    void readsNothingFromAnAbsentType(@TempDir Path root) throws IOException {
        Path file = copy(root, "KotlinOrderDelegate", "KotlinOrderDelegate.kt");

        assertTrue(writer.readCurrentValues(file, "NoSuchType").isEmpty());
    }

    @Test
    void writingToAnAbsentTypeIsRejected(@TempDir Path root) throws IOException {
        Path file = copy(root, "KotlinOrderDelegate", "KotlinOrderDelegate.kt");
        byte[] before = Files.readAllBytes(file);

        assertThrows(IllegalStateException.class,
            () -> writer.write(file, "NoSuchType", List.of("bpmn/processes/order.bpmn")));
        assertArrayEqualsBytes(before, Files.readAllBytes(file));
    }

    private static void assertArrayEqualsBytes(byte[] expected, byte[] actual) {
        assertEquals(new String(expected, StandardCharsets.UTF_8), new String(actual, StandardCharsets.UTF_8),
            "the source file must be left untouched");
    }

    // ---------------------------------------------------------------------
    // Writing.
    // ---------------------------------------------------------------------

    @Test
    void writesTheVarargFormBelowTheKdoc(@TempDir Path root) throws IOException {
        Path file = copy(root, "KotlinOrderDelegate", "KotlinOrderDelegate.kt");

        writer.write(file, "KotlinOrderDelegate",
            List.of("bpmn/processes/order.bpmn", "bpmn/processes/sub/shipping.bpmn"));

        String content = read(file);
        assertTrue(content.contains(
                " */\n@CalledFrom(\"bpmn/processes/order.bpmn\", \"bpmn/processes/sub/shipping.bpmn\")\n"
                    + "class KotlinOrderDelegate : JavaDelegate {"),
            "the annotation must sit between the KDoc and the declaration:\n" + content);
        assertTrue(content.contains("Places an order."), "the KDoc must survive:\n" + content);
        assertTrue(content.contains("// a deliberately oddly placed comment"),
            "unrelated comments must survive:\n" + content);
    }

    @Test
    void replacesAnExistingAnnotationInPlace(@TempDir Path root) throws IOException {
        Path file = copy(root, "KotlinPreAnnotatedOrderDelegate", "KotlinOrderDelegate.kt");

        writer.write(file, "KotlinOrderDelegate", List.of("bpmn/processes/order.bpmn"));

        String content = read(file);
        assertEquals(1, content.split("@CalledFrom", -1).length - 1,
            "the annotation must be replaced, not duplicated:\n" + content);
        assertTrue(content.contains("@CalledFrom(\"bpmn/processes/order.bpmn\")\nclass KotlinOrderDelegate"),
            "the replacement keeps the position of the original:\n" + content);
        assertEquals(1, content.split("import net\\.jakobarndt", -1).length - 1,
            "the import must not be duplicated:\n" + content);
    }

    @Test
    void keepsTheIndentationOfAnAnnotationInsideAForeignAnnotationBlock(@TempDir Path root) throws IOException {
        Path file = copy(root, "KotlinDecoratedDelegate", "KotlinDecoratedDelegate.kt");

        writer.write(file, "KotlinDecoratedDelegate", List.of("bpmn/processes/new.bpmn"));

        String content = read(file);
        assertTrue(content.contains("@Component(\"kotlinDecoratedDelegate\")\n@Deprecated\n"
                + "@CalledFrom(\"bpmn/processes/new.bpmn\")\ninternal class KotlinDecoratedDelegate"),
            "the surrounding annotations must keep their order and position:\n" + content);
        assertEquals(List.of("bpmn/processes/new.bpmn"),
            writer.readCurrentValues(file, "KotlinDecoratedDelegate"));
    }

    @Test
    void removesTheAnnotationAndItsImport(@TempDir Path root) throws IOException {
        Path file = copy(root, "KotlinPreAnnotatedOrderDelegate", "KotlinOrderDelegate.kt");

        writer.write(file, "KotlinOrderDelegate", List.of());

        String content = read(file);
        assertFalse(content.contains("@CalledFrom"), "the annotation must be gone:\n" + content);
        assertFalse(content.contains("import net.jakobarndt.bpmnbacklink.annotation.CalledFrom"),
            "the now unused import must be gone:\n" + content);
        assertTrue(content.contains("class KotlinOrderDelegate : JavaDelegate {"),
            "the declaration must stay intact:\n" + content);
    }

    @Test
    void keepsTheImportWhileAnotherTypeStillUsesIt(@TempDir Path root) throws IOException {
        Path file = copy(root, "KotlinTwoAnnotatedDelegates", "KotlinTwoAnnotatedDelegates.kt");

        writer.write(file, "KotlinFirstDelegate", List.of());

        String content = read(file);
        assertTrue(content.contains("import net.jakobarndt.bpmnbacklink.annotation.CalledFrom"),
            "the import is still needed by the second type:\n" + content);
        assertTrue(writer.readCurrentValues(file, "KotlinFirstDelegate").isEmpty());
        assertEquals(List.of("bpmn/processes/sub/shipping.bpmn"),
            writer.readCurrentValues(file, "KotlinSecondDelegate"));
    }

    @Test
    void removingWithoutAnImportPresentLeavesTheRestUntouched(@TempDir Path root) throws IOException {
        Path file = copy(root, "KotlinFqnAnnotationDelegate", "KotlinFqnAnnotationDelegate.kt");

        writer.write(file, "KotlinFqnAnnotationDelegate", List.of());

        String content = read(file);
        assertFalse(content.contains("CalledFrom"), "the qualified annotation must be gone:\n" + content);
        assertTrue(content.contains("import org.camunda.bpm.engine.delegate.JavaDelegate"),
            "the unrelated import must stay:\n" + content);
    }

    // ---------------------------------------------------------------------
    // Import placement.
    // ---------------------------------------------------------------------

    @Test
    void insertsTheImportBeforeTheFirstGreaterImport(@TempDir Path root) throws IOException {
        Path file = copy(root, "KotlinOrderDelegate", "KotlinOrderDelegate.kt");

        writer.write(file, "KotlinOrderDelegate", List.of("bpmn/processes/order.bpmn"));

        String content = read(file);
        assertTrue(content.contains("import net.jakobarndt.bpmnbacklink.annotation.CalledFrom\n"
                + "import org.camunda.bpm.engine.delegate.DelegateExecution\n"),
            "the import must be sorted in front of the org.* imports:\n" + content);
    }

    @Test
    void appendsTheImportAfterSmallerImports(@TempDir Path root) throws IOException {
        Path file = copy(root, "KotlinLowImportDelegate", "KotlinLowImportDelegate.kt");

        writer.write(file, "KotlinLowImportDelegate", List.of("bpmn/processes/order.bpmn"));

        String content = read(file);
        assertTrue(content.contains("import java.time.Clock\n"
                + "import net.jakobarndt.bpmnbacklink.annotation.CalledFrom\n"),
            "the import must follow the alphabetically smaller one:\n" + content);
    }

    @Test
    void putsTheImportBelowThePackageWhenThereIsNoImportBlock(@TempDir Path root) throws IOException {
        Path file = copy(root, "KotlinNoImportDelegate", "KotlinNoImportDelegate.kt");

        writer.write(file, "KotlinNoImportDelegate", List.of("bpmn/processes/order.bpmn"));

        assertEquals("""
            package net.example.delegate

            import net.jakobarndt.bpmnbacklink.annotation.CalledFrom

            @CalledFrom("bpmn/processes/order.bpmn")
            class KotlinNoImportDelegate : JavaDelegate
            """, read(file));
    }

    @Test
    void putsTheImportAboveTheDeclarationWhenThereIsNoPackageEither(@TempDir Path root) throws IOException {
        Path file = copy(root, "KotlinBareDelegate", "KotlinBareDelegate.kt");

        writer.write(file, "KotlinBareDelegate", List.of("bpmn/processes/order.bpmn"));

        assertEquals("""
            import net.jakobarndt.bpmnbacklink.annotation.CalledFrom

            @CalledFrom("bpmn/processes/order.bpmn")
            class KotlinBareDelegate : JavaDelegate {

                override fun execute(execution: Any) = Unit
            }""", read(file));
    }

    @Test
    void doesNotAddARedundantImportBesideAStarImport(@TempDir Path root) throws IOException {
        Path file = copy(root, "KotlinStarImportDelegate", "KotlinStarImportDelegate.kt");

        writer.write(file, "KotlinStarImportDelegate", List.of("bpmn/processes/new.bpmn"));

        String content = read(file);
        assertTrue(content.contains("import net.jakobarndt.bpmnbacklink.annotation.*"),
            "the star import must stay:\n" + content);
        assertFalse(content.contains("import net.jakobarndt.bpmnbacklink.annotation.CalledFrom"),
            "no explicit import may be added beside the star import:\n" + content);
    }

    // ---------------------------------------------------------------------
    // Idempotency.
    // ---------------------------------------------------------------------

    @Test
    void aSecondIdenticalWriteIsByteIdentical(@TempDir Path root) throws IOException {
        Path file = copy(root, "KotlinOrderDelegate", "KotlinOrderDelegate.kt");
        List<String> values = List.of("bpmn/processes/order.bpmn", "bpmn/processes/sub/shipping.bpmn");

        writer.write(file, "KotlinOrderDelegate", values);
        String afterFirst = read(file);
        writer.write(file, "KotlinOrderDelegate", values);

        assertEquals(afterFirst, read(file), "a second identical write must not change the file");
        assertEquals(values, writer.readCurrentValues(file, "KotlinOrderDelegate"));
    }

    @Test
    void normalizingTheArrayFormIsIdempotent(@TempDir Path root) throws IOException {
        Path file = copy(root, "KotlinPreAnnotatedOrderDelegate", "KotlinOrderDelegate.kt");
        List<String> values = List.of("bpmn/processes/order.bpmn", "bpmn/processes/sub/shipping.bpmn");

        writer.write(file, "KotlinOrderDelegate", values);
        String afterFirst = read(file);
        writer.write(file, "KotlinOrderDelegate", values);

        assertTrue(afterFirst.contains(
                "@CalledFrom(\"bpmn/processes/order.bpmn\", \"bpmn/processes/sub/shipping.bpmn\")"),
            "the array form must be normalized to the vararg form:\n" + afterFirst);
        assertEquals(afterFirst, read(file), "normalizing must be idempotent");
    }
}
