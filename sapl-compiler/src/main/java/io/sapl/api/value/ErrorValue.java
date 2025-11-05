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
package io.sapl.api.value;

import io.sapl.api.SaplVersion;
import lombok.NonNull;
import lombok.val;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;
import java.util.Objects;

/**
 * Error value representing a failure during policy evaluation.
 * Errors are values, not exceptions, enabling functional error handling.
 */
public record ErrorValue(String message, Throwable cause, boolean secret) implements Value {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * Creates an error from an exception.
     *
     * @param cause the exception (must not be null)
     * @param secret whether secret
     */
    public ErrorValue(@NonNull Throwable cause, boolean secret) {
        this(cause.getMessage(), cause, secret);
    }

    /**
     * Creates an error from an exception (not secret).
     *
     * @param cause the exception (must not be null)
     */
    public ErrorValue(@NonNull Throwable cause) {
        this(cause.getMessage(), cause, false);
    }

    /**
     * Creates an error with a message.
     *
     * @param message the error message (must not be null)
     * @param secret whether secret
     */
    public ErrorValue(@NonNull String message, boolean secret) {
        this(message, null, secret);
    }

    /**
     * Creates an error with a message (not secret).
     *
     * @param message the error message (must not be null)
     */
    public ErrorValue(@NonNull String message) {
        this(message, null, false);
    }

    @Override
    public Value asSecret() {
        return secret ? this : new ErrorValue(message, cause, true);
    }

    @Override
    public @NotNull String toString() {
        if (secret) {
            return SECRET_PLACEHOLDER;
        }
        val printMessage = message == null ? "unknown error" : message;
        if (cause != null) {
            return "ERROR[message=\"" + printMessage + "\", cause=" + cause.getClass().getSimpleName() + "]";
        }
        return "ERROR[message=\"" + printMessage + "\"]";
    }

    @Override
    public boolean equals(Object that) {
        if (this == that)
            return true;
        if (!(that instanceof ErrorValue thatError))
            return false;
        // Equality based on message and cause type (not cause instance).
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
