---
title: bpmn-backlink
description: Link Camunda 7 BPMN service tasks to their Java delegates via @CalledFrom annotations
---

# bpmn-backlink

**bpmn-backlink** keeps your Camunda 7 Java delegates and the BPMN processes that use
them in sync. During the build it works out which BPMN files reference each delegate and
records that as a `@CalledFrom` annotation directly in the delegate source — a navigable
backlink from delegate code to the processes that call it, with no runtime cost.

## What it produces

Given a service task `<serviceTask camunda:class="com.example.OrderDelegate"/>`, the
`update` goal rewrites the delegate:

```java
import net.jakobarndt.bpmnbacklink.annotation.CalledFrom;

@CalledFrom("bpmn/processes/order.bpmn")
public class OrderDelegate implements JavaDelegate {
    ...
}
```

A delegate referenced from several processes gets a sorted, multi-valued annotation
(`@CalledFrom({ "a.bpmn", "b.bpmn" })`). The rewrite is idempotent: a second run produces no
diff, and delegates that are no longer referenced have the annotation removed again.

## Quickstart

Add the annotation dependency to the module with your delegates, then bind the plugin's
`update` and `check` goals:

```xml
<dependency>
    <groupId>net.jakobarndt.bpmnbacklink</groupId>
    <artifactId>bpmn-backlink-annotation</artifactId>
    <version>0.1.0</version>
</dependency>
```

```xml
<plugin>
    <groupId>net.jakobarndt.bpmnbacklink</groupId>
    <artifactId>bpmn-backlink-maven-plugin</artifactId>
    <version>0.1.0</version>
    <executions>
        <execution>
            <id>backlink-update</id>
            <phase>process-sources</phase>
            <goals><goal>update</goal></goals>
        </execution>
        <execution>
            <id>backlink-check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
        </execution>
    </executions>
</plugin>
```

Full documentation — configuration parameters, scope, and goals — lives in the
[README on GitHub](https://github.com/jjarndt/bpmn-backlink#readme).

## License

Licensed under the Apache License, Version 2.0.
