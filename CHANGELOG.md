# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Initial release of `bpmn-backlink`, a Maven plugin that links Camunda 7 BPMN
  service tasks (and other delegate-bearing elements) to their Java delegates and
  records the mapping as a `@CalledFrom` annotation in the delegate source files.
- Module `bpmn-backlink-annotation`: the `@CalledFrom` annotation.
- Module `bpmn-backlink-core`: BPMN indexing via the Camunda BPMN Model API,
  source-level delegate scanning and annotation rewriting via JavaParser with
  `LexicalPreservingPrinter`. No Maven dependency.
- Module `bpmn-backlink-maven-plugin`: the `update` goal (bound to `process-sources`)
  and the `check` goal (bound to `verify`, fails the build on drift unless
  `failOnDrift=false`).
- Configurable parameters: `sourceDirectory`, `bpmnDirectory`, `bpmnReferenceRoot`,
  `skip`, and `failOnDrift` (check only).
- Delegate references are detected on any BPMN element carrying `camunda:class` or
  `camunda:delegateExpression` (`${bean}` or `#{bean}`), not just service tasks.
- Idempotent rewriting: a second run produces no diff; unreferenced delegates have
  the annotation removed again.
- 100% line and branch coverage gate (JaCoCo) per module, a maven-invoker
  integration test, and a GitHub Actions CI workflow running `mvn verify`.

[Unreleased]: https://github.com/jjarndt/bpmn-backlink/commits/main
