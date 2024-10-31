/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.grammar.sapl.RecursiveWildcardStep;
import io.sapl.grammar.sapl.WildcardStep;
import io.sapl.grammar.sapl.impl.util.ErrorFactory;
import io.sapl.grammar.sapl.impl.util.FilterAlgorithmUtil;
import lombok.NonNull;
import reactor.core.publisher.Flux;

/**
 * Implements the application of a recursive wildcard step to a previous value,
 * e.g. {@code 'obj..*' or 'arr..[*]'}.
 * <p>
 * Grammar: {@code Step: '..' ({RecursiveWildcardStep} ('*' | '[' '*' ']' )) ;}
 */
public class RecursiveWildcardStepImplCustom extends RecursiveWildcardStepImpl {

    private static final String CANNOT_DESCENT_ON_AN_UNDEFINED_VALUE_ERROR = "Cannot descent on an undefined value.";

    @Override
    public Flux<Val> apply(@NonNull Val parentValue) {
        return Flux.just(applyToValue(parentValue).withTrace(RecursiveWildcardStep.class, true,
                Map.of(Trace.PARENT_VALUE, parentValue)));
    }

    public Val applyToValue(@NonNull Val parentValue) {
        if (parentValue.isError()) {
            return parentValue;
        }
        if (parentValue.isUndefined()) {
            return ErrorFactory.error(this, CANNOT_DESCENT_ON_AN_UNDEFINED_VALUE_ERROR);
        }
        if (!parentValue.isArray() && !parentValue.isObject()) {
            return Val.ofEmptyArray();
        }
        return Val.of(collect(parentValue.get(), Val.JSON.arrayNode()));
    }

    private ArrayNode collect(JsonNode node, ArrayNode results) {
        if (node.isArray()) {
            for (var item : node) {
                if (item.isObject() || item.isArray()) {
                    results.add(item);
                }
                collect(item, results);
            }
        } else if (node.isObject()) {
            final var iter = node.fields();
            while (iter.hasNext()) {
                final var item = iter.next().getValue();
                if (item.isObject() || item.isArray()) {
                    results.add(item);
                }
                collect(item, results);
            }
        } else {
            results.add(node);
        }
        return results;
    }

    @Override
    public Flux<Val> applyFilterStatement(@NonNull Val unfilteredValue, int stepId,
            @NonNull FilterStatement statement) {
        // This type of recursion does not translate well to filtering.
        // Basically just apply filter to top-level matches and do recursion with steps.
        // @.* is basically equivalent to @..* here.
        return FilterAlgorithmUtil.applyFilter(unfilteredValue, stepId, WildcardStepImplCustom::wildcard, statement,
                WildcardStep.class);
    }

}
