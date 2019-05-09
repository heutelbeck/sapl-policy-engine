/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.sapl.interpreter;

import java.util.HashMap;
import java.util.Map;

import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.variables.VariableContext;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EvaluationContext {

	AttributeContext attributeCtx;

	FunctionContext functionCtx;

	VariableContext variableCtx;

	Map<String, String> imports;

	public EvaluationContext(AttributeContext attributeContext,
			FunctionContext functionContext, VariableContext variableContext) {
		attributeCtx = attributeContext;
		functionCtx = functionContext;
		variableCtx = variableContext;
		imports = new HashMap<>();
	}

	public EvaluationContext(FunctionContext functionContext,
			VariableContext variableContext, Map<String, String> imports) {
		functionCtx = functionContext;
		variableCtx = variableContext;
		this.imports = new HashMap<>(imports);
	}

}
