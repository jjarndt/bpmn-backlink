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

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the plugin through real Gradle builds via TestKit, mirroring the Maven
 * invoker IT {@code update-then-check}. The sample project is hermetic: instead
 * of depending on camunda-engine and the annotation artifact, it carries stub
 * sources for {@code JavaDelegate}, {@code DelegateExecution} and
 * {@code CalledFrom} — the delegate scanner works purely on source code, and
 * the stubs keep {@code compileJava} working without any repository access.
 */
class BpmnBacklinkPluginFunctionalTest {

    private static final String BUILD_SCRIPT = String.join("\n",
            "plugins {",
            "    id 'java'",
            "    id 'net.jakobarndt.bpmnbacklink'",
            "}",
            "");

    private static final String DEMO_DELEGATE = String.join("\n",
            "package com.example;",
            "",
            "import org.camunda.bpm.engine.delegate.DelegateExecution;",
            "import org.camunda.bpm.engine.delegate.JavaDelegate;",
            "",
            "public class DemoDelegate implements JavaDelegate {",
            "",
            "    @Override",
            "    public void execute(DelegateExecution execution) {",
            "        execution.setVariable(\"demo\", true);",
            "    }",
            "}",
            "");

    private static final String JAVA_DELEGATE_STUB = String.join("\n",
            "package org.camunda.bpm.engine.delegate;",
            "",
            "public interface JavaDelegate {",
            "    void execute(DelegateExecution execution) throws Exception;",
            "}",
            "");

    private static final String DELEGATE_EXECUTION_STUB = String.join("\n",
            "package org.camunda.bpm.engine.delegate;",
            "",
            "public interface DelegateExecution {",
            "    void setVariable(String variableName, Object value);",
            "}",
            "");

    private static final String CALLED_FROM_STUB = String.join("\n",
            "package net.jakobarndt.bpmnbacklink.annotation;",
            "",
            "public @interface CalledFrom {",
            "    String[] value();",
            "}",
            "");

    private static final String DEMO_BPMN = String.join("\n",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
            "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
            "                  xmlns:camunda=\"http://camunda.org/schema/1.0/bpmn\"",
            "                  id=\"defs_demo\" targetNamespace=\"http://example.com/bpmn\">",
            "  <bpmn:process id=\"demoProcess\" isExecutable=\"true\">",
            "    <bpmn:startEvent id=\"start\"/>",
            "    <bpmn:serviceTask id=\"runDemo\" name=\"Run demo\"",
            "                      camunda:class=\"com.example.DemoDelegate\"/>",
            "    <bpmn:endEvent id=\"end\"/>",
            "    <bpmn:sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"runDemo\"/>",
            "    <bpmn:sequenceFlow id=\"f2\" sourceRef=\"runDemo\" targetRef=\"end\"/>",
            "  </bpmn:process>",
            "</bpmn:definitions>",
            "");

    private static final String EXPECTED_ANNOTATION = "@CalledFrom(\"bpmn/processes/demo.bpmn\")";
    private static final String EXPECTED_IMPORT = "import net.jakobarndt.bpmnbacklink.annotation.CalledFrom;";

    @Test
    void buildRunsUpdateThenCheckAndWritesAnnotation(@TempDir Path projectDir) throws IOException {
        Path delegateFile = writeSampleProject(projectDir, BUILD_SCRIPT);

        BuildResult result = runner(projectDir, "build").build();

        assertEquals(TaskOutcome.SUCCESS, outcomeOf(result, ":bpmnBacklinkUpdate"),
                "update must run as part of build (before compileJava)");
        assertEquals(TaskOutcome.SUCCESS, outcomeOf(result, ":bpmnBacklinkCheck"),
                "check must run as part of build and pass after update");

        String source = Files.readString(delegateFile, StandardCharsets.UTF_8);
        assertTrue(source.contains(EXPECTED_ANNOTATION),
                "build must write the expected @CalledFrom annotation; got:\n" + source);
        assertTrue(source.contains(EXPECTED_IMPORT),
                "build must write the CalledFrom import; got:\n" + source);
    }

