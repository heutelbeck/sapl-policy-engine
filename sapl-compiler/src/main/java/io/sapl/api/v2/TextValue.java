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

import io.sapl.api.SaplVersion;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;
import java.util.Objects;

/**
 * Text value implementation.
 */
public record TextValue(@NonNull String value, boolean secret) implements Value {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    @Override
    public Value asSecret() {
        return secret ? this : new TextValue(value, true);
    }

    @Override
    public @NotNull String toString() {
        return secret ? SECRET_PLACEHOLDER : "\"" + value + "\"";
    }

    @Override
    public boolean equals(Object that) {
        if (this == that)
            return true;
        if (!(that instanceof TextValue thatText))
            return false;
        return Objects.equals(value, thatText.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}