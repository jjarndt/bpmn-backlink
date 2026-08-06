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

import net.jakobarndt.bpmnbacklink.core.util.Names;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds the {@code class} and {@code object} declarations of a Kotlin source by
 * walking the {@linkplain KotlinSanitizer sanitized} text structurally, without
 * a Kotlin compiler.
 *
 * <p>The walk deliberately mirrors the shape of a declaration rather than
 * matching a pattern: after the declaration name it skips a type parameter list
 * ({@code <...>}), so a constraint such as {@code class Foo<T : JavaDelegate>}
 * cannot be mistaken for a supertype, then skips the primary constructor with
 * balanced parentheses, and only then reads the supertype list after the colon
 * up to the class body or a {@code where} clause.
 *
 * <p>The header preceding the keyword is read backwards over modifiers and
 * annotations, which yields both the modifiers that decide whether the
 * declaration is concrete and the exact offsets of the annotations that the
 * writer edits.
 */
public final class KotlinDeclarations {

    private static final Set<String> DECLARATION_KEYWORDS = Set.of("class", "object");

    /** Modifiers that a declaration and its primary constructor have in common. */
    private static final Set<String> VISIBILITY_MODIFIERS =
        Set.of("public", "private", "protected", "internal");

    /** Modifiers that may precede a class or object declaration. */
    private static final Set<String> MODIFIERS = union(VISIBILITY_MODIFIERS,
        "open", "final", "abstract", "sealed",
        "data", "inner", "enum", "annotation", "value", "inline",
        "external", "expect", "actual", "companion");

    /** Tokens that may sit between the declaration name and the primary constructor. */
    private static final Set<String> CONSTRUCTOR_PREFIXES = union(VISIBILITY_MODIFIERS, "constructor");

    private static final String WHERE_KEYWORD = "where";

    /** Delimiter of a name that is not a plain identifier, such as {@code `my name`}. */
    private static final char BACKTICK = '`';

    /** Offset answer of the helpers that may find nothing. */
    private static final int NOT_FOUND = -1;

    private KotlinDeclarations() {
    }

    private static Set<String> union(Set<String> base, String... more) {
        Set<String> all = new HashSet<>(base);
        all.addAll(Set.of(more));
        return Set.copyOf(all);
    }

    /**
     * @param source the sanitized Kotlin source
     * @return every named class and object declaration, in source order
     */
    public static List<KotlinDeclaration> parse(SanitizedSource source) {
        List<KotlinDeclaration> declarations = new ArrayList<>();
        String text = source.text();
        int index = 0;
        while (index < text.length()) {
            if (!isNameStart(text.charAt(index))) {
                index++;
                continue;
            }
            int end = endOfWord(text, index);
            if (DECLARATION_KEYWORDS.contains(text.substring(index, end)) && isKeywordPosition(text, index)) {
                addDeclaration(source, index, end, declarations);
            }
            index = end;
        }
        return declarations;
    }

    private static void addDeclaration(SanitizedSource source, int keywordStart, int keywordEnd,
        List<KotlinDeclaration> declarations) {
        String text = source.text();
        int nameStart = skipWhitespace(text, keywordEnd);
        int nameEnd = endOfDeclarationName(text, nameStart);
        if (nameEnd < 0) {
            // An anonymous object expression or a stray keyword: nothing to address.
            return;
        }
        Header header = readHeader(text, keywordStart);
        int afterConstructor = skipTypeParametersAndConstructor(text, nameEnd);
        declarations.add(new KotlinDeclaration(
            declarationName(source.original(), nameStart, nameEnd),
            readSupertypeNames(text, afterConstructor),
            header.modifiers(),
            header.annotations(),
            header.start()));
    }

    private static boolean isKeywordPosition(String text, int index) {
        // Rejects the class reference operator, as in "Foo::class".
        return index == 0 || text.charAt(index - 1) != ':';
    }

    private static int endOfDeclarationName(String text, int start) {
        if (start >= text.length()) {
            return NOT_FOUND;
        }
        if (text.charAt(start) == BACKTICK) {
            int close = text.indexOf(BACKTICK, start + 1);
            if (close < 0) {
                return NOT_FOUND;
            }
            return close + 1;
        }
        if (!isNameStart(text.charAt(start))) {
            return NOT_FOUND;
        }
        return endOfWord(text, start);
    }

    private static String declarationName(String original, int start, int end) {
        if (original.charAt(start) == BACKTICK) {
            return original.substring(start + 1, end - 1);
        }
        return original.substring(start, end);
    }

    // -----------------------------------------------------------------
    // Forward walk: type parameters, primary constructor, supertypes.
    // -----------------------------------------------------------------

    private static int skipTypeParametersAndConstructor(String text, int start) {
        int index = start;
        while (true) {
            index = skipWhitespace(text, index);
            if (index >= text.length()) {
                return index;
            }
            char current = text.charAt(index);
            if (current == '(') {
                return skipParentheses(text, index);
            }
            int next = afterConstructorToken(text, index, current);
            if (next == index) {
                return index;
            }
            index = next;
        }
    }

