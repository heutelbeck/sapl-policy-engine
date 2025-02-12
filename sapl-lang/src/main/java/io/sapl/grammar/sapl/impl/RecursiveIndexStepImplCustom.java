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
package io.sapl.grammar.sapl.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.grammar.sapl.RecursiveIndexStep;
import io.sapl.grammar.sapl.impl.util.FilterAlgorithmUtil;
import io.sapl.grammar.sapl.impl.util.RepackageUtil;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.NonNull;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * Implements the application of a recursive index step to a previous array
 * value, e.g. {@code 'arr..[2]'}.
 * <p>
 * Grammar: {@code Step: '..' ({RecursiveIndexStep} '[' index=JSONNUMBER ']') ;}
 */
public class RecursiveIndexStepImplCustom extends RecursiveIndexStepImpl {

    @Override
    public Flux<Val> apply(@NonNull Val parentValue) {
        return Flux.just(applyToValue(parentValue).withTrace(RecursiveIndexStep.class, true,
                Map.of(Trace.PARENT_VALUE, parentValue, Trace.INDEX, Val.of(index.intValue()))));
    }

    public Val applyToValue(@NonNull Val parentValue) {
        if (parentValue.isError()) {
            return parentValue.withParentTrace(RecursiveIndexStep.class, true, parentValue);
        }
        if (parentValue.isUndefined()) {
            return Val.ofEmptyArray();
        }
        return Val.of(collect(index.intValue(), parentValue.get(), Val.JSON.arrayNode()));
    }

    private ArrayNode collect(int index, JsonNode node, ArrayNode results) {
        if (node.isArray()) {
            final var idx = normalizeIndex(index, node.size());
            if (node.has(idx)) {
                results.add(node.get(idx));
            }
            for (var item : node) {
                collect(index, item, results);
            }
        } else if (node.isObject()) {
            final var iter = node.fields();
            while (iter.hasNext()) {
                final var item = iter.next().getValue();
                collect(index, item, results);
            }
        }
        return results;
    }

    private static int normalizeIndex(int idx, int size) {
        return idx < 0 ? size + idx : idx;
    }

    @Override
    public Flux<Val> applyFilterStatement(@NonNull Val parentValue, int stepId, @NonNull FilterStatement statement) {
        return doApplyFilterStatement(index.intValue(), parentValue, stepId, statement);
    }

    private static Flux<Val> doApplyFilterStatement(int index, Val parentValue, int stepId, FilterStatement statement) {
        if (parentValue.isObject()) {
            return applyFilterStatementToObject(index, parentValue, stepId, statement);
        }

        if (!parentValue.isArray()) {
            // this means the element does not get selected does not get filtered
            return Flux.just(parentValue.withTrace(RecursiveIndexStep.class, true,
                    Map.of(Trace.PARENT_VALUE, parentValue, Trace.INDEX, Val.of(index))));
        }
        final var array         = parentValue.getArrayNode();
        final var idx           = normalizeIndex(index, array.size());
        final var elementFluxes = new ArrayList<Flux<Val>>(array.size());
        for (var i = 0; i < array.size(); i++) {
            final var element = Val.of(array.get(i)).withTrace(RecursiveIndexStep.class, true,
                    Map.of(Trace.PARENT_VALUE, parentValue, Trace.INDEX, Val.of(index)));
            if (i == idx) {
                if (stepId == statement.getTarget().getSteps().size() - 1) {
                    // this was the final step. apply filter
                    elementFluxes.add(FilterAlgorithmUtil
                            .applyFilterFunction(element, statement.getArguments(), statement.getIdentifier(),
                                    statement.isEach(), statement)
                            .contextWrite(ctx -> AuthorizationContext.setRelativeNode(ctx, parentValue))
                            .map(filteredValue -> {
                                final var trace = new HashMap<String, Val>();
                                trace.put(Trace.UNFILTERED_VALUE, element);
                                trace.put(Trace.FILTERED, filteredValue);
                                return filteredValue.withTrace(RecursiveIndexStep.class, true, trace);
                            }));
                } else {
                    // there are more steps. descent with them
                    elementFluxes.add(statement.getTarget().getSteps().get(stepId + 1).applyFilterStatement(element,
                            stepId + 1, statement));
                }
            } else {
                elementFluxes.add(doApplyFilterStatement(index, element, stepId, statement));
            }
        }
        return Flux.combineLatest(elementFluxes, RepackageUtil::recombineArray);
    }

    private static Flux<Val> applyFilterStatementToObject(int idx, Val parentValue, int stepId,
            FilterStatement statement) {
        final var object      = parentValue.getObjectNode();
        final var fieldFluxes = new ArrayList<Flux<Tuple2<String, Val>>>(object.size());
        final var fields      = object.fields();
        while (fields.hasNext()) {
            final var field      = fields.next();
            final var key        = field.getKey();
            final var value      = field.getValue();
            final var fieldValue = Val.of(value).withTrace(RecursiveIndexStep.class, true,
                    Map.of(Trace.PARENT_VALUE, parentValue, Trace.INDEX, Val.of(idx), Trace.KEY, Val.of(key)));
            fieldFluxes.add(doApplyFilterStatement(idx, fieldValue, stepId, statement).map(val -> Tuples.of(key, val)));
        }
        return Flux.combineLatest(fieldFluxes, RepackageUtil::recombineObject);
    }

}
