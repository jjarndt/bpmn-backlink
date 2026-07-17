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
import org.gradle.work.DisableCachingByDefault;

/**
 * Rewrites the delegate source files so that their {@code @CalledFrom}
 * annotations match the current BPMN index.
 *
 * <p>Wired to run before {@code compileJava} so the annotations are up to date
 * before the sources are compiled. The run is idempotent: a second invocation
 * without intervening BPMN or source changes performs no write.
 */
@DisableCachingByDefault(because = "update mutates its own inputs; the run must never be skipped")
public abstract class UpdateCalledFromTask extends AbstractBacklinkTask {

    @Override
    protected Mode mode() {
        return Mode.UPDATE;
    }

    @Override
    protected void handleResult(BacklinkResult result) {
        logSummary(result);
    }
}
