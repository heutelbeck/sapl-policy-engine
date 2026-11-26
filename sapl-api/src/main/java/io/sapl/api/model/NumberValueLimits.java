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
package io.sapl.api.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

import lombok.experimental.UtilityClass;
import lombok.val;

import static java.lang.Math.abs;
import static java.lang.Math.clamp;
import static java.lang.Math.min;

/** Decimal precision, scale and input-length limits used by SAPL. */
@UtilityClass
public class NumberValueLimits {

    /** Maximum length of a policy number literal. */
    public static final int MAX_NUMBER_LENGTH = 1000;

    /** Maximum number of digits in an arithmetic operand's coefficient. */
    public static final int MAX_PRECISION = 1000;

    /** Maximum absolute scale of an arithmetic operand. */
    public static final int MAX_SCALE = 1000;

    /** Maximum length of a wire number, including sign, decimal point and exponent. */
    public static final int MAX_WIRE_NUMBER_LENGTH = MAX_PRECISION + 10;

    private static final String ERROR_NOT_A_NUMBER        = "The text is not a decimal number.";
    private static final String ERROR_NUMBER_OUT_OF_RANGE = "The number is out of range.";
    private static final String ERROR_NUMBER_TOO_LONG     = "The number is too long. At most %s characters are allowed.";

    /**
     * Fits a decimal into the arithmetic domain exactly, retaining its original
     * representation whenever possible.
     *
     * @param value the number to fit
     * @return the same quantity with bounded precision and scale
     * @throws ArithmeticException if the quantity has no exact representation within the bounds
     */
    public static BigDecimal fitExact(BigDecimal value) {
        val scale     = value.scale();
        val precision = value.precision();
        if (abs((long) scale) <= MAX_SCALE && precision <= MAX_PRECISION) {
            return value;
        }
        if (value.signum() == 0) {
            return BigDecimal.ZERO;
        }
        // Each additional scale position adds one coefficient digit.
        val targetScale = min(clamp(scale, -MAX_SCALE, MAX_SCALE), (long) scale + MAX_PRECISION - precision);
        if (targetScale < -MAX_SCALE || scale - targetScale >= precision) {
            throw new ArithmeticException(ERROR_NUMBER_OUT_OF_RANGE);
        }
        try {
            return value.setScale((int) targetScale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException inexact) {
            val outOfRange = new ArithmeticException(ERROR_NUMBER_OUT_OF_RANGE);
            outOfRange.initCause(inexact);
            throw outOfRange;
        }
    }

    /**
     * Admits an already-parsed decimal. In-range scales retain their original
     * precision. Out-of-range scales must fit the arithmetic domain exactly.
     *
     * @param value the parsed number
     * @return the admitted number, or an error if it cannot be represented
     */
    public static Value boundedNumber(BigDecimal value) {
        if (abs((long) value.scale()) <= MAX_SCALE) {
            return Value.of(value);
        }
        try {
            return Value.of(fitExact(value));
        } catch (ArithmeticException ignored) {
            return Value.error(ERROR_NUMBER_OUT_OF_RANGE);
        }
    }

    /**
     * Parses a bounded ASCII wire token and admits only exact decimal quantities.
     *
     * @param literal the raw numeric token
     * @return the admitted number, or an error if malformed or out of bounds
     */
    public static Value parseBoundedNumber(String literal) {
        if (literal.length() > MAX_WIRE_NUMBER_LENGTH) {
            return Value.error(ERROR_NUMBER_TOO_LONG.formatted(MAX_WIRE_NUMBER_LENGTH));
        }
        if (!isDecimalNumber(literal)) {
            return Value.error(ERROR_NOT_A_NUMBER);
        }
        try {
            return Value.of(fitExact(new BigDecimal(literal)));
        } catch (ArithmeticException | NumberFormatException outOfRange) {
            return Value.error(ERROR_NUMBER_OUT_OF_RANGE);
        }
    }

    /**
     * Removes redundant coefficient zeros in scientific notation. Plain decimal
     * quantities retain their fractional scale, for example {@code 12.50}.
     *
     * @param value the number to write
     * @return a decimal for the wire spelling
     */
    public static BigDecimal wireNumber(BigDecimal value) {
        val scale = value.scale();
        if (scale == 0 || (scale > 0 && (long) value.precision() - 1 - scale >= -6)) {
            return value;
        }
        // An application may supply a scale that cannot be reduced further.
        if ((long) scale - value.precision() < Integer.MIN_VALUE) {
            return value;
        }
        return value.stripTrailingZeros();
    }

    /**
     * Spells whole signed 64-bit values as plain integers for JSON, YAML and CSV
     * text output. Other numbers use their wire spelling.
     *
     * @param value the number to write
     * @return a decimal with the output scale
     */
    public static BigDecimal textFormatNumber(BigDecimal value) {
        val integer = BigDecimal.valueOf(value.longValue());
        return value.compareTo(integer) == 0 ? integer : wireNumber(value);
    }

    private static boolean isDecimalNumber(String literal) {
        var position = 0;
        if (!literal.isEmpty() && (literal.charAt(0) == '+' || literal.charAt(0) == '-')) {
            position++;
        }
        val integerStart = position;
        position = decimalDigitsEnd(literal, position);
        var hasDigits = position > integerStart;
        if (position < literal.length() && literal.charAt(position) == '.') {
            position++;
            val fractionStart = position;
            position  = decimalDigitsEnd(literal, position);
            hasDigits = hasDigits || position > fractionStart;
        }
        if (!hasDigits) {
            return false;
        }
        if (position == literal.length()) {
            return true;
        }
        val marker = literal.charAt(position);
        if (marker != 'e' && marker != 'E') {
            return false;
        }
        position++;
        if (position < literal.length() && (literal.charAt(position) == '+' || literal.charAt(position) == '-')) {
            position++;
        }
        val exponentStart = position;
        position = decimalDigitsEnd(literal, position);
        return position > exponentStart && position == literal.length();
    }

    private static int decimalDigitsEnd(String literal, int start) {
        var end = start;
        while (end < literal.length() && literal.charAt(end) >= '0' && literal.charAt(end) <= '9') {
            end++;
        }
        return end;
    }
}