    @Test
    void checkAloneFailsWithFixHint(@TempDir Path projectDir) throws IOException {
        writeSampleProject(projectDir, BUILD_SCRIPT);

        BuildResult result = runner(projectDir, "bpmnBacklinkCheck").buildAndFail();

        assertEquals(TaskOutcome.FAILED, outcomeOf(result, ":bpmnBacklinkCheck"),
                "check must fail on a project whose delegate lacks its annotation");
        assertTrue(result.getOutput().contains("run './gradlew bpmnBacklinkUpdate' to fix."),
                "the failure must point at the fixing task; got:\n" + result.getOutput());
    }

    @Test
    void updateRunsAgainWhenConfigurationCacheIsReused(@TempDir Path projectDir) throws IOException {
        writeSampleProject(projectDir, BUILD_SCRIPT);

        runner(projectDir, "bpmnBacklinkUpdate", "--configuration-cache").build();
        BuildResult second = runner(projectDir, "bpmnBacklinkUpdate", "--configuration-cache").build();

        assertTrue(second.getOutput().contains("Reusing configuration cache."),
                "the second build must reuse the configuration cache; got:\n" + second.getOutput());
        assertEquals(TaskOutcome.SUCCESS, outcomeOf(second, ":bpmnBacklinkUpdate"),
                "update must run again (never up to date) even with a reused configuration cache");
    }

    @Test
    void autoWiringCanBeDisabled(@TempDir Path projectDir) throws IOException {
        String buildScript = String.join("\n",
                BUILD_SCRIPT,
                "bpmnBacklink {",
                "    updateBeforeCompile = false",
                "    checkOnCheck = false",
                "}",
                "");
        Path delegateFile = writeSampleProject(projectDir, buildScript);

        BuildResult result = runner(projectDir, "build").build();

        assertNull(result.task(":bpmnBacklinkUpdate"),
                "updateBeforeCompile=false must keep update out of the build");
        assertNull(result.task(":bpmnBacklinkCheck"),
                "checkOnCheck=false must keep check out of the build");
        assertFalse(Files.readString(delegateFile, StandardCharsets.UTF_8).contains(EXPECTED_ANNOTATION),
                "without the wiring, build must not touch the delegate");
    }

    private static GradleRunner runner(Path projectDir, String... arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(arguments);
    }

    private static TaskOutcome outcomeOf(BuildResult result, String taskPath) {
        org.gradle.testkit.runner.BuildTask task = result.task(taskPath);
        assertNotNull(task, "expected task " + taskPath + " to be part of the build");
        return task.getOutcome();
    }

    private static Path writeSampleProject(Path projectDir, String buildScript) throws IOException {
        Files.writeString(projectDir.resolve("settings.gradle"),
                "rootProject.name = 'sample'\n", StandardCharsets.UTF_8);
        Files.writeString(projectDir.resolve("build.gradle"), buildScript, StandardCharsets.UTF_8);

        Path exampleDir = projectDir.resolve("src/main/java/com/example");
        Files.createDirectories(exampleDir);
        Path delegateFile = exampleDir.resolve("DemoDelegate.java");
        Files.writeString(delegateFile, DEMO_DELEGATE, StandardCharsets.UTF_8);

        Path camundaStubDir = projectDir.resolve("src/main/java/org/camunda/bpm/engine/delegate");
        Files.createDirectories(camundaStubDir);
        Files.writeString(camundaStubDir.resolve("JavaDelegate.java"),
                JAVA_DELEGATE_STUB, StandardCharsets.UTF_8);
        Files.writeString(camundaStubDir.resolve("DelegateExecution.java"),
                DELEGATE_EXECUTION_STUB, StandardCharsets.UTF_8);

        Path annotationStubDir = projectDir.resolve("src/main/java/net/jakobarndt/bpmnbacklink/annotation");
        Files.createDirectories(annotationStubDir);
        Files.writeString(annotationStubDir.resolve("CalledFrom.java"),
                CALLED_FROM_STUB, StandardCharsets.UTF_8);

        Path bpmnDir = projectDir.resolve("src/main/resources/bpmn/processes");
        Files.createDirectories(bpmnDir);
        Files.writeString(bpmnDir.resolve("demo.bpmn"), DEMO_BPMN, StandardCharsets.UTF_8);

        return delegateFile;
    }
}
