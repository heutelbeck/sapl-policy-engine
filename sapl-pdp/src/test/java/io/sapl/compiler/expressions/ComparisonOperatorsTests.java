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
package io.sapl.compiler.expressions;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.compiler.operators.ComparisonOperators;
import lombok.val;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import org.junit.jupiter.api.DisplayName;

@DisplayName("ComparisonOperators")
class ComparisonOperatorsTests {

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void whenEqualsThenReturnsExpected(String description, Value a, Value b, Value expected) {
        val actual = ComparisonOperators.equals(a, b);
        assertThat(actual).isEqualTo(expected);
    }

    // @formatter:off
    private static Stream<Arguments> whenEqualsThenReturnsExpected() {
        // Complex values for composite tests
        val arr123 = Value.ofArray(Value.of(1), Value.of(2), Value.of(3));
        val arr12  = Value.ofArray(Value.of(1), Value.of(2));
        val objAB  = ObjectValue.builder().put("a", Value.of(1)).put("b", Value.of(2)).build();
        val objA   = ObjectValue.builder().put("a", Value.of(1)).build();

        return Stream.of(
            // Same values
            arguments("same integers", Value.of(5), Value.of(5), Value.TRUE),
            arguments("same strings", Value.of("hello"), Value.of("hello"), Value.TRUE),
            arguments("same booleans", Value.TRUE, Value.TRUE, Value.TRUE),
            arguments("same null", Value.NULL, Value.NULL, Value.TRUE),
            arguments("same decimals", Value.of(2.5), Value.of(2.5), Value.TRUE),
            arguments("same empty array", Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.TRUE),
            arguments("same empty object", Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.TRUE),
            // Arrays with content
            arguments("equal arrays", arr123, Value.ofArray(Value.of(1), Value.of(2), Value.of(3)), Value.TRUE),
            arguments("different length arrays", arr123, arr12, Value.FALSE),
            // Objects with content
            arguments("equal objects", objAB,
                ObjectValue.builder().put("a", Value.of(1)).put("b", Value.of(2)).build(), Value.TRUE),
            arguments("different objects", objAB, objA, Value.FALSE),
            // Different values
            arguments("different integers", Value.of(5), Value.of(10), Value.FALSE),
            arguments("different strings", Value.of("hello"), Value.of("world"), Value.FALSE),
            arguments("different booleans", Value.TRUE, Value.FALSE, Value.FALSE),
            arguments("integer vs decimal", Value.of(5), Value.of(5.0), Value.TRUE),
            // Different types
            arguments("integer vs string", Value.of(5), Value.of("5"), Value.FALSE),
            arguments("boolean vs string", Value.TRUE, Value.of("true"), Value.FALSE),
            arguments("null vs undefined", Value.NULL, Value.UNDEFINED, Value.FALSE));
    }
    // @formatter:on

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void whenNotEqualsThenReturnsExpected(String description, Value a, Value b, Value expected) {
        val actual = ComparisonOperators.notEquals(a, b);
        assertThat(actual).isEqualTo(expected);
    }

    // @formatter:off
    private static Stream<Arguments> whenNotEqualsThenReturnsExpected() {
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
    // @formatter:on

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void whenIsContainedInThenReturnsExpected(String description, Value needle, Value haystack, Value expected) {
        val actual = ComparisonOperators.isContainedIn(needle, haystack, null);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> whenIsContainedInThenReturnsExpected() {
        // Reusable test data
        val array123        = Value.ofArray(Value.of(1), Value.of(2), Value.of(3));
        val mixedArray      = Value.ofArray(Value.of("1"), Value.of(2), Value.TRUE);
        val objABC          = ObjectValue.builder().put("a", Value.of(1)).put("b", Value.of(2)).put("c", Value.of(3))
                .build();
        val objAB           = ObjectValue.builder().put("a", Value.of(1)).put("b", Value.of(2)).build();
        val objFooBar       = ObjectValue.builder().put("foo", Value.of("bar")).build();
        val arrayWithNull   = Value.ofArray(Value.of(1), Value.NULL, Value.of(3));
        val nested          = Value.ofArray(Value.of(1), Value.of(2));
        val outerWithNested = Value.ofArray(nested, Value.of(3));

        return Stream.of(
                // Array containment
                arguments("value in array", Value.of(2), array123, Value.TRUE),
                arguments("value not in array", Value.of(5), array123, Value.FALSE),
                arguments("value in empty array", Value.of(1), Value.EMPTY_ARRAY, Value.FALSE),
                arguments("integer 1 not in mixed array (string '1' exists)", Value.of(1), mixedArray, Value.FALSE),
                arguments("integer 2 in mixed array", Value.of(2), mixedArray, Value.TRUE),
                arguments("string '1' in mixed array", Value.of("1"), mixedArray, Value.TRUE),
                arguments("boolean true in mixed array", Value.TRUE, mixedArray, Value.TRUE),
                arguments("null in array containing null", Value.NULL, arrayWithNull, Value.TRUE),
                arguments("nested array in outer array", nested, outerWithNested, Value.TRUE),
                arguments("element not directly in outer (nested)", Value.of(1), outerWithNested, Value.FALSE),
                // Object containment (checks VALUES, not keys)
                arguments("value in object", Value.of(2), objABC, Value.TRUE),
                arguments("value not in object", Value.of(5), objAB, Value.FALSE),
                arguments("key as needle (not value) in object", Value.of("foo"), objFooBar, Value.FALSE),
                arguments("value 'bar' in object", Value.of("bar"), objFooBar, Value.TRUE),
                arguments("value in empty object", Value.of(1), Value.EMPTY_OBJECT, Value.FALSE),
                // String containment (substring)
                arguments("substring in string", Value.of("ell"), Value.of("hello"), Value.TRUE),
                arguments("whole string in string", Value.of("hello"), Value.of("hello"), Value.TRUE),
                arguments("empty string in string", Value.of(""), Value.of("hello"), Value.TRUE),
                arguments("no substring match", Value.of("xyz"), Value.of("hello"), Value.FALSE));
    }

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void whenIsContainedInWithInvalidTypesThenReturnsError(String description, Value needle, Value haystack) {
        val actual = ComparisonOperators.isContainedIn(needle, haystack, null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    private static Stream<Arguments> whenIsContainedInWithInvalidTypesThenReturnsError() {
        return Stream.of(arguments("non-string needle in string haystack", Value.of(5), Value.of("hello")),
                arguments("number in number (incompatible)", Value.of(1), Value.of(5)),
                arguments("value in boolean haystack", Value.of(1), Value.TRUE),
                arguments("value in null haystack", Value.of(1), Value.NULL),
                arguments("value in undefined haystack", Value.of(1), Value.UNDEFINED));
    }

}
