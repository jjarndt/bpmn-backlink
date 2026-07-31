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
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Finds concrete delegate types in one or more source trees without loading any
 * class.
 *
 * <p>A tree may mix languages: every file is handed to the
 * {@link SourceScanner} that claims its extension, currently
 * {@link JavaSourceScanner} for {@code .java} and {@link KotlinSourceScanner}
 * for {@code .kt}. Files that no scanner claims are skipped.
 *
 * <p>The roots are collected before anything is parsed, so the reported order is
 * the global file-path order across all of them rather than root-by-root. A root
 * that does not exist is skipped silently, and a file reachable from two roots
 * (nested roots, or two spellings of the same root) is scanned once.
 *
 * <p>A root that is itself a symbolic link is followed to the directory it points
 * at; symbolic links further down a tree are not followed, which keeps a linked
 * cycle from ending the scan.
 */
public final class DelegateScanner {

    private static final List<SourceScanner> SCANNERS =
        List.of(new JavaSourceScanner(), new KotlinSourceScanner());

    private final List<Path> sourceDirectories;

    /**
     * @param sourceDirectories the source roots to scan
     */
    public DelegateScanner(List<Path> sourceDirectories) {
        this.sourceDirectories = sourceDirectories.stream().map(Path::normalize).distinct().toList();
    }

    /**
     * Walks the source trees and collects every concrete delegate type.
     *
     * @return the discovered delegate types, in ascending file-path order across
     *     all source roots
     * @throws IOException if a source tree cannot be walked or a source file
     *     cannot be read
     */
    public List<DelegateType> scan() throws IOException {
        List<DelegateType> result = new ArrayList<>();
        for (Path sourceFile : collectSourceFiles()) {
            result.addAll(scannerFor(sourceFile).orElseThrow().scan(sourceFile));
        }
        return result;
    }

    private SortedSet<Path> collectSourceFiles() throws IOException {
        SortedSet<Path> sourceFiles = new TreeSet<>();
        for (Path sourceDirectory : sourceDirectories) {
            if (Files.isDirectory(sourceDirectory)) {
                collectSourceFiles(walkableRoot(sourceDirectory), sourceFiles);
            }
        }
        return sourceFiles;
    }

    // Files.walk reads the attributes of its starting point without following
    // links, so a root that is a link would be reported as a single entry and its
    // tree would never be entered.
    private static Path walkableRoot(Path sourceDirectory) throws IOException {
        if (Files.isSymbolicLink(sourceDirectory)) {
            return sourceDirectory.toRealPath();
        }
        return sourceDirectory;
    }

    private static void collectSourceFiles(Path sourceDirectory, SortedSet<Path> sourceFiles) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceDirectory)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> scannerFor(path).isPresent())
                .forEach(sourceFiles::add);
        }
    }

    private static Optional<SourceScanner> scannerFor(Path sourceFile) {
        return SCANNERS.stream()
            .filter(scanner -> scanner.supports(sourceFile))
            .findFirst();
    }
}
