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

import java.nio.file.Path;
import java.util.List;

/**
 * Selects the {@link AnnotationEditor} responsible for a delegate source file.
 */
public final class AnnotationEditors {

    private static final List<AnnotationEditor> EDITORS =
        List.of(new AnnotationWriter(), new KotlinAnnotationWriter());

    private AnnotationEditors() {
    }

    /**
     * @param sourceFile a delegate source file
     * @return the editor for the file's language
     * @throws IllegalArgumentException if no editor handles the file
     */
    public static AnnotationEditor forFile(Path sourceFile) {
        return EDITORS.stream()
            .filter(editor -> editor.supports(sourceFile))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No annotation editor for " + sourceFile));
    }
}
