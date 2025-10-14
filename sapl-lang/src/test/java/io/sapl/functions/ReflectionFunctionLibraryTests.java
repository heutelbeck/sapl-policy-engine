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

class ReflectionFunctionLibraryTests {

    private static Val json(String json) throws JsonProcessingException {
        return Val.ofJson(json);
    }

    @ParameterizedTest
    @MethodSource("provideIsArrayTestCases")
    void when_isArray_then_returnsCorrectResult(Val input, boolean expected) {
        val actual = ReflectionFunctionLibrary.isArray(input);
        assertThatVal(actual).isEqualTo(Val.of(expected));
    }

    private static Stream<Arguments> provideIsArrayTestCases() throws JsonProcessingException {
        return Stream.of(Arguments.of(json("[1, 2, 3]"), true), Arguments.of(json("[]"), true),
                Arguments.of(json("{}"), false), Arguments.of(Val.of("text"), false), Arguments.of(Val.of(42), false),
                Arguments.of(Val.TRUE, false), Arguments.of(Val.NULL, false), Arguments.of(Val.UNDEFINED, false),
                Arguments.of(Val.error("test"), false));
    }

    @ParameterizedTest
    @MethodSource("provideIsObjectTestCases")
    void when_isObject_then_returnsCorrectResult(Val input, boolean expected) {
        val actual = ReflectionFunctionLibrary.isObject(input);
        assertThatVal(actual).isEqualTo(Val.of(expected));
    }

    private static Stream<Arguments> provideIsObjectTestCases() throws JsonProcessingException {
        return Stream.of(Arguments.of(json("{\"key\": \"val\"}"), true), Arguments.of(json("{}"), true),
                Arguments.of(json("[]"), false), Arguments.of(Val.of("text"), false), Arguments.of(Val.of(42), false),
                Arguments.of(Val.NULL, false), Arguments.of(Val.UNDEFINED, false));
    }

    @ParameterizedTest
    @MethodSource("provideIsTextTestCases")
    void when_isText_then_returnsCorrectResult(Val input, boolean expected) {
        val actual = ReflectionFunctionLibrary.isText(input);
        assertThatVal(actual).isEqualTo(Val.of(expected));
    }

    private static Stream<Arguments> provideIsTextTestCases() throws JsonProcessingException {
        return Stream.of(Arguments.of(Val.of("hello"), true), Arguments.of(Val.of(""), true),
                Arguments.of(Val.of(123), false), Arguments.of(Val.TRUE, false), Arguments.of(json("[]"), false),
                Arguments.of(Val.UNDEFINED, false));
    }

    @ParameterizedTest
    @MethodSource("provideIsNumberTestCases")
    void when_isNumber_then_returnsCorrectResult(Val input, boolean expected) {
        val actual = ReflectionFunctionLibrary.isNumber(input);
        assertThatVal(actual).isEqualTo(Val.of(expected));
    }

    private static Stream<Arguments> provideIsNumberTestCases() {
        return Stream.of(Arguments.of(Val.of(42), true), Arguments.of(Val.of(3.5), true),
                Arguments.of(Val.of(5.0), true), Arguments.of(Val.of(0), true), Arguments.of(Val.of(-10), true),
                Arguments.of(Val.of("123"), false), Arguments.of(Val.TRUE, false), Arguments.of(Val.UNDEFINED, false));
    }

    @ParameterizedTest
    @MethodSource("provideIsBooleanTestCases")
    void when_isBoolean_then_returnsCorrectResult(Val input, boolean expected) {
        val actual = ReflectionFunctionLibrary.isBoolean(input);
        assertThatVal(actual).isEqualTo(Val.of(expected));
    }

    private static Stream<Arguments> provideIsBooleanTestCases() {
        return Stream.of(Arguments.of(Val.TRUE, true), Arguments.of(Val.FALSE, true), Arguments.of(Val.of(1), false),
                Arguments.of(Val.of(0), false), Arguments.of(Val.of("true"), false),
                Arguments.of(Val.UNDEFINED, false));
    }

    @ParameterizedTest
    @MethodSource("provideIsIntegerTestCases")
    void when_isInteger_then_returnsCorrectResult(Val input, boolean expected) {
        val actual = ReflectionFunctionLibrary.isInteger(input);
        assertThatVal(actual).isEqualTo(Val.of(expected));
    }

    private static Stream<Arguments> provideIsIntegerTestCases() {
        return Stream.of(Arguments.of(Val.of(42), true), Arguments.of(Val.of(0), true), Arguments.of(Val.of(-10), true),
                Arguments.of(Val.of(5.0), false), Arguments.of(Val.of(3.5), false), Arguments.of(Val.of(0.1), false),
                Arguments.of(Val.of("5"), false), Arguments.of(Val.TRUE, false), Arguments.of(Val.UNDEFINED, false));
    }

    @ParameterizedTest
    @MethodSource("provideIsFloatTestCases")
    void when_isFloat_then_returnsCorrectResult(Val input, boolean expected) {
        val actual = ReflectionFunctionLibrary.isFloat(input);
        assertThatVal(actual).isEqualTo(Val.of(expected));
    }

    private static Stream<Arguments> provideIsFloatTestCases() {
        return Stream.of(Arguments.of(Val.of(3.5), true), Arguments.of(Val.of(0.5), true),
                Arguments.of(Val.of(-2.5), true), Arguments.of(Val.of(5.0), true), Arguments.of(Val.of(42), false),
                Arguments.of(Val.of(0), false), Arguments.of(Val.of("3.5"), false),
                Arguments.of(Val.UNDEFINED, false));
    }

