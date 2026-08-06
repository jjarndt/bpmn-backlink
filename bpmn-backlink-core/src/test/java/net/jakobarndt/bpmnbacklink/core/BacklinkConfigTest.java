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
package net.jakobarndt.bpmnbacklink.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BacklinkConfigTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java");
    private static final Path KOTLIN_ROOT = Path.of("src/main/kotlin");
    private static final Path BPMN_DIR = Path.of("src/main/resources/bpmn/processes");
    private static final Path REFERENCE_ROOT = Path.of("src/main/resources");

    @Test
    void keepsTheSourceRootsInTheGivenOrder() {
        BacklinkConfig config = builder().sourceDirectories(List.of(KOTLIN_ROOT, JAVA_ROOT)).build();

        assertEquals(List.of(KOTLIN_ROOT, JAVA_ROOT), config.sourceDirectories());
    }

    @Test
    void collapsesDuplicateSourceRoots() {
        BacklinkConfig config =
            builder().sourceDirectories(List.of(JAVA_ROOT, KOTLIN_ROOT, JAVA_ROOT)).build();

        assertEquals(List.of(JAVA_ROOT, KOTLIN_ROOT), config.sourceDirectories(),
            "a root listed twice must be scanned once, keeping its first position");
    }

    @Test
    void sourceRootsAreDetachedFromTheCallersList() {
        List<Path> roots = new ArrayList<>(List.of(JAVA_ROOT));
        BacklinkConfig config = builder().sourceDirectories(roots).build();
        roots.add(KOTLIN_ROOT);

        assertEquals(List.of(JAVA_ROOT), config.sourceDirectories(),
            "the config must not observe later changes to the list it was built from");
    }

    @Test
    void sourceRootsAreImmutable() {
        BacklinkConfig config = builder().sourceDirectories(List.of(JAVA_ROOT)).build();

        assertThrows(UnsupportedOperationException.class,
            () -> config.sourceDirectories().add(KOTLIN_ROOT));
    }

    @Test
    @SuppressWarnings("deprecation")
    void theDeprecatedSingleRootSetterYieldsAOneElementList() {
        BacklinkConfig config = builder().sourceDirectory(JAVA_ROOT).build();

        assertEquals(List.of(JAVA_ROOT), config.sourceDirectories());
    }

    @Test
    void collapsesTwoSpellingsOfTheSameSourceRoot() {
        BacklinkConfig config = builder()
            .sourceDirectories(List.of(JAVA_ROOT, Path.of("src/main/kotlin/../java")))
            .build();

        assertEquals(List.of(JAVA_ROOT), config.sourceDirectories(),
            "roots are normalized before they are compared");
    }

    @Test
    void rejectsANullSourceRoot() {
        BacklinkConfig.Builder builder = builder().sourceDirectories(Arrays.asList(JAVA_ROOT, null));

        NullPointerException thrown = assertThrows(NullPointerException.class, builder::build);
        assertEquals("sourceDirectories", thrown.getMessage(),
            "a null root must be reported like every other missing value");
    }

    @Test
    @SuppressWarnings("deprecation")
    void rejectsANullSingleSourceRoot() {
        BacklinkConfig.Builder builder = builder().sourceDirectory(null);

        NullPointerException thrown = assertThrows(NullPointerException.class, builder::build);
        assertEquals("sourceDirectories", thrown.getMessage());
    }

    @Test
    void rejectsMissingSourceRoots() {
        BacklinkConfig.Builder builder = builder();

        NullPointerException thrown = assertThrows(NullPointerException.class, builder::build);
        assertEquals("sourceDirectories", thrown.getMessage());
    }

    @Test
    void rejectsMissingBpmnDirectory() {
        BacklinkConfig.Builder builder = BacklinkConfig.builder()
            .sourceDirectories(List.of(JAVA_ROOT))
            .bpmnReferenceRoot(REFERENCE_ROOT);

        NullPointerException thrown = assertThrows(NullPointerException.class, builder::build);
        assertEquals("bpmnDirectory", thrown.getMessage());
    }

    @Test
    void rejectsMissingBpmnReferenceRoot() {
        BacklinkConfig.Builder builder = BacklinkConfig.builder()
            .sourceDirectories(List.of(JAVA_ROOT))
            .bpmnDirectory(BPMN_DIR);

        NullPointerException thrown = assertThrows(NullPointerException.class, builder::build);
        assertEquals("bpmnReferenceRoot", thrown.getMessage());
    }

    @Test
    void rejectsAMissingMode() {
        BacklinkConfig.Builder builder = builder()
            .sourceDirectories(List.of(JAVA_ROOT))
            .mode(null);

        NullPointerException thrown = assertThrows(NullPointerException.class, builder::build);
        assertEquals("mode", thrown.getMessage());
    }

    @Test
    void defaultsToUpdateMode() {
        BacklinkConfig config = builder().sourceDirectories(List.of(JAVA_ROOT)).build();

        assertEquals(Mode.UPDATE, config.mode());
    }

    private static BacklinkConfig.Builder builder() {
        return BacklinkConfig.builder()
            .bpmnDirectory(BPMN_DIR)
            .bpmnReferenceRoot(REFERENCE_ROOT);
    }
}
