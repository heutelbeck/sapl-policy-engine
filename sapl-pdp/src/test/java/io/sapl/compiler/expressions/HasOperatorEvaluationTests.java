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

import java.util.Map;
import java.util.stream.Stream;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.sapl.util.SaplTesting.evaluateExpression;
import static io.sapl.util.SaplTesting.testContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("has operator evaluation")
class HasOperatorEvaluationTests {

    private static final Value OBJ_AB = Value.ofJson("""
            {"a": 1, "b": 2}
            """);

    private static final Value EMPTY_OBJ = Value.EMPTY_OBJECT;

    @Nested
    @DisplayName("has (single key)")
    class HasOne {

        @DisplayName("evaluates correctly")
        @ParameterizedTest(name = "{0}")
        @MethodSource
        void whenHasOneThenExpected(String description, String expression, Map<String, Value> vars, Value expected) {
            val ctx    = testContext(vars);
            val result = evaluateExpression(expression, ctx);
            assertThat(result).isEqualTo(expected);
        }

        static Stream<Arguments> whenHasOneThenExpected() {
            return Stream.of(arguments("object has existing key", "x has \"a\"", Map.of("x", OBJ_AB), Value.TRUE),
                    arguments("object has missing key", "x has \"c\"", Map.of("x", OBJ_AB), Value.FALSE),
                    arguments("empty object has key", "x has \"a\"", Map.of("x", EMPTY_OBJ), Value.FALSE),
                    arguments("undefined has key", "x has \"a\"", Map.of("x", Value.UNDEFINED), Value.FALSE),
                    arguments("null has key", "x has \"a\"", Map.of("x", Value.NULL), Value.FALSE),
                    arguments("array has key", "x has \"a\"", Map.of("x", Value.ofJson("[1,2,3]")), Value.FALSE),
                    arguments("string has key", "x has \"a\"", Map.of("x", Value.of("hello")), Value.FALSE),
                    arguments("number has key", "x has \"a\"", Map.of("x", Value.of(42)), Value.FALSE),
                    arguments("boolean has key", "x has \"a\"", Map.of("x", Value.TRUE), Value.FALSE),
                    arguments("obj has undefined key", "x has y", Map.of("x", OBJ_AB, "y", Value.UNDEFINED),
                            Value.FALSE));
        }

        @DisplayName("errors on non-string key")
        @ParameterizedTest(name = "{0}")
        @MethodSource
        void whenHasOneNonStringKeyThenError(String description, String expression, Map<String, Value> vars) {
            val ctx    = testContext(vars);
            val result = evaluateExpression(expression, ctx);
            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        static Stream<Arguments> whenHasOneNonStringKeyThenError() {
            return Stream.of(arguments("number key", "x has 42", Map.of("x", OBJ_AB)),
                    arguments("boolean key", "x has true", Map.of("x", OBJ_AB)),
                    arguments("null key", "x has null", Map.of("x", OBJ_AB)));
        }
    }

    @Nested
    @DisplayName("has any")
    class HasAny {

        @DisplayName("evaluates correctly")
        @ParameterizedTest(name = "{0}")
        @MethodSource
        void whenHasAnyThenExpected(String description, String expression, Map<String, Value> vars, Value expected) {
            val ctx    = testContext(vars);
            val result = evaluateExpression(expression, ctx);
            assertThat(result).isEqualTo(expected);
        }

        static Stream<Arguments> whenHasAnyThenExpected() {
            return Stream.of(
                    arguments("has any with match", "x has any [\"a\", \"c\"]", Map.of("x", OBJ_AB), Value.TRUE),
                    arguments("has any without match", "x has any [\"c\", \"d\"]", Map.of("x", OBJ_AB), Value.FALSE),
                    arguments("has any all match", "x has any [\"a\", \"b\"]", Map.of("x", OBJ_AB), Value.TRUE),
                    arguments("has any empty array", "x has any []", Map.of("x", OBJ_AB), Value.FALSE),
                    arguments("undefined has any", "x has any [\"a\"]", Map.of("x", Value.UNDEFINED), Value.FALSE),
                    arguments("array has any", "x has any [\"a\"]", Map.of("x", Value.ofJson("[1]")), Value.FALSE));
        }

        @DisplayName("errors on non-array keys")
        @ParameterizedTest(name = "{0}")
        @MethodSource
        void whenHasAnyNonArrayThenError(String description, String expression, Map<String, Value> vars) {
            val ctx    = testContext(vars);
            val result = evaluateExpression(expression, ctx);
            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        static Stream<Arguments> whenHasAnyNonArrayThenError() {
            return Stream.of(arguments("string instead of array", "x has any \"a\"", Map.of("x", OBJ_AB)),
                    arguments("number instead of array", "x has any 42", Map.of("x", OBJ_AB)));
        }
    }

    @Nested
    @DisplayName("has all")
    class HasAll {

        @DisplayName("evaluates correctly")
        @ParameterizedTest(name = "{0}")
        @MethodSource
        void whenHasAllThenExpected(String description, String expression, Map<String, Value> vars, Value expected) {
            val ctx    = testContext(vars);
            val result = evaluateExpression(expression, ctx);
            assertThat(result).isEqualTo(expected);
        }

        static Stream<Arguments> whenHasAllThenExpected() {
            return Stream.of(arguments("has all present", "x has all [\"a\", \"b\"]", Map.of("x", OBJ_AB), Value.TRUE),
                    arguments("has all partial", "x has all [\"a\", \"c\"]", Map.of("x", OBJ_AB), Value.FALSE),
                    arguments("has all none", "x has all [\"c\", \"d\"]", Map.of("x", OBJ_AB), Value.FALSE),
                    arguments("has all empty array", "x has all []", Map.of("x", OBJ_AB), Value.TRUE),
                    arguments("undefined has all", "x has all [\"a\"]", Map.of("x", Value.UNDEFINED), Value.FALSE),
                    arguments("array has all", "x has all [\"a\"]", Map.of("x", Value.ofJson("[1]")), Value.FALSE));
        }
    }

}
