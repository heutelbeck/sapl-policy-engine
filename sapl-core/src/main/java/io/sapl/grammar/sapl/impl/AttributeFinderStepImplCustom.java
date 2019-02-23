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
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

public class AttributeFinderStepImplCustom extends io.sapl.grammar.sapl.impl.AttributeFinderStepImpl {

	private static final String EXTERNAL_ATTRIBUTE_IN_TARGET = "Attribute resolution error. Attribute '%s' is not allowed in target.";
	private static final String ATTRIBUTE_RESOLUTION = "Attribute resolution error. Attribute '%s' cannot be resolved.";
	private static final String UNDEFINED_VALUE = "Undefined value handed over as parameter to policy information point";

	private static final int HASH_PRIME_03 = 23;
	private static final int INIT_PRIME_01 = 3;

	private String getFullyQualifiedName(EvaluationContext ctx) {
		String fullyQualifiedName = String.join(".", getIdSteps());
		if (ctx.getImports().containsKey(fullyQualifiedName)) {
			fullyQualifiedName = ctx.getImports().get(fullyQualifiedName);
		}
		return fullyQualifiedName;
	}

	@Override
	public Flux<ResultNode> apply(AbstractAnnotatedJsonNode previousResult, EvaluationContext ctx, boolean isBody,
			Optional<JsonNode> relativeNode) {
		return applyToJson(previousResult.asJsonWithoutAnnotations(), ctx, isBody);
	}

	@Override
	public Flux<ResultNode> apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody,
			Optional<JsonNode> relativeNode) {
		return applyToJson(previousResult.asJsonWithoutAnnotations(), ctx, isBody);
	}

	private Flux<ResultNode> applyToJson(Optional<JsonNode> previousResult, EvaluationContext ctx, boolean isBody) {
		final String fullyQualifiedName = getFullyQualifiedName(ctx);
		if (!isBody) {
			return Flux.error(
					new PolicyEvaluationException(String.format(EXTERNAL_ATTRIBUTE_IN_TARGET, fullyQualifiedName)));
		}

		final Map<String, JsonNode> variables = ctx.getVariableCtx().getVariables();
		final Flux<JsonNode> jsonNodeFlux = ctx.getAttributeCtx()
				.evaluate(fullyQualifiedName,
						previousResult.orElseThrow(
								() -> Exceptions.propagate(new PolicyEvaluationException(UNDEFINED_VALUE))),
						variables)
				.onErrorResume(error -> Flux.error(
						new PolicyEvaluationException(String.format(ATTRIBUTE_RESOLUTION, fullyQualifiedName), error)));
		return jsonNodeFlux.map(Optional::of).map(JsonNodeWithoutParent::new);
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_01;
		hash = HASH_PRIME_03 * hash + Objects.hashCode(getClass().getTypeName());
		for (String idStep : getIdSteps()) {
			hash = HASH_PRIME_03 * hash + Objects.hashCode(idStep);
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
		return true;
	}

}
