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

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.util.ExpressionTestUtil.compileExpression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.libraries.FilterFunctionLibrary;
import io.sapl.util.SimpleFunctionLibrary;
import lombok.val;
import reactor.core.publisher.Flux;

/**
 * Tests for filter expressions (|- operator) and subtemplates (:: operator).
 * <p>
 * NOTE: These tests are expected to FAIL until FilterOperation is implemented.
 * They are ported from sapl-pdp FilterCompilerTests.java to serve as a
 * specification.
 */
@DisplayName("Filter Expressions")
class FilterExpressionTests {

    private static CompilationContext compilationContext;
    private static EvaluationContext  evaluationContext;

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

    @BeforeAll
    static void setupFunctionBroker() {
        val broker = new DefaultFunctionBroker();
        broker.loadStaticFunctionLibrary(FilterFunctionLibrary.class);
        broker.loadStaticFunctionLibrary(SimpleFunctionLibrary.class);
        compilationContext = new CompilationContext(broker, DEFAULT_ATTRIBUTE_BROKER);
        evaluationContext  = new EvaluationContext(null, null, null, null, broker, DEFAULT_ATTRIBUTE_BROKER);
    }

    @Nested
    @DisplayName("Basic Filter Operations")
    class BasicFilterOperations {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        void when_basicFilterApplied_then_producesExpectedResult(String description, String expression,
                Value expected) {
            assertEvaluatesTo(expression, expected);
        }

        static Stream<Arguments> when_basicFilterApplied_then_producesExpectedResult() {
            return Stream.of(
                    // NOTE: These require filter.blacken function - will fail without it
                    arguments("Blacken filter on string", "\"secret\" |- filter.blacken", Value.of("XXXXXX")),
                    arguments("Blacken filter on longer string", "\"password\" |- filter.blacken",
                            Value.of("XXXXXXXX")),
                    // Simple value transforms
                    arguments("Filter on number works", "42 |- simple.doubleValue", Value.of(84)),
                    arguments("Filter on boolean works", "true |- simple.negate", Value.FALSE));
        }

        @Test
        @DisplayName("filter.remove on object returns undefined")
        void when_removeFilterOnObject_then_returnsUndefined() {
            assertEvaluatesTo("{} |- filter.remove", Value.UNDEFINED);
        }

        @Test
        @DisplayName("filter.remove on null returns undefined")
        void when_removeFilterOnNull_then_returnsUndefined() {
            assertEvaluatesTo("null |- filter.remove", Value.UNDEFINED);
        }
    }

    @Nested
    @DisplayName("Each Filter Operations")
    class EachFilterOperations {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        void when_eachFilterApplied_then_producesExpectedResult(String description, String expression, Value expected) {
            assertEvaluatesTo(expression, expected);
        }

        static Stream<Arguments> when_eachFilterApplied_then_producesExpectedResult() {
            return Stream.of(
                    arguments("Each removes all elements", "[null, 5] |- each filter.remove", Value.EMPTY_ARRAY),
                    arguments("Empty array unchanged", "[] |- each filter.remove", Value.EMPTY_ARRAY),
                    arguments("Each doubles numbers", "[1, 2, 3] |- each simple.doubleValue",
                            Value.ofArray(Value.of(2), Value.of(4), Value.of(6))),
                    arguments("Each removes all returns empty", "[null, null, null] |- each filter.remove",
                            Value.EMPTY_ARRAY),
                    arguments("Each negates booleans", "[true, false, true] |- each simple.negate",
                            Value.ofArray(Value.FALSE, Value.TRUE, Value.FALSE)));
        }
    }

    @Nested
    @DisplayName("Extended Filter Operations")
    class ExtendedFilterOperations {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        void when_extendedFilterApplied_then_producesExpectedResult(String description, String expression,
                Value expected) {
            assertEvaluatesTo(expression, expected);
        }

