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
package io.sapl.functions.libraries;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for ArrayFunctionLibrary using the new Value model.
 */
class ArrayFunctionLibraryTests {

    private static ArrayValue array(int... numbers) {
        return Value.ofArray(IntStream.of(numbers).mapToObj(Value::of).toArray(Value[]::new));
    }

    private static Value[] values(int... numbers) {
        return IntStream.of(numbers).mapToObj(Value::of).toArray(Value[]::new);
    }

    @Test
    void when_loadedIntoBroker_then_noError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.loadStaticFunctionLibrary(ArrayFunctionLibrary.class))
                .doesNotThrowAnyException();
    }

    @Test
    void when_concatenateNoParameters_then_returnsEmptyArray() {
        val result = ArrayFunctionLibrary.concatenate();
        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).isEmpty();
    }

    @Test
    void when_concatenate_then_concatenatesCorrectly() {
        val result = ArrayFunctionLibrary.concatenate(array(1, 2), Value.EMPTY_ARRAY, array(3, 4), array(5, 6));
        assertThat(result).isInstanceOf(ArrayValue.class).isEqualTo(array(1, 2, 3, 4, 5, 6));
    }

    @Test
    void when_intersectNoParameters_then_returnsEmptyArray() {
        val result = ArrayFunctionLibrary.intersect();
        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).isEmpty();
    }

    @Test
    void when_intersect_then_returnsIntersection() {
        val result = ArrayFunctionLibrary.intersect(array(1, 2, 3, 4), array(3, 4), array(4, 1, 3));
        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).hasSize(2).containsExactlyInAnyOrder(values(4, 3));
    }

    @Test
    void when_intersectWithOneEmptySet_then_returnsEmptyArray() {
        val result = ArrayFunctionLibrary.intersect(array(1, 2, 3, 4), Value.EMPTY_ARRAY, array(3, 4), array(4, 1, 3));
        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).isEmpty();
    }

    @Test
    void when_intersectWithManyArrays_then_performsCorrectly() {
        val result = ArrayFunctionLibrary.intersect(array(1, 2, 3, 4, 5), array(2, 3, 4, 5, 6), array(3, 4, 5, 6, 7),
                array(4, 5, 6, 7, 8), array(5, 6, 7, 8, 9));
        assertThat(result).isInstanceOf(ArrayValue.class);
        val resultArray = (ArrayValue) result;
        assertThat(resultArray).hasSize(1);
        assertThat(resultArray.getFirst()).isEqualTo(Value.of(5));
    }

    @Test
    void when_intersectWithNoCommonElements_then_returnsEmpty() {
        val result = ArrayFunctionLibrary.intersect(array(1, 2, 3), array(4, 5, 6), array(7, 8, 9));
        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).isEmpty();
    }

    @Test
    void when_unionNoParameters_then_returnsEmptyArray() {
        val result = ArrayFunctionLibrary.union();
        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).isEmpty();
    }

    @Test
    void when_union_then_returnsUnion() {
        val result = ArrayFunctionLibrary.union(array(1, 2, 3), Value.EMPTY_ARRAY, array(3, 4), array(4, 1, 3));
        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).hasSize(4).containsExactlyInAnyOrder(values(4, 3, 1, 2));
    }

    @Test
    void when_unionWithManyArrays_then_performsCorrectly() {
        val result = ArrayFunctionLibrary.union(array(1, 2), array(2, 3), array(3, 4), array(4, 5));
        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).hasSize(5).containsExactlyInAnyOrder(values(1, 2, 3, 4, 5));
    }

    @Test
    void when_toSet_then_returnsSet() {
        val result = ArrayFunctionLibrary.toSet(array(1, 2, 3, 2, 1, 1, 1, 5, 8, 10, 8, 10, 3));
        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).hasSize(6).containsExactlyInAnyOrder(values(1, 2, 3, 5, 8, 10));
    }

    // Performance tests continue with builder pattern
    @Test
    void when_unionWithLargeArrays_then_performsEfficiently() {
        val array1 = ArrayValue.builder();
        val array2 = ArrayValue.builder();

        for (int i = 0; i < 10000; i++) {
            array1.add(Value.of(i));
        }
        for (int i = 5000; i < 15000; i++) {
            array2.add(Value.of(i));
        }

        val startTime = System.currentTimeMillis();
        val result    = ArrayFunctionLibrary.union(array1.build(), array2.build());
        val endTime   = System.currentTimeMillis();

        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).hasSize(15000);
        assertThat(endTime - startTime).isLessThan(1000);
    }
}
