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
package net.jakobarndt.bpmnbacklink.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import static net.jakobarndt.bpmnbacklink.core.Fixtures.bpmnProcessesDir;
import static net.jakobarndt.bpmnbacklink.core.Fixtures.copyBpmn;
import static net.jakobarndt.bpmnbacklink.core.Fixtures.copyBpmnProcess;
import static net.jakobarndt.bpmnbacklink.core.Fixtures.copyDelegates;
import static net.jakobarndt.bpmnbacklink.core.Fixtures.copyKotlinDelegates;
import static net.jakobarndt.bpmnbacklink.core.Fixtures.copyKotlinDelegatesInto;
import static net.jakobarndt.bpmnbacklink.core.Fixtures.delegateFile;
import static net.jakobarndt.bpmnbacklink.core.Fixtures.delegateFileIn;
import static net.jakobarndt.bpmnbacklink.core.Fixtures.kotlinSourceRoot;
import static net.jakobarndt.bpmnbacklink.core.Fixtures.read;
import static net.jakobarndt.bpmnbacklink.core.Fixtures.sourceRoot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacklinkProcessorTest {

    private BacklinkConfig config(Path root, Path resources, Mode mode) {
        return BacklinkConfig.builder()
            .sourceDirectories(List.of(sourceRoot(root)))
            .bpmnDirectory(bpmnProcessesDir(root))
            .bpmnReferenceRoot(resources)
            .mode(mode)
            .build();
    }

    @Test
    void updateAddsSingleAndMultiValueAnnotations(@TempDir Path root) {
        Path resources = copyBpmn(root);
        copyDelegates(root,
            "OrderDelegate", "OrderDelegate.java",
            "PaymentDelegate", "PaymentDelegate.java",
            "ShippingDelegate", "ShippingDelegate.java");

        BacklinkResult result = new BacklinkProcessor(config(root, resources, Mode.UPDATE)).run();

        assertEquals(3, result.updated());
        assertEquals(0, result.removed());
        assertEquals(0, result.unchanged());
        assertTrue(result.drift().isEmpty());

        String order = read(delegateFile(root, "OrderDelegate.java"));
        assertTrue(order.contains("@CalledFrom({"), "order delegate has two references -> multi:\n" + order);

        String payment = read(delegateFile(root, "PaymentDelegate.java"));
        assertTrue(payment.contains("@CalledFrom(\"bpmn/processes/order.bpmn\")"),
            "payment delegate has one reference -> single:\n" + payment);

        String shipping = read(delegateFile(root, "ShippingDelegate.java"));
        assertTrue(shipping.contains("@CalledFrom(\"bpmn/processes/sub/shipping.bpmn\")"),
            "shipping delegate single value:\n" + shipping);
    }

    @Test
    void secondUpdateRunIsIdempotent(@TempDir Path root) {
        Path resources = copyBpmn(root);
        copyDelegates(root,
            "OrderDelegate", "OrderDelegate.java",
            "PaymentDelegate", "PaymentDelegate.java",
            "ShippingDelegate", "ShippingDelegate.java");

        new BacklinkProcessor(config(root, resources, Mode.UPDATE)).run();
        String orderAfterFirst = read(delegateFile(root, "OrderDelegate.java"));

        BacklinkResult second = new BacklinkProcessor(config(root, resources, Mode.UPDATE)).run();

        assertEquals(0, second.updated());
        assertEquals(0, second.removed());
        assertEquals(3, second.unchanged());
        assertEquals(orderAfterFirst, read(delegateFile(root, "OrderDelegate.java")),
            "second run must not change the file byte for byte");
    }

    @Test
    void alreadyCorrectlyAnnotatedDelegateIsUnchangedWithoutRewrite(@TempDir Path root) {
        Path resources = copyBpmn(root);
        copyDelegates(root, "PreAnnotatedOrderDelegate", "OrderDelegate.java");
        Path file = delegateFile(root, "OrderDelegate.java");
        String before = read(file);

        BacklinkResult result = new BacklinkProcessor(config(root, resources, Mode.UPDATE)).run();

        assertEquals(0, result.updated());
        assertEquals(1, result.unchanged());
        assertEquals(before, read(file), "a correct annotation, even if multi-line, must be left untouched");
    }

    @Test
    void updateRemovesObsoleteAnnotation(@TempDir Path root) {
        Path resources = copyBpmn(root);
        copyDelegates(root, "ObsoleteDelegate", "ObsoleteDelegate.java");

        BacklinkResult result = new BacklinkProcessor(config(root, resources, Mode.UPDATE)).run();

        assertEquals(1, result.removed());
        assertEquals(0, result.updated());
        String content = read(delegateFile(root, "ObsoleteDelegate.java"));
        assertFalse(content.contains("@CalledFrom"));
        assertFalse(content.contains("net.jakobarndt.bpmnbacklink.annotation.CalledFrom"));
    }

    @Test
    void checkModeReportsDriftAndNeverWrites(@TempDir Path root) {
        Path resources = copyBpmn(root);
        copyDelegates(root, "OrderDelegate", "OrderDelegate.java");
        Path file = delegateFile(root, "OrderDelegate.java");
        String before = read(file);

        BacklinkResult result = new BacklinkProcessor(config(root, resources, Mode.CHECK)).run();

        assertEquals(before, read(file), "CHECK mode must never write");
        assertEquals(1, result.drift().size());
        BacklinkResult.Drift drift = result.drift().get(0);
        assertEquals(file, drift.javaFile());
        assertEquals(List.of("bpmn/processes/order.bpmn", "bpmn/processes/sub/shipping.bpmn"), drift.expected());
        assertTrue(drift.actual().isEmpty(), "no annotation present yet");
    }

    @Test
    void checkModeReportsNoDriftWhenAlreadyCorrect(@TempDir Path root) {
        Path resources = copyBpmn(root);
        copyDelegates(root, "PreAnnotatedOrderDelegate", "OrderDelegate.java");

        BacklinkResult result = new BacklinkProcessor(config(root, resources, Mode.CHECK)).run();

        assertTrue(result.drift().isEmpty(), "correct annotation -> no drift");
        assertEquals(1, result.unchanged());
    }

    @Test
    void checkModeReportsRemovalDriftForObsoleteAnnotation(@TempDir Path root) {
        Path resources = copyBpmn(root);
        copyDelegates(root, "ObsoleteDelegate", "ObsoleteDelegate.java");

        BacklinkResult result = new BacklinkProcessor(config(root, resources, Mode.CHECK)).run();

        assertEquals(1, result.drift().size());
        BacklinkResult.Drift drift = result.drift().get(0);
        assertTrue(drift.expected().isEmpty(), "no process references it -> expected empty");
        assertEquals(List.of("bpmn/processes/gone.bpmn"), drift.actual());
    }

    @Test
    void delegateReferencedByNoProcessIsTreatedAsRemoval(@TempDir Path root) {
        // ObsoleteDelegate carries @CalledFrom but no process references it,
        // so index.get(reference) returns null -> expectedFor yields empty.
        Path resources = copyBpmn(root);
        copyDelegates(root, "ObsoleteDelegate", "ObsoleteDelegate.java");

        BacklinkResult result = new BacklinkProcessor(config(root, resources, Mode.CHECK)).run();

        assertEquals(1, result.drift().size());
        assertTrue(result.drift().get(0).expected().isEmpty(),
            "an unreferenced delegate must expect an empty annotation");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runWrapsIoFailureAsUncheckedIoException(@TempDir Path root) throws IOException {
        Path resources = copyBpmn(root);
        copyDelegates(root, "OrderDelegate", "OrderDelegate.java");

        // Make one BPMN file unreadable so the indexer hits an IOException.
        Path bpmnFile = bpmnProcessesDir(root).resolve("order.bpmn");
        Files.setPosixFilePermissions(bpmnFile, PosixFilePermissions.fromString("---------"));
        // Skip on environments (e.g. running as root) where permissions are ignored.
        org.junit.jupiter.api.Assumptions.assumeFalse(Files.isReadable(bpmnFile),
            "test requires that file permissions are enforced");

        BacklinkProcessor processor = new BacklinkProcessor(config(root, resources, Mode.UPDATE));

        UncheckedIOException ex = assertThrows(UncheckedIOException.class, processor::run);
        assertEquals("Backlink run failed", ex.getMessage());
        assertTrue(ex.getCause() instanceof IOException, "cause must be the underlying IOException");

        // Restore permissions so the @TempDir cleanup can delete the file.
        Files.setPosixFilePermissions(bpmnFile, PosixFilePermissions.fromString("rw-r--r--"));
    }

    @Test
    void updateAnnotatesEveryTopLevelDelegateOfASharedFile(@TempDir Path root) {
        Path resources = copyBpmn(root);
        copyDelegates(root, "TwoDelegatesInOneFile", "Delegates.java");

        BacklinkResult result = new BacklinkProcessor(config(root, resources, Mode.UPDATE)).run();

        assertEquals(2, result.updated(), "both delegates of the file count as updated");
        assertEquals(0, result.removed());
        assertEquals(0, result.unchanged());

        String content = read(delegateFile(root, "Delegates.java"));
        int paymentAnnotation = content.indexOf("@CalledFrom(\"bpmn/processes/order.bpmn\")");
        int paymentClass = content.indexOf("class PaymentDelegate");
        int shippingAnnotation = content.indexOf("@CalledFrom(\"bpmn/processes/sub/shipping.bpmn\")");
        int shippingClass = content.indexOf("class ShippingDelegate");
        assertTrue(paymentAnnotation >= 0 && paymentAnnotation < paymentClass,
            "payment delegate must carry its own annotation:\n" + content);
        assertTrue(paymentClass < shippingAnnotation && shippingAnnotation < shippingClass,
            "shipping delegate must carry its own annotation:\n" + content);
    }

    @Test
    void secondRunOverASharedFileReportsBothDelegatesAsUnchanged(@TempDir Path root) {
        Path resources = copyBpmn(root);
        copyDelegates(root, "TwoDelegatesInOneFile", "Delegates.java");

        new BacklinkProcessor(config(root, resources, Mode.UPDATE)).run();
        String afterFirst = read(delegateFile(root, "Delegates.java"));
        BacklinkResult second = new BacklinkProcessor(config(root, resources, Mode.UPDATE)).run();

        assertEquals(0, second.updated());
        assertEquals(2, second.unchanged(), "neither delegate may overwrite the other");
        assertEquals(afterFirst, read(delegateFile(root, "Delegates.java")),
            "a second run must not change the file byte for byte");
    }

    @Test
    void updateAnnotatesTheNestedDelegateInsteadOfItsEnclosingType(@TempDir Path root) {
        Path resources = copyBpmn(root);
        copyDelegates(root, "NestedShippingDelegate", "DelegateHolder.java");

        BacklinkResult result = new BacklinkProcessor(config(root, resources, Mode.UPDATE)).run();

        assertEquals(1, result.updated());
        String content = read(delegateFile(root, "DelegateHolder.java"));
        int holderIndex = content.indexOf("class DelegateHolder");
        int annotationIndex = content.indexOf("@CalledFrom(\"bpmn/processes/sub/shipping.bpmn\")");
        int nestedIndex = content.indexOf("class ShippingDelegate");
        assertTrue(holderIndex < annotationIndex,
            "annotation must not land on the enclosing type:\n" + content);
        assertTrue(annotationIndex < nestedIndex,
            "annotation must precede the nested delegate:\n" + content);
    }

    @Test
    void updateThenCheckYieldsNoDrift(@TempDir Path root) {
        Path resources = copyBpmn(root);
        copyDelegates(root,
            "OrderDelegate", "OrderDelegate.java",
            "PaymentDelegate", "PaymentDelegate.java",
            "ShippingDelegate", "ShippingDelegate.java");

        new BacklinkProcessor(config(root, resources, Mode.UPDATE)).run();
        BacklinkResult check = new BacklinkProcessor(config(root, resources, Mode.CHECK)).run();

        assertTrue(check.drift().isEmpty(), "after UPDATE a CHECK must be clean");
        assertEquals(3, check.unchanged());
    }

    @Test
    void updateAnnotatesJavaAndKotlinDelegatesOfOneTree(@TempDir Path root) {
        Path resources = copyBpmn(root);
        copyBpmnProcess(root, "kotlin.bpmn");
        copyDelegates(root, "OrderDelegate", "OrderDelegate.java");
        copyKotlinDelegates(root,
            "KotlinOrderDelegate", "KotlinOrderDelegate.kt",
            "KotlinShippingDelegate", "KotlinShippingDelegate.kt");

        BacklinkResult result = new BacklinkProcessor(config(root, resources, Mode.UPDATE)).run();

        assertEquals(3, result.updated(), "both languages must be written in the same run");
        String java = read(delegateFile(root, "OrderDelegate.java"));
        assertTrue(java.contains("@CalledFrom({"), "the Java delegate keeps the Java array form:\n" + java);

        String kotlin = read(delegateFile(root, "KotlinOrderDelegate.kt"));
        assertTrue(kotlin.contains("@CalledFrom(\"bpmn/processes/kotlin.bpmn\")"),
            "the Kotlin delegate gets the Kotlin vararg form:\n" + kotlin);
        assertTrue(kotlin.contains("import net.jakobarndt.bpmnbacklink.annotation.CalledFrom\n"),
            "a Kotlin import carries no semicolon:\n" + kotlin);

        String shipping = read(delegateFile(root, "KotlinShippingDelegate.kt"));
        assertTrue(shipping.contains("@CalledFrom(\"bpmn/processes/kotlin.bpmn\")\nopen class KotlinShippingDelegate"),
            "a camunda:class reference resolves to the Kotlin type too:\n" + shipping);
    }

    @Test
    void secondRunOverAMixedTreeIsIdempotentAndDriftFree(@TempDir Path root) {
        Path resources = copyBpmn(root);
        copyBpmnProcess(root, "kotlin.bpmn");
        copyDelegates(root, "OrderDelegate", "OrderDelegate.java");
        copyKotlinDelegates(root, "KotlinOrderDelegate", "KotlinOrderDelegate.kt");

        new BacklinkProcessor(config(root, resources, Mode.UPDATE)).run();
        String afterFirst = read(delegateFile(root, "KotlinOrderDelegate.kt"));
        BacklinkResult second = new BacklinkProcessor(config(root, resources, Mode.UPDATE)).run();
        BacklinkResult check = new BacklinkProcessor(config(root, resources, Mode.CHECK)).run();

        assertEquals(0, second.updated());
        assertEquals(2, second.unchanged());
        assertEquals(afterFirst, read(delegateFile(root, "KotlinOrderDelegate.kt")),
            "a second run must not change the Kotlin file byte for byte");
        assertTrue(check.drift().isEmpty(), "the vararg form must not be reported as drift");
    }

    @Test
    void checkReportsDriftOfAKotlinDelegateWithoutWriting(@TempDir Path root) {
        Path resources = copyBpmn(root);
        copyBpmnProcess(root, "kotlin.bpmn");
        copyKotlinDelegates(root, "KotlinOrderDelegate", "KotlinOrderDelegate.kt");
        String before = read(delegateFile(root, "KotlinOrderDelegate.kt"));

        BacklinkResult result = new BacklinkProcessor(config(root, resources, Mode.CHECK)).run();

        assertEquals(1, result.drift().size());
        assertEquals(List.of("bpmn/processes/kotlin.bpmn"), result.drift().get(0).expected());
        assertEquals(List.of(), result.drift().get(0).actual());
        assertEquals(before, read(delegateFile(root, "KotlinOrderDelegate.kt")),
            "CHECK must never write a Kotlin file");
    }

    @Test
    void updateRemovesAnObsoleteKotlinAnnotation(@TempDir Path root) {
        Path resources = copyBpmn(root);
        copyKotlinDelegates(root, "KotlinPreAnnotatedOrderDelegate", "KotlinOrderDelegate.kt");

        BacklinkResult result = new BacklinkProcessor(config(root, resources, Mode.UPDATE)).run();

        assertEquals(1, result.removed(), "no BPMN references the Kotlin delegate any more");
        String content = read(delegateFile(root, "KotlinOrderDelegate.kt"));
        assertFalse(content.contains("CalledFrom"), "annotation and import must be gone:\n" + content);
    }

    @Test
    void updateAnnotatesDelegatesOfSeparateJavaAndKotlinRoots(@TempDir Path root) {
        // The layout of a mixed module: Java below src/main/java, Kotlin below
        // src/main/kotlin, both registered as source roots.
        Path resources = copyBpmn(root);
        copyBpmnProcess(root, "kotlin.bpmn");
        copyDelegates(root, "OrderDelegate", "OrderDelegate.java");
        copyKotlinDelegatesInto(kotlinSourceRoot(root), "KotlinOrderDelegate", "KotlinOrderDelegate.kt");

        BacklinkResult result = new BacklinkProcessor(BacklinkConfig.builder()
            .sourceDirectories(List.of(sourceRoot(root), kotlinSourceRoot(root)))
            .bpmnDirectory(bpmnProcessesDir(root))
            .bpmnReferenceRoot(resources)
            .mode(Mode.UPDATE)
            .build()).run();

        assertEquals(2, result.updated(), "both roots must be written in the same run");
        assertTrue(read(delegateFile(root, "OrderDelegate.java")).contains("@CalledFrom({"),
            "the delegate of the Java root keeps the Java array form");
        String kotlin = read(delegateFileIn(kotlinSourceRoot(root), "KotlinOrderDelegate.kt"));
        assertTrue(kotlin.contains("@CalledFrom(\"bpmn/processes/kotlin.bpmn\")"),
            "the delegate of the Kotlin root is annotated too:\n" + kotlin);
    }

    @Test
    void aRootListedUnderTwoSpellingsIsVisitedOnce(@TempDir Path root) {
        Path resources = copyBpmn(root);
        copyDelegates(root, "PaymentDelegate", "PaymentDelegate.java");
        Path aliasOfSourceRoot = sourceRoot(root).resolve("..").resolve("java");

        BacklinkResult result = new BacklinkProcessor(BacklinkConfig.builder()
            .sourceDirectories(List.of(sourceRoot(root), aliasOfSourceRoot))
            .bpmnDirectory(bpmnProcessesDir(root))
            .bpmnReferenceRoot(resources)
            .mode(Mode.UPDATE)
            .build()).run();

        assertEquals(1, result.updated());
        assertEquals(0, result.unchanged(), "the delegate must not be visited a second time");
    }

    @Test
    void aSourceRootThatDoesNotExistIsIgnored(@TempDir Path root) {
        Path resources = copyBpmn(root);
        copyDelegates(root, "OrderDelegate", "OrderDelegate.java");

        BacklinkResult result = new BacklinkProcessor(BacklinkConfig.builder()
            .sourceDirectories(List.of(sourceRoot(root), kotlinSourceRoot(root)))
            .bpmnDirectory(bpmnProcessesDir(root))
            .bpmnReferenceRoot(resources)
            .mode(Mode.UPDATE)
            .build()).run();

        assertEquals(1, result.updated(),
            "a Java-only module must not fail over its missing Kotlin root");
    }
}
