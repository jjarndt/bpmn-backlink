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
package com.example

import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate

/**
 * The Kotlin half of the mixed module, referenced by mixed.bpmn via
 * camunda:delegateExpression. It lives below src/main/kotlin, which the project
 * model only knows about if kotlin-maven-plugin registers it; the update goal
 * has to find it either way.
 */
class KotlinOrderDelegate : JavaDelegate {

    override fun execute(execution: DelegateExecution) {
        execution.setVariable("ordered", true)
    }
}
