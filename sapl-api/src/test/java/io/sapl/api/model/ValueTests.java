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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Value Factory Tests")
class ValueTests {

    @Nested
    @DisplayName("Boolean Factory")
    class BooleanFactoryTests {

        @Test
        @DisplayName("of(true) returns TRUE singleton")
        void when_ofTrue_then_returnsSingleton() {
            var result = Value.of(true);

            assertThat(result).isSameAs(Value.TRUE).isInstanceOf(BooleanValue.class);
        }

        @Test
        @DisplayName("of(false) returns FALSE singleton")
        void when_ofFalse_then_returnsSingleton() {
            var result = Value.of(false);

            assertThat(result).isSameAs(Value.FALSE).isInstanceOf(BooleanValue.class);
        }

        @Test
        @DisplayName("Multiple calls return same singleton")
        void when_multipleCalls_then_returnSameSingleton() {
            assertThat(Value.of(true)).isSameAs(Value.of(true));
            assertThat(Value.of(false)).isSameAs(Value.of(false));
        }
    }

    @Nested
    @DisplayName("Long Factory")
    class LongFactoryTests {

        @Test
        @DisplayName("of(0L) returns ZERO singleton")
        void when_ofZero_then_returnsSingleton() {
            var result = Value.of(0L);

            assertThat(result).isSameAs(Value.ZERO);
        }

        @Test
        @DisplayName("of(1L) returns ONE singleton")
        void when_ofOne_then_returnsSingleton() {
            var result = Value.of(1L);

            assertThat(result).isSameAs(Value.ONE);
        }

        @Test
        @DisplayName("of(10L) returns TEN singleton")
        void when_ofTen_then_returnsSingleton() {
            var result = Value.of(10L);

            assertThat(result).isSameAs(Value.TEN);
        }

        @ParameterizedTest
        @ValueSource(longs = { 2L, 5L, 42L, 100L, 1000L, -1L, -10L, -100L })
        @DisplayName("of(other values) creates new NumberValue")
        void when_ofOtherValues_then_createsNumberValue(long value) {
            var result = Value.of(value);

            assertThat(result).isInstanceOf(NumberValue.class);
            assertThat(((NumberValue) result).value()).isEqualByComparingTo(BigDecimal.valueOf(value));
        }

        @Test
        @DisplayName("of(Long.MAX_VALUE) does not overflow")
        void when_ofMaxValue_then_doesNotOverflow() {
            var result = Value.of(Long.MAX_VALUE);

            assertThat(result).isInstanceOf(NumberValue.class);
            assertThat(((NumberValue) result).value()).isEqualByComparingTo(BigDecimal.valueOf(Long.MAX_VALUE));
        }

        @Test
        @DisplayName("of(Long.MIN_VALUE) does not overflow")
        void when_ofMinValue_then_doesNotOverflow() {
            var result = Value.of(Long.MIN_VALUE);

            assertThat(result).isInstanceOf(NumberValue.class);
            assertThat(((NumberValue) result).value()).isEqualByComparingTo(BigDecimal.valueOf(Long.MIN_VALUE));
        }

        @Test
        @DisplayName("of(4294967296L) does not truncate to 0")
        void when_ofLargeValue_then_doesNotTruncate() {
            long value  = 4_294_967_296L; // 2^32, would truncate to 0 with (int) cast
            var  result = Value.of(value);

            assertThat(result).isNotSameAs(Value.ZERO);
            assertThat(((NumberValue) result).value()).isEqualByComparingTo(new BigDecimal("4294967296"));
        }

        @Test
        @DisplayName("of(4294967297L) does not truncate to 1")
        void when_ofLargeValuePlusOne_then_doesNotTruncate() {
            long value  = 4_294_967_297L; // 2^32 + 1, would truncate to 1 with (int) cast
            var  result = Value.of(value);

            assertThat(result).isNotSameAs(Value.ONE);
            assertThat(((NumberValue) result).value()).isEqualByComparingTo(new BigDecimal("4294967297"));
        }
    }

