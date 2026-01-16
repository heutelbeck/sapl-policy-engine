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

import static io.sapl.compiler.StringsUtil.unescapeString;
import static io.sapl.compiler.StringsUtil.unquoteString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for StringsUtil SAPL string literal processing utilities.
 */
class StringsUtilTests {

    // ========================================================================
    // unquoteString parameterized tests
    // ========================================================================

    static Stream<Arguments> unquoteStringTestCases() {
        return Stream.of(
                // Null and edge cases
                arguments(null, null), arguments("", ""), arguments("x", "x"),

                // Double quoted
                arguments("\"hello\"", "hello"), arguments("\"hello world\"", "hello world"), arguments("\"\"", ""),
                arguments("\"x\"", "x"),

                // Single quoted
                arguments("'hello'", "hello"), arguments("'hello world'", "hello world"), arguments("''", ""),
                arguments("'x'", "x"),

                // Escape sequences in quoted strings
                arguments("\"line1\\nline2\"", "line1\nline2"), arguments("\"tab\\there\"", "tab\there"),
                arguments("\"carriage\\rreturn\"", "carriage\rreturn"), arguments("\"back\\bspace\"", "back\bspace"),
                arguments("\"form\\ffeed\"", "form\ffeed"), arguments("\"back\\\\slash\"", "back\\slash"),
                arguments("\"double\\\"quote\"", "double\"quote"), arguments("'single\\'quote'", "single'quote"),
                arguments("\"forward\\/slash\"", "forward/slash"),

                // Unicode escapes
                arguments("\"unicode\\u0041char\"", "unicodeAchar"),
                arguments("\"\\u0048\\u0065\\u006c\\u006c\\u006f\"", "Hello"),

                // Unquoted (returned as-is)
                arguments("unquoted", "unquoted"), arguments("no quotes here", "no quotes here"),

                // Mismatched quotes (returned as-is)
                arguments("\"mismatched'", "\"mismatched'"), arguments("'mismatched\"", "'mismatched\""),

                // Complex combinations
                arguments("\"Hello\\nWorld\\t\\\"quoted\\\"\\u0021\"", "Hello\nWorld\t\"quoted\"!"),
                arguments("\"She said \\\"Hello\\\"\"", "She said \"Hello\""),
                arguments("\"\\n\\r\\t\\b\\f\\\\\\\"\\'\\/\"", "\n\r\t\b\f\\\"'/"));
    }

    @ParameterizedTest
    @MethodSource("unquoteStringTestCases")
    void unquoteString_returnsExpected(String input, String expected) {
        assertThat(unquoteString(input)).isEqualTo(expected);
    }

    // ========================================================================
    // unescapeString parameterized tests
    // ========================================================================

    static Stream<Arguments> unescapeStringTestCases() {
        return Stream.of(
                // Null and no-op cases
                arguments(null, null), arguments("", ""), arguments("no escapes", "no escapes"),
                arguments("plain text", "plain text"),

                // Basic escape sequences
                arguments("hello\\nworld", "hello\nworld"), arguments("hello\\rworld", "hello\rworld"),
                arguments("hello\\tworld", "hello\tworld"), arguments("hello\\bworld", "hello\bworld"),
                arguments("hello\\fworld", "hello\fworld"), arguments("hello\\\\world", "hello\\world"),
                arguments("hello\\\"world", "hello\"world"), arguments("hello\\'world", "hello'world"),
                arguments("hello\\/world", "hello/world"),

                // Multiple escapes
                arguments("a\\nb\\tc", "a\nb\tc"), arguments("\\n\\r\\t", "\n\r\t"),

                // Unicode escapes
                arguments("\\u0041", "A"), arguments("\\u0048\\u0069", "Hi"),
                arguments("prefix\\u0041suffix", "prefixAsuffix"),

                // Unknown escape (backslash preserved)
                arguments("hello\\xworld", "hello\\xworld"), arguments("\\z", "\\z"),

                // Incomplete escapes (preserved)
                arguments("\\", "\\"), arguments("trailing\\", "trailing\\"), arguments("\\u00", "\\u00"),
                arguments("\\u004", "\\u004"), arguments("\\uGGGG", "\\uGGGG"));
    }

    @ParameterizedTest
    @MethodSource("unescapeStringTestCases")
    void unescapeString_returnsExpected(String input, String expected) {
        assertThat(unescapeString(input)).isEqualTo(expected);
    }

}
