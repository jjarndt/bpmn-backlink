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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers what the edited file must look like apart from the annotation itself:
 * the line endings of the lines the writer adds, and the line an annotation
 * leaves behind when it is removed.
 */
class KotlinAnnotationWriterSourceFidelityTest {

    private final KotlinAnnotationWriter writer = new KotlinAnnotationWriter();

    private String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    void takesTheLineEndingFromTheFileAndNotFromAStringLiteral(@TempDir Path root) throws IOException {
        Path file = copy(this, root, "KotlinRawStringCrlfDelegate", "KotlinRawStringCrlfDelegate.kt");

        writer.write(file, "KotlinRawStringCrlfDelegate", List.of("bpmn/processes/order.bpmn"));

        String content = read(file);
        assertTrue(content.contains("import net.jakobarndt.bpmnbacklink.annotation.CalledFrom\n"),
            "the inserted import must end the way every line of this file ends:\n"
                + content.replace("\r", "<CR>"));
        assertTrue(content.contains("@CalledFrom(\"bpmn/processes/order.bpmn\")\nclass KotlinRawStringCrlfDelegate"),
            "the inserted annotation must end the way every line of this file ends:\n"
                + content.replace("\r", "<CR>"));
    }

    @Test
    void leavesNoTrailingBlankOnTheLineOfARemovedAnnotation(@TempDir Path root) throws IOException {
        Path file = copy(this, root, "KotlinAdjacentAnnotationDelegate", "KotlinAdjacentAnnotationDelegate.kt");

        writer.write(file, "KotlinAdjacentAnnotationDelegate", List.of());

        String content = read(file);
        assertTrue(content.contains("@Deprecated\nclass KotlinAdjacentAnnotationDelegate"),
            "the separating blank must go with the annotation it separated:\n"
                + content.replace(" \n", "<SP>\n"));
    }
}
