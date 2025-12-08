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
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;

/**
 * Boolean value implementation.
 */
public record BooleanValue(boolean value, @NonNull ValueMetadata metadata) implements Value {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * Singleton for secret true value.
     */
    public static final BooleanValue SECRET_TRUE = new BooleanValue(true, ValueMetadata.SECRET_EMPTY);

    /**
     * Singleton for secret false value.
     */
    public static final BooleanValue SECRET_FALSE = new BooleanValue(false, ValueMetadata.SECRET_EMPTY);

    @Override
    public Value withMetadata(ValueMetadata newMetadata) {
        return new BooleanValue(value, newMetadata);
    }

    @Override
    public @NotNull String toString() {
        return isSecret() ? SECRET_PLACEHOLDER : String.valueOf(value);
    }

    @Override
    public boolean equals(Object that) {
        if (this == that)
            return true;
        if (!(that instanceof BooleanValue(boolean thatValue, ValueMetadata ignored)))
            return false;
        return value == thatValue;
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(value);
    }
}
