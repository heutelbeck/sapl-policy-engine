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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.compiler.operators.ComparisonOperators;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ComparisonOperatorsTests {

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_equals_then_returnsExpected(String description, Value a, Value b, Value expected) {
        val actual = ComparisonOperators.equals(a, b, null);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_equals_then_returnsExpected() {
        return Stream.of(
                // Same values
                arguments("same integers", Value.of(5), Value.of(5), Value.TRUE),
                arguments("same strings", Value.of("hello"), Value.of("hello"), Value.TRUE),
                arguments("same booleans", Value.TRUE, Value.TRUE, Value.TRUE),
                arguments("same null", Value.NULL, Value.NULL, Value.TRUE),
                arguments("same decimals", Value.of(2.5), Value.of(2.5), Value.TRUE),
                arguments("same empty array", Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.TRUE),
                arguments("same empty object", Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.TRUE),
                // Different values
                arguments("different integers", Value.of(5), Value.of(10), Value.FALSE),
                arguments("different strings", Value.of("hello"), Value.of("world"), Value.FALSE),
                arguments("different booleans", Value.TRUE, Value.FALSE, Value.FALSE),
                arguments("integer vs decimal", Value.of(5), Value.of(5.0), Value.TRUE), // should be equal
                // Different types
                arguments("integer vs string", Value.of(5), Value.of("5"), Value.FALSE),
                arguments("boolean vs string", Value.TRUE, Value.of("true"), Value.FALSE),
                arguments("null vs undefined", Value.NULL, Value.UNDEFINED, Value.FALSE));
    }

    @Test
    void when_equals_withArrays_then_comparesContent() {
        val arr1 = Value.ofArray(Value.of(1), Value.of(2), Value.of(3));
        val arr2 = Value.ofArray(Value.of(1), Value.of(2), Value.of(3));
        val arr3 = Value.ofArray(Value.of(1), Value.of(2));

        assertThat(ComparisonOperators.equals(arr1, arr2, null)).isEqualTo(Value.TRUE);
        assertThat(ComparisonOperators.equals(arr1, arr3, null)).isEqualTo(Value.FALSE);
    }

    @Test
    void when_equals_withObjects_then_comparesContent() {
        val obj1 = ObjectValue.builder().put("a", Value.of(1)).put("b", Value.of(2)).build();
        val obj2 = ObjectValue.builder().put("a", Value.of(1)).put("b", Value.of(2)).build();
        val obj3 = ObjectValue.builder().put("a", Value.of(1)).build();

        assertThat(ComparisonOperators.equals(obj1, obj2, null)).isEqualTo(Value.TRUE);
        assertThat(ComparisonOperators.equals(obj1, obj3, null)).isEqualTo(Value.FALSE);
    }

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_notEquals_then_returnsExpected(String description, Value a, Value b, Value expected) {
        val actual = ComparisonOperators.notEquals(a, b, null);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_notEquals_then_returnsExpected() {
        return Stream.of(
                // Same values - should return FALSE
                arguments("same integers", Value.of(5), Value.of(5), Value.FALSE),
                arguments("same strings", Value.of("hello"), Value.of("hello"), Value.FALSE),
                arguments("same booleans", Value.TRUE, Value.TRUE, Value.FALSE),
                // Different values - should return TRUE
                arguments("different integers", Value.of(5), Value.of(10), Value.TRUE),
                arguments("different strings", Value.of("hello"), Value.of("world"), Value.TRUE),
                arguments("different booleans", Value.TRUE, Value.FALSE, Value.TRUE),
                // Different types - should return TRUE
                arguments("integer vs string", Value.of(5), Value.of("5"), Value.TRUE),
                arguments("null vs undefined", Value.NULL, Value.UNDEFINED, Value.TRUE));
    }

    @Test
    void when_in_withArrayContainingValue_then_returnsTrue() {
        val array = Value.ofArray(Value.of(1), Value.of(2), Value.of(3));

        assertThat(ComparisonOperators.isContainedIn(Value.of(2), array, null)).isEqualTo(Value.TRUE);
    }

    @Test
    void when_in_withArrayNotContainingValue_then_returnsFalse() {
        val array = Value.ofArray(Value.of(1), Value.of(2), Value.of(3));

        assertThat(ComparisonOperators.isContainedIn(Value.of(5), array, null)).isEqualTo(Value.FALSE);
    }

    @Test
    void when_in_withEmptyArray_then_returnsFalse() {
        assertThat(ComparisonOperators.isContainedIn(Value.of(1), Value.EMPTY_ARRAY, null)).isEqualTo(Value.FALSE);
    }

    @Test
    void when_in_withArrayContainingDifferentTypes_then_matchesExactType() {
        val array = Value.ofArray(Value.of("1"), Value.of(2), Value.TRUE);

        assertThat(ComparisonOperators.isContainedIn(Value.of(1), array, null)).isEqualTo(Value.FALSE);  // "1" != 1
        assertThat(ComparisonOperators.isContainedIn(Value.of(2), array, null)).isEqualTo(Value.TRUE);
        assertThat(ComparisonOperators.isContainedIn(Value.of("1"), array, null)).isEqualTo(Value.TRUE);
        assertThat(ComparisonOperators.isContainedIn(Value.TRUE, array, null)).isEqualTo(Value.TRUE);
    }

    @Test
    void when_in_withObjectContainingValue_then_returnsTrue() {
        val obj = ObjectValue.builder().put("a", Value.of(1)).put("b", Value.of(2)).put("c", Value.of(3)).build();

        assertThat(ComparisonOperators.isContainedIn(Value.of(2), obj, null)).isEqualTo(Value.TRUE);
    }

    @Test
    void when_in_withObjectNotContainingValue_then_returnsFalse() {
        val obj = ObjectValue.builder().put("a", Value.of(1)).put("b", Value.of(2)).build();

        assertThat(ComparisonOperators.isContainedIn(Value.of(5), obj, null)).isEqualTo(Value.FALSE);
    }

    @Test
    void when_in_withObjectContainingKeyNotValue_then_returnsFalse() {
        // IN operator checks VALUES, not keys
        val obj = ObjectValue.builder().put("foo", Value.of("bar")).build();

        assertThat(ComparisonOperators.isContainedIn(Value.of("foo"), obj, null)).isEqualTo(Value.FALSE);
        assertThat(ComparisonOperators.isContainedIn(Value.of("bar"), obj, null)).isEqualTo(Value.TRUE);
    }

    @Test
    void when_in_withEmptyObject_then_returnsFalse() {
        assertThat(ComparisonOperators.isContainedIn(Value.of(1), Value.EMPTY_OBJECT, null)).isEqualTo(Value.FALSE);
    }

    @Test
    void when_in_withSubstringInString_then_returnsTrue() {
        assertThat(ComparisonOperators.isContainedIn(Value.of("ell"), Value.of("hello"), null)).isEqualTo(Value.TRUE);
        assertThat(ComparisonOperators.isContainedIn(Value.of("hello"), Value.of("hello"), null)).isEqualTo(Value.TRUE);
        assertThat(ComparisonOperators.isContainedIn(Value.of(""), Value.of("hello"), null)).isEqualTo(Value.TRUE);
    }

    @Test
    void when_in_withNoSubstringMatch_then_returnsFalse() {
        assertThat(ComparisonOperators.isContainedIn(Value.of("xyz"), Value.of("hello"), null)).isEqualTo(Value.FALSE);
    }

    @Test
    void when_in_withNonStringNeedle_inStringHaystack_then_returnsError() {
        val actual = ComparisonOperators.isContainedIn(Value.of(5), Value.of("hello"), null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("'in' operator supports");
    }

    @Test
    void when_in_withIncompatibleTypes_then_returnsError() {
        // Number cannot be searched in number
        val actual = ComparisonOperators.isContainedIn(Value.of(1), Value.of(5), null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("'in' operator supports");
    }

    @Test
    void when_in_withBooleanHaystack_then_returnsError() {
        val actual = ComparisonOperators.isContainedIn(Value.of(1), Value.TRUE, null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_in_withNullHaystack_then_returnsError() {
        val actual = ComparisonOperators.isContainedIn(Value.of(1), Value.NULL, null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_in_withUndefinedHaystack_then_returnsError() {
        val actual = ComparisonOperators.isContainedIn(Value.of(1), Value.UNDEFINED, null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_in_withNullNeedle_inArray_then_findsNull() {
        val array = Value.ofArray(Value.of(1), Value.NULL, Value.of(3));
        assertThat(ComparisonOperators.isContainedIn(Value.NULL, array, null)).isEqualTo(Value.TRUE);
    }

    @Test
    void when_in_withNestedArray_then_matchesWholeArray() {
        val nested = Value.ofArray(Value.of(1), Value.of(2));
        val outer  = Value.ofArray(nested, Value.of(3));

        assertThat(ComparisonOperators.isContainedIn(nested, outer, null)).isEqualTo(Value.TRUE);
        assertThat(ComparisonOperators.isContainedIn(Value.of(1), outer, null)).isEqualTo(Value.FALSE); // not directly
                                                                                                        // in outer
    }

}
