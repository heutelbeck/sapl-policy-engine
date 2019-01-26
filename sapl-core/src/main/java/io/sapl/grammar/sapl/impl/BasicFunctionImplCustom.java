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

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.Step;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

public class BasicFunctionImplCustom extends io.sapl.grammar.sapl.impl.BasicFunctionImpl {

	private static final String FUNCTION_EVALUATION = "Function evaluation error. Function '%s' cannot be evaluated.";

	private static final int HASH_PRIME_05 = 31;
	private static final int INIT_PRIME_02 = 5;

	@Override
	public Flux<JsonNode> evaluate(EvaluationContext ctx, boolean isBody, JsonNode relativeNode) {
		final String joinedSteps = String.join(".", getFsteps());
		final String fullyQualifiedName = ctx.getImports().getOrDefault(joinedSteps, joinedSteps);

		if (getArguments() != null && ! getArguments().getArgs().isEmpty()) {
			final List<Flux<JsonNode>> parameterFluxes = new ArrayList<>(getArguments().getArgs().size());
			for (Expression argument : getArguments().getArgs()) {
				parameterFluxes.add(argument.evaluate(ctx, isBody, relativeNode));
			}
			return Flux.combineLatest(parameterFluxes,
					paramNodes -> {
						final ArrayNode argumentsArray = JSON.arrayNode();
						for (Object paramNode : paramNodes) {
							argumentsArray.add((JsonNode) paramNode);
						}
						try {
							final JsonNode resultBeforeSteps = ctx.getFunctionCtx().evaluate(fullyQualifiedName, argumentsArray);
							return evaluateStepsFilterSubtemplate(resultBeforeSteps, getSteps(), ctx, isBody, relativeNode);
						} catch (FunctionException e) {
							throw Exceptions.propagate(new PolicyEvaluationException(String.format(FUNCTION_EVALUATION, fullyQualifiedName), e));
						}
					})
					.flatMap(Function.identity());
		} else {
			try {
				final ArrayNode argumentsArray = JSON.arrayNode();
				final JsonNode resultBeforeSteps = ctx.getFunctionCtx().evaluate(fullyQualifiedName, argumentsArray);
				return evaluateStepsFilterSubtemplate(resultBeforeSteps, getSteps(), ctx, isBody, relativeNode);
			} catch (FunctionException e) {
				return Flux.error(new PolicyEvaluationException(String.format(FUNCTION_EVALUATION, fullyQualifiedName), e));
			}
		}
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_02;
		hash = HASH_PRIME_05 * hash + ((getArguments() == null) ? 0 : getArguments().hash(imports));
		hash = HASH_PRIME_05 * hash + Objects.hashCode(getClass().getTypeName());
		hash = HASH_PRIME_05 * hash + ((getFilter() == null) ? 0 : getFilter().hash(imports));
		String identifier = String.join(".", getFsteps());
		if (imports != null && imports.containsKey(identifier)) {
			identifier = imports.get(identifier);
		}
		hash = HASH_PRIME_05 * hash + Objects.hashCode(identifier);
		for (Step step : getSteps()) {
			hash = HASH_PRIME_05 * hash + ((step == null) ? 0 : step.hash(imports));
		}
		hash = HASH_PRIME_05 * hash + ((getSubtemplate() == null) ? 0 : getSubtemplate().hash(imports));
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
		final BasicFunctionImplCustom otherImpl = (BasicFunctionImplCustom) other;
		if ((getArguments() == null) ? (getArguments() != otherImpl.getArguments())
				: !getArguments().isEqualTo(otherImpl.getArguments(), otherImports, imports)) {
			return false;
		}
		if ((getFilter() == null) ? (getFilter() != otherImpl.getFilter())
				: !getFilter().isEqualTo(otherImpl.getFilter(), otherImports, imports)) {
			return false;
		}
		if ((getFsteps() == null) != (otherImpl.getFsteps() == null)) {
			return false;
		}
		String lhIdentifier = String.join(".", getFsteps());
		if (imports != null && imports.containsKey(lhIdentifier)) {
			lhIdentifier = imports.get(lhIdentifier);
		}
		String rhIdentifier = String.join(".", otherImpl.getFsteps());
		if (otherImports != null && otherImports.containsKey(rhIdentifier)) {
			rhIdentifier = otherImports.get(rhIdentifier);
		}
		if (!Objects.equals(lhIdentifier, rhIdentifier)) {
			return false;
		}
		if ((getSubtemplate() == null) ? (getSubtemplate() != otherImpl.getSubtemplate())
				: !getSubtemplate().isEqualTo(otherImpl.getSubtemplate(), otherImports, imports)) {
			return false;
		}
		if (getSteps().size() != otherImpl.getSteps().size()) {
			return false;
		}
		ListIterator<Step> left = getSteps().listIterator();
		ListIterator<Step> right = otherImpl.getSteps().listIterator();
		while (left.hasNext()) {
			Step lhStep = left.next();
			Step rhStep = right.next();
			if (!lhStep.isEqualTo(rhStep, otherImports, imports)) {
				return false;
			}
		}
		return true;
	}

}
