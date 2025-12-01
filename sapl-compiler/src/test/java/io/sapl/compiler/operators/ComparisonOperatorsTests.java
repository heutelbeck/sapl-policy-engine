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
package io.sapl.compiler.operators;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.compiler.SaplCompilerException;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ComparisonOperatorsTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_equals_then_returnsExpectedValue(String description, Value a, Value b, boolean expected) {
        val actual = ComparisonOperators.equals(null, a, b);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> when_equals_then_returnsExpectedValue() {
        return Stream.of(arguments("equal numbers", Value.of(5), Value.of(5), true),
                arguments("different numbers", Value.of(5), Value.of(3), false),
                arguments("equal strings", Value.of("text"), Value.of("text"), true),
                arguments("different strings", Value.of("text"), Value.of("other"), false),
                arguments("equal booleans", Value.TRUE, Value.TRUE, true),
                arguments("different booleans", Value.TRUE, Value.FALSE, false),
                arguments("null equals null", Value.NULL, Value.NULL, true),
                arguments("number not equal to string", Value.of(5), Value.of("5"), false),
                arguments("empty arrays", Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, true),
                arguments("empty objects", Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, true),
                arguments("number equality with different scales", Value.of(1.0), Value.of(1.00), true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_notEquals_then_returnsExpectedValue(String description, Value a, Value b, boolean expected) {
        val actual = ComparisonOperators.notEquals(null, a, b);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> when_notEquals_then_returnsExpectedValue() {
        return Stream.of(arguments("equal numbers", Value.of(5), Value.of(5), false),
                arguments("different numbers", Value.of(5), Value.of(3), true),
                arguments("equal strings", Value.of("text"), Value.of("text"), false),
                arguments("different strings", Value.of("text"), Value.of("other"), true),
                arguments("number not equal to string", Value.of(5), Value.of("5"), true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_isContainedIn_then_returnsExpectedValue(String description, Value needle, Value haystack,
            boolean expected) {
        val actual = ComparisonOperators.isContainedIn(null, needle, haystack);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> when_isContainedIn_then_returnsExpectedValue() {
        val array  = Value.ofArray(Value.of(1), Value.of(2), Value.of(3));
        val object = Value.ofObject(java.util.Map.of("key1", Value.of("value1"), "key2", Value.of("value2")));
        return Stream.of(
                // Array cases
                arguments("value in array", Value.of(2), array, true),
                arguments("value not in array", Value.of(5), array, false),
                arguments("empty array", Value.of(1), Value.EMPTY_ARRAY, false),
                // Object cases
                arguments("value in object", Value.of("value1"), object, true),
                arguments("value not in object", Value.of("value3"), object, false),
                arguments("key not treated as value", Value.of("key1"), object, false),
                arguments("empty object", Value.of("value"), Value.EMPTY_OBJECT, false),
                // String cases
                arguments("substring present", Value.of("world"), Value.of("hello world"), true),
                arguments("substring not present", Value.of("foo"), Value.of("hello world"), false),
                arguments("empty needle in string", Value.of(""), Value.of("text"), true),
                arguments("exact match", Value.of("text"), Value.of("text"), true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_isContainedIn_withTypeMismatch_then_returnsError(String description, Value needle, Value haystack) {
        val actual = ComparisonOperators.isContainedIn(null, needle, haystack);
        assertThat(actual).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                .contains("'in' operator");
    }

    private static Stream<Arguments> when_isContainedIn_withTypeMismatch_then_returnsError() {
        return Stream.of(arguments("haystack is number", Value.of("text"), Value.of(5)),
                arguments("haystack is boolean", Value.of("text"), Value.TRUE),
                arguments("needle is string but haystack is number", Value.of("5"), Value.of(5)),
                arguments("needle is number but haystack is string", Value.of(5), Value.of("text")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_matchesRegularExpression_then_returnsExpectedValue(String description, Value input, Value regex,
            boolean expected) {
        val actual = ComparisonOperators.matchesRegularExpression(null, input, regex);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> when_matchesRegularExpression_then_returnsExpectedValue() {
        return Stream.of(arguments("exact match", Value.of("hello"), Value.of("hello"), true),
                arguments("no match", Value.of("hello"), Value.of("world"), false),
                arguments("pattern matches entire string", Value.of("123"), Value.of("\\d+"), true),
                arguments("pattern no match", Value.of("abc"), Value.of("\\d+"), false),
                arguments("anchored pattern", Value.of("test"), Value.of("test"), true),
                arguments("partial no match because Pattern.matches requires full match", Value.of("testing"),
                        Value.of("test"), false));
    }

    @Test
    void when_matchesRegularExpression_withInvalidPattern_then_returnsError() {
        val actual = ComparisonOperators.matchesRegularExpression(null, Value.of("text"), Value.of("[invalid"));
        assertThat(actual).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                .contains("Invalid regular expression");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_matchesRegularExpression_withTypeMismatch_then_returnsError(String description, Value input, Value regex,
            String expectedErrorFragment) {
        val actual = ComparisonOperators.matchesRegularExpression(null, input, regex);
        assertThat(actual).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                .contains(expectedErrorFragment);
    }

    private static Stream<Arguments> when_matchesRegularExpression_withTypeMismatch_then_returnsError() {
        return Stream.of(
                arguments("input not string", Value.of(5), Value.of("pattern"), "can only be matched against strings"),
                arguments("regex not string", Value.of("text"), Value.of(5), "must be strings"));
    }

    @Test
    void when_compileRegularExpressionOperator_withValidPattern_then_returnsOperator() {
        val operator = ComparisonOperators.compileRegularExpressionOperator(null, Value.of("\\d+"));

        val matchResult = operator.apply(Value.of("123"));
        assertThat(matchResult).isEqualTo(Value.TRUE);

        val noMatchResult = operator.apply(Value.of("abc"));
        assertThat(noMatchResult).isEqualTo(Value.FALSE);
    }

    /* Secret Flag Preservation Tests */

    @ParameterizedTest(name = "equals: {0}")
    @MethodSource("secretFlagTestCases")
    void when_equals_withSecrets_then_preservesSecretFlag(String description, Value a, Value b,
            boolean expectedSecret) {
        val actual = ComparisonOperators.equals(null, a, b);
        assertThat(actual.secret()).isEqualTo(expectedSecret);
    }

    @ParameterizedTest(name = "notEquals: {0}")
    @MethodSource("secretFlagTestCases")
    void when_notEquals_withSecrets_then_preservesSecretFlag(String description, Value a, Value b,
            boolean expectedSecret) {
        val actual = ComparisonOperators.notEquals(null, a, b);
        assertThat(actual.secret()).isEqualTo(expectedSecret);
    }

    @ParameterizedTest(name = "isContainedIn: {0}")
    @MethodSource("secretFlagTestCasesForContainedIn")
    void when_isContainedIn_withSecrets_then_preservesSecretFlag(String description, Value needle, Value haystack,
            boolean expectedSecret) {
        val actual = ComparisonOperators.isContainedIn(null, needle, haystack);
        assertThat(actual.secret()).isEqualTo(expectedSecret);
    }

    @ParameterizedTest(name = "matchesRegex: {0}")
    @MethodSource("secretFlagTestCases")
    void when_matchesRegularExpression_withSecrets_then_preservesSecretFlag(String description, Value input,
            Value regex, boolean expectedSecret) {
        val actual = ComparisonOperators.matchesRegularExpression(null, input, regex);
        assertThat(actual.secret()).isEqualTo(expectedSecret);
    }

    @ParameterizedTest(name = "compiledRegex: {0}")
    @MethodSource("secretFlagTestCases")
    void when_compileRegularExpressionOperator_withSecrets_then_preservesSecretFlag(String description, Value regex,
            Value input, boolean expectedSecret) {
        val operator = ComparisonOperators.compileRegularExpressionOperator(null, regex);
        val result   = operator.apply(input);
        assertThat(result.secret()).isEqualTo(expectedSecret);
    }

    private static Stream<Arguments> secretFlagTestCases() {
        return Stream.of(arguments("both public", Value.of("test"), Value.of("test"), false),
                arguments("left secret", Value.of("test").asSecret(), Value.of("test"), true),
                arguments("right secret", Value.of("test"), Value.of("test").asSecret(), true),
                arguments("both secret", Value.of("test").asSecret(), Value.of("test").asSecret(), true));
    }

    private static Stream<Arguments> secretFlagTestCasesForContainedIn() {
        val array = Value.ofArray(Value.of(1), Value.of(2), Value.of(3));
        return Stream.of(arguments("both public", Value.of(2), array, false),
                arguments("needle secret", Value.of(2).asSecret(), array, true),
                arguments("haystack secret", Value.of(2), array.asSecret(), true),
                arguments("both secret", Value.of(2).asSecret(), array.asSecret(), true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_compileRegularExpressionOperator_withInvalidInput_then_throwsException(String description, Value regex,
            String expectedErrorFragment) {
        assertThatThrownBy(() -> ComparisonOperators.compileRegularExpressionOperator(null, regex))
                .isInstanceOf(SaplCompilerException.class).hasMessageContaining(expectedErrorFragment);
    }

    private static Stream<Arguments> when_compileRegularExpressionOperator_withInvalidInput_then_throwsException() {
        return Stream.of(arguments("non-text regex", Value.of(5), "must be strings"),
                arguments("invalid pattern", Value.of("[invalid"), "Invalid regular expression"));
    }

    @Test
    void when_compiledOperator_withNonTextInput_then_returnsError() {
        val operator = ComparisonOperators.compileRegularExpressionOperator(null, Value.of("test"));

        val result = operator.apply(Value.of(5));
        assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                .contains("can only be matched against strings");
    }
}
