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
package io.sapl.api.model;

import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("ArrayValue Tests")
class ArrayValueTests {

    @Test
    @DisplayName("Constructor with array creates immutable list")
    void when_constructedWithArray_then_createsImmutableList() {
        var values = new Value[] { Value.of(1), Value.of(2), Value.of(3) };
        var array  = new ArrayValue(values);

        assertThat(array).hasSize(3).containsExactly(Value.of(1), Value.of(2), Value.of(3));
    }

    @Test
    @DisplayName("Constructor with null throws NullPointerException")
    void when_constructedWithNull_then_throwsNullPointerException() {
        assertThatThrownBy(() -> new ArrayValue((Value[]) null)).isInstanceOf(NullPointerException.class);
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("Empty builder returns EMPTY_ARRAY singleton")
        void when_emptyBuilder_then_returnsEmptyArraySingleton() {
            var result = ArrayValue.builder().build();

            assertThat(result).isSameAs(Value.EMPTY_ARRAY);
        }

        @ParameterizedTest(name = "Builder {0}")
        @MethodSource("io.sapl.api.model.ArrayValueTests#provideBuilderCases")
        @DisplayName("Builder methods chain fluently")
        void when_builderMethodsCalled_then_chainFluently(String description, Iterable<Value> expected) {
            ArrayValue result;

            switch (description) {
            case "add()"              ->
                result = ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).add(Value.of(3)).build();
            case "addAll(varargs)"    ->
                result = ArrayValue.builder().addAll(Value.of(1), Value.of(2), Value.of(3)).build();
            case "addAll(collection)" ->
                result = ArrayValue.builder().addAll(List.of(Value.of(1), Value.of(2), Value.of(3))).build();
            default                   -> throw new IllegalArgumentException("Unknown case: " + description);
            }

            assertThat(result).containsExactlyElementsOf(expected);
        }

