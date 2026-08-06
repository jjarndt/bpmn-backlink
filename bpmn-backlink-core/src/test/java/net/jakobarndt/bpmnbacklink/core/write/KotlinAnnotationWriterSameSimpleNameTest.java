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
package net.jakobarndt.bpmnbacklink.core.write;

import net.jakobarndt.bpmnbacklink.core.scan.DelegateScanner;
import net.jakobarndt.bpmnbacklink.core.scan.DelegateType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the defined behaviour for a Kotlin file that nests two delegates of the
 * same simple name in different objects, so the reported name no longer
 * addresses a single declaration. The editor API is type-addressed by simple
 * name, so the first declaration in source order wins and the second one is
 * left untouched, exactly as documented for the Java editor.
 */
class KotlinAnnotationWriterSameSimpleNameTest {

    private final KotlinAnnotationWriter writer = new KotlinAnnotationWriter();

    private Path copyIntoSourceRoot(Path root) {
        Path packageDir = root.resolve("src/main/kotlin/net/example/delegate");
        try (InputStream in = getClass().getClassLoader()
            .getResourceAsStream("delegates/KotlinSameNameDelegates.kt.txt")) {
            Files.createDirectories(packageDir);
            Path file = packageDir.resolve("KotlinSameNameDelegates.kt");
            Files.write(file, in.readAllBytes());
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void anAmbiguousSimpleNameResolvesToTheFirstDeclarationInSourceOrder(@TempDir Path root)
        throws IOException {
        Path file = copyIntoSourceRoot(root);
        List<DelegateType> found = new DelegateScanner(root.resolve("src/main/kotlin")).scan();

        assertEquals(2, found.size(), "both nested delegates are reported: " + found);
        for (DelegateType delegate : found) {
            writer.write(file, delegate.simpleName(), List.of("bpmn/processes/order.bpmn"));
        }

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertEquals(1, content.split("@CalledFrom", -1).length - 1,
            "both writes address the first declaration of that name:\n" + content);
        assertTrue(content.contains("object KotlinShipping {\n\n    class KotlinNamesakeDelegate"),
            "the second declaration of that name stays untouched:\n" + content);
    }
}
