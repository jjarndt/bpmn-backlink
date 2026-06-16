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

import net.jakobarndt.bpmnbacklink.core.bpmn.BpmnDelegateIndexer;
import net.jakobarndt.bpmnbacklink.core.scan.DelegateScanner;
import net.jakobarndt.bpmnbacklink.core.scan.DelegateType;
import net.jakobarndt.bpmnbacklink.core.write.AnnotationWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;

/**
 * Orchestrates a single backlink run: index the BPMN files, scan the delegate
 * sources and reconcile each delegate's {@code @CalledFrom} annotation with the
 * expected value.
 *
 * <p>Behaviour is governed by {@link BacklinkConfig#mode()}:
 * <ul>
 *   <li>{@link Mode#UPDATE} rewrites the affected source files and counts the
 *       additions/changes ({@code updated}), removals ({@code removed}) and
 *       files already correct ({@code unchanged});</li>
 *   <li>{@link Mode#CHECK} never writes a file; every mismatch is reported in
 *       {@link BacklinkResult#drift()} while the counters describe what an
 *       {@code UPDATE} run would have done.</li>
 * </ul>
 *
 * <p>The expected annotation value is the sorted set of BPMN paths referencing
 * the delegate. A delegate whose annotation already equals that sorted value is
 * left untouched, so a second {@code UPDATE} run produces no further change.
 */
public final class BacklinkProcessor {

    private final BacklinkConfig config;
    private final BpmnDelegateIndexer indexer;
    private final DelegateScanner scanner;
    private final AnnotationWriter writer;

    /**
     * @param config the run configuration; must not be {@code null}
     */
    public BacklinkProcessor(BacklinkConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.indexer = new BpmnDelegateIndexer(config.bpmnDirectory(), config.bpmnReferenceRoot());
        this.scanner = new DelegateScanner(config.sourceDirectory());
        this.writer = new AnnotationWriter();
    }

    /**
     * Executes the run.
     *
     * @return the aggregated result
     * @throws UncheckedIOException if a BPMN or Java file cannot be read or written
     */
    public BacklinkResult run() {
        try {
            return runChecked();
        } catch (IOException e) {
            throw new UncheckedIOException("Backlink run failed", e);
        }
    }

    private BacklinkResult runChecked() throws IOException {
        Map<String, SortedSet<String>> index = indexer.index();
        List<DelegateType> delegates = scanner.scan();

        int updated = 0;
        int removed = 0;
        int unchanged = 0;
        List<BacklinkResult.Drift> drift = new ArrayList<>();

        for (DelegateType delegate : delegates) {
            List<String> expected = expectedFor(delegate, index);
            List<String> current = writer.readCurrentValues(delegate.sourceFile());

            if (expected.equals(current)) {
                unchanged++;
                continue;
            }

            boolean removal = expected.isEmpty();
            if (config.mode() == Mode.CHECK) {
                drift.add(new BacklinkResult.Drift(delegate.sourceFile(), expected, current));
            } else {
                writer.write(delegate.sourceFile(), expected);
            }

            if (removal) {
                removed++;
            } else {
                updated++;
            }
        }

        return new BacklinkResult(updated, removed, unchanged, drift);
    }

    private List<String> expectedFor(DelegateType delegate, Map<String, SortedSet<String>> index) {
        // The index never stores an empty set: a reference appears only once a
        // BPMN path has been added for it, so a null lookup is the only "absent" case.
        SortedSet<String> references = index.get(delegate.delegateReference());
        if (references == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(references);
    }
}
