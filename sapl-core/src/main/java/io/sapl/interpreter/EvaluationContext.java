/**
 * Copyright © 2020 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;

import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.variables.VariableContext;
import lombok.Getter;

@Getter
public class EvaluationContext {

	AttributeContext attributeCtx;

	FunctionContext functionCtx;

	VariableContext variableCtx;

	Map<String, String> imports;

	public EvaluationContext() {
		this.variableCtx = new VariableContext();
		this.imports = new HashMap<>();
	}

	public EvaluationContext(FunctionContext functionContext, VariableContext variableContext) {
		this.functionCtx = requireNonNull(functionContext);
		this.variableCtx = requireNonNull(variableContext);
		this.imports = new HashMap<>();
	}

	public EvaluationContext(FunctionContext functionContext, VariableContext variableContext,
			Map<String, String> imports) {
		this.functionCtx = requireNonNull(functionContext);
		this.variableCtx = requireNonNull(variableContext);
		this.imports = imports == null ? new HashMap<>() : new HashMap<>(imports);
	}

	public EvaluationContext(AttributeContext attributeContext, FunctionContext functionContext,
			VariableContext variableContext) {
		this.attributeCtx = requireNonNull(attributeContext);
		this.functionCtx = requireNonNull(functionContext);
		this.variableCtx = requireNonNull(variableContext);
		this.imports = new HashMap<>();
	}

	public EvaluationContext(AttributeContext attributeContext, FunctionContext functionContext,
			VariableContext variableContext, Map<String, String> imports) {
		this.attributeCtx = requireNonNull(attributeContext);
		this.functionCtx = requireNonNull(functionContext);
		this.variableCtx = requireNonNull(variableContext);
		this.imports = imports == null ? new HashMap<>() : new HashMap<>(imports);
	}

	/**
	 * Creates a copy of this evaluation context. The copy references the same instance of
	 * the attribute context and function context, but deep copies of the variable context
	 * and imports. The attribute context and function context are created once during
	 * startup, but the variable context and imports may be specific to a certain scope.
	 * Before passing the evaluation context to a narrower scope, it should be copied to
	 * make sure, the current context is not polluted by elements of the narrower scope
	 * when after the narrower scope has been processed.
	 * @return a copy of this evaluation context to be passed to narrower scopes.
	 */
	public EvaluationContext copy() {
		return new EvaluationContext(attributeCtx, functionCtx, variableCtx.copy(), new HashMap<>(imports));
	}

}
