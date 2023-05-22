/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.Map;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.AttributeFinderStep;
import io.sapl.grammar.sapl.impl.util.FunctionUtil;
import io.sapl.grammar.sapl.impl.util.TargetExpressionUtil;
import io.sapl.interpreter.context.AuthorizationContext;
import reactor.core.publisher.Flux;

/**
 * Implements the evaluation of an environment attribute.
 */
public class BasicEnvironmentHeadAttributeImplCustom extends BasicEnvironmentHeadAttributeImpl {

	private static final String EXTERNAL_ATTRIBUTE_IN_TARGET = "Attribute resolution error. Attribute '%s' is not allowed in target.";

	@Override
	public Flux<Val> evaluate() {
		return Flux.deferContextual(ctx -> {
			var fullyQualifiedName = FunctionUtil.resolveAbsoluteFunctionName(idSteps,
					AuthorizationContext.getImports(ctx));

			if (TargetExpressionUtil.isInTargetExpression(this))
				return Flux.just(Val.error(EXTERNAL_ATTRIBUTE_IN_TARGET,fullyQualifiedName).withTrace(AttributeFinderStep.class,
						Map.of("attribute", Val.of(fullyQualifiedName))));
			
			return AuthorizationContext.getAttributeContext(ctx)
					.evaluateEnvironmentAttribute(fullyQualifiedName, getArguments(),
							AuthorizationContext.getVariables(ctx))
					.next();
		});
	}

}
