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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    @Override
    public boolean supports(Path sourceFile) {
        return sourceFile.getFileName().toString().endsWith(KOTLIN_SUFFIX);
    }

    @Override
    public List<DelegateType> scan(Path sourceFile) throws IOException {
        String source = Files.readString(sourceFile, StandardCharsets.UTF_8);
        List<DelegateType> found = new ArrayList<>();
        for (KotlinDeclaration declaration : KotlinDeclarations.parse(KotlinSanitizer.sanitize(source))) {
            if (declaration.isConcrete() && declaration.supertypes().stream()
                .anyMatch(DelegateSupertypes::marksDelegate)) {
                found.add(new DelegateType(sourceFile, declaration.name()));
            }
        }
        return found;
    }
}
