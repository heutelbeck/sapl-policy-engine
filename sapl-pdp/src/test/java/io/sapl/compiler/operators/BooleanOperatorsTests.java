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
package io.sapl.compiler.operators;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class BooleanOperatorsTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_and_then_returnsExpectedValue(String description, Value a, Value b, Value expected) {
        val actual = BooleanOperators.and(null, a, b);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_and_then_returnsExpectedValue() {
        return Stream.of(arguments("true AND true returns true", Value.TRUE, Value.TRUE, Value.TRUE),
                arguments("true AND false returns false", Value.TRUE, Value.FALSE, Value.FALSE),
                arguments("false AND true returns false", Value.FALSE, Value.TRUE, Value.FALSE),
                arguments("false AND false returns false", Value.FALSE, Value.FALSE, Value.FALSE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_and_withTypeMismatch_then_returnsError(String description, Value a, Value b) {
        val actual = BooleanOperators.and(null, a, b);
        assertThat(actual).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                .contains("Boolean operation requires Boolean values");
    }

    private static Stream<Arguments> when_and_withTypeMismatch_then_returnsError() {
        return Stream.of(arguments("left is number", Value.of(5), Value.TRUE),
                arguments("right is number", Value.TRUE, Value.of(5)),
                arguments("left is text", Value.of("text"), Value.TRUE),
                arguments("right is text", Value.TRUE, Value.of("text")),
                arguments("both are numbers", Value.of(1), Value.of(0)),
                arguments("left is null", Value.NULL, Value.TRUE),
                arguments("left is undefined", Value.UNDEFINED, Value.TRUE),
                arguments("left is array", Value.EMPTY_ARRAY, Value.TRUE),
                arguments("left is object", Value.EMPTY_OBJECT, Value.TRUE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_and_withSecrets_then_preservesSecretFlag(String description, Value a, Value b, boolean expectedSecret) {
        val actual = BooleanOperators.and(null, a, b);
        assertThat(actual.isSecret()).isEqualTo(expectedSecret);
    }

    private static Stream<Arguments> when_and_withSecrets_then_preservesSecretFlag() {
        return Stream.of(arguments("both public", Value.TRUE, Value.TRUE, false),
                arguments("left secret", Value.TRUE.asSecret(), Value.TRUE, true),
                arguments("right secret", Value.TRUE, Value.TRUE.asSecret(), true),
                arguments("both secret", Value.TRUE.asSecret(), Value.TRUE.asSecret(), true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_or_then_returnsExpectedValue(String description, Value a, Value b, Value expected) {
        val actual = BooleanOperators.or(null, a, b);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_or_then_returnsExpectedValue() {
        return Stream.of(arguments("true OR true returns true", Value.TRUE, Value.TRUE, Value.TRUE),
                arguments("true OR false returns true", Value.TRUE, Value.FALSE, Value.TRUE),
                arguments("false OR true returns true", Value.FALSE, Value.TRUE, Value.TRUE),
                arguments("false OR false returns false", Value.FALSE, Value.FALSE, Value.FALSE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_or_withTypeMismatch_then_returnsError(String description, Value a, Value b) {
        val actual = BooleanOperators.or(null, a, b);
        assertThat(actual).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                .contains("Boolean operation requires Boolean values");
    }

    private static Stream<Arguments> when_or_withTypeMismatch_then_returnsError() {
        return Stream.of(arguments("left is number", Value.of(5), Value.TRUE),
                arguments("right is number", Value.TRUE, Value.of(5)),
                arguments("left is text", Value.of("text"), Value.TRUE),
                arguments("right is text", Value.TRUE, Value.of("text")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_or_withSecrets_then_preservesSecretFlag(String description, Value a, Value b, boolean expectedSecret) {
        val actual = BooleanOperators.or(null, a, b);
        assertThat(actual.isSecret()).isEqualTo(expectedSecret);
    }

    private static Stream<Arguments> when_or_withSecrets_then_preservesSecretFlag() {
        return Stream.of(arguments("both public", Value.TRUE, Value.FALSE, false),
                arguments("left secret", Value.TRUE.asSecret(), Value.FALSE, true),
                arguments("right secret", Value.TRUE, Value.FALSE.asSecret(), true),
                arguments("both secret", Value.TRUE.asSecret(), Value.FALSE.asSecret(), true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_xor_then_returnsExpectedValue(String description, Value a, Value b, Value expected) {
        val actual = BooleanOperators.xor(null, a, b);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_xor_then_returnsExpectedValue() {
        return Stream.of(arguments("true XOR true returns false", Value.TRUE, Value.TRUE, Value.FALSE),
                arguments("true XOR false returns true", Value.TRUE, Value.FALSE, Value.TRUE),
                arguments("false XOR true returns true", Value.FALSE, Value.TRUE, Value.TRUE),
                arguments("false XOR false returns false", Value.FALSE, Value.FALSE, Value.FALSE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_xor_withTypeMismatch_then_returnsError(String description, Value a, Value b) {
        val actual = BooleanOperators.xor(null, a, b);
        assertThat(actual).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                .contains("Boolean operation requires Boolean values");
    }

    private static Stream<Arguments> when_xor_withTypeMismatch_then_returnsError() {
        return Stream.of(arguments("left is number", Value.of(5), Value.TRUE),
                arguments("right is number", Value.TRUE, Value.of(5)),
                arguments("left is text", Value.of("text"), Value.TRUE),
                arguments("right is text", Value.TRUE, Value.of("text")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_xor_withSecrets_then_preservesSecretFlag(String description, Value a, Value b, boolean expectedSecret) {
        val actual = BooleanOperators.xor(null, a, b);
        assertThat(actual.isSecret()).isEqualTo(expectedSecret);
    }

    private static Stream<Arguments> when_xor_withSecrets_then_preservesSecretFlag() {
        return Stream.of(arguments("both public", Value.TRUE, Value.FALSE, false),
                arguments("left secret", Value.TRUE.asSecret(), Value.FALSE, true),
                arguments("right secret", Value.TRUE, Value.FALSE.asSecret(), true),
                arguments("both secret", Value.TRUE.asSecret(), Value.FALSE.asSecret(), true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_not_then_returnsExpectedValue(String description, Value input, Value expected) {
        val actual = BooleanOperators.not(null, input);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_not_then_returnsExpectedValue() {
        return Stream.of(arguments("NOT true returns false", Value.TRUE, Value.FALSE),
                arguments("NOT false returns true", Value.FALSE, Value.TRUE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_not_withTypeMismatch_then_returnsError(String description, Value input) {
        val actual = BooleanOperators.not(null, input);
        assertThat(actual).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                .contains("Boolean operation requires Boolean values");
    }

    private static Stream<Arguments> when_not_withTypeMismatch_then_returnsError() {
        return Stream.of(arguments("number", Value.of(5)), arguments("text", Value.of("text")),
                arguments("null", Value.NULL), arguments("undefined", Value.UNDEFINED),
                arguments("array", Value.EMPTY_ARRAY), arguments("object", Value.EMPTY_OBJECT));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_not_withSecret_then_preservesSecretFlag(String description, Value input, boolean expectedSecret) {
        val actual = BooleanOperators.not(null, input);
        assertThat(actual.isSecret()).isEqualTo(expectedSecret);
    }

    private static Stream<Arguments> when_not_withSecret_then_preservesSecretFlag() {
        return Stream.of(arguments("public true", Value.TRUE, false),
                arguments("secret true", Value.TRUE.asSecret(), true), arguments("public false", Value.FALSE, false),
                arguments("secret false", Value.FALSE.asSecret(), true));
    }
}
