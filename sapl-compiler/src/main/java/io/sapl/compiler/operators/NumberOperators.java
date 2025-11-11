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
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.util.function.BiFunction;

/**
 * Provides arithmetic operations for NumberValue instances.
 */
@UtilityClass
public class NumberOperators {

    public static Value add(Value a, Value b) {
        if (a instanceof TextValue leftText) {
            if (!(b instanceof TextValue rightText)) {
                return new TextValue(leftText.value() + b.toString(), a.secret() || b.secret());
            }
            return new TextValue(leftText.value() + rightText.value(), a.secret() || b.secret());
        }
        return applyNumericOperation(a, b, BigDecimal::add);
    }

    public static Value subtract(Value a, Value b) {
        return applyNumericOperation(a, b, BigDecimal::subtract);
    }

    public static Value multiply(Value a, Value b) {
        return applyNumericOperation(a, b, BigDecimal::multiply);
    }

    public static Value divide(Value a, Value b) {
        try {
            return applyNumericOperation(a, b, BigDecimal::divide);
        } catch (ArithmeticException e) {
            return new ErrorValue(e, a.secret() || b.secret());
        }
    }

    /**
     * Computes the modulo of one number value by another using mathematical
     * (Euclidean) semantics.
     * The result is always non-negative when the divisor is positive.
     *
     * @param dividend the dividend
     * @param divisor the divisor
     * @return the remainder of a divided by b, marked as secret if either operand
     * is secret,
     * or an ErrorValue if the divisor is zero
     */
    public static Value modulo(Value dividend, Value divisor) {
        if (!(dividend instanceof NumberValue(BigDecimal dividendValue, boolean dividendSecret))) {
            return Value.error(String.format("Numeric operation requires number values, but found: %s", dividend));
        }
        if (!(divisor instanceof NumberValue(BigDecimal divisorValue, boolean divisorSecret))) {
            return Value.error(String.format("Numeric operation requires number values, but found: %s", dividend));
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

    public static Value unaryPlus(Value v) {
        if (!(v instanceof NumberValue)) {
            return Value.error(String.format("Numeric operation requires number values, but found: %s", v));
        }
        return v;
    }

    public static Value unaryMinus(Value v) {
        if (!(v instanceof NumberValue(BigDecimal number, boolean secret))) {
            return Value.error(String.format("Numeric operation requires number values, but found: %s", v));
        }
        return new NumberValue(number.negate(), secret);
    }

    public static Value lessThan(Value a, Value b) {
        return applyNumericComparison(a, b, (left, right) -> left.compareTo(right) < 0);
    }

    public static Value lessThanOrEqual(Value a, Value b) {
        return applyNumericComparison(a, b, (left, right) -> left.compareTo(right) <= 0);
    }

    public static Value greaterThan(Value a, Value b) {
        return applyNumericComparison(a, b, (left, right) -> left.compareTo(right) > 0);
    }

    public static Value greaterThanOrEqual(Value a, Value b) {
        return applyNumericComparison(a, b, (left, right) -> left.compareTo(right) >= 0);
    }

    private static Value applyNumericComparison(Value left, Value right,
            BiFunction<BigDecimal, BigDecimal, Boolean> comparison) {
        if (!(left instanceof NumberValue(BigDecimal leftValue, boolean leftSecret))) {
            return Value.error(String.format("Numeric comparison requires number values, but found: %s", left));
        }
        if (!(right instanceof NumberValue(BigDecimal rightValue, boolean rightSecret))) {
            return Value.error(String.format("Numeric comparison requires number values, but found: %s", right));
        }
        return preserveSecret(comparison.apply(leftValue, rightValue), leftSecret || rightSecret);
    }

    /**
     * Creates a BooleanValue with secret handling, reusing constants.
     *
     * @param value the boolean value
     * @param secret whether the value should be marked as secret
     * @return a BooleanValue with the specified value and secret flag
     */
    private static BooleanValue preserveSecret(boolean value, boolean secret) {
        if (secret) {
            return value ? BooleanValue.SECRET_TRUE : BooleanValue.SECRET_FALSE;
        } else {
            return value ? Value.TRUE : Value.FALSE;
        }
    }

    private static Value applyNumericOperation(Value left, Value right,
            BiFunction<BigDecimal, BigDecimal, BigDecimal> operation) {
        if (!(left instanceof NumberValue(BigDecimal leftValue, boolean leftSecret))) {
            return Value.error(String.format("Numeric operation requires number values, but found: %s", left));
        }
        if (!(right instanceof NumberValue(BigDecimal rightValue, boolean rightSecret))) {
            return Value.error(String.format("Numeric operation requires number values, but found: %s", right));
        }
        return new NumberValue(operation.apply(leftValue, rightValue), leftSecret || rightSecret);
    }

}
