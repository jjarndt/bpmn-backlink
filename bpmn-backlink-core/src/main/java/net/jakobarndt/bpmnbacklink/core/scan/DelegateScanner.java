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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Finds concrete delegate types in a source tree without loading any class.
 *
 * <p>The tree may mix languages: every file is handed to the
 * {@link SourceScanner} that claims its extension, currently
 * {@link JavaSourceScanner} for {@code .java} and {@link KotlinSourceScanner}
 * for {@code .kt}. Files that no scanner claims are skipped.
 */
public final class DelegateScanner {

    private static final List<SourceScanner> SCANNERS =
        List.of(new JavaSourceScanner(), new KotlinSourceScanner());

    private final Path sourceDirectory;

    /**
     * @param sourceDirectory the source root to scan
     */
    public DelegateScanner(Path sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
    }

    /**
     * Walks the source tree and collects every concrete delegate type.
     *
     * @return the discovered delegate types, in ascending file-path order
     * @throws IOException if the source tree cannot be walked or a source file
     *     cannot be read
     */
    public List<DelegateType> scan() throws IOException {
        List<DelegateType> result = new ArrayList<>();
        if (!Files.isDirectory(sourceDirectory)) {
            return result;
        }
        List<Path> sourceFiles;
        try (Stream<Path> paths = Files.walk(sourceDirectory)) {
            sourceFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> scannerFor(path).isPresent())
                .sorted()
                .toList();
        }
        for (Path sourceFile : sourceFiles) {
            result.addAll(scannerFor(sourceFile).orElseThrow().scan(sourceFile));
        }
        return result;
    }

    private static Optional<SourceScanner> scannerFor(Path sourceFile) {
        return SCANNERS.stream()
            .filter(scanner -> scanner.supports(sourceFile))
            .findFirst();
    }
}
