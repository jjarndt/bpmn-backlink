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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins how the scanner treats a set of source roots: every root contributes, the
 * reported order is global rather than root-by-root, a missing root is skipped
 * and no file is scanned twice.
 */
class DelegateScannerRootsTest {

    private static final String PACKAGE_PATH = "net/example/delegate";

    @Test
    void everyConfiguredRootContributes(@TempDir Path root) throws IOException {
        Path javaRoot = copy(root, "src/main/java", "OrderDelegate.java.txt", "OrderDelegate.java");
        Path kotlinRoot =
            copy(root, "src/main/kotlin", "KotlinShippingDelegate.kt.txt", "KotlinShippingDelegate.kt");

        assertEquals(List.of("OrderDelegate", "KotlinShippingDelegate"), scan(javaRoot, kotlinRoot),
            "a mixed module must report the delegates of both roots");
    }

    @Test
    void orderIsGlobalAndIndependentOfTheRootOrder(@TempDir Path root) throws IOException {
        // Sorting per root would make the result depend on how the build tool
        // happens to list its compile source roots.
        Path javaRoot = copy(root, "src/main/java", "OrderDelegate.java.txt", "OrderDelegate.java");
        Path kotlinRoot =
            copy(root, "src/main/kotlin", "KotlinShippingDelegate.kt.txt", "KotlinShippingDelegate.kt");

        assertEquals(scan(javaRoot, kotlinRoot), scan(kotlinRoot, javaRoot),
            "the delegate order must not depend on the order of the roots");
    }

    @Test
    void aRootListedTwiceIsScannedOnce(@TempDir Path root) throws IOException {
        Path javaRoot = copy(root, "src/main/java", "OrderDelegate.java.txt", "OrderDelegate.java");

        assertEquals(List.of("OrderDelegate"), scan(javaRoot, javaRoot),
            "a duplicate root must not duplicate its delegates");
    }

    @Test
    void aFileReachableFromTwoNestedRootsIsScannedOnce(@TempDir Path root) throws IOException {
        Path javaRoot = copy(root, "src/main/java", "OrderDelegate.java.txt", "OrderDelegate.java");

        assertEquals(List.of("OrderDelegate"), scan(root.resolve("src/main"), javaRoot),
            "a file below two overlapping roots must be reported once");
    }

    @Test
    void aMissingRootIsSkippedSilently(@TempDir Path root) throws IOException {
        Path javaRoot = copy(root, "src/main/java", "OrderDelegate.java.txt", "OrderDelegate.java");

        assertEquals(List.of("OrderDelegate"), scan(root.resolve("src/main/kotlin"), javaRoot),
            "a root that does not exist must neither fail the scan nor hide the other roots");
    }

    @Test
    void scanningWithoutAnyRootYieldsNothing() throws IOException {
        assertTrue(new DelegateScanner(List.of()).scan().isEmpty(),
            "a module without source roots has no delegates");
    }

    private static List<String> scan(Path... sourceDirectories) throws IOException {
        return new DelegateScanner(List.of(sourceDirectories)).scan().stream()
            .map(DelegateType::simpleName)
            .toList();
    }

    private static Path copy(Path root, String sourceRoot, String fixture, String target) {
        Path packageDir = root.resolve(sourceRoot).resolve(PACKAGE_PATH);
        try (InputStream in = DelegateScannerRootsTest.class.getClassLoader()
                .getResourceAsStream("delegates/" + fixture)) {
            Files.createDirectories(packageDir);
            Files.write(packageDir.resolve(target), in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return root.resolve(sourceRoot);
    }
}
