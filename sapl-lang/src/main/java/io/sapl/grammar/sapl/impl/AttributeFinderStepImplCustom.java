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
package io.sapl.grammar.sapl.impl;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithoutParent;
import io.sapl.interpreter.selection.ResultNode;
import lombok.NonNull;
import reactor.core.publisher.Flux;

/**
 * Implements the application of an attribute finder step to a previous value.
 *
 * Grammar: Step: &#39;.&#39; ({AttributeFinderStep} &#39;&lt;&#39; idSteps+=ID
 * (&#39;.&#39; idSteps+=ID)* &#39;&gt;&#39;) ;
 */
public class AttributeFinderStepImplCustom extends AttributeFinderStepImpl {

	private static final String EXTERNAL_ATTRIBUTE_IN_TARGET = "Attribute resolution error. Attribute '%s' is not allowed in target.";

	private static final String ATTRIBUTE_RESOLUTION = "Attribute resolution error. Attribute '%s' cannot be resolved.";

	private static final String UNDEFINED_VALUE = "Undefined value handed over as parameter to policy information point";

	@Override
	public Flux<ResultNode> apply(AbstractAnnotatedJsonNode value, EvaluationContext ctx, @NonNull Val relativeNode) {
		return retrieveAttribute(value.asJsonWithoutAnnotations(), ctx);
	}

	@Override
	public Flux<ResultNode> apply(ArrayResultNode arrayValue, EvaluationContext ctx, @NonNull Val relativeNode) {
		return retrieveAttribute(arrayValue.asJsonWithoutAnnotations(), ctx);
	}

	private Flux<ResultNode> retrieveAttribute(Val value, EvaluationContext ctx) {
		final String fullyQualifiedName = getFullyQualifiedName(ctx);
		if (TargetExpressionIdentifierUtil.isInTargetExpression(this)) {
			return Flux.error(new PolicyEvaluationException(EXTERNAL_ATTRIBUTE_IN_TARGET, fullyQualifiedName));
		}
		if (value.isUndefined()) {
			return Flux.error(new PolicyEvaluationException(UNDEFINED_VALUE));
		}
		final Flux<Val> jsonNodeFlux = ctx.getAttributeCtx().evaluate(fullyQualifiedName, value, ctx, getArguments())
				.onErrorResume(error -> Flux
						.error(new PolicyEvaluationException(error, ATTRIBUTE_RESOLUTION, fullyQualifiedName)));
		return jsonNodeFlux.map(JsonNodeWithoutParent::new);
	}

	private String getFullyQualifiedName(EvaluationContext ctx) {
		String fullyQualifiedName = String.join(".", getIdSteps());
		if (ctx.getImports().containsKey(fullyQualifiedName)) {
			fullyQualifiedName = ctx.getImports().get(fullyQualifiedName);
		}
		return fullyQualifiedName;
	}

}
