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
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    `java-gradle-plugin`
    jacoco
    signing
    id("com.gradle.plugin-publish") version "2.1.1"
    id("com.gradleup.nmcp") version "1.6.1"
    id("com.gradleup.nmcp.aggregation") version "1.6.1"
}

group = "net.jakobarndt.bpmnbacklink"
// The root Maven pom.xml is the single source of truth for the project version.
version = versionFromRootPom()

fun versionFromRootPom(): String {
    val pomFile = rootDir.parentFile.resolve("pom.xml")
    val projectElement = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(pomFile)
        .documentElement
    val children = projectElement.childNodes
    for (i in 0 until children.length) {
        val node = children.item(i)
        if (node.nodeName == "version") {
            return node.textContent.trim()
        }
    }
    throw GradleException("No <version> element found in $pomFile")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    // bpmn-backlink-core is staged here by `mvn -DskipTests install` during
    // development and CI; released versions come from Maven Central.
    mavenLocal()
    mavenCentral()
}

// TestKit-based tests live in their own source set so they can run the plugin
// through real Gradle builds (out of process, therefore outside the JaCoCo gate).
val functionalTest: SourceSet = sourceSets.create("functionalTest")

dependencies {
    implementation("net.jakobarndt.bpmnbacklink:bpmn-backlink-core:$version")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    "functionalTestImplementation"(platform("org.junit:junit-bom:5.11.3"))
    "functionalTestImplementation"("org.junit.jupiter:junit-jupiter")
    "functionalTestImplementation"(gradleTestKit())
    "functionalTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    website = "https://github.com/jjarndt/bpmn-backlink"
    vcsUrl = "https://github.com/jjarndt/bpmn-backlink.git"
    plugins {
        create("bpmnBacklink") {
            id = "net.jakobarndt.bpmnbacklink"
            implementationClass = "net.jakobarndt.bpmnbacklink.gradle.BpmnBacklinkPlugin"
            displayName = "bpmn-backlink"
            description = "Links Camunda 7 BPMN service tasks to their Java delegates and " +
                "records the mapping as a @CalledFrom annotation in the delegate source files."
            tags = listOf("bpmn", "camunda", "camunda-7", "annotation", "backlink")
        }
    }
    // Injects the plugin classpath into GradleRunner.withPluginClasspath().
    testSourceSets(functionalTest)
}

val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs the TestKit-based functional tests."
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    shouldRunAfter(tasks.test)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
    }
}

// Mirror of the Maven coverage gate: 100% line and branch coverage, measured on
// the in-process unit tests. The TestKit tests run out of process and therefore
// do not count; every branch is reachable in-process (see BacklinkTaskTest and
// BpmnBacklinkPluginTest).
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "1.00".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "1.00".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(functionalTestTask)
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// Full POM metadata for Maven Central; configureEach also reaches the plugin
// marker publication created by java-gradle-plugin.
publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name = "bpmn-backlink-gradle-plugin"
            description = "Gradle plugin that links Camunda 7 BPMN service tasks to their Java " +
                "delegates and records the mapping as a @CalledFrom annotation in the delegate " +
                "source files."
            url = "https://github.com/jjarndt/bpmn-backlink"
            licenses {
                license {
                    name = "Apache License, Version 2.0"
                    url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    distribution = "repo"
                }
            }
            developers {
                developer {
                    id = "jjarndt"
                    name = "Jakob Arndt"
                    url = "https://github.com/jjarndt"
                }
            }
            scm {
                connection = "scm:git:https://github.com/jjarndt/bpmn-backlink.git"
                developerConnection = "scm:git:git@github.com:jjarndt/bpmn-backlink.git"
                url = "https://github.com/jjarndt/bpmn-backlink"
            }
        }
    }
}

// The release pipeline provides the key; local builds have none and must never
// sign (isRequired=false skips the sign tasks without a configured signatory).
// The plugin-publish plugin auto-signs all publications once 'signing' is applied.
signing {
    val signingKey = System.getenv("GPG_PRIVATE_KEY")
    isRequired = signingKey != null
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, "")
    }
}

// Uploads the staged publications (main + marker) to the Sonatype Central Portal:
// ./gradlew publishAggregationToCentralPortal
nmcpAggregation {
    centralPortal {
        username = providers.environmentVariable("CENTRAL_USERNAME")
        password = providers.environmentVariable("CENTRAL_PASSWORD")
        publishingType = "AUTOMATIC"
    }
    publishAllProjectsProbablyBreakingProjectIsolation()
}

// The Plugin Portal rejects snapshots only at upload time; fail fast instead.
val projectVersion = version.toString()
tasks.named("publishPlugins") {
    doFirst {
        if (projectVersion.endsWith("-SNAPSHOT")) {
            throw GradleException(
                "Refusing to publish snapshot version $projectVersion to the Plugin Portal; " +
                    "release the version in the root pom.xml first."
            )
        }
    }
}
