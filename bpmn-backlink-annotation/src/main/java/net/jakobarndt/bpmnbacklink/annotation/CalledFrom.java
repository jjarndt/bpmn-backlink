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
package net.jakobarndt.bpmnbacklink.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents which BPMN processes invoke a Java delegate.
 *
 * <p>A Camunda 7 service task can reference a Java delegate either through a
 * {@code delegateExpression} bean reference or through a {@code camunda:class}
 * attribute. This annotation records, on the delegate type itself, the set of
 * BPMN process files from which the annotated delegate is called.
 *
 * <p>Each value is a BPMN resource path expressed in the same format used by
 * Camunda's {@code @Deployment(resources = ...)} declarations, that is a path
 * relative to the BPMN reference root using {@code /} as separator (for example
 * {@code "bpmn/processes/order.bpmn"}). Keeping the format aligned with
 * {@code @Deployment} lets IDEs resolve the string into a clickable link to the
 * BPMN source, so a reader can navigate from the delegate to every process that
 * uses it.
 *
 * <p>The annotation is purely documentary. It is maintained automatically and
 * has no runtime effect: nothing reads it at execution time and its presence or
 * absence does not change process behaviour.
 *
 * @see <a href="https://docs.camunda.org/manual/7.23/user-guide/process-engine/delegation-code/">Camunda delegation code</a>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CalledFrom {

    /**
     * The BPMN process resource paths, in {@code @Deployment} resource format,
     * from which the annotated delegate is called.
     *
     * @return the sorted, deterministic list of BPMN resource paths
     */
    String[] value();
}
