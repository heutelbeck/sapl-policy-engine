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

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.*;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.libraries.FilterFunctionLibrary;
import io.sapl.functions.libraries.StandardFunctionLibrary;
import io.sapl.functions.libraries.StringFunctionLibrary;
import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import lombok.val;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class FilterCompilerTests {

    private static CompilationContext contextWithFunctions;
    private static EvaluationContext  evaluationContext;

    @FunctionLibrary(name = "simple", description = "Simple test functions for filter testing")
    public static class SimpleTestFunctionLibrary {

        @Function(docs = "Double a number")
        public static Value doubleValue(NumberValue value) {
            return Value.of(value.value().multiply(BigDecimal.valueOf(2)));
        }

        @Function(docs = "Negate a boolean")
        public static Value negate(BooleanValue value) {
            return Value.of(!value.value());
        }

        @Function(docs = "Append strings")
        public static Value append(TextValue base, TextValue... suffixes) {
            val builder = new StringBuilder(base.value());
            for (val suffix : suffixes) {
                builder.append(suffix.value());
            }
            return Value.of(builder.toString());
        }

        @Function(docs = "Get length of array or string")
        public static Value length(Value value) {
            if (value instanceof ArrayValue arrayValue) {
                return Value.of(arrayValue.size());
            }
            if (value instanceof TextValue textValue) {
                return Value.of(textValue.value().length());
            }
            return Value.error("Cannot get length of %s.".formatted(value.getClass().getSimpleName()));
        }
    }

    @FunctionLibrary(name = "mock", description = "Mock test functions for filter testing")
    public static class MockTestFunctionLibrary {

        @Function(docs = "Return empty string")
        public static Value emptyString(Value... ignored) {
            return Value.of("");
        }

        @Function(docs = "Return nil (null)")
        public static Value nil(Value... ignored) {
            return Value.NULL;
        }
    }

    @BeforeAll
    static void setupFunctionBroker() {
        val broker = new DefaultFunctionBroker();
        broker.loadStaticFunctionLibrary(StandardFunctionLibrary.class);
        broker.loadStaticFunctionLibrary(StringFunctionLibrary.class);
        broker.loadStaticFunctionLibrary(FilterFunctionLibrary.class);
        broker.loadStaticFunctionLibrary(SimpleTestFunctionLibrary.class);
        broker.loadStaticFunctionLibrary(MockTestFunctionLibrary.class);
        contextWithFunctions = new CompilationContext(broker, null);
        evaluationContext    = new EvaluationContext(null, null, null, null, broker, null);
    }

    // ========================================================================
    // Basic Filter Operations
    // ========================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void whenBasicFilterApplied_thenProducesExpectedResult(String description, String expression, Value expected) {
        assertExpressionEvaluatesTo(expression, expected);
    }

    static Stream<Arguments> whenBasicFilterApplied_thenProducesExpectedResult() {
        return Stream.of(
                arguments("Blacken filter on string returns blackened", "\"secret\" |- filter.blacken",
                        Value.of("XXXXXX")),
                arguments("Blacken filter on longer string", "\"password\" |- filter.blacken", Value.of("XXXXXXXX")),
                arguments("Custom function with args", "\"Ben\" |- simple.append(\" from \", \"Berlin\")",
                        Value.of("Ben from Berlin")),
                arguments("Custom function without args", "\"hello\" |- simple.length", Value.of(5)),
                arguments("Filter on number works", "42 |- simple.doubleValue", Value.of(84)),
                arguments("Filter on boolean works", "true |- simple.negate", Value.FALSE),
                arguments("Filter on array applies without each", "[1,2,3] |- simple.length", Value.of(3)));
    }

    @Test
    void whenRemoveFilterAppliedToObject_thenReturnsUndefined() {
        assertExpressionEvaluatesTo("{} |- filter.remove", Value.UNDEFINED);
    }

    @Test
    void whenRemoveFilterAppliedToNull_thenReturnsUndefined() {
        assertExpressionEvaluatesTo("null |- filter.remove", Value.UNDEFINED);
    }

    @Test
    void whenExtendedFilterWithRootRemove_thenReturnsUndefined() {
        assertExpressionEvaluatesTo("{} |- { @ : filter.remove }", Value.UNDEFINED);
    }

    // ========================================================================
    // Each Filter Operations
    // ========================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void whenEachFilterApplied_thenProducesExpectedResult(String description, String expression, Value expected) {
        assertExpressionEvaluatesTo(expression, expected);
    }

    static Stream<Arguments> whenEachFilterApplied_thenProducesExpectedResult() {
        return Stream.of(
                arguments("Each removes all elements when filtering all", "[null, 5] |- each filter.remove",
                        Value.EMPTY_ARRAY),
                arguments("Each applies function to transform elements", "[\"a\", \"b\"] |- each simple.append(\"!\")",
                        Value.ofArray(Value.of("a!"), Value.of("b!"))),
                arguments("Empty array unchanged returns empty", "[] |- each filter.remove", Value.EMPTY_ARRAY),
                arguments("Each doubles numbers", "[1, 2, 3] |- each simple.doubleValue",
                        Value.ofArray(Value.of(2), Value.of(4), Value.of(6))),
                arguments("Each removes all elements returns empty", "[null, null, null] |- each filter.remove",
                        Value.EMPTY_ARRAY),
                arguments("Each with multiple arguments",
                        "[\"Ben\", \"Alice\"] |- each simple.append(\" from \", \"Berlin\")",
                        Value.ofArray(Value.of("Ben from Berlin"), Value.of("Alice from Berlin"))),
                arguments("Each blackens strings", "[\"secret\", \"password\"] |- each filter.blacken",
                        Value.ofArray(Value.of("XXXXXX"), Value.of("XXXXXXXX"))),
                arguments("Each negates booleans", "[true, false, true] |- each simple.negate",
                        Value.ofArray(Value.FALSE, Value.TRUE, Value.FALSE)),
                arguments("Simple filter with mock.emptyString on array", "[] |- mock.emptyString", Value.of("")),
                arguments("Simple filter with each and emptyString", "[ null, 5 ] |- each mock.emptyString(null)",
                        Value.ofArray(Value.of(""), Value.of(""))));
    }

    // ========================================================================
    // Extended Filter Operations
    // ========================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void whenExtendedFilterApplied_thenProducesExpectedResult(String description, String expression, Value expected) {
        assertExpressionEvaluatesTo(expression, expected);
    }

    static Stream<Arguments> whenExtendedFilterApplied_thenProducesExpectedResult() {
        return Stream.of(
                arguments("Extended filter with single statement", "\"test\" |- { @ : filter.blacken }",
                        Value.of("XXXX")),
                arguments("Extended filter with multiple statements",
                        "5 |- { @ : simple.doubleValue, @ : simple.doubleValue }", Value.of(20)),
                arguments("Extended filter with arguments",
                        "\"Hello\" |- { @ : simple.append(\" \"), @ : simple.append(\"World\") }",
                        Value.of("Hello World")),
                arguments("Extended filter replace value", "\"old\" |- { @ : filter.replace(\"new\") }",
                        Value.of("new")),
                arguments("Extended filter with target path filters field",
                        "{ \"name\": \"secret\" } |- { @.name : filter.blacken }",
                        Value.ofObject(Map.of("name", Value.of("XXXXXX")))),
                arguments("Extended filter with target path removes field",
                        "{ \"name\": \"test\", \"age\": 42 } |- { @.name : filter.remove }",
                        Value.ofObject(Map.of("age", Value.of(42)))),
                arguments("Extended filter with target path transforms field",
                        "{ \"count\": 5 } |- { @.count : simple.doubleValue }",
                        Value.ofObject(Map.of("count", Value.of(10)))),
                arguments("Extended filter with target path on multiple fields",
                        "{ \"a\": 5, \"b\": 10 } |- { @.a : simple.doubleValue, @.b : simple.doubleValue }",
                        Value.ofObject(Map.of("a", Value.of(10), "b", Value.of(20)))),
                arguments("Extended filter with target path replaces field",
                        "{ \"old\": \"value\" } |- { @.old : filter.replace(\"new\") }",
                        Value.ofObject(Map.of("old", Value.of("new")))),
                arguments("Extended filter with index path transforms element",
                        "[1, 2, 3] |- { @[1] : simple.doubleValue }",
                        Value.ofArray(Value.of(1), Value.of(4), Value.of(3))),
                arguments("Extended filter with index path removes element", "[1, 2, 3] |- { @[1] : filter.remove }",
                        Value.ofArray(Value.of(1), Value.of(3))),
                arguments("Extended filter with index path blackens element",
                        "[\"public\", \"secret\", \"open\"] |- { @[1] : filter.blacken }",
                        Value.ofArray(Value.of("public"), Value.of("XXXXXX"), Value.of("open"))),
                arguments("Extended filter with index path on first element",
                        "[10, 20, 30] |- { @[0] : simple.doubleValue }",
                        Value.ofArray(Value.of(20), Value.of(20), Value.of(30))),
                arguments("Extended filter with index path on last element",
                        "[10, 20, 30] |- { @[-1] : simple.doubleValue }",
                        Value.ofArray(Value.of(10), Value.of(20), Value.of(60))),
                arguments("Extended filter with index path on multiple indices",
                        "[1, 2, 3, 4] |- { @[0] : simple.doubleValue, @[2] : simple.doubleValue }",
                        Value.ofArray(Value.of(2), Value.of(2), Value.of(6), Value.of(4))),
                arguments("Extended filter with index path negative index",
                        "[1, 2, 3] |- { @[-2] : simple.doubleValue }",
                        Value.ofArray(Value.of(1), Value.of(4), Value.of(3))),
                arguments("Extended filter with slicing transforms range",
                        "[1, 2, 3, 4, 5] |- { @[1:3] : simple.doubleValue }",
                        Value.ofArray(Value.of(1), Value.of(4), Value.of(6), Value.of(4), Value.of(5))),
                arguments("Extended filter with slicing from start",
                        "[1, 2, 3, 4, 5] |- { @[:3] : simple.doubleValue }",
                        Value.ofArray(Value.of(2), Value.of(4), Value.of(6), Value.of(4), Value.of(5))),
                arguments("Extended filter with slicing to end", "[1, 2, 3, 4, 5] |- { @[2:] : simple.doubleValue }",
                        Value.ofArray(Value.of(1), Value.of(2), Value.of(6), Value.of(8), Value.of(10))),
                arguments("Extended filter with slicing entire array", "[1, 2, 3] |- { @[:] : simple.doubleValue }",
                        Value.ofArray(Value.of(2), Value.of(4), Value.of(6))),
                arguments("Extended filter with slicing with step",
                        "[1, 2, 3, 4, 5, 6] |- { @[0:6:2] : simple.doubleValue }",
                        Value.ofArray(Value.of(2), Value.of(2), Value.of(6), Value.of(4), Value.of(10), Value.of(6))),
                arguments("Extended filter with slicing range with step",
                        "[1, 2, 3, 4, 5, 6] |- { @[1:5:2] : simple.doubleValue }",
                        Value.ofArray(Value.of(1), Value.of(4), Value.of(3), Value.of(8), Value.of(5), Value.of(6))),
                arguments("Extended filter with slicing removes elements",
                        "[1, 2, 3, 4, 5] |- { @[1:4] : filter.remove }", Value.ofArray(Value.of(1), Value.of(5))));
    }

    // ========================================================================
    // Wildcard and Recursive Filters
    // ========================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void whenWildcardOrRecursiveFilterApplied_thenProducesExpectedResult(String description, String expression,
            Value expected) {
        assertExpressionEvaluatesTo(expression, expected);
    }

    static Stream<Arguments> whenWildcardOrRecursiveFilterApplied_thenProducesExpectedResult() {
        return Stream.of(
                arguments("Wildcard filter on object applies to all fields",
                        "{ \"a\": 10, \"b\": 20 } |- { @.* : simple.doubleValue }",
                        Value.ofObject(Map.of("a", Value.of(20), "b", Value.of(40)))),
                arguments("Wildcard filter removes all fields", "{ \"a\": 1, \"b\": 2 } |- { @.* : filter.remove }",
                        Value.EMPTY_OBJECT),
                arguments("Wildcard filter blackens all string fields",
                        "{ \"user\": \"alice\", \"pass\": \"secret\" } |- { @.* : filter.blacken }",
                        Value.ofObject(Map.of("user", Value.of("XXXXX"), "pass", Value.of("XXXXXX")))));
    }

    // ========================================================================
    // Condition Step Filters
    // ========================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void whenConditionStepFilterApplied_thenProducesExpectedResult(String description, String expression,
            Value expected) {
        assertExpressionEvaluatesTo(expression, expected);
    }

    static Stream<Arguments> whenConditionStepFilterApplied_thenProducesExpectedResult() {
        return Stream.of(
                arguments("Condition step with constant true applies to all",
                        "[1, 2, 3, 4, 5] |- { @[?(true)] : simple.doubleValue }",
                        Value.ofArray(Value.of(2), Value.of(4), Value.of(6), Value.of(8), Value.of(10))),
                arguments("Condition step with constant false leaves unchanged",
                        "[1, 2, 3, 4, 5] |- { @[?(false)] : simple.doubleValue }",
                        Value.ofArray(Value.of(1), Value.of(2), Value.of(3), Value.of(4), Value.of(5))),
                arguments("Condition step removes matching elements",
                        "[1, 2, 3, 4, 5] |- { @[?(true)] : filter.remove }", Value.EMPTY_ARRAY),
                arguments("Condition step on object applies to matching fields",
                        "{ \"a\": 1, \"b\": 2, \"c\": 3 } |- { @[?(true)] : simple.doubleValue }",
                        Value.ofObject(Map.of("a", Value.of(2), "b", Value.of(4), "c", Value.of(6)))));
    }

    // ========================================================================
    // Filter with # (Relative Location) - New Tests
    // ========================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void whenFilterWithRelativeLocation_thenProducesExpectedResult(String description, String expression,
            Value expected) {
        assertExpressionEvaluatesTo(expression, expected);
    }

    static Stream<Arguments> whenFilterWithRelativeLocation_thenProducesExpectedResult() {
        return Stream.of(
                // Note: # (relative location) is tracked during condition step iteration.
                // Since condition steps in filters evaluate conditions statically,
                // these tests use constant conditions. Dynamic conditions using @ and # are
                // already tested in ExpressionCompilerTests for step operations.
                arguments("Extended filter with condition modifies elements based on constant true",
                        "[10, 20, 30] |- { @[?(true)] : simple.doubleValue }",
                        Value.ofArray(Value.of(20), Value.of(40), Value.of(60))),
                arguments("Extended filter with condition on object modifies all fields",
                        "{ \"x\": 5, \"y\": 10 } |- { @[?(true)] : simple.doubleValue }",
                        Value.ofObject(Map.of("x", Value.of(10), "y", Value.of(20)))));
    }

    // ========================================================================
    // Index Union Filters
    // ========================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void whenIndexUnionFilterApplied_thenProducesExpectedResult(String description, String expression, Value expected) {
        assertExpressionEvaluatesTo(expression, expected);
    }

    static Stream<Arguments> whenIndexUnionFilterApplied_thenProducesExpectedResult() {
        return Stream.of(arguments("Index union removes array elements at union indices",
                "[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [3,1,2,3], [4,1,2,3] ] |- { @[1,3] : filter.remove }",
                Value.ofArray(Value.ofArray(Value.of(0), Value.of(1), Value.of(2), Value.of(3)),
                        Value.ofArray(Value.of(2), Value.of(1), Value.of(2), Value.of(3)),
                        Value.ofArray(Value.of(4), Value.of(1), Value.of(2), Value.of(3)))));
    }

    // ========================================================================
    // Attribute Union Filters
    // ========================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void whenAttributeUnionFilterApplied_thenProducesExpectedResult(String description, String expression,
            Value expected) {
        assertExpressionEvaluatesTo(expression, expected);
    }

    static Stream<Arguments> whenAttributeUnionFilterApplied_thenProducesExpectedResult() {
        return Stream.of(arguments("Attribute union removes fields in union",
                "{ \"a\" : 1, \"b\" : 2, \"c\" : 3, \"d\" : 4 } |- { @[\"b\" , \"d\"] : filter.remove }",
                Value.ofObject(Map.of("a", Value.of(1), "c", Value.of(3)))));
    }

    // ========================================================================
    // Error Cases
    // ========================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void whenErrorCondition_thenProducesError(String description, String expression, String errorFragment) {
        val result = evaluate(expression);
        assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message().toLowerCase())
                .asString().contains(errorFragment.toLowerCase());
    }

    static Stream<Arguments> whenErrorCondition_thenProducesError() {
        return Stream.of(arguments("Error propagates from parent", "(10/0) |- filter.remove", "Division by zero"),
                arguments("Undefined parent returns error", "undefined |- filter.remove",
                        "Filters cannot be applied to undefined"),
                arguments("Each on non-array returns error", "{} |- each filter.remove",
                        "Cannot use 'each' keyword with non-array"),
                arguments("Extended filter error in parent propagates", "(10/0) |- { @ : filter.remove }",
                        "Division by zero"),
                arguments("Extended filter with target path on non-existent field",
                        "{ \"name\": \"test\" } |- { @.missing : filter.blacken }", "Field 'missing' not found"),
                arguments("Extended filter with target path on non-object", "42 |- { @.field : filter.blacken }",
                        "cannot apply key step to non-object"),
                arguments("Extended filter with index path out of bounds",
                        "[1, 2, 3] |- { @[10] : simple.doubleValue }", "array index out of bounds"),
                arguments("Extended filter with index path on non-array", "{} |- { @[0] : simple.doubleValue }",
                        "cannot apply index step to non-array"),
                arguments("Dynamic conditions in filters not supported",
                        "[1, 2, 3, 4, 5] |- { @[?(@ > 2)] : simple.doubleValue }",
                        "Dynamic conditions in filter condition steps are not supported"),
                arguments("Extended filter with slicing on non-array", "\"text\" |- { @[1:3] : filter.blacken }",
                        "Cannot apply slicing step to non-array"));
    }

    // ========================================================================
    // Subtemplate Operations (:: operator)
    // ========================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void whenSubtemplateApplied_thenProducesExpectedResult(String description, String expression, Value expected) {
        assertExpressionEvaluatesTo(expression, expected);
    }

    static Stream<Arguments> whenSubtemplateApplied_thenProducesExpectedResult() {
        return Stream.of(
                arguments("Subtemplate on simple value wraps in object", "42 :: { \"value\": @ }",
                        Value.ofObject(Map.of("value", Value.of(42)))),
                arguments("Subtemplate with multiple fields", "5 :: { \"original\": @, \"doubled\": @ * 2 }",
                        Value.ofObject(Map.of("original", Value.of(5), "doubled", Value.of(10)))),
                arguments("Subtemplate on object extracts and transforms fields",
                        "{ \"name\": \"Alice\", \"age\": 30 } :: { \"user\": @.name, \"years\": @.age }",
                        Value.ofObject(Map.of("user", Value.of("Alice"), "years", Value.of(30)))),
                arguments("Subtemplate on array maps over each element", "[1, 2, 3] :: { \"num\": @ }",
                        Value.ofArray(Value.ofObject(Map.of("num", Value.of(1))),
                                Value.ofObject(Map.of("num", Value.of(2))),
                                Value.ofObject(Map.of("num", Value.of(3))))),
                arguments("Subtemplate on empty array returns empty", "[] :: { \"value\": @ }", Value.EMPTY_ARRAY),
                arguments("Subtemplate with arithmetic operations", "10 :: { \"half\": @ / 2, \"double\": @ * 2 }",
                        Value.ofObject(Map.of("half", Value.of(5), "double", Value.of(20)))),
                arguments("Subtemplate with nested object construction",
                        "42 :: { \"data\": { \"value\": @, \"squared\": @ * @ } }",
                        Value.ofObject(Map.of("data",
                                Value.ofObject(Map.of("value", Value.of(42), "squared", Value.of(1764)))))),
                arguments("Subtemplate with wildcard step", "{ \"a\": 1, \"b\": 2, \"c\": 3 } :: { \"values\": @.* }",
                        Value.ofObject(Map.of("values", Value.ofArray(Value.of(1), Value.of(2), Value.of(3))))),
                arguments("Subtemplate with multiple fields from same source",
                        "{ \"value\": 10 } :: { \"a\": @.value, \"b\": @.value * 2, \"c\": @.value * 3 }",
                        Value.ofObject(Map.of("a", Value.of(10), "b", Value.of(20), "c", Value.of(30)))),
                arguments("Subtemplate preserves relative context",
                        "{ \"x\": 5, \"y\": 10 } :: { \"sum\": @.x, \"product\": @.y }",
                        Value.ofObject(Map.of("sum", Value.of(5), "product", Value.of(10)))),
                arguments("Subtemplate accesses nested fields in object",
                        "{ \"person\": { \"name\": \"Alice\", \"address\": { \"city\": \"Berlin\" } } } :: { \"city\": @.person.address.city }",
                        Value.ofObject(Map.of("city", Value.of("Berlin")))),
                arguments("Subtemplate accesses array elements with positive and negative indices",
                        "{ \"items\": [10, 20, 30] } :: { \"firstItem\": @.items[0], \"lastItem\": @.items[-1] }",
                        Value.ofObject(Map.of("firstItem", Value.of(10), "lastItem", Value.of(30)))));
    }

    @Test
    void whenSubtemplateOnFilteredArray_thenMapsOverFilteredElements() {
        // Filter first, then apply subtemplate
        val expression = "[ { \"key1\": 1, \"key2\": 2 }, { \"key1\": 3, \"key2\": 4 }, { \"key1\": 5, \"key2\": 6 } ][?(@.key1 > 2)] :: { \"key20\": @.key2 }";
        val expected   = Value.ofArray(Value.ofObject(Map.of("key20", Value.of(4))),
                Value.ofObject(Map.of("key20", Value.of(6))));
        assertExpressionEvaluatesTo(expression, expected);
    }

    @Test
    void whenSubtemplateOnUndefined_thenReturnsUndefined() {
        assertExpressionEvaluatesTo("undefined :: { \"name\": \"foo\" }", Value.UNDEFINED);
    }

    @Test
    void whenSubtemplateWithRecursiveDescentStep_thenCollectsAllMatchingValues() {
        val result = evaluate(
                "{ \"a\": { \"b\": { \"c\": 42 } }, \"x\": { \"b\": { \"c\": 99 } } } :: { \"allCs\": @..c }");
        assertThat(result).isNotNull().isInstanceOf(ObjectValue.class);
        val objectResult = (ObjectValue) result;
        val allCs        = objectResult.get("allCs");
        assertThat(allCs).isInstanceOf(ArrayValue.class);
        val allCsArray = (ArrayValue) allCs;
        assertThat(allCsArray).hasSize(2).contains(Value.of(42), Value.of(99));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void whenSubtemplateWithError_thenProducesError(String description, String expression, String expectedErrorMsg) {
        val result = evaluate(expression);
        assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                .containsIgnoringCase(expectedErrorMsg);
    }

    static Stream<Arguments> whenSubtemplateWithError_thenProducesError() {
        return Stream.of(
                arguments("Subtemplate error propagates from parent", "(10/0) :: { \"value\": @ }", "Division by zero"),
                arguments("Subtemplate propagates division by zero error", "(10/0) :: { \"name\": \"foo\" }",
                        "Division by zero"));
    }

    // ========================================================================
    // Import Resolution Tests
    // ========================================================================

    @Test
    void whenImportedFilterFunctionUsedInExtendedFilter_thenResolvesCorrectly() {
        // This tests the pattern from sapl-demo-mvc-app
        // patient_repository_policyset.sapl:
        // import filter.remove
        // resource |- { @.field : remove }
        val policyDocument = """
                import filter.remove

                policy "test import resolution"
                permit
                transform
                    resource |- {
                        @.secret : remove,
                        @.password : remove
                    }
                """;

        val result = evaluateTransformWithPolicy(policyDocument, Value.ofObject(
                Map.of("secret", Value.of("hidden"), "password", Value.of("hunter2"), "public", Value.of("visible"))));

        assertThat(result).isEqualTo(Value.ofObject(Map.of("public", Value.of("visible"))));
    }

    @Test
    void whenImportedFilterFunctionUsedInSimpleFilter_thenResolvesCorrectly() {
        val policyDocument = """
                import filter.blacken

                policy "test import resolution"
                permit
                transform
                    resource |- blacken
                """;

        val result = evaluateTransformWithPolicy(policyDocument, Value.of("secret"));

        assertThat(result).isEqualTo(Value.of("XXXXXX"));
    }

    @Test
    void whenImportedFunctionWithAliasUsed_thenResolvesCorrectly() {
        val policyDocument = """
                import filter.remove as hide

                policy "test import with alias"
                permit
                transform
                    resource |- { @.secret : hide }
                """;

        val result = evaluateTransformWithPolicy(policyDocument,
                Value.ofObject(Map.of("secret", Value.of("hidden"), "public", Value.of("visible"))));

        assertThat(result).isEqualTo(Value.ofObject(Map.of("public", Value.of("visible"))));
    }

    @Test
    void whenUnresolvedFunctionUsed_thenReturnsErrorInField() {
        val policyDocument = """
                policy "test unresolved function"
                permit
                transform
                    resource |- { @.field : unknownFunction }
                """;

        val result = evaluateTransformWithPolicy(policyDocument, Value.ofObject(Map.of("field", Value.of("value"))));

        // The result is an object with the field containing an error
        assertThat(result).isInstanceOf(ObjectValue.class);
        val objResult  = (ObjectValue) result;
        val fieldValue = objResult.get("field");
        assertThat(fieldValue).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) fieldValue).message()).containsIgnoringCase("invalid function name");
    }

    private Value evaluateTransformWithPolicy(String policyDocument, Value resource) {
        val charStream     = CharStreams.fromString(policyDocument);
        val lexer          = new SAPLLexer(charStream);
        val tokenStream    = new CommonTokenStream(lexer);
        val parser         = new SAPLParser(tokenStream);
        val sapl           = parser.sapl();
        val compiledPolicy = SaplCompiler.compileDocument(sapl, contextWithFunctions);

        // Get the transform expression from the compiled policy
        val decision = compiledPolicy.decisionExpression();
        if (decision instanceof ErrorValue error) {
            return error;
        }
        if (decision instanceof Value value) {
            return value;
        }
        if (decision instanceof PureExpression pure) {
            // Create context with resource via AuthorizationSubscription
            val subscription = new io.sapl.api.pdp.AuthorizationSubscription(Value.UNDEFINED, Value.UNDEFINED, resource,
                    Value.UNDEFINED);
            val evalCtx      = new EvaluationContext(null, null, null, subscription,
                    contextWithFunctions.getFunctionBroker(), null);
            val result       = pure.evaluate(evalCtx);
            // The result is an ObjectValue with decision metadata - extract the resource
            if (result instanceof ObjectValue obj && obj.containsKey("resource")) {
                return obj.get("resource");
            }
            return result;
        }
        return Value.error("Unexpected decision expression type");
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private void assertExpressionEvaluatesTo(String expression, Value expected) {
        val result = evaluate(expression);
        assertThat(result).isEqualTo(expected);
    }

    private Value evaluate(String expression) {
        val compiled = compileExpression(expression);
        if (compiled instanceof ErrorValue error) {
            return error;
        }
        if (compiled instanceof PureExpression pure) {
            return pure.evaluate(evaluationContext);
        }
        return (Value) compiled;
    }

    private CompiledExpression compileExpression(String expression) {
        val charStream       = CharStreams.fromString("policy \"test\" permit " + expression);
        val lexer            = new SAPLLexer(charStream);
        val tokenStream      = new CommonTokenStream(lexer);
        val parser           = new SAPLParser(tokenStream);
        val sapl             = parser.sapl();
        val policyElement    = (PolicyOnlyElementContext) sapl.policyElement();
        val targetExpression = policyElement.policy().targetExpression;
        return ExpressionCompiler.compileExpression(targetExpression, contextWithFunctions);
    }

}
