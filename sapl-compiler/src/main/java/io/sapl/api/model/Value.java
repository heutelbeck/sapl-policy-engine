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

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Central value type for policy evaluation. Represents defined values (null,
 * boolean, number, text, array, object),
 * error states, or undefined states.
 * <p>
 * Values can be marked as secret to prevent exposure in logs. The secret flag
 * only affects toString() and does not
 * impact equality or evaluation.
 * <p>
 * Creating values:
 *
 * <pre>{@code
 * Value user = Value.of("alice");
 * Value age = Value.of(30);
 * Value active = Value.of(true);
 * Value permissions = Value.ofArray(Value.of("read"), Value.of("write"));
 * Value metadata = Value.ofObject(Map.of("department", Value.of("engineering")));
 * }</pre>
 * <p>
 * Pattern matching for type-safe extraction:
 *
 * <pre>{@code
 * String decision = switch (value) {
 * case BooleanValue(boolean allowed, _) -> allowed ? "PERMIT" : "DENY";
 * case TextValue(String role, _) -> "Role: " + role;
 * case ErrorValue e -> "Error: " + e.message();
 * default -> "INDETERMINATE";
 * };
 * }</pre>
 * <p>
 * Secret values prevent sensitive data exposure:
 *
 * <pre>{@code
 * Value password = Value.of("secret123").asSecret();
 * System.out.println(password); // Prints: ***SECRET***
 *
 * ArrayValue tokens = new ArrayValue(List.of(Value.of("token1")), true);
 * Value extracted = tokens.get(0); // Inherits secret flag
 * }</pre>
 * <p>
 * Errors are values, not exceptions:
 *
 * <pre>{@code
 * Value result = evaluatePolicy();
 * if (result instanceof ErrorValue error) {
 *     log.error("Policy evaluation failed: {}", error.message());
 * }
 *
 * ArrayValue users = getUsers();
 * Value missing = users.get(999); // Returns ErrorValue, not exception
 * }</pre>
 * <p>
 * Collections implement standard Java interfaces:
 *
 * <pre>{@code
 * ArrayValue roles = Value.ofArray(Value.of("admin"), Value.of("user"));
 * for (Value role : roles) {
 *     processRole(role);
 * }
 *
 * ObjectValue attrs = Value.ofObject(Map.of("userId", Value.of("123")));
 * Value userId = attrs.get("userId");
 * }</pre>
 *
 * @see BooleanValue
 * @see NumberValue
 * @see TextValue
 * @see ArrayValue
 * @see ObjectValue
 * @see ErrorValue
 * @see UndefinedValue
 * @see NullValue
 */
