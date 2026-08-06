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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the token shapes that decide whether the structural scan sees code or
 * prose: nesting block comments, raw strings, string templates, character
 * literals and backtick-quoted names, each also in its unterminated form.
 *
 * <p>Every expectation is spelled out as "this source, with exactly these parts
 * blanked", so a test states which characters must stop being code rather than
 * a hand-counted run of spaces.
 */
class KotlinSanitizerTest {

    private void assertBlanks(String source, String... hidden) {
        SanitizedSource sanitized = KotlinSanitizer.sanitize(source);
        assertEquals(source, sanitized.original(), "the original must be handed through unchanged");
        String expected = source;
        for (String part : hidden) {
            expected = expected.replace(part, blank(part));
        }
        assertEquals(expected, sanitized.text());
        assertEquals(source.length(), sanitized.text().length(), "offsets must stay stable");
    }

    private static String blank(String part) {
        StringBuilder blanked = new StringBuilder(part.length());
        for (int index = 0; index < part.length(); index++) {
            char current = part.charAt(index);
            blanked.append(current == '\n' || current == '\r' ? current : ' ');
        }
        return blanked.toString();
    }

    private List<String> literals(String source) {
        return KotlinSanitizer.sanitize(source).literalValuesIn(0, source.length());
    }

    // ---------------------------------------------------------------------
    // Comments.
    // ---------------------------------------------------------------------

    @Test
    void blanksLineCommentButKeepsTheLineBreak() {
        assertBlanks("val x = 1 // note\nval y = 2", "// note");
    }

    @Test
    void blanksLineCommentThatRunsToTheEndOfTheFile() {
        assertBlanks("val x = 1 // note", "// note");
    }

    @Test
    void nestedBlockCommentEndsAtTheOuterClosingMarker() {
        assertBlanks("/* a /* b */ c */ val x = 1", "/* a /* b */ c */");
    }

    @Test
    void unterminatedBlockCommentBlanksEverythingAfterIt() {
        assertBlanks("val x = 1 /* a\nb c d", "/* a\nb c d");
    }

    @Test
    void loneSlashAtTheEndOfTheFileIsOrdinaryCode() {
        assertBlanks("val x = 1 /");
    }

    // ---------------------------------------------------------------------
    // Simple strings.
    // ---------------------------------------------------------------------

    @Test
    void blanksStringIncludingAnEscapedQuote() {
        assertBlanks("val s = \"a\\\"b\" + c", "\"a\\\"b\"");
    }

    @Test
    void unterminatedStringStopsAtTheLineBreak() {
        assertBlanks("val s = \"abc\nval y = 2", "\"abc");
    }

    @Test
    void unterminatedStringAtTheEndOfTheFileStopsThere() {
        assertBlanks("val s = \"abc", "\"abc");
    }

    @Test
    void dollarWithoutBraceIsPlainStringContent() {
        assertBlanks("val s = \"a$\" + c", "\"a$\"");
    }

    // ---------------------------------------------------------------------
    // String templates.
    // ---------------------------------------------------------------------

    @Test
    void templateWithNestedBracesAndStringsEndsAtTheOuterQuote() {
        assertBlanks("val s = \"${ mapOf(\"k\" to \"}\") }\" + tail", "\"${ mapOf(\"k\" to \"}\") }\"");
    }

    @Test
    void templateWithACharacterLiteralHoldingABraceEndsCorrectly() {
        assertBlanks("val s = \"${ if (c == '}') 1 else 2 }\" + tail", "\"${ if (c == '}') 1 else 2 }\"");
    }

    @Test
    void templateWithANestedBlockEndsAtItsOwnClosingBrace() {
        assertBlanks("val s = \"${ run { 1 } }\" + tail", "\"${ run { 1 } }\"");
    }

    @Test
    void unterminatedTemplateBlanksTheRestOfTheFile() {
        assertBlanks("val s = \"${ 1 ", "\"${ 1 ");
    }

    // ---------------------------------------------------------------------
    // Raw strings.
    // ---------------------------------------------------------------------

    @Test
    void rawStringHidesADeclarationInItsContent() {
        assertBlanks("val doc = \"\"\"\nclass Fake : JavaDelegate\n\"\"\"\nclass Real : JavaDelegate",
            "\"\"\"\nclass Fake : JavaDelegate\n\"\"\"");
    }

