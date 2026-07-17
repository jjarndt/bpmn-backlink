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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the plugin's registration and wiring logic against in-memory
 * projects built with {@link ProjectBuilder}. Runs in-process so the plugin
 * code is measurable by JaCoCo.
 */
class BpmnBacklinkPluginTest {

    @Test
    void registersTasksAndExtensionWithMavenDefaults(@TempDir Path projectDir) throws Exception {
        // ProjectBuilder canonicalizes the project directory (on macOS /var is a
        // symlink to /private/var), so the expected paths must be canonical too.
        projectDir = projectDir.toRealPath();
        Project project = projectWithPlugin(projectDir);

        BpmnBacklinkExtension extension =
                project.getExtensions().getByType(BpmnBacklinkExtension.class);
        assertEquals(projectDir.resolve("src/main/java").toFile(),
                extension.getSourceDirectory().get().getAsFile(),
                "sourceDirectory must default to src/main/java");
        assertEquals(projectDir.resolve("src/main/resources/bpmn/processes").toFile(),
                extension.getBpmnDirectory().get().getAsFile(),
                "bpmnDirectory must default to src/main/resources/bpmn/processes");
        assertEquals(projectDir.resolve("src/main/resources").toFile(),
                extension.getBpmnReferenceRoot().get().getAsFile(),
                "bpmnReferenceRoot must default to src/main/resources");
        assertFalse(extension.getSkip().get(), "skip must default to false");
        assertTrue(extension.getFailOnDrift().get(), "failOnDrift must default to true");
        assertTrue(extension.getUpdateBeforeCompile().get(), "updateBeforeCompile must default to true");
        assertTrue(extension.getCheckOnCheck().get(), "checkOnCheck must default to true");

        Task update = project.getTasks().getByName(BpmnBacklinkPlugin.UPDATE_TASK_NAME);
        Task check = project.getTasks().getByName(BpmnBacklinkPlugin.CHECK_TASK_NAME);
        assertNotNull(update.getGroup(), "update task must carry a group");
        assertEquals(update.getGroup(), check.getGroup(), "both tasks share the group");
        assertNotNull(update.getDescription(), "update task must carry a description");
        assertNotNull(check.getDescription(), "check task must carry a description");
    }

    @Test
    void extensionValuesFlowIntoTasks(@TempDir Path projectDir) throws Exception {
        projectDir = projectDir.toRealPath();
        Project project = projectWithPlugin(projectDir);
        BpmnBacklinkExtension extension =
                project.getExtensions().getByType(BpmnBacklinkExtension.class);
        extension.getSourceDirectory().set(projectDir.resolve("custom/java").toFile());
        extension.getBpmnDirectory().set(projectDir.resolve("custom/bpmn").toFile());
        extension.getBpmnReferenceRoot().set(projectDir.resolve("custom").toFile());
        extension.getSkip().set(true);
        extension.getFailOnDrift().set(false);

        UpdateCalledFromTask update = (UpdateCalledFromTask)
                project.getTasks().getByName(BpmnBacklinkPlugin.UPDATE_TASK_NAME);
        CheckCalledFromTask check = (CheckCalledFromTask)
                project.getTasks().getByName(BpmnBacklinkPlugin.CHECK_TASK_NAME);

        assertEquals(projectDir.resolve("custom/java").toFile(),
                update.getSourceDirectory().get().getAsFile(),
                "the task must follow the extension's sourceDirectory");
        assertEquals(projectDir.resolve("custom/bpmn").toFile(),
                check.getBpmnDirectory().get().getAsFile(),
                "the task must follow the extension's bpmnDirectory");
        assertEquals(projectDir.resolve("custom").toFile(),
                check.getBpmnReferenceRoot().get().getAsFile(),
                "the task must follow the extension's bpmnReferenceRoot");
        assertTrue(update.getSkip().get(), "the task must follow the extension's skip flag");
        assertFalse(check.getFailOnDrift().get(),
                "the check task must follow the extension's failOnDrift flag");
    }

    @Test
    void updateRunsBeforeCompileJavaByDefault(@TempDir Path projectDir) {
        Project project = projectWithPlugin(projectDir);
        project.getPluginManager().apply("java");

        assertTrue(dependenciesOf(project, "compileJava")
                        .contains(project.getTasks().getByName(BpmnBacklinkPlugin.UPDATE_TASK_NAME)),
                "compileJava must depend on bpmnBacklinkUpdate by default");
    }

    @Test
    void updateWiringCanBeDisabled(@TempDir Path projectDir) {
        Project project = projectWithPlugin(projectDir);
        project.getPluginManager().apply("java");
        project.getExtensions().getByType(BpmnBacklinkExtension.class)
                .getUpdateBeforeCompile().set(false);

        assertFalse(dependenciesOf(project, "compileJava")
                        .contains(project.getTasks().getByName(BpmnBacklinkPlugin.UPDATE_TASK_NAME)),
                "updateBeforeCompile=false must detach bpmnBacklinkUpdate from compileJava");
    }

    @Test
    void checkAttachesToCheckLifecycleByDefault(@TempDir Path projectDir) {
        // The wiring reacts to the 'base' plugin alone; 'java' is not required.
        Project project = projectWithPlugin(projectDir);
        project.getPluginManager().apply("base");

        assertTrue(dependenciesOf(project, "check")
                        .contains(project.getTasks().getByName(BpmnBacklinkPlugin.CHECK_TASK_NAME)),
                "check must depend on bpmnBacklinkCheck by default");
    }

    @Test
    void checkWiringCanBeDisabled(@TempDir Path projectDir) {
        Project project = projectWithPlugin(projectDir);
        project.getPluginManager().apply("base");
        project.getExtensions().getByType(BpmnBacklinkExtension.class)
                .getCheckOnCheck().set(false);

        assertFalse(dependenciesOf(project, "check")
                        .contains(project.getTasks().getByName(BpmnBacklinkPlugin.CHECK_TASK_NAME)),
                "checkOnCheck=false must detach bpmnBacklinkCheck from check");
    }

    @Test
    void worksWithoutJavaOrBasePlugin(@TempDir Path projectDir) {
        // Applying the plugin alone must neither require nor apply 'java'.
        Project project = projectWithPlugin(projectDir);

        assertNull(project.getTasks().findByName("compileJava"),
                "the plugin must not apply 'java' itself");
        assertNull(project.getTasks().findByName("check"),
                "the plugin must not apply 'base' itself");
        assertNotNull(project.getTasks().getByName(BpmnBacklinkPlugin.UPDATE_TASK_NAME),
                "the tasks must exist without 'java'");
        assertNotNull(project.getTasks().getByName(BpmnBacklinkPlugin.CHECK_TASK_NAME),
                "the tasks must exist without 'base'");
    }

    @Test
    void checkMustRunAfterUpdate(@TempDir Path projectDir) {
        Project project = projectWithPlugin(projectDir);

        Task update = project.getTasks().getByName(BpmnBacklinkPlugin.UPDATE_TASK_NAME);
        Task check = project.getTasks().getByName(BpmnBacklinkPlugin.CHECK_TASK_NAME);
        assertTrue(check.getMustRunAfter().getDependencies(check).contains(update),
                "when both tasks run, check must run after update");
    }

    private static Project projectWithPlugin(Path projectDir) {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        project.getPluginManager().apply(BpmnBacklinkPlugin.class);
        return project;
    }

    private static Set<? extends Task> dependenciesOf(Project project, String taskName) {
        Task task = project.getTasks().getByName(taskName);
        return task.getTaskDependencies().getDependencies(task);
    }
}