    @Nested
    @DisplayName("Double Factory")
    class DoubleFactoryTests {

        @ParameterizedTest
        @ValueSource(doubles = { 0.0, -0.0 })
        @DisplayName("of(0.0 variants) returns ZERO singleton")
        void when_ofZero_then_returnsSingleton(double value) {
            var result = Value.of(value);

            assertThat(result).isSameAs(Value.ZERO);
        }

        @Test
        @DisplayName("of(1.0) returns ONE singleton")
        void when_ofOne_then_returnsSingleton() {
            var result = Value.of(1.0);

            assertThat(result).isSameAs(Value.ONE);
        }

        @Test
        @DisplayName("of(10.0) returns TEN singleton")
        void when_ofTen_then_returnsSingleton() {
            var result = Value.of(10.0);

            assertThat(result).isSameAs(Value.TEN);
        }

        @ParameterizedTest
        @ValueSource(doubles = { 1.5, 2.5, 3.7, -1.0, 0.001, 1e10, 1e-10 })
        @DisplayName("of(other values) creates new NumberValue")
        void when_ofOtherValues_then_createsNumberValue(double value) {
            var result = Value.of(value);

            assertThat(result).isInstanceOf(NumberValue.class);
            assertThat(((NumberValue) result).value()).isEqualByComparingTo(BigDecimal.valueOf(value));
        }

