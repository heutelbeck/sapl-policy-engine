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

import lombok.With;

/**
 * Represents a text Value.
 */
public record TextValue(String value, @With boolean secret) implements Value {

    @Override
    public Value asSecret() {
        return Value.asSecretHelper(this, v -> v.withSecret(true));
    }

    @Override
    public String getValType() {
        return "STRING";
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
        return Value.formatToString("TextValue", secret, () -> "\"" + value + "\"");
    }
}