    @ParameterizedTest
    @MethodSource("provideIsNullTestCases")
    void when_isNull_then_returnsCorrectResult(Val input, boolean expected) {
        val actual = ReflectionFunctionLibrary.isNull(input);
        assertThatVal(actual).isEqualTo(Val.of(expected));
    }

    private static Stream<Arguments> provideIsNullTestCases() {
        return Stream.of(Arguments.of(Val.NULL, true), Arguments.of(Val.UNDEFINED, false),
                Arguments.of(Val.of(0), false), Arguments.of(Val.of(""), false), Arguments.of(Val.FALSE, false));
    }

    @ParameterizedTest
    @MethodSource("provideIsUndefinedTestCases")
    void when_isUndefined_then_returnsCorrectResult(Val input, boolean expected) {
        val actual = ReflectionFunctionLibrary.isUndefined(input);
        assertThatVal(actual).isEqualTo(Val.of(expected));
    }

    private static Stream<Arguments> provideIsUndefinedTestCases() {
        return Stream.of(Arguments.of(Val.UNDEFINED, true), Arguments.of(Val.NULL, false),
                Arguments.of(Val.of(0), false), Arguments.of(Val.FALSE, false), Arguments.of(Val.error("test"), false));
    }

    @ParameterizedTest
    @MethodSource("provideIsDefinedTestCases")
    void when_isDefined_then_returnsCorrectResult(Val input, boolean expected) {
        val actual = ReflectionFunctionLibrary.isDefined(input);
        assertThatVal(actual).isEqualTo(Val.of(expected));
    }

    private static Stream<Arguments> provideIsDefinedTestCases() throws JsonProcessingException {
        return Stream.of(Arguments.of(Val.of(42), true), Arguments.of(Val.NULL, true), Arguments.of(Val.FALSE, true),
                Arguments.of(json("[]"), true), Arguments.of(json("{}"), true), Arguments.of(Val.UNDEFINED, false),
                Arguments.of(Val.error("test"), false));
    }

    @Test
    void when_isError_then_returnsTrue() {
        val errorVal = Val.error("test error");
        val actual   = ReflectionFunctionLibrary.isError(errorVal);
        assertThatVal(actual).isEqualTo(Val.TRUE);
    }

    @ParameterizedTest
    @MethodSource("provideIsErrorFalseTestCases")
    void when_isError_then_returnsFalse(Val input) {
        val actual = ReflectionFunctionLibrary.isError(input);
        assertThatVal(actual).isEqualTo(Val.FALSE);
    }

    private static Stream<Arguments> provideIsErrorFalseTestCases() throws JsonProcessingException {
        return Stream.of(Arguments.of(Val.of(42)), Arguments.of(Val.UNDEFINED), Arguments.of(Val.NULL),
                Arguments.of(json("[]")));
    }

    @Test
    void when_isSecret_then_returnsTrue() {
        val secretVal = Val.of("password").asSecret();
        val actual    = ReflectionFunctionLibrary.isSecret(secretVal);
        assertThatVal(actual).isEqualTo(Val.TRUE);
    }

    @Test
    void when_isSecret_then_returnsFalse() {
        val normalVal = Val.of("public data");
        val actual    = ReflectionFunctionLibrary.isSecret(normalVal);
        assertThatVal(actual).isEqualTo(Val.FALSE);
    }

    @ParameterizedTest
    @MethodSource("provideIsEmptyTestCases")
    void when_isEmpty_then_returnsCorrectResult(Val input, boolean expected) {
        val actual = ReflectionFunctionLibrary.isEmpty(input);
        assertThatVal(actual).isEqualTo(Val.of(expected));
    }

    private static Stream<Arguments> provideIsEmptyTestCases() throws JsonProcessingException {
        return Stream.of(Arguments.of(json("[]"), true), Arguments.of(json("{}"), true),
                Arguments.of(json("[1, 2]"), false), Arguments.of(json("{\"a\": 1}"), false),
                Arguments.of(Val.NULL, true), Arguments.of(Val.of(""), true), Arguments.of(Val.of(0), true));
    }

    @ParameterizedTest
    @MethodSource("provideTypeOfTestCases")
    void when_typeOf_then_returnsCorrectType(Val input, String expectedType) {
        val actual = ReflectionFunctionLibrary.typeOf(input);
        assertThatVal(actual).isEqualTo(Val.of(expectedType));
    }

    private static Stream<Arguments> provideTypeOfTestCases() throws JsonProcessingException {
        return Stream.of(Arguments.of(json("[1, 2, 3]"), "ARRAY"), Arguments.of(json("{\"key\": \"val\"}"), "OBJECT"),
                Arguments.of(Val.of("hello"), "STRING"), Arguments.of(Val.of(42), "NUMBER"),
                Arguments.of(Val.TRUE, "BOOLEAN"), Arguments.of(Val.NULL, "NULL"),
                Arguments.of(Val.UNDEFINED, "undefined"), Arguments.of(Val.error("test"), "ERROR"));
    }

    @Test
    void when_typeOfOnComplexNumber_then_returnsNumber() {
        val actual = ReflectionFunctionLibrary.typeOf(Val.of(3.5));
        assertThatVal(actual).isEqualTo(Val.of("NUMBER"));
    }
}