public sealed interface Value extends Serializable, CompiledExpression
        permits UndefinedValue, ErrorValue, NullValue, BooleanValue, NumberValue, TextValue, ArrayValue, ObjectValue {

    @Serial
    long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * Placeholder text used when displaying secret values.
     */
    String SECRET_PLACEHOLDER = "***SECRET***";

    /**
     * Singleton for boolean true.
     */
    BooleanValue TRUE = new BooleanValue(true, ValueMetadata.EMPTY);

    /**
     * Singleton for boolean false.
     */
    BooleanValue FALSE = new BooleanValue(false, ValueMetadata.EMPTY);

    /**
     * Singleton for undefined values.
     */
    UndefinedValue UNDEFINED = new UndefinedValue(ValueMetadata.EMPTY);

    /**
     * Singleton for null values.
     */
    NullValue NULL = new NullValue(ValueMetadata.EMPTY);

    /**
     * Constant for numeric zero.
     */
    NumberValue ZERO = new NumberValue(BigDecimal.ZERO, ValueMetadata.EMPTY);

    /**
     * Constant for numeric one.
     */
    NumberValue ONE = new NumberValue(BigDecimal.ONE, ValueMetadata.EMPTY);

    /**
     * Constant for numeric ten.
     */
    NumberValue TEN = new NumberValue(BigDecimal.TEN, ValueMetadata.EMPTY);

    /**
     * Constant for empty array.
     */
    ArrayValue EMPTY_ARRAY = new ArrayValue(List.of(), ValueMetadata.EMPTY);

    /**
     * Constant for empty object.
     */
    ObjectValue EMPTY_OBJECT = new ObjectValue(Map.of(), ValueMetadata.EMPTY);

    /**
     * Constant for empty text.
     */
    TextValue EMPTY_TEXT = new TextValue("", ValueMetadata.EMPTY);

    /**
     * Creates a boolean value.
     *
     * @param value
     * the boolean
     *
     * @return TRUE or FALSE singleton
     */
    static BooleanValue of(boolean value) {
        return value ? TRUE : FALSE;
    }

    /**
     * Creates a number value from a long.
     *
     * @param value
     * the long
     *
     * @return a NumberValue
     */
    static NumberValue of(long value) {
        if (value == 0L)
            return ZERO;
        if (value == 1L)
            return ONE;
        if (value == 10L)
            return TEN;
        return new NumberValue(BigDecimal.valueOf(value), ValueMetadata.EMPTY);
    }

    /**
     * Creates a number value from a double.
     * <p>
     * Note: NaN and infinite values are not supported and will throw
     * IllegalArgumentException. Function libraries
     * should check for these conditions and return ErrorValue explicitly.
     *
     * @param value
     * the double
     *
     * @return a NumberValue
     *
     * @throws IllegalArgumentException
     * if value is NaN or infinite
     */
    static NumberValue of(double value) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException(
                    "Cannot create Value from NaN. Use Value.error() for computation errors.");
        }
        if (Double.isInfinite(value)) {
            throw new IllegalArgumentException("Cannot create Value from infinite double: " + value
                    + ". Use Value.error() for computation errors.");
        }
        if (value == 0.0)
            return ZERO;
        if (value == 1.0)
            return ONE;
        if (value == 10.0)
            return TEN;
        return new NumberValue(BigDecimal.valueOf(value), ValueMetadata.EMPTY);
    }

    /**
     * Creates a number value from a BigDecimal.
     *
     * @param value
     * the BigDecimal (must not be null)
     *
     * @return a NumberValue
     */
    static Value of(@NonNull BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0)
            return ZERO;
        if (value.compareTo(BigDecimal.ONE) == 0)
            return ONE;
        if (value.compareTo(BigDecimal.TEN) == 0)
            return TEN;
        return new NumberValue(value, ValueMetadata.EMPTY);
    }

    /**
     * Creates a text value.
     *
     * @param value
     * the text (must not be null - use Value.NULL for null values)
     *
     * @return a TextValue
     */
    static TextValue of(@NonNull String value) {
        if (value.isEmpty())
            return EMPTY_TEXT;
        return new TextValue(value, ValueMetadata.EMPTY);
    }

    /**
     * Creates an array value from varargs.
     *
     * @param values
     * the elements (must not contain null - use Value.NULL instead)
     *
     * @return an immutable ArrayValue
     */
    static ArrayValue ofArray(Value... values) {
        if (values.length == 0)
            return EMPTY_ARRAY;
        return new ArrayValue(values, ValueMetadata.EMPTY);
    }

    /**
     * Creates an array value from a list.
     *
     * @param values
     * the elements (must not be null or contain null - use Value.NULL instead)
     *
     * @return an immutable ArrayValue
     */
    static ArrayValue ofArray(@NonNull List<Value> values) {
        if (values.isEmpty())
            return EMPTY_ARRAY;
        return new ArrayValue(values, ValueMetadata.EMPTY);
    }

    /**
     * Creates an object value from a map.
     *
     * @param properties
     * the properties (must not be null)
     *
     * @return an immutable ObjectValue
     */
    static ObjectValue ofObject(@NonNull Map<String, Value> properties) {
        if (properties.isEmpty())
            return EMPTY_OBJECT;
        return new ObjectValue(Map.copyOf(properties), ValueMetadata.EMPTY);
    }

    /**
     * Creates an error value with a message.
     *
     * @param message
     * the error message (must not be null)
     *
     * @return an ErrorValue
     */
    static ErrorValue error(@NonNull String message) {
        return new ErrorValue(message, null, ValueMetadata.EMPTY);
    }

    /**
     * Creates an error value with a formatted message.
     * <p>
     * Uses {@link String#format(String, Object...)} to construct the error message
     * from the format string and
     * arguments.
     * <p>
     * Example:
     *
     * <pre>{@code
     * Value error = Value.error("Index %d out of bounds for array of size %d", 5, 3);
     * // Creates error: "Index 5 out of bounds for array of size 3"
     * }</pre>
     *
     * @param message
     * the format string (must not be null)
     * @param args
     * the arguments for the format string
     *
     * @return an ErrorValue with the formatted message
     *
     * @throws IllegalArgumentException
     * if the format string is invalid
     */
    static ErrorValue error(@NonNull String message, Object... args) {
        return new ErrorValue(String.format(message, args), null, ValueMetadata.EMPTY);
    }

    /**
     * Creates an error value with a message and cause.
     *
     * @param message
     * the error message (must not be null)
     * @param cause
     * the exception (may be null)
     *
     * @return an ErrorValue
     */
    static ErrorValue error(@NonNull String message, Throwable cause) {
        return new ErrorValue(message, cause, ValueMetadata.EMPTY);
    }

    /**
     * Creates an error value from an exception.
     *
     * @param cause
     * the exception (must not be null)
     *
     * @return an ErrorValue
     */
    static ErrorValue error(@NonNull Throwable cause) {
        return new ErrorValue(cause, ValueMetadata.EMPTY);
    }

    /**
     * Creates an error value with a message and source location.
     *
     * @param message
     * the error message (must not be null)
     * @param location
     * the source location where the error occurred (may be null)
     *
     * @return an ErrorValue with location information
     */
    static ErrorValue error(@NonNull String message, SourceLocation location) {
        return new ErrorValue(message, null, ValueMetadata.EMPTY, location);
    }

    /**
     * Creates an error value with a formatted message and source location.
     *
     * @param location
     * the source location where the error occurred (may be null)
     * @param message
     * the format string (must not be null)
     * @param args
     * the arguments for the format string
     *
     * @return an ErrorValue with the formatted message and location
     */
    static ErrorValue errorAt(SourceLocation location, @NonNull String message, Object... args) {
        return new ErrorValue(String.format(message, args), null, ValueMetadata.EMPTY, location);
    }

    /**
     * Creates an error value with a message, cause, and source location.
     *
     * @param message
     * the error message (must not be null)
     * @param cause
     * the exception (may be null)
     * @param location
     * the source location where the error occurred (may be null)
     *
     * @return an ErrorValue with cause and location information
     */
    static ErrorValue error(@NonNull String message, Throwable cause, SourceLocation location) {
        return new ErrorValue(message, cause, ValueMetadata.EMPTY, location);
    }

    /**
     * Creates an error value from an exception with source location.
     *
     * @param cause
     * the exception (must not be null)
     * @param location
     * the source location where the error occurred (may be null)
     *
     * @return an ErrorValue with cause and location information
     */
    static ErrorValue error(@NonNull Throwable cause, SourceLocation location) {
        return new ErrorValue(cause.getMessage(), cause, ValueMetadata.EMPTY, location);
    }

    /**
     * Returns the metadata for this value.
     *
     * @return the value metadata
     */
    ValueMetadata metadata();

    /**
     * Creates a copy of this value with the specified metadata.
     *
     * @param metadata the new metadata
     * @return a value with the new metadata
     */
    Value withMetadata(ValueMetadata metadata);

    /**
     * Returns whether this value is marked as secret.
     *
     * @return true if secret
     */
    default boolean isSecret() {
        return metadata().secret();
    }

    /**
     * Marks this value as secret. Secret values display as "***SECRET***" in
     * toString() to prevent exposure. The secret
     * flag does not affect equality or evaluation.
     * <p>
     * Container types propagate the secret flag to extracted elements.
     *
     * @return a secret value (or this instance if already secret)
     */
    default Value asSecret() {
        return isSecret() ? this : withMetadata(metadata().asSecret());
    }
}
