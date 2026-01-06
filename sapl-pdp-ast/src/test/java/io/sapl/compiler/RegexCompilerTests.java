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
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.Value;
import io.sapl.ast.BinaryOperator;
import io.sapl.ast.BinaryOperatorType;
import io.sapl.ast.Literal;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class RegexCompilerTests {

    private static final SourceLocation TEST_LOC = new SourceLocation("test", "", 0, 0, 1, 1, 1, 1);

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_matchRegexPrecompiled_then_returnsExpected(String description, String input, String pattern,
            Value expected) {
        val matcher = java.util.regex.Pattern.compile(pattern).asMatchPredicate();
        val actual  = RegexCompiler.matchRegex(Value.of(input), matcher, null);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_matchRegexPrecompiled_then_returnsExpected() {
        return Stream.of(
                // Exact match
                arguments("exact match", "hello", "hello", Value.TRUE),
                arguments("no match", "hello", "world", Value.FALSE),
                // Regex patterns
                arguments("starts with", "hello world", "hello.*", Value.TRUE),
                arguments("ends with", "hello world", ".*world", Value.TRUE),
                arguments("contains", "hello world", ".*lo wo.*", Value.TRUE),
                arguments("character class", "abc123", "[a-z]+[0-9]+", Value.TRUE),
                arguments("digit pattern", "12345", "\\d+", Value.TRUE),
                arguments("word boundary", "hello", "\\w+", Value.TRUE),
                // Empty cases
                arguments("empty pattern matches empty", "", "", Value.TRUE),
                arguments("empty pattern matches all", "hello", ".*", Value.TRUE),
                arguments("non-empty pattern vs empty input", "", "hello", Value.FALSE));
    }

    @Test
    void when_matchRegexPrecompiled_withNonTextValue_then_returnsFalse() {
        val matcher = java.util.regex.Pattern.compile(".*").asMatchPredicate();

        assertThat(RegexCompiler.matchRegex(Value.of(5), matcher, null)).isEqualTo(Value.FALSE);
        assertThat(RegexCompiler.matchRegex(Value.TRUE, matcher, null)).isEqualTo(Value.FALSE);
        assertThat(RegexCompiler.matchRegex(Value.NULL, matcher, null)).isEqualTo(Value.FALSE);
        assertThat(RegexCompiler.matchRegex(Value.EMPTY_ARRAY, matcher, null)).isEqualTo(Value.FALSE);
        assertThat(RegexCompiler.matchRegex(Value.EMPTY_OBJECT, matcher, null)).isEqualTo(Value.FALSE);
    }

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_matchRegexRuntime_then_returnsExpected(String description, String input, String pattern, Value expected) {
        val actual = RegexCompiler.matchRegex(Value.of(input), Value.of(pattern), null);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_matchRegexRuntime_then_returnsExpected() {
        return Stream.of(arguments("exact match", "hello", "hello", Value.TRUE),
                arguments("no match", "hello", "world", Value.FALSE),
                arguments("regex pattern", "abc123", "[a-z]+[0-9]+", Value.TRUE),
                arguments("partial match fails (anchored)", "hello world", "hello", Value.FALSE),
                arguments("full match succeeds", "hello", "hello", Value.TRUE));
    }

    @Test
    void when_matchRegexRuntime_withNonTextPattern_then_returnsError() {
        val actual = RegexCompiler.matchRegex(Value.of("hello"), Value.of(123), null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("Regular expression must be a string");
    }

    @Test
    void when_matchRegexRuntime_withNonTextInput_then_returnsFalse() {
        assertThat(RegexCompiler.matchRegex(Value.of(5), Value.of(".*"), null)).isEqualTo(Value.FALSE);
        assertThat(RegexCompiler.matchRegex(Value.TRUE, Value.of(".*"), null)).isEqualTo(Value.FALSE);
    }

    @Test
    void when_matchRegexRuntime_withInvalidPattern_then_returnsError() {
        val actual = RegexCompiler.matchRegex(Value.of("hello"), Value.of("[invalid"), null);
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("Invalid regular expression");
    }

    @Test
    void when_compileRegex_withInvalidPattern_then_throwsException() {
        val compiler = new RegexCompiler();
        val ctx      = new CompilationContext(null, null, null);

        // Create a BinaryOperator with an invalid regex pattern
        val leftExpr  = new Literal(Value.of("hello"), TEST_LOC);
        val rightExpr = new Literal(Value.of("[invalid"), TEST_LOC);
        val binaryOp  = new BinaryOperator(BinaryOperatorType.REGEX, leftExpr, rightExpr, TEST_LOC);

        assertThatThrownBy(() -> compiler.compile(binaryOp, ctx)).isInstanceOf(SaplCompilerException.class)
                .hasMessageContaining("Invalid regular expression");
    }

    @Test
    void when_compileRegex_withLiteralPattern_then_returnsPrecompiledResult() {
        val compiler = new RegexCompiler();
        val ctx      = new CompilationContext(null, null, null);

        // Both input and pattern are literals - should return a Value directly
        val leftExpr  = new Literal(Value.of("hello"), TEST_LOC);
        val rightExpr = new Literal(Value.of("hello"), TEST_LOC);
        val binaryOp  = new BinaryOperator(BinaryOperatorType.REGEX, leftExpr, rightExpr, TEST_LOC);

        val result = compiler.compile(binaryOp, ctx);
        assertThat(result).isEqualTo(Value.TRUE);
    }

    @Test
    void when_compileRegex_withNonMatchingLiteralPattern_then_returnsFalse() {
        val compiler = new RegexCompiler();
        val ctx      = new CompilationContext(null, null, null);

        val leftExpr  = new Literal(Value.of("hello"), TEST_LOC);
        val rightExpr = new Literal(Value.of("world"), TEST_LOC);
        val binaryOp  = new BinaryOperator(BinaryOperatorType.REGEX, leftExpr, rightExpr, TEST_LOC);

        val result = compiler.compile(binaryOp, ctx);
        assertThat(result).isEqualTo(Value.FALSE);
    }

    @Test
    void when_matchRegex_withEmailPattern_then_matchesValidEmail() {
        val emailPattern = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
        val matcher      = java.util.regex.Pattern.compile(emailPattern).asMatchPredicate();

        assertThat(RegexCompiler.matchRegex(Value.of("user@example.com"), matcher, null)).isEqualTo(Value.TRUE);
        assertThat(RegexCompiler.matchRegex(Value.of("invalid-email"), matcher, null)).isEqualTo(Value.FALSE);
    }

    @Test
    void when_matchRegex_withIpAddressPattern_then_matchesValidIp() {
        val ipPattern = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$";
        val matcher   = java.util.regex.Pattern.compile(ipPattern).asMatchPredicate();

        assertThat(RegexCompiler.matchRegex(Value.of("192.168.1.1"), matcher, null)).isEqualTo(Value.TRUE);
        assertThat(RegexCompiler.matchRegex(Value.of("256.1.1.1"), matcher, null)).isEqualTo(Value.FALSE);
    }

    @Test
    void when_matchRegex_withUnicodeSupport_then_matchesUnicode() {
        val unicodePattern = "^[\\p{L}]+$"; // Unicode letters only
        val matcher        = java.util.regex.Pattern.compile(unicodePattern).asMatchPredicate();

        assertThat(RegexCompiler.matchRegex(Value.of("Ümläütß"), matcher, null)).isEqualTo(Value.TRUE);
        assertThat(RegexCompiler.matchRegex(Value.of("hello123"), matcher, null)).isEqualTo(Value.FALSE);
    }

    @Test
    void when_matchRegex_withCaseInsensitiveFlag_then_matchesCaseInsensitive() {
        val pattern = java.util.regex.Pattern.compile("hello", java.util.regex.Pattern.CASE_INSENSITIVE)
                .asMatchPredicate();

        assertThat(RegexCompiler.matchRegex(Value.of("HELLO"), pattern, null)).isEqualTo(Value.TRUE);
        assertThat(RegexCompiler.matchRegex(Value.of("HeLLo"), pattern, null)).isEqualTo(Value.TRUE);
    }

}
