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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelegateScannerTest {

    private Path sourceRoot;

    private void copyDelegate(Path root, String fixture, String target) {
        Path packageDir = root.resolve("src/main/java/net/example/delegate");
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("delegates/" + fixture + ".java.txt")) {
            Files.createDirectories(packageDir);
            Files.write(packageDir.resolve(target), in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.sourceRoot = root.resolve("src/main/java");
    }

    @Test
    void detectsInterfaceImplementingDelegate(@TempDir Path root) throws IOException {
        copyDelegate(root, "OrderDelegate", "OrderDelegate.java");
        List<DelegateType> found = new DelegateScanner(sourceRoot).scan();
        Set<String> names = found.stream().map(DelegateType::simpleName).collect(Collectors.toSet());
        assertTrue(names.contains("OrderDelegate"));
    }

    @Test
    void detectsAbstractJavaDelegateSubclass(@TempDir Path root) throws IOException {
        copyDelegate(root, "PaymentDelegate", "PaymentDelegate.java");
        List<DelegateType> found = new DelegateScanner(sourceRoot).scan();
        Set<String> names = found.stream().map(DelegateType::simpleName).collect(Collectors.toSet());
        assertTrue(names.contains("PaymentDelegate"));
    }

    @Test
    void ignoresAbstractClassEvenIfImplementingDelegate(@TempDir Path root) throws IOException {
        copyDelegate(root, "AbstractJavaDelegate", "AbstractJavaDelegate.java");
        List<DelegateType> found = new DelegateScanner(sourceRoot).scan();
        assertTrue(found.isEmpty(), "abstract delegate base must not be reported");
    }

    @Test
    void ignoresPlainClass(@TempDir Path root) throws IOException {
        copyDelegate(root, "NotADelegate", "NotADelegate.java");
        List<DelegateType> found = new DelegateScanner(sourceRoot).scan();
        assertTrue(found.isEmpty());
    }

    @Test
    void delegateReferenceIsCamelCasedSimpleName(@TempDir Path root) throws IOException {
        copyDelegate(root, "PaymentDelegate", "PaymentDelegate.java");
        DelegateType type = new DelegateScanner(sourceRoot).scan().get(0);
        assertEquals("paymentDelegate", type.delegateReference());
    }

    @Test
    void scanningMissingSourceRootYieldsEmptyList(@TempDir Path root) throws IOException {
        List<DelegateType> found = new DelegateScanner(root.resolve("does/not/exist")).scan();
        assertTrue(found.isEmpty());
    }

    @Test
    void scansMultipleFiles(@TempDir Path root) throws IOException {
        copyDelegate(root, "OrderDelegate", "OrderDelegate.java");
        copyDelegate(root, "ShippingDelegate", "ShippingDelegate.java");
        copyDelegate(root, "NotADelegate", "NotADelegate.java");
        List<DelegateType> found = new DelegateScanner(sourceRoot).scan();
        Set<String> names = found.stream().map(DelegateType::simpleName).collect(Collectors.toSet());
        assertTrue(names.contains("OrderDelegate"));
        assertTrue(names.contains("ShippingDelegate"));
        assertFalse(names.contains("NotADelegate"));
        assertEquals(2, found.size());
    }

    @Test
    void ignoresInterfaceThatExtendsDelegate(@TempDir Path root) throws IOException {
        copyDelegate(root, "DelegateInterface", "DelegateInterface.java");
        List<DelegateType> found = new DelegateScanner(sourceRoot).scan();
        assertTrue(found.isEmpty(), "an interface is not a concrete delegate even if it extends JavaDelegate");
    }

    @Test
    void detectsDelegateWhenNonDelegateInterfaceIsDeclaredFirst(@TempDir Path root) throws IOException {
        copyDelegate(root, "RunnableDelegate", "RunnableDelegate.java");
        List<DelegateType> found = new DelegateScanner(sourceRoot).scan();
        Set<String> names = found.stream().map(DelegateType::simpleName).collect(Collectors.toSet());
        assertTrue(names.contains("RunnableDelegate"),
            "scanner must look past the first non-delegate interface and still match JavaDelegate");
    }

    @Test
    void ignoresClassImplementingOnlyNonDelegateInterface(@TempDir Path root) throws IOException {
        copyDelegate(root, "PlainRunnable", "PlainRunnable.java");
        List<DelegateType> found = new DelegateScanner(sourceRoot).scan();
        assertTrue(found.isEmpty(), "a class with only non-delegate interfaces must be ignored");
    }

    @Test
    void skipsUnparsableSourceFile(@TempDir Path root) throws IOException {
        Path packageDir = root.resolve("src/main/java/net/example/delegate");
        Files.createDirectories(packageDir);
        // Copy a real delegate so the scan would normally find something.
        copyDelegate(root, "OrderDelegate", "OrderDelegate.java");
        // Add a syntactically broken file that JavaParser cannot parse.
        Files.writeString(
            packageDir.resolve("Broken.java"),
            "package net.example.delegate; class Broken { this is not valid java",
            StandardCharsets.UTF_8);

        List<DelegateType> found = new DelegateScanner(sourceRoot).scan();

        Set<String> names = found.stream().map(DelegateType::simpleName).collect(Collectors.toSet());
        assertTrue(names.contains("OrderDelegate"), "valid delegate must still be found");
        assertFalse(names.contains("Broken"), "unparsable file must be skipped silently");
        assertEquals(1, found.size(), "only the parsable delegate is reported");
    }
}
