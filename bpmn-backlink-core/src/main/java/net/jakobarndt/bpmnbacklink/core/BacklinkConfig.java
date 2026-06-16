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

import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable configuration for a {@link BacklinkProcessor} run.
 *
 * <p>Instances are created through {@link #builder()}.
 *
 * @param sourceDirectory the Java source root that is scanned for delegate types
 *     (for example {@code src/main/java})
 * @param bpmnDirectory the root directory below which {@code *.bpmn} files are indexed
 * @param bpmnReferenceRoot the root against which indexed BPMN paths are relativized
 *     before they are stored in an annotation (typically {@code src/main/resources})
 * @param mode the operating mode
 */
public record BacklinkConfig(Path sourceDirectory, Path bpmnDirectory, Path bpmnReferenceRoot, Mode mode) {

    /**
     * @param sourceDirectory the Java source root to scan
     * @param bpmnDirectory the root directory of the BPMN files to index
     * @param bpmnReferenceRoot the root against which BPMN paths are relativized
     * @param mode the operating mode
     * @throws NullPointerException if any argument is {@code null}
     */
    public BacklinkConfig {
        Objects.requireNonNull(sourceDirectory, "sourceDirectory");
        Objects.requireNonNull(bpmnDirectory, "bpmnDirectory");
        Objects.requireNonNull(bpmnReferenceRoot, "bpmnReferenceRoot");
        Objects.requireNonNull(mode, "mode");
    }

    /**
     * @return a new, empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link BacklinkConfig}.
     */
    public static final class Builder {

        private Path sourceDirectory;
        private Path bpmnDirectory;
        private Path bpmnReferenceRoot;
        private Mode mode = Mode.UPDATE;

        private Builder() {
        }

        /**
         * @param sourceDirectory the Java source root to scan
         * @return this builder
         */
        public Builder sourceDirectory(Path sourceDirectory) {
            this.sourceDirectory = sourceDirectory;
            return this;
        }

        /**
         * @param bpmnDirectory the root directory of the BPMN files to index
         * @return this builder
         */
        public Builder bpmnDirectory(Path bpmnDirectory) {
            this.bpmnDirectory = bpmnDirectory;
            return this;
        }

        /**
         * @param bpmnReferenceRoot the root against which BPMN paths are relativized
         * @return this builder
         */
        public Builder bpmnReferenceRoot(Path bpmnReferenceRoot) {
            this.bpmnReferenceRoot = bpmnReferenceRoot;
            return this;
        }

        /**
         * @param mode the operating mode; defaults to {@link Mode#UPDATE}
         * @return this builder
         */
        public Builder mode(Mode mode) {
            this.mode = mode;
            return this;
        }

        /**
         * @return the configured, immutable {@link BacklinkConfig}
         * @throws NullPointerException if a required field was not set
         */
        public BacklinkConfig build() {
            return new BacklinkConfig(sourceDirectory, bpmnDirectory, bpmnReferenceRoot, mode);
        }
    }
}
