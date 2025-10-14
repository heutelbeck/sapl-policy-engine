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
package io.sapl.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.Val;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.sapl.assertj.SaplAssertions.assertThatVal;
import static org.assertj.core.api.Assertions.assertThat;

class ArrayFunctionLibraryTests {

    private static Val json(String json) throws JsonProcessingException {
        return Val.ofJson(json);
    }

    private static JsonNode[] nodes(int... values) {
        return java.util.stream.IntStream.of(values).mapToObj(Val.JSON::numberNode).toArray(JsonNode[]::new);
    }

    @Test
    void when_concatenateNoParameters_then_returnsEmptyArray() {
        assertThatVal(ArrayFunctionLibrary.concatenate()).hasValue().isArray().isEmpty();
    }

    @Test
    void when_concatenate_then_concatenatesCorrectly() throws JsonProcessingException {
        assertThatVal(ArrayFunctionLibrary.concatenate(json("[ 1,2 ]"), json("[ ]"), json("[ 3,4 ]"), json("[ 5,6 ]")))
                .hasValue().isArray().isEqualTo(json("[1,2,3,4,5,6]").getArrayNode());
    }

    @Test
    void when_intersectNoParameters_then_returnsEmptyArray() {
        assertThatVal(ArrayFunctionLibrary.intersect()).hasValue().isArray().isEmpty();
    }

    @Test
    void when_intersect_then_returnsIntersection() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.intersect(json("[ 1,2,3,4 ]"), json("[ 3,4 ]"), json("[ 4,1,3 ]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(2);
        assertThat(actual.getArrayNode()).containsExactlyInAnyOrder(nodes(4, 3));
    }

    @Test
    void when_intersectWithOneEmptySet_then_returnsEmptyArray() throws JsonProcessingException {
        assertThatVal(
                ArrayFunctionLibrary.intersect(json("[ 1,2,3,4 ]"), json("[ ]"), json("[ 3,4 ]"), json("[ 4,1,3 ]")))
                .hasValue().isArray().isEmpty();
    }

    @Test
    void when_unionNoParameters_then_returnsEmptyArray() {
        assertThatVal(ArrayFunctionLibrary.union()).hasValue().isArray().isEmpty();
    }

    @Test
    void when_union_then_returnsUnion() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.union(json("[ 1,2,3 ]"), json("[ ]"), json("[ 3,4 ]"), json("[ 4,1,3 ]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(4);
        assertThat(actual.getArrayNode()).containsExactlyInAnyOrder(nodes(4, 3, 1, 2));
    }

    @Test
    void when_toSet_then_returnsSet() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.toSet(json("[ 1,2,3,2,1,1,1,5,8,10,8,10,3 ]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(6);
        assertThat(actual.getArrayNode()).containsExactlyInAnyOrder(nodes(1, 2, 3, 5, 8, 10));
    }

    @ParameterizedTest
    @MethodSource("provideDifferenceTestCases")
    void when_difference_then_returnsCorrectResult(String sourceArray, String subtractArray, String expectedArray)
            throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.difference(json(sourceArray), json(subtractArray));
        assertThatVal(actual).hasValue().isArray();
        val expected = json(expectedArray).getArrayNode();
        assertThat(actual.getArrayNode()).hasSameSizeAs(expected);
        assertThat(actual.getArrayNode()).containsExactlyInAnyOrderElementsOf(expected);
    }

    private static Stream<Arguments> provideDifferenceTestCases() {
        return Stream.of(Arguments.of("[ 1,2,3,2,1,1,1,5,8,10,8,10,3 ]", "[]", "[1,2,3,5,8,10]"),
                Arguments.of("[ 1,2,3,2,1,1,1,5,8,10,8,10,3 ]", "[20,22,\"abc\"]", "[1,2,3,5,8,10]"),
                Arguments.of("[ 1,2,3,2,1,1,1,5,8,10,8,10,3 ]", "[10,2]", "[1,3,5,8]"));
    }

    @ParameterizedTest
    @MethodSource("provideFlattenTestCases")
    void when_flatten_then_returnsCorrectResult(String inputArray, String expectedArray)
            throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.flatten(json(inputArray));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).isEqualTo(json(expectedArray).getArrayNode());
    }

