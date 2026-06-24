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

import lombok.experimental.UtilityClass;

/**
 * Bounds for numbers admitted from untrusted input (authorization
 * subscriptions, wire codecs).
 * <p>
 * The amplifying denial-of-service vector is an extreme BigDecimal scale: a
 * value such as {@code 1E1000000000} is a few characters and cheap to hold, but
 * forces gigabytes of work when later materialised, for example by
 * {@link BigDecimal#toPlainString()}. Scale is therefore bounded at every
 * admission point; the check is O(1).
 * <p>
 * Raw precision (digit count) is not amplifying: a high-precision number is
 * bounded by the length of its own input, and rendering it is linear in that
 * length. It is capped at the parse boundaries instead. The JSON parser already
 * enforces Jackson's {@code StreamReadConstraints.maxNumberLength}; the string
 * entry point here applies the same length cap before constructing the
 * BigDecimal, so the BigInteger backing an over-long literal is never allocated
 * on the binary path.
 */
@UtilityClass
public class NumberValueLimits {

    /**
     * Maximum number of characters in a numeric literal parsed from the binary
     * wire. Matches Jackson's default
     * {@code StreamReadConstraints.maxNumberLength}.
     */
    public static final int MAX_NUMBER_LENGTH = 1000;

    /**
     * Maximum magnitude of a BigDecimal scale. Bounds the decimal-point shift, so
     * a plain-string rendering can never expand beyond a small multiple of this.
     */
    public static final int MAX_SCALE = 1000;

    private static final String ERROR_NUMBER_OUT_OF_BOUNDS = "Number exceeds the allowed scale.";

    /**
     * Admits an already-parsed number, rejecting it when its scale magnitude
     * exceeds the bound.
     *
     * @param value the parsed number
     * @return a NumberValue when within bounds, otherwise an ErrorValue
     */
    public static Value boundedNumber(BigDecimal value) {
        if (Math.abs((long) value.scale()) > MAX_SCALE) {
            return Value.error(ERROR_NUMBER_OUT_OF_BOUNDS);
        }
        return Value.of(value);
    }

    /**
     * Parses a numeric literal from untrusted input, failing closed on an
     * over-long literal (before constructing the BigDecimal), on a malformed
     * literal, and on a parsed value whose scale exceeds the bound.
     *
     * @param literal the raw numeric literal
     * @return a NumberValue when valid and within bounds, otherwise an ErrorValue
     */
    public static Value parseBoundedNumber(String literal) {
        if (literal.length() > MAX_NUMBER_LENGTH) {
            return Value.error(ERROR_NUMBER_OUT_OF_BOUNDS);
        }
        final BigDecimal value;
        try {
            value = new BigDecimal(literal);
        } catch (NumberFormatException ignored) {
            return Value.error(ERROR_NUMBER_OUT_OF_BOUNDS);
        }
        return boundedNumber(value);
    }
}
