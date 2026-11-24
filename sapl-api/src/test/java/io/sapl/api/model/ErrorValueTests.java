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

import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("ErrorValue Tests")
class ErrorValueTests {

    @ParameterizedTest(name = "Constructor: message={0}, cause={1}")
    @MethodSource
    @DisplayName("Constructors create error values correctly")
    void whenConstructorsInvokedThenCreateErrorValueCorrectly(String message, Throwable cause, boolean useCanonical) {
        ErrorValue error;

        if (useCanonical) {
            error = new ErrorValue(message, cause, null);
        } else if (cause != null && message != null) {
            error = new ErrorValue(message, cause);
        } else if (cause != null) {
            error = new ErrorValue(cause);
        } else {
            error = new ErrorValue(message);
        }

        val expectedMessage = message == null ? cause.getMessage() : message;
        assertThat(error).extracting(ErrorValue::message, ErrorValue::cause, ErrorValue::location)
                .containsExactly(expectedMessage, cause, null);
    }

    static Stream<Arguments> whenConstructorsInvokedThenCreateErrorValueCorrectly() {
        val cause = new RuntimeException("cause message");
        return Stream.of(arguments("message", null, true), arguments("message", cause, true),
                arguments("message", null, false), arguments("custom message", cause, false),
                arguments(null, cause, false), arguments("", null, true), arguments("", cause, false));
    }

    @ParameterizedTest(name = "Null message with cause {0} and location {1}")
    @MethodSource
    @DisplayName("Canonical constructor rejects a null message")
    void whenCanonicalMessageIsNullThenRejectsIt(Throwable cause, SourceLocation location) {
        assertThatThrownBy(() -> new ErrorValue(null, cause, location)).isInstanceOf(NullPointerException.class);
    }

