/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.ConditionStep;
import io.sapl.grammar.sapl.FilterComponent;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.grammar.sapl.FunctionIdentifier;
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
            return Flux.just(unfilteredValue.withParentTrace(ConditionStep.class, true, unfilteredValue));
        }
        if (unfilteredValue.isArray()) {
            return applyFilterOnArray(unfilteredValue, stepId, selector, statement, operationType);
        }
        if (unfilteredValue.isObject()) {
            return applyFilterOnObject(unfilteredValue, stepId, selector, statement, operationType);
        }
        return Flux
                .just(unfilteredValue.withTrace(ConditionStep.class, true, Map.of(UNFILTERED_VALUE, unfilteredValue)));
    }

    public static Flux<Val> applyFilterOnArray(Val unfilteredValue, int stepId, Supplier<Flux<Val>> selector,
            FilterStatement statement, Class<?> operationType) {
        if (!unfilteredValue.isArray()) {
            return Flux.just(
                    unfilteredValue.withTrace(ConditionStep.class, true, Map.of(UNFILTERED_VALUE, unfilteredValue)));
        }
        final var array = unfilteredValue.getArrayNode();
        if (array.isEmpty()) {
            return Flux.just(unfilteredValue.withTrace(operationType, true, Map.of(UNFILTERED_VALUE, unfilteredValue)));
        }
        final var elementFluxes = new ArrayList<Flux<Val>>(array.size());
        final var iter          = array.elements();
        var       elementCount  = 0;
        while (iter.hasNext()) {
            final var element       = iter.next();
            final var elementValue  = Val.of(element).withTrace(operationType, true, Map.of("from", unfilteredValue));
            final var index         = elementCount++;
            final var conditions    = selector.get()
                    .contextWrite(ctx -> AuthorizationContext.setRelativeNodeWithIndex(ctx, elementValue, index));
            final var moddedElement = conditions.concatMap(
                    applyFilterIfConditionMet(elementValue, unfilteredValue, stepId, statement, "[" + index + "]"));
            elementFluxes.add(moddedElement);
        }
        return Flux.combineLatest(elementFluxes, RepackageUtil::recombineArray);
    }

    public static Flux<Val> applyFilterOnObject(Val unfilteredValue, int stepId, Supplier<Flux<Val>> selector,
            FilterStatement statement, Class<?> operationType) {
        if (!unfilteredValue.isObject()) {
            return Flux.just(
                    unfilteredValue.withTrace(ConditionStep.class, true, Map.of(UNFILTERED_VALUE, unfilteredValue)));
        }
        final var object = unfilteredValue.getObjectNode();
        if (object.isEmpty()) {
            return Flux.just(
                    unfilteredValue.withTrace(ConditionStep.class, true, Map.of(UNFILTERED_VALUE, unfilteredValue)));
        }
        final var fieldFluxes = new ArrayList<Flux<Tuple2<String, Val>>>(object.size());
        final var iter        = object.fields();
        while (iter.hasNext()) {
            final var field          = iter.next();
            final var key            = field.getKey();
            final var originalValue  = Val.of(field.getValue()).withTrace(operationType, true,
                    Map.of("from", unfilteredValue));
            final var conditions     = selector.get()
                    .contextWrite(ctx -> AuthorizationContext.setRelativeNodeWithKey(ctx, originalValue, key));
            final var filteredFields = conditions
                    .concatMap(applyFilterIfConditionMet(originalValue, unfilteredValue, stepId, statement, key));
            final var keyValuePairs  = filteredFields.map(filteredField -> Tuples.of(key, filteredField));
            fieldFluxes.add(keyValuePairs);
        }
        return Flux.combineLatest(fieldFluxes, RepackageUtil::recombineObject);
    }

    private static Function<Val, Flux<Val>> applyFilterIfConditionMet(Val elementValue, Val unfilteredValue, int stepId,
            FilterStatement statement, String elementIdentifier) {
        return conditionResult -> {
            final var trace = Map.<String, Val>of(UNFILTERED_VALUE, unfilteredValue, "conditionResult", conditionResult,
                    elementIdentifier, elementValue);
            if (conditionResult.isError()) {
                return Flux.just(conditionResult.withTrace(ConditionStep.class, true, trace));
            }
            if (!conditionResult.isBoolean()) {
                return Flux.just(ErrorFactory.error(statement, TYPE_MISMATCH_CONDITION_NOT_BOOLEAN_S, conditionResult)
                        .withTrace(ConditionStep.class, true, trace));
            }
            if (conditionResult.getBoolean()) {
                final var elementValueTraced = elementValue.withTrace(ConditionStep.class, true, trace);
                if (stepId == statement.getTarget().getSteps().size() - 1) {
                    // this was the final step. apply filter
                    return applyFilterFunction(elementValueTraced, statement.getArguments(), statement.getIdentifier(),
                            statement.isEach(), statement)
                            .contextWrite(ctx -> AuthorizationContext.setRelativeNode(ctx,
                                    unfilteredValue.withTrace(ConditionStep.class, true, trace)));
                } else {
                    // there are more steps. descent with them
                    return statement.getTarget().getSteps().get(stepId + 1).applyFilterStatement(elementValueTraced,
                            stepId + 1, statement);
                }
            } else {
                return Flux.just(elementValue.withTrace(ConditionStep.class, true, trace));
            }
        };
    }

    public static Flux<Val> applyFilterFunction(Val unfilteredValue, Arguments arguments, FunctionIdentifier identifier,
            boolean each, EObject location) {
        if (unfilteredValue.isError()) {
            return Flux.just(unfilteredValue.withTrace(FilterComponent.class, true, unfilteredValue));
        }
        if (unfilteredValue.isUndefined()) {
            return Flux.just(ErrorFactory.error(location, TYPE_MISMATCH_UNFILTERED_UNDEFINED)
                    .withTrace(FilterComponent.class, true, unfilteredValue));
        }

        if (!each) {
            return FunctionUtil.combineArgumentFluxes(arguments)
                    .concatMap(parameters -> FunctionUtil.evaluateFunctionWithLeftHandArgumentMono(location, identifier,
                            unfilteredValue, parameters))
                    .map(val -> val.withTrace(FilterComponent.class, true,
                            Map.of(UNFILTERED_VALUE, unfilteredValue, "filterResult", val)));
        }

        // "|- each" may only be applied to arrays
        if (!unfilteredValue.isArray()) {
            return Flux
                    .just(ErrorFactory.error(location, TYPE_MISMATCH_EACH_ON_NON_ARRAY + unfilteredValue.getValType())
                            .withTrace(FilterComponent.class, true, unfilteredValue));
        }

        final var rootArray      = (ArrayNode) unfilteredValue.get();
        final var argumentFluxes = FunctionUtil.combineArgumentFluxes(arguments);
        return argumentFluxes.concatMap(parameters -> {
            final var elementsEvaluations = new ArrayList<Mono<Val>>(rootArray.size());
            var       index               = 0;
            for (var element : rootArray) {
                final var elementVal = Val.of(element).withTrace(FilterComponent.class, true,
                        Map.of(UNFILTERED_VALUE, unfilteredValue, "index", Val.of(index++)));
                elementsEvaluations.add(FunctionUtil.evaluateFunctionWithLeftHandArgumentMono(location, identifier,
                        elementVal, parameters));
            }
            return Flux.combineLatest(elementsEvaluations, e -> Arrays.copyOf(e, e.length, Val[].class))
                    .map(RepackageUtil::recombineArray);
        });
    }

}
