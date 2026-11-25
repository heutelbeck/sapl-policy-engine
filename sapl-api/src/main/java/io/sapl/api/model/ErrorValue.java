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
import java.util.Objects;

import static java.util.Objects.requireNonNullElse;

/**
 * Error value representing a failure during policy evaluation. Errors are
 * values, not exceptions, enabling functional
 * error handling.
 * <p>
 * Errors can optionally carry a {@link SourceLocation} indicating where in the
 * SAPL metadata code the error occurred.
 * This is invaluable for debugging policy evaluation failures.
 *
 * @param message
 * the error message describing what went wrong (must not be null)
 * @param cause
 * the underlying exception, if any (may be null)
 * @param location
 * the metadata location where the error occurred (may be null)
 */
public record ErrorValue(@NonNull String message, Throwable cause, SourceLocation location) implements Value {

    private static final String ERROR_UNKNOWN = "unknown error";

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * Creates an error with message, cause, and metadata but no location.
     *
     * @param message
     * the error message (must not be null)
     * @param cause
     * the exception (may be null)
     * @throws NullPointerException if message is null
     */
    public ErrorValue(@NonNull String message, Throwable cause) {
        this(message, cause, null);
    }

    /**
     * Creates an error from an exception with no location. An exception without a
     * message uses the text "unknown error".
     *
     * @param cause
     * the exception (must not be null)
     * @throws NullPointerException if cause is null
     */
    public ErrorValue(@NonNull Throwable cause) {
        this(cause, null);
    }

    ErrorValue(@NonNull Throwable cause, SourceLocation location) {
        this(requireNonNullElse(cause.getMessage(), ERROR_UNKNOWN), cause, location);
    }

    /**
     * Creates an error with a message only, empty metadata, no cause, no location.
     *
     * @param message
     * the error message (must not be null)
     * @throws NullPointerException if message is null
     */
    public ErrorValue(@NonNull String message) {
        this(message, null, null);
    }

    @Override
    public @NotNull String toString() {
        val result = new StringBuilder("ERROR[message=\"").append(message).append('"');
        if (cause != null) {
            result.append(", cause=").append(cause.getClass().getSimpleName());
        }
        if (location != null) {
            result.append(", at=").append(location);
        }
        result.append(']');
        return result.toString();
    }

    @Override
    public boolean equals(Object that) {
        if (this == that)
            return true;
        if (!(that instanceof ErrorValue thatError))
            return false;
        // Equality based on message and cause type (not cause instance or location).
        // Location is diagnostic information and should not affect equality.
        return Objects.equals(message, thatError.message) && Objects.equals(getCauseClass(), thatError.getCauseClass());
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, getCauseClass());
    }

    private Class<?> getCauseClass() {
        return cause == null ? null : cause.getClass();
    }
}
