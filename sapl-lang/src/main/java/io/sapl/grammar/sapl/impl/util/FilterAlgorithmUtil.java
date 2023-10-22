/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.sapl.impl.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.Traced;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.ConditionStep;
import io.sapl.grammar.sapl.FilterComponent;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@UtilityClass
public class FilterAlgorithmUtil {
	private static final String UNFILTERED_VALUE                      = "unfilteredValue";
	static final String         TYPE_MISMATCH_CONDITION_NOT_BOOLEAN_S = "Type mismatch. Expected the condition expression to return a Boolean, but was '%s'.";
	static final String         TYPE_MISMATCH_UNFILTERED_UNDEFINED    = "Filters cannot be applied to undefined values.";
	static final String         TYPE_MISMATCH_EACH_ON_NON_ARRAY       = "Type mismatch error. Cannot use 'each' keyword with non-array values. Value type was: ";

	public static Flux<Val> applyFilter(@NonNull Val unfilteredValue, int stepId, Supplier<Flux<Val>> selector,
			@NonNull FilterStatement statement, Class<?> operationType) {
		if (unfilteredValue.isError()) {
			return Flux.just(unfilteredValue.withParentTrace(ConditionStep.class, unfilteredValue));
		}
		if (unfilteredValue.isArray()) {
			return applyFilterOnArray(unfilteredValue, stepId, selector, statement, operationType);
		}
		if (unfilteredValue.isObject()) {
			return applyFilterOnObject(unfilteredValue, stepId, selector, statement, operationType);
		}
		return Flux.just(unfilteredValue.withTrace(ConditionStep.class, Map.of(UNFILTERED_VALUE, unfilteredValue)));
	}

	public static Flux<Val> applyFilterOnArray(Val unfilteredValue, int stepId, Supplier<Flux<Val>> selector,
			FilterStatement statement, Class<?> operationType) {
		if (!unfilteredValue.isArray()) {
			return Flux.just(unfilteredValue.withTrace(ConditionStep.class, Map.of(UNFILTERED_VALUE, unfilteredValue)));
		}
		var array = unfilteredValue.getArrayNode();
		if (array.isEmpty()) {
			return Flux.just(unfilteredValue.withTrace(operationType, Map.of(UNFILTERED_VALUE, unfilteredValue)));
		}
		var elementFluxes = new ArrayList<Flux<Val>>(array.size());
		var iter          = array.elements();
		var elementCount  = 0;
		while (iter.hasNext()) {
			var element       = iter.next();
			var elementValue  = Val.of(element).withTrace(operationType, Map.of("from", unfilteredValue));
			var index         = elementCount++;
			var conditions    = selector.get()
					.contextWrite(ctx -> AuthorizationContext.setRelativeNodeWithIndex(ctx, elementValue, index));
			var moddedElement = conditions.concatMap(
					applyFilterIfConditionMet(elementValue, unfilteredValue, stepId, statement, "[" + index + "]"));
			elementFluxes.add(moddedElement);
		}
		return Flux.combineLatest(elementFluxes, RepackageUtil::recombineArray);
	}

	public static Flux<Val> applyFilterOnObject(Val unfilteredValue, int stepId, Supplier<Flux<Val>> selector,
			FilterStatement statement, Class<?> operationType) {
		if (!unfilteredValue.isObject()) {
			return Flux.just(unfilteredValue.withTrace(ConditionStep.class, Map.of(UNFILTERED_VALUE, unfilteredValue)));
		}
		var object = unfilteredValue.getObjectNode();
		if (object.isEmpty()) {
			return Flux.just(unfilteredValue.withTrace(ConditionStep.class, Map.of(UNFILTERED_VALUE, unfilteredValue)));
		}
		var fieldFluxes = new ArrayList<Flux<Tuple2<String, Val>>>(object.size());
		var iter        = object.fields();
		while (iter.hasNext()) {
			var field          = iter.next();
			var key            = field.getKey();
			var originalValue  = Val.of(field.getValue()).withTrace(operationType, Map.of("from", unfilteredValue));
			var conditions     = selector.get()
					.contextWrite(ctx -> AuthorizationContext.setRelativeNodeWithKey(ctx, originalValue, key));
			var filteredFields = conditions
					.concatMap(applyFilterIfConditionMet(originalValue, unfilteredValue, stepId, statement, key));
			var keyValuePairs  = filteredFields.map(filteredField -> Tuples.of(key, filteredField));
			fieldFluxes.add(keyValuePairs);
		}
		return Flux.combineLatest(fieldFluxes, RepackageUtil::recombineObject);
	}