        static Stream<Arguments> when_extendedFilterApplied_then_producesExpectedResult() {
            return Stream.of(
                    arguments("Extended filter with single statement", "\"test\" |- { : filter.blacken }",
                            Value.of("XXXX")),
                    arguments("Extended filter with multiple statements",
                            "5 |- { : simple.doubleValue, : simple.doubleValue }", Value.of(20)),
                    arguments("Extended filter replace value", "\"old\" |- { : filter.replace(\"new\") }",
                            Value.of("new")),
                    arguments("Extended filter with target path filters field",
                            "{ \"name\": \"secret\" } |- { @.name : filter.blacken }",
                            Value.ofObject(Map.of("name", Value.of("XXXXXX")))),
                    arguments("Extended filter with target path removes field",
                            "{ \"name\": \"test\", \"age\": 42 } |- { @.name : filter.remove }",
                            Value.ofObject(Map.of("age", Value.of(42)))),
                    arguments("Extended filter with index path transforms element",
                            "[1, 2, 3] |- { @[1] : simple.doubleValue }",
                            Value.ofArray(Value.of(1), Value.of(4), Value.of(3))),
                    arguments("Extended filter with index path removes element",
                            "[1, 2, 3] |- { @[1] : filter.remove }", Value.ofArray(Value.of(1), Value.of(3))),
                    arguments("Extended filter with slicing transforms range",
                            "[1, 2, 3, 4, 5] |- { @[1:3] : simple.doubleValue }",
                            Value.ofArray(Value.of(1), Value.of(4), Value.of(6), Value.of(4), Value.of(5))));
        }
    }

    @Nested
    @DisplayName("Wildcard Filters")
    class WildcardFilters {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        void when_wildcardFilterApplied_then_producesExpectedResult(String description, String expression,
                Value expected) {
            assertEvaluatesTo(expression, expected);
        }

        static Stream<Arguments> when_wildcardFilterApplied_then_producesExpectedResult() {
            return Stream.of(
                    arguments("Wildcard filter on object applies to all fields",
                            "{ \"a\": 10, \"b\": 20 } |- { @.* : simple.doubleValue }",
                            Value.ofObject(Map.of("a", Value.of(20), "b", Value.of(40)))),
                    arguments("Wildcard filter removes all fields", "{ \"a\": 1, \"b\": 2 } |- { @.* : filter.remove }",
                            Value.EMPTY_OBJECT));
        }
    }

    @Nested
    @DisplayName("Condition Step Filters")
    class ConditionStepFilters {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        void when_conditionStepFilterApplied_then_producesExpectedResult(String description, String expression,
                Value expected) {
            assertEvaluatesTo(expression, expected);
        }

        static Stream<Arguments> when_conditionStepFilterApplied_then_producesExpectedResult() {
            return Stream.of(
                    arguments("Condition step with constant true applies to all",
                            "[1, 2, 3, 4, 5] |- { @[?(true)] : simple.doubleValue }",
                            Value.ofArray(Value.of(2), Value.of(4), Value.of(6), Value.of(8), Value.of(10))),
                    arguments("Condition step with constant false leaves unchanged",
                            "[1, 2, 3, 4, 5] |- { @[?(false)] : simple.doubleValue }",
                            Value.ofArray(Value.of(1), Value.of(2), Value.of(3), Value.of(4), Value.of(5))),
                    arguments("Condition step removes matching elements",
                            "[1, 2, 3, 4, 5] |- { @[?(true)] : filter.remove }", Value.EMPTY_ARRAY));
        }
    }

    @Nested
    @DisplayName("Index Union Filters")
    class IndexUnionFilters {

        @Test
        @DisplayName("Index union removes array elements at union indices")
        void when_indexUnionFilterApplied_then_removesCorrectElements() {
            val expression = "[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [3,1,2,3], [4,1,2,3] ] |- { @[1,3] : filter.remove }";
            val expected   = Value.ofArray(Value.ofArray(Value.of(0), Value.of(1), Value.of(2), Value.of(3)),
                    Value.ofArray(Value.of(2), Value.of(1), Value.of(2), Value.of(3)),
                    Value.ofArray(Value.of(4), Value.of(1), Value.of(2), Value.of(3)));
            assertEvaluatesTo(expression, expected);
        }
    }

    @Nested
    @DisplayName("Attribute Union Filters")
    class AttributeUnionFilters {

