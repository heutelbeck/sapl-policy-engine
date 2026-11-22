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

    private static final String ERROR_NUMBER_OUT_OF_BOUNDS = "Number exceeds the allowed scale.";

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
        if (Math.abs((long) scale) <= MAX_SCALE && precision <= MAX_PRECISION) {
            return value;
        }
        if (value.signum() == 0) {
            return BigDecimal.ZERO;
        }
        // Each additional scale position adds one coefficient digit.
        val targetScale = Math.min(Math.clamp(scale, -MAX_SCALE, MAX_SCALE), (long) scale + MAX_PRECISION - precision);
        if (targetScale < -MAX_SCALE || scale - targetScale >= precision) {
            throw new ArithmeticException(ERROR_NUMBER_OUT_OF_BOUNDS);
        }
        return value.setScale((int) targetScale, RoundingMode.UNNECESSARY);
    }

    /**
     * Admits an already-parsed decimal. In-range scales retain their original
     * precision. Out-of-range scales must fit the arithmetic domain exactly.
     *
     * @param value the parsed number
     * @return the admitted number, or an error if it cannot be represented
     */
    public static Value boundedNumber(BigDecimal value) {
        if (Math.abs((long) value.scale()) <= MAX_SCALE) {
            return Value.of(value);
        }
        try {
            return Value.of(fitExact(value));
        } catch (ArithmeticException ignored) {
            return Value.error(ERROR_NUMBER_OUT_OF_BOUNDS);
        }
    }

    /**
     * Parses a bounded wire token and admits only exact decimal quantities.
     *
     * @param literal the raw numeric token
     * @return the admitted number, or an error if malformed or out of bounds
     */
    public static Value parseBoundedNumber(String literal) {
        if (literal.length() > MAX_WIRE_NUMBER_LENGTH) {
            return Value.error(ERROR_NUMBER_OUT_OF_BOUNDS);
        }
        try {
            return Value.of(fitExact(new BigDecimal(literal)));
        } catch (ArithmeticException | NumberFormatException ignored) {
            return Value.error(ERROR_NUMBER_OUT_OF_BOUNDS);
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
}
