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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static net.jakobarndt.bpmnbacklink.core.write.KotlinAnnotationWriterTest.copy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adversarial edge-case coverage for {@link KotlinAnnotationWriter}: source
 * shapes that could break setting, updating or removing the annotation, or that
 * could silently corrupt foreign production code.
 */
class KotlinAnnotationWriterHardeningTest {

    private final KotlinAnnotationWriter writer = new KotlinAnnotationWriter();

    private String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------------
    // Value escaping.
    // ---------------------------------------------------------------------

    @Test
    void writesEveryCharacterThatNeedsEscapingAndReadsItBack(@TempDir Path root) throws IOException {
        Path file = copy(this, root, "KotlinOrderDelegate", "KotlinOrderDelegate.kt");
        String tricky = "bpmn/a\\b/\"q\"/$tpl/\tt/\bb/\nn/\rr/uenicode-ü.bpmn";

        writer.write(file, "KotlinOrderDelegate", List.of(tricky));

        String content = read(file);
        assertTrue(content.contains("\\\\"), "a backslash must be escaped:\n" + content);
        assertTrue(content.contains("\\\""), "a quote must be escaped:\n" + content);
        assertTrue(content.contains("\\$"), "a dollar must be escaped so it is no template:\n" + content);
        assertTrue(content.contains("\\t") && content.contains("\\b"), "control characters must be escaped");
        assertTrue(content.contains("\\n") && content.contains("\\r"), "line breaks must be escaped");
        assertTrue(content.contains("ü"), "a non-ASCII character stays literal:\n" + content);
        assertTrue(content.contains("class KotlinOrderDelegate : JavaDelegate"), "the declaration must stay intact");
        assertEquals(List.of(tricky), writer.readCurrentValues(file, "KotlinOrderDelegate"),
            "the exact logical value must round-trip");
    }

    @Test
    void readsBackAHandWrittenEscapedValueAsItsLogicalValue(@TempDir Path root) throws IOException {
        Path file = copy(this, root, "KotlinEscapedValueDelegate", "KotlinEscapedValueDelegate.kt");

        assertEquals(List.of("bpmn/a\\b\"q\".bpmn"),
            writer.readCurrentValues(file, "KotlinEscapedValueDelegate"),
            "the reader must decode escape sequences");
    }

    // ---------------------------------------------------------------------
    // Line endings.
    // ---------------------------------------------------------------------

    @Test
    void preservesCrlfLineEndingsOnEveryLineIncludingInsertedOnes(@TempDir Path root) throws IOException {
        Path file = copy(this, root, "KotlinCrlfDelegate", "KotlinCrlfDelegate.kt");

        writer.write(file, "KotlinCrlfDelegate", List.of("bpmn/processes/order.bpmn"));

        String content = read(file);
        String withoutCrlf = content.replace("\r\n", "");
        assertFalse(withoutCrlf.contains("\n"), "no line may end with a lone LF:\n" + content);
        assertFalse(withoutCrlf.contains("\r"), "no line may end with a lone CR:\n" + content);
        assertTrue(content.contains("@CalledFrom(\"bpmn/processes/order.bpmn\")\r\n"),
            "the inserted annotation line must also end with CRLF:\n" + content);
        assertTrue(content.contains("import net.jakobarndt.bpmnbacklink.annotation.CalledFrom\r\n"),
            "the inserted import line must also end with CRLF:\n" + content);
        assertEquals(List.of("bpmn/processes/order.bpmn"), writer.readCurrentValues(file, "KotlinCrlfDelegate"));
    }

    // ---------------------------------------------------------------------
    // Annotation placement in unusual positions.
    // ---------------------------------------------------------------------

    @Test
    void insertsInlineWhenTheDeclarationDoesNotStartItsLine(@TempDir Path root) throws IOException {
        Path file = copy(this, root, "KotlinSameLineDelegate", "KotlinSameLineDelegate.kt");

        writer.write(file, "KotlinSameLineDelegate", List.of("bpmn/processes/order.bpmn"));

        String content = read(file);
        assertTrue(content.contains(
                "val marker = 1; @CalledFrom(\"bpmn/processes/order.bpmn\") class KotlinSameLineDelegate"),
            "the annotation must be inserted inline, leaving the neighbour alone:\n" + content);
        assertEquals(List.of("bpmn/processes/order.bpmn"), writer.readCurrentValues(file, "KotlinSameLineDelegate"));
    }

    @Test
    void removingAnInlineAnnotationLeavesTheDeclarationLineIntact(@TempDir Path root) throws IOException {
        Path file = copy(this, root, "KotlinInlineAnnotationDelegate", "KotlinInlineAnnotationDelegate.kt");

        writer.write(file, "KotlinInlineAnnotationDelegate", List.of());

        String content = read(file);
        assertTrue(content.contains("\nclass KotlinInlineAnnotationDelegate : JavaDelegate"),
            "the declaration must survive on its line without a leading blank:\n" + content);
        assertFalse(content.contains("@CalledFrom"), "the annotation must be gone:\n" + content);
        assertFalse(content.contains("import net.jakobarndt.bpmnbacklink.annotation.CalledFrom"),
            "the unused import must be gone:\n" + content);
    }

    @Test
    void removingAnInlineAnnotationOnTheLastLineWithoutALineBreak(@TempDir Path root) throws IOException {
        Path file = copy(this, root, "KotlinLastLineDelegate", "KotlinLastLineDelegate.kt");

        writer.write(file, "KotlinLastLineDelegate", List.of());

        assertEquals("""
            package net.example.delegate

            import org.camunda.bpm.engine.delegate.JavaDelegate

            class KotlinLastLineDelegate : JavaDelegate""", read(file));
    }

