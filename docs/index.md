---
title: bpmn-backlink
description: Link Camunda 7 BPMN service tasks to their Java delegates via @CalledFrom annotations
---

# bpmn-backlink

**bpmn-backlink** keeps your Camunda 7 Java delegates and the BPMN processes that use
them in sync. During the build it works out which BPMN files reference each delegate and
records that as a `@CalledFrom` annotation directly in the delegate source. The result is
a navigable backlink from delegate code to the processes that call it, with no runtime cost.

- Built on the **Camunda BPMN Model API** (no brittle regex) for reading processes.
- Built on **JavaParser** with `LexicalPreservingPrinter` for rewriting sources without
  reformatting the rest of the file.
- **Idempotent**: a second run produces no diff, and delegates that are no longer
  referenced have the annotation removed again.

## How it works

1. Index every `*.bpmn` file under the configured directory and collect each delegate
   reference: `camunda:delegateExpression` (`${bean}` or `#{bean}`) and `camunda:class`.
   References are read from any element that carries them (service tasks, execution/task
   listeners, send tasks, business-rule tasks, message event definitions). `camunda:expression`
   is not a delegate and is ignored.
2. Scan the Java sources for concrete classes implementing `JavaDelegate` or extending
   `AbstractJavaDelegate` (source-level, no class loading).
3. Write the sorted list of referencing BPMN paths into a `@CalledFrom` annotation on the
   matching delegate, adding or removing the import as needed.

## What it produces

Given a service task in `src/main/resources/bpmn/processes/order.bpmn`:

```xml
<serviceTask id="charge" name="Charge order" camunda:class="com.example.OrderDelegate"/>
```

the `update` goal rewrites the delegate:

```java
import net.jakobarndt.bpmnbacklink.annotation.CalledFrom;

@CalledFrom("bpmn/processes/order.bpmn")
public class OrderDelegate implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        // ...
    }
}
```

A delegate referenced from several processes gets a sorted, multi-valued annotation:

```java
@CalledFrom({ "bpmn/processes/cancel.bpmn", "bpmn/processes/order.bpmn" })
```

## Installation

Add the annotation dependency to the module that contains your delegates, so the generated
import compiles:

```xml
<dependency>
    <groupId>net.jakobarndt.bpmnbacklink</groupId>
    <artifactId>bpmn-backlink-annotation</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Then add the plugin to the same module:

```xml
<plugin>
    <groupId>net.jakobarndt.bpmnbacklink</groupId>
    <artifactId>bpmn-backlink-maven-plugin</artifactId>
    <version>0.1.0-SNAPSHOT</version>
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

## Goals

- `bpmn-backlink:update` writes or refreshes the annotations. Bound to `process-sources`
  by default, so the annotation is compiled with the rest of the sources.
- `bpmn-backlink:check` verifies the annotations without writing. Bound to `verify` by
  default and fails the build on drift (configurable via `failOnDrift`). Use it in CI to
  ensure committed annotations stay up to date.

You can also run them ad hoc:

```bash
mvn bpmn-backlink:update
mvn bpmn-backlink:check
```

## Configuration

All parameters have sensible defaults and can be overridden:

| Parameter           | Default                                                | Goals          |
|---------------------|--------------------------------------------------------|----------------|
| `sourceDirectory`   | `${project.build.sourceDirectory}`                     | update, check  |
| `bpmnDirectory`     | `${project.basedir}/src/main/resources/bpmn/processes` | update, check  |
| `bpmnReferenceRoot` | `${project.basedir}/src/main/resources`                | update, check  |
| `skip`              | `false`                                                | update, check  |
| `failOnDrift`       | `true`                                                 | check          |

BPMN paths are stored relative to `bpmnReferenceRoot` using `/` separators, so they line up
with classpath-relative deployment paths and stay navigable from the IDE.

## Scope

Version 0.1.0 targets Camunda 7 and the element-to-code relation: any delegate reference via
`camunda:class` or `camunda:delegateExpression`, regardless of the element type. Out of scope
for now are external-task topics, call activities (process-to-process) and Camunda 8
(`@JobWorker`). The core keeps the reference type abstract, so these can be added later
without a redesign.

## Requirements

- Java 17+
- Maven 3.9+
- Camunda 7 (the consuming project provides `camunda-engine`)

## License

Licensed under the Apache License, Version 2.0.
