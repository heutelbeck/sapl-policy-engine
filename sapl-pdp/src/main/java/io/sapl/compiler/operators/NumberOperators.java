/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import io.sapl.compiler.Error;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.antlr.v4.runtime.ParserRuleContext;

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

    private static final String RUNTIME_ERROR_DIVISION_BY_ZERO              = "Division by zero.";
    public static final String  RUNTIME_ERROR_TYPE_MISMATCH_NUMBER_EXPECTED = "Numeric operation requires number values, but found: %s.";

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
    public static Value add(ParserRuleContext astOperator, Value a, Value b) {
        val metadata = a.metadata().merge(b.metadata());
        if (a instanceof ErrorValue error) {
            return error.withMetadata(metadata);
        }
        if (b instanceof ErrorValue error) {
            return error.withMetadata(metadata);
        }
        if (a instanceof TextValue leftText) {
            if (!(b instanceof TextValue rightText)) {
                return new TextValue(leftText.value() + b.toString(), metadata);
            }
            return new TextValue(leftText.value() + rightText.value(), metadata);
        }
        return applyNumericOperation(astOperator, a, b, BigDecimal::add, metadata);
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
    public static Value subtract(ParserRuleContext astOperator, Value a, Value b) {
        return applyNumericOperation(astOperator, a, b, BigDecimal::subtract);
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
    public static Value multiply(ParserRuleContext astOperator, Value a, Value b) {
        return applyNumericOperation(astOperator, a, b, BigDecimal::multiply);
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
    public static Value divide(ParserRuleContext astOperator, Value a, Value b) {
        val metadata = a.metadata().merge(b.metadata());
        try {
            return applyNumericOperation(astOperator, a, b, BigDecimal::divide, metadata);
        } catch (ArithmeticException e) {
            return Error.at(astOperator, metadata, e.getMessage());
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
    public static Value modulo(ParserRuleContext astOperator, Value dividend, Value divisor) {
        val metadata = dividend.metadata().merge(divisor.metadata());
        if (dividend instanceof ErrorValue error) {
            return error.withMetadata(metadata);
        }
        if (divisor instanceof ErrorValue error) {
            return error.withMetadata(metadata);
        }
        if (!(dividend instanceof NumberValue(BigDecimal dividendValue, ValueMetadata ignore))) {
            return Error.at(astOperator, metadata, RUNTIME_ERROR_TYPE_MISMATCH_NUMBER_EXPECTED, dividend);
        }
        if (!(divisor instanceof NumberValue(BigDecimal divisorValue, ValueMetadata ignore2))) {
            return Error.at(astOperator, metadata, RUNTIME_ERROR_TYPE_MISMATCH_NUMBER_EXPECTED, dividend);
        }
        if (divisorValue.signum() == 0) {
            return Error.at(astOperator, metadata, RUNTIME_ERROR_DIVISION_BY_ZERO, dividend);
        }
        var result = dividendValue.remainder(divisorValue);
        // Adjust to mathematical modulo: ensure non-negative result for positive
        // divisor
        if (result.signum() < 0 && divisorValue.signum() > 0) {
            result = result.add(divisorValue);
        }
        return new NumberValue(result, metadata);
    }

    /**
     * Returns the numeric value unchanged (unary plus operator).
     *
     * @param v
     * the value
     *
     * @return the value itself if it is a NumberValue, or error if not
     */
    public static Value unaryPlus(ParserRuleContext astOperator, Value v) {
        if (v instanceof ErrorValue) {
            return v;
        }
        if (!(v instanceof NumberValue)) {
            return Error.at(astOperator, v.metadata(), RUNTIME_ERROR_TYPE_MISMATCH_NUMBER_EXPECTED, v);
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
    public static Value unaryMinus(ParserRuleContext astOperator, Value v) {
        if (v instanceof ErrorValue) {
            return v;
        }
        if (!(v instanceof NumberValue(BigDecimal number, ValueMetadata ignored))) {
            return Error.at(astOperator, v.metadata(), RUNTIME_ERROR_TYPE_MISMATCH_NUMBER_EXPECTED, v);
        }
        return new NumberValue(number.negate(), v.metadata());
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
    public static Value lessThan(ParserRuleContext astOperator, Value a, Value b) {
        return applyNumericComparison(astOperator, a, b, (left, right) -> left.compareTo(right) < 0);
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
    public static Value lessThanOrEqual(ParserRuleContext astOperator, Value a, Value b) {
        return applyNumericComparison(astOperator, a, b, (left, right) -> left.compareTo(right) <= 0);
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
    public static Value greaterThan(ParserRuleContext astOperator, Value a, Value b) {
        return applyNumericComparison(astOperator, a, b, (left, right) -> left.compareTo(right) > 0);
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
    public static Value greaterThanOrEqual(ParserRuleContext astOperator, Value a, Value b) {
        return applyNumericComparison(astOperator, a, b, (left, right) -> left.compareTo(right) >= 0);
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
    private static Value applyNumericComparison(ParserRuleContext astOperator, Value left, Value right,
            BiPredicate<BigDecimal, BigDecimal> comparison) {
        val metadata = left.metadata().merge(right.metadata());
        if (left instanceof ErrorValue error) {
            return error.withMetadata(metadata);
        }
        if (right instanceof ErrorValue error) {
            return error.withMetadata(metadata);
        }
        if (!(left instanceof NumberValue(BigDecimal leftValue, ValueMetadata ignored))) {
            return Error.at(astOperator, metadata, RUNTIME_ERROR_TYPE_MISMATCH_NUMBER_EXPECTED, left);
        }
        if (!(right instanceof NumberValue(BigDecimal rightValue, ValueMetadata ignored2))) {
            return Error.at(astOperator, metadata, RUNTIME_ERROR_TYPE_MISMATCH_NUMBER_EXPECTED, right);
        }
        return new BooleanValue(comparison.test(leftValue, rightValue), metadata);
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
    private static Value applyNumericOperation(ParserRuleContext astOperator, Value left, Value right,
            BinaryOperator<BigDecimal> operation, ValueMetadata metadata) {
        if (left instanceof ErrorValue error) {
            return error.withMetadata(metadata);
        }
        if (right instanceof ErrorValue error) {
            return error.withMetadata(metadata);
        }
        if (!(left instanceof NumberValue(BigDecimal leftValue, ValueMetadata ignored))) {
            return Error.at(astOperator, metadata, RUNTIME_ERROR_TYPE_MISMATCH_NUMBER_EXPECTED, left);
        }
        if (!(right instanceof NumberValue(BigDecimal rightValue, ValueMetadata ignored2))) {
            return Error.at(astOperator, metadata, RUNTIME_ERROR_TYPE_MISMATCH_NUMBER_EXPECTED, right);
        }
        return new NumberValue(operation.apply(leftValue, rightValue), metadata);
    }

    private static Value applyNumericOperation(ParserRuleContext astOperator, Value left, Value right,
            BinaryOperator<BigDecimal> operation) {
        val metadata = left.metadata().merge(right.metadata());
        return applyNumericOperation(astOperator, left, right, operation, metadata);
    }
}