    @Test
    void putsTheImportBelowALicenseHeaderWhenThereIsNoPackage(@TempDir Path root) throws IOException {
        Path file = copy(this, root, "KotlinCommentedBareDelegate", "KotlinCommentedBareDelegate.kt");

        writer.write(file, "KotlinCommentedBareDelegate", List.of("bpmn/processes/order.bpmn"));

        String content = read(file);
        assertTrue(content.startsWith("/*\n * A license header"), "the header must stay on top:\n" + content);
        assertTrue(content.contains(" */\n\nimport net.jakobarndt.bpmnbacklink.annotation.CalledFrom\n\n"
                + "@CalledFrom(\"bpmn/processes/order.bpmn\")\nclass KotlinCommentedBareDelegate"),
            "the import must sit between the header and the declaration:\n" + content);
    }

    @Test
    void removingAnAnnotationBesideAnotherOneKeepsTheNeighbour(@TempDir Path root) throws IOException {
        Path file = copy(this, root, "KotlinAdjacentAnnotationDelegate", "KotlinAdjacentAnnotationDelegate.kt");

        writer.write(file, "KotlinAdjacentAnnotationDelegate", List.of());

        String content = read(file);
        assertTrue(content.contains("@Deprecated"), "the foreign annotation must survive:\n" + content);
        assertFalse(content.contains("@CalledFrom"), "the annotation must be gone:\n" + content);
        assertTrue(content.contains("class KotlinAdjacentAnnotationDelegate : JavaDelegate"),
            "the declaration must stay intact:\n" + content);
    }

    // ---------------------------------------------------------------------
    // Multi-line annotations.
    // ---------------------------------------------------------------------

    @Test
    void normalizesAMultiLineAnnotationToASingleLine(@TempDir Path root) throws IOException {
        Path file = copy(this, root, "KotlinHandFormattedDelegate", "KotlinHandFormattedDelegate.kt");

        assertEquals(List.of("bpmn/processes/order.bpmn"),
            writer.readCurrentValues(file, "KotlinHandFormattedDelegate"),
            "the named array form must be readable");

        writer.write(file, "KotlinHandFormattedDelegate", List.of("bpmn/processes/order.bpmn"));

        String content = read(file);
        assertTrue(content.contains("@CalledFrom(\"bpmn/processes/order.bpmn\")\nclass KotlinHandFormattedDelegate"),
            "the annotation must be normalized into one line:\n" + content);
        assertTrue(content.contains("Hand-written annotation"), "the KDoc must survive:\n" + content);
    }

    @Test
    void removingAMultiLineAnnotationTakesAllOfItsLines(@TempDir Path root) throws IOException {
        Path file = copy(this, root, "KotlinHandFormattedDelegate", "KotlinHandFormattedDelegate.kt");

        writer.write(file, "KotlinHandFormattedDelegate", List.of());

        String content = read(file);
        assertFalse(content.contains("CalledFrom"), "no leftover of the annotation may remain:\n" + content);
        assertFalse(content.contains("value = ["), "the argument lines must be gone too:\n" + content);
        assertTrue(content.contains(" */\nclass KotlinHandFormattedDelegate : JavaDelegate"),
            "the declaration must follow the KDoc directly:\n" + content);
    }

    // ---------------------------------------------------------------------
    // Types that share a file.
    // ---------------------------------------------------------------------

    @Test
    void annotatesEveryTopLevelTypeOfAFileIndependently(@TempDir Path root) throws IOException {
        Path file = copy(this, root, "KotlinTwoAnnotatedDelegates", "KotlinTwoAnnotatedDelegates.kt");

        writer.write(file, "KotlinFirstDelegate", List.of("bpmn/processes/sub/shipping.bpmn"));
        writer.write(file, "KotlinSecondDelegate", List.of("bpmn/processes/order.bpmn"));

        assertEquals(List.of("bpmn/processes/sub/shipping.bpmn"),
            writer.readCurrentValues(file, "KotlinFirstDelegate"),
            "the first write must survive the second one");
        assertEquals(List.of("bpmn/processes/order.bpmn"), writer.readCurrentValues(file, "KotlinSecondDelegate"));
    }

    @Test
    void annotatesAnObjectDeclarationAndABacktickName(@TempDir Path root) throws IOException {
        Path file = copy(this, root, "KotlinEdgeCaseDelegates", "KotlinEdgeCaseDelegates.kt");

        writer.write(file, "KotlinObjectDelegate", List.of("bpmn/processes/order.bpmn"));
        writer.write(file, "spaced out delegate", List.of("bpmn/processes/sub/shipping.bpmn"));

        String content = read(file);
        assertTrue(content.contains("@CalledFrom(\"bpmn/processes/order.bpmn\")\nobject KotlinObjectDelegate"),
            "the object declaration must be annotated:\n" + content);
        assertTrue(content.contains(
                "@CalledFrom(\"bpmn/processes/sub/shipping.bpmn\")\nclass `spaced out delegate`"),
            "the backtick-named class must be annotated:\n" + content);
        assertTrue(content.contains("class RawStringDelegate : JavaDelegate"),
            "the raw string content must be untouched:\n" + content);
        assertEquals(List.of("bpmn/processes/order.bpmn"), writer.readCurrentValues(file, "KotlinObjectDelegate"));
        assertEquals(List.of("bpmn/processes/sub/shipping.bpmn"),
            writer.readCurrentValues(file, "spaced out delegate"));
    }
}
