/*
 * Post-build verification for the mixed-java-kotlin integration test.
 *
 * Asserts that:
 *   1. the sub-build succeeded (build.log contains BUILD SUCCESS),
 *   2. the update goal annotated the delegate of the Java source root as well as
 *      the one below src/main/kotlin, each in its own language's syntax, and
 *   3. both rewritten sources still compile, so the annotation the goal wrote is
 *      valid Java and valid Kotlin.
 *
 * 'basedir' is provided by the maven-invoker-plugin and points at the cloned
 * project directory.
 */

File buildLog = new File(basedir, "build.log")
assert buildLog.exists() : "build.log not found at ${buildLog}"
String log = buildLog.text
assert log.contains("BUILD SUCCESS") : "sub-build did not report BUILD SUCCESS:\n${log}"

File javaDelegate = new File(basedir, "src/main/java/com/example/JavaShipDelegate.java")
assert javaDelegate.exists() : "JavaShipDelegate.java not found at ${javaDelegate}"
String javaSource = javaDelegate.text
assert javaSource.contains('@CalledFrom("bpmn/processes/mixed.bpmn")') :
        "update did not annotate the Java delegate; got:\n${javaSource}"
assert javaSource.contains("import net.jakobarndt.bpmnbacklink.annotation.CalledFrom;") :
        "update did not write the Java import; got:\n${javaSource}"

File kotlinDelegate = new File(basedir, "src/main/kotlin/com/example/KotlinOrderDelegate.kt")
assert kotlinDelegate.exists() : "KotlinOrderDelegate.kt not found at ${kotlinDelegate}"
String kotlinSource = kotlinDelegate.text
assert kotlinSource.contains('@CalledFrom("bpmn/processes/mixed.bpmn")') :
        "update did not annotate the Kotlin delegate; got:\n${kotlinSource}"
assert kotlinSource.contains("import net.jakobarndt.bpmnbacklink.annotation.CalledFrom\n") :
        "update did not write the Kotlin import (which carries no semicolon); got:\n${kotlinSource}"

assert new File(basedir, "target/classes/com/example/JavaShipDelegate.class").exists() :
        "the rewritten Java delegate was not compiled"
assert new File(basedir, "target/classes/com/example/KotlinOrderDelegate.class").exists() :
        "the rewritten Kotlin delegate was not compiled"

// Both goals share the process-sources phase and run in the order their plugins
// are declared, so update ran before kotlin-maven-plugin had a chance to touch
// the project model. The Kotlin delegate above was therefore found through the
// src/main/kotlin convention, not through ${project.compileSourceRoots}.
int updateAt = log.indexOf(":update (backlink-update) @ mixed-java-kotlin")
int kotlinAt = log.indexOf(":compile (compile) @ mixed-java-kotlin")
assert updateAt >= 0 : "the update goal did not run:\n${log}"
assert kotlinAt >= 0 : "kotlin-maven-plugin did not run:\n${log}"
assert kotlinAt > updateAt :
        "expected the update goal to run before kotlin-maven-plugin:\n${log}"

return true
