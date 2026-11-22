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

import io.sapl.api.SaplVersion;
import lombok.NonNull;
import lombok.val;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * Numeric value implementation. Uses numerical equality (not BigDecimal
 * scale-sensitive equality). For example,
 * Value.of(1.0) equals Value.of(1.00).
 */
public record NumberValue(@NonNull BigDecimal value) implements Value {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * The exact SAPL text of this number. Magnitudes from 10<sup>-6</sup>
     * inclusive to 10<sup>21</sup> exclusive use plain decimal notation.
     * Other nonzero values use scientific notation. Equal numbers have the same
     * text regardless of their scale.
     *
     * @return the SAPL text of this number
     */
    @Override
    public @NotNull String toString() {
        if (value.signum() == 0) {
            return "0";
        }
        val precision = value.precision();
        val exponent  = (long) precision - 1 - value.scale();
        if (exponent >= -6 && exponent < 21) {
            return value.scale() <= 0 ? value.toPlainString() : value.stripTrailingZeros().toPlainString();
        }
        // Normalizing the significand at scale precision - 1 also works when the
        // original BigDecimal is already at its minimum or maximum scale.
        val significand = new BigDecimal(value.unscaledValue(), precision - 1).stripTrailingZeros();
        return significand.toPlainString() + (exponent > 0 ? "E+" : "E") + exponent;
    }

    @Override
    public boolean equals(Object that) {
        if (this == that)
            return true;
        if (!(that instanceof NumberValue(BigDecimal thatValue)))
            return false;
        // Uses numerical equality, not BigDecimal equality.
        // Makes Value.of(1.0).equals(Value.of(1.00)) return true.
        return value.compareTo(thatValue) == 0;
    }

    @Override
    public int hashCode() {
        if (value.signum() == 0) {
            return BigDecimal.ZERO.hashCode();
        }

        try {
            // Normalize for consistent hashing: 1.0 and 1.00 return same hash
            return value.stripTrailingZeros().hashCode();
        } catch (ArithmeticException e) {
            // Extreme scale edge case: fallback maintains contract
            // Two numerically equal extreme values both fail, then both use fallback and
            // are consistent
            return value.hashCode();
        }
    }
}
