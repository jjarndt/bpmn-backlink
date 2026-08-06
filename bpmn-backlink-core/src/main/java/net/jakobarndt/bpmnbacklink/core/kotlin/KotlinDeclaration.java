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
package net.jakobarndt.bpmnbacklink.core.kotlin;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A {@code class} or {@code object} declaration found by the structural scan,
 * described by everything the scanner and the annotation writer need: its name,
 * its supertypes, its modifiers and the offsets of its header.
 *
 * @param name the declared name, backticks already stripped
 * @param supertypes the simple names of the declared supertypes, in source order
 * @param modifiers the modifier keywords preceding the declaration keyword
 * @param annotations the annotations preceding the declaration keyword
 * @param headerStart the offset of the first token of the declaration, that is
 *     the first annotation or modifier, or the declaration keyword itself
 */
public record KotlinDeclaration(
    String name,
    List<String> supertypes,
    List<String> modifiers,
    List<AnnotationRef> annotations,
    int headerStart) {

    /**
     * Modifiers that make a declaration something other than an instantiable
     * class, so it can never be a delegate implementation.
     */
    private static final Set<String> NON_CONCRETE_MODIFIERS =
        Set.of("abstract", "sealed", "annotation", "enum", "expect");

    /**
     * An annotation of the declaration, located in the original source.
     *
     * @param name the simple annotation name, package qualifier stripped
     * @param start the offset of the {@code @}
     * @param end the offset after the annotation, including its arguments
     * @param argumentsStart the offset of the opening parenthesis, or
     *     {@code end} if the annotation has no argument list
     * @param argumentsEnd the offset after the closing parenthesis, or
     *     {@code end} if the annotation has no argument list
     */
    public record AnnotationRef(String name, int start, int end, int argumentsStart, int argumentsEnd) {
    }

    /**
     * @return whether the declaration can be instantiated, and is therefore a
     *     candidate for a delegate implementation
     */
    public boolean isConcrete() {
        return modifiers.stream().noneMatch(NON_CONCRETE_MODIFIERS::contains);
    }

    /**
     * @param annotationName the simple annotation name to look for
     * @return the first matching annotation of this declaration, if any
     */
    public Optional<AnnotationRef> annotation(String annotationName) {
        return annotations.stream()
            .filter(annotation -> annotation.name().equals(annotationName))
            .findFirst();
    }
}
