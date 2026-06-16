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
package net.jakobarndt.bpmnbacklink.core.write;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.utils.StringEscapeUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads and rewrites the {@code @CalledFrom} annotation of a single delegate
 * source file with JavaParser and the {@link LexicalPreservingPrinter}, so that
 * the rest of the file keeps its original formatting and comments.
 *
 * <p>All operations are idempotent: applying the same expected value twice
 * produces no second change.
 */
public final class AnnotationWriter {

    public static final String ANNOTATION_SIMPLE_NAME = "CalledFrom";

    public static final String ANNOTATION_FQN = "net.jakobarndt.bpmnbacklink.annotation.CalledFrom";

    private static final String ANNOTATION_VALUE_ATTRIBUTE = "value";

    /**
     * Reads the BPMN paths currently stored in the {@code @CalledFrom}
     * annotation of the top-level type in the given source file.
     *
     * @param javaFile the delegate source file
     * @return the values found, in source order; an empty list if the
     *     annotation is absent or carries no value
     * @throws IOException if the file cannot be read
     */
    public List<String> readCurrentValues(Path javaFile) throws IOException {
        CompilationUnit unit = parse(javaFile);
        return findTopLevelType(unit)
            .flatMap(type -> type.getAnnotationByName(ANNOTATION_SIMPLE_NAME))
            .map(AnnotationWriter::extractValues)
            .orElseGet(ArrayList::new);
    }

    /**
     * Rewrites the annotation of the given file to carry exactly the expected
     * values, preserving the rest of the file.
     *
     * <p>An empty {@code expected} list removes the annotation (and the import,
     * if it then becomes unused). Calling this method when the file already
     * matches still rewrites the file content identically; callers that care
     * about idempotency should guard with {@link #readCurrentValues(Path)}.
     *
     * @param javaFile the delegate source file
     * @param expected the desired BPMN paths (assumed already sorted)
     * @throws IOException if the file cannot be read or written
     */
    public void write(Path javaFile, List<String> expected) throws IOException {
        CompilationUnit unit = parse(javaFile);
        TypeDeclaration<?> type = findTopLevelType(unit)
            .orElseThrow(() -> new IllegalStateException("No top-level type in " + javaFile));

        type.getAnnotationByName(ANNOTATION_SIMPLE_NAME).ifPresent(AnnotationExpr::remove);

        if (expected.isEmpty()) {
            removeImportIfUnused(unit);
        } else {
            type.addAnnotation(buildAnnotation(expected));
            ensureImport(unit);
        }

        String rendered = LexicalPreservingPrinter.print(unit);
        Files.writeString(javaFile, rendered, StandardCharsets.UTF_8);
    }

    private CompilationUnit parse(Path javaFile) throws IOException {
        CompilationUnit unit = StaticJavaParser.parse(javaFile);
        return LexicalPreservingPrinter.setup(unit);
    }

    private Optional<TypeDeclaration<?>> findTopLevelType(CompilationUnit unit) {
        return unit.getPrimaryType()
            .or(() -> unit.getTypes().isEmpty()
                ? Optional.empty()
                : Optional.of(unit.getType(0)));
    }

    private static List<String> extractValues(AnnotationExpr annotation) {
        Stream<Expression> valueExpressions = Stream.empty();
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            valueExpressions = Stream.of(single.getMemberValue());
        } else if (annotation instanceof NormalAnnotationExpr normal) {
            valueExpressions = normal.getPairs().stream()
                .filter(pair -> pair.getNameAsString().equals(ANNOTATION_VALUE_ATTRIBUTE))
                .map(MemberValuePair::getValue);
        }
        return valueExpressions
            .flatMap(AnnotationWriter::flattenValues)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private static Stream<String> flattenValues(Expression expression) {
        if (expression instanceof ArrayInitializerExpr array) {
            return array.getValues().stream().flatMap(AnnotationWriter::flattenValues);
        }
        if (expression instanceof StringLiteralExpr literal) {
            return Stream.of(literal.asString());
        }
        return Stream.empty();
    }

    private void removeImportIfUnused(CompilationUnit unit) {
        boolean stillUsed = unit.findAll(AnnotationExpr.class).stream()
            .anyMatch(a -> {
                String name = a.getNameAsString();
                return name.equals(ANNOTATION_SIMPLE_NAME) || name.equals(ANNOTATION_FQN);
            });
        if (stillUsed) {
            return;
        }
        NodeList<ImportDeclaration> imports = unit.getImports();
        imports.removeIf(this::isAnnotationImport);
    }

    private boolean isAnnotationImport(ImportDeclaration importDeclaration) {
        boolean isRegularImport = !importDeclaration.isStatic() && !importDeclaration.isAsterisk();
        boolean matchesAnnotation = importDeclaration.getNameAsString().equals(ANNOTATION_FQN);
        return isRegularImport && matchesAnnotation;
    }

    private AnnotationExpr buildAnnotation(List<String> values) {
        if (values.size() == 1) {
            return StaticJavaParser.parseAnnotation(
                "@" + ANNOTATION_SIMPLE_NAME + "(" + literal(values.get(0)) + ")");
        }
        String body = values.stream()
            .map(this::literal)
            .collect(Collectors.joining(", "));
        return StaticJavaParser.parseAnnotation("@" + ANNOTATION_SIMPLE_NAME + "({" + body + "})");
    }

    private String literal(String value) {
        return "\"" + StringEscapeUtils.escapeJava(value) + "\"";
    }

    private void ensureImport(CompilationUnit unit) {
        boolean present = unit.getImports().stream()
            .anyMatch(this::isAnnotationImport);
        if (present) {
            return;
        }
        unit.addImport(new ImportDeclaration(ANNOTATION_FQN, false, false));
    }
}
