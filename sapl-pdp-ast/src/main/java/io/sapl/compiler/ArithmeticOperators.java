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
package io.sapl.compiler;

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.ast.AstNode;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Arithmetic and comparison operations for SAPL expression evaluation.
 * <p>
 * All operations return {@link ErrorValue} for type mismatches rather than
 * throwing exceptions. Error values propagate through operations (if either
 * operand is an error, the error is returned).
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
    public static Value add(AstNode op, Value a, Value b) {
        if (a instanceof ErrorValue)
            return a;
        if (b instanceof ErrorValue)
            return b;
        if (a instanceof TextValue(String va)) {
            return b instanceof TextValue(String vb) ? new TextValue(va + vb) : new TextValue(va + b.toString());
        }
        if (!(a instanceof NumberValue(BigDecimal va)))
            return Value.errorAt(op, ERROR_TYPE_MISMATCH, a);
        if (!(b instanceof NumberValue(BigDecimal vb)))
            return Value.errorAt(op, ERROR_TYPE_MISMATCH, b);
        return new NumberValue(va.add(vb));
    }

    /**
     * Subtracts the second number from the first.
     */
    public static Value subtract(AstNode op, Value a, Value b) {
        if (a instanceof ErrorValue)
            return a;
        if (b instanceof ErrorValue)
            return b;
        if (!(a instanceof NumberValue(BigDecimal va)))
            return Value.errorAt(op, ERROR_TYPE_MISMATCH, a);
        if (!(b instanceof NumberValue(BigDecimal vb)))
            return Value.errorAt(op, ERROR_TYPE_MISMATCH, b);
        return new NumberValue(va.subtract(vb));
    }

    /**
     * Multiplies two numbers.
     */
    public static Value multiply(AstNode op, Value a, Value b) {
        if (a instanceof ErrorValue)
            return a;
        if (b instanceof ErrorValue)
            return b;
        if (!(a instanceof NumberValue(BigDecimal va)))
            return Value.errorAt(op, ERROR_TYPE_MISMATCH, a);
        if (!(b instanceof NumberValue(BigDecimal vb)))
            return Value.errorAt(op, ERROR_TYPE_MISMATCH, b);
        return new NumberValue(va.multiply(vb));
    }

    /**
     * Divides the first number by the second.
     * <p>
     * Uses {@link MathContext#DECIMAL128} for 34 digits of precision, allowing
     * non-terminating decimals like {@code 1/3} to produce a result rather than
     * an error. This precision exceeds IEEE 754 double (~15-17 digits) and is
     * sufficient for all practical policy evaluation scenarios.
     */
    public static Value divide(AstNode op, Value a, Value b) {
        if (a instanceof ErrorValue)
            return a;
        if (b instanceof ErrorValue)
            return b;
        if (!(a instanceof NumberValue(BigDecimal va)))
            return Value.errorAt(op, ERROR_TYPE_MISMATCH, a);
        if (!(b instanceof NumberValue(BigDecimal vb)))
            return Value.errorAt(op, ERROR_TYPE_MISMATCH, b);
        if (vb.signum() == 0)
            return Value.errorAt(op, ERROR_DIVISION_BY_ZERO);
        return new NumberValue(va.divide(vb, MathContext.DECIMAL128));
    }

    /**
     * Computes mathematical (Euclidean) modulo.
     * <p>
     * Unlike Java's remainder operator which can return negative values,
     * Euclidean modulo always returns a non-negative result when the divisor
     * is positive. For example: {@code -7 % 3 = 2} (not -1).
     */
    public static Value modulo(AstNode op, Value a, Value b) {
        if (a instanceof ErrorValue)
            return a;
        if (b instanceof ErrorValue)
            return b;
        if (!(a instanceof NumberValue(BigDecimal va)))
            return Value.errorAt(op, ERROR_TYPE_MISMATCH, a);
        if (!(b instanceof NumberValue(BigDecimal vb)))
            return Value.errorAt(op, ERROR_TYPE_MISMATCH, b);
        if (vb.signum() == 0)
            return Value.errorAt(op, ERROR_DIVISION_BY_ZERO);
        var result = va.remainder(vb);
        if (result.signum() < 0 && vb.signum() > 0) {
            result = result.add(vb);
        }
        return new NumberValue(result);
    }

    /**
     * Unary plus - validates operand is numeric and returns it unchanged.
     */
    public static Value unaryPlus(AstNode op, Value v) {
        if (v instanceof ErrorValue)
            return v;
        if (v instanceof NumberValue)
            return v;
        return Value.errorAt(op, ERROR_TYPE_MISMATCH, v);
    }

    /**
     * Unary minus - negates a numeric value.
     */
    public static Value unaryMinus(AstNode op, Value v) {
        if (v instanceof ErrorValue)
            return v;
        if (!(v instanceof NumberValue(BigDecimal val)))
            return Value.errorAt(op, ERROR_TYPE_MISMATCH, v);
        return new NumberValue(val.negate());
    }

    /**
     * Tests if first number is less than second.
     */
    public static Value lessThan(AstNode op, Value a, Value b) {
        if (a instanceof ErrorValue)
            return a;
        if (b instanceof ErrorValue)
            return b;
        if (!(a instanceof NumberValue(BigDecimal va)))
            return Value.errorAt(op, ERROR_TYPE_MISMATCH, a);
        if (!(b instanceof NumberValue(BigDecimal vb)))
            return Value.errorAt(op, ERROR_TYPE_MISMATCH, b);
        return va.compareTo(vb) < 0 ? Value.TRUE : Value.FALSE;
    }

    /**
     * Tests if first number is less than or equal to second.
     */
    public static Value lessThanOrEqual(AstNode op, Value a, Value b) {
        if (a instanceof ErrorValue)
            return a;
        if (b instanceof ErrorValue)
            return b;
        if (!(a instanceof NumberValue(BigDecimal va)))
            return Value.errorAt(op, ERROR_TYPE_MISMATCH, a);
        if (!(b instanceof NumberValue(BigDecimal vb)))
            return Value.errorAt(op, ERROR_TYPE_MISMATCH, b);
        return va.compareTo(vb) <= 0 ? Value.TRUE : Value.FALSE;
    }

    /**
     * Tests if first number is greater than second.
     */
    public static Value greaterThan(AstNode op, Value a, Value b) {
        if (a instanceof ErrorValue)
            return a;
        if (b instanceof ErrorValue)
            return b;
        if (!(a instanceof NumberValue(BigDecimal va)))
            return Value.errorAt(op, ERROR_TYPE_MISMATCH, a);
        if (!(b instanceof NumberValue(BigDecimal vb)))
            return Value.errorAt(op, ERROR_TYPE_MISMATCH, b);
        return va.compareTo(vb) > 0 ? Value.TRUE : Value.FALSE;
    }

    /**
     * Tests if first number is greater than or equal to second.
     */
    public static Value greaterThanOrEqual(AstNode op, Value a, Value b) {
        if (a instanceof ErrorValue)
            return a;
        if (b instanceof ErrorValue)
            return b;
        if (!(a instanceof NumberValue(BigDecimal va)))
            return Value.errorAt(op, ERROR_TYPE_MISMATCH, a);
        if (!(b instanceof NumberValue(BigDecimal vb)))
            return Value.errorAt(op, ERROR_TYPE_MISMATCH, b);
        return va.compareTo(vb) >= 0 ? Value.TRUE : Value.FALSE;
    }

}
