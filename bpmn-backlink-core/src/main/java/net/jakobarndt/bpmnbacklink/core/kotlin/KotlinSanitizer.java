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
            char current = source.charAt(index);
            if (current == '/' && charAt(source, index + 1) == '/') {
                int end = endOfLine(source, index);
                blank(blanked, index, end);
                index = end;
            } else if (current == '/' && charAt(source, index + 1) == '*') {
                int end = endOfBlockComment(source, index);
                blank(blanked, index, end);
                index = end;
            } else if (current == '"') {
                int end = endOfString(source, index);
                literals.add(new SanitizedSource.StringLiteral(index, end, isRawString(source, index)));
                blank(blanked, index, end);
                index = end;
            } else if (current == '\'') {
                int end = endOfCharLiteral(source, index);
                blank(blanked, index, end);
                index = end;
            } else if (current == '`') {
                index = blankBacktickName(source, blanked, index);
            } else {
                index++;
            }
        }
        return new SanitizedSource(source, new String(blanked), List.copyOf(literals));
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
        return newline < 0 ? source.length() : newline;
    }

    private static int endOfBlockComment(String source, int start) {
        int index = start + 2;
        int depth = 1;
        while (index < source.length()) {
            if (source.startsWith("/*", index)) {
                depth++;
                index += 2;
            } else if (source.startsWith("*/", index)) {
                depth--;
                index += 2;
                if (depth == 0) {
                    return index;
                }
            } else {
                index++;
            }
        }
        return source.length();
    }

    private static boolean isRawString(String source, int start) {
        return source.startsWith("\"\"\"", start);
    }

    private static int endOfString(String source, int start) {
        return isRawString(source, start) ? endOfRawString(source, start) : endOfSimpleString(source, start);
    }

    private static int endOfRawString(String source, int start) {
        int index = start + 3;
        while (index < source.length()) {
            if (source.startsWith("\"\"\"", index)) {
                int end = index;
                while (end < source.length() && source.charAt(end) == '"') {
                    end++;
                }
                return end;
            }
            index = isTemplateStart(source, index) ? endOfTemplate(source, index) : index + 1;
        }
        return source.length();
    }

    private static int endOfSimpleString(String source, int start) {
        int index = start + 1;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (current == '\\') {
                index += 2;
            } else if (current == '"') {
                return index + 1;
            } else if (current == '\n') {
                return index;
            } else if (isTemplateStart(source, index)) {
                index = endOfTemplate(source, index);
            } else {
                index++;
            }
        }
        return source.length();
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
            } else if (current == '}') {
                depth--;
                index++;
                if (depth == 0) {
                    return index;
                }
            } else if (current == '"') {
                index = endOfString(source, index);
            } else if (current == '\'') {
                index = endOfCharLiteral(source, index);
            } else {
                index++;
            }
        }
        return source.length();
    }

    private static int endOfCharLiteral(String source, int start) {
        int index = start + 1;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (current == '\\') {
                index += 2;
            } else if (current == '\'') {
                return index + 1;
            } else if (current == '\n') {
                return index;
            } else {
                index++;
            }
        }
        return source.length();
    }

    private static char charAt(String source, int index) {
        return index < source.length() ? source.charAt(index) : '\0';
    }

    private static void blank(char[] blanked, int from, int to) {
        for (int index = from; index < to; index++) {
            if (blanked[index] != '\n' && blanked[index] != '\r') {
                blanked[index] = ' ';
            }
        }
    }
}
