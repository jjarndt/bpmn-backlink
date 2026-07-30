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

    /** Modifiers that may precede a class or object declaration. */
    private static final Set<String> MODIFIERS = Set.of(
        "public", "private", "protected", "internal",
        "open", "final", "abstract", "sealed",
        "data", "inner", "enum", "annotation", "value", "inline",
        "external", "expect", "actual", "companion");

    /** Tokens that may sit between the declaration name and the primary constructor. */
    private static final Set<String> CONSTRUCTOR_PREFIXES = Set.of(
        "constructor", "public", "private", "protected", "internal");

    private static final String WHERE_KEYWORD = "where";

    private KotlinDeclarations() {
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
            return -1;
        }
        if (text.charAt(start) == '`') {
            int close = text.indexOf('`', start + 1);
            return close < 0 ? -1 : close + 1;
        }
        return isNameStart(text.charAt(start)) ? endOfWord(text, start) : -1;
    }

    private static String declarationName(String original, int start, int end) {
        return original.charAt(start) == '`'
            ? original.substring(start + 1, end - 1)
            : original.substring(start, end);
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
            if (current == '<') {
                index = skipAngles(text, index);
            } else if (current == '@') {
                index = skipAnnotation(text, index);
            } else if (current == '(') {
                return skipParentheses(text, index);
            } else if (isNameStart(current) && CONSTRUCTOR_PREFIXES.contains(word(text, index))) {
                index = endOfWord(text, index);
            } else {
                return index;
            }
        }
    }

    private static List<String> readSupertypeNames(String text, int start) {
        List<String> names = new ArrayList<>();
        int index = skipWhitespace(text, start);
        if (index >= text.length() || text.charAt(index) != ':') {
            return names;
        }
        int listStart = index + 1;
        index = listStart;
        int depth = 0;
        boolean expectName = true;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (isArrow(text, index)) {
                index += 2;
            } else if (isOpeningBracket(current)) {
                depth++;
                index++;
            } else if (isClosingBracket(current)) {
                depth--;
                index++;
            } else if (depth > 0) {
                index++;
            } else if (current == '{') {
                return names;
            } else if (current == ',') {
                expectName = true;
                index++;
            } else if (current == '\n' && !continuesAfterLineBreak(text, listStart, index)) {
                return names;
            } else if (!isNameStart(current)) {
                index++;
            } else {
                int end = endOfQualifiedName(text, index);
                String qualified = text.substring(index, end);
                if (qualified.equals(WHERE_KEYWORD)) {
                    return names;
                }
                if (expectName) {
                    names.add(Names.simpleName(qualified));
                    expectName = false;
                }
                index = end;
            }
        }
        return names;
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

    private static Header readHeader(String text, int keywordStart) {
        List<String> modifiers = new ArrayList<>();
        List<KotlinDeclaration.AnnotationRef> annotations = new ArrayList<>();
        int start = keywordStart;
        while (true) {
            int last = lastNonWhitespace(text, 0, start);
            if (last < 0) {
                return new Header(start, modifiers, annotations);
            }
            int argumentsStart = -1;
            int nameEnd;
            if (text.charAt(last) == ')') {
                argumentsStart = matchingParenthesis(text, last);
                if (argumentsStart < 0) {
                    return new Header(start, modifiers, annotations);
                }
                nameEnd = lastNonWhitespace(text, 0, argumentsStart) + 1;
            } else if (isNamePart(text.charAt(last))) {
                nameEnd = last + 1;
            } else {
                return new Header(start, modifiers, annotations);
            }
            int nameStart = startOfQualifiedName(text, nameEnd);
            if (nameStart == nameEnd) {
                return new Header(start, modifiers, annotations);
            }
            String name = text.substring(nameStart, nameEnd);
            if (nameStart > 0 && text.charAt(nameStart - 1) == '@') {
                int end = argumentsStart < 0 ? nameEnd : last + 1;
                annotations.add(new KotlinDeclaration.AnnotationRef(
                    Names.simpleName(name), nameStart - 1, end,
                    argumentsStart < 0 ? end : argumentsStart, end));
                start = nameStart - 1;
            } else if (argumentsStart < 0 && MODIFIERS.contains(name)) {
                modifiers.add(name);
                start = nameStart;
            } else {
                return new Header(start, modifiers, annotations);
            }
        }
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
            } else if (current == '<') {
                depth++;
                index++;
            } else if (current == '>') {
                depth--;
                index++;
                if (depth == 0) {
                    return index;
                }
            } else {
                index++;
            }
        }
        return index;
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
        return index < text.length() && text.charAt(index) == '(' ? skipParentheses(text, index) : index;
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
        return -1;
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
        return index < from ? -1 : index;
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
        return index < text.length() ? text.charAt(index) : '\0';
    }
}
