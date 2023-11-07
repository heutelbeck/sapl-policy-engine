/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.ConditionStep;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.grammar.sapl.impl.util.FilterAlgorithmUtil;
import io.sapl.grammar.sapl.impl.util.StepAlgorithmUtil;
import lombok.NonNull;
import reactor.core.publisher.Flux;

/**
 * Implements the conditional subscript of an array (or object), written as
 * '[?(Condition)]'.
 * <p>
 * [?(Condition)] returns an array containing all array items (or attribute
 * values) for which Condition evaluates to true. Can be applied to both an
 * array (then it checks each item) and an object (then it checks each attribute
 * value). Condition must be an expression, in which relative expressions
 * starting with @ can be used.
 * <p>
 * {@literal @} evaluates to the current array item or attribute value for which
 * the condition is evaluated and can be followed by further selection steps.
 * <p>
 * As attributes have no order, the sorting of the result array of a condition
 * step applied to an object is not specified.
 * <p>
 * Example: Applied to the array [1, 2, 3, 4, 5], the selection step
 * [?({@literal @} &gt; 2)] returns the array [3, 4, 5] (containing all values
 * that are greater than 2).
 * <p>
 * Grammar: Step: ... | '[' Subscript ']' | ... Subscript returns Step: ... |
 * {ConditionStep} '?' '(' expression=Expression ')' | ...
 */
public class ConditionStepImplCustom extends ConditionStepImpl {

    @Override
    public Flux<Val> apply(@NonNull Val parentValue) {
        return StepAlgorithmUtil.apply(parentValue, expression::evaluate, Trace.CONDITION_EXPRESSION,
                ConditionStep.class);
    }

    @Override
    public Flux<Val> applyFilterStatement(@NonNull Val parentValue, int stepId, @NonNull FilterStatement statement) {
        return FilterAlgorithmUtil.applyFilter(parentValue, stepId, expression::evaluate, statement,
                ConditionStep.class);
    }

}
