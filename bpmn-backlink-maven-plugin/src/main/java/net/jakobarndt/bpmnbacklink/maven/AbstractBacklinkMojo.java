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
package net.jakobarndt.bpmnbacklink.maven;

import net.jakobarndt.bpmnbacklink.core.BacklinkConfig;
import net.jakobarndt.bpmnbacklink.core.BacklinkProcessor;
import net.jakobarndt.bpmnbacklink.core.BacklinkResult;
import net.jakobarndt.bpmnbacklink.core.Mode;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Shared base for the backlink goals. It holds the common parameters, assembles
 * the {@link BacklinkConfig} and runs the {@link BacklinkProcessor} in the mode
 * chosen by the concrete goal.
 */
public abstract class AbstractBacklinkMojo extends AbstractMojo {

    /**
     * The Java source root that is scanned for delegate types.
     */
    @Parameter(defaultValue = "${project.build.sourceDirectory}", property = "bpmnBacklink.sourceDirectory")
    private File sourceDirectory;

    /**
     * The root directory below which {@code *.bpmn} files are indexed.
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/resources/bpmn/processes",
            property = "bpmnBacklink.bpmnDirectory")
    private File bpmnDirectory;

    /**
     * The root against which indexed BPMN paths are relativized before they are
     * stored in a {@code @CalledFrom} annotation.
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/resources",
            property = "bpmnBacklink.bpmnReferenceRoot")
    private File bpmnReferenceRoot;

    /**
     * Skips the goal entirely when {@code true}.
     */
    @Parameter(defaultValue = "false", property = "bpmnBacklink.skip")
    private boolean skip;

    /**
     * Executes the goal. Honours {@link #skip} and delegates the actual work to
     * the {@link BacklinkProcessor} configured for {@link #mode()}.
     *
     * @throws MojoExecutionException if the run fails for a technical reason
     *     (for example an unreadable or unwritable file)
     * @throws MojoFailureException if a concrete goal turns the result into a
     *     build failure (see {@link CheckCalledFromMojo})
     */
    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping bpmn-backlink (skip=true).");
            return;
        }

        BacklinkConfig config = BacklinkConfig.builder()
                .sourceDirectory(toPath(sourceDirectory))
                .bpmnDirectory(toPath(bpmnDirectory))
                .bpmnReferenceRoot(toPath(bpmnReferenceRoot))
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
            throw new MojoExecutionException("bpmn-backlink failed to read or write a file", e.getCause());
        }

        handleResult(result);
    }

    /**
     * @return the mode the concrete goal runs in
     */
    protected abstract Mode mode();

    /**
     * Reacts to a finished run. The default logs a one-line summary; goals that
     * need richer behaviour (such as failing on drift) override this method and
     * may call {@link #logSummary(BacklinkResult)} to reuse the summary line.
     *
     * @param result the run result
     * @throws MojoFailureException if the goal turns the result into a failure
     */
    protected void handleResult(BacklinkResult result) throws MojoFailureException {
        logSummary(result);
    }

    /**
     * Logs the standard one-line summary of a run.
     *
     * @param result the run result
     */
    protected final void logSummary(BacklinkResult result) {
        getLog().info("bpmn-backlink: updated=" + result.updated()
                + ", removed=" + result.removed()
                + ", unchanged=" + result.unchanged());
    }

    private static Path toPath(File file) {
        return file.toPath();
    }
}
