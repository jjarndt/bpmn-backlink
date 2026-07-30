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
import java.nio.file.Path;
import java.util.List;

/**
 * Finds the delegate types declared in a single source file of one language.
 *
 * <p>The implementations are selected by file extension, which keeps the
 * language-specific parsing out of {@link DelegateScanner}.
 */
public interface SourceScanner {

    /**
     * @param sourceFile a file of the scanned source tree
     * @return whether this scanner is responsible for the file
     */
    boolean supports(Path sourceFile);

    /**
     * @param sourceFile a file this scanner {@linkplain #supports(Path) supports}
     * @return the concrete delegate types declared in the file, in source order
     * @throws IOException if the file cannot be read
     */
    List<DelegateType> scan(Path sourceFile) throws IOException;
}