        @Test
        @DisplayName("Builder cannot be reused after build()")
        void when_builderReused_then_throwsIllegalStateException() {
            var builder = ArrayValue.builder();
            var first   = builder.add(Value.of(1)).build();

            assertThat(first).hasSize(1);
            val secondValue = Value.of(2);
            assertThatThrownBy(() -> builder.add(secondValue)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder throws on add after build()")
        void when_addCalledAfterBuild_then_throwsIllegalStateException() {
            var builder = ArrayValue.builder().add(Value.of("first"));
            builder.build();

            val additionalValue = Value.of("second");
            assertThatThrownBy(() -> builder.add(additionalValue)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder throws on addAll(varargs) after build()")
        void when_addAllVarargsCalledAfterBuild_then_throwsIllegalStateException() {
            var builder = ArrayValue.builder().add(Value.of("first"));
            builder.build();

            val value1 = Value.of("second");
            val value2 = Value.of("third");
            assertThatThrownBy(() -> builder.addAll(value1, value2)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder throws on addAll(collection) after build()")
        void when_addAllCollectionCalledAfterBuild_then_throwsIllegalStateException() {
            var builder = ArrayValue.builder().add(Value.of("first"));
            builder.build();

            var moreValues = List.of(Value.of("second"), Value.of("third"));
            assertThatThrownBy(() -> builder.addAll(moreValues)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder throws on multiple build() calls")
        void when_buildCalledMultipleTimes_then_throwsIllegalStateException() {
            var builder = ArrayValue.builder().add(Value.of("element"));
            builder.build();

            assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }
    }

    @Nested
    @DisplayName("Error-as-Value Pattern")
    class ErrorAsValueTests {

        @Test
        @DisplayName("get() out of bounds returns ErrorValue")
        void when_getOutOfBounds_then_returnsErrorValue() {
            var array  = ArrayValue.builder().add(Value.of(1)).build();
            var result = array.get(10);

            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        @Test
        @DisplayName("getFirst() on empty returns ErrorValue")
        void when_getFirstOnEmpty_then_returnsErrorValue() {
            var result = Value.EMPTY_ARRAY.getFirst();

            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        @Test
        @DisplayName("getLast() on empty returns ErrorValue")
        void when_getLastOnEmpty_then_returnsErrorValue() {
            var result = Value.EMPTY_ARRAY.getLast();

            assertThat(result).isInstanceOf(ErrorValue.class);
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("iterator().remove() throws UnsupportedOperationException")
        void when_iteratorRemove_then_throwsUnsupportedOperationException() {
            var array = ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).build();
            var iter  = array.iterator();
            iter.next();

            assertThatThrownBy(iter::remove).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("listIterator().set() throws UnsupportedOperationException")
        void when_listIteratorSet_then_throwsUnsupportedOperationException() {
            var array = ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).build();
            var iter  = array.listIterator();
            iter.next();

            var newValue = Value.of(99);
            assertThatThrownBy(() -> iter.set(newValue)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("listIterator().add() throws UnsupportedOperationException")
        void when_listIteratorAdd_then_throwsUnsupportedOperationException() {
            var array    = ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).build();
            var iter     = array.listIterator();
            var newValue = Value.of(99);

            assertThatThrownBy(() -> iter.add(newValue)).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("List Semantics")
    class ListSemanticsTests {

        @Test
        @DisplayName("List interface methods work correctly")
        void when_listInterfaceMethodsCalled_then_workCorrectly() {
            var array = ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).add(Value.of(3)).build();

            assertThat(array).isNotEmpty().hasSize(3).contains(Value.of(2));
            assertThat(array.indexOf(Value.of(2))).isEqualTo(array.lastIndexOf(Value.of(2))).isEqualTo(1);
        }

        @Test
        @DisplayName("subList returns ArrayValue")
        void when_subListCalled_then_returnsArrayValue() {
            var array = ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).add(Value.of(3)).build();

            var subList = array.subList(1, 3);

            assertThat(subList).isInstanceOf(ArrayValue.class).containsExactly(Value.of(2), Value.of(3));
        }

        @Test
        @DisplayName("Iterators work correctly")
        void when_iteratorsUsed_then_workCorrectly() {
            var array = ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).build();

            var iterator = array.iterator();
            assertThat(iterator.hasNext()).isTrue();
            assertThat(iterator.next()).isEqualTo(Value.of(1));

            var listIterator = array.listIterator();
            assertThat(listIterator.hasNext()).isTrue();
            assertThat(listIterator.next()).isEqualTo(Value.of(1));

            var listIteratorAtIndex = array.listIterator(1);
            assertThat(listIteratorAtIndex.hasNext()).isTrue();
            assertThat(listIteratorAtIndex.next()).isEqualTo(Value.of(2));
        }
    }

    @Nested
    @DisplayName("Equality and ToString")
    class EqualityAndToStringTests {

        @Test
        @DisplayName("equals() compares by List equality")
        void when_equalsCalled_then_comparesByListEquality() {
            var array1    = ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).build();
            var array2    = ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).build();
            var array3    = ArrayValue.builder().add(Value.of(1)).add(Value.of(3)).build();
            var plainList = List.of(Value.of(1), Value.of(2));

            assertThat(array1).isEqualTo(array2).isEqualTo(plainList).isNotEqualTo(array3).hasSameHashCodeAs(array2);
        }

        @Test
        @DisplayName("toString() for empty array shows []")
        void when_toStringOnEmpty_then_showsBrackets() {
            assertThat(Value.EMPTY_ARRAY).hasToString("[]");
        }

        @Test
        @DisplayName("toString() shows elements")
        void when_toString_then_showsElements() {
            var array = ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).build();

            assertThat(array.toString()).contains("1").contains("2");
        }

        @Test
        @DisplayName("toString() handles nested arrays")
        void when_toStringWithNestedArrays_then_showsNested() {
            var inner = ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).build();
            var outer = ArrayValue.builder().add(inner).add(Value.of(3)).build();

            var result = outer.toString();

            assertThat(result).contains("[1, 2]").contains("3");
        }

        @Test
        @DisplayName("toString() handles mixed types")
        void when_toStringWithMixedTypes_then_handlesThem() {
            var array = ArrayValue.builder().add(Value.of(1)).add(Value.of("text")).add(Value.of(true)).add(Value.NULL)
                    .add(Value.UNDEFINED).add(Value.error("test")).build();

            var result = array.toString();

            assertThat(result).contains("1").contains("text").contains("true").contains("null").contains("undefined")
                    .contains("ERROR");
        }
    }

    @Nested
    @DisplayName("Append")
    class AppendTests {

        @Test
        @DisplayName("append() combines two non-empty arrays")
        void when_appendTwoNonEmptyArrays_then_combinedInOrder() {
            var first  = ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).build();
            var second = ArrayValue.builder().add(Value.of(3)).add(Value.of(4)).build();

            var result = first.append(second);

            assertThat(result).containsExactly(Value.of(1), Value.of(2), Value.of(3), Value.of(4));
        }

        @Test
        @DisplayName("append() to empty array returns the other array unchanged")
        void when_appendToEmptyArray_then_returnsOtherArrayIdentity() {
            var other = ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).build();

            var result = Value.EMPTY_ARRAY.append(other);

            assertThat(result).isSameAs(other);
        }

        @Test
        @DisplayName("append() empty array returns this array unchanged")
        void when_appendEmptyArray_then_returnsThisArrayIdentity() {
            var first = ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).build();

            var result = first.append(Value.EMPTY_ARRAY);

            assertThat(result).isSameAs(first);
        }

        @Test
        @DisplayName("append() two empty arrays returns empty array")
        void when_appendTwoEmptyArrays_then_returnsEmptyArray() {
            var result = Value.EMPTY_ARRAY.append(Value.EMPTY_ARRAY);

            assertThat(result).isSameAs(Value.EMPTY_ARRAY);
        }

        @Test
        @DisplayName("append() preserves element order")
        void when_append_then_preservesElementOrder() {
            var first  = ArrayValue.builder().add(Value.of("a")).add(Value.of("b")).build();
            var second = ArrayValue.builder().add(Value.of("c")).add(Value.of("d")).build();

            var result = first.append(second);

            assertThat(result).containsExactly(Value.of("a"), Value.of("b"), Value.of("c"), Value.of("d"));
        }

        @Test
        @DisplayName("append() with single element arrays")
        void when_appendSingleElementArrays_then_combinesCorrectly() {
            var first  = ArrayValue.builder().add(Value.of(1)).build();
            var second = ArrayValue.builder().add(Value.of(2)).build();

            var result = first.append(second);

            assertThat(result).containsExactly(Value.of(1), Value.of(2));
        }
    }

    static Stream<Arguments> provideBuilderCases() {
        var expected = List.of(Value.of(1), Value.of(2), Value.of(3));
        return Stream.of(arguments("add()", expected), arguments("addAll(varargs)", expected),
                arguments("addAll(collection)", expected));
    }
}
