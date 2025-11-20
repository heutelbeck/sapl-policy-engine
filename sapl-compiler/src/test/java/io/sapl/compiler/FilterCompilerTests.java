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

import io.sapl.api.model.*;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.sapl.util.TestUtil.*;
import static org.assertj.core.api.Assertions.assertThat;

class FilterCompilerTests {

    // ========================================================================
    // Basic Filter Operations
    // ========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource
    void basicFilters(String description, String expression, String... expectedValues) {
        assertExpressionAsStreamEmits(expression, expectedValues);
    }

    private static Stream<Object[]> basicFilters() {
        return Stream.of(
                testCase("Blacken filter on string returns blackened", "\"secret\" |- filter.blacken", "\"XXXXXX\""),
                testCase("Blacken filter on longer string returns blackened", "\"password\" |- filter.blacken",
                        "\"XXXXXXXX\""),
                testCase("Custom function with args applies function",
                        "\"Ben\" |- simple.append(\" from \", \"Berlin\")", "\"Ben from Berlin\""),
                testCase("Custom function without args applies function", "\"hello\" |- simple.length", "5"),
                testCase("Filter on number works correctly", "42 |- simple.double", "84"),
                testCase("Filter on boolean works correctly", "true |- simple.negate", "false"),
                testCase("Filter on array applies without each", "[1,2,3] |- simple.length", "3"),
                testCase("Chained filter expressions apply sequentially", "5 |- simple.double", "10"));
    }

    @Test
    void removeFilterOnObject_returnsUndefined() {
        assertExpressionEvaluatesTo("{} |- filter.remove", Value.UNDEFINED);
    }

    @Test
    void removeFilterOnNull_returnsUndefined() {
        assertExpressionEvaluatesTo("null |- filter.remove", Value.UNDEFINED);
    }

    @Test
    void extendedFilterWithRootRemove_returnsUndefined() {
        assertExpressionEvaluatesTo("{} |- { @ : filter.remove }", Value.UNDEFINED);
    }

    @Test
    void subtemplateOnUndefined_returnsUndefined() {
        assertExpressionEvaluatesTo("undefined :: { \"name\": \"foo\" }", Value.UNDEFINED);
    }

    // ========================================================================
    // Each Filter Operations
    // ========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource
    void eachFilters(String description, String expression, String... expectedValues) {
        assertExpressionAsStreamEmits(expression, expectedValues);
    }

    private static Stream<Object[]> eachFilters() {
        return Stream.of(
                testCase("Each removes all elements when filtering all", "[null, 5] |- each filter.remove", "[]"),
                testCase("Each applies function to transform elements", "[\"a\", \"b\"] |- each simple.append(\"!\")",
                        "[\"a!\", \"b!\"]"),
                testCase("Empty array unchanged returns empty", "[] |- each filter.remove", "[]"),
                testCase("Each doubles numbers to transform numbers", "[1, 2, 3] |- each simple.double", "[2, 4, 6]"),
                testCase("Each removes all elements returns empty array", "[null, null, null] |- each filter.remove",
                        "[]"),
                testCase("Each with multiple arguments applies correctly",
                        "[\"Ben\", \"Alice\"] |- each simple.append(\" from \", \"Berlin\")",
                        "[\"Ben from Berlin\", \"Alice from Berlin\"]"),
                testCase("Each blackens strings to redact each element",
                        "[\"secret\", \"password\"] |- each filter.blacken", "[\"XXXXXX\", \"XXXXXXXX\"]"),
                testCase("Each negates booleans to negate each element", "[true, false, true] |- each simple.negate",
                        "[false, true, false]"),
                testCase("Simple filter with mock.emptyString on array returns empty string", "[] |- mock.emptyString",
                        "\"\""),
                testCase("Simple filter with each and emptyString transforms all elements",
                        "[ null, 5 ] |- each mock.emptyString(null)", "[\"\", \"\"]"));
    }

