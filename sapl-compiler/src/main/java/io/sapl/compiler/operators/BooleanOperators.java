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
package io.sapl.compiler.operators;

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;

import java.util.function.BinaryOperator;

/**
 * Provides logical operations for BooleanValue instances.
 */
@UtilityClass
public class BooleanOperators {

    public static final String TYPE_MISMATCH_BOOLEAN_EXPECTED_ERROR = "Type mismatch error. Boolean operation requires Boolean values, but found: %s";

    public static Value and(Value a, Value b) {
        return applyBooleanOperation(a, b, (left, right) -> left && right);
    }

    public static Value or(Value a, Value b) {
        return applyBooleanOperation(a, b, (left, right) -> left || right);
    }

    public static Value xor(Value a, Value b) {
        return applyBooleanOperation(a, b, (left, right) -> left ^ right);
    }

    private static Value applyBooleanOperation(Value left, Value right, BinaryOperator<Boolean> operation) {
        if (!(left instanceof BooleanValue boolLeft)) {
            return Value.error(TYPE_MISMATCH_BOOLEAN_EXPECTED_ERROR, left);
        }
        if (!(right instanceof BooleanValue boolRight)) {
            return Value.error(TYPE_MISMATCH_BOOLEAN_EXPECTED_ERROR, right);
        }
        return preserveSecret(operation.apply(boolLeft.value(), boolRight.value()), left.secret() || right.secret());
    }

    public static Value not(Value value) {
        if (!(value instanceof BooleanValue(boolean bool, boolean secret))) {
            return Value.error(TYPE_MISMATCH_BOOLEAN_EXPECTED_ERROR, value);
        }
        return preserveSecret(!bool, secret);
    }

    /**
     * Creates a BooleanValue with secret handling, reusing constants.
     *
     * @param value
     * the boolean value
     * @param secret
     * whether the value should be marked as secret
     *
     * @return a BooleanValue with the specified value and secret flag
     */
    private static BooleanValue preserveSecret(boolean value, boolean secret) {
        if (secret) {
            return value ? BooleanValue.SECRET_TRUE : BooleanValue.SECRET_FALSE;
        } else {
            return value ? Value.TRUE : Value.FALSE;
        }
    }
}