        @Test
        @DisplayName("Attribute union removes fields in union")
        void when_attributeUnionFilterApplied_then_removesCorrectFields() {
            val expression = "{ \"a\" : 1, \"b\" : 2, \"c\" : 3, \"d\" : 4 } |- { @[\"b\" , \"d\"] : filter.remove }";
            val expected   = Value.ofObject(Map.of("a", Value.of(1), "c", Value.of(3)));
            assertEvaluatesTo(expression, expected);
        }
    }

    @Nested
    @DisplayName("Filter Error Cases")
    class FilterErrorCases {

        @ParameterizedTest(name = "{0} - {1}")
        @MethodSource
        void when_errorCondition_then_producesError(String description, String expression, String errorFragment) {
            val result = evaluate(expression);
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message().toLowerCase()).contains(errorFragment.toLowerCase());
        }

        static Stream<Arguments> when_errorCondition_then_producesError() {
            return Stream.of(arguments("Error propagates from parent", "(10/0) |- filter.remove", "division by zero"),
                    arguments("Extended filter error in parent propagates", "(10/0) |- { : filter.remove }",
                            "division by zero"));
        }

        // Blacklist semantics: path type mismatches return unchanged, not error
        @ParameterizedTest(name = "{0}")
        @MethodSource
        void when_pathTypeMismatch_then_returnsUnchanged(String description, String expression, String expected) {
            val result = evaluate(expression);
            assertThat(result).isEqualTo(json(expected));
        }

