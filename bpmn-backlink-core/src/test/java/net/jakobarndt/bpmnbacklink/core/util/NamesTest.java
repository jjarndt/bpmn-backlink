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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NamesTest {

    @Test
    void unwrapsDollarExpression() {
        assertEquals("orderDelegate", Names.unwrapExpression("${orderDelegate}"));
    }

    @Test
    void unwrapsHashExpression() {
        assertEquals("orderDelegate", Names.unwrapExpression("#{orderDelegate}"));
    }

    @Test
    void unwrapsExpressionWithSurroundingWhitespace() {
        assertEquals("orderDelegate", Names.unwrapExpression("  ${ orderDelegate }  "));
    }

    @Test
    void returnsPlainBeanNameUnchanged() {
        assertEquals("orderDelegate", Names.unwrapExpression("orderDelegate"));
    }

    @Test
    void unwrapNullReturnsNull() {
        assertNull(Names.unwrapExpression(null));
    }

    @Test
    void mapsFullyQualifiedClassToCamelCaseSimpleName() {
        assertEquals("paymentDelegate", Names.toDelegateReference("net.example.delegate.PaymentDelegate"));
    }

    @Test
    void mapsSimpleClassToCamelCase() {
        assertEquals("paymentDelegate", Names.toDelegateReference("PaymentDelegate"));
    }

    @Test
    void decapitalizeOnlyTouchesFirstCharacter() {
        assertEquals("hTTPDelegate", Names.decapitalize("HTTPDelegate"));
    }

    @Test
    void unwrapTreatsOpenedButUnclosedExpressionAsPlainValue() {
        assertEquals("${orderDelegate", Names.unwrapExpression("${orderDelegate"));
    }

    @Test
    void toDelegateReferenceNullReturnsNull() {
        assertNull(Names.toDelegateReference(null));
    }

    @Test
    void decapitalizeNullReturnsNull() {
        assertNull(Names.decapitalize(null));
    }

    @Test
    void decapitalizeEmptyReturnsEmpty() {
        assertEquals("", Names.decapitalize(""));
    }

    @Test
    void unwrapEmptyDollarExpressionYieldsBlank() {
        assertEquals("", Names.unwrapExpression("${}"));
    }

    @Test
    void unwrapEmptyHashExpressionYieldsBlank() {
        assertEquals("", Names.unwrapExpression("#{}"));
    }

    @Test
    void unwrapTextBeforeWrapperIsTreatedAsPlainValue() {
        // The value does not start with a wrapper, so it is returned trimmed and
        // unchanged rather than having an inner ${...} extracted.
        assertEquals("text${bean}", Names.unwrapExpression("text${bean}"));
    }

    @Test
    void unwrapTwoAdjacentWrappersIsDegenerateButDefined() {
        // "${a}${b}" starts with "${" and ends with "}", so the outer braces are
        // stripped, leaving the literal inner "a}${b". Camunda never emits such a
        // value for delegateExpression; this pins the defined fallback behaviour.
        assertEquals("a}${b", Names.unwrapExpression("${a}${b}"));
    }

    @Test
    void toDelegateReferenceTrailingDotYieldsBlank() {
        // A trailing dot makes the simple name empty, which decapitalizes to "".
        assertEquals("", Names.toDelegateReference("net.example."));
    }

    @Test
    void toDelegateReferenceKeepsInnerClassDollarSegment() {
        // The simple name is taken after the last dot only; a nested-class '$'
        // separator is preserved verbatim and merely the first char is lowered.
        assertEquals("outer$Inner", Names.toDelegateReference("net.example.Outer$Inner"));
    }
}
