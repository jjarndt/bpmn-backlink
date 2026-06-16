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
package net.jakobarndt.bpmnbacklink.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Test support that materialises BPMN and Java delegate fixtures into a
 * temporary workspace so tests can mutate them freely.
 *
 * <p>The Java delegate fixtures are stored as {@code *.java.txt} resources so
 * the build does not try to compile them. They are copied into the
 * {@code net/example/delegate} package of the temporary source root.
 */
final class Fixtures {

    static final String DELEGATE_PACKAGE_PATH = "net/example/delegate";

    private Fixtures() {
    }

    /**
     * Copies the standard set of BPMN process fixtures into
     * {@code <root>/src/main/resources/bpmn/processes}.
     *
     * @param root the temporary workspace root
     * @return the resources root (the BPMN reference root)
     */
    static Path copyBpmn(Path root) {
        Path resources = root.resolve("src/main/resources");
        copyResource("bpmn/processes/order.bpmn", resources.resolve("bpmn/processes/order.bpmn"));
        copyResource("bpmn/processes/sub/shipping.bpmn", resources.resolve("bpmn/processes/sub/shipping.bpmn"));
        return resources;
    }

    /**
     * @param root the temporary workspace root
     * @return the BPMN process directory inside the workspace
     */
    static Path bpmnProcessesDir(Path root) {
        return root.resolve("src/main/resources/bpmn/processes");
    }

    /**
     * @param root the temporary workspace root
     * @return the Java source root inside the workspace
     */
    static Path sourceRoot(Path root) {
        return root.resolve("src/main/java");
    }

    /**
     * Copies the named Java delegate fixtures into the delegate package of the
     * workspace source root.
     *
     * @param root the temporary workspace root
     * @param fixtureToTarget alternating fixture base name and target file name
     *     pairs, for example {@code "OrderDelegate", "OrderDelegate.java"}
     */
    static void copyDelegates(Path root, String... fixtureToTarget) {
        Path packageDir = sourceRoot(root).resolve(DELEGATE_PACKAGE_PATH);
        for (int i = 0; i < fixtureToTarget.length; i += 2) {
            String fixture = fixtureToTarget[i];
            String target = fixtureToTarget[i + 1];
            copyResource("delegates/" + fixture + ".java.txt", packageDir.resolve(target));
        }
    }

    /**
     * @param root the temporary workspace root
     * @param fileName the delegate file name, for example {@code OrderDelegate.java}
     * @return the delegate file path inside the workspace
     */
    static Path delegateFile(Path root, String fileName) {
        return sourceRoot(root).resolve(DELEGATE_PACKAGE_PATH).resolve(fileName);
    }

    static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void copyResource(String resource, Path target) {
        try (InputStream in = Fixtures.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource: " + resource);
            }
            Files.createDirectories(target.getParent());
            Files.write(target, in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<String> lines(String content) {
        return List.of(content.split("\n", -1));
    }
}
