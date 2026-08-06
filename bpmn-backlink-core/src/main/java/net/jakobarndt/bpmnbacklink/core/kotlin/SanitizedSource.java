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
 * A Kotlin source file paired with a sanitized copy of the same length, in
 * which every comment, string, character literal and backtick-quoted name has
 * been blanked out.
 *
 * <p>Because the sanitized text has the exact same length and keeps its line
 * breaks, every offset found on it addresses the same character in
 * {@link #original()}. That is what lets the structural scan work on text that
 * cannot lie about braces or keywords, while all edits still happen on the
 * untouched original.
 *
 * @param original the source exactly as read from disk
 * @param text the blanked copy, of identical length
 * @param literals the string literals of the original, in source order
 */
public record SanitizedSource(String original, String text, List<StringLiteral> literals) {

    /** Length of the {@code """} delimiter of a raw string. */
    private static final int RAW_DELIMITER_LENGTH = 3;

    /** Length of the {@code "} delimiter of a simple string. */
    private static final int SIMPLE_DELIMITER_LENGTH = 1;

    /** Length of a {@code \\uXXXX} escape sequence. */
    private static final int UNICODE_ESCAPE_LENGTH = 6;

    /**
     * A string literal token of the original source.
     *
     * @param start the offset of the opening quote
     * @param end the offset after the closing quote
     * @param raw whether the literal is a triple-quoted raw string
     */
    public record StringLiteral(int start, int end, boolean raw) {
    }

    /**
     * Decodes every string literal that lies completely inside the given range.
     *
     * @param from the inclusive start offset
     * @param to the exclusive end offset
     * @return the decoded literal values, in source order
     */
    public List<String> literalValuesIn(int from, int to) {
        List<String> values = new ArrayList<>();
        for (StringLiteral literal : literals) {
            if (literal.start() >= from && literal.end() <= to) {
                values.add(valueOf(literal));
            }
        }
        return values;
    }

    private String valueOf(StringLiteral literal) {
        int delimiter = RAW_DELIMITER_LENGTH;
        if (!literal.raw()) {
            delimiter = SIMPLE_DELIMITER_LENGTH;
        }
        int contentStart = literal.start() + delimiter;
        int contentEnd = literal.end() - delimiter;
        if (contentEnd <= contentStart) {
            return "";
        }
        String content = original.substring(contentStart, contentEnd);
        if (literal.raw()) {
            return content;
        }
        return unescape(content);
    }

    private static String unescape(String content) {
        StringBuilder result = new StringBuilder(content.length());
        int index = 0;
        while (index < content.length()) {
            index = appendCharacterAt(content, index, result);
        }
        return result.toString();
    }

    private static int appendCharacterAt(String content, int index, StringBuilder result) {
        char current = content.charAt(index);
        if (current != '\\' || index + 1 >= content.length()) {
            result.append(current);
            return index + 1;
        }
        if (isUnicodeEscapeAt(content, index)) {
            String digits = content.substring(index + 2, index + UNICODE_ESCAPE_LENGTH);
            result.append((char) Integer.parseInt(digits, 16));
            return index + UNICODE_ESCAPE_LENGTH;
        }
        result.append(unescape(content.charAt(index + 1)));
        return index + 2;
    }

    /**
     * A {@code \\u} sequence only decodes when it is complete and all four of
     * its digits are hexadecimal; anything else stays plain text rather than
     * failing the whole value.
     */
    private static boolean isUnicodeEscapeAt(String content, int index) {
        if (content.charAt(index + 1) != 'u' || index + UNICODE_ESCAPE_LENGTH > content.length()) {
            return false;
        }
        for (int digit = index + 2; digit < index + UNICODE_ESCAPE_LENGTH; digit++) {
            if (Character.digit(content.charAt(digit), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static char unescape(char escaped) {
        switch (escaped) {
            case 't':
                return '\t';
            case 'b':
                return '\b';
            case 'n':
                return '\n';
            case 'r':
                return '\r';
            default:
                return escaped;
        }
    }
}