    // ========================================================================
    // Extended Filter Operations
    // ========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource
    void extendedFilters(String description, String expression, String... expectedValues) {
        assertExpressionAsStreamEmits(expression, expectedValues);
    }

    private static Stream<Object[]> extendedFilters() {
        return Stream.of(
                testCase("Extended filter with single statement applies function", "\"test\" |- { : filter.blacken }",
                        "\"XXXX\""),
                testCase("Extended filter with multiple statements applies sequentially",
                        "5 |- { : simple.double, : simple.double }", "20"),
                testCase("Extended filter with arguments applies correctly",
                        "\"Hello\" |- { : simple.append(\" \"), : simple.append(\"World\") }", "\"Hello World\""),
                testCase("Extended filter replace value returns replacement",
                        "\"old\" |- { : filter.replace(\"new\") }", "\"new\""),
                testCase("Extended filter with target path filters field",
                        "{ \"name\": \"secret\" } |- { @.name : filter.blacken }", "{ \"name\": \"XXXXXX\" }"),
                testCase("Extended filter with target path removes field",
                        "{ \"name\": \"test\", \"age\": 42 } |- { @.name : filter.remove }", "{ \"age\": 42 }"),
                testCase("Extended filter with target path transforms field",
                        "{ \"count\": 5 } |- { @.count : simple.double }", "{ \"count\": 10 }"),
                testCase("Extended filter with target path on multiple fields",
                        "{ \"a\": 5, \"b\": 10 } |- { @.a : simple.double, @.b : simple.double }",
                        "{ \"a\": 10, \"b\": 20 }"),
                testCase("Extended filter with target path replaces field",
                        "{ \"old\": \"value\" } |- { @.old : filter.replace(\"new\") }", "{ \"old\": \"new\" }"),
                testCase("Extended filter with index path transforms element", "[1, 2, 3] |- { @[1] : simple.double }",
                        "[1, 4, 3]"),
                testCase("Extended filter with index path removes element", "[1, 2, 3] |- { @[1] : filter.remove }",
                        "[1, 3]"),
                testCase("Extended filter with index path blackens element",
                        "[\"public\", \"secret\", \"open\"] |- { @[1] : filter.blacken }",
                        "[\"public\", \"XXXXXX\", \"open\"]"),
                testCase("Extended filter with index path on first element", "[10, 20, 30] |- { @[0] : simple.double }",
                        "[20, 20, 30]"),
                testCase("Extended filter with index path on last element", "[10, 20, 30] |- { @[-1] : simple.double }",
                        "[10, 20, 60]"),
                testCase("Extended filter with index path on multiple indices",
                        "[1, 2, 3, 4] |- { @[0] : simple.double, @[2] : simple.double }", "[2, 2, 6, 4]"),
                testCase("Extended filter with index path negative index applies correctly",
                        "[1, 2, 3] |- { @[-2] : simple.double }", "[1, 4, 3]"),
                testCase("Extended filter with slicing transforms range",
                        "[1, 2, 3, 4, 5] |- { @[1:3] : simple.double }", "[1, 4, 6, 4, 5]"),
                testCase("Extended filter with slicing from start", "[1, 2, 3, 4, 5] |- { @[:3] : simple.double }",
                        "[2, 4, 6, 4, 5]"),
                testCase("Extended filter with slicing to end", "[1, 2, 3, 4, 5] |- { @[2:] : simple.double }",
                        "[1, 2, 6, 8, 10]"),
                testCase("Extended filter with slicing entire array", "[1, 2, 3] |- { @[:] : simple.double }",
                        "[2, 4, 6]"),
                testCase("Extended filter with slicing with step", "[1, 2, 3, 4, 5, 6] |- { @[0:6:2] : simple.double }",
                        "[2, 2, 6, 4, 10, 6]"),
                testCase("Extended filter with slicing range with step",
                        "[1, 2, 3, 4, 5, 6] |- { @[1:5:2] : simple.double }", "[1, 4, 3, 8, 5, 6]"),
                testCase("Extended filter with slicing removes elements",
                        "[1, 2, 3, 4, 5] |- { @[1:4] : filter.remove }", "[1, 5]"),
                testCase("Extended filter with slicing blackens strings",
                        "[\"public\", \"secret1\", \"secret2\", \"data\"] |- { @[1:3] : filter.blacken }",
                        "[\"public\", \"XXXXXXX\", \"XXXXXXX\", \"data\"]"),
                testCase("Extended filter with slicing out of bounds clamps",
                        "[1, 2, 3] |- { @[1:10] : simple.double }", "[1, 4, 6]"),
                testCase("Extended filter with slicing negative to index",
                        "[0, 1, 2, 3, 4, 5, 6, 7, 8, 9] |- { @[7:-1] : simple.double }",
                        "[0, 1, 2, 3, 4, 5, 6, 14, 16, 9]"),
                testCase("Extended filter with slicing negative from index",
                        "[0, 1, 2, 3, 4, 5, 6, 7, 8, 9] |- { @[-3:9] : simple.double }",
                        "[0, 1, 2, 3, 4, 5, 6, 14, 16, 9]"),
                testCase("Extended filter with slicing negative from omitted to",
                        "[0, 1, 2, 3, 4, 5, 6, 7, 8, 9] |- { @[-3:] : simple.double }",
                        "[0, 1, 2, 3, 4, 5, 6, 14, 16, 18]"),
                testCase("Extended filter with slicing negative step minus one",
                        "[0, 1, 2, 3, 4, 5, 6, 7, 8, 9] |- { @[: :-1] : simple.double }",
                        "[0, 2, 4, 6, 8, 10, 12, 14, 16, 18]"),
                testCase("Extended filter with slicing negative step minus three",
                        "[0, 1, 2, 3, 4, 5, 6, 7, 8, 9] |- { @[: :-3] : simple.double }",
                        "[0, 2, 2, 3, 8, 5, 6, 14, 8, 9]"),
                testCase("Extended filter with slicing negative step minus two removes elements",
                        "[0, 1, 2, 3, 4, 5, 6, 7, 8, 9] |- { @[: :-2] : filter.remove }", "[1, 3, 5, 7, 9]"),
                testCase("Extended filter with slicing negative to with filter removes before to",
                        "[0, 1, 2, 3, 4, 5, 6, 7, 8, 9] |- { @[:-2] : filter.remove }", "[8, 9]"),
                testCase("Extended filter with each and slicing applies function to slice in each element",
                        "[[1, 2, 3], [4, 5, 6], [7, 8, 9]] |- { each @[0:2] : simple.double }",
                        "[[2, 4, 3], [8, 10, 6], [14, 16, 9]]"),
                testCase("Slicing step filter with pure argument applies filter to slice",
                        "[10, 20, 30, 40, 50] |- { @[1:4] : simple.double }", "[10, 40, 60, 80, 50]"),
                testCase("Slicing filter with negative indices applies correctly",
                        "[10, 20, 30, 40, 50] |- { @[-3:-1] : simple.double }", "[10, 20, 60, 80, 50]"),

                // Expression step filters
                testCase("Filter with expression step on non-array non-object passes through unchanged",
                        "123 |- { @[(1+1)] : mock.nil }", "123"),
                testCase("Remove element using expression step on array",
                        "[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [3,1,2,3], [4,1,2,3] ] |- { @[(1+2)] : filter.remove }",
                        "[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [4,1,2,3] ]"),
                testCase("Remove key using expression step on object",
                        "{ \"ab\" : [0,1,2,3], \"bb\" : [0,1,2,3], \"cb\" : [0,1,2,3], \"d\" : [0,1,2,3] } |- { @[(\"c\"+\"b\")] : filter.remove }",
                        "{ \"ab\" : [0,1,2,3], \"bb\" : [0,1,2,3], \"d\" : [0,1,2,3] }"),

                // Nested key removal
                testCase("Extended filter removes nested key two levels deep",
                        "{ \"name\" : \"Jack the Ripper\", \"job\" : { \"title\" : \"recreational surgeon\", \"wage\" : 1000000 } } |- { @.job.wage : filter.remove }",
                        "{\"name\":\"Jack the Ripper\",\"job\":{\"title\":\"recreational surgeon\"}}"),
                testCase("Extended filter removes nested key three levels deep",
                        "{ \"name\" : \"Jack the Ripper\", \"job\" : { \"title\" : \"recreational surgeon\", \"wage\" : { \"monthly\" : 1000000, \"currency\" : \"GBP\"} } } |- { @.job.wage.monthly : filter.remove }",
                        "{\"name\":\"Jack the Ripper\",\"job\":{\"title\":\"recreational surgeon\",\"wage\":{\"currency\":\"GBP\"}}}"),

                // Implicit array mapping
                testCase("Extended filter removes key from each array element with implicit mapping",
                        "[ { \"name\" : \"Jack the Ripper\", \"job\" : \"recreational surgeon\" }, { \"name\" : \"Billy the Kid\", \"job\" : \"professional perforator\" } ] |- { @.name : filter.remove }",
                        "[{\"job\":\"recreational surgeon\"},{\"job\":\"professional perforator\"}]"),

                // Each with @ target
                testCase("Extended filter with each and @ removes all array elements",
                        "[ null, true ] |- { each @ : filter.remove }", "[]"),
                testCase("Extended filter with @ and emptyString replaces entire value",
                        "[ null, true ] |- { @ : mock.emptyString }", "\"\""),
                testCase("Extended filter with each @ and emptyString replaces each element",
                        "[ null, true ] |- { each @ : mock.emptyString }", "[\"\", \"\"]"),

                // Nested array operations
                testCase("Extended filter blackens field in specific array element",
                        "[ { \"name\" : \"Jack the Ripper\", \"job\" : \"recreational surgeon\" }, { \"name\" : \"Billy the Kid\", \"job\" : \"professional perforator\" } ] |- { @[0].job : filter.blacken }",
                        "[{\"name\":\"Jack the Ripper\",\"job\":\"XXXXXXXXXXXXXXXXXXXX\"},{\"name\":\"Billy the Kid\",\"job\":\"professional perforator\"}]"),

                // Complex slicing operations
                testCase("Extended filter removes elements in slice with negative to index",
                        "[ 1, 2, 3, 4, 5 ] |- { @[0:-2:2] : filter.remove }", "[2, 4, 5]"),
                testCase("Extended filter removes elements with negative step slice",
                        "[ 0, 1, 2, 3, 4, 5 ] |- { @[1:5:-2] : filter.remove }", "[0, 2, 4, 5]"),

                // Attribute unions
                testCase("Extended filter removes fields in attribute union",
                        "{ \"a\" : 1, \"b\" : 2, \"c\" : 3, \"d\" : 4 } |- { @[\"b\" , \"d\"] : filter.remove }",
                        "{\"a\":1, \"c\":3}"),
                testCase("Extended filter removes array elements in fields selected by attribute union",
                        "{ \"a\" : [0,1,2,3], \"b\" : [0,1,2,3], \"c\" : [0,1,2,3], \"d\" : [0,1,2,3] } |- { @[\"b\" , \"d\"][1] : filter.remove }",
                        "{\"a\":[0, 1, 2, 3],\"b\":[0, 2, 3],\"c\":[0, 1, 2, 3],\"d\":[0, 2, 3]}"),
                testCase("Extended filter replaces with empty string in attribute union",
                        "{ \"a\" : [0,1,2,3], \"b\" : [0,1,2,3], \"c\" : [0,1,2,3], \"d\" : [0,1,2,3] } |- { @[\"b\" , \"d\"][1] : mock.emptyString }",
                        "{\"a\":[0, 1, 2, 3],\"b\":[0, \"\", 2, 3],\"c\":[0, 1, 2, 3],\"d\":[0, \"\", 2, 3]}"),

                // Index unions
                testCase("Extended filter removes array elements at union indices",
                        "[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [3,1,2,3], [4,1,2,3] ] |- { @[1,3] : filter.remove }",
                        "[[0, 1, 2, 3], [2, 1, 2, 3], [4, 1, 2, 3]]"),
                testCase("Extended filter removes nested elements with double index union",
                        "[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [3,1,2,3], [4,1,2,3] ] |- { @[1,3][2,1] : filter.remove }",
                        "[[0, 1, 2, 3], [1, 3], [2, 1, 2, 3], [3, 3], [4, 1, 2, 3]]"),

                // Recursive descent blackening
                testCase("Extended filter blackens all occurrences of key with recursive descent",
                        "{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ] } |- { @..key : filter.blacken }",
                        "{\"key\":\"XXXXXX\",\"array1\":[{\"key\":\"XXXXXX\"},{\"key\":\"XXXXXX\"}],\"array2\":[1, 2, 3, 4, 5]}"),

                // Multiple filter statements
                testCase("Extended filter applies multiple filter statements in sequence",
                        "{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ] } |- { @..[0] : filter.remove, @..key : filter.blacken, @.array2[-1] : filter.remove }",
                        "{\"key\":\"XXXXXX\",\"array1\":[{\"key\":\"XXXXXX\"}],\"array2\":[2, 3, 4]}"));
    }

