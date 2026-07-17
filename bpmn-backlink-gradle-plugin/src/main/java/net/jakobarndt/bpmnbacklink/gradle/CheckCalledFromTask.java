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

import net.jakobarndt.bpmnbacklink.core.BacklinkResult;
import net.jakobarndt.bpmnbacklink.core.Mode;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.VerificationException;
import org.gradle.work.DisableCachingByDefault;

import java.util.List;

/**
 * Verifies that the {@code @CalledFrom} annotations are in sync with the BPMN
 * index without writing any file. Suitable for CI: it fails the build when a
 * delegate's annotation drifts from the value an {@code update} run would set.
 *
 * <p>Attached to the {@code check} lifecycle. Each drift is logged with the
 * affected file and its expected versus actual annotation values. Whether drift
 * fails the build is controlled by {@link #getFailOnDrift()}.
 */
@DisableCachingByDefault(because = "update mutates its own inputs; the run must never be skipped")
public abstract class CheckCalledFromTask extends AbstractBacklinkTask {

    /**
     * @return whether drift fails the build; when {@code false}, drift is only
     *     logged as a warning and the build continues
     */
    @Internal
    public abstract Property<Boolean> getFailOnDrift();

    @Override
    protected Mode mode() {
        return Mode.CHECK;
    }

    @Override
    protected void handleResult(BacklinkResult result) {
        logSummary(result);

        List<BacklinkResult.Drift> drift = result.drift();
        if (drift.isEmpty()) {
            getLogger().lifecycle("bpmn-backlink: no @CalledFrom drift detected.");
            return;
        }

        for (BacklinkResult.Drift d : drift) {
            getLogger().warn("bpmn-backlink drift in " + d.javaFile());
            getLogger().warn("  expected: " + d.expected());
            getLogger().warn("  actual:   " + d.actual());
        }

        String message = "bpmn-backlink: detected " + drift.size()
                + " @CalledFrom drift(s); run './gradlew bpmnBacklinkUpdate' to fix.";
        if (getFailOnDrift().get()) {
            throw new VerificationException(message);
        }
        getLogger().warn(message + " (failOnDrift=false, continuing)");
    }
}
