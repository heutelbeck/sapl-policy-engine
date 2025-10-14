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

    /* Helper to create Val from JSON string */
    private static Val json(String json) throws JsonProcessingException {
        return Val.ofJson(json);
    }

    /* Helper to extract JsonNode array from Val objects */
    private static JsonNode[] nodes(Val... vals) {
        return Stream.of(vals).map(Val::getJsonNode).toArray(JsonNode[]::new);
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
        assertThat(actual.getArrayNode()).containsExactlyInAnyOrder(nodes(Val.of(4), Val.of(3)));
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
        assertThat(actual.getArrayNode()).containsExactlyInAnyOrder(nodes(Val.of(4), Val.of(3), Val.of(1), Val.of(2)));
    }

    @Test
    void when_toSet_then_returnsSet() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.toSet(json("[ 1,2,3,2,1,1,1,5,8,10,8,10,3 ]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(6);
        assertThat(actual.getArrayNode())
                .containsExactlyInAnyOrder(nodes(Val.of(1), Val.of(2), Val.of(3), Val.of(5), Val.of(8), Val.of(10)));
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
                Arguments.of("[\"apple\", \"banana\", \"cherry\"]", "[\"banana\", \"date\"]", true));
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
                Arguments.of("[\"apple\", \"banana\", \"cherry\", \"date\"]", "[\"banana\", \"apple\"]", true),
                Arguments.of("[\"apple\", \"banana\"]", "[\"banana\", \"cherry\"]", false));
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
                Arguments.of("[\"apple\", \"banana\", \"cherry\", \"date\"]", "[\"banana\", \"date\"]", true),
                Arguments.of("[\"apple\", \"banana\", \"cherry\", \"date\"]", "[\"cherry\", \"banana\"]", false));
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
                Arguments.of("[3.14, 1.5, 2.71, 1.0]", "[1.0, 1.5, 2.71, 3.14]"),
                Arguments.of("[\"dog\", \"cat\", \"bird\", \"ant\"]", "[\"ant\", \"bird\", \"cat\", \"dog\"]"),
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
                Arguments.of("[1, \"1\", 2]", true), Arguments.of("[\"apple\", \"banana\", \"cherry\"]", true),
                Arguments.of("[\"apple\", \"banana\", \"apple\"]", false), Arguments.of("[null, null]", false),
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
}
