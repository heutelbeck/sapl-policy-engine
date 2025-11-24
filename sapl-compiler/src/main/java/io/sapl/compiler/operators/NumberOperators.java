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

import io.sapl.api.model.*;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;

/**
 * Provides arithmetic and comparison operations for NumberValue instances.
 * <p>
 * Supports basic arithmetic (add, subtract, multiply, divide, modulo), unary
 * operations, and numeric comparisons. All
 * operations preserve secret flags from operands and return appropriate error
 * values for type mismatches.
 */
@UtilityClass
public class NumberOperators {

    public static final String TYPE_MISMATCH_NUMBER_EXPECTED_ERROR = "Numeric operation requires number values, but found: %s";

    /**
     * Adds two values.
     * <p>
     * Special case: if the left operand is a TextValue, performs string
     * concatenation instead of numeric addition.
     *
     * @param a
     * the first operand (or string to concatenate to)
     * @param b
     * the second operand (or value to concatenate)
     *
     * @return sum of two numbers, or concatenated string if left operand is text,
     * or error if type mismatch
     */
    public static Value add(Value a, Value b) {
        if (a instanceof TextValue leftText) {
            if (!(b instanceof TextValue rightText)) {
                return new TextValue(leftText.value() + b.toString(), a.secret() || b.secret());
            }
            return new TextValue(leftText.value() + rightText.value(), a.secret() || b.secret());
        }
        return applyNumericOperation(a, b, BigDecimal::add);
    }

    /**
     * Subtracts one number from another.
     *
     * @param a
     * the minuend
     * @param b
     * the subtrahend
     *
     * @return difference of the two numbers, or error if either operand is not a
     * NumberValue
     */
    public static Value subtract(Value a, Value b) {
        return applyNumericOperation(a, b, BigDecimal::subtract);
    }

    /**
     * Multiplies two numbers.
     *
     * @param a
     * the first factor
     * @param b
     * the second factor
     *
     * @return product of the two numbers, or error if either operand is not a
     * NumberValue
     */
    public static Value multiply(Value a, Value b) {
        return applyNumericOperation(a, b, BigDecimal::multiply);
    }

    /**
     * Divides one number by another.
     *
     * @param a
     * the dividend
     * @param b
     * the divisor
     *
     * @return quotient of the division, or error if either operand is not a
     * NumberValue or division is not exact
     */
    public static Value divide(Value a, Value b) {
        try {
            return applyNumericOperation(a, b, BigDecimal::divide);
        } catch (ArithmeticException e) {
            return new ErrorValue(e, a.secret() || b.secret());
        }
    }

    /**
     * Computes the modulo of one number value by another using mathematical
     * (Euclidean) semantics. The result is always
     * non-negative when the divisor is positive.
     *
     * @param dividend
     * the dividend
     * @param divisor
     * the divisor
     *
     * @return the remainder of a divided by b, marked as secret if either operand
     * is secret, or an ErrorValue if the
     * divisor is zero
     */
    public static Value modulo(Value dividend, Value divisor) {
        if (!(dividend instanceof NumberValue(BigDecimal dividendValue, boolean dividendSecret))) {
            return Value.error(TYPE_MISMATCH_NUMBER_EXPECTED_ERROR, dividend);
        }
        if (!(divisor instanceof NumberValue(BigDecimal divisorValue, boolean divisorSecret))) {
            return Value.error(TYPE_MISMATCH_NUMBER_EXPECTED_ERROR, dividend);
        }
        if (divisorValue.signum() == 0) {
            return new ErrorValue("Division by zero.", dividendSecret || divisorSecret);
        }
        var result = dividendValue.remainder(divisorValue);
        // Adjust to mathematical modulo: ensure non-negative result for positive
        // divisor
        if (result.signum() < 0 && divisorValue.signum() > 0) {
            result = result.add(divisorValue);
        }
        return new NumberValue(result, dividendSecret || divisorSecret);
    }

    /**
     * Returns the numeric value unchanged (unary plus operator).
     *
     * @param v
     * the value
     *
     * @return the value itself if it is a NumberValue, or error if not
     */
    public static Value unaryPlus(Value v) {
        if (!(v instanceof NumberValue)) {
            return Value.error(TYPE_MISMATCH_NUMBER_EXPECTED_ERROR, v);
        }
        return v;
    }

