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

import java.time.Duration;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * The scan runs inside a build plugin over source trees it does not own, so it
 * has to survive half-written and outright broken Kotlin. These tests assert
 * the two properties that make that true for any input at all: the scan
 * terminates without an exception, and the sanitized text keeps the length and
 * the line breaks of the original, which is what makes every offset usable on
 * the untouched source.
 */
class KotlinScannerRobustnessTest {

    private static final String ALPHABET = "{}()<>[]:@`'\"/*$-,;\n abcABC";

    /** Sources cut off in the middle of every construct the scan knows. */
    private static final List<String> TRUNCATED = List.of(
        "", " ", "\n", "`", "\"", "'", "/", "$", "{", "<", "@", ":", "-", ",",
        "class", "class ", "class X<", "class X(", "class X : ", "class X : Y<",
        "@", "@Foo(", "/*", "/*/", "\"\"\"", "${", "\"${", "\"\"\"${", "`x",
        "object", "object ", "object : ", "class `", "class X<T : (Int) -> ",
        "class X @A(", "val a = f(", ")", ">", "}", "class X : Y by ",
        "class X where ", "\\", "\"\\", "\"\"\"\"", "\"\"\"\"\"", "'\\",
        "class X<<<<<<<<<<", "${${${${${", "((((((((((", "<<<<<<<<<<",
        "\"${\"${\"${\"${\"${", "/*/*/*/*/*", "class X : A, , B {");

    @Test
    void truncatedSourceIsScannedWithoutFailingOrHanging() {
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> TRUNCATED.forEach(this::scan));
    }

    @Test
    void randomSourceIsScannedWithoutFailingOrHanging() {
        Random random = new Random(20260730L);
        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            for (int run = 0; run < 4000; run++) {
                scan(randomSource(random));
            }
        });
    }

    private String randomSource(Random random) {
        StringBuilder source = new StringBuilder();
        int length = random.nextInt(60);
        for (int index = 0; index < length; index++) {
            source.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return source.toString();
    }

    private void scan(String source) {
        SanitizedSource sanitized = KotlinSanitizer.sanitize(source);
        assertEquals(source.length(), sanitized.text().length(),
            "the sanitized text must stay as long as the original: " + escape(source));
        for (int index = 0; index < source.length(); index++) {
            char original = source.charAt(index);
            if (original == '\n' || original == '\r') {
                assertEquals(original, sanitized.text().charAt(index),
                    "line break at " + index + " must survive: " + escape(source));
            }
        }
        sanitized.literalValuesIn(0, source.length());
        for (KotlinDeclaration declaration : KotlinDeclarations.parse(sanitized)) {
            declaration.isConcrete();
            declaration.supertypes();
            declaration.annotation("CalledFrom");
        }
    }

    private static String escape(String source) {
        return source.replace("\n", "\\n");
    }
}
