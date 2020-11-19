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
package io.sapl.interpreter;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.variables.VariableContext;
import lombok.Getter;
import lombok.NonNull;

@Getter
public class EvaluationContext {

	AttributeContext attributeCtx;
	FunctionContext functionCtx;
	VariableContext variableCtx;
	Map<String, String> imports;

	public EvaluationContext(@NonNull AttributeContext attributeCtx, @NonNull FunctionContext functionCtx,
			Map<String, JsonNode> environmentVariables) {
		this.attributeCtx = attributeCtx;
		this.functionCtx = functionCtx;
		this.variableCtx = new VariableContext(environmentVariables);
		this.imports = new HashMap<>();
	}

	private EvaluationContext(@NonNull AttributeContext attributeContext, @NonNull FunctionContext functionContext,
			@NonNull VariableContext variableContext, @NonNull Map<String, String> imports) {
		this.attributeCtx = attributeContext;
		this.functionCtx = functionContext;
		this.variableCtx = variableContext;
		this.imports = new HashMap<>(imports);
	}

	/**
	 * Creates a copy of this evaluation context. The copy references the same
	 * instance of the attribute context and function context, but deep copies of
	 * the variable context and imports. The attribute context and function context
	 * are created once during startup, but the variable context and imports may be
	 * specific to a certain scope. Before passing the evaluation context to a
	 * narrower scope, it should be copied to make sure, the current context is not
	 * polluted by elements of the narrower scope when after the narrower scope has
	 * been processed.
	 */
	public EvaluationContext withEnvironmentVariable(String identifier, JsonNode value)
			throws PolicyEvaluationException {
		return new EvaluationContext(attributeCtx, functionCtx, variableCtx.withEnvironmentVariable(identifier, value),
				imports);
	}

	public EvaluationContext withImports(Map<String, String> localImports) {
		return new EvaluationContext(attributeCtx, functionCtx, variableCtx, localImports);
	}

	public EvaluationContext forAuthorizationSubscription(AuthorizationSubscription authzSubscription) {
		return new EvaluationContext(attributeCtx, functionCtx,
				variableCtx.forAuthorizationSubscription(authzSubscription), imports);
	}

}
