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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

import java.util.List;

/**
 * Registers the {@code bpmnBacklink} extension and the two backlink tasks.
 *
 * <p>Mirroring the Maven plugin's lifecycle defaults, {@code bpmnBacklinkUpdate}
 * runs before {@code compileJava} and {@code bpmnBacklinkCheck} is attached to
 * the {@code check} lifecycle task. Both wirings react to the {@code java} and
 * {@code base} plugins being applied (in any order) and can be switched off via
 * the extension's {@code updateBeforeCompile} and {@code checkOnCheck} flags,
 * which are read lazily so they may be set anywhere in the build script.
 */
public class BpmnBacklinkPlugin implements Plugin<Project> {

    /** Name of the {@link BpmnBacklinkExtension}. */
    public static final String EXTENSION_NAME = "bpmnBacklink";

    /** Name of the {@link UpdateCalledFromTask}. */
    public static final String UPDATE_TASK_NAME = "bpmnBacklinkUpdate";

    /** Name of the {@link CheckCalledFromTask}. */
    public static final String CHECK_TASK_NAME = "bpmnBacklinkCheck";

    private static final String TASK_GROUP = "bpmn backlink";

    @Override
    public void apply(Project project) {
        BpmnBacklinkExtension extension =
                project.getExtensions().create(EXTENSION_NAME, BpmnBacklinkExtension.class);
        extension.getSourceDirectory()
                .convention(project.getLayout().getProjectDirectory().dir("src/main/java"));
        extension.getBpmnDirectory()
                .convention(project.getLayout().getProjectDirectory().dir("src/main/resources/bpmn/processes"));
        extension.getBpmnReferenceRoot()
                .convention(project.getLayout().getProjectDirectory().dir("src/main/resources"));
        extension.getSkip().convention(false);
        extension.getFailOnDrift().convention(true);
        extension.getUpdateBeforeCompile().convention(true);
        extension.getCheckOnCheck().convention(true);

        TaskProvider<UpdateCalledFromTask> update =
                project.getTasks().register(UPDATE_TASK_NAME, UpdateCalledFromTask.class, task -> {
                    task.setGroup(TASK_GROUP);
                    task.setDescription("Rewrites the delegate sources so their @CalledFrom "
                            + "annotations match the BPMN index.");
                    configureCommon(task, extension);
                });

        TaskProvider<CheckCalledFromTask> check =
                project.getTasks().register(CHECK_TASK_NAME, CheckCalledFromTask.class, task -> {
                    task.setGroup(TASK_GROUP);
                    task.setDescription("Fails the build when a @CalledFrom annotation drifts "
                            + "from the BPMN index; writes nothing.");
                    configureCommon(task, extension);
                    task.getFailOnDrift().convention(extension.getFailOnDrift());
                    task.mustRunAfter(update);
                });

        // The flags are wrapped in providers so they are only read at task-graph
        // time; users may therefore set them after the plugins block.
        project.getPluginManager().withPlugin("java", applied ->
                project.getTasks().named("compileJava", compileJava ->
                        compileJava.dependsOn(project.provider(() ->
                                extension.getUpdateBeforeCompile().get() ? List.of(update) : List.of()))));

        project.getPluginManager().withPlugin("base", applied ->
                project.getTasks().named("check", checkLifecycle ->
                        checkLifecycle.dependsOn(project.provider(() ->
                                extension.getCheckOnCheck().get() ? List.of(check) : List.of()))));
    }

    private static void configureCommon(AbstractBacklinkTask task, BpmnBacklinkExtension extension) {
        task.getSourceDirectory().convention(extension.getSourceDirectory());
        task.getBpmnDirectory().convention(extension.getBpmnDirectory());
        task.getBpmnReferenceRoot().convention(extension.getBpmnReferenceRoot());
        task.getSkip().convention(extension.getSkip());
    }
}
