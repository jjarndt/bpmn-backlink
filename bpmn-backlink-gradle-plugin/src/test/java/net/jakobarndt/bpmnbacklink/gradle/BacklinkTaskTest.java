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
package net.jakobarndt.bpmnbacklink.gradle;

import net.jakobarndt.bpmnbacklink.core.BacklinkResult;
import net.jakobarndt.bpmnbacklink.core.Mode;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.tasks.VerificationException;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the tasks against a small example project laid out in a temporary
 * directory. The task actions are invoked directly, which keeps the test
 * in-process (JaCoCo-measurable) and independent of a running Gradle build.
 */
class BacklinkTaskTest {

    private static final String DELEGATE_SOURCE = String.join("\n",
            "package com.example.delegate;",
            "",
            "import org.camunda.bpm.engine.delegate.DelegateExecution;",
            "import org.camunda.bpm.engine.delegate.JavaDelegate;",
            "",
            "public class OrderDelegate implements JavaDelegate {",
            "",
            "    @Override",
            "    public void execute(DelegateExecution execution) {",
            "        execution.setVariable(\"ordered\", true);",
            "    }",
            "}",
            "");

    private static final String BPMN_SOURCE = String.join("\n",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
            "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
            "                  xmlns:camunda=\"http://camunda.org/schema/1.0/bpmn\"",
            "                  id=\"defs_order\" targetNamespace=\"http://example.com/bpmn\">",
            "  <bpmn:process id=\"orderProcess\" isExecutable=\"true\">",
            "    <bpmn:serviceTask id=\"placeOrder\" name=\"Place order\"",
            "                      camunda:delegateExpression=\"${orderDelegate}\"/>",
            "  </bpmn:process>",
            "</bpmn:definitions>",
            "");

    private static final String EXPECTED_ANNOTATION = "@CalledFrom(\"bpmn/processes/order.bpmn\")";
    private static final String EXPECTED_IMPORT = "import net.jakobarndt.bpmnbacklink.annotation.CalledFrom;";

    @Test
    void updateWritesAnnotationThenCheckIsClean(@TempDir Path projectDir) throws Exception {
        Path delegateFile = writeExampleProject(projectDir);
        Project project = projectWithPlugin(projectDir);

        updateTask(project).runBacklink();

        String afterUpdate = Files.readString(delegateFile, StandardCharsets.UTF_8);
        assertTrue(afterUpdate.contains(EXPECTED_ANNOTATION),
                "update must add the @CalledFrom annotation; got:\n" + afterUpdate);
        assertTrue(afterUpdate.contains(EXPECTED_IMPORT),
                "update must add the annotation import; got:\n" + afterUpdate);

        // A second update must be a no-op (idempotence): content stays identical.
        updateTask(projectWithPlugin(projectDir)).runBacklink();
        assertEquals(afterUpdate, Files.readString(delegateFile, StandardCharsets.UTF_8),
                "a second update must not change the file");

        // Check must now be clean and must not throw with failOnDrift=true.
        CheckCalledFromTask check = checkTask(project);
        check.getFailOnDrift().set(true);
        check.runBacklink();

        assertEquals(afterUpdate, Files.readString(delegateFile, StandardCharsets.UTF_8),
                "check must never modify a source file");
    }

    @Test
    void checkFailsOnDriftWhenAnnotationMissing(@TempDir Path projectDir) throws Exception {
        writeExampleProject(projectDir);

        CheckCalledFromTask check = checkTask(projectWithPlugin(projectDir));
        check.getFailOnDrift().set(true);

        VerificationException thrown = assertThrows(VerificationException.class, check::runBacklink,
                "check must fail when a delegate lacks its expected annotation");
        assertEquals("bpmn-backlink: detected 1 @CalledFrom drift(s); "
                        + "run './gradlew bpmnBacklinkUpdate' to fix.", thrown.getMessage(),
                "the failure message must point at the fixing task");
    }

    @Test
    void checkWarnsButPassesWhenFailOnDriftIsFalse(@TempDir Path projectDir) throws Exception {
        Path delegateFile = writeExampleProject(projectDir);

        CheckCalledFromTask check = checkTask(projectWithPlugin(projectDir));
        check.getFailOnDrift().set(false);
        check.runBacklink();

        assertFalse(Files.readString(delegateFile, StandardCharsets.UTF_8).contains(EXPECTED_ANNOTATION),
                "check must not write the annotation even when drift is tolerated");
    }

    @Test
    void skipShortCircuitsTheTask(@TempDir Path projectDir) throws Exception {
        Path delegateFile = writeExampleProject(projectDir);
        String before = Files.readString(delegateFile, StandardCharsets.UTF_8);

        UpdateCalledFromTask update = updateTask(projectWithPlugin(projectDir));
        update.getSkip().set(true);
        update.runBacklink();

        assertEquals(before, Files.readString(delegateFile, StandardCharsets.UTF_8),
                "skip=true must leave the file untouched");
    }

