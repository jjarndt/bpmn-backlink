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
package net.jakobarndt.bpmnbacklink.gradle;

import net.jakobarndt.bpmnbacklink.core.BacklinkConfig;
import net.jakobarndt.bpmnbacklink.core.BacklinkProcessor;
import net.jakobarndt.bpmnbacklink.core.BacklinkResult;
import net.jakobarndt.bpmnbacklink.core.Mode;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.UncheckedIOException;

/**
 * Shared base for the backlink tasks. It holds the common properties, assembles
 * the {@link BacklinkConfig} and runs the {@link BacklinkProcessor} in the mode
 * chosen by the concrete task.
 *
 * <p>The properties are deliberately {@link Internal}: the update task mutates
 * its own inputs (it rewrites the delegate sources it scans), so honest
 * input/output fingerprinting is impossible. The task therefore always runs.
 */
@DisableCachingByDefault(because = "update mutates its own inputs; the run must never be skipped")
public abstract class AbstractBacklinkTask extends DefaultTask {

    /**
     * Marks the task as never up to date so every invocation performs a run.
     */
    protected AbstractBacklinkTask() {
        getOutputs().upToDateWhen(Specs.satisfyNone());
    }

    /**
     * @return the Java source root that is scanned for delegate types
     */
    @Internal
    public abstract DirectoryProperty getSourceDirectory();

    /**
     * @return the root directory below which {@code *.bpmn} files are indexed
     */
    @Internal
    public abstract DirectoryProperty getBpmnDirectory();

    /**
     * @return the root against which indexed BPMN paths are relativized before
     *     they are stored in a {@code @CalledFrom} annotation
     */
    @Internal
    public abstract DirectoryProperty getBpmnReferenceRoot();

    /**
     * @return whether the task skips its work entirely
     */
    @Internal
    public abstract Property<Boolean> getSkip();

    /**
     * Executes the task. Honours {@link #getSkip()} and delegates the actual
     * work to the {@link BacklinkProcessor} configured for {@link #mode()}.
     *
     * @throws GradleException if the run fails for a technical reason (for
     *     example an unreadable or unwritable file)
     */
    @TaskAction
    public final void runBacklink() {
        if (getSkip().get()) {
            getLogger().lifecycle("Skipping bpmn-backlink (skip=true).");
            return;
        }

        BacklinkConfig config = BacklinkConfig.builder()
                .sourceDirectory(getSourceDirectory().get().getAsFile().toPath())
                .bpmnDirectory(getBpmnDirectory().get().getAsFile().toPath())
                .bpmnReferenceRoot(getBpmnReferenceRoot().get().getAsFile().toPath())
                .mode(mode())
                .build();

        BacklinkResult result;
        try {
            result = new BacklinkProcessor(config).run();
        } catch (UncheckedIOException e) {
            // BacklinkProcessor.run() signals every read/write failure (including a
            // malformed BPMN file, which the indexer rewraps as a named IOException)
            // as an UncheckedIOException. Surface it as a technical build failure and
            // unwrap the original IOException as the cause so the offending file shows.
            throw new GradleException("bpmn-backlink failed to read or write a file", e.getCause());
        }

        handleResult(result);
    }

    /**
     * @return the mode the concrete task runs in
     */
    protected abstract Mode mode();

    /**
     * Reacts to a finished run. The default logs a one-line summary; tasks that
     * need richer behaviour (such as failing on drift) override this method and
     * may call {@link #logSummary(BacklinkResult)} to reuse the summary line.
     *
     * @param result the run result
     */
    protected void handleResult(BacklinkResult result) {
        logSummary(result);
    }

    /**
     * Logs the standard one-line summary of a run.
     *
     * @param result the run result
     */
    protected final void logSummary(BacklinkResult result) {
        getLogger().lifecycle("bpmn-backlink: updated=" + result.updated()
                + ", removed=" + result.removed()
                + ", unchanged=" + result.unchanged());
    }
}
