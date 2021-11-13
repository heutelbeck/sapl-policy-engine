/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
		this(attributeCtx, functionCtx, new VariableContext(environmentVariables), new HashMap<>(1));
	}

	private EvaluationContext(AttributeContext attributeContext, FunctionContext functionContext,
			VariableContext variableContext, Map<String, String> imports) {
		this.attributeCtx = attributeContext;
		this.functionCtx = functionContext;
		this.variableCtx = variableContext;
		this.imports = new HashMap<>(imports);
	}

	public EvaluationContext withEnvironmentVariable(String identifier, JsonNode value) {
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
