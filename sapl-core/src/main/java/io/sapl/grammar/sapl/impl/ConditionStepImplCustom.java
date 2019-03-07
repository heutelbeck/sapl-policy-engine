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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentArray;
import io.sapl.interpreter.selection.JsonNodeWithParentObject;
import io.sapl.interpreter.selection.ResultNode;
import reactor.core.publisher.Flux;

/**
 * Implements the conditional subscript of an array (or object), written as '[?(Condition)]'.
 *
 * [?(Condition)] returns an array containing all array items (or attribute values) for which Condition evaluates to true.
 * Can be applied to both an array (then it checks each item) and an object (then it checks each attribute value).
 * Condition must be an expression, in which relative expressions starting with @ can be used.
 * @ evaluates to the current array item or attribute value for which the condition is evaluated and can be followed
 * by further selection steps.
 *
 * As attributes have no order, the sorting of the result array of a condition step applied to an object is not specified.
 *
 * Example:
 * Applied to the array [1, 2, 3, 4, 5], the selection step [?(@ > 2)] returns the array [3, 4, 5]
 * (containing all values that are greater than 2).
 *
 * Grammar:
 * Step:
 * 	... |
 * 	'[' Subscript ']' |
 * 	...
 *
 * Subscript returns Step:
 *    ... |
 *    {ConditionStep} '?' '(' expression=Expression ')' |
 *    ...
 */
public class ConditionStepImplCustom extends io.sapl.grammar.sapl.impl.ConditionStepImpl {

	private static final String UNDEFINED_CANNOT_BE_ADDED_TO_JSON_ARRAY_RESULTS = "'undefined' cannot be added to JSON array results.";

	private static final String CONDITION_ACCESS_TYPE_MISMATCH = "Type mismatch. Condition access is only possible for array or object, but got '%s'.";

	/**
	 * Applies the conditional subscript to an abstract annotated JsonNode, which can either be an array or an object.
	 *
	 * @param previousResult the array or object
	 * @param ctx the evaluation context
	 * @param isBody a flag indicating whether the expression is part of a policy body
	 * @param relativeNode the relative node (not needed here)
	 * @return a flux of ArrayResultNodes containing the elements/attribute values of the original array/object
	 *         for which the condition expression evaluates to true
	 */
	@Override
	public Flux<ResultNode> apply(AbstractAnnotatedJsonNode previousResult, EvaluationContext ctx, boolean isBody,
			Optional<JsonNode> relativeNode) {
		final Optional<JsonNode> optPreviousResultNode = previousResult.getNode();
		if (!optPreviousResultNode.isPresent()) {
			return Flux.error(new PolicyEvaluationException("undefined value during conditional step evaluation."));
		}
		final JsonNode previousResultNode = optPreviousResultNode.get();
		if (previousResultNode.isArray()) {
			return handleArrayNode(ctx, isBody, previousResultNode);
		} else if (previousResultNode.isObject()) {
			return handleObjectNode(ctx, isBody, previousResultNode);
		} else {
			return Flux.error(new PolicyEvaluationException(
					String.format(CONDITION_ACCESS_TYPE_MISMATCH, Value.typeOf(previousResult.getNode()))));
		}
	}

	private Flux<ResultNode> handleArrayNode(EvaluationContext ctx, boolean isBody, JsonNode arrayNode) {
		final List<Flux<JsonNode>> itemFluxes = new ArrayList<>(arrayNode.size());
		IntStream.range(0, arrayNode.size()).forEach(idx -> itemFluxes.add(getExpression()
				.evaluate(ctx, isBody, Optional.of(arrayNode.get(idx))).flatMap(Value::toJsonNode)));
		// the indices of the elements in the arrayNode correspond to the indices of the flux results,
		// because combineLatest() preserves the order of the given list of fluxes in the results array
		// passed to the combinator function
		return Flux.combineLatest(itemFluxes, results -> {
			final List<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
			// iterate over all condition results and collect the array elements related to a result
			// representing true
			IntStream.range(0, results.length).forEach(idx -> {
				final JsonNode result = (JsonNode) results[idx];
				if (result.isBoolean() && result.asBoolean()) {
					resultList.add(new JsonNodeWithParentArray(Optional.of(arrayNode.get(idx)),
							Optional.of(arrayNode), idx));
				}
			});
			return new ArrayResultNode(resultList);
		});
	}

