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

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacklinkResultTest {

    @Test
    void toStringSummarisesCountersAndDriftSize() {
        BacklinkResult.Drift drift = new BacklinkResult.Drift(
            Path.of("Sample.java"),
            List.of("bpmn/a.bpmn"),
            List.of());
        BacklinkResult result = new BacklinkResult(2, 1, 3, List.of(drift));

        assertEquals("BacklinkResult{updated=2, removed=1, unchanged=3, drift=1}", result.toString());
    }

    @Test
    void accessorsExposeConstructorValues() {
        BacklinkResult result = new BacklinkResult(5, 4, 7, List.of());

        assertEquals(5, result.updated());
        assertEquals(4, result.removed());
        assertEquals(7, result.unchanged());
        assertTrue(result.drift().isEmpty());
    }

    @Test
    void driftListIsCopiedDefensively() {
        BacklinkResult result = new BacklinkResult(0, 0, 0, List.of());

        assertThrows(UnsupportedOperationException.class,
            () -> result.drift().add(new BacklinkResult.Drift(Path.of("x.java"), List.of(), List.of())));
    }

    @Test
    void driftRecordCopiesItsListsDefensively() {
        BacklinkResult.Drift drift = new BacklinkResult.Drift(
            Path.of("Sample.java"),
            List.of("expected.bpmn"),
            List.of("actual.bpmn"));

        assertThrows(UnsupportedOperationException.class, () -> drift.expected().add("nope"));
        assertThrows(UnsupportedOperationException.class, () -> drift.actual().add("nope"));
    }
}
