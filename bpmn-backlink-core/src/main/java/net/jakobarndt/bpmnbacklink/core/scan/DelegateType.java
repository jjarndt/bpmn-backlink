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

import net.jakobarndt.bpmnbacklink.core.util.Names;

import java.nio.file.Path;

/**
 * A concrete Java delegate type discovered on the source level.
 *
 * @param sourceFile the {@code .java} file that declares the delegate
 * @param simpleName the simple type name of the delegate class
 */
public record DelegateType(Path sourceFile, String simpleName) {

    /**
     * @return the delegate reference used for the BPMN index lookup: the
     *     camelCased simple name (matching {@code camunda:class} indexing and a
     *     {@code delegateExpression} bean named after the class)
     */
    public String delegateReference() {
        return Names.decapitalize(simpleName);
    }
}
