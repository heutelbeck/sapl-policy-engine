/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter.variables;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class VariableContext {

	private static final String SUBJECT = "subject";

	private static final String ACTION = "action";

	private static final String RESOURCE = "resource";

	private static final String ENVIRONMENT = "environment";

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private Map<String, JsonNode> variables = new HashMap<>();

	public VariableContext(AuthorizationSubscription authzSubscription) throws PolicyEvaluationException {
		this(authzSubscription, null);
	}

	public VariableContext(AuthorizationSubscription authzSubscription, Map<String, JsonNode> defaultVariables)
			throws PolicyEvaluationException {
		if (authzSubscription != null) {
			if (authzSubscription.getSubject() != null) {
				variables.put(SUBJECT, authzSubscription.getSubject());
			}
			else {
				variables.put(SUBJECT, JSON.nullNode());
			}
			if (authzSubscription.getAction() != null) {
				variables.put(ACTION, authzSubscription.getAction());
			}
			else {
				variables.put(ACTION, JSON.nullNode());
			}
			if (authzSubscription.getResource() != null) {
				variables.put(RESOURCE, authzSubscription.getResource());
			}
			else {
				variables.put(RESOURCE, JSON.nullNode());
			}
			if (authzSubscription.getEnvironment() != null) {
				variables.put(ENVIRONMENT, authzSubscription.getEnvironment());
			}
			else {
				variables.put(ENVIRONMENT, JSON.nullNode());
			}
		}

		if (defaultVariables != null) {
			for (Entry<String, JsonNode> entry : defaultVariables.entrySet()) {
				put(entry.getKey(), entry.getValue());
			}
		}
	}

	public final void put(String identifier, JsonNode value) throws PolicyEvaluationException {
		if (SUBJECT.equals(identifier) || RESOURCE.equals(identifier) || ACTION.equals(identifier)
				|| ENVIRONMENT.equals(identifier)) {
			throw new PolicyEvaluationException("cannot overwrite system variable " + identifier);
		}
		variables.put(identifier, value);
	}

	public Map<String, JsonNode> getVariables() {
		return Collections.unmodifiableMap(variables);
	}

	public boolean exists(String identifier) {
		return variables.containsKey(identifier);
	}

	public JsonNode get(String identifier) throws PolicyEvaluationException {
		JsonNode result = variables.get(identifier);
		if (result == null) {
			throw new PolicyEvaluationException("unbound variable " + identifier);
		}
		return result;
	}

	/**
	 * @return a deep copy of this variables context.
	 */
	public VariableContext copy() {
		final VariableContext copy = new VariableContext();
		variables.forEach((key, value) -> copy.variables.put(key, value.deepCopy()));
		return copy;
	}

}