    // ========================================================================
    // Wildcard and Recursive Filters
    // ========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource
    void wildcardAndRecursiveFilters(String description, String expression, String... expectedValues) {
        assertExpressionAsStreamEmits(expression, expectedValues);
    }

    private static Stream<Object[]> wildcardAndRecursiveFilters() {
        return Stream.of(
                testCase("Wildcard filter on object applies to all fields",
                        "{ \"a\": 10, \"b\": 20 } |- { @.* : simple.double }", "{ \"a\": 20, \"b\": 40 }"),
                testCase("Wildcard filter removes all fields", "{ \"a\": 1, \"b\": 2 } |- { @.* : filter.remove }",
                        "{}"),
                testCase("Wildcard filter blackens all string fields",
                        "{ \"user\": \"alice\", \"pass\": \"secret\" } |- { @.* : filter.blacken }",
                        "{ \"user\": \"XXXXX\", \"pass\": \"XXXXXX\" }"),
                testCase("Recursive key filter applies to all matching keys throughout structure",
                        "{ \"a\": { \"x\": 5 }, \"b\": { \"x\": 10 } } |- { @..x : simple.double }",
                        "{ \"a\": { \"x\": 10 }, \"b\": { \"x\": 20 } }"),
                testCase("Recursive key filter on nested arrays",
                        "{ \"data\": [{ \"val\": 1 }, { \"val\": 2 }] } |- { @..val : simple.double }",
                        "{ \"data\": [{ \"val\": 2 }, { \"val\": 4 }] }"),
                testCase("Recursive key filter removes all matching keys",
                        "{ \"a\": { \"x\": 1 }, \"b\": { \"x\": 2 } } |- { @..x : filter.remove }",
                        "{ \"a\": {}, \"b\": {} }"));
    }

