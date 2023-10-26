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
package io.sapl.grammar.sapl.impl;

import java.math.BigDecimal;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.ArraySlicingStep;
import io.sapl.grammar.sapl.AttributeUnionStep;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.grammar.sapl.impl.util.FilterAlgorithmUtil;
import io.sapl.grammar.sapl.impl.util.SelectorUtil;
import io.sapl.grammar.sapl.impl.util.StepAlgorithmUtil;
import lombok.NonNull;
import reactor.core.publisher.Flux;

/**
 * Implements the application of an index union step to a previous array value,
 * e.g. {@code 'arr[4, 7, 11]'.}
 *
 * Grammar:{@code  Step: '[' Subscript ']' ;
 * 
<p>
 * Subscript returns Step: {IndexUnionStep} indices+=JSONNUMBER ','
 * indices+=JSONNUMBER (',' indices+=JSONNUMBER)* ;}
 */
public class IndexUnionStepImplCustom extends IndexUnionStepImpl {

    @Override
    public Flux<Val> apply(@NonNull Val parentValue) {
        return StepAlgorithmUtil.applyOnArray(parentValue, SelectorUtil.toArrayElementSelector(hasIndex(parentValue)),
                parameters(), AttributeUnionStep.class);
    }

    @Override
    public Flux<Val> applyFilterStatement(@NonNull Val unfilteredValue, int stepId,
            @NonNull FilterStatement statement) {
        return FilterAlgorithmUtil.applyFilterOnArray(unfilteredValue, stepId,
                SelectorUtil.toArrayElementSelector(hasIndex(unfilteredValue)), statement, ArraySlicingStep.class);
    }

    private BiPredicate<Integer, Val> hasIndex(Val parentValue) {
        return (index, v) -> {
            var arraySize = parentValue.getArrayNode().size();
            return indices.stream().map(BigDecimal::intValue).map(i -> normalizeIndex(i, arraySize))
                    .anyMatch(i -> i.equals(index));
        };
    }

    private int normalizeIndex(int i, int arraySize) {
        return i < 0 ? i + arraySize : i;
    }

    private String parameters() {
        return "[" + (indices == null ? "" : indices.stream().map(Object::toString).collect(Collectors.joining(",")))
                + "]";
    }

}
