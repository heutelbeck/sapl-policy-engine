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
     * The text form of the number, as the SAPL value model writes it: a whole
     * number in the range of a signed 64 bit integer plainly, such as {@code 5}
     * for {@code 5.0} and {@code 1000} for {@code 1E+3}, and any other number as
     * {@link BigDecimal#toString()} writes it, such as {@code 12.50},
     * {@code 1E-7} and {@code 1E+20}.
     *
     * @return the text form of the number
     */
    @Override
    public @NotNull String toString() {
        try {
            return Long.toString(value.longValueExact());
        } catch (ArithmeticException notAWholeNumberOf64Bits) {
            return value.toString();
        }
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
