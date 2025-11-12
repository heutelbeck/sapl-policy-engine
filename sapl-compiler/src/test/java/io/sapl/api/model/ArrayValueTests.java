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
package io.sapl.api.model;

import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ArrayValue Tests")
class ArrayValueTests {
    @Test
    @DisplayName("Constructor with array creates immutable list")
    void constructorWithArray() {
        var values = new Value[] { Value.of(1), Value.of(2), Value.of(3) };
        var array  = new ArrayValue(values, false);

        assertThat(array).hasSize(3).containsExactly(Value.of(1), Value.of(2), Value.of(3));
    }

    @ParameterizedTest(name = "Constructor with null {0} throws NPE")
    @MethodSource("provideNullConstructorCases")
    @DisplayName("Constructor with null arguments throws NullPointerException")
    void constructorNullThrows(String description, Runnable constructor) {
        assertThatThrownBy(constructor::run).isInstanceOf(NullPointerException.class);
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("Empty builder returns EMPTY_ARRAY singleton")
        void emptyBuilderReturnsSingleton() {
            var result = ArrayValue.builder().build();

            assertThat(result).isSameAs(Value.EMPTY_ARRAY);
        }

        @Test
        @DisplayName("Empty secret builder creates new instance")
        void emptySecretBuilderCreatesNewInstance() {
            var result = ArrayValue.builder().secret().build();

            assertThat(result).isNotSameAs(Value.EMPTY_ARRAY).isEmpty();
            assertThat(result.secret()).isTrue();
        }

        @ParameterizedTest(name = "Builder {0}")
        @MethodSource("io.sapl.api.model.ArrayValueTests#provideBuilderCases")
        @DisplayName("Builder methods chain fluently")
        void builderMethodsChain(String description, Iterable<Value> expected) {
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
        @DisplayName("Builder secret() marks array as secret")
        void builderSecretMarksAsSecret() {
            var result = ArrayValue.builder().add(Value.of(1)).secret().build();

            assertThat(result.secret()).isTrue();
        }

        @Test
        @DisplayName("Builder cannot be reused after build()")
        void builderCannotBeReused() {
            var builder = ArrayValue.builder();
            var first   = builder.add(Value.of(1)).build();

            assertThat(first).hasSize(1);
            assertThatThrownBy(() -> builder.add(Value.of(2))).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder throws on add after build()")
        void builderThrowsOnAddAfterBuild() {
            var builder = ArrayValue.builder().add(Value.of("Necronomicon"));
            builder.build();

            assertThatThrownBy(() -> builder.add(Value.of("De Vermis Mysteriis")))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder throws on addAll(varargs) after build()")
        void builderThrowsOnAddAllVarargsAfterBuild() {
            var builder = ArrayValue.builder().add(Value.of("Cthulhu"));
            builder.build();

            assertThatThrownBy(() -> builder.addAll(Value.of("Yog-Sothoth"), Value.of("Nyarlathotep")))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder throws on addAll(collection) after build()")
        void builderThrowsOnAddAllCollectionAfterBuild() {
            var builder = ArrayValue.builder().add(Value.of("R'lyeh"));
            builder.build();

            var moreLocations = List.of(Value.of("Innsmouth"), Value.of("Arkham"));
            assertThatThrownBy(() -> builder.addAll(moreLocations)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder throws on secret after build()")
        void builderThrowsOnSecretAfterBuild() {
            var builder = ArrayValue.builder().add(Value.of("forbidden knowledge"));
            builder.build();

            assertThatThrownBy(builder::secret).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder throws on multiple build() calls")
        void builderThrowsOnMultipleBuildCalls() {
            var builder = ArrayValue.builder().add(Value.of("elder sign"));
            builder.build();

            assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder secret() before add() marks subsequent elements as secret")
        void secretBeforeAddMarksElementsSecret() {
            var grimoire = ArrayValue.builder().secret().add(Value.of("Ritual of Summoning"))
                    .add(Value.of("Rites of Protection")).build();

            assertThat(grimoire.secret()).isTrue();
            var element0 = grimoire.getFirst();
            assertThat(element0).isNotNull();
            assertThat(element0.secret()).isTrue();
            var element1 = grimoire.get(1);
            assertThat(element1).isNotNull();
            assertThat(element1.secret()).isTrue();
        }

        @Test
        @DisplayName("Builder secret() after add() marks existing elements as secret")
        void secretAfterAddMarksElementsSecret() {
            var cultists = ArrayValue.builder().add(Value.of("Wilbur Whateley")).add(Value.of("Lavinia Whateley"))
                    .secret().build();

            assertThat(cultists.secret()).isTrue();
            var element0 = cultists.getFirst();
            assertThat(element0).isNotNull();
            assertThat(element0.secret()).isTrue();
            var element1 = cultists.get(1);
            assertThat(element1).isNotNull();
            assertThat(element1.secret()).isTrue();
        }

        @Test
        @DisplayName("Builder addAll(varargs) with secret builder marks all elements as secret")
        void addAllVarargsWithSecretBuilderMarksSecret() {
            var elderSigns = ArrayValue.builder().secret()
                    .addAll(Value.of("Pentagram"), Value.of("Eye"), Value.of("Star")).build();

            assertThat(elderSigns.secret()).isTrue();
            elderSigns.forEach(element -> {
                assertThat(element).isNotNull();
                assertThat(element.secret()).isTrue();
            });
        }

        @Test
        @DisplayName("Builder addAll(collection) then secret() marks all elements as secret")
        void addAllCollectionThenSecretMarksSecret() {
            var tomes   = List.of(Value.of("Necronomicon"), Value.of("Pnakotic Manuscripts"),
                    Value.of("Book of Eibon"));
            var library = ArrayValue.builder().addAll(tomes).secret().build();

            assertThat(library.secret()).isTrue();
            library.forEach(element -> {
                assertThat(element).isNotNull();
                assertThat(element.secret()).isTrue();
            });
        }

        @Test
        @DisplayName("Builder secret() is idempotent")
        void secretIdempotency() {
            var incantations = ArrayValue.builder().add(Value.of("Ph'nglui mglw'nafh")).secret().secret()
                    .add(Value.of("Cthulhu R'lyeh")).secret().build();

            assertThat(incantations.secret()).isTrue();
            incantations.forEach(element -> {
                assertThat(element).isNotNull();
                assertThat(element.secret()).isTrue();
            });
        }

        @Test
        @DisplayName("Builder mixed operations maintain secret consistency")
        void mixedOperationsMaintainSecretConsistency() {
            var ritualItems = ArrayValue.builder().add(Value.of("candles")).secret()
                    .addAll(Value.of("incense"), Value.of("chalice")).add(Value.of("dagger")).build();

            assertThat(ritualItems.secret()).isTrue();
            assertThat(ritualItems).hasSize(4);
            ritualItems.forEach(element -> {
                assertThat(element).isNotNull();
                assertThat(element.secret()).isTrue();
            });
        }

        @Test
        @DisplayName("Builder with non-secret addAll preserves element states")
        void nonSecretAddAllPreservesStates() {
            var locations = List.of(Value.of("Arkham"), Value.of("Innsmouth"), Value.of("Dunwich"));
            var places    = ArrayValue.builder().addAll(locations).build();

            assertThat(places.secret()).isFalse();
            places.forEach(element -> {
                assertThat(element).isNotNull();
                assertThat(element.secret()).isFalse();
            });
        }
    }

    // ============================================================================
    // SECRET FLAG PROPAGATION
    // ============================================================================

    @Test
    @DisplayName("asSecret() on non-secret creates secret copy")
    void asSecretCreatesSecretCopy() {
        var original = new ArrayValue(List.of(Value.of(1)), false);
        var secret   = original.asSecret();

        assertThat(secret).isInstanceOf(ArrayValue.class);
        assertThat(secret.secret()).isTrue();
        assertThat(original.secret()).isFalse();
    }

    @Test
    @DisplayName("asSecret() on secret returns same instance")
    void asSecretOnSecretReturnsSame() {
        var original = new ArrayValue(List.of(Value.of(1)), true);

        assertThat(original.asSecret()).isSameAs(original);
    }

    @ParameterizedTest(name = "{0} propagates secret flag")
    @MethodSource("provideSecretPropagationMethods")
    @DisplayName("Secret flag propagates through all access methods")
    void secretFlagPropagates(String method, java.util.function.Function<ArrayValue, Stream<Value>> accessor) {
        var secretArray = new ArrayValue(List.of(Value.of(1), Value.of(2)), true);

        var values = accessor.apply(secretArray).toList();

        assertThat(values).isNotEmpty().allMatch(Value::secret);
    }

    @Test
    @DisplayName("ErrorValue from secret array operations inherit secret flag")
    void errorValuesInheritSecretFlag() {
        var secret = new ArrayValue(List.of(), true);

        var errorFromGet      = secret.get(0);
        var errorFromGetFirst = secret.getFirst();
        var errorFromGetLast  = secret.getLast();

        assertThat(errorFromGet).isInstanceOf(ErrorValue.class).matches(Value::secret);
        assertThat(errorFromGetFirst).isInstanceOf(ErrorValue.class).matches(Value::secret);
        assertThat(errorFromGetLast).isInstanceOf(ErrorValue.class).matches(Value::secret);
    }

    // ============================================================================
    // ERROR-AS-VALUE PATTERN
    // ============================================================================

    @ParameterizedTest(name = "{0} returns ErrorValue")
    @MethodSource("provideErrorAsValueCases")
    @DisplayName("Invalid operations return ErrorValue instead of throwing exceptions")
    void invalidOperationsReturnError(String operation, java.util.function.Function<ArrayValue, Value> accessor) {
        var array = new ArrayValue(List.of(Value.of(1)), false);
        var empty = new ArrayValue(List.of(), false);

        var result = accessor.apply(operation.contains("empty") ? empty : array);

        assertThat(result).isInstanceOf(ErrorValue.class);
        var error = (ErrorValue) result;
        assertThat(error.message()).isNotBlank();
    }

    // ============================================================================
    // IMMUTABILITY
    // ============================================================================

    @ParameterizedTest(name = "{0} throws UnsupportedOperationException")
    @MethodSource("provideMutationAttempts")
    @DisplayName("All mutation operations throw UnsupportedOperationException")
    void allMutationOperationsThrow(String operation, java.util.function.Consumer<ArrayValue> mutator) {
        var array = new ArrayValue(List.of(Value.of(1), Value.of(2)), false);

        assertThatThrownBy(() -> mutator.accept(array)).isInstanceOf(UnsupportedOperationException.class);
    }

    // ============================================================================
    // LIST SEMANTICS
    // ============================================================================

    @Test
    @DisplayName("List interface methods work correctly")
    void listInterfaceMethodsWork() {
        var array = new ArrayValue(List.of(Value.of(1), Value.of(2), Value.of(3)), false);

        assertThat(array).isNotEmpty().hasSize(3).contains(Value.of(2));
        assertThat(array.indexOf(Value.of(2))).isEqualTo(array.lastIndexOf(Value.of(2))).isEqualTo(1);
    }

    @Test
    @DisplayName("subList propagates secret flag")
    void subListPropagatesSecretFlag() {
        var secret = new ArrayValue(List.of(Value.of(1), Value.of(2), Value.of(3)), true);

        var subList = secret.subList(1, 3);

        assertThat(subList).isInstanceOf(ArrayValue.class);
        assertThat(((ArrayValue) subList).secret()).isTrue();
        assertThat(subList).containsExactly(Value.of(2), Value.of(3));
    }

    @Test
    @DisplayName("Iterators work correctly with secret propagation")
    void iteratorsWorkWithSecretPropagation() {
        var secret = new ArrayValue(List.of(Value.of(1), Value.of(2)), true);

        // Test iterator
        var iterator = secret.iterator();
        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next().secret()).isTrue();

        // Test listIterator
        var listIterator = secret.listIterator();
        assertThat(listIterator.hasNext()).isTrue();
        assertThat(listIterator.next().secret()).isTrue();

        // Test listIterator with index
        var listIteratorAtIndex = secret.listIterator(1);
        assertThat(listIteratorAtIndex.hasNext()).isTrue();
        assertThat(listIteratorAtIndex.next().secret()).isTrue();
    }

    // ============================================================================
    // EQUALITY AND TOSTRING
    // ============================================================================

    @Test
    @DisplayName("equals() compares by List equality")
    void equalsComparesByListEquality() {
        var array1    = new ArrayValue(List.of(Value.of(1), Value.of(2)), false);
        var array2    = new ArrayValue(List.of(Value.of(1), Value.of(2)), true);
        var array3    = new ArrayValue(List.of(Value.of(1), Value.of(3)), false);
        var plainList = List.of(Value.of(1), Value.of(2));

        assertThat(array1).isEqualTo(array2).isEqualTo(plainList).isNotEqualTo(array3).hasSameHashCodeAs(array2);
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("provideToStringCases")
    @DisplayName("toString() formats appropriately")
    void toStringFormatting(ArrayValue array, String expected, String description) {
        var result = array.toString();

        if (array.secret()) {
            assertThat(result).isEqualTo("***SECRET***");
        } else {
            assertThat(result).contains(expected);
        }
    }

    // ============================================================================
    // TEST DATA PROVIDERS
    // ============================================================================

    static Stream<Arguments> provideNullConstructorCases() {
        return Stream.of(Arguments.of("list", (Runnable) () -> new ArrayValue((List<Value>) null, false)),
                Arguments.of("array", (Runnable) () -> new ArrayValue((Value[]) null, false)));
    }

    static Stream<Arguments> provideBuilderCases() {
        var expected = List.of(Value.of(1), Value.of(2), Value.of(3));
        return Stream.of(Arguments.of("add()", expected), Arguments.of("addAll(varargs)", expected),
                Arguments.of("addAll(collection)", expected));
    }

    static Stream<Arguments> provideSecretPropagationMethods() {
        return Stream.of(
                Arguments.of("get()",
                        (java.util.function.Function<ArrayValue, Stream<Value>>) arr -> Stream.of(arr.getFirst())),
                Arguments.of("iterator()", (java.util.function.Function<ArrayValue, Stream<Value>>) arr -> {
                    var values = new ArrayList<Value>();
                    arr.iterator().forEachRemaining(values::add);
                    return values.stream();
                }),
                Arguments.of("stream()", (java.util.function.Function<ArrayValue, Stream<Value>>) ArrayValue::stream),
                Arguments.of("parallelStream()",
                        (java.util.function.Function<ArrayValue, Stream<Value>>) ArrayValue::parallelStream),
                Arguments.of("toArray()",
                        (java.util.function.Function<ArrayValue, Stream<Value>>) arr -> Arrays.stream(arr.toArray())
                                .map(o -> (Value) o)),
                Arguments.of("forEach()", (java.util.function.Function<ArrayValue, Stream<Value>>) arr -> {
                    val values = new ArrayList<Value>(arr);
                    return values.stream();
                }));
    }

    static Stream<Arguments> provideErrorAsValueCases() {
        return Stream.of(
                Arguments.of("get() out of bounds",
                        (java.util.function.Function<ArrayValue, Value>) arr -> arr.get(10)),
                Arguments.of("getFirst() on empty",
                        (java.util.function.Function<ArrayValue, Value>) ArrayValue::getFirst),
                Arguments.of("getLast() on empty",
                        (java.util.function.Function<ArrayValue, Value>) ArrayValue::getLast));
    }

    static Stream<Arguments> provideMutationAttempts() {
        return Stream.of(Arguments.of("iterator().remove()", (java.util.function.Consumer<ArrayValue>) arr -> {
            var iter = arr.iterator();
            iter.next();
            iter.remove();
        }), Arguments.of("listIterator().remove()", (java.util.function.Consumer<ArrayValue>) arr -> {
            var iter = arr.listIterator();
            iter.next();
            iter.remove();
        }), Arguments.of("listIterator().set()", (java.util.function.Consumer<ArrayValue>) arr -> {
            var iter = arr.listIterator();
            iter.next();
            iter.set(Value.of(99));
        }), Arguments.of("listIterator().add()", (java.util.function.Consumer<ArrayValue>) arr -> {
            var iter = arr.listIterator();
            iter.add(Value.of(99));
        }), Arguments.of("listIterator(1).remove()", (java.util.function.Consumer<ArrayValue>) arr -> {
            var iter = arr.listIterator(1);
            iter.next();
            iter.remove();
        }));
    }

    static Stream<Arguments> provideToStringCases() {
        return Stream
                .of(Arguments.of(new ArrayValue(List.of(), false), "[]", "empty array"),
                        Arguments.of(new ArrayValue(List.of(Value.of(1), Value.of(2)), false), "1, 2", "simple values"),
                        Arguments.of(new ArrayValue(List.of(Value.of(1)), true), "***SECRET***", "secret array"),
                        Arguments.of(new ArrayValue(
                                List.of(new ArrayValue(List.of(Value.of(1), Value.of(2)), false), Value.of(3)), false),
                                "[1, 2]", "nested arrays"),
                        Arguments.of(new ArrayValue(List.of(Value.of(1), Value.of("text"), Value.of(true), Value.NULL,
                                Value.UNDEFINED, Value.error("test")), false), "ERROR", "mixed types"));
    }
}
