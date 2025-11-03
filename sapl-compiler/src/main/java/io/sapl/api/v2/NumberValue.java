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
package io.sapl.api.v2;

import java.math.BigDecimal;
import java.util.Objects;

import lombok.With;

/**
 * Represents a numeric Value.
 *
 * Uses numerical equality (not BigDecimal scale-sensitive equality).
 * For example, Value.of(1.0) equals Value.of(1.00).
 */
public record NumberValue(BigDecimal value, @With boolean secret) implements Value {

    @Override
    public Value asSecret() {
        return Value.asSecretHelper(this, v -> v.withSecret(true));
    }

    @Override
    public String getValType() {
        return "NUMBER";
    }

    @Override
    public Object getTrace() {
        return null;
    }

    @Override
    public Object getErrorsFromTrace() {
        return null;
    }

    @Override
    public String toString() {
        return Value.formatToString("NumberValue", secret, () -> value.toString());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof NumberValue other)) return false;
        // Use numerical equality, not BigDecimal equality
        // This makes Value.of(1.0).equals(Value.of(1.00)) return true
        return secret == other.secret &&
               value.compareTo(other.value) == 0;
    }

    @Override
    public int hashCode() {
        // Normalize for consistent hashing with numerical equality
        // stripTrailingZeros() normalizes scale, but special-case zero
        // because stripTrailingZeros() can throw on "0"
        var normalized = value.signum() == 0
            ? BigDecimal.ZERO
            : value.stripTrailingZeros();

        return Objects.hash(normalized, secret);
    }
}