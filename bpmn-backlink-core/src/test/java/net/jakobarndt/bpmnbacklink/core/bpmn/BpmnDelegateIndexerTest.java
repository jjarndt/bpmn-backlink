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
package net.jakobarndt.bpmnbacklink.core.bpmn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BpmnDelegateIndexerTest {

    private Path copyBpmnFixtures(Path root) {
        Path resources = root.resolve("src/main/resources");
        copy("bpmn/processes/order.bpmn", resources.resolve("bpmn/processes/order.bpmn"));
        copy("bpmn/processes/sub/shipping.bpmn", resources.resolve("bpmn/processes/sub/shipping.bpmn"));
        return resources;
    }

    /** Copies a single edge-case fixture into {@code dir} under its bare file name. */
    private Path copyEdgeFixture(Path dir, String fileName) {
        Path target = dir.resolve(fileName);
        copy("bpmn/edge/" + fileName, target);
        return target;
    }

    private void copy(String resource, Path target) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            Files.createDirectories(target.getParent());
            Files.write(target, in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void indexesDelegateExpressionAndCamundaClass(@TempDir Path root) throws IOException {
        Path resources = copyBpmnFixtures(root);
        Path processes = resources.resolve("bpmn/processes");

        Map<String, SortedSet<String>> index = new BpmnDelegateIndexer(processes, resources).index();

        assertTrue(index.containsKey("orderDelegate"), "delegateExpression bean must be indexed");
        assertTrue(index.containsKey("paymentDelegate"), "camunda:class must be indexed as camelCase simple name");
        assertTrue(index.containsKey("shippingDelegate"));
    }

    @Test
    void aggregatesMultipleReferencesSortedWithForwardSlashes(@TempDir Path root) throws IOException {
        Path resources = copyBpmnFixtures(root);
        Path processes = resources.resolve("bpmn/processes");

        Map<String, SortedSet<String>> index = new BpmnDelegateIndexer(processes, resources).index();

        assertEquals(
            List.of("bpmn/processes/order.bpmn", "bpmn/processes/sub/shipping.bpmn"),
            List.copyOf(index.get("orderDelegate")));
    }

    @Test
    void relativizesCamundaClassReferenceToReferenceRoot(@TempDir Path root) throws IOException {
        Path resources = copyBpmnFixtures(root);
        Path processes = resources.resolve("bpmn/processes");

        Map<String, SortedSet<String>> index = new BpmnDelegateIndexer(processes, resources).index();

        assertEquals(List.of("bpmn/processes/order.bpmn"), List.copyOf(index.get("paymentDelegate")));
    }

    @Test
    void emptyDirectoryYieldsEmptyIndex(@TempDir Path root) throws IOException {
        Path empty = root.resolve("none");
        Map<String, SortedSet<String>> index = new BpmnDelegateIndexer(empty, root).index();
        assertTrue(index.isEmpty());
    }

    @Test
    void missingDirectoryYieldsEmptyIndex(@TempDir Path root) throws IOException {
        // bpmnDirectory points at a regular file, not a directory.
        Path resources = copyBpmnFixtures(root);
        Path notADir = resources.resolve("bpmn/processes/order.bpmn");

        Map<String, SortedSet<String>> index = new BpmnDelegateIndexer(notADir, resources).index();

        assertTrue(index.isEmpty(), "a non-directory path must yield an empty index");
    }

    @Test
    void ignoresBlankAndUnresolvableDelegateAttributes(@TempDir Path root) throws IOException {
        Path resources = root.resolve("src/main/resources");
        copy("bpmn/processes/edgecases.bpmn", resources.resolve("bpmn/processes/edgecases.bpmn"));
        Path processes = resources.resolve("bpmn/processes");

        Map<String, SortedSet<String>> index = new BpmnDelegateIndexer(processes, resources).index();

        // ${} -> blank bean name, "   " -> blank class, "." -> decapitalizes to blank,
        // and a task with neither attribute. None of these may produce an index entry.
        assertTrue(index.isEmpty(),
            "blank or unresolvable delegate attributes must not be indexed, was: " + index);
        assertFalse(index.containsKey(""), "an empty reference must never be indexed");
    }

    @Test
    void indexesBothDelegateAndHashPrefixedExpressionsAndPlainBeanNames(@TempDir Path root) throws IOException {
        copyEdgeFixture(root, "prefixes.bpmn");

        Map<String, SortedSet<String>> index = new BpmnDelegateIndexer(root, root).index();

        // ${dollarBean}, #{hashBean} and a wrapper-less plainBean all resolve to
        // their bare bean name.
        assertEquals(List.of("prefixes.bpmn"), List.copyOf(index.get("dollarBean")));
        assertEquals(List.of("prefixes.bpmn"), List.copyOf(index.get("hashBean")));
        assertEquals(List.of("prefixes.bpmn"), List.copyOf(index.get("plainBean")));
    }

    @Test
    void doesNotIndexPlainCamundaExpression(@TempDir Path root) throws IOException {
        copyEdgeFixture(root, "expression-only.bpmn");

        Map<String, SortedSet<String>> index = new BpmnDelegateIndexer(root, root).index();

        // camunda:expression is a value expression, not a delegate reference.
        assertTrue(index.isEmpty(), "camunda:expression must never be indexed, was: " + index);
    }

    @Test
    void trimsAndCamelCasesCamundaClassVariants(@TempDir Path root) throws IOException {
        copyEdgeFixture(root, "classvariants.bpmn");

        Map<String, SortedSet<String>> index = new BpmnDelegateIndexer(root, root).index();

        // Surrounding whitespace is stripped; nested and default-package class
        // names both reduce to the camelCased simple name.
        assertEquals(List.of("classvariants.bpmn"), List.copyOf(index.get("spacedDelegate")));
        assertEquals(List.of("classvariants.bpmn"), List.copyOf(index.get("nestedDelegate")));
        assertEquals(List.of("classvariants.bpmn"), List.copyOf(index.get("defaultPackageDelegate")));
    }

    @Test
    void mergesSameDelegateReferencedViaExpressionAndClassWithinOneFile(@TempDir Path root) throws IOException {
        copyEdgeFixture(root, "sametask.bpmn");

        Map<String, SortedSet<String>> index = new BpmnDelegateIndexer(root, root).index();

        // The same delegate is referenced once via delegateExpression and once
        // via camunda:class. Both map to "sharedDelegate" and the file path is
        // deduplicated by the backing TreeSet.
        assertEquals(List.of("sametask.bpmn"), List.copyOf(index.get("sharedDelegate")));
    }

    @Test
    void indexesEveryProcessInACollaborationFile(@TempDir Path root) throws IOException {
        copyEdgeFixture(root, "multiprocess.bpmn");

        Map<String, SortedSet<String>> index = new BpmnDelegateIndexer(root, root).index();

        // Both processes of the collaboration contribute their delegates.
        assertEquals(List.of("multiprocess.bpmn"), List.copyOf(index.get("oneDelegate")));
        assertEquals(List.of("multiprocess.bpmn"), List.copyOf(index.get("twoBean")));
    }

    @Test
    void parsesFileWithUtf8ByteOrderMark(@TempDir Path root) throws IOException {
        Path bom = copyEdgeFixture(root, "bom.bpmn");
        // Guard the fixture: the first three bytes must be the UTF-8 BOM, otherwise
        // this test would silently stop exercising the BOM path.
        byte[] head = Files.readAllBytes(bom);
        assertEquals((byte) 0xEF, head[0]);
        assertEquals((byte) 0xBB, head[1]);
        assertEquals((byte) 0xBF, head[2]);

        Map<String, SortedSet<String>> index = new BpmnDelegateIndexer(root, root).index();

        assertEquals(List.of("bom.bpmn"), List.copyOf(index.get("bomBean")));
    }

    @Test
    void ignoresNonBpmnFiles(@TempDir Path root) throws IOException {
        // notes.txt contains a delegateExpression-looking string but is not a *.bpmn file.
        copyEdgeFixture(root, "notes.txt");

        Map<String, SortedSet<String>> index = new BpmnDelegateIndexer(root, root).index();

        assertTrue(index.isEmpty(), "non-.bpmn files must be ignored, was: " + index);
    }

    /**
     * Scope documentation: v1 is specified to capture only the element-to-code
     * relation on service tasks (camunda:delegateExpression / camunda:class on
     * ServiceTasks). The current implementation walks <em>every</em> element and
     * indexes the same two attributes wherever they occur. This test pins that
     * actual, broader behaviour so any future scope decision is a deliberate,
     * visible change rather than an accidental regression.
     */
    @Test
    void documentsThatIndexerCapturesDelegatesBeyondServiceTasks(@TempDir Path root) throws IOException {
        copyEdgeFixture(root, "listeners.bpmn");
        copyEdgeFixture(root, "othertasks.bpmn");

        Map<String, SortedSet<String>> index = new BpmnDelegateIndexer(root, root).index();

        // From listeners.bpmn: the service task itself plus its execution listener
        // (camunda:class) and task listener (camunda:delegateExpression).
        assertEquals(List.of("listeners.bpmn"), List.copyOf(index.get("taskDelegate")));
        assertEquals(List.of("listeners.bpmn"), List.copyOf(index.get("startListener")));
        assertEquals(List.of("listeners.bpmn"), List.copyOf(index.get("createListenerBean")));

        // From othertasks.bpmn: a send task, a business rule task and a message
        // event definition are all indexed despite not being service tasks.
        assertEquals(List.of("othertasks.bpmn"), List.copyOf(index.get("sendDelegate")));
        assertEquals(List.of("othertasks.bpmn"), List.copyOf(index.get("ruleBean")));
        assertEquals(List.of("othertasks.bpmn"), List.copyOf(index.get("messageDelegate")));

        assertEquals(
            Set.of("taskDelegate", "startListener", "createListenerBean",
                "sendDelegate", "ruleBean", "messageDelegate"),
            index.keySet());
    }

    @Test
    void failsWithFileContextOnMalformedBpmn(@TempDir Path root) {
        Path malformed = copyEdgeFixture(root, "malformed.bpmn");

        IOException failure = assertThrows(IOException.class,
            () -> new BpmnDelegateIndexer(root, root).index());

        // The offending file must be named so the user can locate it; the parse
        // exception is preserved as the cause.
        assertTrue(failure.getMessage().contains(malformed.toString()),
            "error must name the offending file, was: " + failure.getMessage());
        assertNotNull(failure.getCause(), "the underlying parse exception must be preserved");
    }
}
