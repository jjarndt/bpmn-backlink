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
package net.jakobarndt.bpmnbacklink.maven;

import net.jakobarndt.bpmnbacklink.core.BacklinkResult;
import net.jakobarndt.bpmnbacklink.core.Mode;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the goals against a small example project laid out in a temporary
 * directory. The mojos are instantiated directly and configured by reflection,
 * which keeps the test self-contained and independent of a running Maven build.
 */
class BacklinkMojoTest {

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

        UpdateCalledFromMojo update = new UpdateCalledFromMojo();
        configureCommon(update, projectDir);
        update.execute();

        String afterUpdate = Files.readString(delegateFile, StandardCharsets.UTF_8);
        assertTrue(afterUpdate.contains(EXPECTED_ANNOTATION),
                "update must add the @CalledFrom annotation; got:\n" + afterUpdate);
        assertTrue(afterUpdate.contains(EXPECTED_IMPORT),
                "update must add the annotation import; got:\n" + afterUpdate);

        // A second update must be a no-op (idempotence): content stays identical.
        UpdateCalledFromMojo updateAgain = new UpdateCalledFromMojo();
        configureCommon(updateAgain, projectDir);
        updateAgain.execute();
        assertEquals(afterUpdate, Files.readString(delegateFile, StandardCharsets.UTF_8),
                "a second update must not change the file");

        // Check must now be clean and must not throw with failOnDrift=true.
        CheckCalledFromMojo check = new CheckCalledFromMojo();
        configureCommon(check, projectDir);
        setField(check, "failOnDrift", true);
        check.execute();

        assertEquals(afterUpdate, Files.readString(delegateFile, StandardCharsets.UTF_8),
                "check must never modify a source file");
    }

    @Test
    void checkFailsOnDriftWhenAnnotationMissing(@TempDir Path projectDir) throws Exception {
        writeExampleProject(projectDir);

        CheckCalledFromMojo check = new CheckCalledFromMojo();
        configureCommon(check, projectDir);
        setField(check, "failOnDrift", true);

        assertThrows(MojoFailureException.class, check::execute,
                "check must fail when a delegate lacks its expected annotation");
    }

    @Test
    void checkWarnsButPassesWhenFailOnDriftIsFalse(@TempDir Path projectDir) throws Exception {
        Path delegateFile = writeExampleProject(projectDir);

        CheckCalledFromMojo check = new CheckCalledFromMojo();
        configureCommon(check, projectDir);
        setField(check, "failOnDrift", false);
        check.execute();

        assertFalse(Files.readString(delegateFile, StandardCharsets.UTF_8).contains(EXPECTED_ANNOTATION),
                "check must not write the annotation even when drift is tolerated");
    }

    @Test
    void skipShortCircuitsTheGoal(@TempDir Path projectDir) throws Exception {
        Path delegateFile = writeExampleProject(projectDir);
        String before = Files.readString(delegateFile, StandardCharsets.UTF_8);

        UpdateCalledFromMojo update = new UpdateCalledFromMojo();
        configureCommon(update, projectDir);
        setField(update, "skip", true);
        update.execute();

        assertEquals(before, Files.readString(delegateFile, StandardCharsets.UTF_8),
                "skip=true must leave the file untouched");
    }

    @Test
    void malformedBpmnBecomesMojoExecutionException(@TempDir Path projectDir) throws Exception {
        // A malformed BPMN file makes the Camunda model API throw a ModelParseException
        // (a RuntimeException). The indexer catches it and rethrows a named IOException so
        // the offending file is reported, which the mojo surfaces through the I/O-failure branch.
        writeExampleProject(projectDir);
        Path bpmnDir = projectDir.resolve("src/main/resources/bpmn/processes");
        Files.writeString(bpmnDir.resolve("broken.bpmn"),
                "<?xml version=\"1.0\"?><not-bpmn>oops</not-bpmn>", StandardCharsets.UTF_8);

        UpdateCalledFromMojo update = new UpdateCalledFromMojo();
        configureCommon(update, projectDir);

        MojoExecutionException thrown = assertThrows(MojoExecutionException.class, update::execute,
                "a malformed BPMN file must surface as a MojoExecutionException");
        assertEquals("bpmn-backlink failed to read or write a file", thrown.getMessage(),
                "a malformed BPMN file is reported through the I/O-failure branch");
        assertInstanceOf(IOException.class, thrown.getCause(),
                "the indexer wraps the parse failure as an IOException carrying the original cause");
    }

    @Test
    void ioFailureWhileWritingBecomesMojoExecutionException(@TempDir Path projectDir) throws Exception {
        // The update mode writes the delegate source. Making that file read-only forces
        // an IOException inside the processor, surfaced as an UncheckedIOException, which
        // the mojo must map to a MojoExecutionException whose cause is the underlying IOException.
        Path delegateFile = writeExampleProject(projectDir);
        Assumptions.assumeTrue(Files.getFileStore(delegateFile).supportsFileAttributeView("posix"),
                "test requires POSIX file permissions to make a file read-only");
        Files.setPosixFilePermissions(delegateFile, Set.of(PosixFilePermission.OWNER_READ));

        UpdateCalledFromMojo update = new UpdateCalledFromMojo();
        configureCommon(update, projectDir);
        try {
            MojoExecutionException thrown = assertThrows(MojoExecutionException.class, update::execute,
                    "an unwritable delegate file must surface as a MojoExecutionException");
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
    void defaultHandleResultLogsSummaryWithoutFailing() throws Exception {
        // The base class provides a default handleResult that only logs the summary.
        // Both shipped goals override it, so it is exercised here through a minimal
        // subclass that keeps the inherited behaviour.
        DefaultHandlingMojo mojo = new DefaultHandlingMojo();
        BacklinkResult result = new BacklinkResult(1, 2, 3, List.of());

        // Must not throw: the default reaction never turns a result into a failure.
        mojo.invokeHandleResult(result);

        assertEquals(Mode.UPDATE, mojo.mode(), "the test subclass reports its declared mode");
    }

    /**
     * Minimal concrete goal that does NOT override {@link AbstractBacklinkMojo#handleResult}
     * so the inherited default (summary logging only) can be exercised.
     */
    private static final class DefaultHandlingMojo extends AbstractBacklinkMojo {
        @Override
        protected Mode mode() {
            return Mode.UPDATE;
        }

        void invokeHandleResult(BacklinkResult result) throws MojoFailureException {
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

    private void configureCommon(AbstractBacklinkMojo mojo, Path projectDir) {
        setField(mojo, "sourceDirectory", projectDir.resolve("src/main/java").toFile());
        setField(mojo, "bpmnDirectory", projectDir.resolve("src/main/resources/bpmn/processes").toFile());
        setField(mojo, "bpmnReferenceRoot", projectDir.resolve("src/main/resources").toFile());
        setField(mojo, "skip", false);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = findField(target.getClass(), name);
            field.setAccessible(true);
            if (value instanceof File file) {
                field.set(target, file);
            } else if (value instanceof Boolean bool) {
                field.setBoolean(target, bool);
            } else {
                field.set(target, value);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set field '" + name + "'", e);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
