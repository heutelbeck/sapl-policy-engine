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
package io.sapl.compiler.util;

import com.google.re2j.PatternSyntaxException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("LinearRegex")
class LinearRegexTests {

    private static final String TOO_LARGE        = LinearRegex.ERROR_EXPRESSION_TOO_LARGE;
    private static final String NESTS_TOO_DEEPLY = LinearRegex.ERROR_EXPRESSION_NESTS_TOO_DEEPLY;

    @Nested
    @DisplayName("Hostile input")
    class HostileInput {

        @Timeout(10)
        @MethodSource
        @ParameterizedTest(name = "{0}")
        @DisplayName("a pattern that backtracking engines take exponential time for answers in linear time")
        void whenPatternWouldBacktrackCatastrophicallyThenAnswersQuickly(String description, String regex,
                String input) {
            assertThat(LinearRegex.compile(regex).matches(input)).isFalse();
        }

        static Stream<Arguments> whenPatternWouldBacktrackCatastrophicallyThenAnswersQuickly() {
            return Stream.of(arguments("nested plus", "(a+)+c", "a".repeat(100_000) + "b"),
                    arguments("nested star over alternatives", "(a|aa)*c", "a".repeat(100_000) + "b"),
                    arguments("repeated wildcard groups", "(.*,){30}P", "1,".repeat(10_000)),
                    arguments("overlapping classes", "(\\w|\\d)+!", "1".repeat(100_000)));
        }

        @Timeout(10)
        @MethodSource
        @ParameterizedTest(name = "{0}")
        @DisplayName("a pattern whose program would exhaust memory is refused before it is compiled")
        void whenPatternExpandsBeyondTheLimitThenRefusedAsTooLarge(String description, String regex) {
            assertThatThrownBy(() -> LinearRegex.compile(regex)).isInstanceOf(PatternSyntaxException.class)
                    .extracting(e -> ((PatternSyntaxException) e).getDescription()).isEqualTo(TOO_LARGE);
        }

        static Stream<Arguments> whenPatternExpandsBeyondTheLimitThenRefusedAsTooLarge() {
            return Stream.of(arguments("two levels of a thousand", "(?:a{1000}){1000}"),
                    arguments("three levels of a thousand", "(?:(?:a{1000}){1000}){1000}"),
                    arguments("just above the limit", "(?:a{100}){101}"),
                    arguments("optional repetitions", "(?:a{0,100}){0,100}"),
                    arguments("unbounded repetition of a large group", "(?:a{1000}){20,}"),
                    arguments("a long concatenation of repetitions", "a{1000}".repeat(11)),
                    arguments("a long literal", "a".repeat(LinearRegex.MAX_PROGRAM_SIZE + 1)),
                    arguments("a long quoted section", "\\Q" + "a".repeat(LinearRegex.MAX_PROGRAM_SIZE + 1) + "\\E"),
                    arguments("alternatives of large groups", "(a{1000}|b{1000}|c{1000}){5}"),
                    arguments("a group left open", "(a{1000}" + "b{1000}".repeat(10)));
        }

        @ParameterizedTest(name = "{0} is accepted")
        @ValueSource(strings = { "\\p{L}{1,238}", "\\P{L}{1,238}", "\\pL{243}", "\\p{^L}{0,238}", "[\\p{L}]{1,238}",
                "\\p{Lu}{1,370}", "\\p{Lo}{1,294}", "\\p{N}{1,1000}", "\\p{Greek}{1,1000}", ".{1,1000}", "[^a]{1,1000}",
                "[\\p{L}\\p{N}]{1,196}" })
        @DisplayName("a repetition of a large Unicode class up to its border is accepted")
        void whenLargeUnicodeClassIsRepeatedUpToItsBorderThenAccepted(String regex) {
            assertThatCode(() -> LinearRegex.compile(regex)).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "{0} is too large")
        @ValueSource(strings = { "\\p{L}{1,1000}", "\\p{L}{1,239}", "\\P{L}{1,239}", "\\pL{244}", "\\p{^L}{0,239}",
                "[\\p{L}]{1,239}", "[^\\p{L}]{1,239}", "\\p{Lu}{1,371}", "\\p{Lo}{1,295}", "[\\p{L}\\p{N}]{1,197}",
                "(?:\\p{L}\\p{Lu}){1,200}" })
        @DisplayName("a repetition of a large Unicode class beyond its border is refused as too large")
        void whenLargeUnicodeClassIsRepeatedBeyondItsBorderThenRefusedAsTooLarge(String regex) {
            assertThatThrownBy(() -> LinearRegex.compile(regex)).isInstanceOf(PatternSyntaxException.class)
                    .extracting(e -> ((PatternSyntaxException) e).getDescription()).isEqualTo(TOO_LARGE);
        }

