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
package net.jakobarndt.bpmnbacklink.annotation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;

class CalledFromTest {

    @CalledFrom({"a.bpmn", "b.bpmn"})
    private static final class AnnotatedFixture {
    }

    @Test
    void isRetainedAtRuntime() {
        Retention retention = CalledFrom.class.getAnnotation(Retention.class);
        assertNotNull(retention, "@CalledFrom must declare a @Retention policy");
        assertEquals(RetentionPolicy.RUNTIME, retention.value());
    }

    @Test
    void targetsTypesOnly() {
        Target target = CalledFrom.class.getAnnotation(Target.class);
        assertNotNull(target, "@CalledFrom must declare a @Target");
        assertArrayEquals(new ElementType[] {ElementType.TYPE}, target.value());
    }

    @Test
    void valueIsReadableViaReflection() {
        assertTrue(
                AnnotatedFixture.class.isAnnotationPresent(CalledFrom.class),
                "@CalledFrom must be visible at runtime on the annotated type");

        CalledFrom calledFrom = AnnotatedFixture.class.getAnnotation(CalledFrom.class);
        assertArrayEquals(new String[] {"a.bpmn", "b.bpmn"}, calledFrom.value());
    }
}
