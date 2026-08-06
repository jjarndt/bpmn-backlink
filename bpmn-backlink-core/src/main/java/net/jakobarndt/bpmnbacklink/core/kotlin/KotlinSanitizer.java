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

import java.util.ArrayList;
import java.util.List;

/**
 * Blanks out everything in a Kotlin source that must not be read as code:
 * comments, string and character literals, and backtick-quoted names.
 *
 * <p>Every blanked character is replaced by a space, line breaks are kept, so
 * the result has the same length and the same line structure as the input and
 * all offsets remain valid for the original text.
 *
 * <p>The rules that matter and that a naive regex gets wrong: Kotlin block
 * comments nest, raw strings are delimited by three quotes and end at the last
 * quote of a longer run, and string templates ({@code ${...}}) may contain
 * arbitrary code including further strings, so the closing quote can only be
 * found by tracking the template braces.
 *
 * <p>A backtick-quoted name keeps its two backticks and only loses its content,
 * so the declaration parser can still recognise the token and read the actual
 * name from the original text.
 */
public final class KotlinSanitizer {

    /** The delimiter that opens and closes a raw string. */
    private static final String RAW_DELIMITER = "\"\"\"";

    /** Length of the {@code /*} and {@code *} + {@code /} block comment markers. */
    private static final int COMMENT_MARKER_LENGTH = 2;

    private KotlinSanitizer() {
    }

    /**
     * @param source the Kotlin source text
     * @return the original paired with its blanked copy and the string literals
     */
    public static SanitizedSource sanitize(String source) {
        char[] blanked = source.toCharArray();
        List<SanitizedSource.StringLiteral> literals = new ArrayList<>();
        int index = 0;
        while (index < source.length()) {
            index = blankTokenAt(source, blanked, literals, index);
        }
        return new SanitizedSource(source, new String(blanked), List.copyOf(literals));
    }

    private static int blankTokenAt(String source, char[] blanked,
        List<SanitizedSource.StringLiteral> literals, int index) {
        char current = source.charAt(index);
        if (current == '/' && charAt(source, index + 1) == '/') {
            return blankUpTo(blanked, index, endOfLine(source, index));
        }
        if (current == '/' && charAt(source, index + 1) == '*') {
            return blankUpTo(blanked, index, endOfBlockComment(source, index));
        }
        if (current == '"') {
            int end = endOfString(source, index);
            literals.add(new SanitizedSource.StringLiteral(index, end, isRawString(source, index)));
            return blankUpTo(blanked, index, end);
        }
        if (current == '\'') {
            return blankUpTo(blanked, index, endOfCharLiteral(source, index));
        }
        if (current == '`') {
            return blankBacktickName(source, blanked, index);
        }
        return index + 1;
    }

    private static int blankUpTo(char[] blanked, int from, int to) {
        blank(blanked, from, to);
        return to;
    }

    private static int blankBacktickName(String source, char[] blanked, int start) {
        int lineEnd = endOfLine(source, start);
        int close = start + 1;
        while (close < lineEnd && source.charAt(close) != '`') {
            close++;
        }
        if (close == lineEnd) {
            blank(blanked, start, lineEnd);
            return lineEnd;
        }
        blank(blanked, start + 1, close);
        return close + 1;
    }

    private static int endOfLine(String source, int start) {
        int newline = source.indexOf('\n', start);
        if (newline < 0) {
            return source.length();
        }
        return newline;
    }

    private static int endOfBlockComment(String source, int start) {
        int index = start + COMMENT_MARKER_LENGTH;
        int depth = 1;
        while (index < source.length()) {
            if (source.startsWith("/*", index)) {
                depth++;
                index += COMMENT_MARKER_LENGTH;
                continue;
            }
            if (!source.startsWith("*/", index)) {
                index++;
                continue;
            }
            depth--;
            index += COMMENT_MARKER_LENGTH;
            if (depth == 0) {
                return index;
            }
        }
        return source.length();
    }

    private static boolean isRawString(String source, int start) {
        return source.startsWith(RAW_DELIMITER, start);
    }

    private static int endOfString(String source, int start) {
        if (isRawString(source, start)) {
            return endOfRawString(source, start);
        }
        return endOfSimpleString(source, start);
    }

    private static int endOfRawString(String source, int start) {
        int index = start + RAW_DELIMITER.length();
        while (index < source.length()) {
            if (source.startsWith(RAW_DELIMITER, index)) {
                return endOfQuoteRun(source, index);
            }
            if (isTemplateStart(source, index)) {
                index = endOfTemplate(source, index);
                continue;
            }
            index++;
        }
        return source.length();
    }

    /**
     * A raw string ends at the last quote of a run of three or more, so any
     * extra quote belongs to its content.
     */
    private static int endOfQuoteRun(String source, int start) {
        int end = start;
        while (end < source.length() && source.charAt(end) == '"') {
            end++;
        }
        return end;
    }

    private static int endOfSimpleString(String source, int start) {
        int index = start + 1;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (current == '"') {
                return index + 1;
            }
            if (current == '\n') {
                return index;
            }
            index = afterSimpleStringCharacter(source, index, current);
        }
        return source.length();
    }

    private static int afterSimpleStringCharacter(String source, int index, char current) {
        if (current == '\\') {
            return index + 2;
        }
        if (isTemplateStart(source, index)) {
            return endOfTemplate(source, index);
        }
        return index + 1;
    }

    private static boolean isTemplateStart(String source, int index) {
        return source.charAt(index) == '$' && charAt(source, index + 1) == '{';
    }

    private static int endOfTemplate(String source, int start) {
        int index = start + 2;
        int depth = 1;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
                index++;
                continue;
            }
            if (current == '}') {
                depth--;
                index++;
                if (depth == 0) {
                    return index;
                }
                continue;
            }
            index = afterTemplateCharacter(source, index, current);
        }
        return source.length();
    }

    private static int afterTemplateCharacter(String source, int index, char current) {
        if (current == '"') {
            return endOfString(source, index);
        }
        if (current == '\'') {
            return endOfCharLiteral(source, index);
        }
        return index + 1;
    }

    private static int endOfCharLiteral(String source, int start) {
        int index = start + 1;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (current == '\'') {
                return index + 1;
            }
            if (current == '\n') {
                return index;
            }
            index = afterCharLiteralCharacter(index, current);
        }
        return source.length();
    }

    private static int afterCharLiteralCharacter(int index, char current) {
        if (current == '\\') {
            return index + 2;
        }
        return index + 1;
    }

    private static char charAt(String source, int index) {
        if (index < source.length()) {
            return source.charAt(index);
        }
        return '\0';
    }

    private static void blank(char[] blanked, int from, int to) {
        for (int index = from; index < to; index++) {
            if (blanked[index] != '\n' && blanked[index] != '\r') {
                blanked[index] = ' ';
            }
        }
    }
}