    static Stream<Arguments> whenCanonicalMessageIsNullThenRejectsIt() {
        val cause    = new RuntimeException("cause");
        val location = new SourceLocation("policy.sapl", null, 1, 3, 1, 1);
        return Stream.of(arguments(null, null), arguments(cause, null), arguments(null, location),
                arguments(cause, location));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    @DisplayName("An exception without a message becomes an error retaining its cause and origin")
    void whenCauseHasNoMessageThenErrorRetainsCauseAndOrigin(String entry, Function<Throwable, ErrorValue> factory,
            SourceLocation location) {
        val cause = new MessageLessCause();
        val error = factory.apply(cause);

        assertThat(error).extracting(ErrorValue::message, ErrorValue::cause, ErrorValue::location)
                .containsExactly("unknown error", cause, location);
        assertThat(cause.messageReads).isOne();
    }

    static Stream<Arguments> whenCauseHasNoMessageThenErrorRetainsCauseAndOrigin() {
        val location = new SourceLocation("policy.sapl", null, 1, 3, 1, 1);
        return Stream.of(missingMessageCase("Constructor", ErrorValue::new, null),
                missingMessageCase("Factory", Value::error, null),
                missingMessageCase("Factory with location", cause -> Value.error(cause, location), location));
    }

    private static Arguments missingMessageCase(String name, Function<Throwable, ErrorValue> factory,
            SourceLocation location) {
        return arguments(name, factory, location);
    }

    @Test
    @DisplayName("Convenience constructor with null message throws NullPointerException")
    void when_convenienceConstructorCalledWithNullMessage_then_throws() {
        assertThatThrownBy(() -> new ErrorValue((String) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Convenience constructor with null cause throws NullPointerException")
    void when_convenienceConstructorCalledWithNullCause_then_throws() {
        assertThatThrownBy(() -> new ErrorValue((Throwable) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Factory methods create ErrorValue correctly")
    void when_factoryMethodsInvoked_then_createErrorValue() {
        var errorFromMessage = (ErrorValue) Value.error("test");
        var cause            = new RuntimeException("cause");
        var errorFromCause   = (ErrorValue) Value.error(cause);
        var errorFromBoth    = (ErrorValue) Value.error("message", cause);

        assertThat(errorFromMessage.message()).isEqualTo("test");
        assertThat(errorFromMessage.cause()).isNull();

        assertThat(errorFromCause.message()).isEqualTo("cause");
        assertThat(errorFromCause.cause()).isSameAs(cause);

        assertThat(errorFromBoth.message()).isEqualTo("message");
        assertThat(errorFromBoth.cause()).isSameAs(cause);
    }

    @ParameterizedTest(name = "{0} equals {1}: {2}")
    @MethodSource
    @DisplayName("equals() and hashCode() compare by message and cause type")
    void when_equalsAndHashCodeCompared_then_comparesByMessageAndCauseType(ErrorValue error1, ErrorValue error2,
            boolean shouldBeEqual) {
        if (shouldBeEqual) {
            assertThat(error1).isEqualTo(error2).hasSameHashCodeAs(error2);
        } else {
            assertThat(error1).isNotEqualTo(error2);
        }
    }

    static Stream<Arguments> when_equalsAndHashCodeCompared_then_comparesByMessageAndCauseType() {
        return Stream
                .of(arguments(new ErrorValue("msg"), new ErrorValue("msg"), true),
                        arguments(new ErrorValue("msg", new RuntimeException()),
                                new ErrorValue("msg", new RuntimeException()), true),
                        arguments(new ErrorValue("msg1"), new ErrorValue("msg2"), false),
                        arguments(new ErrorValue("msg", new RuntimeException()),
                                new ErrorValue("msg", new IllegalArgumentException()), false),
                        arguments(new ErrorValue("msg", new RuntimeException()), new ErrorValue("msg"), false),
                        arguments(new ErrorValue(new RuntimeException()),
                                new ErrorValue("unknown error", new RuntimeException()), true),
                        arguments(new ErrorValue(new RuntimeException()), new ErrorValue("msg", new RuntimeException()),
                                false));
    }

    @ParameterizedTest(name = "{3}")
    @MethodSource
    @DisplayName("toString() formats appropriately")
    void when_toStringCalled_then_formatsAppropriately(String message, Throwable cause, String testDescription) {
        var error  = new ErrorValue(message, cause, null);
        var result = error.toString();

        assertThat(result).contains(message);

        if (cause != null) {
            assertThat(result).contains(cause.getClass().getSimpleName()).startsWith("ERROR[message=")
                    .contains(", cause=");
        } else {
            assertThat(result).isEqualTo("ERROR[message=\"" + message + "\"]");
        }
    }

    static Stream<Arguments> when_toStringCalled_then_formatsAppropriately() {
        return Stream.of(arguments("test error", null, "message only"),
                arguments("test error", new RuntimeException(), "message and cause"),
                arguments("unknown error", new RuntimeException(), "fallback message with cause"),
                arguments("long error: " + "x".repeat(100), null, "long message"));
    }

    @Test
    @DisplayName("Pattern matching for error recovery works correctly")
    void when_patternMatchingUsedForErrorRecovery_then_matchesCorrectly() {
        Value result = Value.error("Database connection failed");

        var recovery = switch (result) {
        case ErrorValue(String msg, Throwable ignore, SourceLocation ignoreLoc) when msg != null && msg.contains(
                "Database")                                                                                                  ->
            "Retry with backup";
        case ErrorValue(String msg, Throwable ignore, SourceLocation ignoreLoc) when msg != null && msg.contains(
                "Network")                                                                                                   ->
            "Check connectivity";
        case ErrorValue e                                                                                                    ->
            "Generic recovery";
        default                                                                                                              ->
            "No recovery needed";
        };

        assertThat(recovery).isEqualTo("Retry with backup");
    }

    @Test
    @DisplayName("Pattern matching with cause inspection works correctly")
    void when_patternMatchingUsedWithCauseInspection_then_matchesCorrectly() {
        Value result = Value.error("Failed", new IllegalArgumentException());

        var isValidationError = switch (result) {
        case ErrorValue(String ignore, Throwable cause, SourceLocation ignoreLoc) when cause instanceof IllegalArgumentException ->
            true;
        case ErrorValue e                                                                                                        ->
            false;
        default                                                                                                                  ->
            false;
        };

        assertThat(isValidationError).isTrue();
    }

    @Test
    @DisplayName("Constructor with location creates error with metadata location")
    void when_constructorWithLocationUsed_then_createsErrorWithLocation() {
        var location = new SourceLocation("test.sapl", null, 1, 10, 5, 5);
        var error    = new ErrorValue("Test error", null, location);

        assertThat(error.message()).isEqualTo("Test error");
        assertThat(error.location()).isEqualTo(location);
        assertThat(error.toString()).contains("at=").contains("test.sapl");
    }

    @Test
    @DisplayName("Factory with location creates error with metadata location")
    void when_factoryWithLocationUsed_then_createsErrorWithLocation() {
        var location = new SourceLocation("test.sapl", null, 1, 10, 5, 5);
        var error    = Value.error("Test error", location);

        assertThat(error.message()).isEqualTo("Test error");
        assertThat(error.location()).isEqualTo(location);
    }

    @Test
    @DisplayName("ErrorValue is not equal to other value types")
    void when_comparedToOtherValueTypes_then_notEqual() {
        var error = Value.error("error");

        assertThat(error).isNotEqualTo(Value.of("error")).isNotEqualTo(Value.NULL).isNotEqualTo(Value.UNDEFINED);
    }

    private static final class MessageLessCause extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private int messageReads;

        @Override
        public String getMessage() {
            messageReads++;
            return null;
        }
    }
}
