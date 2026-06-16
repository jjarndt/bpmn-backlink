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
package net.jakobarndt.bpmnbacklink.core.bpmn;

import net.jakobarndt.bpmnbacklink.core.util.Names;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.xml.instance.DomElement;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Indexes the delegate references of every {@code *.bpmn} file below a root
 * directory using the Camunda BPMN Model API.
 *
 * <p>Two reference styles are recognised on any BPMN element:
 * <ul>
 *   <li>{@code camunda:delegateExpression="${beanName}"} indexes under
 *       {@code beanName};</li>
 *   <li>{@code camunda:class="a.b.C"} indexes under the camelCased simple name
 *       of the class ({@code c}).</li>
 * </ul>
 *
 * <p>The resulting index maps each delegate reference to the sorted set of BPMN
 * file paths that reference it, relative to a configurable reference root and
 * using {@code /} as path separator (matching the {@code @Deployment} format).
 */
public final class BpmnDelegateIndexer {

    /** Camunda extension namespace used for delegate attributes. */
    static final String CAMUNDA_NS = "http://camunda.org/schema/1.0/bpmn";

    private static final String DELEGATE_EXPRESSION = "delegateExpression";
    private static final String CLASS = "class";
    private static final String BPMN_SUFFIX = ".bpmn";

    private final Path bpmnDirectory;
    private final Path referenceRoot;

    /**
     * @param bpmnDirectory the root directory below which BPMN files are indexed
     * @param referenceRoot the root against which BPMN paths are relativized
     */
    public BpmnDelegateIndexer(Path bpmnDirectory, Path referenceRoot) {
        this.bpmnDirectory = bpmnDirectory;
        this.referenceRoot = referenceRoot;
    }

    /**
     * Walks the BPMN directory and builds the delegate index.
     *
     * @return a map from delegate reference to the sorted set of relative BPMN
     *     paths that reference it; never {@code null}
     * @throws IOException if the BPMN directory cannot be walked
     */
    public Map<String, SortedSet<String>> index() throws IOException {
        Map<String, SortedSet<String>> result = new HashMap<>();
        if (!Files.isDirectory(bpmnDirectory)) {
            return result;
        }

        List<Path> bpmnFiles;
        try (Stream<Path> paths = Files.walk(bpmnDirectory)) {
            bpmnFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(BPMN_SUFFIX))
                .sorted()
                .toList();
        }

        for (Path bpmnFile : bpmnFiles) {
            indexFile(bpmnFile, result);
        }
        return result;
    }

    private void indexFile(Path bpmnFile, Map<String, SortedSet<String>> result) throws IOException {
        String relativePath = relativeReference(bpmnFile);
        try (InputStream in = Files.newInputStream(bpmnFile)) {
            BpmnModelInstance model = Bpmn.readModelFromStream(in);
            DomElement root = model.getDocument().getRootElement();
            collect(root, relativePath, result);
        } catch (RuntimeException parseFailure) {
            // The Camunda model API throws an unchecked ModelParseException for
            // malformed BPMN. Because this tool writes @CalledFrom annotations,
            // silently skipping an unparseable file would drop its references and
            // could remove valid backlinks. Fail fast, but name the offending file.
            throw new IOException("Failed to parse BPMN file: " + bpmnFile, parseFailure);
        }
    }

    private void collect(DomElement element, String relativePath, Map<String, SortedSet<String>> result) {
        collectDelegateExpression(element, relativePath, result);
        collectClassName(element, relativePath, result);
        for (DomElement child : element.getChildElements()) {
            collect(child, relativePath, result);
        }
    }

    private void collectDelegateExpression(DomElement element, String relativePath,
            Map<String, SortedSet<String>> result) {
        String delegateExpression = element.getAttribute(CAMUNDA_NS, DELEGATE_EXPRESSION);
        if (delegateExpression == null) {
            return;
        }
        // unwrapExpression never returns null for a non-null argument.
        String beanName = Names.unwrapExpression(delegateExpression);
        if (beanName.isBlank()) {
            return;
        }
        addReference(result, beanName, relativePath);
    }

    private void collectClassName(DomElement element, String relativePath,
            Map<String, SortedSet<String>> result) {
        String className = element.getAttribute(CAMUNDA_NS, CLASS);
        if (className == null) {
            return;
        }
        // toDelegateReference never returns null for a non-null argument.
        String reference = Names.toDelegateReference(className);
        if (reference.isBlank()) {
            return;
        }
        addReference(result, reference, relativePath);
    }

    private void addReference(Map<String, SortedSet<String>> result, String reference, String relativePath) {
        result.computeIfAbsent(reference, k -> new TreeSet<>()).add(relativePath);
    }

    private String relativeReference(Path bpmnFile) {
        Path relative = referenceRoot.toAbsolutePath().normalize()
            .relativize(bpmnFile.toAbsolutePath().normalize());
        return relative.toString().replace('\\', '/');
    }
}