    // ========================================================================
    // Condition Step Filters
    // ========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource
    void conditionStepFilters(String description, String expression, String... expectedValues) {
        assertExpressionAsStreamEmits(expression, expectedValues);
    }

    private static Stream<Object[]> conditionStepFilters() {
        return Stream.of(
                testCase("Condition step in filter with constant true condition applies filter to all elements",
                        "[1, 2, 3, 4, 5] |- { @[?(true)] : simple.double }", "[2, 4, 6, 8, 10]"),
                testCase("Condition step in filter with constant false condition leaves all elements unchanged",
                        "[1, 2, 3, 4, 5] |- { @[?(false)] : simple.double }", "[1, 2, 3, 4, 5]"),
                testCase("Condition step in filter removes matching elements",
                        "[1, 2, 3, 4, 5] |- { @[?(true)] : filter.remove }", "[]"),
                testCase("Condition step in filter on object applies filter to matching fields",
                        "{ \"a\": 1, \"b\": 2, \"c\": 3 } |- { @[?(true)] : simple.double }",
                        "{ \"a\": 2, \"b\": 4, \"c\": 6 }"));
    }

    // ========================================================================
    // Subtemplate Operations
    // ========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource
    void subtemplates(String description, String expression, String... expectedValues) {
        assertExpressionAsStreamEmits(expression, expectedValues);
    }

