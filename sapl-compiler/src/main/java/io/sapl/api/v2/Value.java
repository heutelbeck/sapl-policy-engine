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

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Central value type for policy evaluation. A Value can represent a defined
 * value (null, boolean, number, text, array, object), an error state, or an
 * undefined state.
 * <p/>
 * Values can be marked as secret to prevent exposure in traces and logs.
 */
public sealed interface Value extends Serializable
        permits UndefinedValue, ErrorValue, NullValue, BooleanValue, NumberValue, TextValue, ArrayValue, ObjectValue {

    /**
     * Placeholder text used when displaying secret values.
     */
    String SECRET_PLACEHOLDER = "***SECRET***";

    /**
     * Creates an undefined Value.
     *
     * @return an undefined Value
     */
    static Value undefined() {
        return UndefinedValue.INSTANCE;
    }

    /**
     * Creates a null Value.
     *
     * @return a null Value
     */
    static Value ofNull() {
        return NullValue.INSTANCE;
    }

    /**
     * Creates a boolean Value.
     *
     * @param value the boolean value
     * @return a boolean Value
     */
    static Value of(boolean value) {
        return value ? BooleanValue.TRUE : BooleanValue.FALSE;
    }

    /**
     * Creates a number Value from a long.
     *
     * @param value the long value
     * @return a number Value
     */
    static Value of(long value) {
        return new NumberValue(BigDecimal.valueOf(value), false);
    }

    /**
     * Creates a number Value from a double.
     *
     * @param value the double value
     * @return a number Value
     */
    static Value of(double value) {
        return new NumberValue(BigDecimal.valueOf(value), false);
    }

    /**
     * Creates a number Value from a BigDecimal.
     *
     * @param value the BigDecimal value
     * @return a number Value
     */
    static Value of(BigDecimal value) {
        return new NumberValue(value, false);
    }

    /**
     * Creates a text Value.
     *
     * @param value the text value
     * @return a text Value
     */
    static Value of(String value) {
        return new TextValue(value, false);
    }

    /**
     * Creates an array Value.
     *
     * @param values the array elements
     * @return an array Value
     */
    static Value ofArray(Value... values) {
        return new ArrayValue(List.of(values), false);
    }

    /**
     * Creates an array Value from a list.
     *
     * @param values the array elements
     * @return an array Value
     */
    static Value ofArray(List<Value> values) {
        return new ArrayValue(List.copyOf(values), false);
    }

    /**
     * Creates an object Value.
     *
     * @param properties the object properties
     * @return an object Value
     */
    static Value ofObject(Map<String, Value> properties) {
        return new ObjectValue(Map.copyOf(properties), false);
    }

    /**
     * Creates an error Value.
     *
     * @param message the error message
     * @return an error Value
     */
    static Value error(String message) {
        return new ErrorValue(message, null, false);
    }

    /**
     * Creates an error Value with a cause.
     *
     * @param message the error message
     * @param cause the causing exception
     * @return an error Value
     */
    static Value error(String message, Throwable cause) {
        return new ErrorValue(message, cause, false);
    }

    /**
     * Creates an error Value from an exception.
     *
     * @param cause the causing exception
     * @return an error Value
     */
    static Value error(Throwable cause) {
        return new ErrorValue(cause.getMessage(), cause, false);
    }

    /**
     * Checks if the Value is marked as secret.
     *
     * @return true if secret, false otherwise
     */
    boolean secret();

    /**
     * Marks the Value as secret. Implementations should use the helper method
     * asSecretHelper to implement this consistently.
     *
     * @return a new Value marked as secret
     */
    Value asSecret();

    /**
     * Helper method to implement asSecret() consistently across all Value types.
     * Eliminates duplication of the "check if already secret" pattern.
     *
     * @param value the current value
     * @param secretCreator function that creates a secret version of the value
     * @param <T> the specific Value type
     * @return the value if already secret, or a new secret version
     */
    static <T extends Value> T asSecretHelper(T value, Function<T, T> secretCreator) {
        return value.secret() ? value : secretCreator.apply(value);
    }

    /**
     * Gets a string describing the type of the Value.
     *
     * @return the type description
     */
    String getValType();

    /**
     * Placeholder for trace information.
     * TODO: Design tracing mechanism without Jackson dependency.
     *
     * @return trace representation (format TBD)
     */
    Object getTrace();

    /**
     * Placeholder for error collection from trace.
     * TODO: Design error collection mechanism.
     *
     * @return collected errors (format TBD)
     */
    Object getErrorsFromTrace();

    /**
     * Utility method to format a toString representation respecting secret flag.
     *
     * @param typeName the name of the type (e.g., "BooleanValue")
     * @param secret whether this value is secret
     * @param valueSupplier supplier that produces the value representation
     * @return formatted string, with SECRET_PLACEHOLDER if secret
     */
    static String formatToString(String typeName, boolean secret, java.util.function.Supplier<String> valueSupplier) {
        if (secret) {
            return typeName + "[" + SECRET_PLACEHOLDER + "]";
        }
        return typeName + "[" + valueSupplier.get() + "]";
    }

    /**
     * Utility method to format a simple toString with no brackets.
     * Used for types where the type name alone is sufficient (e.g., UndefinedValue).
     *
     * @param typeName the name of the type
     * @param secret whether this value is secret
     * @return formatted string
     */
    static String formatToStringSimple(String typeName, boolean secret) {
        return secret ? typeName + "[" + SECRET_PLACEHOLDER + "]" : typeName;
    }
}