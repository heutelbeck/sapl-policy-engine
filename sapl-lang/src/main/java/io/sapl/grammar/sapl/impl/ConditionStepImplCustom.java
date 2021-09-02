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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.interpreter.EvaluationContext;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * Implements the conditional subscript of an array (or object), written as
 * '[?(Condition)]'.
 *
 * [?(Condition)] returns an array containing all array items (or attribute
 * values) for which Condition evaluates to true. Can be applied to both an
 * array (then it checks each item) and an object (then it checks each attribute
 * value). Condition must be an expression, in which relative expressions
 * starting with @ can be used.
 *
 * {@literal @} evaluates to the current array item or attribute value for which
 * the condition is evaluated and can be followed by further selection steps.
 *
 * As attributes have no order, the sorting of the result array of a condition
 * step applied to an object is not specified.
 *
 * Example: Applied to the array [1, 2, 3, 4, 5], the selection step
 * [?({@literal @} &gt; 2)] returns the array [3, 4, 5] (containing all values
 * that are greater than 2).
 *
 * Grammar: Step: ... | '[' Subscript ']' | ... Subscript returns Step: ... |
 * {ConditionStep} '?' '(' expression=Expression ')' | ...
 */
@Slf4j
public class ConditionStepImplCustom extends ConditionStepImpl {

	private static final String CONDITION_ACCESS_TYPE_MISMATCH = "Type mismatch. Condition access is only possible for array or object, but got '%s'.";

	@Override
	public Flux<Val> apply(@NonNull Val parentValue, @NonNull EvaluationContext ctx, @NonNull Val relativeNode) {
		if (parentValue.isError()) {
			return Flux.just(parentValue);
		}
		if (parentValue.isArray()) {
			return applyToArray(parentValue.getArrayNode(), ctx);
		}
		if (parentValue.isObject()) {
			return applyToObject(parentValue.getObjectNode(), ctx);
		}
		return Val.errorFlux(CONDITION_ACCESS_TYPE_MISMATCH, parentValue);
	}

	private Flux<Val> applyToObject(ObjectNode object, EvaluationContext ctx) {
		// handle the empty object
		if (object.isEmpty()) {
			return Flux.just(Val.ofEmptyArray());
		}
		// collect the fluxes providing the evaluated conditions for the array elements
		final List<Flux<Tuple2<JsonNode, Val>>> itemFluxes = new ArrayList<>(object.size());
		var iter = object.fields();
		while (iter.hasNext()) {
			var field = iter.next();
			itemFluxes.add(getExpression().evaluate(ctx, Val.of(field.getValue())).map(expressionResult -> Tuples.of(field.getValue(), expressionResult)));
		}
		return packageResultsInArray(itemFluxes);
	}

	private Flux<Val> applyToArray(ArrayNode arrayNode, EvaluationContext ctx) {
		// handle the empty array
		if (arrayNode.isEmpty()) {
			return Flux.just(Val.ofEmptyArray());
		}
		// collect the fluxes providing the evaluated conditions for the array elements
		final List<Flux<Tuple2<JsonNode, Val>>> itemFluxes = new ArrayList<>(arrayNode.size());
		for (var value : arrayNode) {
			itemFluxes.add(getExpression().evaluate(ctx, Val.of(value)).map(expressionResult -> Tuples.of(value, expressionResult)));
		}
		return packageResultsInArray(itemFluxes);
	}

	private Flux<Val> packageResultsInArray(Iterable<Flux<Tuple2<JsonNode, Val>>> itemFluxes) {
		return Flux.combineLatest(itemFluxes, Function.identity()).map(itemResults -> {
			var resultArray = Val.JSON.arrayNode();
			for (var itemResultObject : itemResults) {
				@SuppressWarnings("unchecked")
				var itemResult = (Tuple2<JsonNode, Val>) itemResultObject;
				var conditionResult = itemResult.getT2();
				if (conditionResult.isError()) {
					return conditionResult;
				}
				if (conditionResult.isBoolean() && conditionResult.getBoolean()) {
					resultArray.add(itemResult.getT1());
				}
			}
			return Val.of(resultArray);
		});
	}

