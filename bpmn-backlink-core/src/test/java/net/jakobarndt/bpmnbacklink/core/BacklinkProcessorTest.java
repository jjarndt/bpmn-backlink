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
import static net.jakobarndt.bpmnbacklink.core.Fixtures.copyDelegates;
import static net.jakobarndt.bpmnbacklink.core.Fixtures.delegateFile;
import static net.jakobarndt.bpmnbacklink.core.Fixtures.read;
import static net.jakobarndt.bpmnbacklink.core.Fixtures.sourceRoot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacklinkProcessorTest {

    private BacklinkConfig config(Path root, Path resources, Mode mode) {
        return BacklinkConfig.builder()
            .sourceDirectory(sourceRoot(root))
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
}
