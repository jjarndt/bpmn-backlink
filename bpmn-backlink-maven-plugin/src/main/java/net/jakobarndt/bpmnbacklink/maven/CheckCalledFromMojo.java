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

import net.jakobarndt.bpmnbacklink.core.BacklinkResult;
import net.jakobarndt.bpmnbacklink.core.Mode;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.List;

/**
 * Verifies that the {@code @CalledFrom} annotations are in sync with the BPMN
 * index without writing any file. Suitable for CI: it fails the build when a
 * delegate's annotation drifts from the value an {@code update} run would set.
 *
 * <p>Bound to {@code verify}. Each drift is logged with the affected file and
 * its expected versus actual annotation values. Whether drift fails the build
 * is controlled by {@link #failOnDrift}.
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class CheckCalledFromMojo extends AbstractBacklinkMojo {

    /**
     * Fails the build when drift is detected. When {@code false}, drift is only
     * logged as a warning and the build continues.
     */
    @Parameter(defaultValue = "true", property = "bpmnBacklink.failOnDrift")
    private boolean failOnDrift;

    @Override
    protected Mode mode() {
        return Mode.CHECK;
    }

    @Override
    protected void handleResult(BacklinkResult result) throws MojoFailureException {
        logSummary(result);

        List<BacklinkResult.Drift> drift = result.drift();
        if (drift.isEmpty()) {
            getLog().info("bpmn-backlink: no @CalledFrom drift detected.");
            return;
        }

        for (BacklinkResult.Drift d : drift) {
            getLog().warn("bpmn-backlink drift in " + d.javaFile());
            getLog().warn("  expected: " + d.expected());
            getLog().warn("  actual:   " + d.actual());
        }

        String message = "bpmn-backlink: detected " + drift.size()
                + " @CalledFrom drift(s); run 'mvn bpmn-backlink:update' to fix.";
        if (failOnDrift) {
            throw new MojoFailureException(message);
        }
        getLog().warn(message + " (failOnDrift=false, continuing)");
    }
}
