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
    void when_differenceWithIntersection_then_returnsCorrectDifferenceAsSet() throws JsonProcessingException {
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

    @Test
    void when_containsAnyWithMatchingElement_then_returnsTrue() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.containsAny(Val.ofJson("[1, 2, 3, 4]"), Val.ofJson("[3, 5, 6]"));
        assertThatVal(actual).isTrue();
    }

    @Test
    void when_containsAnyWithNoMatchingElements_then_returnsFalse() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.containsAny(Val.ofJson("[1, 2, 3, 4]"), Val.ofJson("[5, 6, 7]"));
        assertThatVal(actual).isFalse();
    }

    @Test
    void when_containsAnyWithEmptyElementsArray_then_returnsFalse() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.containsAny(Val.ofJson("[1, 2, 3, 4]"), Val.ofJson("[]"));
        assertThatVal(actual).isFalse();
    }

    @Test
    void when_containsAnyWithEmptySourceArray_then_returnsFalse() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.containsAny(Val.ofJson("[]"), Val.ofJson("[1, 2, 3]"));
        assertThatVal(actual).isFalse();
    }

    @Test
    void when_containsAnyWithMultipleMatches_then_returnsTrue() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.containsAny(Val.ofJson("[1, 2, 3, 4]"), Val.ofJson("[2, 3, 4]"));
        assertThatVal(actual).isTrue();
    }

    @Test
    void when_containsAnyWithStrings_then_worksCorrectly() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.containsAny(Val.ofJson("[\"apple\", \"banana\", \"cherry\"]"),
                Val.ofJson("[\"banana\", \"date\"]"));
        assertThatVal(actual).isTrue();
    }

    @Test
    void when_containsAllWithAllElementsPresent_then_returnsTrue() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.containsAll(Val.ofJson("[1, 2, 3, 4, 5]"), Val.ofJson("[3, 1, 5]"));
        assertThatVal(actual).isTrue();
    }

    @Test
    void when_containsAllWithMissingElements_then_returnsFalse() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.containsAll(Val.ofJson("[1, 2, 3, 4]"), Val.ofJson("[3, 5, 6]"));
        assertThatVal(actual).isFalse();
    }

    @Test
    void when_containsAllWithEmptyElementsArray_then_returnsTrue() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.containsAll(Val.ofJson("[1, 2, 3, 4]"), Val.ofJson("[]"));
        assertThatVal(actual).isTrue();
    }

    @Test
    void when_containsAllWithIdenticalArrays_then_returnsTrue() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.containsAll(Val.ofJson("[1, 2, 3]"), Val.ofJson("[1, 2, 3]"));
        assertThatVal(actual).isTrue();
    }

    @Test
    void when_containsAllWithStrings_then_worksCorrectly() throws JsonProcessingException {
        val actualTrue = ArrayFunctionLibrary.containsAll(Val.ofJson("[\"apple\", \"banana\", \"cherry\", \"date\"]"),
                Val.ofJson("[\"banana\", \"apple\"]"));
        assertThatVal(actualTrue).isTrue();

        val actualFalse = ArrayFunctionLibrary.containsAll(Val.ofJson("[\"apple\", \"banana\"]"),
                Val.ofJson("[\"banana\", \"cherry\"]"));
        assertThatVal(actualFalse).isFalse();
    }

    @Test
    void when_containsAllInOrderWithCorrectOrder_then_returnsTrue() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.containsAllInOrder(Val.ofJson("[1, 2, 3, 4, 5]"), Val.ofJson("[2, 4, 5]"));
        assertThatVal(actual).isTrue();
    }

    @Test
    void when_containsAllInOrderWithWrongOrder_then_returnsFalse() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.containsAllInOrder(Val.ofJson("[1, 2, 3, 4, 5]"), Val.ofJson("[2, 5, 4]"));
        assertThatVal(actual).isFalse();
    }

    @Test
    void when_containsAllInOrderWithEmptyElementsArray_then_returnsTrue() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.containsAllInOrder(Val.ofJson("[1, 2, 3, 4]"), Val.ofJson("[]"));
        assertThatVal(actual).isTrue();
    }

    @Test
    void when_containsAllInOrderWithMissingElements_then_returnsFalse() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.containsAllInOrder(Val.ofJson("[1, 2, 3, 4, 5]"), Val.ofJson("[2, 6]"));
        assertThatVal(actual).isFalse();
    }

    @Test
    void when_containsAllInOrderWithDuplicatesRequired_then_returnsFalseIfNotEnough() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.containsAllInOrder(Val.ofJson("[1, 2, 3, 4, 5]"), Val.ofJson("[1, 1, 2]"));
        assertThatVal(actual).isFalse();
    }

    @Test
    void when_containsAllInOrderWithDuplicatesPresent_then_returnsTrue() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.containsAllInOrder(Val.ofJson("[1, 1, 2, 3, 4, 5]"), Val.ofJson("[1, 1, 2]"));
        assertThatVal(actual).isTrue();
    }

    @Test
    void when_containsAllInOrderWithStrings_then_worksCorrectly() throws JsonProcessingException {
        val actualTrue = ArrayFunctionLibrary.containsAllInOrder(
                Val.ofJson("[\"apple\", \"banana\", \"cherry\", \"date\"]"), Val.ofJson("[\"banana\", \"date\"]"));
        assertThatVal(actualTrue).isTrue();

        val actualFalse = ArrayFunctionLibrary.containsAllInOrder(
                Val.ofJson("[\"apple\", \"banana\", \"cherry\", \"date\"]"), Val.ofJson("[\"cherry\", \"banana\"]"));
        assertThatVal(actualFalse).isFalse();
    }

    @Test
    void when_sortNumericArray_then_returnsSortedArray() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.sort(Val.ofJson("[3, 1, 4, 1, 5, 9, 2, 6]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).containsExactly(Val.of(1).getJsonNode(), Val.of(1).getJsonNode(),
                Val.of(2).getJsonNode(), Val.of(3).getJsonNode(), Val.of(4).getJsonNode(), Val.of(5).getJsonNode(),
                Val.of(6).getJsonNode(), Val.of(9).getJsonNode());
    }

    @Test
    void when_sortNumericArrayWithDecimals_then_returnsSortedArray() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.sort(Val.ofJson("[3.14, 1.5, 2.71, 1.0]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(4);
        assertThat(actual.getArrayNode().get(0).asDouble()).isEqualTo(1.0);
        assertThat(actual.getArrayNode().get(1).asDouble()).isEqualTo(1.5);
        assertThat(actual.getArrayNode().get(2).asDouble()).isEqualTo(2.71);
        assertThat(actual.getArrayNode().get(3).asDouble()).isEqualTo(3.14);
    }

    @Test
    void when_sortTextArray_then_returnsSortedArray() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.sort(Val.ofJson("[\"dog\", \"cat\", \"bird\", \"ant\"]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).containsExactly(Val.of("ant").getJsonNode(), Val.of("bird").getJsonNode(),
                Val.of("cat").getJsonNode(), Val.of("dog").getJsonNode());
    }

    @Test
    void when_sortTextArrayWithNumbers_then_sortsByString() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.sort(Val.ofJson("[\"10\", \"2\", \"20\", \"1\"]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).containsExactly(Val.of("1").getJsonNode(), Val.of("10").getJsonNode(),
                Val.of("2").getJsonNode(), Val.of("20").getJsonNode());
    }

    @Test
    void when_sortEmptyArray_then_returnsError() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.sort(Val.ofJson("[]"));
        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains("Cannot sort an empty array");
    }

    @Test
    void when_sortMixedTypesArray_then_returnsError() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.sort(Val.ofJson("[1, \"two\", 3]"));
        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains("All array elements must be numeric");
    }

    @Test
    void when_sortBooleanArray_then_returnsError() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.sort(Val.ofJson("[true, false, true]"));
        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains("Array elements must be numeric or text");
    }

    @Test
    void when_sortArrayWithNullFirstElement_then_returnsError() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.sort(Val.ofJson("[null, 1, 2]"));
        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains("Array elements must be numeric or text");
    }

    @Test
    void when_sortNumericArrayWithNonNumericElement_then_returnsError() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.sort(Val.ofJson("[1, 2, \"three\", 4]"));
        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains("All array elements must be numeric");
    }

    @Test
    void when_sortTextArrayWithNonTextElement_then_returnsError() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.sort(Val.ofJson("[\"apple\", \"banana\", 3, \"cherry\"]"));
        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains("All array elements must be text");
    }

    @Test
    void when_sortAlreadySortedArray_then_returnsIdenticalArray() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.sort(Val.ofJson("[1, 2, 3, 4, 5]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).containsExactly(Val.of(1).getJsonNode(), Val.of(2).getJsonNode(),
                Val.of(3).getJsonNode(), Val.of(4).getJsonNode(), Val.of(5).getJsonNode());
    }

    @Test
    void when_sortReverseSortedArray_then_returnsCorrectOrder() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.sort(Val.ofJson("[5, 4, 3, 2, 1]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).containsExactly(Val.of(1).getJsonNode(), Val.of(2).getJsonNode(),
                Val.of(3).getJsonNode(), Val.of(4).getJsonNode(), Val.of(5).getJsonNode());
    }

    @Test
    void when_sortSingleElementArray_then_returnsSameArray() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.sort(Val.ofJson("[42]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).containsExactly(Val.of(42).getJsonNode());
    }

    @Test
    void when_sortNumericArrayWithNegativeNumbers_then_returnsSortedArray() throws JsonProcessingException {
        val actual = ArrayFunctionLibrary.sort(Val.ofJson("[3, -1, 4, -5, 2]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).containsExactly(Val.of(-5).getJsonNode(), Val.of(-1).getJsonNode(),
                Val.of(2).getJsonNode(), Val.of(3).getJsonNode(), Val.of(4).getJsonNode());
    }
}
