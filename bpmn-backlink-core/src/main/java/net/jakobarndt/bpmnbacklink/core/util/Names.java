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
package net.jakobarndt.bpmnbacklink.core.util;

/**
 * Naming helpers shared by the BPMN indexer and the delegate scanner.
 *
 * <p>The conventions reproduce the behaviour of the regex-based predecessor
 * tool so that an existing index keeps resolving to the same delegate types.
 */
public final class Names {

    private Names() {
    }

    /**
     * Unwraps a Camunda expression of the form {@code ${beanName}} or
     * {@code #{beanName}} into the bare bean name.
     *
     * <p>Values that are not wrapped are returned trimmed and unchanged, so a
     * plain bean name also works.
     *
     * @param expression the raw attribute value, may be {@code null}
     * @return the unwrapped bean name, or {@code null} if the input is {@code null}
     */
    public static String unwrapExpression(String expression) {
        if (expression == null) {
            return null;
        }
        String trimmed = expression.trim();
        if ((trimmed.startsWith("${") || trimmed.startsWith("#{")) && trimmed.endsWith("}")) {
            return trimmed.substring(2, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    /**
     * Converts a fully qualified or simple class name into the delegate
     * reference used for indexing: the camelCased simple name.
     *
     * @param className a class name, optionally fully qualified, may be {@code null}
     * @return the camelCased simple name, or {@code null} if the input is {@code null}
     */
    public static String toDelegateReference(String className) {
        if (className == null) {
            return null;
        }
        String simpleName = simpleName(className.trim());
        return decapitalize(simpleName);
    }

    /**
     * @param className a class name, optionally fully qualified
     * @return the simple name (everything after the last dot)
     */
    public static String simpleName(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }

    /**
     * Lower-cases the first character of the given name, matching the
     * predecessor tool's camelCase mapping (only the leading character is
     * touched).
     *
     * @param name the name to decapitalize
     * @return the decapitalized name
     */
    public static String decapitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
