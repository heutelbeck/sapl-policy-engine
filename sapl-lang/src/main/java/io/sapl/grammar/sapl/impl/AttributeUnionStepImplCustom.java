/*
 * Streaming Attribute Policy Language (SAPL) Engine
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
 * Implements the application of an attribute union step to a previous object
 * value, e.g. 'person["firstName", "lastName"]'.
 * <p>
 * Grammar: Step: '[' Subscript ']' ;
 * <p>
 * Subscript returns Step: {AttributeUnionStep} attributes+=STRING ','
 * attributes+=STRING (',' attributes+=STRING)* ;
 */
public class AttributeUnionStepImplCustom extends AttributeUnionStepImpl {

    @Override
    public Flux<Val> apply(@NonNull Val parentValue) {
        return StepAlgorithmUtil.applyOnObject(parentValue, SelectorUtil.toObjectFieldSelector(this::hasKey),
                parameters(), AttributeUnionStep.class);
    }

    @Override
    public Flux<Val> applyFilterStatement(@NonNull Val unfilteredValue, int stepId,
            @NonNull FilterStatement statement) {
        return FilterAlgorithmUtil.applyFilterOnObject(unfilteredValue, stepId,
                SelectorUtil.toObjectFieldSelector(this::hasKey), statement, ArraySlicingStep.class);
    }

    private boolean hasKey(String key, Val value) {
        return attributes.contains(key);
    }

    private String parameters() {
        return "[" + (attributes == null ? "" : String.join(",", attributes)) + "]";
    }

}
