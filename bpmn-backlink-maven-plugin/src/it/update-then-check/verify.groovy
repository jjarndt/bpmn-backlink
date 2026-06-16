/*
 * Post-build verification for the update-then-check integration test.
 *
 * Asserts that:
 *   1. the sub-build succeeded (build.log contains BUILD SUCCESS), and
 *   2. the update goal mutated DemoDelegate.java in the cloned project, adding
 *      the @CalledFrom annotation for demo.bpmn and the matching import.
 *
 * 'basedir' is provided by the maven-invoker-plugin and points at the cloned
 * project directory.
 */

File buildLog = new File(basedir, "build.log")
assert buildLog.exists() : "build.log not found at ${buildLog}"
String log = buildLog.text
assert log.contains("BUILD SUCCESS") : "sub-build did not report BUILD SUCCESS:\n${log}"

File delegate = new File(basedir, "src/main/java/com/example/DemoDelegate.java")
assert delegate.exists() : "DemoDelegate.java not found at ${delegate}"
String source = delegate.text

assert source.contains('@CalledFrom("bpmn/processes/demo.bpmn")') :
        "update did not write the expected @CalledFrom annotation; got:\n${source}"
assert source.contains("import net.jakobarndt.bpmnbacklink.annotation.CalledFrom;") :
        "update did not write the CalledFrom import; got:\n${source}"

return true
