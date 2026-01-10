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

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.*;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.libraries.StandardFunctionLibrary;
import io.sapl.functions.libraries.StringFunctionLibrary;
import io.sapl.functions.libraries.TemporalFunctionLibrary;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Stream;

import static io.sapl.util.ExpressionTestUtil.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ExpressionCompilerTests {

    private static CompilationContext contextWithFunctions;

    private static final AttributeBroker DEFAULT_ATTRIBUTE_BROKER = new AttributeBroker() {
        @Override
        public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
            return Flux.just(Value.error("No attribute finder registered for: " + invocation.attributeName()));
        }

        @Override
        public List<Class<?>> getRegisteredLibraries() {
            return List.of();
        }
    };

    private static DefaultFunctionBroker functionBroker;

    @BeforeAll
    static void setupFunctionBroker() {
        functionBroker = new DefaultFunctionBroker();
        functionBroker.loadStaticFunctionLibrary(StandardFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(StringFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(TemporalFunctionLibrary.class);
        contextWithFunctions = new CompilationContext(functionBroker, DEFAULT_ATTRIBUTE_BROKER);
    }

    @Nested
    @DisplayName("Escaped String Literals")
    class EscapedStringLiterals {

        @MethodSource
        @ParameterizedTest(name = "{0}")
        void when_escapedString_then_parsesCorrectly(String description, String expression, Value expected) {
            assertCompilesTo(expression, expected);
        }

        // @formatter:off
        static Stream<Arguments> when_escapedString_then_parsesCorrectly() {
            return Stream.of(
                arguments("newline",
                    """
                    "line1\\nline2"
                    """,
                    Value.of("line1\nline2")),
                arguments("tab",
                    """
                    "col1\\tcol2"
                    """,
                    Value.of("col1\tcol2")),
                arguments("carriage return",
                    """
                    "line1\\rline2"
                    """,
                    Value.of("line1\rline2")),
                arguments("backslash",
                    """
                    "path\\\\file"
                    """,
                    Value.of("path\\file")),
                arguments("double quote",
                    """
                    "say \\"hello\\""
                    """,
                    Value.of("say \"hello\"")),
                arguments("mixed escapes",
                    """
                    "line1\\nline2\\ttab"
                    """,
                    Value.of("line1\nline2\ttab")),
                arguments("unicode escape",
                    """
                    "\\u0041"
                    """,
                    Value.of("A")),
                arguments("multiple unicode",
                    """
                    "\\u0048\\u0065\\u006c\\u006c\\u006f"
                    """,
                    Value.of("Hello")));
        }
        // @formatter:on
    }

    @Nested
    @DisplayName("Arithmetic Integration")
    class ArithmeticIntegration {

        @MethodSource
        @ParameterizedTest(name = "{0}")
        void when_arithmeticExpression_then_evaluatesCorrectly(String description, String expression, Value expected) {
            assertCompilesTo(expression, expected);
        }

        // @formatter:off
        static Stream<Arguments> when_arithmeticExpression_then_evaluatesCorrectly() {
            return Stream.of(
                // Precedence
                arguments("mult before add", "2 + 3 * 4", Value.of(14)),
                arguments("div before sub", "20 - 12 / 4", Value.of(17)),
                arguments("parens override", "(2 + 3) * 4", Value.of(20)),
                arguments("complex precedence", "100 / (2 + 3) * 2", Value.of(40)),
                // Chained operations
                arguments("chained add", "1 + 2 + 3 + 4", Value.of(10)),
                arguments("chained sub", "20 - 5 - 3 - 2", Value.of(10)),
                arguments("chained mult", "2 * 3 * 4", Value.of(24)),
                arguments("chained div", "120 / 2 / 3 / 4", Value.of(5)),
                arguments("mixed chain", "10 - 3 + 2", Value.of(9)),
                // Unary in expressions
                arguments("unary minus in add", "5 + -3", Value.of(2)),
                arguments("unary plus", "+42", Value.of(42)),
                arguments("double negative", "--5", Value.of(5)),
                arguments("negate expression", "-(10 - 3)", Value.of(-7)),
                // Spacing variations
                arguments("no spaces", "1+2*3", Value.of(7)),
                arguments("unary no space", "1+-1", Value.of(0)),
                arguments("unary with space", "1 + -1", Value.of(0)),
                // Decimal operations
                arguments("decimal add", "1.5 + 2.5", Value.of(4.0)),
                arguments("decimal mult", "2.5 * 4", Value.of(10.0)),
                arguments("decimal in expr", "(1+2)*3.0", Value.of(9.0)),
                // String concatenation
                arguments("string concat",
                    """
                    "hello" + "world"
                    """,
                    Value.of("helloworld")),
                arguments("string + number",
                    """
                    "count: " + 42
                    """,
                    Value.of("count: 42")),
                arguments("string + boolean",
                    """
                    "flag: " + true
                    """,
                    Value.of("flag: true")),
                arguments("string + null",
                    """
                    "value: " + null
                    """,
                    Value.of("value: null")));
        }
        // @formatter:on

        @MethodSource
        @ParameterizedTest(name = "{0}")
        void when_arithmeticError_then_returnsError(String description, String expression) {
            val result = evaluateExpression(expression);
            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        // @formatter:off
        static Stream<Arguments> when_arithmeticError_then_returnsError() {
            return Stream.of(
                arguments("div by zero", "10 / 0"),
                arguments("mod by zero", "10 % 0"),
                arguments("complex div zero", "42 / (5 - 5)"),
                arguments("string subtract",
                    """
                    "hello" - 5
                    """),
                arguments("string multiply",
                    """
                    "hello" * 2
                    """),
                arguments("negate string",
                    """
                    -"text"
                    """));
        }
        // @formatter:on
    }

    @Nested
    @DisplayName("Boolean Short-Circuit")
    class BooleanShortCircuit {

        @MethodSource
        @ParameterizedTest(name = "{0}")
        void when_shortCircuitPreventsError_then_returnsResult(String description, String expression, Value expected) {
            assertCompilesTo(expression, expected);
        }

        static Stream<Arguments> when_shortCircuitPreventsError_then_returnsResult() {
            return Stream.of(
                    // OR short-circuit: true || error => true
                    arguments("or short-circuits error", "true || (1/0 > 0)", Value.TRUE),
                    arguments("chained or short-circuit", "false || true || (1/0 > 0)", Value.TRUE),
                    arguments("multiple error terms", "false || true || (1/0 > 0) || (2/0 > 0)", Value.TRUE),
                    // AND short-circuit: false && error => false
                    arguments("and short-circuits error", "false && (1/0 > 0)", Value.FALSE),
                    arguments("chained and short-circuit", "true && false && (1/0 > 0)", Value.FALSE),
                    arguments("multiple and errors", "true && false && (1/0 > 0) && (2/0 > 0)", Value.FALSE));
        }

        @MethodSource
        @ParameterizedTest(name = "{0}")
        void when_noShortCircuit_then_errorPropagates(String description, String expression) {
            val result = evaluateExpression(expression);
            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        static Stream<Arguments> when_noShortCircuit_then_errorPropagates() {
            return Stream.of(arguments("or no short-circuit", "false || (1/0 > 0)"),
                    arguments("and no short-circuit", "true && (1/0 > 0)"));
        }

        @MethodSource
        @ParameterizedTest(name = "{0}")
        void when_booleanWithComparisons_then_evaluatesCorrectly(String description, String expression,
                Value expected) {
            assertCompilesTo(expression, expected);
        }

        static Stream<Arguments> when_booleanWithComparisons_then_evaluatesCorrectly() {
            return Stream.of(arguments("comparison before and", "3 < 5 && 7 > 4", Value.TRUE),
                    arguments("comparison before or", "3 > 5 || 7 < 4", Value.FALSE),
                    arguments("complex boolean", "(5 > 3) && (2 < 4) || false", Value.TRUE),
                    arguments("negated comparison", "!(3 > 5) && (1 == 1)", Value.TRUE));
        }
    }

    @Nested
    @DisplayName("Comparison and Containment")
    class ComparisonAndContainment {

        @MethodSource
        @ParameterizedTest(name = "{0}")
        void when_inOperator_then_evaluatesCorrectly(String description, String expression, Value expected) {
            assertCompilesTo(expression, expected);
        }

        // @formatter:off
        static Stream<Arguments> when_inOperator_then_evaluatesCorrectly() {
            return Stream.of(
                // Array containment
                arguments("in array found", "3 in [1, 2, 3, 4]", Value.TRUE),
                arguments("in array not found", "5 in [1, 2, 3, 4]", Value.FALSE),
                arguments("string in array",
                    """
                    "read" in ["read", "write"]
                    """,
                    Value.TRUE),
                arguments("null in array", "null in [null, 1, 2]", Value.TRUE),
                arguments("boolean in array", "true in [true, false]", Value.TRUE),
                // Object VALUE containment (not keys!)
                arguments("value in object found",
                    """
                    "Innsmouth" in {name: "Innsmouth", pop: 1000}
                    """,
                    Value.TRUE),
                arguments("number in object",
                    """
                    1000 in {name: "Innsmouth", pop: 1000}
                    """,
                    Value.TRUE),
                arguments("key not in object values",
                    """
                    "name" in {name: "value"}
                    """,
                    Value.FALSE),
                // Substring in string
                arguments("substring found",
                    """
                    "ell" in "hello"
                    """,
                    Value.TRUE),
                arguments("substring not found",
                    """
                    "xyz" in "hello"
                    """,
                    Value.FALSE),
                arguments("empty substring",
                    """
                    "" in "hello"
                    """,
                    Value.TRUE));
        }
        // @formatter:on

        @MethodSource
        @ParameterizedTest(name = "{0}")
        void when_regexMatch_then_evaluatesCorrectly(String description, String expression, Value expected) {
            assertCompilesTo(expression, expected);
        }

        // @formatter:off
        static Stream<Arguments> when_regexMatch_then_evaluatesCorrectly() {
            return Stream.of(
                arguments("simple regex",
                    """
                    "hello" =~ "hel.*"
                    """,
                    Value.TRUE),
                arguments("anchor regex",
                    """
                    "hello" =~ "^h.*o$"
                    """,
                    Value.TRUE),
                arguments("no match",
                    """
                    "hello" =~ "world"
                    """,
                    Value.FALSE),
                arguments("prefix match",
                    """
                    "Stormbringer" =~ "^Storm.*"
                    """,
                    Value.TRUE),
                arguments("email pattern",
                    """
                    "test@example.com" =~ ".*@.*\\\\.com"
                    """,
                    Value.TRUE),
                arguments("digit pattern",
                    """
                    "abc123" =~ ".*\\\\d+"
                    """,
                    Value.TRUE));
        }
        // @formatter:on

        @Test
        @DisplayName("regex on non-string returns false")
        void when_regexOnNonString_then_returnsFalse() {
            assertCompilesTo("""
                    42 =~ "\\\\d+"
                    """, Value.FALSE);
        }
    }

    @Nested
    @DisplayName("Filter by Membership Pattern")
    class FilterByMembership {

        @MethodSource
        @ParameterizedTest(name = "{0}")
        void when_filterByMembership_then_filtersCorrectly(String description, String expression, Value expected) {
            assertCompilesTo(expression, expected);
        }

        // @formatter:off
        static Stream<Arguments> when_filterByMembership_then_filtersCorrectly() {
            return Stream.of(
                arguments("single match",
                    """
                    [{"class": "energy", "entities": ["OilCorp", "GasGiant"]},
                     {"class": "banking", "entities": ["GlobalBank"]}]
                    [?("OilCorp" in @.entities)].class
                    """,
                    Value.ofArray(Value.of("energy"))),
                arguments("no match",
                    """
                    [{"class": "energy", "entities": ["OilCorp"]},
                     {"class": "banking", "entities": ["GlobalBank"]}]
                    [?("Unknown" in @.entities)].class
                    """,
                    Value.EMPTY_ARRAY),
                arguments("multiple matches",
                    """
                    [{"class": "energy", "entities": ["Shared"]},
                     {"class": "banking", "entities": ["Shared"]}]
                    [?("Shared" in @.entities)].class
                    """,
                    Value.ofArray(Value.of("energy"), Value.of("banking"))));
        }
        // @formatter:on

        @Test
        @DisplayName("key step on array of objects projects key across elements")
        void when_keyStepOnArrayOfObjects_then_projects() {
            val compiled = compileExpression("""
                    [{"class": "energy"}, {"class": "banking"}].class
                    """);
            assertThat(compiled).isEqualTo(Value.ofArray(Value.of("energy"), Value.of("banking")));
        }
    }

    @Nested
    @DisplayName("Temporal Functions")
    class TemporalFunctions {

        @MethodSource
        @ParameterizedTest(name = "{0}")
        void when_temporalFunction_then_constantFolds(String description, String expression, Value expected) {
            val compiled = compileExpression(expression, contextWithFunctions);
            assertThat(compiled).isInstanceOf(Value.class).isEqualTo(expected);
        }

        // @formatter:off
        static Stream<Arguments> when_temporalFunction_then_constantFolds() {
            return Stream.of(
                // Duration conversions
                arguments("seconds to ms", "time.durationOfSeconds(60)", Value.of(60000)),
                arguments("minutes to ms", "time.durationOfMinutes(5)", Value.of(300000)),
                arguments("hours to ms", "time.durationOfHours(2)", Value.of(7200000)),
                arguments("days to ms", "time.durationOfDays(1)", Value.of(86400000)),
                // Epoch conversions
                arguments("epoch second", "time.ofEpochSecond(0)", Value.of("1970-01-01T00:00:00Z")),
                arguments("epoch milli", "time.ofEpochMilli(0)", Value.of("1970-01-01T00:00:00Z")),
                // Date/time extraction
                arguments("hour extraction",
                    """
                    time.hourOf("2021-11-08T13:17:23Z")
                    """,
                    Value.of(13)),
                arguments("minute extraction",
                    """
                    time.minuteOf("2021-11-08T13:17:23Z")
                    """,
                    Value.of(17)),
                arguments("second extraction",
                    """
                    time.secondOf("2021-11-08T13:00:23Z")
                    """,
                    Value.of(23)),
                arguments("day of year",
                    """
                    time.dayOfYear("2021-11-08T13:00:00Z")
                    """,
                    Value.of(312)),
                arguments("week of year",
                    """
                    time.weekOfYear("2021-11-08T13:00:00Z")
                    """,
                    Value.of(45)),
                // Date arithmetic
                arguments("plus days",
                    """
                    time.plusDays("2021-11-08T13:00:00Z", 5)
                    """,
                    Value.of("2021-11-13T13:00:00Z")),
                arguments("minus days",
                    """
                    time.minusDays("2021-11-08T13:00:00Z", 5)
                    """,
                    Value.of("2021-11-03T13:00:00Z")),
                arguments("plus seconds",
                    """
                    time.plusSeconds("2021-11-08T13:00:00Z", 10)
                    """,
                    Value.of("2021-11-08T13:00:10Z")),
                // Date comparisons
                arguments("before",
                    """
                    time.before("2021-11-08T13:00:00Z", "2021-11-08T13:00:01Z")
                    """,
                    Value.TRUE),
                arguments("after",
                    """
                    time.after("2021-11-08T13:00:01Z", "2021-11-08T13:00:00Z")
                    """,
                    Value.TRUE),
                arguments("between",
                    """
                    time.between("2021-11-08T13:00:00Z", "2021-11-07T13:00:00Z", "2021-11-09T13:00:00Z")
                    """,
                    Value.TRUE),
                // Temporal bounds
                arguments("start of day",
                    """
                    time.startOfDay("2021-11-08T13:45:30Z")
                    """,
                    Value.of("2021-11-08T00:00:00Z")),
                arguments("start of month",
                    """
                    time.startOfMonth("2021-11-08T13:45:30Z")
                    """,
                    Value.of("2021-11-01T00:00:00Z")),
                arguments("start of year",
                    """
                    time.startOfYear("2021-11-08T13:45:30Z")
                    """,
                    Value.of("2021-01-01T00:00:00Z")),
                // Validation
                arguments("valid UTC",
                    """
                    time.validUTC("2021-11-08T13:00:00Z")
                    """,
                    Value.TRUE),
                arguments("invalid UTC",
                    """
                    time.validUTC("invalid")
                    """,
                    Value.FALSE),
                // Nested temporal
                arguments("nested temporal",
                    """
                    time.hourOf(time.plusDays("2021-11-08T13:00:00Z", 1))
                    """,
                    Value.of(13)),
                arguments("duration from hour",
                    """
                    time.durationOfMinutes(time.hourOf("2021-11-08T02:00:00Z"))
                    """,
                    Value.of(120000)));
        }
        // @formatter:on
    }

    @Nested
    @DisplayName("Function Integration")
    class FunctionIntegration {

        @MethodSource
        @ParameterizedTest(name = "{0}")
        void when_functionCall_then_evaluatesCorrectly(String description, String expression, Value expected) {
            val compiled = compileExpression(expression, contextWithFunctions);
            assertThat(compiled).isEqualTo(expected);
        }

        // @formatter:off
        static Stream<Arguments> when_functionCall_then_evaluatesCorrectly() {
            return Stream.of(
                arguments("string length",
                    """
                    standard.length("hello")
                    """,
                    Value.of(5)),
                arguments("array length", "standard.length([1, 2, 3])", Value.of(3)),
                arguments("object length", "standard.length({a: 1, b: 2})", Value.of(2)),
                arguments("string concat",
                    """
                    string.concat("hello", "world")
                    """,
                    Value.of("helloworld")),
                arguments("nested function",
                    """
                    standard.length(string.concat("ab", "cde"))
                    """,
                    Value.of(5)),
                arguments("function in arithmetic",
                    """
                    standard.length("test") + 10
                    """,
                    Value.of(14)),
                arguments("function in comparison",
                    """
                    standard.length("hello") > 3
                    """,
                    Value.TRUE));
        }
        // @formatter:on

        @Test
        @DisplayName("function in condition step constant folds")
        void when_functionInCondition_then_constantFolds() {
            val compiled = compileExpression("""
                    ["a", "ab", "abc"][?(standard.length(@) > 1)]
                    """, contextWithFunctions);
            assertThat(compiled).isInstanceOf(Value.class).isEqualTo(Value.ofArray(Value.of("ab"), Value.of("abc")));
        }
    }

    @Nested
    @DisplayName("Nested Relative Values")
    class NestedRelativeValues {

        @Test
        @DisplayName("nested condition steps - outer @ shadows inner")
        void when_nestedConditionSteps_then_outerShadowsInner() {
            val compiled   = compileExpression("[[1, 2], [3, 4]][?(@[0] > 1)]");
            val innerArray = Value.ofArray(Value.of(3), Value.of(4));
            assertThat(compiled).isInstanceOf(Value.class).isEqualTo(Value.ofArray(java.util.List.of(innerArray)));
        }

        @Test
        @DisplayName("nested condition with inner array iteration")
        void when_nestedConditionWithInnerIteration_then_correctlyScopes() {
            val compiled   = compileExpression("[[1, 2, 3], [4, 5, 6]][?(@[?(# == 0)][0] < 3)]");
            val innerArray = Value.ofArray(Value.of(1), Value.of(2), Value.of(3));
            assertThat(compiled).isInstanceOf(Value.class).isEqualTo(Value.ofArray(java.util.List.of(innerArray)));
        }

        @Test
        @DisplayName("deeply nested conditions correctly scope relative values")
        void when_deeplyNestedConditions_then_correctlyScopes() {
            val compiled  = compileExpression("[[[1, 2]], [[3, 4]]][?(@[0][?(# == 0)][0] == 1)]");
            val innermost = Value.ofArray(Value.of(1), Value.of(2));
            val middle    = Value.ofArray(java.util.List.of(innermost));
            assertThat(compiled).isInstanceOf(Value.class).isEqualTo(Value.ofArray(java.util.List.of(middle)));
        }

        @Test
        @DisplayName("@ used directly returns undefined")
        void when_atUsedDirectly_then_returnsUndefined() {
            val compiled = compileExpression("@");
            assertThat(compiled).isInstanceOf(PureOperator.class);
            val result = ((PureOperator) compiled).evaluate(emptyEvaluationContext());
            assertThat(result).isEqualTo(Value.UNDEFINED);
        }

        @Test
        @DisplayName("# used directly returns undefined")
        void when_hashUsedDirectly_then_returnsUndefined() {
            val compiled = compileExpression("#");
            assertThat(compiled).isInstanceOf(PureOperator.class);
            val result = ((PureOperator) compiled).evaluate(emptyEvaluationContext());
            assertThat(result).isEqualTo(Value.UNDEFINED);
        }
    }

    @Nested
    @DisplayName("Eager Boolean Operators")
    class EagerBooleanOperators {

        @MethodSource
        @ParameterizedTest(name = "{0}")
        void when_eagerOperator_then_evaluatesCorrectly(String description, String expression, Value expected) {
            assertCompilesTo(expression, expected);
        }

        static Stream<Arguments> when_eagerOperator_then_evaluatesCorrectly() {
            return Stream.of(
                    // Eager OR (|) - aliased to lazy OR
                    arguments("eager or true|false", "true | false", Value.TRUE),
                    arguments("eager or false|true", "false | true", Value.TRUE),
                    arguments("eager or false|false", "false | false", Value.FALSE),
                    // Eager AND (&) - aliased to lazy AND
                    arguments("eager and true&true", "true & true", Value.TRUE),
                    arguments("eager and true&false", "true & false", Value.FALSE),
                    arguments("eager and false&false", "false & false", Value.FALSE),
                    // Mixed lazy and eager
                    arguments("mixed lazy and eager 1", "(true || false) & (false | true)", Value.TRUE),
                    arguments("mixed lazy and eager 2", "(false && true) | (true && true)", Value.TRUE),
                    arguments("mixed lazy and eager 3", "(true || true) & false", Value.FALSE),
                    arguments("mixed lazy and eager 4", "(false && false) | true", Value.TRUE));
        }
    }

    @Nested
    @DisplayName("Key Projection on Arrays")
    class KeyProjectionOnArrays {

        @MethodSource
        @ParameterizedTest(name = "{0}")
        void when_keyStepOnArray_then_projectsCorrectly(String description, String expression, Value expected) {
            assertCompilesTo(expression, expected);
        }

        static Stream<Arguments> when_keyStepOnArray_then_projectsCorrectly() {
            return Stream.of(
                    // Key on array of non-objects returns empty array
                    arguments("key on number array", "[1, 2, 3].name", Value.EMPTY_ARRAY),
                    arguments("key on string array", "[\"a\", \"b\", \"c\"].key", Value.EMPTY_ARRAY),
                    arguments("key on boolean array", "[true, false].field", Value.EMPTY_ARRAY),
                    // Mixed array - only objects with key contribute
                    arguments("mixed array key projection", "[{a: 1}, 5, {a: 3}].a",
                            Value.ofArray(Value.of(1), Value.of(3))),
                    arguments("mixed array missing key", "[{a: 1}, {b: 2}, {a: 3}].a",
                            Value.ofArray(Value.of(1), Value.of(3))),
                    // Empty array
                    arguments("key on empty array", "[].name", Value.EMPTY_ARRAY),
                    // Objects with key
                    arguments("key on object array", "[{name: \"Alice\"}, {name: \"Bob\"}].name",
                            Value.ofArray(Value.of("Alice"), Value.of("Bob"))));
        }
    }

    @Nested
    @DisplayName("Undefined Equality Comparisons")
    class UndefinedEquality {

        @MethodSource
        @ParameterizedTest(name = "{0}")
        void when_undefinedComparison_then_evaluatesCorrectly(String description, String expression, Value expected) {
            assertCompilesTo(expression, expected);
        }

        static Stream<Arguments> when_undefinedComparison_then_evaluatesCorrectly() {
            return Stream.of(
                    // Undefined equality
                    arguments("undefined == undefined", "undefined == undefined", Value.TRUE),
                    arguments("undefined != undefined", "undefined != undefined", Value.FALSE),
                    arguments("undefined == null", "undefined == null", Value.FALSE),
                    arguments("undefined == 42", "undefined == 42", Value.FALSE),
                    // Undefined containment - undefined is never found
                    arguments("undefined in array with undefined", "undefined in [undefined, 1, 2]", Value.FALSE),
                    arguments("undefined in array without undefined", "undefined in [1, 2, 3]", Value.FALSE));
        }
    }

    @Nested
    @DisplayName("Condition Step Error Cases")
    class ConditionStepErrors {

        @MethodSource
        @ParameterizedTest(name = "{0}")
        void when_conditionNonBoolean_then_returnsError(String description, String expression) {
            val result = evaluateExpression(expression);
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message().toLowerCase()).contains("condition");
        }

        static Stream<Arguments> when_conditionNonBoolean_then_returnsError() {
            return Stream.of(arguments("number condition on number", "42[?(@ + 1)]"),
                    arguments("number condition on string", "\"text\"[?(123)]"),
                    arguments("arithmetic condition on array", "[1, 2, 3][?(@ * 2)]"),
                    arguments("arithmetic condition on object", "{\"a\": 1, \"b\": 2}[?(@ + 5)]"));
        }
    }

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_compileExpression_then_returnsExpectedValue(String description, String expression,
            CompiledExpression expected) {
        val actual = evaluateExpression(expression);
        assertThat(actual).isEqualTo(expected);
    }

    // @formatter:off
    private static Stream<Arguments> when_compileExpression_then_returnsExpectedValue() {
        return Stream.of(
            // Literals
            arguments("integer", "42", Value.of(42)),
            arguments("decimal", "2.5", Value.of(2.5)),
            arguments("zero", "0", Value.of(0)),
            arguments("string",
                """
                "hello"
                """,
                Value.of("hello")),
            arguments("empty string",
                """
                ""
                """,
                Value.of("")),
            arguments("true", "true", Value.TRUE),
            arguments("false", "false", Value.FALSE),
            arguments("null", "null", Value.NULL),
            arguments("undefined", "undefined", Value.UNDEFINED),
            // Parenthesized
            arguments("parenthesized integer", "(42)", Value.of(42)),
            arguments("parenthesized string",
                """
                ("test")
                """,
                Value.of("test")),
            arguments("parenthesized boolean", "(true)", Value.TRUE),
            arguments("nested parentheses", "((42))", Value.of(42)),
            arguments("deeply nested", "(((false)))", Value.FALSE));
    }
    // @formatter:on

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_compileSubscriptionElement_then_returnsSubscriptionValue(String description, String expression,
            CompiledExpression expected) {
        val ctx    = subscriptionContext();
        val actual = evaluateExpression(expression, ctx);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_compileSubscriptionElement_then_returnsSubscriptionValue() {
        return Stream.of(arguments("subject", "subject", Value.of("alice")),
                arguments("action", "action", Value.of("read")),
                arguments("resource", "resource", Value.of("document")),
                arguments("environment", "environment", Value.of("production")));
    }

    private static EvaluationContext subscriptionContext() {
        val subscription = new AuthorizationSubscription(Value.of("alice"), Value.of("read"), Value.of("document"),
                Value.of("production"));
        return new EvaluationContext(null, null, null, subscription, functionBroker, DEFAULT_ATTRIBUTE_BROKER);
    }

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_compileLiteralExpression_then_returnsResult(String description, String expression, Object expected) {
        val actual = evaluateExpression(expression);
        if (expected instanceof Class<?> c)
            assertThat(actual).isInstanceOf(c);
        else
            assertThat(actual).isEqualTo(expected);
    }

    // @formatter:off
    private static Stream<Arguments> when_compileLiteralExpression_then_returnsResult() {
        return Stream.of(
            // Logical NOT
            arguments("not true", "!true", Value.FALSE),
            arguments("not false", "!false", Value.TRUE),
            arguments("double negation", "!!true", Value.TRUE),
            arguments("not integer", "!5", ErrorValue.class),
            arguments("not string",
                """
                !"text"
                """,
                ErrorValue.class),
            arguments("not null", "!null", ErrorValue.class),
            // Unary minus
            arguments("negate positive", "-5", Value.of(-5)),
            arguments("negate negative", "-(-5)", Value.of(5)),
            arguments("negate zero", "-0", Value.of(0)),
            arguments("negate decimal", "-2.5", Value.of(-2.5)),
            arguments("negate boolean", "-true", ErrorValue.class),
            arguments("negate string",
                """
                -"text"
                """,
                ErrorValue.class),
            arguments("negate null", "-null", ErrorValue.class),
            // Unary plus
            arguments("plus positive", "+5", Value.of(5)),
            arguments("plus negative", "+(-5)", Value.of(-5)),
            arguments("plus zero", "+0", Value.of(0)),
            arguments("plus boolean", "+true", ErrorValue.class),
            arguments("plus string",
                """
                +"text"
                """,
                ErrorValue.class),
            arguments("plus null", "+null", ErrorValue.class),
            // Arrays - Empty and simple
            arguments("empty array", "[]", Value.EMPTY_ARRAY),
            arguments("single element", "[1]", Value.ofArray(Value.of(1))),
            arguments("two elements", "[1, 2]", Value.ofArray(Value.of(1), Value.of(2))),
            arguments("three elements", "[1, 2, 3]", Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
            // Arrays - Mixed types
            arguments("mixed types",
                """
                [1, "a", true]
                """,
                Value.ofArray(Value.of(1), Value.of("a"), Value.TRUE)),
            // Arrays - Nested
            arguments("nested array", "[[1, 2], [3, 4]]",
                Value.ofArray(Value.ofArray(Value.of(1), Value.of(2)),
                        Value.ofArray(Value.of(3), Value.of(4)))),
            // Arrays - Undefined handling
            arguments("undefined dropped", "[1, undefined, 2]", Value.ofArray(Value.of(1), Value.of(2))),
            arguments("all undefined", "[undefined, undefined]", Value.EMPTY_ARRAY),
            // Arrays - Expressions as elements
            arguments("expression elements", "[!false, -5]", Value.ofArray(Value.TRUE, Value.of(-5))),
            // Arrays - Error propagation
            arguments("array error propagates", "[1, !5, 2]", ErrorValue.class),
            // Objects - Empty and simple
            arguments("empty object", "{}", Value.EMPTY_OBJECT),
            arguments("single property", "{a: 1}", obj("a", Value.of(1))),
            arguments("two properties", "{a: 1, b: 2}", obj("a", Value.of(1), "b", Value.of(2))),
            // Objects - Mixed value types
            arguments("mixed value types",
                """
                {n: 1, s: "x", b: true}
                """,
                obj("n", Value.of(1), "s", Value.of("x"), "b", Value.TRUE)),
            // Objects - Nested
            arguments("nested object", "{outer: {inner: 1}}", obj("outer", obj("inner", Value.of(1)))),
            arguments("object with array", "{arr: [1, 2]}", obj("arr", Value.ofArray(Value.of(1), Value.of(2)))),
            // Objects - Undefined handling
            arguments("undefined value dropped", "{a: 1, b: undefined, c: 2}",
                obj("a", Value.of(1), "c", Value.of(2))),
            arguments("all undefined values", "{a: undefined, b: undefined}", Value.EMPTY_OBJECT),
            // Objects - Expressions as values
            arguments("expression values", "{neg: -5, not: !false}", obj("neg", Value.of(-5), "not", Value.TRUE)),
            // Objects - Error propagation
            arguments("object error propagates", "{a: 1, b: !5, c: 2}", ErrorValue.class));
    }
    // @formatter:on

    @MethodSource("constantFoldingCases")
    @ParameterizedTest(name = "constant folding: {0}")
    void when_allLiterals_then_constantFoldsToValue(String description, String expression, Value expected) {
        val compiled = compileExpression(expression);
        assertThat(compiled).as("should constant-fold to Value, not PureOperator").isInstanceOf(Value.class)
                .isEqualTo(expected);
    }

    // @formatter:off
    private static Stream<Arguments> constantFoldingCases() {
        return Stream.of(
            // Arrays
            arguments("empty array", "[]", Value.EMPTY_ARRAY),
            arguments("single literal", "[1]", Value.ofArray(Value.of(1))),
            arguments("multiple literals", "[1, 2, 3]", Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
            arguments("mixed literal types",
                """
                [1, "a", true, null]
                """,
                Value.ofArray(Value.of(1), Value.of("a"), Value.TRUE, Value.NULL)),
            arguments("nested literal arrays", "[[1, 2], [3, 4]]",
                Value.ofArray(Value.ofArray(Value.of(1), Value.of(2)),
                        Value.ofArray(Value.of(3), Value.of(4)))),
            arguments("array with undefined dropped", "[1, undefined, 2]", Value.ofArray(Value.of(1), Value.of(2))),
            // Objects
            arguments("empty object", "{}", Value.EMPTY_OBJECT),
            arguments("single property", "{a: 1}", obj("a", Value.of(1))),
            arguments("multiple properties", "{a: 1, b: 2, c: 3}",
                obj("a", Value.of(1), "b", Value.of(2), "c", Value.of(3))),
            arguments("mixed value types",
                """
                {n: 1, s: "x", b: true, nil: null}
                """,
                obj("n", Value.of(1), "s", Value.of("x"), "b", Value.TRUE, "nil", Value.NULL)),
            arguments("nested literal objects", "{outer: {inner: 1}}", obj("outer", obj("inner", Value.of(1)))),
            arguments("object with undefined dropped", "{a: 1, b: undefined, c: 2}",
                obj("a", Value.of(1), "c", Value.of(2))),
            // Mixed nesting
            arguments("object with array value", "{arr: [1, 2]}",
                obj("arr", Value.ofArray(Value.of(1), Value.of(2)))),
            arguments("array with object element", "[{a: 1}, {b: 2}]",
                Value.ofArray(obj("a", Value.of(1)), obj("b", Value.of(2)))));
    }
    // @formatter:on

    @MethodSource("pureOperatorCases")
    @ParameterizedTest(name = "pure operator: {0}")
    void when_containsVariableReference_then_returnsPureOperatorWithCorrectValue(String description, String expression,
            Value varValue, Value expected) {
        val compiled = compileExpression(expression);
        assertThat(compiled).as("should be PureOperator when containing variable reference")
                .isInstanceOf(PureOperator.class);

        val ctx    = withVariables(java.util.Map.of("x", varValue));
        val result = ((PureOperator) compiled).evaluate(ctx);
        assertThat(result).isEqualTo(expected);
    }

    private static Stream<Arguments> pureOperatorCases() {
        return Stream.of(
                // Arrays with variable
                arguments("array with single var", "[x]", Value.of(42), Value.ofArray(Value.of(42))),
                arguments("array with var and literal", "[1, x, 3]", Value.of(2),
                        Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
                arguments("array with undefined var dropped", "[1, x, 3]", Value.UNDEFINED,
                        Value.ofArray(Value.of(1), Value.of(3))),
                arguments("array with var at start", "[x, 2, 3]", Value.of(1),
                        Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
                arguments("array with var at end", "[1, 2, x]", Value.of(3),
                        Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
                // Objects with variable
                arguments("object with single var value", "{a: x}", Value.of(42), obj("a", Value.of(42))),
                arguments("object with var and literal", "{a: 1, b: x, c: 3}", Value.of(2),
                        obj("a", Value.of(1), "b", Value.of(2), "c", Value.of(3))),
                arguments("object with undefined var dropped", "{a: 1, b: x, c: 3}", Value.UNDEFINED,
                        obj("a", Value.of(1), "c", Value.of(3))),
                // Nested with variable
                arguments("nested array with var", "[[x, 2], [3, 4]]", Value.of(1),
                        Value.ofArray(Value.ofArray(Value.of(1), Value.of(2)),
                                Value.ofArray(Value.of(3), Value.of(4)))),
                arguments("nested object with var", "{outer: {inner: x}}", Value.of(99),
                        obj("outer", obj("inner", Value.of(99)))),
                arguments("object with array containing var", "{arr: [x, 2]}", Value.of(1),
                        obj("arr", Value.ofArray(Value.of(1), Value.of(2)))));
    }

}
