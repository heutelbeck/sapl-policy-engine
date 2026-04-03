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

import java.util.stream.Stream;

import io.sapl.ast.BinaryOperator;
import io.sapl.ast.BinaryOperatorType;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.sapl.util.SaplTesting.parseExpression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("has operator parsing and AST transformation")
class HasOperatorParsingTests {

    @Nested
    @DisplayName("has expressions parse to BinaryOperator with correct type")
    class Parsing {

        @DisplayName("mode maps to correct BinaryOperatorType")
        @ParameterizedTest(name = "{0}")
        @MethodSource
        void whenHasExpressionThenCorrectOperatorType(String description, String expression,
                BinaryOperatorType expectedType) {
            val ast = parseExpression(expression);
            assertThat(ast).isInstanceOf(BinaryOperator.class).satisfies(node -> {
                val op = (BinaryOperator) node;
                assertThat(op.op()).isEqualTo(expectedType);
            });
        }

        static Stream<Arguments> whenHasExpressionThenCorrectOperatorType() {
            return Stream.of(arguments("has -> HAS_ONE", "subject has \"key\"", BinaryOperatorType.HAS_ONE),
                    arguments("has any -> HAS_ANY", "subject has any [\"a\", \"b\"]", BinaryOperatorType.HAS_ANY),
                    arguments("has all -> HAS_ALL", "subject has all [\"a\", \"b\"]", BinaryOperatorType.HAS_ALL));
        }
    }

    @Nested
    @DisplayName("non-has expressions are unaffected")
    class NonHasExpressions {

        @Test
        @DisplayName("comparison without has passes through")
        void whenComparisonThenNotHasOperator() {
            val ast = parseExpression("subject > 1");
            assertThat(ast).isInstanceOf(BinaryOperator.class)
                    .satisfies(node -> assertThat(((BinaryOperator) node).op()).isEqualTo(BinaryOperatorType.GT));
        }

        @Test
        @DisplayName("equality without has passes through")
        void whenEqualityThenNotHasOperator() {
            val ast = parseExpression("subject == \"test\"");
            assertThat(ast).isInstanceOf(BinaryOperator.class)
                    .satisfies(node -> assertThat(((BinaryOperator) node).op()).isEqualTo(BinaryOperatorType.EQ));
        }

        @Test
        @DisplayName("in operator is not affected")
        void whenInThenNotHasOperator() {
            val ast = parseExpression("\"admin\" in subject.roles");
            assertThat(ast).isInstanceOf(BinaryOperator.class)
                    .satisfies(node -> assertThat(((BinaryOperator) node).op()).isEqualTo(BinaryOperatorType.IN));
        }
    }

}
