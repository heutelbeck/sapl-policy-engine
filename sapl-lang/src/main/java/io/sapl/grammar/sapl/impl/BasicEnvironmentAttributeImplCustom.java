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
package io.sapl.grammar.sapl.impl;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.context.AuthorizationContext;
import reactor.core.publisher.Flux;

/**
 * Implements the evaluation of an environment attribute.
 */
public class BasicEnvironmentAttributeImplCustom extends BasicEnvironmentAttributeImpl {

	private static final String EXTERNAL_ATTRIBUTE_IN_TARGET = "Attribute resolution error. Attributes not allowed in target.";

	@Override
	public Flux<Val> evaluate() {
		if (TargetExpressionUtil.isInTargetExpression(this))
			return Val.errorFlux(EXTERNAL_ATTRIBUTE_IN_TARGET);

		return Flux.deferContextual(ctxView -> {
			return AuthorizationContext.getAttributeContext(ctxView).evaluateEnvironmentAttribute(
					FunctionUtil.resolveAbsoluteFunctionName(getIdSteps(), AuthorizationContext.getImports(ctxView)),
					getArguments(), AuthorizationContext.getVariables(ctxView))
					.distinctUntilChanged();
		});
	}

}
