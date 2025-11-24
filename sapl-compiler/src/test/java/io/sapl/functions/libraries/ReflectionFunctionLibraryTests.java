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

import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ReflectionFunctionLibraryTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void isArrayTests(String description, Value input, boolean expected) {
        val actual = ReflectionFunctionLibrary.isArray(input);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> isArrayTests() {
        return Stream.of(
                arguments("Array with elements returns true", Value.ofArray(Value.of(1), Value.of(2), Value.of(3)),
                        true),
                arguments("Empty array returns true", Value.EMPTY_ARRAY, true),
                arguments("Empty object returns false", Value.EMPTY_OBJECT, false),
                arguments("Text returns false", Value.of("text"), false),
                arguments("Number returns false", Value.of(42), false),
                arguments("Boolean returns false", Value.TRUE, false),
                arguments("Null returns false", Value.NULL, false),
                arguments("Undefined returns false", Value.UNDEFINED, false),
                arguments("Error returns false", Value.error("test"), false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void isObjectTests(String description, Value input, boolean expected) {
        val actual = ReflectionFunctionLibrary.isObject(input);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> isObjectTests() {
        return Stream.of(
                arguments("Object with fields returns true", Value.ofObject(java.util.Map.of("key", Value.of("val"))),
                        true),
                arguments("Empty object returns true", Value.EMPTY_OBJECT, true),
                arguments("Empty array returns false", Value.EMPTY_ARRAY, false),
                arguments("Text returns false", Value.of("text"), false),
                arguments("Number returns false", Value.of(42), false),
                arguments("Null returns false", Value.NULL, false),
                arguments("Undefined returns false", Value.UNDEFINED, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void isTextTests(String description, Value input, boolean expected) {
        val actual = ReflectionFunctionLibrary.isText(input);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> isTextTests() {
        return Stream.of(arguments("Non-empty string returns true", Value.of("hello"), true),
                arguments("Empty string returns true", Value.of(""), true),
                arguments("Number returns false", Value.of(123), false),
                arguments("Boolean returns false", Value.TRUE, false),
                arguments("Array returns false", Value.EMPTY_ARRAY, false),
                arguments("Undefined returns false", Value.UNDEFINED, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void isNumberTests(String description, Value input, boolean expected) {
        val actual = ReflectionFunctionLibrary.isNumber(input);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> isNumberTests() {
        return Stream.of(arguments("Integer returns true", Value.of(15), true),
                arguments("Float 2.7 returns true", Value.of(2.7), true),
                arguments("Float 5.0 returns true", Value.of(5.0), true),
                arguments("Zero returns true", Value.of(0), true),
                arguments("Negative number returns true", Value.of(-10), true),
                arguments("String number returns false", Value.of("123"), false),
                arguments("Boolean returns false", Value.TRUE, false),
                arguments("Undefined returns false", Value.UNDEFINED, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void isIntegerTests(String description, Value input, boolean expected) {
        val actual = ReflectionFunctionLibrary.isInteger(input);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> isIntegerTests() {
        return Stream.of(arguments("Integer 7 returns true", Value.of(7), true),
                arguments("Integer -5 returns true", Value.of(-5), true),
                arguments("Zero returns true", Value.of(0), true),
                arguments("Float 1.0 returns true", Value.of(1.0), true),
                arguments("Float 2.00 returns true", Value.of(2.00), true),
                arguments("Float 2.7 returns false", Value.of(2.7), false),
                arguments("Float 1.5 returns false", Value.of(1.5), false),
                arguments("String returns false", Value.of("7"), false),
                arguments("Boolean returns false", Value.TRUE, false),
                arguments("Array returns false", Value.EMPTY_ARRAY, false),
                arguments("Null returns false", Value.NULL, false),
                arguments("Undefined returns false", Value.UNDEFINED, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void isBooleanTests(String description, Value input, boolean expected) {
        val actual = ReflectionFunctionLibrary.isBoolean(input);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> isBooleanTests() {
        return Stream.of(arguments("TRUE returns true", Value.TRUE, true),
                arguments("FALSE returns true", Value.FALSE, true),
                arguments("Number 1 returns false", Value.of(1), false),
                arguments("Number 0 returns false", Value.of(0), false),
                arguments("String 'true' returns false", Value.of("true"), false),
                arguments("Undefined returns false", Value.UNDEFINED, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void isNullTests(String description, Value input, boolean expected) {
        val actual = ReflectionFunctionLibrary.isNull(input);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> isNullTests() {
        return Stream.of(arguments("NULL returns true", Value.NULL, true),
                arguments("Undefined returns false", Value.UNDEFINED, false),
                arguments("Number 0 returns false", Value.of(0), false),
                arguments("Empty string returns false", Value.of(""), false),
                arguments("FALSE returns false", Value.FALSE, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void isUndefinedTests(String description, Value input, boolean expected) {
        val actual = ReflectionFunctionLibrary.isUndefined(input);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> isUndefinedTests() {
        return Stream.of(arguments("UNDEFINED returns true", Value.UNDEFINED, true),
                arguments("NULL returns false", Value.NULL, false),
                arguments("Number 0 returns false", Value.of(0), false),
                arguments("FALSE returns false", Value.FALSE, false),
                arguments("Error returns false", Value.error("test"), false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void isDefinedTests(String description, Value input, boolean expected) {
        val actual = ReflectionFunctionLibrary.isDefined(input);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> isDefinedTests() {
        return Stream.of(arguments("Number returns true", Value.of(42), true),
                arguments("NULL returns true", Value.NULL, true), arguments("FALSE returns true", Value.FALSE, true),
                arguments("Empty array returns true", Value.EMPTY_ARRAY, true),
                arguments("Empty object returns true", Value.EMPTY_OBJECT, true),
                arguments("Undefined returns false", Value.UNDEFINED, false),
                arguments("Error returns false", Value.error("test"), false));
    }

    @Test
    void isError_whenError_returnsTrue() {
        val errorVal = Value.error("test error");
        val actual   = ReflectionFunctionLibrary.isError(errorVal);
        assertThat(actual).isEqualTo(Value.TRUE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void isErrorTests(String description, Value input, boolean expected) {
        val actual = ReflectionFunctionLibrary.isError(input);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> isErrorTests() {
        return Stream.of(arguments("Number returns false", Value.of(42), false),
                arguments("Undefined returns false", Value.UNDEFINED, false),
                arguments("NULL returns false", Value.NULL, false),
                arguments("Array returns false", Value.EMPTY_ARRAY, false));
    }

    @Test
    void isSecret_whenSecret_returnsTrue() {
        val secretVal = Value.of("password").asSecret();
        val actual    = ReflectionFunctionLibrary.isSecret(secretVal);
        assertThat(actual).isEqualTo(Value.TRUE);
    }

    @Test
    void isSecret_whenNotSecret_returnsFalse() {
        val normalVal = Value.of("public data");
        val actual    = ReflectionFunctionLibrary.isSecret(normalVal);
        assertThat(actual).isEqualTo(Value.FALSE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void isEmptyTests(String description, Value input, boolean expected) {
        val actual = ReflectionFunctionLibrary.isEmpty(input);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> isEmptyTests() {
        return Stream.of(arguments("Empty array returns true", Value.EMPTY_ARRAY, true),
                arguments("Empty object returns true", Value.EMPTY_OBJECT, true),
                arguments("Array with elements returns false", Value.ofArray(Value.of(1), Value.of(2)), false),
                arguments("Object with fields returns false", Value.ofObject(java.util.Map.of("a", Value.of(1))),
                        false),
                arguments("NULL returns true", Value.NULL, true),
                arguments("Empty string returns true", Value.of(""), true),
                arguments("Zero returns true", Value.of(0), true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void typeOfTests(String description, Value input, String expectedType) {
        val actual = ReflectionFunctionLibrary.typeOf(input);
        assertThat(actual).isEqualTo(Value.of(expectedType));
    }

    private static Stream<Arguments> typeOfTests() {
        return Stream
                .of(arguments("Array returns ARRAY", Value.ofArray(Value.of(1), Value.of(2), Value.of(3)), "ARRAY"),
                        arguments("Object returns OBJECT", Value.ofObject(java.util.Map.of("key", Value.of("val"))),
                                "OBJECT"),
                        arguments("String returns STRING", Value.of("hello"), "STRING"),
                        arguments("Number returns NUMBER", Value.of(42), "NUMBER"),
                        arguments("Boolean returns BOOLEAN", Value.TRUE, "BOOLEAN"),
                        arguments("Null returns NULL", Value.NULL, "NULL"),
                        arguments("Undefined returns undefined", Value.UNDEFINED, "undefined"),
                        arguments("Error returns ERROR", Value.error("test"), "ERROR"));
    }

    @Test
    void typeOf_whenComplexNumber_returnsNumber() {
        val actual = ReflectionFunctionLibrary.typeOf(Value.of(3.5));
        assertThat(actual).isEqualTo(Value.of("NUMBER"));
    }
}