    private static Stream<Object[]> subtemplates() {
        return Stream.of(
                testCase("Subtemplate on simple value wraps in object", "42 :: { \"value\": @ }", "{ \"value\": 42 }"),
                testCase("Subtemplate with multiple fields", "5 :: { \"original\": @, \"doubled\": @ * 2 }",
                        "{ \"original\": 5, \"doubled\": 10 }"),
                testCase("Subtemplate on object extracts and transforms fields",
                        "{ \"name\": \"Alice\", \"age\": 30 } :: { \"user\": @.name, \"years\": @.age }",
                        "{ \"user\": \"Alice\", \"years\": 30 }"),
                testCase("Subtemplate on array maps over each element", "[1, 2, 3] :: { \"num\": @ }",
                        "[{ \"num\": 1 }, { \"num\": 2 }, { \"num\": 3 }]"),
                testCase("Subtemplate on empty array returns empty", "[] :: { \"value\": @ }", "[]"),
                testCase("Subtemplate with arithmetic operations", "10 :: { \"half\": @ / 2, \"double\": @ * 2 }",
                        "{ \"half\": 5, \"double\": 20 }"),
                testCase("Subtemplate with nested object construction",
                        "42 :: { \"data\": { \"value\": @, \"squared\": @ * @ } }",
                        "{ \"data\": { \"value\": 42, \"squared\": 1764 } }"),
                testCase("Subtemplate with wildcard step", "{ \"a\": 1, \"b\": 2, \"c\": 3 } :: { \"values\": @.* }",
                        "{ \"values\": [1, 2, 3] }"),
                testCase("Subtemplate with multiple fields from same source",
                        "{ \"value\": 10 } :: { \"a\": @.value, \"b\": @.value * 2, \"c\": @.value * 3 }",
                        "{ \"a\": 10, \"b\": 20, \"c\": 30 }"),
                testCase("Subtemplate preserves relative context",
                        "{ \"x\": 5, \"y\": 10 } :: { \"sum\": @.x, \"product\": @.y }",
                        "{ \"sum\": 5, \"product\": 10 }"),

                // Additional subtemplate scenarios
                testCase("Subtemplate on filtered array with condition",
                        "[ { \"key1\": 1, \"key2\": 2 }, { \"key1\": 3, \"key2\": 4 }, { \"key1\": 5, \"key2\": 6 } ][?(@.key1 > 2)] :: { \"key20\": @.key2 }",
                        "[{\"key20\":4}, {\"key20\":6}]"),
                testCase("Subtemplate accesses nested fields in object",
                        "{ \"person\": { \"name\": \"Alice\", \"address\": { \"city\": \"Berlin\" } } } :: { \"city\": @.person.address.city }",
                        "{\"city\":\"Berlin\"}"),
                testCase("Subtemplate accesses array elements with positive and negative indices",
                        "{ \"items\": [10, 20, 30] } :: { \"firstItem\": @.items[0], \"lastItem\": @.items[-1] }",
                        "{\"firstItem\":10, \"lastItem\":30}"),
                testCase("Combined subtemplate with condition-filtered input",
                        "[ { \"key1\": 1, \"key2\": \"a\" }, { \"key1\": 3, \"key2\": \"b\" }, { \"key1\": 5, \"key2\": \"c\" } ][?(@.key1 > 2)] :: { \"value\": @.key2 }",
                        "[{\"value\":\"b\"}, {\"value\":\"c\"}]"));
    }