    @Test
    void malformedBpmnBecomesGradleException(@TempDir Path projectDir) throws Exception {
        // A malformed BPMN file makes the Camunda model API throw a ModelParseException
        // (a RuntimeException). The indexer catches it and rethrows a named IOException so
        // the offending file is reported, which the task surfaces through the I/O-failure branch.
        writeExampleProject(projectDir);
        Path bpmnDir = projectDir.resolve("src/main/resources/bpmn/processes");
        Files.writeString(bpmnDir.resolve("broken.bpmn"),
                "<?xml version=\"1.0\"?><not-bpmn>oops</not-bpmn>", StandardCharsets.UTF_8);

        UpdateCalledFromTask update = updateTask(projectWithPlugin(projectDir));

        GradleException thrown = assertThrows(GradleException.class, update::runBacklink,
                "a malformed BPMN file must surface as a GradleException");
        assertEquals("bpmn-backlink failed to read or write a file", thrown.getMessage(),
                "a malformed BPMN file is reported through the I/O-failure branch");
        assertInstanceOf(IOException.class, thrown.getCause(),
                "the indexer wraps the parse failure as an IOException carrying the original cause");
    }

    @Test
    void ioFailureWhileWritingBecomesGradleException(@TempDir Path projectDir) throws Exception {
        // The update mode writes the delegate source. Making that file read-only forces
        // an IOException inside the processor, surfaced as an UncheckedIOException, which
        // the task must map to a GradleException whose cause is the underlying IOException.
        Path delegateFile = writeExampleProject(projectDir);
        Assumptions.assumeTrue(Files.getFileStore(delegateFile).supportsFileAttributeView("posix"),
                "test requires POSIX file permissions to make a file read-only");
        Files.setPosixFilePermissions(delegateFile, Set.of(PosixFilePermission.OWNER_READ));

        UpdateCalledFromTask update = updateTask(projectWithPlugin(projectDir));
        try {
            GradleException thrown = assertThrows(GradleException.class, update::runBacklink,
                    "an unwritable delegate file must surface as a GradleException");
            assertEquals("bpmn-backlink failed to read or write a file", thrown.getMessage(),
                    "the I/O-failure branch must use the read-or-write message");
            assertInstanceOf(IOException.class, thrown.getCause(),
                    "the UncheckedIOException cause (the original IOException) must be unwrapped");
        } finally {
            // Restore write permission so @TempDir cleanup can delete the file.
            Files.setPosixFilePermissions(delegateFile,
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        }
    }

    @Test
    void defaultHandleResultLogsSummaryWithoutFailing(@TempDir Path projectDir) {
        // The base class provides a default handleResult that only logs the summary.
        // Both shipped tasks override it, so it is exercised here through a minimal
        // subclass that keeps the inherited behaviour.
        Project project = projectWithPlugin(projectDir);
        DefaultHandlingTask task = project.getTasks()
                .register("defaultHandling", DefaultHandlingTask.class).get();
        BacklinkResult result = new BacklinkResult(1, 2, 3, List.of());

        // Must not throw: the default reaction never turns a result into a failure.
        task.invokeHandleResult(result);

        assertEquals(Mode.UPDATE, task.mode(), "the test subclass reports its declared mode");
    }

    /**
     * Minimal concrete task that does NOT override {@link AbstractBacklinkTask#handleResult}
     * so the inherited default (summary logging only) can be exercised.
     */
    public abstract static class DefaultHandlingTask extends AbstractBacklinkTask {
        @Override
        protected Mode mode() {
            return Mode.UPDATE;
        }

        void invokeHandleResult(BacklinkResult result) {
            handleResult(result);
        }
    }

    private Path writeExampleProject(Path projectDir) throws IOException {
        Path sourceDir = projectDir.resolve("src/main/java/com/example/delegate");
        Files.createDirectories(sourceDir);
        Path delegateFile = sourceDir.resolve("OrderDelegate.java");
        Files.writeString(delegateFile, DELEGATE_SOURCE, StandardCharsets.UTF_8);

        Path bpmnDir = projectDir.resolve("src/main/resources/bpmn/processes");
        Files.createDirectories(bpmnDir);
        Files.writeString(bpmnDir.resolve("order.bpmn"), BPMN_SOURCE, StandardCharsets.UTF_8);

        return delegateFile;
    }

    private static Project projectWithPlugin(Path projectDir) {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        project.getPluginManager().apply(BpmnBacklinkPlugin.class);
        return project;
    }

    private static UpdateCalledFromTask updateTask(Project project) {
        return (UpdateCalledFromTask) project.getTasks()
                .getByName(BpmnBacklinkPlugin.UPDATE_TASK_NAME);
    }

    private static CheckCalledFromTask checkTask(Project project) {
        return (CheckCalledFromTask) project.getTasks()
                .getByName(BpmnBacklinkPlugin.CHECK_TASK_NAME);
    }
}