        static Stream<Arguments> when_pathTypeMismatch_then_returnsUnchanged() {
            return Stream.of(
                    arguments("Extended filter key path on non-object", "42 |- { @.field : filter.blacken }", "42"),
                    arguments("Extended filter index path out of bounds", "[1, 2, 3] |- { @[10] : simple.doubleValue }",
                            "[1, 2, 3]"),
                    arguments("Extended filter index path on non-array", "{} |- { @[0] : simple.doubleValue }", "{}"),
                    arguments("Extended filter slice on non-array", "\"text\" |- { @[1:3] : filter.blacken }",
                            "\"text\""));
        }
    }

    @Nested
    @DisplayName("Each Filter Error Short-Circuit")
    class EachFilterErrorShortCircuit {

        @Test
        @DisplayName("Each filter short-circuits on first type error in array")
        void when_eachFilterEncountersTypeError_then_shortCircuitsAndReturnsError() {
            // doubleValue requires number, "string" causes type error - should
            // short-circuit
            val result = evaluate("[1, 2, \"string\", 4] |- each simple.doubleValue");
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).contains("number");
        }

        @Test
        @DisplayName("Each filter returns error on first bad element not last")
        void when_eachFilterErrorOnFirstElement_then_returnsErrorImmediately() {
            // First element is a string, should error immediately without processing rest
            val result = evaluate("[\"bad\", 2, 3, 4] |- each simple.doubleValue");
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).contains("number");
        }

        @Test
        @DisplayName("Each filter on object works")
        void when_eachFilterOnObject_then_filterApplied() {
            // Each only works on arrays, not objects
            val result   = evaluate("{\"a\": 1, \"b\": 2, \"c\": 3} |- each simple.doubleValue");
            val expected = json("""
                        {"a": 2, "b": 4, "c": 6}
                    """);
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("Negate filter short-circuits on non-boolean in array")
        void when_negateFilterEncountersNonBoolean_then_shortCircuits() {
            // negate requires boolean, number causes error
            val result = evaluate("[true, false, 42, true] |- each simple.negate");
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).contains("boolean");
        }

        @Test
        @DisplayName("Length filter short-circuits on invalid type")
        void when_lengthFilterEncountersInvalidType_then_shortCircuits() {
            // length requires text or array, number causes error
            val result = evaluate("[[1,2], \"hello\", 42, [3,4]] |- each simple.length");
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).contains("text or array");
        }
    }

    @Nested
    @DisplayName("Subtemplate Operations")
    class SubtemplateOperations {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        void when_subtemplateApplied_then_producesExpectedResult(String description, String expression,
                Value expected) {
            assertEvaluatesTo(expression, expected);
        }

        static Stream<Arguments> when_subtemplateApplied_then_producesExpectedResult() {
            return Stream.of(
                    // Scalar parent: @ = scalar, # = 0, returns single transformed value
                    arguments("Subtemplate on simple value wraps in object", "42 :: { \"value\": @ }",
                            Value.ofObject(Map.of("value", Value.of(42)))),
                    arguments("Subtemplate with multiple fields", "5 :: { \"original\": @, \"doubled\": @ * 2 }",
                            Value.ofObject(Map.of("original", Value.of(5), "doubled", Value.of(10)))),
                    arguments("Subtemplate with arithmetic operations", "10 :: { \"half\": @ / 2, \"double\": @ * 2 }",
                            Value.ofObject(Map.of("half", Value.of(5), "double", Value.of(20)))),
                    arguments("Subtemplate with nested object construction",
                            "42 :: { \"data\": { \"value\": @, \"squared\": @ * @ } }",
                            Value.ofObject(Map.of("data",
                                    Value.ofObject(Map.of("value", Value.of(42), "squared", Value.of(1764)))))),

                    // Array parent: @ = element, # = index, returns array of transformed values
                    arguments("Subtemplate on array maps over each element", "[1, 2, 3] :: { \"num\": @ }",
                            Value.ofArray(Value.ofObject(Map.of("num", Value.of(1))),
                                    Value.ofObject(Map.of("num", Value.of(2))),
                                    Value.ofObject(Map.of("num", Value.of(3))))),
                    arguments("Subtemplate on empty array returns empty", "[] :: { \"value\": @ }", Value.EMPTY_ARRAY),

                    // Object parent: @ = entry value, # = entry key, returns array of transformed
                    // values
                    arguments("Subtemplate on object extracts values", "{ \"name\": \"Alice\", \"age\": 30 } :: @",
                            Value.ofArray(Value.of("Alice"), Value.of(30))),
                    arguments("Subtemplate on object extracts keys", "{ \"name\": \"Alice\", \"age\": 30 } :: #",
                            Value.ofArray(Value.of("name"), Value.of("age"))),
                    arguments("Subtemplate on object wraps values in object",
                            "{ \"a\": 1, \"b\": 2 } :: { \"key\": #, \"value\": @ }",
                            Value.ofArray(Value.ofObject(Map.of("key", Value.of("a"), "value", Value.of(1))),
                                    Value.ofObject(Map.of("key", Value.of("b"), "value", Value.of(2))))));
        }

        @Test
        @DisplayName("Subtemplate on undefined returns undefined")
        void when_subtemplateOnUndefined_then_returnsUndefined() {
            assertEvaluatesTo("undefined :: { \"name\": \"foo\" }", Value.UNDEFINED);
        }

        @Test
        @DisplayName("Subtemplate on filtered array maps over filtered elements")
        void when_subtemplateOnFilteredArray_then_mapsOverFilteredElements() {
            val expression = "[ { \"key1\": 1, \"key2\": 2 }, { \"key1\": 3, \"key2\": 4 }, { \"key1\": 5, \"key2\": 6 } ][?(@.key1 > 2)] :: { \"key20\": @.key2 }";
            val expected   = Value.ofArray(Value.ofObject(Map.of("key20", Value.of(4))),
                    Value.ofObject(Map.of("key20", Value.of(6))));
            assertEvaluatesTo(expression, expected);
        }
    }

    @Nested
    @DisplayName("Subtemplate Error Cases")
    class SubtemplateErrorCases {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        void when_subtemplateWithError_then_producesError(String description, String expression,
                String expectedErrorMsg) {
            val result = evaluate(expression);
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message().toLowerCase()).contains(expectedErrorMsg.toLowerCase());
        }

        static Stream<Arguments> when_subtemplateWithError_then_producesError() {
            return Stream.of(
                    arguments("Subtemplate error propagates from parent", "(10/0) :: { \"value\": @ }",
                            "division by zero"),
                    arguments("Subtemplate propagates division by zero error", "(10/0) :: { \"name\": \"foo\" }",
                            "division by zero"));
        }
    }

    private void assertEvaluatesTo(String expression, Value expected) {
        val result = evaluate(expression);
        assertThat(result).isEqualTo(expected);
    }

    private CompiledExpression evaluate(String expression) {
        val compiled = compileExpression(expression, compilationContext);
        return switch (compiled) {
        case Value v         -> v;
        case PureOperator op -> op.evaluate(evaluationContext);
        default              -> compiled;
        };
    }

}
