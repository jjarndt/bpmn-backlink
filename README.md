<div align="center">

# bpmn-backlink

#### Navigable backlinks from Camunda&nbsp;7 delegate code to the BPMN processes that call it — written into your sources and maintained at build time.

[![Maven Central](https://img.shields.io/maven-central/v/net.jakobarndt.bpmnbacklink/bpmn-backlink-maven-plugin?style=flat-square&logo=apachemaven&label=Maven%20Central&color=blue)](https://central.sonatype.com/artifact/net.jakobarndt.bpmnbacklink/bpmn-backlink-maven-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/net.jakobarndt.bpmnbacklink?style=flat-square&logo=gradle&label=Plugin%20Portal&color=blue)](https://plugins.gradle.org/plugin/net.jakobarndt.bpmnbacklink)
[![CI](https://img.shields.io/github/actions/workflow/status/jjarndt/bpmn-backlink/ci.yml?branch=main&style=flat-square&logo=githubactions&logoColor=white&label=CI)](https://github.com/jjarndt/bpmn-backlink/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=flat-square)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange?style=flat-square&logo=openjdk&logoColor=white)](#requirements)

</div>

<p align="center"><img src="docs/img/hero.png" alt="A Camunda delegate before and after bpmn-backlink: the update goal adds a @CalledFrom annotation listing the BPMN processes that call it" width="620"/></p>

## Quickstart (Maven)

Add the annotation dependency to the module with your delegates, so the generated import compiles:

```xml
<dependency>
    <groupId>net.jakobarndt.bpmnbacklink</groupId>
    <artifactId>bpmn-backlink-annotation</artifactId>
    <version>0.1.0</version>
</dependency>
```

Then add the plugin to the same module:

```xml
<plugin>
    <groupId>net.jakobarndt.bpmnbacklink</groupId>
    <artifactId>bpmn-backlink-maven-plugin</artifactId>
    <version>0.1.0</version>
    <executions>
        <!-- Keep the @CalledFrom annotations in sync during the build. -->
        <execution>
            <id>backlink-update</id>
            <phase>process-sources</phase>
            <goals>
                <goal>update</goal>
            </goals>
        </execution>
        <!-- Fail the build if the committed annotations are out of date. -->
        <execution>
            <id>backlink-check</id>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

## Quickstart (Gradle)

Apply the plugin and add the annotation dependency:

```kotlin
plugins {
    java
    id("net.jakobarndt.bpmnbacklink") version "0.2.0"
}

dependencies {
    implementation("net.jakobarndt.bpmnbacklink:bpmn-backlink-annotation:0.2.0")
}
```

That is all: `bpmnBacklinkUpdate` runs automatically before `compileJava` and
`bpmnBacklinkCheck` is attached to `check`. Both wirings and all parameters can be
adjusted via the extension:

```kotlin
bpmnBacklink {
    bpmnDirectory = layout.projectDirectory.dir("src/main/resources/bpmn/processes")
    failOnDrift = true
    updateBeforeCompile = true // set false to run bpmnBacklinkUpdate manually
    checkOnCheck = true        // set false to detach bpmnBacklinkCheck from check
}
```

## What it produces

The `update` goal records which BPMN processes reference each delegate in a `@CalledFrom`
annotation — the before/after shown above. A delegate called from several processes gets a
sorted, multi-valued annotation, `@CalledFrom({ "a.bpmn", "b.bpmn" })`; existing formatting
and comments are left untouched.

## Configuration

All parameters have sensible defaults and can be overridden — in Maven via the plugin's
`<configuration>`, in Gradle via the `bpmnBacklink { }` extension:

| Parameter             | Default                                | Applies to               |
|-----------------------|----------------------------------------|--------------------------|
| `sourceDirectory`     | `src/main/java`                        | update, check            |
| `bpmnDirectory`       | `src/main/resources/bpmn/processes`    | update, check            |
| `bpmnReferenceRoot`   | `src/main/resources`                   | update, check            |
| `skip`                | `false`                                | update, check            |
| `failOnDrift`         | `true`                                 | check                    |
| `updateBeforeCompile` | `true`                                 | Gradle only: auto-wiring |
| `checkOnCheck`        | `true`                                 | Gradle only: auto-wiring |

BPMN paths are stored relative to `bpmnReferenceRoot` using `/` separators, so they line up
with classpath-relative deployment paths and stay navigable from the IDE.

## Goals and tasks

Maven goals:

- `bpmn-backlink:update` — writes/refreshes the `@CalledFrom` annotations. Bound to the
  `process-sources` phase by default.
- `bpmn-backlink:check` — verifies the annotations are up to date without writing. Bound to
  the `verify` phase by default; fails the build on drift (configurable via `failOnDrift`).

Gradle tasks (same behaviour):

- `bpmnBacklinkUpdate` — runs automatically before `compileJava` (`updateBeforeCompile`).
- `bpmnBacklinkCheck` — attached to the `check` lifecycle (`checkOnCheck`).

## Scope

Version 0.1.0 targets Camunda 7 and the element-to-code relation only:

- Detected delegates: concrete classes implementing `JavaDelegate` or extending
  `AbstractJavaDelegate`.
- Detected references: `camunda:delegateExpression` (`${bean}` or `#{bean}`) and
  `camunda:class`, on any BPMN element that carries them. `camunda:expression` is not a
  delegate and is ignored.
- Out of scope for now: external-task topics, call activities (process-to-process), and
  Camunda 8 (`@JobWorker`).

## Requirements

- Java 17+
- Maven 3.9+ or Gradle 8.0+
- Camunda 7 (the consuming project provides `camunda-engine`)

## Modules

- `bpmn-backlink-annotation` — the `@CalledFrom` annotation.
- `bpmn-backlink-core` — the engine: BPMN indexing (BPMN Model API), delegate scanning and
  annotation writing (JavaParser). No Maven dependency.
- `bpmn-backlink-maven-plugin` — the Maven plugin exposing the `update` and `check` goals.
- `bpmn-backlink-gradle-plugin` — the Gradle plugin exposing the same functionality as the
  `bpmnBacklinkUpdate` and `bpmnBacklinkCheck` tasks (own Gradle build, not a Maven module).

## Building

```bash
mvn verify
```

This runs the unit tests, a maven-invoker integration test (`update` then `check` against a
sample project), and the JaCoCo coverage gate (100% line and branch per module). CI runs the
same on every push and pull request to `main`.

The Gradle plugin builds separately and consumes `bpmn-backlink-core` from `mavenLocal`:

```bash
mvn -DskipTests install
cd bpmn-backlink-gradle-plugin && ./gradlew build
```

This runs the unit tests (with the same 100% coverage gate) and TestKit functional tests
that drive the plugin through real Gradle builds.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
