/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.interpreter.context.AuthorizationContext.getAttributeContext;
import static io.sapl.interpreter.context.AuthorizationContext.getImports;
import static io.sapl.interpreter.context.AuthorizationContext.getVariables;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.interpreter.ExplainedValue;
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

		return Flux.deferContextual(ctxView -> {
			var attributeContext = getAttributeContext(ctxView);
			var attributeName    = FunctionUtil.resolveAbsoluteFunctionName(getIdSteps(), getImports(ctxView));
			var variables        = getVariables(ctxView);
			// @formatter:off
			return attributeContext
					.evaluateAttribute(attributeName, parentValue, getArguments(), variables)
					.distinctUntilChanged();
			// @formatter:on
		});
	}

	public Flux<ExplainedValue> applyAndExplain(@NonNull ExplainedValue parentValue) {
		if (parentValue.getValue().isError()) {
			return Flux.just(ExplainedValue.of(parentValue.getValue(), parentValue));
		}
		if (TargetExpressionUtil.isInTargetExpression(this)) {
			return Flux.just(ExplainedValue.of(Val.error(EXTERNAL_ATTRIBUTE_IN_TARGET)));
		}
		if (parentValue.getValue().isUndefined()) {
			return Flux.just(ExplainedValue.of(Val.error(UNDEFINED_VALUE), parentValue));
		}

		return Flux.deferContextual(ctxView -> {
			var attributeContext = getAttributeContext(ctxView);
			var attributeName    = FunctionUtil.resolveAbsoluteFunctionName(getIdSteps(), getImports(ctxView));
			var variables        = getVariables(ctxView);
			// @formatter:off
			return attributeContext
					.evaluateAttributeAndExplain(attributeName, parentValue, getArguments(), variables)
					.distinctUntilChanged();
			// @formatter:on
		});
	}

	@Override
	public Flux<Val> applyFilterStatement(@NonNull Val parentValue, int stepId, @NonNull FilterStatement statement) {
		return Val.errorFlux("AttributeFinderStep not permitted in filter selection steps.");
	}

}