    /**
     * Negates a numeric value (unary minus operator).
     *
     * @param v
     * the value to negate
     *
     * @return negated number preserving secret flag, or error if not a NumberValue
     */
    public static Value unaryMinus(Value v) {
        if (!(v instanceof NumberValue(BigDecimal number, boolean secret))) {
            return Value.error(TYPE_MISMATCH_NUMBER_EXPECTED_ERROR, v);
        }
        return new NumberValue(number.negate(), secret);
    }

    /**
     * Tests if one number is less than another.
     *
     * @param a
     * the left operand
     * @param b
     * the right operand
     *
     * @return Value.TRUE if a &lt; b, Value.FALSE otherwise, or error if either
     * operand is not a NumberValue
     */
    public static Value lessThan(Value a, Value b) {
        return applyNumericComparison(a, b, (left, right) -> left.compareTo(right) < 0);
    }

    /**
     * Tests if one number is less than or equal to another.
     *
     * @param a
     * the left operand
     * @param b
     * the right operand
     *
     * @return Value.TRUE if a &lt;= b, Value.FALSE otherwise, or error if either
     * operand is not a NumberValue
     */
    public static Value lessThanOrEqual(Value a, Value b) {
        return applyNumericComparison(a, b, (left, right) -> left.compareTo(right) <= 0);
    }

    /**
     * Tests if one number is greater than another.
     *
     * @param a
     * the left operand
     * @param b
     * the right operand
     *
     * @return Value.TRUE if a &gt; b, Value.FALSE otherwise, or error if either
     * operand is not a NumberValue
     */
    public static Value greaterThan(Value a, Value b) {
        return applyNumericComparison(a, b, (left, right) -> left.compareTo(right) > 0);
    }

    /**
     * Tests if one number is greater than or equal to another.
     *
     * @param a
     * the left operand
     * @param b
     * the right operand
     *
     * @return Value.TRUE if a &gt;= b, Value.FALSE otherwise, or error if either
     * operand is not a NumberValue
     */
    public static Value greaterThanOrEqual(Value a, Value b) {
        return applyNumericComparison(a, b, (left, right) -> left.compareTo(right) >= 0);
    }

    /**
     * Applies a numeric comparison operation with type checking and secret
     * preservation.
     *
     * @param left
     * the left operand
     * @param right
     * the right operand
     * @param comparison
     * the comparison predicate to apply
     *
     * @return result of the comparison with combined secret flag, or error if type
     * mismatch
     */
    private static Value applyNumericComparison(Value left, Value right,
            BiPredicate<BigDecimal, BigDecimal> comparison) {
        if (!(left instanceof NumberValue(BigDecimal leftValue, boolean leftSecret))) {
            return Value.error(TYPE_MISMATCH_NUMBER_EXPECTED_ERROR, left);
        }
        if (!(right instanceof NumberValue(BigDecimal rightValue, boolean rightSecret))) {
            return Value.error(TYPE_MISMATCH_NUMBER_EXPECTED_ERROR, right);
        }
        return preserveSecret(comparison.test(leftValue, rightValue), leftSecret || rightSecret);
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

    /**
     * Applies a numeric operation with type checking and secret preservation.
     *
     * @param left
     * the left operand
     * @param right
     * the right operand
     * @param operation
     * the arithmetic operation to apply
     *
     * @return result of the operation with combined secret flag, or error if type
     * mismatch
     */
    private static Value applyNumericOperation(Value left, Value right, BinaryOperator<BigDecimal> operation) {
        if (!(left instanceof NumberValue(BigDecimal leftValue, boolean leftSecret))) {
            return Value.error(TYPE_MISMATCH_NUMBER_EXPECTED_ERROR, left);
        }
        if (!(right instanceof NumberValue(BigDecimal rightValue, boolean rightSecret))) {
            return Value.error(TYPE_MISMATCH_NUMBER_EXPECTED_ERROR, right);
        }
        return new NumberValue(operation.apply(leftValue, rightValue), leftSecret || rightSecret);
    }

}