	@Override
	public Flux<Val> applyFilterStatement(@NonNull Val parentValue, @NonNull EvaluationContext ctx,
			@NonNull Val relativeNode, int stepId, @NonNull FilterStatement statement) {
		if (!parentValue.isObject() && !parentValue.isArray()) {
			return Flux.just(parentValue);
		}
		if (parentValue.isArray()) {
			return applyFilterStatementToArray(parentValue.getArrayNode(), ctx, relativeNode, stepId, statement);
		}
		return applyFilterStatementToObject(parentValue.getObjectNode(), ctx, relativeNode, stepId, statement);
	}

	private Flux<Val> applyFilterStatementToObject(ObjectNode object, EvaluationContext ctx, Val relativeNode,
			int stepId, FilterStatement statement) {
		// handle the empty object
		if (object.isEmpty()) {
			return Flux.just(Val.ofEmptyObject());
		}
		// collect the fluxes providing the evaluated conditions for the array elements
		final List<Flux<Tuple2<String, Val>>> fieldFluxes = new ArrayList<>(object.size());
		var iter = object.fields();
		while (iter.hasNext()) {
			var field = iter.next();
			log.trace("inspect field {}", field);
			fieldFluxes.add(getExpression().evaluate(ctx, Val.of(field.getValue())).concatMap(expressionResult -> {
				if (expressionResult.isError()) {
					return Flux.just(expressionResult);
				}
				if (!expressionResult.isBoolean()) {
					return Val.errorFlux("Type mismatch. Condition did not evaluate to boolean. Was: {}",
							expressionResult);
				}
				if (expressionResult.getBoolean()) {
					log.trace("field matches due to fulfilled condition");
					if (stepId == statement.getTarget().getSteps().size() - 1) {
						// this was the final step. apply filter
						log.trace("final step. select and filter!");
						return FilterComponentImplCustom.applyFilterFunction(Val.of(field.getValue()),
								statement.getArguments(),
								FunctionUtil.resolveAbsoluteFunctionName(statement.getFsteps(), ctx), ctx,
								Val.of(object), statement.isEach());
					} else {
						// there are more steps. descent with them
						log.trace("this step was successful. descent with next step...");
						return statement.getTarget().getSteps().get(stepId + 1).applyFilterStatement(
								Val.of(field.getValue()), ctx, relativeNode, stepId + 1, statement);
					}
				} else {
					return Flux.just(Val.of(field.getValue()));
				}
			}).map(expressionResult -> Tuples.of(field.getKey(), expressionResult)));
		}
		return Flux.combineLatest(fieldFluxes, RepackageUtil::recombineObject);
	}

	private Flux<Val> applyFilterStatementToArray(ArrayNode array, EvaluationContext ctx, Val relativeNode, int stepId,
			FilterStatement statement) {
		// handle the empty object
		if (array.isEmpty()) {
			return Flux.just(Val.ofEmptyArray());
		}
		// collect the fluxes providing the evaluated conditions for the array elements
		final List<Flux<Val>> elementFluxes = new ArrayList<>(array.size());
		var iter = array.elements();
		while (iter.hasNext()) {
			var element = iter.next();
			log.trace("inspect element {}", element);
			elementFluxes.add(getExpression().evaluate(ctx, Val.of(element)).concatMap(expressionResult -> {
				if (expressionResult.isError()) {
					return Flux.just(expressionResult);
				}
				if (!expressionResult.isBoolean()) {
					return Val.errorFlux("Type mismatch. Condition did not evaluate to boolean. Was: {}",
							expressionResult);
				}
				if (expressionResult.getBoolean()) {
					log.trace("element matches due to fulfilled condition");
					if (stepId == statement.getTarget().getSteps().size() - 1) {
						// this was the final step. apply filter
						log.trace("final step. select and filter!");
						return FilterComponentImplCustom.applyFilterFunction(Val.of(element), statement.getArguments(),
								FunctionUtil.resolveAbsoluteFunctionName(statement.getFsteps(), ctx), ctx,
								Val.of(array), statement.isEach());
					} else {
						// there are more steps. descent with them
						log.trace("this step was successful. descent with next step...");
						return statement.getTarget().getSteps().get(stepId + 1).applyFilterStatement(Val.of(element),
								ctx, relativeNode, stepId + 1, statement);
					}
				} else {
					return Flux.just(Val.of(element));
				}
			}));
		}
		return Flux.combineLatest(elementFluxes, RepackageUtil::recombineArray);
	}

}
