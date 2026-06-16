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
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import net.jakobarndt.bpmnbacklink.core.util.Names;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Finds concrete Java delegate types in a source tree without loading any
 * class, using JavaParser on the source level.
 *
 * <p>A type qualifies as a delegate when it is a concrete (non-abstract,
 * non-interface) class that either implements {@code JavaDelegate} or extends
 * {@code AbstractJavaDelegate}. The supertypes are matched by their simple
 * names, so both imported and fully qualified references are recognised.
 */
public final class DelegateScanner {

    private static final String JAVA_SUFFIX = ".java";

    /** Interface simple names that mark a delegate when implemented. */
    static final Set<String> DELEGATE_INTERFACES = Set.of("JavaDelegate");

    /** Superclass simple names that mark a delegate when extended. */
    static final Set<String> DELEGATE_SUPERCLASSES = Set.of("AbstractJavaDelegate");

    private final Path sourceDirectory;

    /**
     * @param sourceDirectory the Java source root to scan
     */
    public DelegateScanner(Path sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
    }

    /**
     * Walks the source tree and collects every concrete delegate type.
     *
     * @return the discovered delegate types, in ascending file-path order
     * @throws IOException if the source tree cannot be walked
     */
    public List<DelegateType> scan() throws IOException {
        List<DelegateType> result = new ArrayList<>();
        if (!Files.isDirectory(sourceDirectory)) {
            return result;
        }
        List<Path> javaFiles;
        try (Stream<Path> paths = Files.walk(sourceDirectory)) {
            javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(JAVA_SUFFIX))
                .sorted()
                .toList();
        }
        for (Path javaFile : javaFiles) {
            scanFile(javaFile, result);
        }
        return result;
    }

    private void scanFile(Path javaFile, List<DelegateType> result) throws IOException {
        CompilationUnit unit;
        try {
            unit = StaticJavaParser.parse(javaFile);
        } catch (RuntimeException parseFailure) {
            // A source file that does not parse cannot be a delegate we can rewrite.
            return;
        }
        unit.findAll(ClassOrInterfaceDeclaration.class).stream()
            .filter(this::isConcreteDelegate)
            .forEach(type -> result.add(new DelegateType(javaFile, type.getNameAsString())));
    }

    private boolean isConcreteDelegate(ClassOrInterfaceDeclaration type) {
        if (type.isInterface() || type.isAbstract()) {
            return false;
        }
        return implementsDelegateInterface(type) || extendsDelegateSuperclass(type);
    }

    private boolean implementsDelegateInterface(ClassOrInterfaceDeclaration type) {
        return matchesAny(type.getImplementedTypes(), DELEGATE_INTERFACES);
    }

    private boolean extendsDelegateSuperclass(ClassOrInterfaceDeclaration type) {
        return matchesAny(type.getExtendedTypes(), DELEGATE_SUPERCLASSES);
    }

    private boolean matchesAny(Iterable<ClassOrInterfaceType> declaredTypes, Set<String> simpleNames) {
        for (ClassOrInterfaceType declared : declaredTypes) {
            if (simpleNames.contains(Names.simpleName(declared.getNameWithScope()))) {
                return true;
            }
        }
        return false;
    }
}
