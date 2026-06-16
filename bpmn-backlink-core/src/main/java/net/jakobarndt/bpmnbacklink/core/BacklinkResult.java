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
import java.util.List;

/**
 * Outcome of a {@link BacklinkProcessor} run.
 *
 * <p>In {@link Mode#UPDATE} the counters {@link #updated()}, {@link #removed()}
 * and {@link #unchanged()} describe the actions performed and {@link #drift()}
 * is empty. In {@link Mode#CHECK} no file is touched, the counters reflect what
 * <em>would</em> happen and {@link #drift()} lists every mismatch.
 *
 * @param updated number of delegates whose annotation was (or would be) added or changed
 * @param removed number of delegates whose annotation was (or would be) removed
 * @param unchanged number of delegates that already carried the correct annotation
 * @param drift the detected mismatches; always empty in {@link Mode#UPDATE}
 */
public record BacklinkResult(int updated, int removed, int unchanged, List<Drift> drift) {

    /**
     * @param updated number of delegates whose annotation was added or changed
     * @param removed number of delegates whose annotation was removed
     * @param unchanged number of delegates that were already correct
     * @param drift the detected mismatches (empty in {@link Mode#UPDATE})
     */
    public BacklinkResult {
        drift = List.copyOf(drift);
    }

    @Override
    public String toString() {
        return "BacklinkResult{updated=" + updated
            + ", removed=" + removed
            + ", unchanged=" + unchanged
            + ", drift=" + drift.size() + "}";
    }

    /**
     * A single mismatch between the annotation found in a delegate source file
     * and the value expected from the BPMN index.
     *
     * @param javaFile the affected delegate source file
     * @param expected the BPMN paths the annotation should carry, sorted
     * @param actual the BPMN paths the annotation currently carries
     */
    public record Drift(Path javaFile, List<String> expected, List<String> actual) {

        /**
         * @param javaFile the affected delegate source file
         * @param expected the expected, sorted BPMN paths
         * @param actual the BPMN paths currently present
         */
        public Drift {
            expected = List.copyOf(expected);
            actual = List.copyOf(actual);
        }
    }
}
