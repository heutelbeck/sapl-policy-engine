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
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.NonNull;
import reactor.core.publisher.Flux;

/**
 * Implements the application of an attribute finder step to a previous value.
 *
 * Grammar: Step: &#39;.&#39; ({AttributeFinderStep} &#39;&lt;&#39; idSteps+=ID
 * (&#39;.&#39; idSteps+=ID)* &#39;&gt;&#39;) ;
 */
public class AttributeFinderStepImplCustom extends AttributeFinderStepImpl {

	private static final String UNDEFINED_VALUE = "Undefined value handed over as parameter to policy information point";

	private static final String EXTERNAL_ATTRIBUTE_IN_TARGET = "Attribute resolution error. Attributes are not allowed in target.";

	@Override
	public Flux<Val> apply(@NonNull Val parentValue) {
		if (parentValue.isError()) {
			return Flux.just(parentValue);
		}
		if (TargetExpressionUtil.isInTargetExpression(this)) {
			return Val.errorFlux(EXTERNAL_ATTRIBUTE_IN_TARGET);
		}
		if (parentValue.isUndefined()) {
			return Val.errorFlux(UNDEFINED_VALUE);
		}
		return Flux.deferContextual(ctxView -> AuthorizationContext.getAttributeContext(ctxView)
				.evaluateAttribute(
						FunctionUtil.resolveAbsoluteFunctionName(getIdSteps(),
								AuthorizationContext.getImports(ctxView)),
						parentValue, getArguments(), AuthorizationContext.getVariables(ctxView))
				.distinctUntilChanged());
	}

	@Override
	public Flux<Val> applyFilterStatement(
			@NonNull Val parentValue,
			int stepId,
			@NonNull FilterStatement statement) {
		return Val.errorFlux("AttributeFinderStep not permitted in filter selection steps.");
	}

}
