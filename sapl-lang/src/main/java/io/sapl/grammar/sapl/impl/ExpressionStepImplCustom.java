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

import java.util.Map;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.ExpressionStep;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.grammar.sapl.impl.util.ErrorFactory;
import lombok.NonNull;
import reactor.core.publisher.Flux;

/**
 * Implements the expression subscript of an array (or object), written as
 * '[(Expression)]'.
 * <p>
 * Returns the value of an attribute with a key or an array item with an index
 * specified by an expression. Expression must evaluate to a string or a number.
 * If Expression evaluates to a string, the selection can only be applied to an
 * object. If Expression evaluates to a number, the selection can only be
 * applied to an array.
 * <p>
 * Example: The expression step can be used to refer to custom variables
 * (object.array[(anIndex+2)]) or apply custom functions
 * (object.array[(max_value(object.array))]).
 * <p>
 * Grammar: Step: ... | '[' Subscript ']' | ... Subscript returns Step: ... |
 * {ExpressionStep} '(' expression=Expression ')' | ...
 */
public class ExpressionStepImplCustom extends ExpressionStepImpl {

    private static final String OBJECT_ACCESS_TYPE_MISMATCH_EXPECT_A_STRING_WAS_S_ERROR         = "Object access type mismatch. Expect a string, was: %s ";
    private static final String INDEX_OUT_OF_BOUNDS_INDEX_MUST_BE_BETWEEN_0_AND_D_WAS_D_ERROR   = "Index out of bounds. Index must be between 0 and %d, was: %d";
    private static final String ARRAY_ACCESS_TYPE_MISMATCH_EXPECT_AN_INTEGER_WAS_S_ERROR        = "Array access type mismatch. Expect an integer, was: %s ";
    private static final String EXPRESSIONS_STEP_ONLY_APPLICABLE_TO_ARRAY_OR_OBJECT_WAS_S_ERROR = "Expressions step only applicable to Array or Object. was: %s";

    @Override
    public Flux<Val> apply(@NonNull Val parentValue) {
        if (parentValue.isError()) {
            return Flux.just(parentValue.withParentTrace(ExpressionStep.class, false, parentValue));
        }
        if (parentValue.isArray()) {
            return expression.evaluate().map(index -> extractValueAt(parentValue, index));
        }
        if (parentValue.isObject()) {
            return expression.evaluate().map(index -> extractKey(parentValue, index));
        }
        return Flux.just(
                ErrorFactory.error(this, EXPRESSIONS_STEP_ONLY_APPLICABLE_TO_ARRAY_OR_OBJECT_WAS_S_ERROR, parentValue)
                        .withParentTrace(ExpressionStep.class, false, parentValue));
    }

    @Override
    public Flux<Val> applyFilterStatement(@NonNull Val parentValue, int stepId, @NonNull FilterStatement statement) {
        if (!parentValue.isArray() && !parentValue.isObject()) {
            // this means the element does not get selected does not get filtered
            return Flux.just(parentValue.withParentTrace(ExpressionStep.class, true, parentValue));
        }
        return expression.evaluate().concatMap(key -> applyFilterStatement(key, parentValue, stepId, statement));
    }

    private Flux<Val> applyFilterStatement(Val key, Val parentValue, int stepId, FilterStatement statement) {
        if (key.isTextual() && parentValue.isObject()) {
            // This is a KeyStep equivalent
            return KeyStepImplCustom.applyKeyStepFilterStatement(key.getText(), parentValue, stepId, statement);
        }
        if (key.isNumber() && parentValue.isArray()) {
            // This is an IndexStep equivalent
            return IndexStepImplCustom.doApplyFilterStatement(key.decimalValue(), parentValue, stepId, statement);
        }
        return Flux.just(ErrorFactory
                .error(this, "Type mismatch. Tried to access {} with {}", parentValue.getValType(), key.getValType())
                .withParentTrace(ExpressionStep.class, false, parentValue));
    }

    private Val extractValueAt(Val parentValue, Val index) {
        final var trace = Map.<String, Val>of("parentValue", parentValue, "expressionResult", index);
        if (index.isError()) {
            return index.withTrace(ExpressionStep.class, false, trace);
        }
        if (!index.isNumber()) {
            return ErrorFactory.error(this, ARRAY_ACCESS_TYPE_MISMATCH_EXPECT_AN_INTEGER_WAS_S_ERROR, index)
                    .withTrace(ExpressionStep.class, false, trace);
        }
        final var idx   = index.get().asInt();
        final var array = parentValue.get();
        if (idx < 0 || idx > array.size()) {
            return ErrorFactory
                    .error(this, INDEX_OUT_OF_BOUNDS_INDEX_MUST_BE_BETWEEN_0_AND_D_WAS_D_ERROR, array.size(), idx)
                    .withTrace(ExpressionStep.class, false, trace);
        }
        return Val.of(array.get(idx)).withTrace(ExpressionStep.class, true, trace);
    }

    private Val extractKey(Val parentValue, Val key) {
        final var trace = Map.<String, Val>of("parentValue", parentValue, "expressionResult", key);
        if (key.isError()) {
            return key.withTrace(ExpressionStep.class, false, trace);
        }
        if (!key.isTextual()) {
            return ErrorFactory.error(this, OBJECT_ACCESS_TYPE_MISMATCH_EXPECT_A_STRING_WAS_S_ERROR, key)
                    .withTrace(ExpressionStep.class, false, trace);
        }
        final var fieldName = key.get().asText();
        final var object    = parentValue.getObjectNode();
        if (!object.has(fieldName)) {
            return Val.UNDEFINED.withTrace(ExpressionStep.class, true, trace);
        }
        return Val.of(object.get(fieldName)).withTrace(ExpressionStep.class, true, trace);
    }

}