	private static Function<Val, Flux<Val>> applyFilterIfConditionMet(Val elementValue, Val unfilteredValue, int stepId,
			FilterStatement statement, String elementIdentifier) {
		return conditionResult -> {
			var trace = Map.<String, Traced>of(UNFILTERED_VALUE, unfilteredValue, "conditionResult", conditionResult,
					elementIdentifier, elementValue);
			if (conditionResult.isError()) {
				return Flux.just(conditionResult.withTrace(ConditionStep.class, trace));
			}
			if (!conditionResult.isBoolean()) {
				return Flux.just(Val.error(TYPE_MISMATCH_CONDITION_NOT_BOOLEAN_S, conditionResult)
						.withTrace(ConditionStep.class, trace));
			}
			if (conditionResult.getBoolean()) {
				var elementValueTraced = elementValue.withTrace(ConditionStep.class, trace);
				if (stepId == statement.getTarget().getSteps().size() - 1) {
					// this was the final step. apply filter
					return applyFilterFunction(elementValueTraced, statement.getArguments(), statement.getFsteps(),
							statement.isEach())
							.contextWrite(ctx -> AuthorizationContext.setRelativeNode(ctx,
									unfilteredValue.withTrace(ConditionStep.class, trace)));
				} else {
					// there are more steps. descent with them
					return statement.getTarget().getSteps().get(stepId + 1).applyFilterStatement(elementValueTraced,
							stepId + 1, statement);
				}
			} else {
				return Flux.just(elementValue.withTrace(ConditionStep.class, trace));
			}
		};
	}

	public static Flux<Val> applyFilterFunction(Val unfilteredValue, Arguments arguments, Iterable<String> fsteps,
			boolean each) {
		if (unfilteredValue.isError()) {
			return Flux.just(unfilteredValue.withTrace(FilterComponent.class, unfilteredValue));
		}
		if (unfilteredValue.isUndefined()) {
			return Flux.just(
					Val.error(TYPE_MISMATCH_UNFILTERED_UNDEFINED).withTrace(FilterComponent.class, unfilteredValue));
		}

		if (!each) {
			return FunctionUtil.combineArgumentFluxes(arguments)
					.concatMap(parameters -> FunctionUtil.evaluateFunctionWithLeftHandArgumentMono(fsteps,
							unfilteredValue, parameters))
					.map(val -> val.withTrace(FilterComponent.class,
							Map.of(UNFILTERED_VALUE, unfilteredValue, "filterResult", val)));
		}

		// "|- each" may only be applied to arrays
		if (!unfilteredValue.isArray()) {
			return Flux.just(Val.error(TYPE_MISMATCH_EACH_ON_NON_ARRAY + unfilteredValue.getValType())
					.withTrace(FilterComponent.class, unfilteredValue));
		}

		var rootArray      = (ArrayNode) unfilteredValue.get();
		var argumentFluxes = FunctionUtil.combineArgumentFluxes(arguments);
		return argumentFluxes.concatMap(parameters -> {
			var elementsEvaluations = new ArrayList<Mono<Val>>(rootArray.size());
			var index               = 0;
			for (var element : rootArray) {
				var elementVal = Val.of(element).withTrace(FilterComponent.class,
						Map.of(UNFILTERED_VALUE, unfilteredValue, "index", Val.of(index++)));
				elementsEvaluations
						.add(FunctionUtil.evaluateFunctionWithLeftHandArgumentMono(fsteps, elementVal, parameters));
			}
			return Flux.combineLatest(elementsEvaluations, e -> Arrays.copyOf(e, e.length, Val[].class))
					.map(RepackageUtil::recombineArray);
		});
	}

}
