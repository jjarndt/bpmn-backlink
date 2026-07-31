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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static net.jakobarndt.bpmnbacklink.core.write.KotlinAnnotationWriterTest.copy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the import forms that Kotlin allows besides the plain one: an alias
 * import, which binds another name than {@code CalledFrom}, and an import
 * terminated by a semicolon. Both decide whether the written annotation
 * resolves and whether a foreign import survives.
 */
class KotlinAnnotationWriterImportResolutionTest {

    private final KotlinAnnotationWriter writer = new KotlinAnnotationWriter();

    private String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    void addsThePlainImportBesideAnAliasImport(@TempDir Path root) throws IOException {
        Path file = copy(this, root, "KotlinAliasImportDelegate", "KotlinAliasImportDelegate.kt");

        writer.write(file, "KotlinAliasImportDelegate", List.of("bpmn/processes/order.bpmn"));

        String content = read(file);
        assertTrue(content.contains("@CalledFrom(\"bpmn/processes/order.bpmn\")"),
            "the annotation is written under its simple name:\n" + content);
        assertTrue(content.contains("import net.jakobarndt.bpmnbacklink.annotation.CalledFrom\n"),
            "an alias import binds another name, so the simple name still needs its own import:\n" + content);
    }

    @Test
    void keepsAnAliasImportThatAnotherTypeStillUses(@TempDir Path root) throws IOException {
        Path file = copy(this, root, "KotlinAliasedUsageDelegate", "KotlinAliasedUsageDelegate.kt");

        writer.write(file, "KotlinAliasedUsageDelegate", List.of());

        String content = read(file);
        assertTrue(content.contains("import net.jakobarndt.bpmnbacklink.annotation.CalledFrom as Backlink"),
            "the alias import is still needed by the neighbouring type:\n" + content);
    }

    @Test
    void keepsTheImportWhileTheAnnotationTypeIsStillReferenced(@TempDir Path root) throws IOException {
        Path file = copy(this, root, "KotlinReflectiveDelegate", "KotlinReflectiveDelegate.kt");

        writer.write(file, "KotlinReflectiveDelegate", List.of());

        String content = read(file);
        assertTrue(content.contains("val annotationType = CalledFrom::class.java"),
            "the foreign reference must survive:\n" + content);
        assertTrue(content.contains("import net.jakobarndt.bpmnbacklink.annotation.CalledFrom"),
            "the import is still needed by the class reference:\n" + content);
    }

    @Test
    void doesNotDuplicateAnImportTerminatedByASemicolon(@TempDir Path root) throws IOException {
        Path file = copy(this, root, "KotlinSemicolonImportDelegate", "KotlinSemicolonImportDelegate.kt");

        writer.write(file, "KotlinSemicolonImportDelegate", List.of("bpmn/processes/order.bpmn"));

        String content = read(file);
        assertEquals(1, content.split("import net\\.jakobarndt", -1).length - 1,
            "the annotation is already imported, so no second import may be added:\n" + content);
    }
}
