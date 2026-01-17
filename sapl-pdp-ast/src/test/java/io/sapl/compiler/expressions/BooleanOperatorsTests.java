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
package io.sapl.compiler.expressions;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.compiler.operators.BooleanOperators;
import lombok.val;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class BooleanOperatorsTests {

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_not_withBoolean_then_returnsExpected(String description, Value input, Value expected) {
        val actual = BooleanOperators.not(input, null);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_not_withBoolean_then_returnsExpected() {
        return Stream.of(arguments("not true", Value.TRUE, Value.FALSE),
                arguments("not false", Value.FALSE, Value.TRUE));
    }

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_not_withNonBoolean_then_returnsError(String description, Value input) {
        val actual = BooleanOperators.not(input, null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("Logical op requires boolean value");
    }

    private static Stream<Arguments> when_not_withNonBoolean_then_returnsError() {
        return Stream.of(arguments("not number", Value.of(5)), arguments("not string", Value.of("text")),
                arguments("not null", Value.NULL),
                // Error propagation happens in compiler, not in operators
                // Operators only do type checking - ErrorValue is not a BooleanValue
                arguments("not errors", Value.error("original errors")));
    }

    // Note: AND and OR tests are in LazyBooleanOperationCompilerTests
    // because they use cost-stratified short-circuit evaluation

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

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_xor_withNonBoolean_then_returnsError(String description, Value a, Value b) {
        val actual = BooleanOperators.xor(a, b, null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    private static Stream<Arguments> when_xor_withNonBoolean_then_returnsError() {
        return Stream.of(arguments("left is number", Value.of(5), Value.TRUE),
                arguments("right is string", Value.TRUE, Value.of("text")),
                arguments("left is null", Value.NULL, Value.FALSE));
    }

}
