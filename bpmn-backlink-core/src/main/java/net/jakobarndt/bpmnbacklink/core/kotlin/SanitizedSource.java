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
        int delimiter = literal.raw() ? 3 : 1;
        int contentStart = literal.start() + delimiter;
        int contentEnd = literal.end() - delimiter;
        if (contentEnd <= contentStart) {
            return "";
        }
        String content = original.substring(contentStart, contentEnd);
        return literal.raw() ? content : unescape(content);
    }

    private static String unescape(String content) {
        StringBuilder result = new StringBuilder(content.length());
        int index = 0;
        while (index < content.length()) {
            char current = content.charAt(index);
            if (current != '\\' || index + 1 >= content.length()) {
                result.append(current);
                index++;
            } else if (content.charAt(index + 1) == 'u' && index + 5 < content.length()) {
                result.append((char) Integer.parseInt(content.substring(index + 2, index + 6), 16));
                index += 6;
            } else {
                result.append(unescape(content.charAt(index + 1)));
                index += 2;
            }
        }
        return result.toString();
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