        @Timeout(10)
        @MethodSource
        @ParameterizedTest(name = "{0}")
        @DisplayName("a pattern whose groups nest too deeply is refused without exhausting the stack")
        void whenGroupsNestTooDeeplyThenRefused(String description, int depth) {
            final var regex = "(".repeat(depth) + "a" + ")".repeat(depth);
            assertThatThrownBy(() -> LinearRegex.compile(regex)).isInstanceOf(PatternSyntaxException.class)
                    .extracting(e -> ((PatternSyntaxException) e).getDescription()).isEqualTo(NESTS_TOO_DEEPLY);
        }

        static Stream<Arguments> whenGroupsNestTooDeeplyThenRefused() {
            return Stream.of(arguments("one level above the limit", LinearRegex.MAX_NESTING_DEPTH + 1),
                    arguments("a hundred thousand levels", 100_000));
        }
    }

    @Nested
    @DisplayName("Patterns within the limits")
    class WithinLimits {

        @MethodSource
        @ParameterizedTest(name = "{0}")
        @DisplayName("a pattern within the limits is compiled and matches")
        void whenPatternWithinLimitsThenMatches(String description, String regex, String input, boolean expected) {
            assertThat(LinearRegex.compile(regex).matches(input)).isEqualTo(expected);
        }

        static Stream<Arguments> whenPatternWithinLimitsThenMatches() {
            return Stream.of(arguments("the largest single repetition", "a{1000}", "a".repeat(1000), true),
                    arguments("nested repetitions just below the limit", "(?:a{100}){99}", "a".repeat(9900), true),
                    arguments("a bounded class repetition", "[a-z]{1,1000}", "abc", true),
                    arguments("a Unicode category", "\\p{L}{1,100}", "été", true),
                    arguments("a host name", "(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.){1,10}[a-z]{2,63}",
                            "api.example.com", true),
                    arguments("groups nested to the limit",
                            "(".repeat(LinearRegex.MAX_NESTING_DEPTH) + "a" + ")".repeat(LinearRegex.MAX_NESTING_DEPTH),
                            "a", true),
                    arguments("a brace without a count is a literal", "a{,1000}", "a{,1000}", true),
                    arguments("an escaped brace is a literal", "a\\{1000}", "a{1000}", true),
                    arguments("braces in a class are literals", "[{}0-9]{3}", "{1}", true),
                    arguments("a quoted section is read literally", "\\Qa{1000}\\E", "a{1000}", true),
                    arguments("named groups", "(?P<user>[a-z]+):(?<role>[a-z]+)", "alice:admin", true),
                    arguments("flags", "(?i)ADMIN", "admin", true), arguments("a flag group", "(?i:A)b", "ab", true),
                    arguments("the whole text must match", "admin", "administrator", false));
        }

        @MethodSource
        @ParameterizedTest(name = "{0}")
        @DisplayName("an invalid pattern is refused with the reason RE2/J gives")
        void whenPatternInvalidThenRefusedWithReason(String description, String regex, String reason) {
            assertThatThrownBy(() -> LinearRegex.compile(regex)).isInstanceOf(PatternSyntaxException.class)
                    .extracting(e -> ((PatternSyntaxException) e).getDescription()).isEqualTo(reason);
        }

        static Stream<Arguments> whenPatternInvalidThenRefusedWithReason() {
            return Stream.of(arguments("an unclosed group", "(", "missing closing )"),
                    arguments("an unclosed class", "[a", "missing closing ]"),
                    arguments("a look-ahead", "a(?=b)", "invalid or unsupported Perl syntax"),
                    arguments("a backreference", "(a)\\1", "invalid escape sequence"),
                    arguments("a possessive quantifier", "a*+", "invalid nested repetition operator"),
                    arguments("a repetition count above a thousand", "a{1001}", "invalid repeat count"),
                    arguments("a stray closing parenthesis", "a)", "unexpected )"));
        }
    }
}
