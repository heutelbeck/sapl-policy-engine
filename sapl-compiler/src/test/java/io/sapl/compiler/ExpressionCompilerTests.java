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
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.util.TestUtil;
import lombok.val;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.test.StepVerifier;

import java.util.stream.Stream;

import static io.sapl.util.TestUtil.*;
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

                // Arithmetic with various spacing patterns
                arguments("1+-1", Value.of(0)), arguments("1+ -1", Value.of(0)), arguments("1 + -1", Value.of(0)),
                arguments("1 + - 1", Value.of(0)), arguments("--1", Value.of(1)), arguments("1+ +(2)", Value.of(3)),
                arguments("(1+2)*3.0", Value.of(9.0)), arguments("5+5-3", Value.of(7)),

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
                arguments("[10, 20, 30, 40, 50][1: :2]", Value.ofArray(Value.of(20), Value.of(40))),
                arguments("[10, 20, 30, 40, 50][:3]", Value.ofArray(Value.of(10), Value.of(20), Value.of(30))),
                arguments("[10, 20, 30, 40, 50][2:]", Value.ofArray(Value.of(30), Value.of(40), Value.of(50))),

                // Wildcard on constant structures
                arguments("[1, 2, 3].*", Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
                arguments("{\"a\": 1, \"b\": 2}.*", ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).build()),

                // Condition step on constant arrays
                arguments("[1, 2, 3, 4, 5][?(@ > 3)]", Value.ofArray(Value.of(4), Value.of(5))),
                arguments("[10, 20, 30, 40][?(@ < 30)]", Value.ofArray(Value.of(10), Value.of(20))),

                // Expression step on constant values
                arguments("[\"Imrryr\", \"Tanelorn\", \"Vilmir\"][(1 + 1)]", Value.of("Vilmir")),
                arguments("[10, 20, 30][((6 / 2) - 1)]", Value.of(30)),
                arguments("[0,1,2,3,4,5,6,7,8,9][(2+3)]", Value.of(5)),
                arguments("{ \"key\" : true }[(\"ke\"+\"y\")]", Value.TRUE),

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
                arguments("{\"a\": subject, \"b\": resource}.b", Value.of("Moonglum")),

                // Objects with undefined values filtered out
                arguments("{\"name\": \"Arioch\", \"title\": undefined, \"realm\": \"Chaos\"}.name",
                        Value.of("Arioch")),
                arguments("{\"name\": \"Arioch\", \"title\": undefined, \"realm\": \"Chaos\"}.realm",
                        Value.of("Chaos")),

                // Arrays with undefined values filtered out
                arguments("[1, undefined, 3, undefined, 5]", Value.ofArray(Value.of(1), Value.of(3), Value.of(5))),
                arguments("[subject, undefined, action]", Value.ofArray(Value.of("Elric"), Value.of("slay"))),

                // Condition steps with scalar values
                arguments("42[?(@ > 40)]", Value.of(42)), arguments("42[?(@ < 40)]", Value.UNDEFINED),
                arguments("\"Stormbringer\"[?(@ == \"Stormbringer\")]", Value.of("Stormbringer")),
                arguments("\"Stormbringer\"[?(@ == \"Mournblade\")]", Value.UNDEFINED),

                // Expression steps with non-existent keys return undefined
                arguments("{ \"key\" : true }[(\"no_ke\"+\"y\")]", Value.UNDEFINED),

                // Condition steps on arrays with pure conditions
                arguments("[1, 2, 3, 4, 5][?(@ > {\"threshold\": 3}.threshold)]",
                        Value.ofArray(Value.of(4), Value.of(5))),
                arguments("[\"Elric\", \"Moonglum\", \"Cymoril\"][?(@ == subject)]", Value.ofArray(Value.of("Elric"))),

                // Condition steps on objects with pure conditions
                arguments("{\"Elric\": 100, \"Moonglum\": 50, \"Cymoril\": 75}[?(@ > {\"min\": 60}.min)]",
                        ObjectValue.builder().put("Elric", Value.of(100)).put("Cymoril", Value.of(75)).build()),

                // Condition steps with subscription-scoped conditions
                arguments("{\"warrior\": \"Elric\", \"companion\": \"Moonglum\"}[?(@ == subject)]",
                        ObjectValue.builder().put("warrior", Value.of("Elric")).build()),

                // Regex operations with constant patterns
                arguments("\"Stormbringer\" =~ \"^Storm.*\"", Value.TRUE),
                arguments("\"Stormbringer\" =~ \"^Mourn.*\"", Value.FALSE),
                arguments("\"chaos@law.com\" =~ \".*@.*\\\\.com\"", Value.TRUE),

                // More complex object compositions
                arguments("{\"lord\": {\"name\": subject, \"domain\": \"Chaos\"}}",
                        ObjectValue.builder()
                                .put("lord",
                                        ObjectValue.builder().put("name", Value.of("Elric"))
                                                .put("domain", Value.of("Chaos")).build())
                                .build()),

                // Expression steps with pure expressions
                arguments("[\"Arioch\", \"Xiombarg\", \"Mabelode\"][(1 + {\"offset\": 1}.offset)]",
                        Value.of("Mabelode")),

                // XOR operations
                arguments("true ^ (subject == \"Elric\")", Value.FALSE),
                arguments("false ^ (action == \"slay\")", Value.TRUE),

                // Eager OR operations
                arguments("true | (subject == \"Elric\")", Value.TRUE),
                arguments("false | (action == \"slay\")", Value.TRUE),

                // Eager AND operations
                arguments("true & (subject == \"Elric\")", Value.TRUE),
                arguments("false & (action == \"slay\")", Value.FALSE),

                // Lazy operators with nested subscription access
                arguments("({\"authorized\": true}.authorized) || (subject == \"Yyrkoon\")", Value.TRUE),
                arguments("({\"authorized\": false}.authorized) || (subject == \"Elric\")", Value.TRUE),
                arguments("({\"soulCount\": 999}.soulCount > 500) && (action == \"slay\")", Value.TRUE),
                arguments("({\"soulCount\": 100}.soulCount > 500) && (action == \"slay\")", Value.FALSE),

                // Lazy operators in complex boolean expressions
                arguments("(subject == \"Elric\") || (action == \"summon\")", Value.TRUE),
                arguments("(subject == \"Moonglum\") || (action == \"slay\")", Value.TRUE),
                arguments("(subject == \"Yyrkoon\") || (action == \"summon\")", Value.FALSE),
                arguments("(subject == \"Elric\") && (action == \"slay\")", Value.TRUE),
                arguments("(subject == \"Elric\") && (action == \"summon\")", Value.FALSE),

                // Lazy operators in condition steps
                arguments("[1, 2, 3, 4, 5][?((@  > 2) && (subject == \"Elric\"))]",
                        Value.ofArray(Value.of(3), Value.of(4), Value.of(5))),
                arguments("[1, 2, 3, 4, 5][?((@  < 2) || (action == \"slay\"))]",
                        Value.ofArray(Value.of(1), Value.of(2), Value.of(3), Value.of(4), Value.of(5))),

                // Lazy operators with object property access
                arguments("{\"emperor\": subject, \"weapon\": action}.emperor == \"Elric\"", Value.TRUE),
                arguments("({\"dragonCount\": 13}.dragonCount > 10) && (resource == \"Moonglum\")", Value.TRUE),

                // Chained lazy operators with subscription elements
                arguments("(subject == \"Elric\") || (action == \"summon\") || (resource == \"Moonglum\")", Value.TRUE),
                arguments("(subject == \"Yyrkoon\") || (action == \"betray\") || (resource == \"Moonglum\")",
                        Value.TRUE),
                arguments("(subject == \"Elric\") && (action == \"slay\") && true", Value.TRUE));
    }

    @ParameterizedTest
    @MethodSource
    void errorConditionsHandledCorrectly(String expression, String expectedErrorSubstring) {
        assertCompiledExpressionEvaluatesToErrorContaining(expression, expectedErrorSubstring);
    }

    private static Stream<Arguments> errorConditionsHandledCorrectly() {
        return Stream.of(
                // Condition steps with non-boolean conditions on scalars
                arguments("42[?(@ + 1)]", "Condition"), arguments("\"text\"[?(123)]", "Condition"),

                // Condition steps with non-boolean conditions on arrays
                arguments("[1, 2, 3][?(@ * 2)]", "Condition"),

                // Condition steps with non-boolean conditions on objects
                arguments("{\"a\": 1, \"b\": 2}[?(@ + 5)]", "Condition"),

                // Lazy OR type errors - left operand
                arguments("\"Stormbringer\" || true", "type mismatch"), arguments("999 || false", "type mismatch"),
                arguments("null || true", "type mismatch"),
                arguments("[\"Imrryr\", \"Tanelorn\"] || false", "type mismatch"),
                arguments("{\"lord\": \"Arioch\"} || true", "type mismatch"),

                // Lazy OR type errors - right operand
                arguments("false || \"Mournblade\"", "type mismatch"), arguments("false || 666", "type mismatch"),
                arguments("false || null", "type mismatch"), arguments("false || [\"Melniboné\"]", "type mismatch"),
                arguments("false || {\"city\": \"Nadsokor\"}", "type mismatch"),

                // Lazy AND type errors - left operand
                arguments("\"Xiombarg\" && true", "type mismatch"), arguments("777 && false", "type mismatch"),
                arguments("null && true", "type mismatch"),
                arguments("[\"Pyaray\", \"Mabelode\"] && false", "type mismatch"),
                arguments("{\"sword\": \"Stormbringer\"} && true", "type mismatch"),

                // Lazy AND type errors - right operand
                arguments("true && \"Young Kingdoms\"", "type mismatch"), arguments("true && 888", "type mismatch"),
                arguments("true && null", "type mismatch"), arguments("true && [\"Dragon Lords\"]", "type mismatch"),
                arguments("true && {\"emperor\": \"Elric\"}", "type mismatch"),

                // Lazy operators with compile-time type errors
                arguments("\"Elric\" || true", "type mismatch"), arguments("false || \"Moonglum\"", "type mismatch"),
                arguments("\"Dyvim Tvar\" && false", "type mismatch"),
                arguments("true && \"Tanelorn\"", "type mismatch"));
    }

    @ParameterizedTest
    @MethodSource
    void edgeCasesHandledCorrectly(String expression, Value expected) {
        assertCompiledExpressionEvaluatesTo(expression, expected);
    }

    private static Stream<Arguments> edgeCasesHandledCorrectly() {
        return Stream.of(
                // Empty condition results
                arguments("[1, 2, 3][?(@ > 10)]", Value.EMPTY_ARRAY),
                arguments("{\"a\": 1, \"b\": 2}[?(@ > 10)]", Value.EMPTY_OBJECT),

                // Condition steps on empty collections
                arguments("[][?(@ > 0)]", Value.EMPTY_ARRAY), arguments("{}[?(@ > 0)]", Value.EMPTY_OBJECT),

                // Nested empty collections
                arguments("[[]]", Value.ofArray((new Value[] { Value.EMPTY_ARRAY }))),
                arguments("[{}]", Value.ofArray(Value.EMPTY_OBJECT)),
                arguments("{\"empty\": []}", ObjectValue.builder().put("empty", Value.EMPTY_ARRAY).build()),
                arguments("{\"empty\": {}}", ObjectValue.builder().put("empty", Value.EMPTY_OBJECT).build()),

                // Multiple undefined values in collections
                arguments("[undefined, undefined, undefined]", Value.EMPTY_ARRAY),
                arguments("{\"a\": undefined, \"b\": undefined}", Value.EMPTY_OBJECT),

                // Mixed defined and undefined in nested structures
                arguments("{\"outer\": {\"inner\": undefined, \"value\": 42}}",
                        ObjectValue.builder().put("outer", ObjectValue.builder().put("value", Value.of(42)).build())
                                .build()),

                // All boolean combinations with lazy operators
                arguments("true || true", Value.TRUE), arguments("true || false", Value.TRUE),
                arguments("false || true", Value.TRUE),

                // Unary plus and minus
                arguments("+123", Value.of(123)), arguments("+(5 + 5)", Value.of(10)),
                arguments("-(10 - 3)", Value.of(-7)),

                // Comparison edge cases
                arguments("0 == 0", Value.TRUE), arguments("-1 < 0", Value.TRUE), arguments("0 <= 0", Value.TRUE),
                arguments("1 >= 1", Value.TRUE), arguments("2 > 1", Value.TRUE),

                // Element membership edge cases
                arguments("null in [null, 1, 2]", Value.TRUE), arguments("undefined in [undefined, 1, 2]", Value.FALSE),
                arguments("true in [true, false]", Value.TRUE), arguments("false in [true, false]", Value.TRUE),

                // Lazy AND with all boolean combinations
                arguments("true && true", Value.TRUE), arguments("true && false", Value.FALSE),
                arguments("false && true", Value.FALSE), arguments("false && false", Value.FALSE),

                // Chained lazy OR operations
                arguments("true || false || false", Value.TRUE), arguments("false || false || true", Value.TRUE),
                arguments("false || false || false", Value.FALSE), arguments("false || true || false", Value.TRUE),

                // Chained lazy AND operations
                arguments("true && true && true", Value.TRUE), arguments("true && false && true", Value.FALSE),
                arguments("false && true && true", Value.FALSE), arguments("true && true && false", Value.FALSE),

                // Mixed lazy and eager boolean operators
                arguments("(true || false) & (false | true)", Value.TRUE),
                arguments("(false && true) | (true && true)", Value.TRUE),
                arguments("(true || true) & false", Value.FALSE), arguments("(false && false) | true", Value.TRUE),

                // Lazy operators with negation
                arguments("!(true || false)", Value.FALSE), arguments("!(false && true)", Value.TRUE),
                arguments("!false || false", Value.TRUE), arguments("!true && true", Value.FALSE),

                // Complex nested lazy operations
                arguments("(true && (false || true))", Value.TRUE),
                arguments("(false || (true && false))", Value.FALSE),
                arguments("((true || false) && (true || false))", Value.TRUE),
                arguments("((false && true) || (false && true))", Value.FALSE));
    }

    @ParameterizedTest
    @MethodSource
    void functionCallsWithConstantParameters(String expression, Value expected) {
        assertExpressionCompilesToValue(expression, expected);
    }

    private static Stream<Arguments> functionCallsWithConstantParameters() {
        return Stream.of(
                // Duration conversion functions with constant parameters fold
                arguments("time.durationOfSeconds(60)", Value.of(60000)),
                arguments("time.durationOfMinutes(5)", Value.of(300000)),
                arguments("time.durationOfHours(2)", Value.of(7200000)),
                arguments("time.durationOfDays(1)", Value.of(86400000)),

                // Epoch conversion functions with constant parameters fold
                arguments("time.ofEpochSecond(0)", Value.of("1970-01-01T00:00:00Z")),
                arguments("time.ofEpochMilli(0)", Value.of("1970-01-01T00:00:00Z")),

                // Date/time extraction functions with constant parameters fold
                arguments("time.hourOf(\"2021-11-08T13:17:23Z\")", Value.of(13)),
                arguments("time.minuteOf(\"2021-11-08T13:17:23Z\")", Value.of(17)),
                arguments("time.secondOf(\"2021-11-08T13:00:23Z\")", Value.of(23)),
                arguments("time.dayOfYear(\"2021-11-08T13:00:00Z\")", Value.of(312)),
                arguments("time.weekOfYear(\"2021-11-08T13:00:00Z\")", Value.of(45)),

                // Date arithmetic functions with constant parameters fold
                arguments("time.plusDays(\"2021-11-08T13:00:00Z\", 5)", Value.of("2021-11-13T13:00:00Z")),
                arguments("time.minusDays(\"2021-11-08T13:00:00Z\", 5)", Value.of("2021-11-03T13:00:00Z")),
                arguments("time.plusSeconds(\"2021-11-08T13:00:00Z\", 10)", Value.of("2021-11-08T13:00:10Z")),
                arguments("time.minusSeconds(\"2021-11-08T13:00:00Z\", 10)", Value.of("2021-11-08T12:59:50Z")),

                // Date comparison functions with constant parameters fold
                arguments("time.before(\"2021-11-08T13:00:00Z\", \"2021-11-08T13:00:01Z\")", Value.TRUE),
                arguments("time.after(\"2021-11-08T13:00:01Z\", \"2021-11-08T13:00:00Z\")", Value.TRUE),
                arguments("time.between(\"2021-11-08T13:00:00Z\", \"2021-11-07T13:00:00Z\", \"2021-11-09T13:00:00Z\")",
                        Value.TRUE),

                // Temporal bounds functions with constant parameters fold
                arguments("time.startOfDay(\"2021-11-08T13:45:30Z\")", Value.of("2021-11-08T00:00:00Z")),
                arguments("time.endOfDay(\"2021-11-08T13:45:30Z\")", Value.of("2021-11-08T23:59:59.999999999Z")),
                arguments("time.startOfMonth(\"2021-11-08T13:45:30Z\")", Value.of("2021-11-01T00:00:00Z")),
                arguments("time.endOfMonth(\"2021-11-08T13:45:30Z\")", Value.of("2021-11-30T23:59:59.999999999Z")),
                arguments("time.startOfYear(\"2021-11-08T13:45:30Z\")", Value.of("2021-01-01T00:00:00Z")),

                // Validation functions with constant parameters fold
                arguments("time.validUTC(\"2021-11-08T13:00:00Z\")", Value.TRUE),
                arguments("time.validUTC(\"20111-000:00Z\")", Value.FALSE),
                arguments("time.validRFC3339(\"2021-11-08T13:00:00Z\")", Value.TRUE),
                arguments("time.validRFC3339(\"2021-11-08T13:00:00\")", Value.FALSE),

                // Age calculation functions with constant parameters fold
                arguments("time.ageInYears(\"1990-05-15\", \"2021-11-08\")", Value.of(31)),
                arguments("time.ageInMonths(\"1990-05-15\", \"1990-08-20\")", Value.of(3)),

                // Nested function calls with constant parameters fold
                arguments("time.hourOf(time.plusDays(\"2021-11-08T13:00:00Z\", 1))", Value.of(13)),
                arguments("time.durationOfMinutes(time.hourOf(\"2021-11-08T02:00:00Z\"))", Value.of(120000)));
    }

    @ParameterizedTest
    @MethodSource
    void functionCallsWithPureParameters(String expression, Value expected) {
        assertCompiledExpressionEvaluatesTo(expression, expected);
    }

    private static Stream<Arguments> functionCallsWithPureParameters() {
        return Stream.of(
                // Function calls with subscription elements
                arguments("time.hourOf({\"time\": \"2021-11-08T13:00:00Z\"}.time)", Value.of(13)),
                arguments("time.durationOfSeconds({\"duration\": 60}.duration)", Value.of(60000)),
                arguments("time.validUTC({\"timestamp\": \"2021-11-08T13:00:00Z\"}.timestamp)", Value.TRUE),

                // Function calls combined with operators
                arguments("time.hourOf(\"2021-11-08T13:00:00Z\") > 12", Value.TRUE),
                arguments("time.durationOfMinutes(5) == 300000", Value.TRUE),
                arguments("time.before(\"2021-11-08T13:00:00Z\", \"2021-11-08T14:00:00Z\") && (subject == \"Elric\")",
                        Value.TRUE),

                // Function results with steps
                arguments("time.dateOf(\"2021-11-08T13:00:00Z\") == \"2021-11-08\"", Value.TRUE),
                arguments("[time.hourOf(\"2021-11-08T13:00:00Z\"), time.minuteOf(\"2021-11-08T13:17:23Z\")][0]",
                        Value.of(13)),

                // Functions in array and object construction
                arguments("[time.hourOf(\"2021-11-08T13:00:00Z\"), time.hourOf(\"2021-11-08T14:00:00Z\")]",
                        Value.ofArray(Value.of(13), Value.of(14))),
                arguments("{\"hour\": time.hourOf(\"2021-11-08T13:00:00Z\")}",
                        ObjectValue.builder().put("hour", Value.of(13)).build()),

                // Functions in condition steps
                arguments("[\"2021-11-08T13:00:00Z\", \"2021-11-08T14:00:00Z\"][?(time.hourOf(@) > 12)]",
                        Value.ofArray(Value.of("2021-11-08T13:00:00Z"), Value.of("2021-11-08T14:00:00Z"))),

                // Functions with mixed constant and pure parameters
                arguments(
                        "time.timeBetween(\"2021-01-01T00:00:00Z\", \"2022-01-01T00:00:00Z\", {\"unit\": \"YEARS\"}.unit)",
                        Value.of(1)));
    }

    @ParameterizedTest
    @MethodSource
    void arithmeticErrorConditions(String expression, String expectedErrorSubstring) {
        assertCompiledExpressionEvaluatesToErrorContaining(expression, expectedErrorSubstring);
    }

    private static Stream<Arguments> arithmeticErrorConditions() {
        return Stream.of(
                // Division by zero
                arguments("10 / 0", "divis"), arguments("100 / 0", "divis"), arguments("42 / (5 - 5)", "divis"),

                // Modulo by zero
                arguments("10 % 0", "divis"), arguments("17 % (3 - 3)", "divis"),

                // Division/modulo by zero in complex expressions
                arguments("(100 / 0) + 5", "divis"), arguments("{\"value\": (10 % 0)}.value", "divis"));
    }

    @ParameterizedTest
    @MethodSource
    void invalidStepOperations(String expression, String expectedErrorSubstring) {
        assertCompiledExpressionEvaluatesToErrorContaining(expression, expectedErrorSubstring);
    }

    private static Stream<Arguments> invalidStepOperations() {
        return Stream.of(
                // Key step on non-objects (returns error)
                arguments("\"Stormbringer\".weapon", "non-object"), arguments("true.realm", "non-object"),
                arguments("[1, 2, 3].key", "non-object"),

                // Index step on non-arrays (returns error)
                arguments("\"Stormbringer\"[0]", "non-array"), arguments("true[1]", "non-array"),
                arguments("999[0]", "non-array"), arguments("{\"key\": \"value\"}[0]", "non-array"),

                // Slicing on non-arrays (returns error)
                arguments("999[1:3]", "slice"), arguments("{\"a\": 1}[0:2]", "slice"),

                // Expression step type errors
                arguments("[1,2,3][(true)]", "but got true"),
                arguments("[0,1,2,3,4,5,6,7,8,9][(\"key\")]", "using a key"),
                arguments("{ \"key\" : true }[(5+2)]", "non-array"), arguments("undefined[(1 + 1)]", "non-array"),

                // Expression step out of bounds errors
                arguments("[1,2,3][(1+100)]", "out of bounds"), arguments("[1,2,3][(1 - 100)]", "out of bounds"),

                // Expression step error propagation
                arguments("[][(10/0)]", "division"), arguments("[(10/0)][(2+2)]", "division"),
                arguments("{ \"key\" : true }[(10/0)]", "division"));
    }

    @ParameterizedTest
    @MethodSource
    void indexAndAttributeUnionEdgeCases(String expression, Value expected) {
        assertCompiledExpressionEvaluatesTo(expression, expected);
    }

    private static Stream<Arguments> indexAndAttributeUnionEdgeCases() {
        return Stream.of(
                // Index unions automatically de-duplicate
                arguments("[10, 20, 30][0, 1, 2]", Value.ofArray(Value.of(10), Value.of(20), Value.of(30))),
                arguments("[\"Arioch\", \"Xiombarg\", \"Pyaray\"][0, 2]",
                        Value.ofArray(Value.of("Arioch"), Value.of("Pyaray"))),

                // Attribute unions automatically de-duplicate
                arguments("{\"realm\": \"Chaos\", \"lord\": \"Arioch\"}[\"realm\", \"lord\"]",
                        Value.ofArray(Value.of("Chaos"), Value.of("Arioch"))),

                // Attribute unions with non-existent keys (only returns existing values, skips
                // missing)
                arguments("{\"weapon\": \"Stormbringer\"}[\"weapon\", \"missing\", \"nothere\"]",
                        Value.ofArray(Value.of("Stormbringer"))));
    }

    @ParameterizedTest
    @MethodSource
    void indexUnionWithOutOfBoundsError(String expression, String expectedErrorSubstring) {
        assertCompiledExpressionEvaluatesToErrorContaining(expression, expectedErrorSubstring);
    }

    private static Stream<Arguments> indexUnionWithOutOfBoundsError() {
        return Stream.of(
                // Out-of-bounds indices cause errors
                arguments("[10, 20, 30][0, 10, 1]", "out of bounds"),
                arguments("[\"Elric\", \"Moonglum\"][-1, 0, 5]", "out of bounds"));
    }

    @ParameterizedTest
    @MethodSource
    void chainedConditionSteps(String expression, Value expected) {
        assertCompiledExpressionEvaluatesTo(expression, expected);
    }

    private static Stream<Arguments> chainedConditionSteps() {
        return Stream.of(
                // Multiple condition steps chained on arrays
                arguments("[1, 2, 3, 4, 5, 6, 7, 8][?(@ > 2)][?(@ < 7)]",
                        Value.ofArray(Value.of(3), Value.of(4), Value.of(5), Value.of(6))),
                arguments("[10, 20, 30, 40, 50][?(@ >= 20)][?(@ <= 40)]",
                        Value.ofArray(Value.of(20), Value.of(30), Value.of(40))),

                // Slicing followed by condition step on non-edge values
                arguments("[10, 20, 30, 40, 50, 60][1:5][?(@ < 40)]", Value.ofArray(Value.of(20), Value.of(30))),

                // Condition step followed by slicing
                arguments("[1, 2, 3, 4, 5, 6, 7, 8, 9, 10][?(@ > 3)][0:3]",
                        Value.ofArray(Value.of(4), Value.of(5), Value.of(6))));
    }

    @ParameterizedTest
    @MethodSource
    void nullAndUndefinedPropagation(String expression, Value expected) {
        assertCompiledExpressionEvaluatesTo(expression, expected);
    }

    private static Stream<Arguments> nullAndUndefinedPropagation() {
        return Stream.of(
                // Null in comparisons
                arguments("null == null", Value.TRUE), arguments("null != null", Value.FALSE),
                arguments("null == 42", Value.FALSE), arguments("null == \"Elric\"", Value.FALSE),

                // Undefined in comparisons
                arguments("undefined == undefined", Value.TRUE), arguments("undefined != undefined", Value.FALSE),
                arguments("undefined == null", Value.FALSE), arguments("undefined == 42", Value.FALSE),

                // Null and undefined with 'in' operator
                arguments("null in [null, 1, 2]", Value.TRUE), arguments("null in [1, 2, 3]", Value.FALSE),
                // undefined values are filtered from arrays before 'in' check
                arguments("undefined in [undefined, 1, 2]", Value.FALSE),
                arguments("undefined in [1, 2, 3]", Value.FALSE),

                // Null in boolean contexts (type errors handled elsewhere)
                arguments("null == null && true", Value.TRUE), arguments("(null == null) || false", Value.TRUE));
    }

    // ========== StreamExpression Tests using Attribute Finders ==========

    @ParameterizedTest
    @MethodSource
    void attributeFinderBasicUsage(String expression, int expectedStreamCount) {
        // Attribute finders create StreamExpressions that emit multiple values
        val evaluated = TestUtil.evaluateExpression(expression);
        StepVerifier.create(evaluated.take(expectedStreamCount)).expectNextCount(expectedStreamCount).verifyComplete();
    }

    private static Stream<Arguments> attributeFinderBasicUsage() {
        return Stream.of(
                // TestPip.echo returns Flux.just(entity) - 1 value
                arguments("\"Elric\".<test.echo[{\"fresh\":true}]>", 1),
                arguments("subject.<test.echo[{\"fresh\":true}]>", 1),
                // Workaround: Use (42) instead of 42 due to lexer ambiguity with decimal
                // numbers
                arguments("(43).<test.echo>", 1), arguments("true.<test.echo>", 1));
    }

    @ParameterizedTest
    @MethodSource
    void attributeFinderInExpressions(String expression) {
        // Attribute finders in expressions create StreamExpressions - verify it streams
        val evaluated = TestUtil.evaluateExpression(expression);
        StepVerifier.create(evaluated.take(1).log()).expectNextCount(1).verifyComplete();
    }

    private static Stream<Arguments> attributeFinderInExpressions() {
        return Stream.of(
                // Attribute finders in comparisons
                arguments("\"Elric\".<test.echo[{\"fresh\":true}]> == \"Elric\""), // arguments("subject.<test.echo[{fresh=true}]>
                                                                                   // !=
                // \"Yyrkoon\""),
                arguments("subject.<test.echo[{\"fresh\": true}]>"),
                // Attribute finders in arithmetic
                // Workaround: Use (number) instead of number due to lexer ambiguity
                arguments("(42).<test.echo> + 10"), arguments("(100).<test.echo> - 50"),

                // Attribute finders in comparisons
                arguments("\"Stormbringer\".<test.echo> == \"Stormbringer\""), arguments("(999).<test.echo> > 500"),

                // Attribute finders with 'in' operator
                arguments("\"Elric\" in [\"Elric\", \"Moonglum\"].<test.echo>"));
    }

    @ParameterizedTest
    @MethodSource
    void attributeFinderWithSteps(String expression) {
        // Attribute finders with steps applied
        val evaluated = TestUtil.evaluateExpression(expression);
        StepVerifier.create(evaluated.take(1)).expectNextMatches(v -> !(v instanceof ErrorValue)).verifyComplete();
    }

    private static Stream<Arguments> attributeFinderWithSteps() {
        return Stream.of(
                // Key access on attribute result
                arguments("{\"weapon\": \"Stormbringer\"}.<test.echo>.weapon"),

                // Index access on attribute result
                arguments("[\"Arioch\", \"Xiombarg\"].<test.echo>[0]"),

                // Condition step on attribute result
                arguments("[1, 2, 3, 4, 5].<test.echo>[?(@ > 2)]"),

                // Slicing on attribute result
                arguments("[10, 20, 30, 40, 50].<test.echo>[0:2]"),

                // Wildcard on attribute result
                arguments("{\"a\": 1, \"b\": 2}.<test.echo>.*"),

                // Recursive descent on attribute result
                arguments("{\"level1\": {\"value\": 42}}.<test.echo>..value"));
    }

    @ParameterizedTest
    @MethodSource
    void attributeFinderInDataStructures(String expression) {
        // Attribute finders in arrays and objects
        val evaluated = TestUtil.evaluateExpression(expression);
        StepVerifier.create(evaluated.take(1))
                .expectNextMatches(v -> v instanceof ArrayValue || v instanceof ObjectValue).verifyComplete();
    }

    private static Stream<Arguments> attributeFinderInDataStructures() {
        return Stream.of(
                // In array construction
                arguments("[\"Elric\".<test.echo>, \"static\"]"), arguments("[subject.<test.echo>, action]"),

                // In object construction
                arguments("{\"dynamic\": \"Moonglum\".<test.echo>, \"static\": \"value\"}"),
                arguments("{\"s\": subject.<test.echo>, \"a\": action}"));
    }

    @ParameterizedTest
    @MethodSource
    void attributeFinderInFunctionCalls(String expression) {
        // Attribute finders as function arguments create StreamExpressions
        val evaluated = TestUtil.evaluateExpression(expression);
        StepVerifier.create(evaluated.take(1)).expectNextMatches(v -> !(v instanceof ErrorValue)).verifyComplete();
    }

    private static Stream<Arguments> attributeFinderInFunctionCalls() {
        return Stream.of(
                // Time functions with attribute finder arguments
                arguments("time.hourOf(\"2021-11-08T13:00:00Z\".<test.echo>)"),
                // Workaround: Use (60) instead of 60 due to lexer ambiguity
                arguments("time.durationOfSeconds((60).<test.echo>)"),
                arguments("time.validUTC(\"2021-11-08T13:00:00Z\".<test.echo>)"));
    }

    @ParameterizedTest
    @MethodSource
    void chainedAttributeFinderOperations(String expression) {
        // Multiple attribute finders chained
        // Filter errors because TestPip emits 2 values and chained operations
        // on "hello world" may produce errors (e.g., .key on a string)
        val evaluated = TestUtil.evaluateExpression(expression);
        StepVerifier.create(evaluated.filter(v -> !(v instanceof ErrorValue)).take(1)).expectNextCount(1)
                .verifyComplete();
    }

    private static Stream<Arguments> chainedAttributeFinderOperations() {
        return Stream.of(
                // Attribute finder on attribute finder result
                arguments("\"Elric\".<test.echo>.<test.echo>"), arguments("subject.<test.echo>.<test.echo>"),

                // Attribute finder with steps then another attribute finder
                arguments("{\"key\": \"value\"}.<test.echo>.key.<test.echo>"));
    }
}