    @Test
    void subtemplate_withRecursiveDescentStep() {
        var result = evaluate(
                "{ \"a\": { \"b\": { \"c\": 42 } }, \"x\": { \"b\": { \"c\": 99 } } } :: { \"allCs\": @..c }");
        assertThat(result).isNotNull().isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        var allCs        = objectResult.get("allCs");
        assertThat(allCs).isInstanceOf(ArrayValue.class);
        var allCsArray = (ArrayValue) allCs;
        assertThat(allCsArray).hasSize(2);
        assertThat(allCsArray).contains(Value.of(42), Value.of(99));
    }

    // ========================================================================
    // Streaming PIPs - Single Emission
    // ========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource
    void streamingSingleEmission(String description, String expression, String... expectedValues) {
        assertExpressionAsStreamEmits(expression, expectedValues);
    }

    private static Stream<Object[]> streamingSingleEmission() {
        return Stream.of(
                testCase("Simple filter with attribute finder applies filter to stream values",
                        "\"test\".<test.echo> |- filter.blacken", "\"XXXX\""),
                testCase("Simple filter with attribute finder on number applies filter to stream",
                        "(10).<test.echo> |- simple.double", "20"),
                testCase("Extended filter with attribute finder applies filter to stream of objects",
                        "{ \"name\": \"Alice\" }.<test.echo> |- { @.name : filter.blacken }",
                        "{ \"name\": \"XXXXX\" }"),
                testCase("Extended filter with attribute finder in path applies filter to nested stream values",
                        "{ \"data\": { \"value\": \"secret\" } }.<test.echo> |- { @.data.value : filter.blacken }",
                        "{ \"data\": { \"value\": \"XXXXXX\" } }"),
                testCase("Each filter with attribute finder applies filter to each stream value",
                        "[1, 2, 3].<test.echo> |- each simple.double", "[2, 4, 6]"),
                testCase("Wildcard filter with attribute finder applies filter to all fields in stream",
                        "{ \"a\": 10, \"b\": 20 }.<test.echo> |- { @.* : simple.double }", "{ \"a\": 20, \"b\": 40 }"),
                testCase("Condition step filter with attribute finder on constant condition",
                        "[10, 20, 30].<test.echo> |- { @[?(true)] : simple.double }", "[20, 40, 60]"),
                testCase("Recursive key filter with attribute finder applies filter recursively to stream",
                        "{ \"a\": { \"x\": 5 }, \"b\": { \"x\": 10 } }.<test.echo> |- { @..x : simple.double }",
                        "{ \"a\": { \"x\": 10 }, \"b\": { \"x\": 20 } }"),
                testCase("Subtemplate with attribute finder applies template to stream values",
                        "{ \"name\": \"Bob\" }.<test.echo> :: { \"user\": @.name }", "{ \"user\": \"Bob\" }"),
                testCase("Combined filters with attribute finder applies multiple filters to stream",
                        "{ \"a\": 5, \"b\": 10 }.<test.echo> |- { @.a : simple.double, @.b : simple.double }",
                        "{ \"a\": 10, \"b\": 20 }"));
    }

