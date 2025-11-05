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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ErrorValue Tests")
class ErrorValueTests {

    @ParameterizedTest(name = "Constructor: message={0}, cause={1}, secret={2}")
    @MethodSource("provideConstructorCases")
    @DisplayName("Constructors create ErrorValue correctly")
    void constructorsCreateValue(String message, Throwable cause, boolean secret, boolean useCanonical) {
        ErrorValue error;

        if (useCanonical) {
            error = new ErrorValue(message, cause, secret);
        } else if (cause != null && message != null) {
            error = secret ? new ErrorValue(cause, true) : new ErrorValue(cause);
        } else if (cause != null) {
            error = secret ? new ErrorValue(cause, true) : new ErrorValue(cause);
        } else {
            error = secret ? new ErrorValue(message, true) : new ErrorValue(message);
        }

        // Canonical constructor preserves the provided message.
        // Non-canonical constructor with cause extracts message from cause.
        // Non-canonical constructor without cause uses the provided message.
        var expectedMessage = useCanonical ? message : (cause != null ? cause.getMessage() : message);
        assertThat(error.message()).isEqualTo(expectedMessage);
        assertThat(error.cause()).isSameAs(cause);
        assertThat(error.secret()).isEqualTo(secret);
    }

    @Test
    @DisplayName("Constructor with null message in canonical form allows null")
    void canonicalConstructorAllowsNullMessage() {
        var error = new ErrorValue(null, new RuntimeException(), false);

        assertThat(error.message()).isNull();
    }

