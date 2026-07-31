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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable configuration for a {@link BacklinkProcessor} run.
 *
 * <p>Instances are created through {@link #builder()}.
 *
 * @param sourceDirectories the source roots that are scanned for delegate types
 *     (for example {@code src/main/java} and {@code src/main/kotlin}); duplicate
 *     roots are collapsed and the remaining order is preserved
 * @param bpmnDirectory the root directory below which {@code *.bpmn} files are indexed
 * @param bpmnReferenceRoot the root against which indexed BPMN paths are relativized
 *     before they are stored in an annotation (typically {@code src/main/resources})
 * @param mode the operating mode
 */
public record BacklinkConfig(List<Path> sourceDirectories, Path bpmnDirectory, Path bpmnReferenceRoot, Mode mode) {

    /**
     * @param sourceDirectories the source roots to scan
     * @param bpmnDirectory the root directory of the BPMN files to index
     * @param bpmnReferenceRoot the root against which BPMN paths are relativized
     * @param mode the operating mode
     * @throws NullPointerException if any argument or source root is {@code null}
     */
    public BacklinkConfig {
        Objects.requireNonNull(sourceDirectories, "sourceDirectories");
        Objects.requireNonNull(bpmnDirectory, "bpmnDirectory");
        Objects.requireNonNull(bpmnReferenceRoot, "bpmnReferenceRoot");
        Objects.requireNonNull(mode, "mode");
        sourceDirectories = List.copyOf(new LinkedHashSet<>(sourceDirectories));
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

        private List<Path> sourceDirectories;
        private Path bpmnDirectory;
        private Path bpmnReferenceRoot;
        private Mode mode = Mode.UPDATE;

        private Builder() {
        }

        /**
         * @param sourceDirectories the source roots to scan
         * @return this builder
         */
        public Builder sourceDirectories(List<Path> sourceDirectories) {
            this.sourceDirectories = sourceDirectories;
            return this;
        }

        /**
         * Convenience for the single-root case.
         *
         * @param sourceDirectory the source root to scan
         * @return this builder
         * @deprecated use {@link #sourceDirectories(List)}; a build may have more
         *     than one source root (for example {@code src/main/java} and
         *     {@code src/main/kotlin})
         */
        @Deprecated(since = "0.2.0")
        public Builder sourceDirectory(Path sourceDirectory) {
            return sourceDirectories(Collections.singletonList(sourceDirectory));
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
            return new BacklinkConfig(sourceDirectories, bpmnDirectory, bpmnReferenceRoot, mode);
        }
    }
}
