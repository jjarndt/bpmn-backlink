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
package net.jakobarndt.bpmnbacklink.core.scan;

import java.util.Set;

/**
 * The supertype simple names that mark a type as a Camunda delegate, shared by
 * every language scanner.
 *
 * <p>Matching happens on the simple name, so both an imported and a fully
 * qualified reference are recognised.
 */
final class DelegateSupertypes {

    /** Interface simple names that mark a delegate when implemented. */
    static final Set<String> INTERFACES = Set.of("JavaDelegate");

    /** Superclass simple names that mark a delegate when extended. */
    static final Set<String> SUPERCLASSES = Set.of("AbstractJavaDelegate");

    private DelegateSupertypes() {
    }

    /**
     * @param simpleName a supertype simple name
     * @return whether the name marks the declaring type as a delegate
     */
    static boolean marksDelegate(String simpleName) {
        return INTERFACES.contains(simpleName) || SUPERCLASSES.contains(simpleName);
    }
}
