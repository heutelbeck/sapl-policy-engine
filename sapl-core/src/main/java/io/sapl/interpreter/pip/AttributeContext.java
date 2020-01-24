/**
 * Copyright © 2019 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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
package io.sapl.interpreter.pip;

import java.util.Collection;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pip.AttributeException;
import reactor.core.publisher.Flux;

public interface AttributeContext {

	Flux<JsonNode> evaluate(String attribute, JsonNode value, Map<String, JsonNode> variables);

	Boolean provides(String function);

	Collection<String> findersInLibrary(String pipName);

	void loadPolicyInformationPoint(Object pip) throws AttributeException;

	Collection<PolicyInformationPointDocumentation> getDocumentation();

}
