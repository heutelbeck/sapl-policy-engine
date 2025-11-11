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
package io.sapl.api.model;

import io.sapl.api.SaplVersion;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;

/**
 * Null value implementation.
 */
public record NullValue(boolean secret) implements Value {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * Singleton for secret null value.
     */
    public static final Value SECRET_NULL = new NullValue(true);

    @Override
    public Value asSecret() {
        return SECRET_NULL;
    }

    @Override
    public @NotNull String toString() {
        return secret ? SECRET_PLACEHOLDER : "null";
    }

    @Override
    public boolean equals(Object that) {
        if (this == that)
            return true;
        // All null values are semantically equal.
        return that instanceof NullValue;
    }

    @Override
    public int hashCode() {
        // All null values have same hash code.
        return NullValue.class.hashCode();
    }
}