    // ========================================================================
    // Streaming PIPs - Multiple Emissions
    // ========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource
    void streamingMultipleEmissions(String description, String expression, String... expectedValues) {
        assertExpressionAsStreamEmits(expression, expectedValues);
    }

    private static Stream<Object[]> streamingMultipleEmissions() {
        return Stream.of(
                testCase("Simple filter with sequence PIP applies filter to all emitted values",
                        "<test.sequence> |- simple.double", "2", "4", "6"),
                testCase("Simple filter with counter PIP applies filter to all emitted values",
                        "(10).<test.counter> |- simple.double", "20", "22", "24"),
                testCase("Extended filter with changes PIP applies filter to each object in stream",
                        "{ \"status\": \"active\" }.<test.changes> |- { @.version : simple.double }",
                        "{ \"status\": \"active\", \"version\": 2 }", "{ \"status\": \"active\", \"version\": 4 }",
                        "{ \"status\": \"active\", \"version\": 6 }"),
                testCase("Subtemplate with sequence PIP applies template to each emitted value",
                        "<test.sequence> :: { \"value\": @, \"doubled\": @ * 2 }", "{ \"value\": 1, \"doubled\": 2 }",
                        "{ \"value\": 2, \"doubled\": 4 }", "{ \"value\": 3, \"doubled\": 6 }"),
                testCase("Combined filters and subtemplate with changes PIP transforms stream values",
                        "({ \"count\": 0 }.<test.changes> |- { @.version : simple.double }) :: { \"v\": @.version, \"c\": @.count }",
                        "{ \"v\": 2, \"c\": 0 }", "{ \"v\": 4, \"c\": 0 }", "{ \"v\": 6, \"c\": 0 }"),
                testCase("Array map with sequence PIP applies array operation to each stream value",
                        "<test.sequence> :: [@, @ * 2, @ * 3]", "[1, 2, 3]", "[2, 4, 6]", "[3, 6, 9]"));
    }

