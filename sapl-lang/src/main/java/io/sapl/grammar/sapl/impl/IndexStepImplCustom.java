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

import com.fasterxml.jackson.core.TreeNode;
import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.grammar.sapl.IndexStep;
import io.sapl.grammar.sapl.impl.util.ErrorFactory;
import io.sapl.grammar.sapl.impl.util.FilterAlgorithmUtil;
import io.sapl.grammar.sapl.impl.util.RepackageUtil;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.NonNull;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;

/**
 * Implements the application of an index step to a previous array value, e.g.
 * 'arr[2]'.
 * <p>
 * Grammar: {@code Step: '[' Subscript ']' ;
 *
<p>
 * Subscript returns Step: {IndexStep} index=JSONNUMBER ;}
 */
public class IndexStepImplCustom extends IndexStepImpl {

    private static final String TYPE_MISMATCH_S_ERROR         = "Type mismatch. The [index] access operator can only be applied to arrays. However, the policy actually attempted to apply the operator to: %s";
    private static final String INDEX_OUT_OF_BOUNDS_D_D_ERROR = "Index out of bounds. Index must be between 0 and %d, was: %d";

    @Override
    public Flux<Val> apply(@NonNull Val parentValue) {
        return Flux.just(applyToValue(parentValue).withTrace(IndexStep.class, true,
                Map.of(Trace.PARENT_VALUE, parentValue, Trace.INDEX, Val.of(index))));
    }

    public Val applyToValue(@NonNull Val parentValue) {
        if (parentValue.isError()) {
            return parentValue;
        }
        if (!parentValue.isArray()) {
            return ErrorFactory.error(this, TYPE_MISMATCH_S_ERROR, parentValue);
        }
        final var array = parentValue.getArrayNode();
        final var idx   = normalizeIndex(index, array);
        if (idx < 0 || idx >= array.size()) {
            return ErrorFactory.error(this, INDEX_OUT_OF_BOUNDS_D_D_ERROR, array.size(), idx);
        }
        return Val.of(array.get(idx));
    }

    private static int normalizeIndex(BigDecimal index, TreeNode array) {
        // handle negative index values
        final var idx = index.intValue();
        return idx < 0 ? array.size() + idx : idx;
    }

    @Override
    public Flux<Val> applyFilterStatement(@NonNull Val parentValue, int stepId, @NonNull FilterStatement statement) {
        return doApplyFilterStatement(index, parentValue, stepId, statement);
    }

    public static Flux<Val> doApplyFilterStatement(BigDecimal index, Val parentValue, int stepId,
            FilterStatement statement) {
        if (!parentValue.isArray()) {
            // this means the element does not get selected does not get filtered
            return Flux.just(parentValue.withTrace(IndexStep.class, true,
                    Map.of(Trace.PARENT_VALUE, parentValue, Trace.INDEX, Val.of(index))));
        }
        final var array = parentValue.getArrayNode();
        final var idx   = normalizeIndex(index, array);
        if (idx < 0 || idx >= array.size()) {
            // this means the element does not get selected does not get filtered
            return Flux.just(parentValue.withTrace(IndexStep.class, true,
                    Map.of(Trace.PARENT_VALUE, parentValue, Trace.INDEX, Val.of(index))));
        }
        final var elementFluxes = new ArrayList<Flux<Val>>(array.size());
        for (var i = 0; i < array.size(); i++) {
            final var element = Val.of(array.get(i)).withTrace(IndexStep.class, true, Map.of(Trace.PARENT_VALUE,
                    parentValue, Trace.ELEMENT_INDEX, Val.of(i), Trace.SELECTED_INDEX, Val.of(index)));
            if (i == idx) {
                if (stepId == statement.getTarget().getSteps().size() - 1) {
                    // this was the final step. apply filter
                    elementFluxes.add(FilterAlgorithmUtil
                            .applyFilterFunction(element, statement.getArguments(), statement.getIdentifier(),
                                    statement.isEach(), statement)
                            .contextWrite(ctx -> AuthorizationContext.setRelativeNode(ctx, parentValue)));
                } else {
                    // there are more steps. descent with them
                    elementFluxes.add(statement.getTarget().getSteps().get(stepId + 1).applyFilterStatement(element,
                            stepId + 1, statement));
                }
            } else {
                elementFluxes.add(Flux.just(element));
            }
        }
        return Flux.combineLatest(elementFluxes, RepackageUtil::recombineArray);
    }

}
