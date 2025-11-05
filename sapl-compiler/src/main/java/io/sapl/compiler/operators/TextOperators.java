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
package io.sapl.compiler.operators;

import java.math.BigDecimal;
import java.util.Objects;

import io.sapl.api.v2.NumberValue;
import io.sapl.api.v2.TextValue;
import io.sapl.api.v2.Value;
import lombok.experimental.UtilityClass;

/**
 * Provides operations for TextValue instances.
 */
@UtilityClass
public class TextOperators {

    /**
     * Concatenates a text value with another value.
     * <p>
     * If the second value is a TextValue, concatenates the raw text without quotes.
     * Otherwise, uses the toString() representation of the value.
     *
     * @param a the first text value
     * @param b the value to concatenate
     * @return the concatenated text, marked as secret if either operand is secret
     */
    public static TextValue concatenate(TextValue a, Value b) {
        String appendValue;
        if (b instanceof TextValue textValue) {
            // Concatenate raw text without quotes
            appendValue = textValue.value();
        } else {
            // Use toString() for other value types
            appendValue = b.toString();
        }
        return new TextValue(a.value() + appendValue, a.secret() || b.secret());
    }

    /**
     * Returns the length of a text value.
     *
     * @param a the text value
     * @return a NumberValue representing the length, preserving the secret status
     */
    public static NumberValue length(TextValue a) {
        return new NumberValue(BigDecimal.valueOf(a.value().length()), a.secret());
    }
}