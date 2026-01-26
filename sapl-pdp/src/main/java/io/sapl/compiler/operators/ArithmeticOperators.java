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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Arithmetic and comparison operations for SAPL expression evaluation.
 * <p>
 * All operations return {@link ErrorValue} for type mismatches rather than
 * throwing exceptions.
 */
@UtilityClass
public class ArithmeticOperators {

    private static final String ERROR_DIVISION_BY_ZERO = "Division by zero.";
    private static final String ERROR_TYPE_MISMATCH    = "Numeric operation requires number values, but found: %s.";

    /**
     * Adds two values.
     * <p>
     * Special case: if the left operand is a {@link TextValue}, performs string
     * concatenation instead of numeric addition.
     */
    public static Value add(Value a, Value b, SourceLocation location) {
        if (a instanceof TextValue(var va)) {
            return b instanceof TextValue(var vb) ? new TextValue(va + vb) : new TextValue(va + b.toString());
        }
        if (a instanceof NumberValue(var va) && b instanceof NumberValue(var vb)) {
            return new NumberValue(va.add(vb));
        }
        return Value.errorAt(location, ERROR_TYPE_MISMATCH, !(a instanceof NumberValue) ? a : b);
    }

    /**
     * Subtracts the second number from the first.
     */
    public static Value subtract(Value a, Value b, SourceLocation location) {
        if (a instanceof NumberValue(var va) && b instanceof NumberValue(var vb)) {
            return new NumberValue(va.subtract(vb));
        }
        return Value.errorAt(location, ERROR_TYPE_MISMATCH, !(a instanceof NumberValue) ? a : b);
    }

    /**
     * Multiplies two numbers.
     */
    public static Value multiply(Value a, Value b, SourceLocation location) {
        if (a instanceof NumberValue(var va) && b instanceof NumberValue(var vb)) {
            return new NumberValue(va.multiply(vb));
        }
        return Value.errorAt(location, ERROR_TYPE_MISMATCH, !(a instanceof NumberValue) ? a : b);
    }

    /**
     * Divides the first number by the second.
     * <p>
     * Uses {@link MathContext#DECIMAL128} for 34 digits of precision, allowing
     * non-terminating decimals like {@code 1/3} to produce a result rather than
     * an error. This precision exceeds IEEE 754 double (~15-17 digits) and is
     * sufficient for all practical policy evaluation scenarios.
     */
    public static Value divide(Value a, Value b, SourceLocation location) {
        if (a instanceof NumberValue(var va) && b instanceof NumberValue(var vb)) {
            if (vb.signum() == 0) {
                return Value.errorAt(location, ERROR_DIVISION_BY_ZERO);
            }
            return new NumberValue(va.divide(vb, MathContext.DECIMAL128));
        }
        return Value.errorAt(location, ERROR_TYPE_MISMATCH, !(a instanceof NumberValue) ? a : b);
    }

    /**
     * Computes mathematical (Euclidean) modulo.
     * <p>
     * Unlike Java's remainder operator which can return negative values,
     * Euclidean modulo always returns a non-negative result when the divisor
     * is positive. For example: {@code -7 % 3 = 2} (not -1).
     */
    public static Value modulo(Value a, Value b, SourceLocation location) {
        if (a instanceof NumberValue(var va) && b instanceof NumberValue(var vb)) {
            if (vb.signum() == 0) {
                return Value.errorAt(location, ERROR_DIVISION_BY_ZERO);
            }
            var result = va.remainder(vb);
            if (result.signum() < 0 && vb.signum() > 0) {
                result = result.add(vb);
            }
            return new NumberValue(result);
        }
        return Value.errorAt(location, ERROR_TYPE_MISMATCH, !(a instanceof NumberValue) ? a : b);
    }

    /**
     * Unary plus - validates operand is numeric and returns it unchanged.
     */
    public static Value unaryPlus(Value v, SourceLocation location) {
        if (v instanceof NumberValue)
            return v;
        return Value.errorAt(location, ERROR_TYPE_MISMATCH, v);
    }

    /**
     * Unary minus - negates a numeric value.
     */
    public static Value unaryMinus(Value v, SourceLocation location) {
        if (!(v instanceof NumberValue(BigDecimal val)))
            return Value.errorAt(location, ERROR_TYPE_MISMATCH, v);
        return new NumberValue(val.negate());
    }

    /**
     * Tests if first number is less than second.
     */
    public static Value lessThan(Value a, Value b, SourceLocation location) {
        if (a instanceof NumberValue(var va) && b instanceof NumberValue(var vb)) {
            return va.compareTo(vb) < 0 ? Value.TRUE : Value.FALSE;
        }
        return Value.errorAt(location, ERROR_TYPE_MISMATCH, !(a instanceof NumberValue) ? a : b);
    }

    /**
     * Tests if first number is less than or equal to second.
     */
    public static Value lessThanOrEqual(Value a, Value b, SourceLocation location) {
        if (a instanceof NumberValue(var va) && b instanceof NumberValue(var vb)) {
            return va.compareTo(vb) <= 0 ? Value.TRUE : Value.FALSE;
        }
        return Value.errorAt(location, ERROR_TYPE_MISMATCH, !(a instanceof NumberValue) ? a : b);
    }

    /**
     * Tests if first number is greater than second.
     */
    public static Value greaterThan(Value a, Value b, SourceLocation location) {
        if (a instanceof NumberValue(var va) && b instanceof NumberValue(var vb)) {
            return va.compareTo(vb) > 0 ? Value.TRUE : Value.FALSE;
        }
        return Value.errorAt(location, ERROR_TYPE_MISMATCH, !(a instanceof NumberValue) ? a : b);
    }

    /**
     * Tests if first number is greater than or equal to second.
     */
    public static Value greaterThanOrEqual(Value a, Value b, SourceLocation location) {
        if (a instanceof NumberValue(var va) && b instanceof NumberValue(var vb)) {
            return va.compareTo(vb) >= 0 ? Value.TRUE : Value.FALSE;
        }
        return Value.errorAt(location, ERROR_TYPE_MISMATCH, !(a instanceof NumberValue) ? a : b);
    }

}