    @Test
    void rawStringEndsAtTheLastQuoteOfALongerRun() {
        assertBlanks("val s = \"\"\"a\"\"\"\" + tail", "\"\"\"a\"\"\"\"");
    }

    @Test
    void rawStringWithATemplateEndsAtItsOwnDelimiter() {
        assertBlanks("val s = \"\"\"${ \"x\" }\"\"\" + tail", "\"\"\"${ \"x\" }\"\"\"");
    }

    @Test
    void unterminatedRawStringBlanksTheRestOfTheFile() {
        assertBlanks("val s = \"\"\"abc\nd e f", "\"\"\"abc\nd e f");
    }

    // ---------------------------------------------------------------------
    // Character literals.
    // ---------------------------------------------------------------------

    @Test
    void blanksCharacterLiteralIncludingAnEscapedQuote() {
        assertBlanks("val c = '\\'' + x", "'\\''");
    }

    @Test
    void unterminatedCharacterLiteralStopsAtTheLineBreak() {
        assertBlanks("val c = 'a\nval y = 2", "'a");
    }

    @Test
    void unterminatedCharacterLiteralAtTheEndOfTheFileStopsThere() {
        assertBlanks("val c = 'a", "'a");
    }

    // ---------------------------------------------------------------------
    // Backtick-quoted names.
    // ---------------------------------------------------------------------

    @Test
    void backtickNameKeepsItsDelimitersAndLosesItsContent() {
        assertBlanks("class `a class : Y // z` : X", "a class : Y // z");
    }

    @Test
    void unterminatedBacktickNameIsBlankedToTheLineEnd() {
        assertBlanks("class `a class : Y\nval y = `z`", "`a class : Y", "z");
    }

    // ---------------------------------------------------------------------
    // Line endings.
    // ---------------------------------------------------------------------

    @Test
    void carriageReturnsSurviveBlanking() {
        assertBlanks("val x = 1 // note\r\nval y = 2", "// note");
    }

    // ---------------------------------------------------------------------
    // Literal decoding.
    // ---------------------------------------------------------------------

    @Test
    void decodesEveryEscapeSequenceOfASimpleLiteral() {
        assertEquals(List.of("a\tb\bc\nd\re\\f\"g$hA"),
            literals("\"a\\tb\\bc\\nd\\re\\\\f\\\"g\\$h\\u0041\""));
    }

    @Test
    void keepsAnIncompleteUnicodeEscapeAsPlainText() {
        assertEquals(List.of("u12"), literals("\"\\u12\""));
    }

    @Test
    void keepsAUnicodeEscapeWithNonHexDigitsAsPlainText() {
        assertEquals(List.of("uZZZZ"), literals("\"\\uZZZZ\""));
    }

    @Test
    void keepsATrailingLoneBackslashAsPlainText() {
        assertEquals(List.of("a\\"), literals("\"a\\\\\""));
    }

    @Test
    void decodesRawLiteralWithoutTouchingBackslashes() {
        assertEquals(List.of("a\\tb"), literals("\"\"\"a\\tb\"\"\""));
    }

    @Test
    void emptyLiteralsDecodeToEmptyStrings() {
        assertEquals(List.of("", ""), literals("f(\"\", \"\"\"\"\"\")"));
    }

    @Test
    void aTruncatedLiteralEndingInABackslashIsDecodedWithoutFailing() {
        // Hand-built input: the sanitizer never records such a literal, so this
        // pins the decoder's own guard against running past the content.
        String original = "\"a\\\"";
        SanitizedSource source = new SanitizedSource(original, original,
            List.of(new SanitizedSource.StringLiteral(0, original.length(), false)));

        assertEquals(List.of("a\\"), source.literalValuesIn(0, original.length()));
    }

    @Test
    void literalsOutsideTheRequestedRangeAreIgnored() {
        SanitizedSource source = KotlinSanitizer.sanitize("f(\"a\") + g(\"b\")");
        assertEquals(List.of("a"), source.literalValuesIn(0, 6));
        assertEquals(2, source.literals().size(), "both literals must have been recorded");
    }

    @Test
    void recordsWhetherALiteralIsRaw() {
        List<SanitizedSource.StringLiteral> literals =
            KotlinSanitizer.sanitize("f(\"a\", \"\"\"b\"\"\")").literals();
        assertFalse(literals.get(0).raw(), "the first literal is a simple string");
        assertTrue(literals.get(1).raw(), "the second literal is a raw string");
    }
}
