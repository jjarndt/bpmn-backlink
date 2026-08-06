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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads and rewrites the {@code @CalledFrom} annotation of a single delegate
 * type in one source language.
 *
 * <p>The type is addressed by its simple name. All implementations are
 * idempotent: applying the same expected value twice produces no second change,
 * and they touch nothing but the annotation and its import.
 */
public interface AnnotationEditor {

    /** The simple name of the annotation this editor maintains. */
    String ANNOTATION_SIMPLE_NAME = "CalledFrom";

    /** The fully qualified name of the annotation this editor maintains. */
    String ANNOTATION_FQN = "net.jakobarndt.bpmnbacklink.annotation.CalledFrom";

    /**
     * @param sourceFile a delegate source file
     * @return whether this editor is responsible for the file
     */
    boolean supports(Path sourceFile);

    /**
     * Reads the BPMN paths currently stored in the {@code @CalledFrom}
     * annotation of the named type.
     *
     * @param sourceFile the delegate source file
     * @param typeName the simple name of the delegate type
     * @return the values found, in source order; an empty list if the type or
     *     the annotation is absent or the annotation carries no value
     * @throws IOException if the file cannot be read
     */
    List<String> readCurrentValues(Path sourceFile, String typeName) throws IOException;

    /**
     * Rewrites the annotation of the named type to carry exactly the expected
     * values, preserving the rest of the file.
     *
     * <p>An empty {@code expected} list removes the annotation, and the import
     * if no other type of the file still uses it.
     *
     * @param sourceFile the delegate source file
     * @param typeName the simple name of the delegate type
     * @param expected the desired BPMN paths (assumed already sorted)
     * @throws IOException if the file cannot be read or written
     * @throws IllegalStateException if the file declares no such type
     */
    void write(Path sourceFile, String typeName, List<String> expected) throws IOException;
}
