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
package io.sapl.functions.libraries;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for ArrayFunctionLibrary using the new Value model.
 */
@DisplayName("ArrayFunctionLibrary")
class ArrayFunctionLibraryTests {

    private static ArrayValue array(int... numbers) {
        return Value.ofArray(IntStream.of(numbers).mapToObj(Value::of).toArray(Value[]::new));
    }

    private static Value[] values(int... numbers) {
        return IntStream.of(numbers).mapToObj(Value::of).toArray(Value[]::new);
    }

    @Test
    void whenLoadedIntoBrokerThenNoError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.load(new ArrayFunctionLibrary())).doesNotThrowAnyException();
    }

    @Test
    void whenConcatenateNoParametersThenReturnsEmptyArray() {
        val result = ArrayFunctionLibrary.concatenate();
        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).isEmpty();
    }

    @Test
    void whenConcatenateThenConcatenatesCorrectly() {
        val result = ArrayFunctionLibrary.concatenate(array(1, 2), Value.EMPTY_ARRAY, array(3, 4), array(5, 6));
        assertThat(result).isInstanceOf(ArrayValue.class).isEqualTo(array(1, 2, 3, 4, 5, 6));
    }

    @Test
    void whenIntersectNoParametersThenReturnsEmptyArray() {
        val result = ArrayFunctionLibrary.intersect();
        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).isEmpty();
    }

    @Test
    void whenIntersectThenReturnsIntersection() {
        val result = ArrayFunctionLibrary.intersect(array(1, 2, 3, 4), array(3, 4), array(4, 1, 3));
        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).hasSize(2).containsExactlyInAnyOrder(values(4, 3));
    }

    @Test
    void whenIntersectWithOneEmptySetThenReturnsEmptyArray() {
        val result = ArrayFunctionLibrary.intersect(array(1, 2, 3, 4), Value.EMPTY_ARRAY, array(3, 4), array(4, 1, 3));
        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).isEmpty();
    }

    @Test
    void whenIntersectWithManyArraysThenPerformsCorrectly() {
        val result = ArrayFunctionLibrary.intersect(array(1, 2, 3, 4, 5), array(2, 3, 4, 5, 6), array(3, 4, 5, 6, 7),
                array(4, 5, 6, 7, 8), array(5, 6, 7, 8, 9));
        assertThat(result).isInstanceOf(ArrayValue.class);
        val resultArray = (ArrayValue) result;
        assertThat(resultArray).hasSize(1);
        assertThat(resultArray.getFirst()).isEqualTo(Value.of(5));
    }

    @Test
    void whenIntersectWithNoCommonElementsThenReturnsEmpty() {
        val result = ArrayFunctionLibrary.intersect(array(1, 2, 3), array(4, 5, 6), array(7, 8, 9));
        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).isEmpty();
    }

    @Test
    void whenUnionNoParametersThenReturnsEmptyArray() {
        val result = ArrayFunctionLibrary.union();
        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).isEmpty();
    }

    @Test
    void whenUnionThenReturnsUnion() {
        val result = ArrayFunctionLibrary.union(array(1, 2, 3), Value.EMPTY_ARRAY, array(3, 4), array(4, 1, 3));
        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).hasSize(4).containsExactlyInAnyOrder(values(4, 3, 1, 2));
    }

    @Test
    void whenUnionWithManyArraysThenPerformsCorrectly() {
        val result = ArrayFunctionLibrary.union(array(1, 2), array(2, 3), array(3, 4), array(4, 5));
        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).hasSize(5).containsExactlyInAnyOrder(values(1, 2, 3, 4, 5));
    }

    @Test
    void whenToSetThenReturnsSet() {
        val result = ArrayFunctionLibrary.toSet(array(1, 2, 3, 2, 1, 1, 1, 5, 8, 10, 8, 10, 3));
        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).hasSize(6).containsExactlyInAnyOrder(values(1, 2, 3, 5, 8, 10));
    }

    // Performance tests continue with builder pattern
    @Test
    void whenUnionWithLargeArraysThenPerformsEfficiently() {
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

    @Test
    void whenRangeExceedsMaximumThenError() {
        val result = ArrayFunctionLibrary.range(Value.of(0), Value.of(100000));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("exceeds");
    }

    @Test
    void whenRangeBoundsWouldOverflowThenErrorWithoutHanging() {
        val result = ArrayFunctionLibrary.range(Value.of(0), Value.of(Long.MAX_VALUE));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("exceeds");
    }

    @Nested
    @DisplayName("numeric aggregation precision and overflow")
    class NumericAggregationTests {

        private static ArrayValue arrayOf(BigDecimal... numbers) {
            val builder = ArrayValue.builder();
            for (val number : numbers) {
                builder.add(Value.of(number));
            }
            return builder.build();
        }

        @Test
        @DisplayName("max returns the true largest integer beyond double precision")
        void whenIntegersExceedDoublePrecisionThenMaxReturnsTrueLargestElement() {
            val larger  = new BigDecimal("9007199254740993");
            val smaller = new BigDecimal("9007199254740992");

            val result = ArrayFunctionLibrary.max(arrayOf(larger, smaller));

            assertThat(result).isInstanceOf(NumberValue.class).extracting(value -> ((NumberValue) value).value())
                    .satisfies(value -> assertThat(value).isEqualByComparingTo(larger));
        }

        @Test
        @DisplayName("min returns the true smallest high-precision decimal")
        void whenDecimalsDifferBelowDoublePrecisionThenMinReturnsTrueSmallestElement() {
            val smaller = new BigDecimal("0.10000000000000001");
            val larger  = new BigDecimal("0.10000000000000002");

            val result = ArrayFunctionLibrary.min(arrayOf(larger, smaller));

            assertThat(result).isInstanceOf(NumberValue.class).extracting(value -> ((NumberValue) value).value())
                    .satisfies(value -> assertThat(value).isEqualByComparingTo(smaller));
        }

        @Test
        @DisplayName("sum of values whose total exceeds double range stays numeric and exact")
        void whenSumExceedsDoubleRangeThenNumericResultNotError() {
            val huge = new BigDecimal("1E308");

            val result = ArrayFunctionLibrary.sum(arrayOf(huge, huge));

            assertThat(result).isInstanceOf(NumberValue.class).extracting(value -> ((NumberValue) value).value())
                    .satisfies(value -> assertThat(value).isEqualByComparingTo(new BigDecimal("2E308")));
        }

        @Test
        @DisplayName("multiply of values whose product exceeds double range stays numeric and exact")
        void whenProductExceedsDoubleRangeThenNumericResultNotError() {
            val huge = new BigDecimal("1E308");

            val result = ArrayFunctionLibrary.multiply(arrayOf(huge, huge));

            assertThat(result).isInstanceOf(NumberValue.class).extracting(value -> ((NumberValue) value).value())
                    .satisfies(value -> assertThat(value).isEqualByComparingTo(new BigDecimal("1E616")));
        }
    }
}
