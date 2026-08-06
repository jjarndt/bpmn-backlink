# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Multiple source roots are scanned in one run. Maven defaults to the project's
  compile source roots (as they stand in `process-sources`, minus generated roots
  below the build directory) plus `src/main/kotlin` when that directory exists;
  Gradle defaults to `src/main/java` and `src/main/kotlin`. A root that does not
  exist is skipped, duplicates are collapsed, and the delegates are sorted across
  all roots so the result does not depend on the order the roots are listed in.
- New Maven parameter `sourceDirectories` (property
  `bpmnBacklink.sourceDirectories`) and new Gradle property `sourceDirectories`
  to configure the roots explicitly.

### Changed

- **Breaking (API):** `BacklinkConfig.sourceDirectory` is now
  `BacklinkConfig.sourceDirectories` (`List<Path>`). `BacklinkConfig.Builder`
  gained `sourceDirectories(List<Path>)`; `sourceDirectory(Path)` remains as a
  deprecated convenience for the single-root case.

### Deprecated

- Maven parameter `sourceDirectory` and Gradle property `sourceDirectory`. Both
  still work: when set explicitly, the named root is the only one scanned.

### Fixed

- Delegate expressions now resolve explicit bean names declared with
  `@Component("...")`, `@Service("...")` or `@Named("...")` in Java and Kotlin
  sources. Empty or non-literal annotation values continue to use the default
  decapitalized class name.

## [0.1.0] - 2026-06-16

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

[Unreleased]: https://github.com/jjarndt/bpmn-backlink/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/jjarndt/bpmn-backlink/releases/tag/v0.1.0
