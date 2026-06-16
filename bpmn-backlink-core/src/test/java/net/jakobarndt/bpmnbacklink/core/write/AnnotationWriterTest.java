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

class AnnotationWriterTest {

    private final AnnotationWriter writer = new AnnotationWriter();

    private Path copy(Path root, String fixture, String target) {
        Path file = root.resolve(target);
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("delegates/" + fixture + ".java.txt")) {
            Files.write(file, in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    private String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    void addsSingleValueAnnotationAndImport(@TempDir Path root) throws IOException {
        Path file = copy(root, "OrderDelegate", "OrderDelegate.java");

        writer.write(file, List.of("bpmn/processes/order.bpmn"));

        String content = read(file);
        assertTrue(content.contains("@CalledFrom(\"bpmn/processes/order.bpmn\")"),
            "single value must render without braces, was:\n" + content);
        assertTrue(content.contains("import net.jakobarndt.bpmnbacklink.annotation.CalledFrom;"),
            "import must be added");
    }

    @Test
    void addsMultiValueAnnotationWithBraces(@TempDir Path root) throws IOException {
        Path file = copy(root, "OrderDelegate", "OrderDelegate.java");

        writer.write(file, List.of("bpmn/processes/order.bpmn", "bpmn/processes/sub/shipping.bpmn"));

        String content = read(file);
        assertTrue(content.contains("@CalledFrom({"), "multi value must use braces, was:\n" + content);
        assertTrue(content.contains("\"bpmn/processes/order.bpmn\""));
        assertTrue(content.contains("\"bpmn/processes/sub/shipping.bpmn\""));
    }

    @Test
    void readsValuesBackAfterWrite(@TempDir Path root) throws IOException {
        Path file = copy(root, "OrderDelegate", "OrderDelegate.java");
        List<String> values = List.of("bpmn/processes/order.bpmn", "bpmn/processes/sub/shipping.bpmn");

        writer.write(file, values);

        assertEquals(values, writer.readCurrentValues(file));
    }

    @Test
    void readsExistingMultiLineAnnotation(@TempDir Path root) throws IOException {
        Path file = copy(root, "PreAnnotatedOrderDelegate", "OrderDelegate.java");

        assertEquals(
            List.of("bpmn/processes/order.bpmn", "bpmn/processes/sub/shipping.bpmn"),
            writer.readCurrentValues(file));
    }

    @Test
    void preservesSurroundingFormattingAndComments(@TempDir Path root) throws IOException {
        Path file = copy(root, "OrderDelegate", "OrderDelegate.java");

        writer.write(file, List.of("bpmn/processes/order.bpmn"));

        String content = read(file);
        assertTrue(content.contains("This Javadoc and the spacing below must survive a rewrite."),
            "Javadoc must be preserved");
        assertTrue(content.contains("// a deliberately oddly placed comment"),
            "inner comment must be preserved");
        assertTrue(content.contains("execution.setVariable(\"ordered\", true);"),
            "method body must be preserved");
    }

    @Test
    void removingAnnotationAlsoRemovesUnusedImport(@TempDir Path root) throws IOException {
        Path file = copy(root, "ObsoleteDelegate", "ObsoleteDelegate.java");

        writer.write(file, List.of());

        String content = read(file);
        assertFalse(content.contains("@CalledFrom"), "annotation must be gone");
        assertFalse(content.contains("net.jakobarndt.bpmnbacklink.annotation.CalledFrom"),
            "unused import must be removed");
        assertTrue(content.contains("implements JavaDelegate"), "class itself must remain");
    }

    @Test
    void updatesExistingAnnotationToNewValueSet(@TempDir Path root) throws IOException {
        Path file = copy(root, "ObsoleteDelegate", "ObsoleteDelegate.java");

        writer.write(file, List.of("bpmn/processes/new.bpmn"));

        assertEquals(List.of("bpmn/processes/new.bpmn"), writer.readCurrentValues(file));
        String content = read(file);
        assertFalse(content.contains("gone.bpmn"), "old value must be replaced");
    }

    @Test
    void readCurrentValuesReturnsEmptyWhenNoAnnotation(@TempDir Path root) throws IOException {
        Path file = copy(root, "OrderDelegate", "OrderDelegate.java");
        assertTrue(writer.readCurrentValues(file).isEmpty());
    }

    @Test
    void readsValuesFromNormalAnnotationForm(@TempDir Path root) throws IOException {
        Path file = copy(root, "NormalAnnotatedDelegate", "NormalAnnotatedDelegate.java");

        assertEquals(
            List.of("bpmn/processes/order.bpmn", "bpmn/processes/sub/shipping.bpmn"),
            writer.readCurrentValues(file));
    }

    @Test
    void readCurrentValuesIsEmptyForCompilationUnitWithoutTopLevelType(@TempDir Path root) throws IOException {
        Path file = copy(root, "PackageInfo", "package-info.java");
        assertTrue(writer.readCurrentValues(file).isEmpty(),
            "a unit without any type carries no annotation values");
    }

    @Test
    void writeFailsWhenNoTopLevelTypeExists(@TempDir Path root) throws IOException {
        Path file = copy(root, "PackageInfo", "package-info.java");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> writer.write(file, List.of("bpmn/processes/order.bpmn")));
        assertTrue(ex.getMessage().contains("No top-level type"), "message must name the cause");
    }

    @Test
    void usesFirstDeclaredTypeWhenNoPrimaryTypeIsPresent(@TempDir Path root) throws IOException {
        Path file = copy(root, "NoPrimaryType", "NoPrimaryType.java");

        writer.write(file, List.of("bpmn/processes/order.bpmn"));

        String content = read(file);
        // The annotation must land on the first declared type, FirstHelper.
        int annotationIndex = content.indexOf("@CalledFrom");
        int firstHelperIndex = content.indexOf("class FirstHelper");
        int secondHelperIndex = content.indexOf("class SecondHelper");
        assertTrue(annotationIndex >= 0, "annotation must be written");
        assertTrue(annotationIndex < firstHelperIndex,
            "annotation must precede the first declared type");
        assertTrue(firstHelperIndex < secondHelperIndex, "type order preserved");
        assertEquals(List.of("bpmn/processes/order.bpmn"), writer.readCurrentValues(file));
    }

    @Test
    void readingMarkerAnnotationYieldsNoValues(@TempDir Path root) throws IOException {
        Path file = copy(root, "MarkerCalledFromDelegate", "MarkerCalledFromDelegate.java");
        assertTrue(writer.readCurrentValues(file).isEmpty(),
            "a bare marker @CalledFrom carries no values");
    }

    @Test
    void readingNormalAnnotationWithNonValuePairYieldsNoValues(@TempDir Path root) throws IOException {
        Path file = copy(root, "OtherPairDelegate", "OtherPairDelegate.java");
        assertTrue(writer.readCurrentValues(file).isEmpty(),
            "a member pair not named 'value' must be skipped");
    }

    @Test
    void readingNonStringLiteralValueYieldsNoValues(@TempDir Path root) throws IOException {
        Path file = copy(root, "NonStringValueDelegate", "NonStringValueDelegate.java");
        assertTrue(writer.readCurrentValues(file).isEmpty(),
            "a non-string-literal value must contribute nothing");
    }

    @Test
    void removingTypeAnnotationKeepsImportWhenFullyQualifiedUsageRemains(@TempDir Path root) throws IOException {
        Path file = copy(root, "FqnRemainingDelegate", "FqnRemainingDelegate.java");

        writer.write(file, List.of());

        String content = read(file);
        assertFalse(content.contains("@CalledFrom(\"bpmn/processes/gone.bpmn\")"),
            "type-level simple-name annotation must be removed");
        assertTrue(content.contains("net.jakobarndt.bpmnbacklink.annotation.CalledFrom(\"bpmn/processes/kept.bpmn\")"),
            "fully qualified method annotation must remain:\n" + content);
        assertTrue(content.contains("import net.jakobarndt.bpmnbacklink.annotation.CalledFrom;"),
            "import must stay because the FQN usage still resolves through it");
    }

    @Test
    void removingAnnotationDropsOnlyTheExactImportAndKeepsStaticAndWildcardImports(@TempDir Path root)
        throws IOException {
        Path file = copy(root, "MixedImportsDelegate", "MixedImportsDelegate.java");

        writer.write(file, List.of());

        String content = read(file);
        assertFalse(content.contains("@CalledFrom"), "annotation must be gone");
        assertFalse(content.contains("import net.jakobarndt.bpmnbacklink.annotation.CalledFrom;"),
            "the exact annotation import must be removed");
        assertTrue(content.contains("import static java.lang.Math.PI;"),
            "static import must be left untouched");
        assertTrue(content.contains("import java.util.*;"),
            "wildcard import must be left untouched");
    }

    @Test
    void removingTypeAnnotationKeepsImportWhenAnotherAnnotationRemains(@TempDir Path root) throws IOException {
        Path file = copy(root, "SecondAnnotationDelegate", "SecondAnnotationDelegate.java");

        writer.write(file, List.of());

        String content = read(file);
        assertTrue(content.contains("@CalledFrom(\"bpmn/processes/other.bpmn\")"),
            "method-level annotation must remain:\n" + content);
        assertFalse(content.contains("@CalledFrom(\"bpmn/processes/gone.bpmn\")"),
            "type-level annotation must be removed");
        assertTrue(content.contains("import net.jakobarndt.bpmnbacklink.annotation.CalledFrom;"),
            "import must stay because it is still used by the method annotation");
    }
}