        @Test
        @DisplayName("of(NaN) throws IllegalArgumentException")
        void when_ofNaN_then_throws() {
            assertThatThrownBy(() -> Value.of(Double.NaN)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot create Value from NaN").hasMessageContaining("Use Value.error()");
        }

        @Test
        @DisplayName("of(POSITIVE_INFINITY) throws IllegalArgumentException")
        void when_ofPositiveInfinity_then_throws() {
            assertThatThrownBy(() -> Value.of(Double.POSITIVE_INFINITY)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot create Value from infinite double")
                    .hasMessageContaining("Use Value.error()");
        }

        @Test
        @DisplayName("of(NEGATIVE_INFINITY) throws IllegalArgumentException")
        void when_ofNegativeInfinity_then_throws() {
            assertThatThrownBy(() -> Value.of(Double.NEGATIVE_INFINITY)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot create Value from infinite double")
                    .hasMessageContaining("Use Value.error()");
        }
    }

    @Nested
    @DisplayName("BigDecimal Factory")
    class BigDecimalFactoryTests {

        @Test
        @DisplayName("of(BigDecimal.ZERO) returns ZERO singleton")
        void when_ofZero_then_returnsSingleton() {
            var result = Value.of(BigDecimal.ZERO);

            assertThat(result).isSameAs(Value.ZERO);
        }

        @Test
        @DisplayName("of(BigDecimal.ONE) returns ONE singleton")
        void when_ofOne_then_returnsSingleton() {
            var result = Value.of(BigDecimal.ONE);

            assertThat(result).isSameAs(Value.ONE);
        }

        @Test
        @DisplayName("of(BigDecimal.TEN) returns TEN singleton")
        void when_ofTen_then_returnsSingleton() {
            var result = Value.of(BigDecimal.TEN);

            assertThat(result).isSameAs(Value.TEN);
        }

        @Test
        @DisplayName("of(new BigDecimal(0)) returns ZERO singleton")
        void when_ofNewZero_then_returnsSingleton() {
            var result = Value.of(new BigDecimal("0"));

            assertThat(result).isSameAs(Value.ZERO);
        }

        @Test
        @DisplayName("of(new BigDecimal(0.00)) returns ZERO singleton")
        void when_ofZeroWithScale_then_returnsSingleton() {
            var result = Value.of(new BigDecimal("0.00"));

            assertThat(result).isSameAs(Value.ZERO);
        }

        @ParameterizedTest
        @MethodSource("provideBigDecimalValues")
        @DisplayName("of(other values) creates new NumberValue")
        void when_ofOtherValues_then_createsNumberValue(BigDecimal value) {
            var result = Value.of(value);

            assertThat(result).isInstanceOf(NumberValue.class);
            assertThat(((NumberValue) result).value()).isEqualByComparingTo(value);
        }

        static Stream<BigDecimal> provideBigDecimalValues() {
            return Stream.of(new BigDecimal("2.5"), new BigDecimal("3.7"), new BigDecimal("-1.5"),
                    new BigDecimal("1000000000000"), new BigDecimal("0.000000001"));
        }

        @Test
        @DisplayName("of(null) throws NullPointerException")
        void when_ofNull_then_throws() {
            assertThatThrownBy(() -> Value.of((BigDecimal) null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("String Factory")
    class StringFactoryTests {

        @Test
        @DisplayName("of(empty string) returns EMPTY_TEXT singleton")
        void when_ofEmptyString_then_returnsSingleton() {
            var result = Value.of("");

            assertThat(result).isSameAs(Value.EMPTY_TEXT);
        }

        @ParameterizedTest
        @ValueSource(strings = { "hello", "world", "test", " ", "  ", "\t", "\n" })
        @DisplayName("of(non-empty strings) creates new TextValue")
        void when_ofNonEmptyStrings_then_createsTextValue(String value) {
            var result = Value.of(value);

            assertThat(result).isInstanceOf(TextValue.class);
            assertThat(((TextValue) result).value()).isEqualTo(value);
        }

        @Test
        @DisplayName("of(null) throws NullPointerException")
        void when_ofNull_then_throws() {
            assertThatThrownBy(() -> Value.of((String) null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Array Factory")
    class ArrayFactoryTests {

        @Test
        @DisplayName("ofArray() with no args returns EMPTY_ARRAY singleton")
        void when_ofArrayNoArgs_then_returnsSingleton() {
            var result = Value.ofArray();

            assertThat(result).isSameAs(Value.EMPTY_ARRAY);
        }

        @Test
        @DisplayName("ofArray(empty list) returns EMPTY_ARRAY singleton")
        void when_ofArrayEmptyList_then_returnsSingleton() {
            var result = Value.ofArray(List.of());

            assertThat(result).isSameAs(Value.EMPTY_ARRAY);
        }

        @Test
        @DisplayName("ofArray(varargs) creates ArrayValue")
        void when_ofArrayVarargs_then_createsArrayValue() {
            var result = Value.ofArray(Value.of(1), Value.of(2), Value.of(3));

            assertThat(result).isInstanceOf(ArrayValue.class);
            var array = (ArrayValue) result;
            assertThat(array).hasSize(3);
            assertThat(array.get(0)).isEqualTo(Value.of(1));
            assertThat(array.get(1)).isEqualTo(Value.of(2));
            assertThat(array.get(2)).isEqualTo(Value.of(3));
        }

        @Test
        @DisplayName("ofArray(list) creates ArrayValue")
        void when_ofArrayList_then_createsArrayValue() {
            var list   = List.<Value>of(Value.of("a"), Value.of("b"));
            var result = Value.ofArray(list);

            assertThat(result).isInstanceOf(ArrayValue.class);
            var array = (ArrayValue) result;
            assertThat(array).hasSize(2);
            assertThat(array.get(0)).isEqualTo(Value.of("a"));
            assertThat(array.get(1)).isEqualTo(Value.of("b"));
        }

        @Test
        @DisplayName("ofArray(null list) throws NullPointerException")
        void when_ofArrayNullList_then_throws() {
            assertThatThrownBy(() -> Value.ofArray((List<Value>) null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Object Factory")
    class ObjectFactoryTests {

        @Test
        @DisplayName("ofObject(empty map) returns EMPTY_OBJECT singleton")
        void when_ofObjectEmptyMap_then_returnsSingleton() {
            var result = Value.ofObject(Map.of());

            assertThat(result).isSameAs(Value.EMPTY_OBJECT);
        }

        @Test
        @DisplayName("ofObject(map) creates ObjectValue")
        void when_ofObjectMap_then_createsObjectValue() {
            var map    = Map.<String, Value>of("name", Value.of("Alice"), "age", Value.of(30));
            var result = Value.ofObject(map);

            assertThat(result).isInstanceOf(ObjectValue.class);
            var obj = (ObjectValue) result;
            assertThat(obj).hasSize(2).containsEntry("name", Value.of("Alice")).containsEntry("age", Value.of(30));
        }

        @Test
        @DisplayName("ofObject(null) throws NullPointerException")
        void when_ofObjectNull_then_throws() {
            assertThatThrownBy(() -> Value.ofObject(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Error Factory")
    class ErrorFactoryTests {

        @Test
        @DisplayName("error(message) creates ErrorValue")
        void when_errorWithMessage_then_createsErrorValue() {
            var result = Value.error("Test error");

            assertThat(result).isInstanceOf(ErrorValue.class);
            var error = (ErrorValue) result;
            assertThat(error.message()).isEqualTo("Test error");
            assertThat(error.cause()).isNull();
        }

        @Test
        @DisplayName("error(message, cause) creates ErrorValue")
        void when_errorWithMessageAndCause_then_createsErrorValue() {
            var cause  = new RuntimeException("Cause");
            var result = Value.error("Test error", cause);

            assertThat(result).isInstanceOf(ErrorValue.class);
            var error = (ErrorValue) result;
            assertThat(error.message()).isEqualTo("Test error");
            assertThat(error.cause()).isSameAs(cause);
        }

        @Test
        @DisplayName("error(cause) creates ErrorValue with cause message")
        void when_errorWithCause_then_createsErrorValue() {
            var cause  = new RuntimeException("Cause message");
            var result = Value.error(cause);

            assertThat(result).isInstanceOf(ErrorValue.class);
            var error = (ErrorValue) result;
            assertThat(error.message()).isEqualTo("Cause message");
            assertThat(error.cause()).isSameAs(cause);
        }

        @Test
        @DisplayName("error(null message) throws NullPointerException")
        void when_errorNullMessage_then_throws() {
            assertThatThrownBy(() -> Value.error((String) null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("error(null cause) throws NullPointerException")
        void when_errorNullCause_then_throws() {
            assertThatThrownBy(() -> Value.error((Throwable) null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Constants")
    class ConstantsTests {

        @Test
        @DisplayName("TRUE is BooleanValue(true)")
        void when_trueConstant_then_isBooleanValue() {
            assertThat(Value.TRUE).isInstanceOf(BooleanValue.class);
            assertThat(((BooleanValue) Value.TRUE).value()).isTrue();
        }

        @Test
        @DisplayName("FALSE is BooleanValue(false)")
        void when_falseConstant_then_isBooleanValue() {
            assertThat(Value.FALSE).isInstanceOf(BooleanValue.class);
            assertThat(((BooleanValue) Value.FALSE).value()).isFalse();
        }

        @Test
        @DisplayName("NULL is NullValue")
        void when_nullConstant_then_isNullValue() {
            assertThat(Value.NULL).isInstanceOf(NullValue.class);
        }

        @Test
        @DisplayName("UNDEFINED is UndefinedValue")
        void when_undefinedConstant_then_isUndefinedValue() {
            assertThat(Value.UNDEFINED).isInstanceOf(UndefinedValue.class);
        }

        @Test
        @DisplayName("ZERO is NumberValue(0)")
        void when_zeroConstant_then_isNumberValue() {
            assertThat(Value.ZERO).isInstanceOf(NumberValue.class);
            assertThat(((NumberValue) Value.ZERO).value()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("ONE is NumberValue(1)")
        void when_oneConstant_then_isNumberValue() {
            assertThat(Value.ONE).isInstanceOf(NumberValue.class);
            assertThat(((NumberValue) Value.ONE).value()).isEqualByComparingTo(BigDecimal.ONE);
        }

        @Test
        @DisplayName("TEN is NumberValue(10)")
        void when_tenConstant_then_isNumberValue() {
            assertThat(Value.TEN).isInstanceOf(NumberValue.class);
            assertThat(((NumberValue) Value.TEN).value()).isEqualByComparingTo(BigDecimal.TEN);
        }

        @Test
        @DisplayName("EMPTY_ARRAY is empty ArrayValue")
        void when_emptyArrayConstant_then_isEmptyArrayValue() {
            assertThat(Value.EMPTY_ARRAY).isInstanceOf(ArrayValue.class).isEmpty();
        }

        @Test
        @DisplayName("EMPTY_OBJECT is empty ObjectValue")
        void when_emptyObjectConstant_then_isEmptyObjectValue() {
            assertThat(Value.EMPTY_OBJECT).isInstanceOf(ObjectValue.class).isEmpty();
        }

        @Test
        @DisplayName("EMPTY_TEXT is empty TextValue")
        void when_emptyTextConstant_then_isEmptyTextValue() {
            assertThat(Value.EMPTY_TEXT).isInstanceOf(TextValue.class);
            assertThat(((TextValue) Value.EMPTY_TEXT).value()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Pattern Matching Examples")
    class PatternMatchingTests {

        @Test
        @DisplayName("Pattern matching for policy decision")
        void when_patternMatchingPolicyDecision_then_returnsCorrectDecision() {
            Value policyResult = Value.of(true);

            var decision = switch (policyResult) {
            case BooleanValue(boolean allowed) -> allowed ? "PERMIT" : "DENY";
            case ErrorValue ignore             -> "INDETERMINATE";
            case UndefinedValue ignore         -> "NOT_APPLICABLE";
            default                            -> "INDETERMINATE";
            };

            assertThat(decision).isEqualTo("PERMIT");
        }

        @Test
        @DisplayName("Pattern matching with guards for access control")
        void when_patternMatchingWithGuards_then_returnsCorrectAccess() {
            var clearanceLevel = Value.of(7);

            var access = switch (clearanceLevel) {
            case NumberValue(BigDecimal level) when level.compareTo(BigDecimal.valueOf(10)) >= 0 -> "TOP SECRET";
            case NumberValue(BigDecimal level) when level.compareTo(BigDecimal.valueOf(5)) >= 0  -> "SECRET";
            case NumberValue(BigDecimal level) when level.compareTo(BigDecimal.ZERO) > 0         -> "CONFIDENTIAL";
            default                                                                              -> "PUBLIC";
            };

            assertThat(access).isEqualTo("SECRET");
        }

        @Test
        @DisplayName("Pattern matching for attribute extraction")
        void when_patternMatchingAttributeExtraction_then_extractsCorrectly() {
            Value user = Value.ofObject(Map.of("name", Value.of("alice"), "role", Value.of("admin")));

            boolean isAdmin = switch (user) {
            case ObjectValue obj -> {
                var role = obj.get("role");
                yield role instanceof TextValue(String r) && "admin".equals(r);
            }
            default              -> false;
            };

            assertThat(isAdmin).isTrue();
        }
    }

    @Nested
    @DisplayName("Factory Edge Cases")
    class FactoryEdgeCasesTests {

        @Test
        @DisplayName("ofArray with varargs consistency")
        void when_ofArrayVarargs_then_consistentEquality() {
            var v1 = Value.ofArray(Value.of(1), Value.of(2));
            var v2 = Value.ofArray(Value.of(1), Value.of(2));

            assertThat(v1).isEqualTo(v2);
        }

        @Test
        @DisplayName("ofObject creates defensive copy")
        void when_ofObject_then_createsDefensiveCopy() {
            var mutableMap = new HashMap<String, Value>();
            mutableMap.put("key", Value.of(1));

            var obj = Value.ofObject(mutableMap);
            mutableMap.put("key2", Value.of(2));

            assertThat(((ObjectValue) obj)).hasSize(1);
        }

        @Test
        @DisplayName("Factory methods return appropriate types")
        void when_factoryMethods_then_returnAppropriateTypes() {
            assertThat(Value.of(true)).isInstanceOf(BooleanValue.class);
            assertThat(Value.of(1L)).isInstanceOf(NumberValue.class);
            assertThat(Value.of(1.5)).isInstanceOf(NumberValue.class);
            assertThat(Value.of(BigDecimal.ONE)).isInstanceOf(NumberValue.class);
            assertThat(Value.of("text")).isInstanceOf(TextValue.class);
            assertThat(Value.ofArray()).isInstanceOf(ArrayValue.class);
            assertThat(Value.ofObject(Map.of())).isInstanceOf(ObjectValue.class);
            assertThat(Value.error("error")).isInstanceOf(ErrorValue.class);
        }

        @Test
        @DisplayName("Singleton constants are consistent")
        void when_singletonConstants_then_consistent() {
            assertThat(Value.of(true)).isSameAs(Value.TRUE);
            assertThat(Value.of(false)).isSameAs(Value.FALSE);
            assertThat(Value.of(0L)).isSameAs(Value.ZERO);
            assertThat(Value.of(1L)).isSameAs(Value.ONE);
            assertThat(Value.of(10L)).isSameAs(Value.TEN);
            assertThat(Value.of("")).isSameAs(Value.EMPTY_TEXT);
            assertThat(Value.ofArray()).isSameAs(Value.EMPTY_ARRAY);
            assertThat(Value.ofObject(Map.of())).isSameAs(Value.EMPTY_OBJECT);
        }

        @Test
        @DisplayName("Double zero variants return same singleton")
        void when_doubleZeroVariants_then_returnSameSingleton() {
            assertThat(Value.of(0.0)).isSameAs(Value.ZERO);
            assertThat(Value.of(-0.0)).isSameAs(Value.ZERO);
        }

        @Test
        @DisplayName("BigDecimal with different scales but same value are equal")
        void when_bigDecimalDifferentScales_then_equal() {
            var v1 = Value.of(new BigDecimal("1.0"));
            var v2 = Value.of(new BigDecimal("1.00"));

            assertThat(v1).isEqualTo(v2);
        }

        @Test
        @DisplayName("Long values beyond int range")
        void when_longValuesBeyondIntRange_then_handledCorrectly() {
            long large = 10_000_000_000L; // Beyond int range
            var  value = Value.of(large);

            assertThat(value).isInstanceOf(NumberValue.class);
            var numValue = (NumberValue) value;
            assertThat(numValue.value()).isEqualByComparingTo(BigDecimal.valueOf(large));
        }

        @Test
        @DisplayName("Array with mixed value types")
        void when_arrayWithMixedTypes_then_createsCorrectly() {
            var array = Value.ofArray(Value.of(1), Value.of("text"), Value.of(true), Value.NULL, Value.UNDEFINED);

            assertThat(array).isInstanceOf(ArrayValue.class);
            assertThat((ArrayValue) array).hasSize(5);
        }

        @Test
        @DisplayName("Object with all value types")
        void when_objectWithAllTypes_then_createsCorrectly() {
            var obj = Value.ofObject(Map.of("number", Value.of(1), "text", Value.of("hello"), "bool", Value.of(true),
                    "null", Value.NULL));

            assertThat(obj).isInstanceOf(ObjectValue.class);
            assertThat((ObjectValue) obj).hasSize(4);
        }

        @Test
        @DisplayName("Error factory with null cause is allowed")
        void when_errorFactoryNullCause_then_allowed() {
            var error = Value.error("message", (Throwable) null);

            assertThat(error).isInstanceOf(ErrorValue.class);
            var errorValue = (ErrorValue) error;
            assertThat(errorValue.message()).isEqualTo("message");
            assertThat(errorValue.cause()).isNull();
        }
    }

}
