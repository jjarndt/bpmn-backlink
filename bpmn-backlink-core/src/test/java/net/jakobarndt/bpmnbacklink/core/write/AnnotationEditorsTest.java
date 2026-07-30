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
package net.jakobarndt.bpmnbacklink.core.write;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationEditorsTest {

    @Test
    void javaSourcesGoToTheJavaParserBackedEditor() {
        assertInstanceOf(AnnotationWriter.class, AnnotationEditors.forFile(Path.of("src", "Delegate.java")));
    }

    @Test
    void kotlinSourcesGoToTheKotlinEditor() {
        assertInstanceOf(KotlinAnnotationWriter.class, AnnotationEditors.forFile(Path.of("src", "Delegate.kt")));
    }

    @Test
    void anUnknownLanguageIsRejectedInsteadOfSilentlyMishandled() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> AnnotationEditors.forFile(Path.of("src", "Delegate.groovy")));
        assertTrue(failure.getMessage().contains("Delegate.groovy"),
            "the message must name the offending file, was: " + failure.getMessage());
    }
}
