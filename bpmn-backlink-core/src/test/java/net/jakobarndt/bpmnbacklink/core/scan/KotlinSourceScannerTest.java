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
package net.jakobarndt.bpmnbacklink.core.scan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that Kotlin sources are scanned alongside Java ones and that the
 * shapes a regex would trip over are classified correctly.
 */
class KotlinSourceScannerTest {

    private Path sourceRoot;

    private void copy(Path root, String fixture, String target) {
        Path packageDir = root.resolve("src/main/kotlin/net/example/delegate");
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("delegates/" + fixture)) {
            Files.createDirectories(packageDir);
            Files.write(packageDir.resolve(target), in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.sourceRoot = root.resolve("src/main/kotlin");
    }

    private List<String> scan() throws IOException {
        return new DelegateScanner(sourceRoot).scan().stream().map(DelegateType::simpleName).toList();
    }

    @Test
    void detectsAKotlinClassImplementingJavaDelegate(@TempDir Path root) throws IOException {
        copy(root, "KotlinOrderDelegate.kt.txt", "KotlinOrderDelegate.kt");

        assertEquals(List.of("KotlinOrderDelegate"), scan());
    }

    @Test
    void detectsAKotlinSubclassOfAbstractJavaDelegate(@TempDir Path root) throws IOException {
        copy(root, "KotlinShippingDelegate.kt.txt", "KotlinShippingDelegate.kt");

        assertEquals(List.of("KotlinShippingDelegate"), scan());
    }

    @Test
    void delegateReferenceOfAKotlinTypeIsTheCamelCasedName(@TempDir Path root) throws IOException {
        copy(root, "KotlinOrderDelegate.kt.txt", "KotlinOrderDelegate.kt");

        assertEquals("kotlinOrderDelegate", new DelegateScanner(sourceRoot).scan().get(0).delegateReference());
    }

    @Test
    void classifiesTheAdversarialShapesOfASingleFile(@TempDir Path root) throws IOException {
        copy(root, "KotlinEdgeCaseDelegates.kt.txt", "KotlinEdgeCaseDelegates.kt");

        List<String> found = scan();

        assertEquals(List.of(
            "MultilineConstructorDelegate",
            "KotlinObjectDelegate",
            "DelegatingKotlinDelegate",
            "spaced out delegate"), found);
        assertFalse(found.contains("ConstrainedRegistry"), "a type parameter bound is not a supertype");
        assertFalse(found.contains("WhereBoundRegistry"), "a where constraint is not a supertype");
        assertFalse(found.contains("AbstractKotlinDelegate"), "an abstract class is not instantiable");
        assertFalse(found.contains("SealedKotlinDelegate"), "a sealed class is not instantiable");
        assertFalse(found.contains("KotlinDelegateFacade"), "an interface is not a delegate implementation");
        assertFalse(found.contains("CommentedOutDelegate"), "a nested block comment must be ignored");
        assertFalse(found.contains("RawStringDelegate"), "a raw string must not be read as code");
        assertFalse(found.contains("TemplateDelegate"), "a string template must not be read as code");
    }

    @Test
    void scansAMixedJavaAndKotlinTreeInPathOrder(@TempDir Path root) throws IOException {
        copy(root, "KotlinOrderDelegate.kt.txt", "KotlinOrderDelegate.kt");
        Path javaFile = sourceRoot.resolve("net/example/delegate/PaymentDelegate.java");
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("delegates/PaymentDelegate.java.txt")) {
            Files.write(javaFile, in.readAllBytes());
        }

        assertEquals(List.of("KotlinOrderDelegate", "PaymentDelegate"), scan());
    }

    @Test
    void skipsFilesOfAnUnknownLanguage(@TempDir Path root) throws IOException {
        copy(root, "KotlinOrderDelegate.kt.txt", "KotlinOrderDelegate.kts");

        assertTrue(scan().isEmpty(), "a Kotlin script is not a delegate source root file");
    }

    @Test
    void kotlinScannerRejectsJavaFiles() {
        assertFalse(new KotlinSourceScanner().supports(Path.of("Delegate.java")));
        assertTrue(new JavaSourceScanner().supports(Path.of("Delegate.java")));
    }
}
