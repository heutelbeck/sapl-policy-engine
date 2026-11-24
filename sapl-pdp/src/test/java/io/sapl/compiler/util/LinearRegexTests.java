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

import com.heutelbeck.rustregex4j.Regex;
import com.heutelbeck.rustregex4j.RegexError;
import com.heutelbeck.rustregex4j.RegexErrorKind;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("LinearRegex")
class LinearRegexTests {

    private static boolean matchesWholeText(String regex, String text) {
        return LinearRegex.compileWholeText(regex) instanceof Regex compiled && LinearRegex.matches(compiled, text);
    }

    private static String refusal(String regex) {
        return LinearRegex.compileWholeText(regex) instanceof RegexError error ? LinearRegex.describe(error)
                : "accepted";
    }

    @Nested
    @DisplayName("Hostile input")
    class HostileInput {

        @Timeout(10)
        @MethodSource
        @ParameterizedTest(name = "{0}")
        @DisplayName("a pattern that backtracking engines take exponential time for answers in linear time")
        void whenPatternWouldBacktrackCatastrophicallyThenAnswersQuickly(String description, String regex,
                String input) {
            assertThat(matchesWholeText(regex, input)).isFalse();
        }

        static Stream<Arguments> whenPatternWouldBacktrackCatastrophicallyThenAnswersQuickly() {
            return Stream.of(arguments("nested plus", "(a+)+c", "a".repeat(60_000) + "b"),
                    arguments("nested star over alternatives", "(a|aa)*c", "a".repeat(60_000) + "b"),
                    arguments("repeated wildcard groups", "(.*,){30}P", "1,".repeat(10_000)),
                    arguments("overlapping classes", "(\\w|\\d)+!", "1".repeat(60_000)));
        }

        @Timeout(10)
        @ValueSource(strings = { "(?:a{1000}){1000}", "(?:(?:a{1000}){1000}){1000}", "a{100000}", "\\p{L}{1,1000}",
                "\\w{1,1000}", "(?:\\w{100}){10}" })
        @ParameterizedTest(name = "{0}")
        @DisplayName("a pattern whose program would exceed the size limit is refused as too large")
        void whenPatternExpandsBeyondTheLimitThenRefusedAsTooLarge(String regex) {
            assertThat(refusal(regex)).isEqualTo(LinearRegex.ERROR_EXPRESSION_TOO_LARGE);
        }

        @Timeout(10)
        @ValueSource(ints = { 65, 100_000 })
        @ParameterizedTest(name = "{0} levels")
        @DisplayName("a pattern whose groups nest too deeply is refused without exhausting the stack")
        void whenGroupsNestTooDeeplyThenRefused(int depth) {
            assertThat(refusal("(".repeat(depth) + "a" + ")".repeat(depth)))
                    .isIn(LinearRegex.ERROR_EXPRESSION_NESTS_TOO_DEEPLY, LinearRegex.ERROR_EXPRESSION_TOO_LONG);
        }

        @Test
        @DisplayName("a pattern longer than the pattern limit is refused as too long")
        void whenPatternIsLongerThanTheLimitThenRefusedAsTooLong() {
            assertThat(refusal("a".repeat(LinearRegex.LIMITS.maxPatternBytes() + 1)))
                    .isEqualTo(LinearRegex.ERROR_EXPRESSION_TOO_LONG);
        }

        @MethodSource
        @ParameterizedTest(name = "{0}")
        @DisplayName("a text the engine cannot search fails the match instead of matching")
        void whenTextCannotBeSearchedThenMatchFails(String description, String text, String reason) {
            final var regex = (Regex) LinearRegex.compileWholeText("[a-z]*");
            assertThat(regex.isMatch(text)).isInstanceOfSatisfying(RegexError.class,
                    error -> assertThat(LinearRegex.describe(error)).isEqualTo(reason));
        }

        static Stream<Arguments> whenTextCannotBeSearchedThenMatchFails() {
            return Stream.of(
                    arguments("a text above the text limit", "a".repeat(LinearRegex.LIMITS.maxTextBytes() + 1),
                            LinearRegex.ERROR_TEXT_TOO_LONG),
                    arguments("an unpaired surrogate", "a\uD800b", LinearRegex.ERROR_MALFORMED_UNICODE));
        }
    }

    @Nested
    @DisplayName("Patterns within the limits")
    class WithinLimits {

        @MethodSource
        @ParameterizedTest(name = "{0}")
        @DisplayName("a pattern within the limits is compiled and matches the whole text")
        void whenPatternWithinLimitsThenMatches(String description, String regex, String input, boolean expected) {
            assertThat(matchesWholeText(regex, input)).isEqualTo(expected);
        }

