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
package io.sapl.compiler;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.sapl.util.TestUtil.assertCompiledExpressionEvaluatesTo;
import static io.sapl.util.TestUtil.assertExpressionCompilesToValue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Tests for compile-time constant folding and pure expression compilation in
 * ExpressionCompiler. Verifies that constants are folded at compile time and
 * that expressions requiring runtime evaluation compile to appropriate
 * representations.
 */
class ExpressionCompilerTests {

    @ParameterizedTest
    @MethodSource
    void expressionsThatCompileToConstants(String expression, Value expected) {
        assertExpressionCompilesToValue(expression, expected);
    }

    private static Stream<Arguments> expressionsThatCompileToConstants() {
        return Stream.of(
                // Literals fold to constants
                arguments("true", Value.TRUE), arguments("false", Value.FALSE), arguments("null", Value.NULL),
                arguments("undefined", Value.UNDEFINED), arguments("42", Value.of(42)), arguments("-17", Value.of(-17)),
                arguments("3.14159", Value.of(3.14159)), arguments("\"Stormbringer\"", Value.of("Stormbringer")),
                arguments("\"\"", Value.EMPTY_TEXT),

                // Empty collections
                arguments("[]", Value.EMPTY_ARRAY), arguments("{}", Value.EMPTY_OBJECT),

                // Simple arrays with constant elements
                arguments("[1, 2, 3]", Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
                arguments("[true, false, null]", Value.ofArray(Value.TRUE, Value.FALSE, Value.NULL)),
                arguments("[\"Elric\", \"Moonglum\", \"Dyvim Tvar\"]",
                        Value.ofArray(Value.of("Elric"), Value.of("Moonglum"), Value.of("Dyvim Tvar"))),

                // Simple objects with constant properties
                arguments("{\"lord\": \"Arioch\"}", ObjectValue.builder().put("lord", Value.of("Arioch")).build()),
                arguments("{\"emperor\": \"Elric\", \"city\": \"Imrryr\"}",
                        ObjectValue.builder().put("emperor", Value.of("Elric")).put("city", Value.of("Imrryr"))
                                .build()),
                arguments("{\"clearance\": 3, \"authorized\": true}",
                        ObjectValue.builder().put("clearance", Value.of(3)).put("authorized", Value.TRUE).build()),

                // Nested structures with constants
                arguments("{\"sword\": {\"name\": \"Stormbringer\", \"souls\": 999}}",
                        ObjectValue.builder()
                                .put("sword",
                                        ObjectValue.builder().put("name", Value.of("Stormbringer"))
                                                .put("souls", Value.of(999)).build())
                                .build()),
                arguments("[[1, 2], [3, 4], [5, 6]]",
                        Value.ofArray(Value.ofArray(Value.of(1), Value.of(2)), Value.ofArray(Value.of(3), Value.of(4)),
                                Value.ofArray(Value.of(5), Value.of(6)))),

                // Arithmetic operations with constants fold
                arguments("2 + 3", Value.of(5)), arguments("10 - 7", Value.of(3)), arguments("6 * 7", Value.of(42)),
                arguments("100 / 4", Value.of(25)), arguments("17 % 5", Value.of(2)),
                arguments("2 + 3 * 4", Value.of(14)), arguments("(2 + 3) * 4", Value.of(20)),
                arguments("-5", Value.of(-5)), arguments("+42", Value.of(42)),

                // Boolean operations with constants fold
                arguments("true && true", Value.TRUE), arguments("true && false", Value.FALSE),
                arguments("false && true", Value.FALSE), arguments("false && false", Value.FALSE),
                arguments("true || false", Value.TRUE), arguments("false || false", Value.FALSE),
                arguments("true ^ false", Value.TRUE), arguments("true ^ true", Value.FALSE),
                arguments("!true", Value.FALSE), arguments("!false", Value.TRUE),
                arguments("true & false", Value.FALSE), arguments("true | false", Value.TRUE),

                // Comparison operations with constants fold
                arguments("5 > 3", Value.TRUE), arguments("3 > 5", Value.FALSE), arguments("5 >= 5", Value.TRUE),
                arguments("3 < 5", Value.TRUE), arguments("5 < 3", Value.FALSE), arguments("5 <= 5", Value.TRUE),
                arguments("42 == 42", Value.TRUE), arguments("42 == 17", Value.FALSE),
                arguments("42 != 17", Value.TRUE), arguments("42 != 42", Value.FALSE),
                arguments("\"Imrryr\" == \"Imrryr\"", Value.TRUE), arguments("\"Imrryr\" != \"Tanelorn\"", Value.TRUE),
                arguments("true == true", Value.TRUE), arguments("null == null", Value.TRUE),

                // Element membership with constants fold
                arguments("3 in [1, 2, 3, 4]", Value.TRUE), arguments("5 in [1, 2, 3, 4]", Value.FALSE),
                arguments("\"read\" in [\"read\", \"write\", \"delete\"]", Value.TRUE),

                // Complex expressions combining multiple operations
                arguments("(5 > 3) && (10 < 20)", Value.TRUE), arguments("(5 + 3) * 2 == 16", Value.TRUE),
                arguments("(true || false) && (3 < 5)", Value.TRUE), arguments("100 / (2 + 3) * 2", Value.of(40)),

                // Key step access on constant objects
                arguments("{\"weapon\": \"Stormbringer\"}.weapon", Value.of("Stormbringer")),
                arguments("{\"dragons\": 13, \"city\": \"Imrryr\"}.dragons", Value.of(13)),
                arguments("{\"outer\": {\"inner\": \"value\"}}.outer",
                        ObjectValue.builder().put("inner", Value.of("value")).build()),
                arguments("{\"outer\": {\"inner\": \"deep\"}}.outer.inner", Value.of("deep")),

                // Escaped key step access
                arguments("{\"with-dash\": \"value\"}[\"with-dash\"]", Value.of("value")),
                arguments("{\"key.with.dots\": 42}[\"key.with.dots\"]", Value.of(42)),

                // Index step access on constant arrays
                arguments("[10, 20, 30, 40][0]", Value.of(10)), arguments("[10, 20, 30, 40][2]", Value.of(30)),
                arguments("[10, 20, 30, 40][3]", Value.of(40)),
                arguments("[\"Elric\", \"Moonglum\", \"Cymoril\"][1]", Value.of("Moonglum")),
                arguments("[[1, 2], [3, 4]][0][1]", Value.of(2)),

                // Array slicing on constants
                arguments("[10, 20, 30, 40, 50][1:3]", Value.ofArray(Value.of(20), Value.of(30))),
                arguments("[10, 20, 30, 40, 50][: :2]", Value.ofArray(Value.of(10), Value.of(30), Value.of(50))),
                arguments("[10, 20, 30, 40, 50][:3]", Value.ofArray(Value.of(10), Value.of(20), Value.of(30))),
                arguments("[10, 20, 30, 40, 50][2:]", Value.ofArray(Value.of(30), Value.of(40), Value.of(50))),

                // Wildcard on constant structures
                arguments("[1, 2, 3].*", Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
                arguments("{\"a\": 1, \"b\": 2}.*", ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).build()),

                // Condition step on constant arrays
                arguments("[1, 2, 3, 4, 5][?(@ > 3)]", Value.ofArray(Value.of(4), Value.of(5))),
                arguments("[10, 20, 30, 40][?(@ < 30)]", Value.ofArray(Value.of(10), Value.of(20))),
                arguments("[{\"power\": 100}, {\"power\": 50}, {\"power\": 200}][?(@.power > 75)]",
                        Value.ofArray(ObjectValue.builder().put("power", Value.of(100)).build(),
                                ObjectValue.builder().put("power", Value.of(200)).build())),

                // Expression step on constant values
                arguments("[\"Imrryr\", \"Tanelorn\", \"Vilmir\"][(1 + 1)]", Value.of("Vilmir")),
                arguments("[10, 20, 30][((6 / 2)-1)]", Value.of(30)),

                // Index union on constant arrays
                arguments("[10, 20, 30, 40, 50][0, 2, 4]", Value.ofArray(Value.of(10), Value.of(30), Value.of(50))),
                arguments("[\"a\", \"b\", \"c\", \"d\"][1, 3]", Value.ofArray(Value.of("b"), Value.of("d"))),

                // Attribute union on constant objects
                arguments("{\"name\": \"Melniboné\", \"capital\": \"Imrryr\", \"age\": 10000}[\"name\", \"capital\"]",
                        Value.ofArray(Value.of("Melniboné"), Value.of("Imrryr"))),

                // Recursive descent on constant structures
                arguments("{\"outer\": {\"inner\": {\"deep\": 42}}}..deep", Value.ofArray(Value.of(42))),
                arguments("[{\"x\": 1}, {\"y\": {\"x\": 2}}, {\"x\": 3}]..x",
                        Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),

                // Mixed operations with steps and operators
                arguments("[1, 2, 3][0] + 10", Value.of(11)), arguments("{\"value\": 100}.value / 4", Value.of(25)),
                arguments("([1, 2, 3][1] * 5) > 9", Value.TRUE), arguments("[5, 10, 15][?(@ > 7)][0]", Value.of(10)));
    }

    @ParameterizedTest
    @MethodSource
    void expressionsThatCompileToPureExpressions(String expression, Value expectedResult) {
        assertCompiledExpressionEvaluatesTo(expression, expectedResult);
    }

    private static Stream<Arguments> expressionsThatCompileToPureExpressions() {
        return Stream.of(
                // Subscription elements require runtime evaluation
                arguments("subject", Value.of("Elric")), arguments("action", Value.of("slay")),
                arguments("resource", Value.of("Moonglum")), arguments("environment", Value.of("Tanelorn")),

                // Operations on subscription elements
                arguments("subject == \"Elric\"", Value.TRUE), arguments("action == \"slay\"", Value.TRUE),
                arguments("resource != \"Stormbringer\"", Value.TRUE), arguments("subject == \"Arioch\"", Value.FALSE),

                // Key access on subscription elements
                arguments("{\"subject\": subject}.subject", Value.of("Elric")),
                arguments("{\"action\": action, \"resource\": resource}.action", Value.of("slay")),

                // Arrays containing subscription elements
                arguments("[subject, action][0]", Value.of("Elric")),
                arguments("[subject, action, resource][1]", Value.of("slay")),

                // Arithmetic with subscription elements (when they're numbers)
                arguments("{\"clearance\": 3}.clearance + 1", Value.of(4)),
                arguments("{\"level\": 10}.level * 2", Value.of(20)),

                // Boolean operations with subscription elements
                arguments("true && (subject == \"Elric\")", Value.TRUE),
                arguments("false || (action == \"slay\")", Value.TRUE),
                arguments("!(subject == \"Arioch\")", Value.TRUE),

                // Condition steps on arrays with subscription references
                arguments("[{\"name\": \"Elric\"}, {\"name\": \"Moonglum\"}][?(@.name == subject)][0]",
                        ObjectValue.builder().put("name", Value.of("Elric")).build()),

                // Complex expressions mixing constants and subscription elements
                arguments("(subject == \"Elric\") && (5 > 3)", Value.TRUE),
                arguments("[10, 20, 30][?((@ > 15) && (subject == \"Elric\"))]",
                        Value.ofArray(Value.of(20), Value.of(30))),
                arguments("{\"authorized\": (action == \"slay\")}.authorized", Value.TRUE),

                // Nested access with subscription elements
                arguments("{\"user\": subject, \"weapons\": [\"Stormbringer\", \"Mournblade\"]}.user",
                        Value.of("Elric")),
                arguments("{\"metadata\": {\"actor\": subject}}.metadata.actor", Value.of("Elric")),

                // Comparisons involving subscription elements
                arguments("{\"title\": \"emperor\"}.title == \"emperor\"", Value.TRUE),
                arguments("[\"Elric\", \"Moonglum\"][0] == subject", Value.TRUE),

                // Operations on objects containing subscription elements
                arguments("{\"subject\": subject, \"action\": action}.subject == \"Elric\"", Value.TRUE),
                arguments("{\"a\": subject, \"b\": resource}.b", Value.of("Moonglum")));
    }
}
