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
package net.jakobarndt.bpmnbacklink.core.scan;

import net.jakobarndt.bpmnbacklink.core.kotlin.KotlinDeclaration;
import net.jakobarndt.bpmnbacklink.core.kotlin.KotlinDeclarations;
import net.jakobarndt.bpmnbacklink.core.kotlin.KotlinSanitizer;
import net.jakobarndt.bpmnbacklink.core.kotlin.SanitizedSource;
import net.jakobarndt.bpmnbacklink.core.util.Names;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Finds concrete Kotlin delegate types by scanning the source structurally,
 * without a Kotlin compiler on the classpath.
 *
 * <p>A declaration qualifies when it is a {@code class} or {@code object} that
 * is not {@code abstract}, {@code sealed}, {@code annotation}, {@code enum} or
 * {@code expect}, and that lists {@code JavaDelegate} or
 * {@code AbstractJavaDelegate} among its supertypes. Kotlin does not
 * distinguish syntactically between an implemented interface and an extended
 * class, so both names are accepted in the same position.
 */
public final class KotlinSourceScanner implements SourceScanner {

    private static final String KOTLIN_SUFFIX = ".kt";

    /** An unescaped dollar starts a Kotlin string template. */
    private static final Pattern SIMPLE_STRING_TEMPLATE =
        Pattern.compile("(?<!\\\\)(?:\\\\\\\\)*\\$");

    @Override
    public boolean supports(Path sourceFile) {
        return sourceFile.getFileName().toString().endsWith(KOTLIN_SUFFIX);
    }

    @Override
    public List<DelegateType> scan(Path sourceFile) throws IOException {
        String source = Files.readString(sourceFile, StandardCharsets.UTF_8);
        SanitizedSource sanitized = KotlinSanitizer.sanitize(source);
        List<DelegateType> found = new ArrayList<>();
        for (KotlinDeclaration declaration : KotlinDeclarations.parse(sanitized)) {
            if (declaration.isConcrete() && declaration.supertypes().stream()
                .anyMatch(DelegateSupertypes::marksDelegate)) {
                found.add(delegateType(sourceFile, sanitized, declaration));
            }
        }
        return found;
    }

    private static DelegateType delegateType(Path sourceFile, SanitizedSource source,
        KotlinDeclaration declaration) {
        String delegateReference = declaration.annotations().stream()
            .filter(annotation -> BeanNameAnnotations.contains(annotation.name()))
            .map(annotation -> literalValue(source, annotation))
            .flatMap(Optional::stream)
            .filter(value -> !value.isBlank())
            .findFirst()
            .orElseGet(() -> Names.decapitalize(declaration.name()));
        return new DelegateType(sourceFile, declaration.name(), delegateReference);
    }

    private static Optional<String> literalValue(SanitizedSource source,
        KotlinDeclaration.AnnotationRef annotation) {
        List<SanitizedSource.StringLiteral> literals = source.literals().stream()
            .filter(literal -> literal.start() >= annotation.argumentsStart())
            .filter(literal -> literal.end() <= annotation.argumentsEnd())
            .toList();
        if (literals.size() != 1 || !isLiteralValueArgument(source, annotation)
            || containsInterpolation(source, literals.get(0))) {
            return Optional.empty();
        }
        return source.literalValuesIn(annotation.argumentsStart(), annotation.argumentsEnd()).stream()
            .findFirst();
    }

    private static boolean isLiteralValueArgument(SanitizedSource source,
        KotlinDeclaration.AnnotationRef annotation) {
        StringBuilder structure = new StringBuilder();
        String text = source.text();
        for (int index = annotation.argumentsStart(); index < annotation.argumentsEnd(); index++) {
            char current = text.charAt(index);
            if (!Character.isWhitespace(current)) {
                structure.append(current);
            }
        }
        return structure.toString().equals("()") || structure.toString().equals("(value=)");
    }

    private static boolean containsInterpolation(SanitizedSource source,
        SanitizedSource.StringLiteral literal) {
        int delimiterLength = literal.raw() ? 3 : 1;
        String content = source.original().substring(
            literal.start() + delimiterLength, literal.end() - delimiterLength);
        if (literal.raw()) {
            return content.contains("$");
        }
        return SIMPLE_STRING_TEMPLATE.matcher(content).find();
    }
}
