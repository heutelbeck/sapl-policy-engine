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
import io.sapl.ast.BinaryOperator;
import io.sapl.ast.BinaryOperatorType;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.sapl.util.SaplTesting.evaluateExpression;
import static io.sapl.util.SaplTesting.parseExpression;
import static io.sapl.util.SaplTesting.testContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("any in / all in operators")
class AnyAllInOperatorTests {

    private static final Value ROLES = Value.ofJson("""
            ["admin", "editor", "viewer"]
            """);

    @Nested
    @DisplayName("parsing")
    class Parsing {

        @DisplayName("parses to correct BinaryOperatorType")
        @ParameterizedTest(name = "{0}")
        @MethodSource
        void whenAnyAllInThenCorrectType(String description, String expression, BinaryOperatorType expectedType) {
            val ast = parseExpression(expression);
            assertThat(ast).isInstanceOf(BinaryOperator.class)
                    .satisfies(node -> assertThat(((BinaryOperator) node).op()).isEqualTo(expectedType));
        }

        static Stream<Arguments> whenAnyAllInThenCorrectType() {
            return Stream.of(arguments("plain in", "\"admin\" in subject", BinaryOperatorType.IN),
                    arguments("any in", "[\"admin\"] any in subject", BinaryOperatorType.ANY_IN),
                    arguments("all in", "[\"admin\"] all in subject", BinaryOperatorType.ALL_IN));
        }
    }

    @Nested
    @DisplayName("any in evaluation")
    class AnyIn {

        @DisplayName("evaluates correctly")
        @ParameterizedTest(name = "{0}")
        @MethodSource
        void whenAnyInThenExpected(String description, String expression, Map<String, Value> vars, Value expected) {
            val result = evaluateExpression(expression, testContext(vars));
            assertThat(result).isEqualTo(expected);
        }

        static Stream<Arguments> whenAnyInThenExpected() {
            return Stream.of(arguments("some match", "[\"admin\", \"guest\"] any in x", Map.of("x", ROLES), Value.TRUE),
                    arguments("no match", "[\"guest\", \"user\"] any in x", Map.of("x", ROLES), Value.FALSE),
                    arguments("all match", "[\"admin\", \"editor\"] any in x", Map.of("x", ROLES), Value.TRUE),
                    arguments("empty needles", "[] any in x", Map.of("x", ROLES), Value.FALSE));
        }

        @DisplayName("errors on non-array LHS")
        @ParameterizedTest(name = "{0}")
        @MethodSource
        void whenAnyInNonArrayLhsThenError(String description, String expression, Map<String, Value> vars) {
            val result = evaluateExpression(expression, testContext(vars));
            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        static Stream<Arguments> whenAnyInNonArrayLhsThenError() {
            return Stream.of(arguments("string lhs", "\"admin\" any in x", Map.of("x", ROLES)),
                    arguments("number lhs", "42 any in x", Map.of("x", ROLES)));
        }
    }

    @Nested
    @DisplayName("all in evaluation")
    class AllIn {

        @DisplayName("evaluates correctly")
        @ParameterizedTest(name = "{0}")
        @MethodSource
        void whenAllInThenExpected(String description, String expression, Map<String, Value> vars, Value expected) {
            val result = evaluateExpression(expression, testContext(vars));
            assertThat(result).isEqualTo(expected);
        }

        static Stream<Arguments> whenAllInThenExpected() {
            return Stream.of(
                    arguments("all present", "[\"admin\", \"editor\"] all in x", Map.of("x", ROLES), Value.TRUE),
                    arguments("partial", "[\"admin\", \"guest\"] all in x", Map.of("x", ROLES), Value.FALSE),
                    arguments("none present", "[\"guest\", \"user\"] all in x", Map.of("x", ROLES), Value.FALSE),
                    arguments("empty needles", "[] all in x", Map.of("x", ROLES), Value.TRUE));
        }
    }

}
