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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ErrorValueTests {

    @ParameterizedTest(name = "Constructor: message={0}, cause={1}, secret={2}")
    @MethodSource
    void when_constructorsInvoked_then_createErrorValueCorrectly(String message, Throwable cause, boolean secret,
            boolean useCanonical) {
        ErrorValue error;
        var        metadata = secret ? ValueMetadata.SECRET_EMPTY : ValueMetadata.EMPTY;

        if (useCanonical) {
            // Use canonical 4-arg constructor which allows null message
            error = new ErrorValue(message, cause, metadata, null);
        } else if (cause != null && message != null) {
            error = secret ? new ErrorValue(cause, ValueMetadata.SECRET_EMPTY) : new ErrorValue(cause);
        } else if (cause != null) {
            error = secret ? new ErrorValue(cause, ValueMetadata.SECRET_EMPTY) : new ErrorValue(cause);
        } else {
            error = secret ? new ErrorValue(message, ValueMetadata.SECRET_EMPTY) : new ErrorValue(message);
        }

        // Canonical constructor preserves the provided message.
        // Non-canonical constructor with cause extracts message from cause.
        // Non-canonical constructor without cause uses the provided message.
        var expectedMessage = useCanonical ? message : (cause != null ? cause.getMessage() : message);
        assertThat(error.message()).isEqualTo(expectedMessage);
        assertThat(error.cause()).isSameAs(cause);
        assertThat(error.isSecret()).isEqualTo(secret);
    }

    static Stream<Arguments> when_constructorsInvoked_then_createErrorValueCorrectly() {
        var cause = new RuntimeException("cause message");
        return Stream.of(arguments("message", null, false, true), arguments("message", null, true, true),
                arguments("message", cause, false, true), arguments("message", cause, true, true),
                arguments(null, cause, false, true), arguments(null, cause, true, true),
                arguments("message", null, false, false), arguments("message", null, true, false),
                arguments("cause message", cause, false, false), arguments("cause message", cause, true, false));
    }

    @Test
    void when_canonicalConstructorCalledWithNullMessage_then_allowsNull() {
        var error = new ErrorValue(null, new RuntimeException(), ValueMetadata.EMPTY, null);

        assertThat(error.message()).isNull();
    }

    @Test
    void when_convenienceConstructorCalledWithNullMessage_then_throws() {
        assertThatThrownBy(() -> new ErrorValue((String) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void when_convenienceConstructorCalledWithNullCause_then_throws() {
        assertThatThrownBy(() -> new ErrorValue((Throwable) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void when_factoryMethodsInvoked_then_createErrorValue() {
        var errorFromMessage = (ErrorValue) Value.error("test");
        var cause            = new RuntimeException("cause");
        var errorFromCause   = (ErrorValue) Value.error(cause);
        var errorFromBoth    = (ErrorValue) Value.error("message", cause);

        assertThat(errorFromMessage.message()).isEqualTo("test");
        assertThat(errorFromMessage.cause()).isNull();
        assertThat(errorFromMessage.isSecret()).isFalse();

        assertThat(errorFromCause.message()).isEqualTo("cause");
        assertThat(errorFromCause.cause()).isSameAs(cause);
        assertThat(errorFromCause.isSecret()).isFalse();

        assertThat(errorFromBoth.message()).isEqualTo("message");
        assertThat(errorFromBoth.cause()).isSameAs(cause);
        assertThat(errorFromBoth.isSecret()).isFalse();
    }

    @Test
    void when_asSecretCalled_then_createsSecretCopyOrReturnsSameInstance() {
        var cause         = new RuntimeException();
        var original      = new ErrorValue("message", cause, ValueMetadata.EMPTY);
        var alreadySecret = new ErrorValue("message", cause, ValueMetadata.SECRET_EMPTY);

        var secretCopy = (ErrorValue) original.asSecret();
        assertThat(secretCopy.isSecret()).isTrue();
        assertThat(secretCopy.message()).isEqualTo("message");
        assertThat(secretCopy.cause()).isSameAs(cause);
        assertThat(alreadySecret.asSecret()).isSameAs(alreadySecret);
    }

    @ParameterizedTest(name = "{0} equals {1}: {2}")
    @MethodSource
    void when_equalsAndHashCodeCompared_then_comparesByMessageAndCauseType(ErrorValue error1, ErrorValue error2,
            boolean shouldBeEqual) {
        if (shouldBeEqual) {
            assertThat(error1).isEqualTo(error2).hasSameHashCodeAs(error2);
        } else {
            assertThat(error1).isNotEqualTo(error2);
        }
    }

    static Stream<Arguments> when_equalsAndHashCodeCompared_then_comparesByMessageAndCauseType() {
        return Stream.of(
                arguments(new ErrorValue("msg", ValueMetadata.EMPTY), new ErrorValue("msg", ValueMetadata.SECRET_EMPTY),
                        true),
                arguments(new ErrorValue("msg", new RuntimeException(), ValueMetadata.EMPTY),
                        new ErrorValue("msg", new RuntimeException(), ValueMetadata.EMPTY), true),
                arguments(new ErrorValue("msg1", ValueMetadata.EMPTY), new ErrorValue("msg2", ValueMetadata.EMPTY),
                        false),
                arguments(new ErrorValue("msg", new RuntimeException(), ValueMetadata.EMPTY),
                        new ErrorValue("msg", new IllegalArgumentException(), ValueMetadata.EMPTY), false),
                arguments(new ErrorValue("msg", new RuntimeException(), ValueMetadata.EMPTY),
                        new ErrorValue("msg", ValueMetadata.EMPTY), false),
                // Use canonical 4-arg constructor for null message test cases
                arguments(new ErrorValue(null, new RuntimeException(), ValueMetadata.EMPTY, null),
                        new ErrorValue(null, new RuntimeException(), ValueMetadata.EMPTY, null), true),
                arguments(new ErrorValue(null, new RuntimeException(), ValueMetadata.EMPTY, null),
                        new ErrorValue("msg", new RuntimeException(), ValueMetadata.EMPTY), false));
    }

    @ParameterizedTest(name = "{3}")
    @MethodSource
    void when_toStringCalled_then_formatsAppropriately(String message, Throwable cause, boolean secret,
            String testDescription) {
        // Use canonical 4-arg constructor to allow null message in test cases
        var metadata = secret ? ValueMetadata.SECRET_EMPTY : ValueMetadata.EMPTY;
        var error    = new ErrorValue(message, cause, metadata, null);
        var result   = error.toString();

        if (secret) {
            assertThat(result).isEqualTo("***SECRET***");
        } else {
            var expectedMessage = message != null ? message : "unknown error";
            assertThat(result).contains(expectedMessage);

            if (cause != null) {
                assertThat(result).contains(cause.getClass().getSimpleName()).startsWith("ERROR[message=")
                        .contains(", cause=");
            } else {
                assertThat(result).isEqualTo("ERROR[message=\"" + expectedMessage + "\"]");
            }
        }
    }

    static Stream<Arguments> when_toStringCalled_then_formatsAppropriately() {
        return Stream.of(arguments("test error", null, false, "message only"),
                arguments("test error", new RuntimeException(), false, "message and cause"),
                arguments("secret error", null, true, "secret"),
                arguments(null, new RuntimeException(), false, "null message with cause"),
                arguments("long error: " + "x".repeat(100), null, false, "long message"));
    }

    @Test
    void when_patternMatchingUsedForErrorRecovery_then_matchesCorrectly() {
        Value result = Value.error("Database connection failed");

        var recovery = switch (result) {
        case ErrorValue(String msg, Throwable ignore, ValueMetadata ignoreMeta, SourceLocation ignoreLoc) when msg != null
                && msg.contains(
                        "Database")                                                                                                                    ->
            "Retry with backup";
        case ErrorValue(String msg, Throwable ignore, ValueMetadata ignoreMeta, SourceLocation ignoreLoc) when msg != null
                && msg.contains(
                        "Network")                                                                                                                     ->
            "Check connectivity";
        case ErrorValue e                                                                                                                              ->
            "Generic recovery";
        default                                                                                                                                        ->
            "No recovery needed";
        };

        assertThat(recovery).isEqualTo("Retry with backup");
    }

    @Test
    void when_patternMatchingUsedWithCauseInspection_then_matchesCorrectly() {
        Value result = Value.error("Failed", new IllegalArgumentException());

        var isValidationError = switch (result) {
        case ErrorValue(String ignore, Throwable cause, ValueMetadata ignoreMeta, SourceLocation ignoreLoc) when cause instanceof IllegalArgumentException ->
            true;
        case ErrorValue e                                                                                                                                  ->
            false;
        default                                                                                                                                            ->
            false;
        };

        assertThat(isValidationError).isTrue();
    }

}