    @Test
    @DisplayName("Convenience constructor with null message throws NullPointerException")
    void convenienceConstructorNullMessageThrows() {
        assertThatThrownBy(() -> new ErrorValue((String) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Convenience constructor with null cause throws NullPointerException")
    void convenienceConstructorNullCauseThrows() {
        assertThatThrownBy(() -> new ErrorValue((Throwable) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Value.error() factory methods create ErrorValue")
    void factoryMethodsCreateErrorValue() {
        var errorFromMessage = (ErrorValue) Value.error("test");
        var cause = new RuntimeException("cause");
        var errorFromCause = (ErrorValue) Value.error(cause);
        var errorFromBoth = (ErrorValue) Value.error("message", cause);

        assertThat(errorFromMessage.message()).isEqualTo("test");
        assertThat(errorFromMessage.cause()).isNull();
        assertThat(errorFromMessage.secret()).isFalse();

        assertThat(errorFromCause.message()).isEqualTo("cause");
        assertThat(errorFromCause.cause()).isSameAs(cause);
        assertThat(errorFromCause.secret()).isFalse();

        assertThat(errorFromBoth.message()).isEqualTo("message");
        assertThat(errorFromBoth.cause()).isSameAs(cause);
        assertThat(errorFromBoth.secret()).isFalse();
    }

    @Test
    @DisplayName("asSecret() creates secret copy or returns same instance")
    void asSecretBehavior() {
        var cause = new RuntimeException();
        var original = new ErrorValue("message", cause, false);
        var alreadySecret = new ErrorValue("message", cause, true);

        var secretCopy = (ErrorValue) original.asSecret();
        assertThat(secretCopy.secret()).isTrue();
        assertThat(secretCopy.message()).isEqualTo("message");
        assertThat(secretCopy.cause()).isSameAs(cause);
        assertThat(alreadySecret.asSecret()).isSameAs(alreadySecret);
    }

    @ParameterizedTest(name = "{0} equals {1}: {2}")
    @MethodSource("provideEqualityCases")
    @DisplayName("equals() and hashCode() compare by message and cause type, ignoring secret flag and cause instance")
    void equalsAndHashCode(ErrorValue error1, ErrorValue error2, boolean shouldBeEqual) {
        if (shouldBeEqual) {
            assertThat(error1)
                    .isEqualTo(error2)
                    .hasSameHashCodeAs(error2);
        } else {
            assertThat(error1).isNotEqualTo(error2);
        }
    }

    @ParameterizedTest(name = "{3}")
    @MethodSource("provideToStringCases")
    @DisplayName("toString() formats appropriately")
    void toStringFormatting(String message, Throwable cause, boolean secret, String testDescription) {
        var error = new ErrorValue(message, cause, secret);
        var result = error.toString();

        if (secret) {
            assertThat(result).isEqualTo("***SECRET***");
        } else {
            var expectedMessage = message != null ? message : "unknown error";
            assertThat(result).contains(expectedMessage);

            if (cause != null) {
                assertThat(result)
                        .contains(cause.getClass().getSimpleName())
                        .startsWith("ERROR[message=")
                        .contains(", cause=");
            } else {
                assertThat(result).isEqualTo("ERROR[message=\"" + expectedMessage + "\"]");
            }
        }
    }

    @Test
    @DisplayName("Pattern matching for error recovery")
    void patternMatchingErrorRecovery() {
        Value result = Value.error("Database connection failed");

        var recovery = switch (result) {
            case ErrorValue(String msg, Throwable ignore, boolean ignoreSecret) when msg != null && msg.contains("Database") ->
                "Retry with backup";
            case ErrorValue(String msg, Throwable ignore, boolean ignoreSecret) when msg != null && msg.contains("Network") ->
                "Check connectivity";
            case ErrorValue e -> "Generic recovery";
            default -> "No recovery needed";
        };

        assertThat(recovery).isEqualTo("Retry with backup");
    }

    @Test
    @DisplayName("Pattern matching with cause inspection")
    void patternMatchingWithCause() {
        Value result = Value.error("Failed", new IllegalArgumentException());

        var isValidationError = switch (result) {
            case ErrorValue(String ignore, Throwable cause, boolean ignoreToo) when cause instanceof IllegalArgumentException ->
                true;
            case ErrorValue e -> false;
            default -> false;
        };

        assertThat(isValidationError).isTrue();
    }

    static Stream<Arguments> provideConstructorCases() {
        var cause = new RuntimeException("cause message");
        return Stream.of(
            Arguments.of("message", null, false, true),
            Arguments.of("message", null, true, true),
            Arguments.of("message", cause, false, true),
            Arguments.of("message", cause, true, true),
            Arguments.of(null, cause, false, true),
            Arguments.of(null, cause, true, true),
            Arguments.of("message", null, false, false),
            Arguments.of("message", null, true, false),
            Arguments.of("cause message", cause, false, false),
            Arguments.of("cause message", cause, true, false)
        );
    }

    static Stream<Arguments> provideEqualityCases() {
        return Stream.of(
            Arguments.of(
                new ErrorValue("msg", false),
                new ErrorValue("msg", true),
                true
            ),
            Arguments.of(
                new ErrorValue("msg", new RuntimeException(), false),
                new ErrorValue("msg", new RuntimeException(), false),
                true
            ),
            Arguments.of(
                new ErrorValue("msg1", false),
                new ErrorValue("msg2", false),
                false
            ),
            Arguments.of(
                new ErrorValue("msg", new RuntimeException(), false),
                new ErrorValue("msg", new IllegalArgumentException(), false),
                false
            ),
            Arguments.of(
                new ErrorValue("msg", new RuntimeException(), false),
                new ErrorValue("msg", false),
                false
            ),
            Arguments.of(
                new ErrorValue(null, new RuntimeException(), false),
                new ErrorValue(null, new RuntimeException(), false),
                true
            ),
            Arguments.of(
                new ErrorValue(null, new RuntimeException(), false),
                new ErrorValue("msg", new RuntimeException(), false),
                false
            )
        );
    }

    static Stream<Arguments> provideToStringCases() {
        return Stream.of(
            Arguments.of("test error", null, false, "message only"),
            Arguments.of("test error", new RuntimeException(), false, "message and cause"),
            Arguments.of("secret error", null, true, "secret"),
            Arguments.of(null, new RuntimeException(), false, "null message with cause"),
            Arguments.of("long error: " + "x".repeat(100), null, false, "long message")
        );
    }
}