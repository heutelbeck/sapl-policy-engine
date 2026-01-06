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
package io.sapl.compiler;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.compiler.operators.BooleanOperators;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class BooleanOperatorsTests {

    @Test
    void when_not_withTrue_then_returnsFalse() {
        val actual = BooleanOperators.not(Value.TRUE, null);
        assertThat(actual).isEqualTo(Value.FALSE);
    }

    @Test
    void when_not_withFalse_then_returnsTrue() {
        val actual = BooleanOperators.not(Value.FALSE, null);
        assertThat(actual).isEqualTo(Value.TRUE);
    }

    @Test
    void when_not_withNonBoolean_then_returnsError() {
        val actual = BooleanOperators.not(Value.of(5), null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("Logical op requires boolean value");
    }

    @Test
    void when_not_withString_then_returnsError() {
        val actual = BooleanOperators.not(Value.of("text"), null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_not_withNull_then_returnsError() {
        val actual = BooleanOperators.not(Value.NULL, null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_not_withError_then_returnsTypeMismatchError() {
        // Error propagation happens in compiler, not in operators
        // Operators only do type checking - ErrorValue is not a BooleanValue
        val error  = Value.error("original error");
        val actual = BooleanOperators.not(error, null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("Logical op requires boolean value");
    }

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_and_withBooleans_then_returnsExpected(String description, Value a, Value b, Value expected) {
        val actual = BooleanOperators.and(a, b, null);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_and_withBooleans_then_returnsExpected() {
        return Stream.of(arguments("true AND true", Value.TRUE, Value.TRUE, Value.TRUE),
                arguments("true AND false", Value.TRUE, Value.FALSE, Value.FALSE),
                arguments("false AND true", Value.FALSE, Value.TRUE, Value.FALSE),
                arguments("false AND false", Value.FALSE, Value.FALSE, Value.FALSE));
    }

    @Test
    void when_and_withLeftNonBoolean_then_returnsError() {
        val actual = BooleanOperators.and(Value.of(5), Value.TRUE, null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("Logical op requires boolean value");
    }

    @Test
    void when_and_withRightNonBoolean_then_returnsError() {
        val actual = BooleanOperators.and(Value.TRUE, Value.of("text"), null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("Logical op requires boolean value");
    }

    @Test
    void when_and_withBothNonBoolean_then_returnsErrorForLeft() {
        val actual = BooleanOperators.and(Value.of(5), Value.of("text"), null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
        // Error for left operand reported first
    }

    @Test
    void when_and_withNull_then_returnsError() {
        val actual = BooleanOperators.and(Value.NULL, Value.TRUE, null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_or_withBooleans_then_returnsExpected(String description, Value a, Value b, Value expected) {
        val actual = BooleanOperators.or(a, b, null);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_or_withBooleans_then_returnsExpected() {
        return Stream.of(arguments("true OR true", Value.TRUE, Value.TRUE, Value.TRUE),
                arguments("true OR false", Value.TRUE, Value.FALSE, Value.TRUE),
                arguments("false OR true", Value.FALSE, Value.TRUE, Value.TRUE),
                arguments("false OR false", Value.FALSE, Value.FALSE, Value.FALSE));
    }

    @Test
    void when_or_withLeftNonBoolean_then_returnsError() {
        val actual = BooleanOperators.or(Value.of(5), Value.TRUE, null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("Logical op requires boolean value");
    }

    @Test
    void when_or_withRightNonBoolean_then_returnsError() {
        val actual = BooleanOperators.or(Value.TRUE, Value.of("text"), null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("Logical op requires boolean value");
    }

    @Test
    void when_or_withNull_then_returnsError() {
        val actual = BooleanOperators.or(Value.TRUE, Value.NULL, null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_xor_withBooleans_then_returnsExpected(String description, Value a, Value b, Value expected) {
        val actual = BooleanOperators.xor(a, b, null);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_xor_withBooleans_then_returnsExpected() {
        return Stream.of(arguments("true XOR true", Value.TRUE, Value.TRUE, Value.FALSE),
                arguments("true XOR false", Value.TRUE, Value.FALSE, Value.TRUE),
                arguments("false XOR true", Value.FALSE, Value.TRUE, Value.TRUE),
                arguments("false XOR false", Value.FALSE, Value.FALSE, Value.FALSE));
    }

    @Test
    void when_xor_withLeftNonBoolean_then_returnsError() {
        val actual = BooleanOperators.xor(Value.of(5), Value.TRUE, null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("Logical op requires boolean value");
    }

    @Test
    void when_xor_withRightNonBoolean_then_returnsError() {
        val actual = BooleanOperators.xor(Value.TRUE, Value.of("text"), null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("Logical op requires boolean value");
    }

    @Test
    void when_xor_withNull_then_returnsError() {
        val actual = BooleanOperators.xor(Value.NULL, Value.FALSE, null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

}