	private Flux<ResultNode> handleObjectNode(EvaluationContext ctx, boolean isBody, JsonNode objectNode) {
		// create three parallel lists collecting the field names and field values of the object
		// and the fluxes providing the evaluated conditions for the field values
		final List<String> fieldNames = new ArrayList<>();
		final List<JsonNode> fieldValues = new ArrayList<>();
		final List<Flux<JsonNode>> valueFluxes = new ArrayList<>();
		final Iterator<String> it = objectNode.fieldNames();
		while (it.hasNext()) {
			final String fieldName = it.next();
			final JsonNode fieldValue = objectNode.get(fieldName);
			fieldNames.add(fieldName);
			fieldValues.add(fieldValue);
			valueFluxes.add(
					getExpression().evaluate(ctx, isBody, Optional.of(fieldValue)).flatMap(Value::toJsonNode)
			);
		}
		// the indices of the elements in the fieldNames list and the fieldValues list
		// correspond to the indices of the flux results, because combineLatest() preserves
		// the order of the given list of fluxes in the results array passed to the combinator function
		return Flux.combineLatest(valueFluxes, results -> {
			final List<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
			// iterate over all condition results and collect the field values related to a result
			// representing true
			IntStream.range(0, results.length).forEach(idx -> {
				final JsonNode result = (JsonNode) results[idx];
				if (result.isBoolean() && result.asBoolean()) {
					resultList.add(new JsonNodeWithParentObject(Optional.of(fieldValues.get(idx)),
							Optional.of(objectNode), fieldNames.get(idx)));
				}
			});
			return new ArrayResultNode(resultList);
		});
	}

	/**
	 * Applies the conditional subscript to an array.
	 *
	 * @param previousResult the array
	 * @param ctx the evaluation context
	 * @param isBody a flag indicating whether the expression is part of a policy body
	 * @param relativeNode the relative node (not needed here)
	 * @return a flux of ArrayResultNodes containing the elements of the original array for which the
	 *         condition expression evaluates to true
	 */
	@Override
	public Flux<ResultNode> apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody,
			Optional<JsonNode> relativeNode) {
		// create two parallel lists collecting the elements of the array
		// and the fluxes providing the evaluated conditions for these elements
		final List<AbstractAnnotatedJsonNode> arrayElements = new ArrayList<>(previousResult.getNodes().size());
		final List<Flux<Optional<JsonNode>>> conditionResultFluxes = new ArrayList<>(previousResult.getNodes().size());
		for (AbstractAnnotatedJsonNode arrayElement : previousResult) {
			arrayElements.add(arrayElement);
			conditionResultFluxes.add(getExpression().evaluate(ctx, isBody, arrayElement.getNode()));
		}

		// the indices of the elements in the arrayElements list correspond to the indices
		// of the flux results, because combineLatest() preserves the order of the given
		// list of fluxes in the results array passed to the combinator function
		return Flux.combineLatest(conditionResultFluxes, Function.identity()).flatMap(results -> {
			final List<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
			// iterate over all condition results and collect the array elements related to a result
			// representing true
			for (int idx = 0; idx < results.length; idx++) {
				@SuppressWarnings("unchecked")
				Optional<JsonNode> optionalResult = ((Optional<JsonNode>) results[idx]);
				if (!optionalResult.isPresent()) {
					return Flux.error(new PolicyEvaluationException(UNDEFINED_CANNOT_BE_ADDED_TO_JSON_ARRAY_RESULTS));
				}
				final JsonNode result = optionalResult.get();
				if (result.isBoolean() && result.asBoolean()) {
					resultList.add(arrayElements.get(idx));
				}
			}
			return Flux.just(new ArrayResultNode(resultList));
		});
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = 17;
		hash = 37 * hash + Objects.hashCode(getClass().getTypeName());
		hash = 37 * hash + ((getExpression() == null) ? 0 : getExpression().hash(imports));
		return hash;
	}

	@Override
	public boolean isEqualTo(EObject other, Map<String, String> otherImports, Map<String, String> imports) {
		if (this == other)
			return true;
		if (other == null || getClass() != other.getClass())
			return false;
		final ConditionStepImplCustom otherImpl = (ConditionStepImplCustom) other;
		return (getExpression() == null) ? (getExpression() == otherImpl.getExpression())
				: getExpression().isEqualTo(otherImpl.getExpression(), otherImports, imports);
	}

}
