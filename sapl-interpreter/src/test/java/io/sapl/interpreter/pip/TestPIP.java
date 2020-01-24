/**
 * Copyright © 2017 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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

import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@PolicyInformationPoint(name = TestPIP.NAME, description = TestPIP.DESCRIPTION)
public class TestPIP {

	public static final String NAME = "sapl.pip.test";

	public static final String DESCRIPTION = "Policy information Point for testing";

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	public TestPIP() {
		LOGGER.trace("PIP: " + NAME);
	}

	@Attribute
	public Flux<JsonNode> echo(JsonNode value, Map<String, JsonNode> variables) {
		logVars(variables);
		return Flux.just(value);
	}

	private void logVars(Map<String, JsonNode> variables) {
		for (Entry<String, JsonNode> entry : variables.entrySet()) {
			LOGGER.trace("env: {} value: {}", entry.getKey(), entry.getValue());
		}
	}

	@Attribute
	public Flux<JsonNode> someVariableOrNull(JsonNode value, Map<String, JsonNode> variables) {
		logVars(variables);
		if (variables.containsKey(value.asText())) {
			return Flux.just(variables.get(value.asText()).deepCopy());
		}
		return Flux.just(JSON.nullNode());
	}

}
