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

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import net.jakobarndt.bpmnbacklink.core.util.Names;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Finds concrete Java delegate types with JavaParser on the source level,
 * without loading any class.
 *
 * <p>A type qualifies as a delegate when it is a concrete (non-abstract,
 * non-interface) class that either implements {@code JavaDelegate} or extends
 * {@code AbstractJavaDelegate}. Nested types are reachable as well.
 */
public final class JavaSourceScanner implements SourceScanner {

    private static final String JAVA_SUFFIX = ".java";

    @Override
    public boolean supports(Path sourceFile) {
        return sourceFile.getFileName().toString().endsWith(JAVA_SUFFIX);
    }

    @Override
    public List<DelegateType> scan(Path sourceFile) throws IOException {
        List<DelegateType> found = new ArrayList<>();
        CompilationUnit unit;
        try {
            unit = StaticJavaParser.parse(sourceFile);
        } catch (RuntimeException parseFailure) {
            // A source file that does not parse cannot be a delegate we can rewrite.
            return found;
        }
        unit.findAll(ClassOrInterfaceDeclaration.class).stream()
            .filter(JavaSourceScanner::isConcreteDelegate)
            .map(type -> delegateType(sourceFile, type))
            .forEach(found::add);
        return found;
    }

    private static DelegateType delegateType(Path sourceFile, ClassOrInterfaceDeclaration type) {
        String simpleName = type.getNameAsString();
        String delegateReference = type.getAnnotations().stream()
            .filter(JavaSourceScanner::namesABean)
            .map(JavaSourceScanner::literalValue)
            .flatMap(Optional::stream)
            .filter(value -> !value.isBlank())
            .findFirst()
            .orElseGet(() -> Names.decapitalize(simpleName));
        return new DelegateType(sourceFile, simpleName, delegateReference);
    }

    private static boolean namesABean(AnnotationExpr annotation) {
        return BeanNameAnnotations.contains(Names.simpleName(annotation.getNameAsString()));
    }

    private static Optional<String> literalValue(AnnotationExpr annotation) {
        if (annotation.isSingleMemberAnnotationExpr()) {
            return stringValue(annotation.asSingleMemberAnnotationExpr().getMemberValue());
        }
        if (!annotation.isNormalAnnotationExpr()) {
            return Optional.empty();
        }
        return annotation.asNormalAnnotationExpr().getPairs().stream()
            .filter(pair -> pair.getNameAsString().equals("value"))
            .map(pair -> pair.getValue())
            .map(JavaSourceScanner::stringValue)
            .flatMap(Optional::stream)
            .findFirst();
    }

    private static Optional<String> stringValue(Expression expression) {
        if (!expression.isStringLiteralExpr()) {
            return Optional.empty();
        }
        return Optional.of(expression.asStringLiteralExpr().asString());
    }

    private static boolean isConcreteDelegate(ClassOrInterfaceDeclaration type) {
        if (type.isInterface() || type.isAbstract()) {
            return false;
        }
        return matchesAny(type.getImplementedTypes(), DelegateSupertypes.INTERFACES)
            || matchesAny(type.getExtendedTypes(), DelegateSupertypes.SUPERCLASSES);
    }

    private static boolean matchesAny(Iterable<ClassOrInterfaceType> declaredTypes, Set<String> simpleNames) {
        for (ClassOrInterfaceType declared : declaredTypes) {
            if (simpleNames.contains(Names.simpleName(declared.getNameWithScope()))) {
                return true;
            }
        }
        return false;
    }
}