    // ========================================================================
    // Error Cases
    // ========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource
    void errorCases(String description, String expression, String errorFragment) {
        assertEvaluatesToError(expression, errorFragment);
    }

    private static Stream<Object[]> errorCases() {
        return Stream.of(errorCase("Error propagates from parent", "(10/0) |- filter.remove", "Division by zero"),
                errorCase("Undefined parent returns error", "undefined |- filter.remove",
                        "Filters cannot be applied to undefined"),
                errorCase("Filter with error in arguments returns error", "\"text\" |- simple.append(10/0)",
                        "Division by zero"),
                errorCase("Each on non-array returns error", "{} |- each filter.remove",
                        "Cannot use 'each' keyword with non-array values"),
                errorCase("Extended filter error in parent propagates error", "(10/0) |- { : filter.remove }",
                        "Division by zero"),
                errorCase("Extended filter with target path on non-existent field returns error",
                        "{ \"name\": \"test\" } |- { @.missing : filter.blacken }", "Field 'missing' not found"),
                errorCase("Extended filter with target path on non-object returns error",
                        "42 |- { @.field : filter.blacken }", "cannot apply key step to non-object"),
                errorCase("Extended filter with index path out of bounds returns error",
                        "[1, 2, 3] |- { @[10] : simple.double }", "array index out of bounds"),
                errorCase("Extended filter with index path on non-array returns error",
                        "{} |- { @[0] : simple.double }", "cannot apply index step to non-array"),
                errorCase("Subtemplate error propagates from parent", "(10/0) :: { \"value\": @ }", "Division by zero"),
                errorCase("Subtemplate propagates division by zero error", "(10/0) :: { \"name\": \"foo\" }",
                        "Division by zero"),
                errorCase("Dynamic conditions in filters not supported",
                        "[1, 2, 3, 4, 5] |- { @[?(@ > 2)] : simple.double }",
                        "Dynamic conditions in filter condition steps are not supported"),
                errorCase("Dynamic equality conditions in filters not supported",
                        "[1, 2, 3, 2, 1] |- { @[?(@ == 2)] : simple.double }",
                        "Dynamic conditions in filter condition steps are not supported"),
                errorCase("Extended filter with slicing on non-array returns error",
                        "\"text\" |- { @[1:3] : filter.blacken }", "Cannot apply slicing step to non-array"),

                // Expression step type errors in filters
                errorCase("Filter with boolean expression step returns error",
                        "[ [4,1,2,3] ] |- { @[(false)] : filter.remove }", "Array access type mismatch"),
                errorCase("Filter with string expression step on array returns error",
                        "[ [4,1,2,3] ] |- { @[(\"a\")] : filter.remove }", "Array access type mismatch"),
                errorCase("Filter with numerical expression step on object returns error",
                        "{ \"a\": [4,1,2,3] } |- { @[(123)] : filter.remove }", "Object access type mismatch"),

                // Extended filter with each on non-array errors
                errorCase("Extended filter with each on non-array in index step returns error",
                        "[ {}, true ] |- { each @[0] : mock.emptyString }", "Cannot apply index step to non-array"),
                errorCase("Extended filter with each on non-array object returns error",
                        "{} |- { each @ : filter.remove }", "each"));
    }
}
