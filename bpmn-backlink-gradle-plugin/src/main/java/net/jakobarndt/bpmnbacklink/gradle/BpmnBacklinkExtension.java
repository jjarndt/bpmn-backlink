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
package net.jakobarndt.bpmnbacklink.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

/**
 * Build-script configuration for the bpmn-backlink tasks, registered as the
 * {@code bpmnBacklink} extension. The conventions mirror the defaults of the
 * Maven plugin; see {@link BpmnBacklinkPlugin} for the values.
 */
public abstract class BpmnBacklinkExtension {

    /**
     * @return the Java source root that is scanned for delegate types
     */
    public abstract DirectoryProperty getSourceDirectory();

    /**
     * @return the root directory below which {@code *.bpmn} files are indexed
     */
    public abstract DirectoryProperty getBpmnDirectory();

    /**
     * @return the root against which indexed BPMN paths are relativized before
     *     they are stored in a {@code @CalledFrom} annotation
     */
    public abstract DirectoryProperty getBpmnReferenceRoot();

    /**
     * @return whether both tasks skip their work entirely
     */
    public abstract Property<Boolean> getSkip();

    /**
     * @return whether {@code bpmnBacklinkCheck} fails the build when drift is
     *     detected; when {@code false}, drift is only logged as a warning
     */
    public abstract Property<Boolean> getFailOnDrift();

    /**
     * @return whether {@code bpmnBacklinkUpdate} runs automatically before
     *     {@code compileJava}
     */
    public abstract Property<Boolean> getUpdateBeforeCompile();

    /**
     * @return whether {@code bpmnBacklinkCheck} is attached to the {@code check}
     *     lifecycle task
     */
    public abstract Property<Boolean> getCheckOnCheck();
}
