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

    @Test
    void when_concatenateNoParameters_then_returnsEmptyArray() {
        assertThatVal(ArrayFunctionLibrary.concatenate()).hasValue().isArray().isEmpty();
    }

    @Test
    void when_concatenate_then_concatenatesCorrectly() throws JsonProcessingException {
        assertThatVal(ArrayFunctionLibrary.concatenate(Val.ofJson("[ 1,2 ]"), Val.ofJson("[ ]"), Val.ofJson("[ 3,4 ]"),
                Val.ofJson("[ 5,6 ]"))).hasValue().isArray().isEqualTo(Val.ofJson("[1,2,3,4,5,6]").getArrayNode());
    }

    @Test
    void when_intersectNoParameters_then_returnsEmptyArray() {
        assertThatVal(ArrayFunctionLibrary.intersect()).hasValue().isArray().isEmpty();
    }

    @Test
    void when_intersect_then_returnsIntersection() throws JsonProcessingException {
        final var actual = ArrayFunctionLibrary.intersect(Val.ofJson("[ 1,2,3,4 ]"), Val.ofJson("[ 3,4 ]"),
                Val.ofJson("[ 4,1,3 ]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(2);
        assertThat(actual.getArrayNode()).containsExactlyInAnyOrder(Val.of(4).getJsonNode(), Val.of(3).getJsonNode());
    }

    @Test
    void when_intersectWithOneEmptySet_then_returnsEmptyArray() throws JsonProcessingException {
        assertThatVal(ArrayFunctionLibrary.intersect(Val.ofJson("[ 1,2,3,4 ]"), Val.ofJson("[ ]"),
                Val.ofJson("[ 3,4 ]"), Val.ofJson("[ 4,1,3 ]"))).hasValue().isArray().isEmpty();
    }

    @Test
    void when_unionNoParameters_then_returnsEmptyArray() {
        assertThatVal(ArrayFunctionLibrary.union()).hasValue().isArray().isEmpty();
    }

    @Test
    void when_union_then_returnsUnion() throws JsonProcessingException {
        final var actual = ArrayFunctionLibrary.union(Val.ofJson("[ 1,2,3 ]"), Val.ofJson("[ ]"), Val.ofJson("[ 3,4 ]"),
                Val.ofJson("[ 4,1,3 ]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(4);
        assertThat(actual.getArrayNode()).containsExactlyInAnyOrder(Val.of(4).getJsonNode(), Val.of(3).getJsonNode(),
                Val.of(1).getJsonNode(), Val.of(2).getJsonNode());
    }

    @Test
    void when_toSet_then_returnsSet() throws JsonProcessingException {
        final var actual = ArrayFunctionLibrary.toSet(Val.ofJson("[ 1,2,3,2,1,1,1,5,8,10,8,10,3 ]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(6);
        assertThat(actual.getArrayNode()).containsExactlyInAnyOrder(Val.of(1).getJsonNode(), Val.of(2).getJsonNode(),
                Val.of(3).getJsonNode(), Val.of(5).getJsonNode(), Val.of(8).getJsonNode(), Val.of(10).getJsonNode());
    }

    @Test
    void when_differenceWithEmptySet_then_returnsOriginalArrayAsSet() throws JsonProcessingException {
        final var actual = ArrayFunctionLibrary.difference(Val.ofJson("[ 1,2,3,2,1,1,1,5,8,10,8,10,3 ]"),
                Val.ofJson("[]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(6);
        assertThat(actual.getArrayNode()).containsExactlyInAnyOrder(Val.of(1).getJsonNode(), Val.of(2).getJsonNode(),
                Val.of(3).getJsonNode(), Val.of(5).getJsonNode(), Val.of(8).getJsonNode(), Val.of(10).getJsonNode());
    }

    @Test
    void when_differenceWithNoIntersection_then_returnsOriginalArrayAsSet() throws JsonProcessingException {
        final var actual = ArrayFunctionLibrary.difference(Val.ofJson("[ 1,2,3,2,1,1,1,5,8,10,8,10,3 ]"),
                Val.ofJson("[20,22,\"abc\"]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(6);
        assertThat(actual.getArrayNode()).containsExactlyInAnyOrder(Val.of(1).getJsonNode(), Val.of(2).getJsonNode(),
                Val.of(3).getJsonNode(), Val.of(5).getJsonNode(), Val.of(8).getJsonNode(), Val.of(10).getJsonNode());
    }

    @Test
    void when_differenceWithIntersection_then_returnsCorrectDifferecrAsSet() throws JsonProcessingException {
        final var actual = ArrayFunctionLibrary.difference(Val.ofJson("[ 1,2,3,2,1,1,1,5,8,10,8,10,3 ]"),
                Val.ofJson("[10,2]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(4);
        assertThat(actual.getArrayNode()).containsExactlyInAnyOrder(Val.of(1).getJsonNode(), Val.of(3).getJsonNode(),
                Val.of(5).getJsonNode(), Val.of(8).getJsonNode());
    }

    @Test
    void when_flattenEmptyArray_then_returnsEmptyArray() throws JsonProcessingException {
        assertThatVal(ArrayFunctionLibrary.flatten(Val.ofJson("[]"))).hasValue().isArray().isEmpty();
    }

    @Test
    void when_flattenArrayWithoutNesting_then_returnsIdenticalArray() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.flatten(Val.ofJson("[1, 2, 3, 4]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(4);
        assertThat(actual.getArrayNode()).containsExactly(Val.of(1).getJsonNode(), Val.of(2).getJsonNode(),
                Val.of(3).getJsonNode(), Val.of(4).getJsonNode());
    }

    @Test
    void when_flattenNestedArray_then_flattensOneLevel() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.flatten(Val.ofJson("[1, [2, 3], 4, [3, 2], 1]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(7);
        assertThat(actual.getArrayNode()).containsExactly(Val.of(1).getJsonNode(), Val.of(2).getJsonNode(),
                Val.of(3).getJsonNode(), Val.of(4).getJsonNode(), Val.of(3).getJsonNode(), Val.of(2).getJsonNode(),
                Val.of(1).getJsonNode());
    }

    @Test
    void when_flattenArrayWithEmptyNestedArrays_then_removesEmptyArrays() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.flatten(Val.ofJson("[1, [], 2, [], 3]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(3);
        assertThat(actual.getArrayNode()).containsExactly(Val.of(1).getJsonNode(), Val.of(2).getJsonNode(),
                Val.of(3).getJsonNode());
    }

    @ParameterizedTest
    @MethodSource("provideSizeTestCases")
    void when_size_then_returnsCorrectCount(String arrayJson, int expectedSize) throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.size(Val.ofJson(arrayJson));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().asInt()).isEqualTo(expectedSize);
    }

    private static Stream<Arguments> provideSizeTestCases() {
        return Stream.of(Arguments.of("[]", 0), Arguments.of("[1]", 1), Arguments.of("[1, 2, 3, 4, 5]", 5),
                Arguments.of("[1, [2, 3], 4]", 3));
    }

    @Test
    void when_reverseEmptyArray_then_returnsEmptyArray() throws JsonProcessingException {
        assertThatVal(ArrayFunctionLibrary.reverse(Val.ofJson("[]"))).hasValue().isArray().isEmpty();
    }

    @Test
    void when_reverseSingleElement_then_returnsSameArray() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.reverse(Val.ofJson("[1]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(1);
        assertThat(actual.getArrayNode()).containsExactly(Val.of(1).getJsonNode());
    }

    @Test
    void when_reverseMultipleElements_then_reversesOrder() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.reverse(Val.ofJson("[1, 2, 3, 4]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(4);
        assertThat(actual.getArrayNode()).containsExactly(Val.of(4).getJsonNode(), Val.of(3).getJsonNode(),
                Val.of(2).getJsonNode(), Val.of(1).getJsonNode());
    }

    @Test
    void when_reverseMixedTypes_then_reversesOrderPreservingTypes() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.reverse(Val.ofJson("[1, \"abc\", true, null, 2.5]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(5);
        assertThat(actual.getArrayNode().get(0)).isEqualTo(Val.of(2.5).getJsonNode());
        assertThat(actual.getArrayNode().get(1).isNull()).isTrue();
        assertThat(actual.getArrayNode().get(2)).isEqualTo(Val.of(true).getJsonNode());
        assertThat(actual.getArrayNode().get(3)).isEqualTo(Val.of("abc").getJsonNode());
        assertThat(actual.getArrayNode().get(4)).isEqualTo(Val.of(1).getJsonNode());
    }
}
