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
package io.sapl.grammar.sapl.impl;

import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithoutParent;
import io.sapl.interpreter.selection.ResultNode;
import reactor.core.publisher.Flux;

/**
 * Implements the application of an attribute finder step to a previous value.
 *
 * Grammar:
 * Step:
 * 	'.' ({AttributeFinderStep} '<' idSteps+=ID ('.' idSteps+=ID)* '>') ;
 */
public class AttributeFinderStepImplCustom extends AttributeFinderStepImpl {

	private static final String EXTERNAL_ATTRIBUTE_IN_TARGET = "Attribute resolution error. Attribute '%s' is not allowed in target.";

	private static final String ATTRIBUTE_RESOLUTION = "Attribute resolution error. Attribute '%s' cannot be resolved.";

	private static final String UNDEFINED_VALUE = "Undefined value handed over as parameter to policy information point";

	private String getFullyQualifiedName(EvaluationContext ctx) {
		String fullyQualifiedName = String.join(".", getIdSteps());
		if (ctx.getImports().containsKey(fullyQualifiedName)) {
			fullyQualifiedName = ctx.getImports().get(fullyQualifiedName);
		}
		return fullyQualifiedName;
	}

	@Override
	public Flux<ResultNode> apply(AbstractAnnotatedJsonNode value, EvaluationContext ctx,
			boolean isBody, Optional<JsonNode> relativeNode) {
		return retrieveAttribute(value.asJsonWithoutAnnotations(), ctx, isBody);
	}

	@Override
	public Flux<ResultNode> apply(ArrayResultNode arrayValue, EvaluationContext ctx,
			boolean isBody, Optional<JsonNode> relativeNode) {
		return retrieveAttribute(arrayValue.asJsonWithoutAnnotations(), ctx, isBody);
	}

	private Flux<ResultNode> retrieveAttribute(Optional<JsonNode> value,
			EvaluationContext ctx, boolean isBody) {
		final String fullyQualifiedName = getFullyQualifiedName(ctx);
		if (!isBody) {
			return Flux.error(new PolicyEvaluationException(
					String.format(EXTERNAL_ATTRIBUTE_IN_TARGET, fullyQualifiedName)));
		}
		if (!value.isPresent()) {
			return Flux.error(new PolicyEvaluationException(UNDEFINED_VALUE));
		}
		final Map<String, JsonNode> variables = ctx.getVariableCtx().getVariables();
		final Flux<JsonNode> jsonNodeFlux = ctx.getAttributeCtx()
				.evaluate(fullyQualifiedName, value.get(), variables)
				.onErrorResume(error -> Flux.error(new PolicyEvaluationException(
						String.format(ATTRIBUTE_RESOLUTION, fullyQualifiedName), error)));
		return jsonNodeFlux.map(Optional::of).map(JsonNodeWithoutParent::new);
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
	public boolean isEqualTo(EObject other, Map<String, String> otherImports,
			Map<String, String> imports) {
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
		return true;
	}

}
