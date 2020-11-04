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

import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.EcoreUtil2;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.PolicyBody;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithoutParent;
import io.sapl.interpreter.selection.ResultNode;
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
	public Flux<ResultNode> apply(AbstractAnnotatedJsonNode value, EvaluationContext ctx, boolean isBody,
			Val relativeNode) {
		return retrieveAttribute(value.asJsonWithoutAnnotations(), ctx, isBody);
	}

	@Override
	public Flux<ResultNode> apply(ArrayResultNode arrayValue, EvaluationContext ctx, boolean isBody, Val relativeNode) {
		return retrieveAttribute(arrayValue.asJsonWithoutAnnotations(), ctx, isBody);
	}

	private Flux<ResultNode> retrieveAttribute(Val value, EvaluationContext ctx, boolean isBody) {
		final String fullyQualifiedName = getFullyQualifiedName(ctx);
		if (EcoreUtil2.getContainerOfType(this, PolicyBody.class) == null) {
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

	@Override
	public int hash(Map<String, String> imports) {
		int hash = 17;
		hash = 37 * hash + Objects.hashCode(getClass().getTypeName());
		for (String idStep : getIdSteps()) {
			hash = 37 * hash + Objects.hashCode(idStep);
		}
		return hash;
	}

	@Override
	public boolean isEqualTo(EObject other, Map<String, String> otherImports, Map<String, String> imports) {
		if (this == other) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		final AttributeFinderStepImplCustom otherImpl = (AttributeFinderStepImplCustom) other;
		if (getIdSteps().size() != otherImpl.getIdSteps().size()) {
			return false;
		}
		ListIterator<String> left = getIdSteps().listIterator();
		ListIterator<String> right = otherImpl.getIdSteps().listIterator();
		while (left.hasNext()) {
			String lhs = left.next();
			String rhs = right.next();
			if (!Objects.equals(lhs, rhs)) {
				return false;
			}
		}
		// TODO: compare arguments
		return true;
	}

}