    private static Stream<Arguments> provideFlattenTestCases() {
        return Stream.of(Arguments.of("[]", "[]"), Arguments.of("[1, 2, 3, 4]", "[1, 2, 3, 4]"),
                Arguments.of("[1, [2, 3], 4, [3, 2], 1]", "[1, 2, 3, 4, 3, 2, 1]"),
                Arguments.of("[1, [], 2, [], 3]", "[1, 2, 3]"));
    }

    @ParameterizedTest
    @MethodSource("provideSizeTestCases")
    void when_size_then_returnsCorrectCount(String arrayJson, int expectedSize) throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.size(json(arrayJson));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().asInt()).isEqualTo(expectedSize);
    }

    private static Stream<Arguments> provideSizeTestCases() {
        return Stream.of(Arguments.of("[]", 0), Arguments.of("[1]", 1), Arguments.of("[1, 2, 3, 4, 5]", 5),
                Arguments.of("[1, [2, 3], 4]", 3));
    }

    @ParameterizedTest
    @MethodSource("provideReverseTestCases")
    void when_reverse_then_returnsCorrectResult(String inputArray, String expectedArray)
            throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.reverse(json(inputArray));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).isEqualTo(json(expectedArray).getArrayNode());
    }

    private static Stream<Arguments> provideReverseTestCases() {
        return Stream.of(Arguments.of("[]", "[]"), Arguments.of("[1]", "[1]"),
                Arguments.of("[1, 2, 3, 4]", "[4, 3, 2, 1]"),
                Arguments.of("[1, \"abc\", true, null, 2.5]", "[2.5, null, true, \"abc\", 1]"));
    }

    @ParameterizedTest
    @MethodSource("provideContainsAnyTestCases")
    void when_containsAny_then_returnsCorrectResult(String sourceArray, String elementsArray, boolean expected)
            throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.containsAny(json(sourceArray), json(elementsArray));
        assertThatVal(actual).isEqualTo(Val.of(expected));
    }

    private static Stream<Arguments> provideContainsAnyTestCases() {
        return Stream.of(Arguments.of("[1, 2, 3, 4]", "[3, 5, 6]", true),
                Arguments.of("[1, 2, 3, 4]", "[5, 6, 7]", false), Arguments.of("[1, 2, 3, 4]", "[]", false),
                Arguments.of("[]", "[1, 2, 3]", false), Arguments.of("[1, 2, 3, 4]", "[2, 3, 4]", true),
                Arguments.of("[\"Dagon\", \"Hydra\", \"Cthulhu\"]", "[\"Cthulhu\", \"Hastur\"]", true));
    }

    @ParameterizedTest
    @MethodSource("provideContainsAllTestCases")
    void when_containsAll_then_returnsCorrectResult(String sourceArray, String elementsArray, boolean expected)
            throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.containsAll(json(sourceArray), json(elementsArray));
        assertThatVal(actual).isEqualTo(Val.of(expected));
    }

    private static Stream<Arguments> provideContainsAllTestCases() {
        return Stream.of(Arguments.of("[1, 2, 3, 4, 5]", "[3, 1, 5]", true),
                Arguments.of("[1, 2, 3, 4]", "[3, 5, 6]", false), Arguments.of("[1, 2, 3, 4]", "[]", true),
                Arguments.of("[1, 2, 3]", "[1, 2, 3]", true),
                Arguments.of("[\"Dagon\", \"Hydra\", \"Cthulhu\", \"Nyarlathotep\"]", "[\"Hydra\", \"Dagon\"]", true),
                Arguments.of("[\"Dagon\", \"Hydra\"]", "[\"Hydra\", \"Cthulhu\"]", false));
    }

    @ParameterizedTest
    @MethodSource("provideContainsAllInOrderTestCases")
    void when_containsAllInOrder_then_returnsCorrectResult(String sourceArray, String elementsArray, boolean expected)
            throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.containsAllInOrder(json(sourceArray), json(elementsArray));
        assertThatVal(actual).isEqualTo(Val.of(expected));
    }

    private static Stream<Arguments> provideContainsAllInOrderTestCases() {
        return Stream.of(Arguments.of("[1, 2, 3, 4, 5]", "[2, 4, 5]", true),
                Arguments.of("[1, 2, 3, 4, 5]", "[2, 5, 4]", false), Arguments.of("[1, 2, 3, 4]", "[]", true),
                Arguments.of("[1, 2, 3, 4, 5]", "[2, 6]", false), Arguments.of("[1, 2, 3, 4, 5]", "[1, 1, 2]", false),
                Arguments.of("[1, 1, 2, 3, 4, 5]", "[1, 1, 2]", true),
                Arguments.of("[\"Dagon\", \"Hydra\", \"Cthulhu\", \"Nyarlathotep\"]", "[\"Hydra\", \"Nyarlathotep\"]",
                        true),
                Arguments.of("[\"Dagon\", \"Hydra\", \"Cthulhu\", \"Nyarlathotep\"]", "[\"Cthulhu\", \"Hydra\"]",
                        false));
    }

    @ParameterizedTest
    @MethodSource("provideSortSuccessTestCases")
    void when_sort_then_returnsSortedArray(String inputArray, String expectedArray) throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.sort(json(inputArray));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).isEqualTo(json(expectedArray).getArrayNode());
    }

    private static Stream<Arguments> provideSortSuccessTestCases() {
        return Stream.of(Arguments.of("[3, 1, 4, 1, 5, 9, 2, 6]", "[1, 1, 2, 3, 4, 5, 6, 9]"),
                Arguments.of("[2.71, 1.5, 2.71, 1.0]", "[1.0, 1.5, 2.71, 2.71]"),
                Arguments.of("[\"Shoggoth\", \"Cthulhu\", \"Nyarlathotep\", \"Dagon\"]",
                        "[\"Cthulhu\", \"Dagon\", \"Nyarlathotep\", \"Shoggoth\"]"),
                Arguments.of("[\"10\", \"2\", \"20\", \"1\"]", "[\"1\", \"10\", \"2\", \"20\"]"),
                Arguments.of("[1, 2, 3, 4, 5]", "[1, 2, 3, 4, 5]"), Arguments.of("[5, 4, 3, 2, 1]", "[1, 2, 3, 4, 5]"),
                Arguments.of("[42]", "[42]"), Arguments.of("[3, -1, 4, -5, 2]", "[-5, -1, 2, 3, 4]"));
    }

    @ParameterizedTest
    @MethodSource("provideSortErrorTestCases")
    void when_sortWithInvalidInput_then_returnsError(String inputArray, String expectedErrorMessage)
            throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.sort(json(inputArray));
        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains(expectedErrorMessage);
    }

    private static Stream<Arguments> provideSortErrorTestCases() {
        return Stream.of(Arguments.of("[]", "Cannot sort an empty array"),
                Arguments.of("[1, \"two\", 3]", "All array elements must be numeric"),
                Arguments.of("[true, false, true]", "Array elements must be numeric or text"),
                Arguments.of("[null, 1, 2]", "Array elements must be numeric or text"),
                Arguments.of("[1, 2, \"three\", 4]", "All array elements must be numeric"),
                Arguments.of("[\"apple\", \"banana\", 3, \"cherry\"]", "All array elements must be text"));
    }

    @ParameterizedTest
    @MethodSource("provideIsSetTestCases")
    void when_isSet_then_returnsCorrectResult(String inputArray, boolean expected) throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.isSet(json(inputArray));
        assertThatVal(actual).isEqualTo(Val.of(expected));
    }

    private static Stream<Arguments> provideIsSetTestCases() {
        return Stream.of(Arguments.of("[1, 2, 3, 4]", true), Arguments.of("[1, 2, 3, 2]", false),
                Arguments.of("[]", true), Arguments.of("[1]", true), Arguments.of("[1, 1, 1]", false),
                Arguments.of("[1, \"1\", 2]", true), Arguments.of("[\"Dagon\", \"Hydra\", \"Cthulhu\"]", true),
                Arguments.of("[\"Dagon\", \"Hydra\", \"Dagon\"]", false), Arguments.of("[null, null]", false),
                Arguments.of("[null, 1, 2]", true), Arguments.of("[true, false, true]", false),
                Arguments.of("[{\"a\": 1}, {\"a\": 1}]", false), Arguments.of("[{\"a\": 1}, {\"b\": 2}]", true),
                Arguments.of("[0, 0.0]", true), Arguments.of("[1, 2.0, 3]", true));
    }

    @ParameterizedTest
    @MethodSource("provideIsEmptyTestCases")
    void when_isEmpty_then_returnsCorrectResult(String inputArray, boolean expected) throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.isEmpty(json(inputArray));
        assertThatVal(actual).isEqualTo(Val.of(expected));
    }

    private static Stream<Arguments> provideIsEmptyTestCases() {
        return Stream.of(Arguments.of("[]", true), Arguments.of("[1]", false), Arguments.of("[1, 2, 3]", false),
                Arguments.of("[null]", false), Arguments.of("[[]]", false), Arguments.of("[{}]", false));
    }

    @ParameterizedTest
    @MethodSource("provideHeadAndLastTestCases")
    void when_headOrLast_then_returnsCorrectElement(String functionName, String inputArray, String expectedElement)
            throws JsonProcessingException {
        val actual = "head".equals(functionName) ? ArrayFunctionLibrary.head(json(inputArray))
                : ArrayFunctionLibrary.last(json(inputArray));
        assertThatVal(actual).hasValue();
        assertThat(actual.get()).isEqualTo(json(expectedElement).get());
    }

    private static Stream<Arguments> provideHeadAndLastTestCases() {
        return Stream.of(Arguments.of("head", "[1, 2, 3, 4]", "1"), Arguments.of("last", "[1, 2, 3, 4]", "4"),
                Arguments.of("head", "[\"apple\", \"banana\"]", "\"apple\""),
                Arguments.of("last", "[\"apple\", \"banana\"]", "\"banana\""),
                Arguments.of("head", "[true, false]", "true"), Arguments.of("last", "[true, false]", "false"),
                Arguments.of("head", "[null, 1, 2]", "null"), Arguments.of("last", "[1, 2, null]", "null"));
    }

    @ParameterizedTest
    @MethodSource("provideEmptyArrayErrorTestCases")
    void when_operationOnEmptyArray_then_returnsError(String functionName, String expectedErrorMessage)
            throws JsonProcessingException {
        val actual = switch (functionName) {
        case "head"   -> ArrayFunctionLibrary.head(json("[]"));
        case "last"   -> ArrayFunctionLibrary.last(json("[]"));
        case "max"    -> ArrayFunctionLibrary.max(json("[]"));
        case "min"    -> ArrayFunctionLibrary.min(json("[]"));
        case "avg"    -> ArrayFunctionLibrary.avg(json("[]"));
        case "median" -> ArrayFunctionLibrary.median(json("[]"));
        default       -> throw new IllegalArgumentException("Unknown function: " + functionName);
        };

        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains(expectedErrorMessage);
    }

    private static Stream<Arguments> provideEmptyArrayErrorTestCases() {
        return Stream.of(Arguments.of("head", "Cannot get head of an empty array"),
                Arguments.of("last", "Cannot get last of an empty array"),
                Arguments.of("max", "Cannot find max of an empty array"),
                Arguments.of("min", "Cannot find min of an empty array"),
                Arguments.of("avg", "Cannot calculate average of an empty array"),
                Arguments.of("median", "Cannot calculate median of an empty array"));
    }

    @ParameterizedTest
    @MethodSource("provideMaxMinNumericTestCases")
    void when_maxOrMinWithNumericArray_then_returnsCorrectValue(String functionName, String inputArray,
            double expectedValue) throws JsonProcessingException {
        val actual = "max".equals(functionName) ? ArrayFunctionLibrary.max(json(inputArray))
                : ArrayFunctionLibrary.min(json(inputArray));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().asDouble()).isEqualTo(expectedValue);
    }

    private static Stream<Arguments> provideMaxMinNumericTestCases() {
        return Stream.of(Arguments.of("max", "[3, 1, 4, 1, 5, 9]", 9.0), Arguments.of("min", "[3, 1, 4, 1, 5, 9]", 1.0),
                Arguments.of("max", "[1]", 1.0), Arguments.of("min", "[1]", 1.0),
                Arguments.of("max", "[-5, -1, -10]", -1.0), Arguments.of("min", "[-5, -1, -10]", -10.0),
                Arguments.of("max", "[2.5, 2.7, 2.1]", 2.7), Arguments.of("min", "[2.5, 2.7, 2.1]", 2.1));
    }

    @ParameterizedTest
    @MethodSource("provideMaxMinStringTestCases")
    void when_maxOrMinWithStringArray_then_returnsCorrectValue(String functionName, String inputArray,
            String expectedValue) throws JsonProcessingException {
        val actual = "max".equals(functionName) ? ArrayFunctionLibrary.max(json(inputArray))
                : ArrayFunctionLibrary.min(json(inputArray));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().asText()).isEqualTo(json(expectedValue).get().asText());
    }

    private static Stream<Arguments> provideMaxMinStringTestCases() {
        return Stream.of(Arguments.of("max", "[\"Shoggoth\", \"Cthulhu\", \"Nyarlathotep\"]", "\"Shoggoth\""),
                Arguments.of("min", "[\"Shoggoth\", \"Cthulhu\", \"Nyarlathotep\"]", "\"Cthulhu\""),
                Arguments.of("max", "[\"Azathoth\", \"Yog-Sothoth\", \"Shub-Niggurath\"]", "\"Yog-Sothoth\""),
                Arguments.of("min", "[\"Azathoth\", \"Yog-Sothoth\", \"Shub-Niggurath\"]", "\"Azathoth\""));
    }

    @ParameterizedTest
    @MethodSource("provideMaxMinErrorTestCases")
    void when_maxOrMinWithInvalidInput_then_returnsError(String functionName, String inputArray,
            String expectedErrorMessage) throws JsonProcessingException {
        val actual = "max".equals(functionName) ? ArrayFunctionLibrary.max(json(inputArray))
                : ArrayFunctionLibrary.min(json(inputArray));
        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains(expectedErrorMessage);
    }

    private static Stream<Arguments> provideMaxMinErrorTestCases() {
        return Stream.of(Arguments.of("max", "[1, \"two\", 3]", "All array elements must be numeric"),
                Arguments.of("min", "[1, \"two\", 3]", "All array elements must be numeric"),
                Arguments.of("max", "[true, false]", "Array elements must be numeric or text"),
                Arguments.of("min", "[true, false]", "Array elements must be numeric or text"));
    }

    @ParameterizedTest
    @MethodSource("provideSumTestCases")
    void when_sum_then_returnsCorrectResult(String inputArray, double expectedValue) throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.sum(json(inputArray));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().asDouble()).isEqualTo(expectedValue);
    }

    private static Stream<Arguments> provideSumTestCases() {
        return Stream.of(Arguments.of("[1, 2, 3, 4, 5]", 15.0), Arguments.of("[]", 0.0), Arguments.of("[-5, 5]", 0.0),
                Arguments.of("[2.5, 3.5]", 6.0), Arguments.of("[100]", 100.0));
    }

    @ParameterizedTest
    @MethodSource("provideMultiplyTestCases")
    void when_multiply_then_returnsCorrectResult(String inputArray, double expectedValue)
            throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.multiply(json(inputArray));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().asDouble()).isEqualTo(expectedValue);
    }

    private static Stream<Arguments> provideMultiplyTestCases() {
        return Stream.of(Arguments.of("[2, 3, 4]", 24.0), Arguments.of("[]", 1.0), Arguments.of("[5, 0]", 0.0),
                Arguments.of("[2.5, 2]", 5.0), Arguments.of("[7]", 7.0));
    }

    @ParameterizedTest
    @MethodSource("provideNumericAggregationErrorTestCases")
    void when_numericAggregationWithNonNumeric_then_returnsError(String functionName, String inputArray)
            throws JsonProcessingException {
        val actual = switch (functionName) {
        case "sum"      -> ArrayFunctionLibrary.sum(json(inputArray));
        case "multiply" -> ArrayFunctionLibrary.multiply(json(inputArray));
        case "avg"      -> ArrayFunctionLibrary.avg(json(inputArray));
        case "median"   -> ArrayFunctionLibrary.median(json(inputArray));
        default         -> throw new IllegalArgumentException("Unknown function: " + functionName);
        };

        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains("All array elements must be numeric");
    }

    private static Stream<Arguments> provideNumericAggregationErrorTestCases() {
        return Stream.of(Arguments.of("sum", "[1, 2, \"three\"]"), Arguments.of("multiply", "[1, 2, \"three\"]"),
                Arguments.of("avg", "[1, 2, \"three\"]"), Arguments.of("median", "[1, 2, \"three\"]"));
    }

    @ParameterizedTest
    @MethodSource("provideAvgTestCases")
    void when_avg_then_returnsCorrectResult(String inputArray, double expectedValue) throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.avg(json(inputArray));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().asDouble()).isEqualTo(expectedValue, org.assertj.core.data.Offset.offset(0.0001));
    }

    private static Stream<Arguments> provideAvgTestCases() {
        return Stream.of(Arguments.of("[1, 2, 3, 4, 5]", 3.0), Arguments.of("[10, 20]", 15.0), Arguments.of("[5]", 5.0),
                Arguments.of("[0, 0, 0]", 0.0));
    }

    @ParameterizedTest
    @MethodSource("provideMedianTestCases")
    void when_median_then_returnsCorrectResult(String inputArray, double expectedValue) throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.median(json(inputArray));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().asDouble()).isEqualTo(expectedValue);
    }

    private static Stream<Arguments> provideMedianTestCases() {
        return Stream.of(Arguments.of("[1, 2, 3, 4, 5]", 3.0), Arguments.of("[1, 2, 3, 4]", 2.5),
                Arguments.of("[5, 1, 3, 2, 4]", 3.0), Arguments.of("[1]", 1.0), Arguments.of("[10, 20]", 15.0));
    }

    @ParameterizedTest
    @MethodSource("provideRangeTestCases")
    void when_range_then_returnsCorrectResult(int from, int to, String expectedArray) throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.range(Val.of(from), Val.of(to));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).isEqualTo(json(expectedArray).getArrayNode());
    }

    private static Stream<Arguments> provideRangeTestCases() {
        return Stream.of(Arguments.of(1, 5, "[1, 2, 3, 4, 5]"), Arguments.of(5, 5, "[5]"), Arguments.of(5, 2, "[]"),
                Arguments.of(0, 3, "[0, 1, 2, 3]"), Arguments.of(-2, 2, "[-2, -1, 0, 1, 2]"));
    }

    @ParameterizedTest
    @MethodSource("provideRangeWithStepTestCases")
    void when_rangeWithStep_then_returnsCorrectResult(int from, int to, int step, String expectedArray)
            throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.rangeStepped(Val.of(from), Val.of(to), Val.of(step));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).isEqualTo(json(expectedArray).getArrayNode());
    }

    private static Stream<Arguments> provideRangeWithStepTestCases() {
        return Stream.of(Arguments.of(1, 10, 2, "[1, 3, 5, 7, 9]"), Arguments.of(10, 1, -2, "[10, 8, 6, 4, 2]"),
                Arguments.of(5, 2, 1, "[]"), Arguments.of(1, 5, -1, "[]"), Arguments.of(0, 10, 3, "[0, 3, 6, 9]"),
                Arguments.of(-5, 5, 2, "[-5, -3, -1, 1, 3, 5]"));
    }

    @Test
    void when_rangeWithStepZero_then_returnsError() {
        val actual = ArrayFunctionLibrary.rangeStepped(Val.of(1), Val.of(5), Val.of(0));
        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains("Step must not be zero");
    }

    @ParameterizedTest
    @MethodSource("provideCrossProductTestCases")
    void when_crossProduct_then_returnsCorrectResult(String array1, String array2, String expectedArray)
            throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.crossProduct(json(array1), json(array2));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).isEqualTo(json(expectedArray).getArrayNode());
    }

    private static Stream<Arguments> provideCrossProductTestCases() {
        return Stream.of(Arguments.of("[1, 2]", "[\"a\", \"b\"]", "[[1, \"a\"], [1, \"b\"], [2, \"a\"], [2, \"b\"]]"),
                Arguments.of("[1]", "[\"x\", \"y\", \"z\"]", "[[1, \"x\"], [1, \"y\"], [1, \"z\"]]"),
                Arguments.of("[]", "[1, 2]", "[]"), Arguments.of("[1, 2]", "[]", "[]"),
                Arguments.of("[true]", "[1, 2]", "[[true, 1], [true, 2]]"));
    }

    @ParameterizedTest
    @MethodSource("provideZipTestCases")
    void when_zip_then_returnsCorrectResult(String array1, String array2, String expectedArray)
            throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.zip(json(array1), json(array2));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).isEqualTo(json(expectedArray).getArrayNode());
    }

    private static Stream<Arguments> provideZipTestCases() {
        return Stream.of(Arguments.of("[1, 2, 3]", "[\"a\", \"b\", \"c\"]", "[[1, \"a\"], [2, \"b\"], [3, \"c\"]]"),
                Arguments.of("[1, 2, 3, 4]", "[\"a\", \"b\"]", "[[1, \"a\"], [2, \"b\"]]"),
                Arguments.of("[]", "[1, 2, 3]", "[]"), Arguments.of("[1, 2, 3]", "[]", "[]"),
                Arguments.of("[true, false]", "[1, 0]", "[[true, 1], [false, 0]]"));
    }
}