    private static int afterConstructorToken(String text, int index, char current) {
        if (current == '<') {
            return skipAngles(text, index);
        }
        if (current == '@') {
            return skipAnnotation(text, index);
        }
        if (isNameStart(current) && CONSTRUCTOR_PREFIXES.contains(word(text, index))) {
            return endOfWord(text, index);
        }
        return index;
    }

    private static List<String> readSupertypeNames(String text, int start) {
        int colon = skipWhitespace(text, start);
        if (colon >= text.length() || text.charAt(colon) != ':') {
            return new ArrayList<>();
        }
        int listStart = colon + 1;
        return headNames(text, listStart, endOfSupertypeList(text, listStart));
    }

    /**
     * Finds where the supertype list stops: at the class body, at a
     * {@code where} clause, or at the line break that ends the declaration
     * without a body.
     */
    private static int endOfSupertypeList(String text, int listStart) {
        int index = listStart;
        int depth = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            int nested = afterBracket(text, index, current);
            if (nested != index) {
                depth += bracketDelta(current);
                index = nested;
                continue;
            }
            if (depth > 0) {
                index++;
                continue;
            }
            if (current == '{' || endsDeclarationLine(text, listStart, index, current)) {
                return index;
            }
            if (!isNameStart(current)) {
                index++;
                continue;
            }
            int end = endOfQualifiedName(text, index);
            if (text.substring(index, end).equals(WHERE_KEYWORD)) {
                return index;
            }
            index = end;
        }
        return index;
    }

    /**
     * Reads the leading qualified name of every comma-separated entry of the
     * supertype list, so a generic argument or a delegation target is skipped.
     */
    private static List<String> headNames(String text, int listStart, int listEnd) {
        List<String> names = new ArrayList<>();
        int index = listStart;
        int depth = 0;
        boolean expectName = true;
        while (index < listEnd) {
            char current = text.charAt(index);
            int nested = afterBracket(text, index, current);
            if (nested != index) {
                depth += bracketDelta(current);
                index = nested;
                continue;
            }
            if (depth > 0) {
                index++;
                continue;
            }
            if (current == ',') {
                expectName = true;
                index++;
                continue;
            }
            if (!isNameStart(current)) {
                index++;
                continue;
            }
            int end = endOfQualifiedName(text, index);
            if (expectName) {
                names.add(Names.simpleName(text.substring(index, end)));
                expectName = false;
            }
            index = end;
        }
        return names;
    }

    /**
     * @return the offset after an arrow or a bracket at {@code index}, or
     *     {@code index} itself if neither is there
     */
    private static int afterBracket(String text, int index, char current) {
        if (isArrow(text, index)) {
            return index + 2;
        }
        if (isOpeningBracket(current) || isClosingBracket(current)) {
            return index + 1;
        }
        return index;
    }

    private static int bracketDelta(char current) {
        if (isOpeningBracket(current)) {
            return 1;
        }
        if (isClosingBracket(current)) {
            return -1;
        }
        return 0;
    }

    private static boolean endsDeclarationLine(String text, int listStart, int index, char current) {
        return current == '\n' && !continuesAfterLineBreak(text, listStart, index);
    }

    private static boolean continuesAfterLineBreak(String text, int listStart, int lineBreak) {
        int before = lastNonWhitespace(text, listStart, lineBreak);
        if (before < 0 || text.charAt(before) == ',') {
            return true;
        }
        int after = skipWhitespace(text, lineBreak + 1);
        return after < text.length() && text.charAt(after) == ',';
    }

    // -----------------------------------------------------------------
    // Backward walk: annotations and modifiers preceding the keyword.
    // -----------------------------------------------------------------

    private record Header(int start, List<String> modifiers, List<KotlinDeclaration.AnnotationRef> annotations) {
    }

    /**
     * A named token found by the backward walk, with the offsets of its
     * optional argument list.
     *
     * @param nameStart the offset of the first character of the (qualified) name
     * @param nameEnd the offset after the name
     * @param argumentsStart the offset of the opening parenthesis, or
     *     {@link #NOT_FOUND} if the token carries no argument list
     * @param tokenEnd the offset after the whole token, arguments included
     */
    private record NameToken(int nameStart, int nameEnd, int argumentsStart, int tokenEnd) {

        private static final NameToken NONE = new NameToken(NOT_FOUND, NOT_FOUND, NOT_FOUND, NOT_FOUND);

        private boolean isAbsent() {
            return nameStart == NOT_FOUND;
        }

        private boolean hasArguments() {
            return argumentsStart != NOT_FOUND;
        }
    }

    private static Header readHeader(String text, int keywordStart) {
        List<String> modifiers = new ArrayList<>();
        List<KotlinDeclaration.AnnotationRef> annotations = new ArrayList<>();
        int start = keywordStart;
        while (true) {
            NameToken token = tokenBefore(text, start);
            if (token.isAbsent()) {
                return new Header(start, modifiers, annotations);
            }
            String name = text.substring(token.nameStart(), token.nameEnd());
            if (token.nameStart() > 0 && text.charAt(token.nameStart() - 1) == '@') {
                annotations.add(annotationRef(name, token));
                start = token.nameStart() - 1;
                continue;
            }
            if (!token.hasArguments() && MODIFIERS.contains(name)) {
                modifiers.add(name);
                start = token.nameStart();
                continue;
            }
            return new Header(start, modifiers, annotations);
        }
    }

    private static KotlinDeclaration.AnnotationRef annotationRef(String name, NameToken token) {
        int end = token.tokenEnd();
        int argumentsStart = end;
        if (token.hasArguments()) {
            argumentsStart = token.argumentsStart();
        }
        return new KotlinDeclaration.AnnotationRef(
            Names.simpleName(name), token.nameStart() - 1, end, argumentsStart, end);
    }

    private static NameToken tokenBefore(String text, int position) {
        int last = lastNonWhitespace(text, 0, position);
        if (last < 0) {
            return NameToken.NONE;
        }
        if (text.charAt(last) == ')') {
            return parenthesizedTokenBefore(text, last);
        }
        if (!isNamePart(text.charAt(last))) {
            return NameToken.NONE;
        }
        return nameTokenEndingAt(text, last + 1, NOT_FOUND, last + 1);
    }

    private static NameToken parenthesizedTokenBefore(String text, int close) {
        int open = matchingParenthesis(text, close);
        if (open < 0) {
            return NameToken.NONE;
        }
        return nameTokenEndingAt(text, lastNonWhitespace(text, 0, open) + 1, open, close + 1);
    }

    private static NameToken nameTokenEndingAt(String text, int nameEnd, int argumentsStart, int tokenEnd) {
        int nameStart = startOfQualifiedName(text, nameEnd);
        if (nameStart == nameEnd) {
            return NameToken.NONE;
        }
        return new NameToken(nameStart, nameEnd, argumentsStart, tokenEnd);
    }

    // -----------------------------------------------------------------
    // Small text helpers, all operating on the sanitized text.
    // -----------------------------------------------------------------

    private static int skipAngles(String text, int start) {
        int index = start;
        int depth = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (isArrow(text, index)) {
                index += 2;
                continue;
            }
            depth += angleDelta(current);
            index++;
            if (current == '>' && depth == 0) {
                return index;
            }
        }
        return index;
    }

    private static int angleDelta(char current) {
        if (current == '<') {
            return 1;
        }
        if (current == '>') {
            return -1;
        }
        return 0;
    }

    private static int skipParentheses(String text, int start) {
        int index = start;
        int depth = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
                if (depth == 0) {
                    return index + 1;
                }
            }
            index++;
        }
        return index;
    }

    private static int skipAnnotation(String text, int start) {
        int index = endOfQualifiedName(text, start + 1);
        if (index < text.length() && text.charAt(index) == '(') {
            return skipParentheses(text, index);
        }
        return index;
    }

    private static int matchingParenthesis(String text, int close) {
        int index = close;
        int depth = 0;
        while (index >= 0) {
            char current = text.charAt(index);
            if (current == ')') {
                depth++;
            } else if (current == '(') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
            index--;
        }
        return NOT_FOUND;
    }

    private static int skipWhitespace(String text, int start) {
        int index = start;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int lastNonWhitespace(String text, int from, int to) {
        int index = to - 1;
        while (index >= from && Character.isWhitespace(text.charAt(index))) {
            index--;
        }
        if (index < from) {
            return NOT_FOUND;
        }
        return index;
    }

    private static String word(String text, int start) {
        return text.substring(start, endOfWord(text, start));
    }

    private static int endOfWord(String text, int start) {
        int index = start;
        while (index < text.length() && isNamePart(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int endOfQualifiedName(String text, int start) {
        int index = start;
        while (index < text.length() && (isNamePart(text.charAt(index)) || text.charAt(index) == '.')) {
            index++;
        }
        return index;
    }

    private static int startOfQualifiedName(String text, int end) {
        int index = end;
        while (index > 0 && (isNamePart(text.charAt(index - 1)) || text.charAt(index - 1) == '.')) {
            index--;
        }
        return index;
    }

    private static boolean isArrow(String text, int index) {
        return text.charAt(index) == '-' && charAt(text, index + 1) == '>';
    }

    private static boolean isOpeningBracket(char current) {
        return current == '(' || current == '[' || current == '<';
    }

    private static boolean isClosingBracket(char current) {
        return current == ')' || current == ']' || current == '>';
    }

    private static boolean isNameStart(char current) {
        return Character.isLetter(current) || current == '_';
    }

    private static boolean isNamePart(char current) {
        return Character.isLetterOrDigit(current) || current == '_';
    }

    private static char charAt(String text, int index) {
        if (index < text.length()) {
            return text.charAt(index);
        }
        return '\0';
    }
}