        static Stream<Arguments> whenPatternWithinLimitsThenMatches() {
            return Stream.of(arguments("a large single repetition", "a{1000}", "a".repeat(1000), true),
                    arguments("nested repetitions", "(?:a{100}){20}", "a".repeat(2000), true),
                    arguments("a bounded class repetition", "[a-z]{1,1000}", "abc", true),
                    arguments("a bounded Unicode category", "\\p{L}{1,4}", "été", true),
                    arguments("an unbounded Unicode category", "\\p{L}+", "été", true),
                    arguments("a host name", "(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.){1,10}[a-z]{2,63}",
                            "api.example.com", true),
                    arguments("groups nested to the limit", "(".repeat(64) + "a" + ")".repeat(64), "a", true),
                    arguments("an escaped brace is a literal", "a\\{1000}", "a{1000}", true),
                    arguments("braces in a class are literals", "[{}0-9]{3}", "{1}", true),
                    arguments("named groups", "(?P<user>[a-z]+):(?<role>[a-z]+)", "alice:admin", true),
                    arguments("flags", "(?i)ADMIN", "admin", true), arguments("a flag group", "(?i:A)b", "ab", true),
                    arguments("the whole text must match", "admin", "administrator", false),
                    arguments("an alternative that matches a prefix only", "a|ab", "ab", true),
                    arguments("a text that ends with a line feed does not match $", "a$", "a\n", false));
        }

        @MethodSource
        @ParameterizedTest(name = "{0}")
        @DisplayName("the classes \\d, \\w, \\s and the word boundary are Unicode-aware unless (?-u) restricts them")
        void whenClassIsUsedThenUnicodeAwareUnlessRestricted(String description, String regex, String input,
                boolean expected) {
            assertThat(matchesWholeText(regex, input)).isEqualTo(expected);
        }

        static Stream<Arguments> whenClassIsUsedThenUnicodeAwareUnlessRestricted() {
            return Stream.of(arguments("an Arabic-Indic digit is a digit", "\\d", "\u0663", true),
                    arguments("an Arabic-Indic digit is no ASCII digit", "(?-u:\\d)", "\u0663", false),
                    arguments("a letter with accent is a word character", "\\w+", "été", true),
                    arguments("a letter with accent is no ASCII word character", "(?-u:\\w)+", "été", false),
                    arguments("a no-break space is white space", "\\s", "\u00a0", true),
                    arguments("[0-9] holds ASCII digits only", "[0-9]", "\u0663", false));
        }

        @MethodSource
        @ParameterizedTest(name = "{0}")
        @DisplayName("an invalid pattern is refused with the reason the parser gives")
        void whenPatternInvalidThenRefusedWithReason(String description, String regex, String reason) {
            assertThat(refusal(regex)).isEqualTo(reason);
        }

        static Stream<Arguments> whenPatternInvalidThenRefusedWithReason() {
            return Stream.of(arguments("an unclosed group", "(", "unclosed group"),
                    arguments("an unclosed class", "[a", "unclosed character class"),
                    arguments("a look-ahead", "a(?=b)",
                            "look-around, including look-ahead and look-behind, is not supported"),
                    arguments("a backreference", "(a)\\1", "backreferences are not supported"),
                    arguments("a quoted section of java.util.regex", "\\Qa\\E", "unrecognized escape sequence"),
                    arguments("a stray closing parenthesis", "a)", "unopened group"));
        }
    }

    @Nested
    @DisplayName("Patterns where an interface demands an exception")
    class RefusingPatterns {

        @Test
        @DisplayName("a valid pattern is compiled")
        void whenPatternIsValidThenCompiled() {
            assertThat(LinearRegex.matches(LinearRegex.compileWholeTextOrRefuse("[a-z]+"), "abc")).isTrue();
        }

        @Test
        @DisplayName("an invalid pattern is refused with the reason, as an illegal argument")
        void whenPatternIsInvalidThenRefused() {
            assertThatThrownBy(() -> LinearRegex.compileOrRefuse("(")).isInstanceOf(IllegalArgumentException.class)
                    .isInstanceOfSatisfying(RegexRefusedException.class,
                            refusal -> assertThat(refusal.getDescription()).isEqualTo("unclosed group"));
        }

        @ValueSource(strings = { "a\uD800", "too long" })
        @ParameterizedTest(name = "{0}")
        @DisplayName("a text the engine cannot search is neither a match nor a non-match")
        void whenTextCannotBeSearchedThenMatchingFails(String kind) {
            val regex = LinearRegex.compileOrRefuse("a");
            val text  = "too long".equals(kind) ? "a".repeat(LinearRegex.LIMITS.maxTextBytes() + 1) : kind;
            assertThatThrownBy(() -> LinearRegex.matches(regex, text)).isInstanceOf(RegexSearchFailedException.class);
        }

        @Test
        @DisplayName("every error kind has a description")
        void whenAnyErrorKindIsDescribedThenDescriptionIsNotBlank() {
            for (val kind : RegexErrorKind.values()) {
                assertThat(LinearRegex.describe(new RegexError(kind, "error: detail"))).isNotBlank();
            }
        }
    }
}
