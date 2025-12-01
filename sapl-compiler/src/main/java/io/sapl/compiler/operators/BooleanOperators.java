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
import io.sapl.compiler.Error;
import lombok.experimental.UtilityClass;
import org.eclipse.emf.ecore.EObject;

import java.util.function.BinaryOperator;

/**
 * Provides logical operations for BooleanValue instances.
 * <p>
 * All operations preserve secret flags from operands and return appropriate
 * error values for type mismatches.
 */
@UtilityClass
public class BooleanOperators {

    public static final String TYPE_MISMATCH_BOOLEAN_EXPECTED_ERROR = "Type mismatch error. Boolean operation requires Boolean values, but found: %s";

    /**
     * Performs logical AND operation on two boolean values.
     *
     * @param a
     * the first operand
     * @param b
     * the second operand
     *
     * @return Value.TRUE if both operands are true, Value.FALSE otherwise, or error
     * if either operand is not a
     * BooleanValue
     */
    public static Value and(EObject astNode, Value a, Value b) {
        return applyBooleanOperation(astNode, a, b, (left, right) -> left && right);
    }

    /**
     * Performs logical OR operation on two boolean values.
     *
     * @param a
     * the first operand
     * @param b
     * the second operand
     *
     * @return Value.TRUE if at least one operand is true, Value.FALSE otherwise, or
     * error if either operand is not a
     * BooleanValue
     */
    public static Value or(EObject astNode, Value a, Value b) {
        return applyBooleanOperation(astNode, a, b, (left, right) -> left || right);
    }

    /**
     * Performs logical XOR operation on two boolean values.
     *
     * @param a
     * the first operand
     * @param b
     * the second operand
     *
     * @return Value.TRUE if exactly one operand is true, Value.FALSE otherwise, or
     * error if either operand is not a
     * BooleanValue
     */
    public static Value xor(EObject astNode, Value a, Value b) {
        return applyBooleanOperation(astNode, a, b, (left, right) -> left ^ right);
    }

    /**
     * Applies a binary boolean operation to two values with type checking and
     * secret preservation.
     *
     * @param left
     * the left operand
     * @param right
     * the right operand
     * @param operation
     * the boolean operation to apply
     *
     * @return result of the operation with combined secret flag, or error if type
     * mismatch
     */
    private static Value applyBooleanOperation(EObject astNode, Value left, Value right,
            BinaryOperator<Boolean> operation) {
        if (!(left instanceof BooleanValue boolLeft)) {
            return Error.at(astNode, TYPE_MISMATCH_BOOLEAN_EXPECTED_ERROR, left);
        }
        if (!(right instanceof BooleanValue boolRight)) {
            return Error.at(astNode, TYPE_MISMATCH_BOOLEAN_EXPECTED_ERROR, right);
        }
        return preserveSecret(operation.apply(boolLeft.value(), boolRight.value()), left.secret() || right.secret());
    }

    /**
     * Performs logical NOT operation on a boolean value.
     *
     * @param value
     * the operand to negate
     *
     * @return negated boolean value preserving secret flag, or error if operand is
     * not a BooleanValue
     */
    public static Value not(EObject astNode, Value value) {
        if (!(value instanceof BooleanValue(boolean bool, boolean secret))) {
            return Error.at(astNode, TYPE_MISMATCH_BOOLEAN_EXPECTED_ERROR, value);
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
