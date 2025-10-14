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

import io.sapl.api.interpreter.Val;
import lombok.val;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PatternsFunctionLibraryTests {

    private static void assertGlobMatches(String pattern, String value, boolean expected, String... delimiters) {
        val delimiterVals = new Val[delimiters.length];
        for (int i = 0; i < delimiters.length; i++) {
            delimiterVals[i] = Val.of(delimiters[i]);
        }
        val result = PatternsFunctionLibrary.matchGlob(Val.of(pattern), Val.of(value), delimiterVals);
        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isEqualTo(expected);
    }

    private static void assertGlobWithoutDelimitersMatches(String pattern, String value, boolean expected) {
        val result = PatternsFunctionLibrary.matchGlobWithoutDelimiters(Val.of(pattern), Val.of(value));
        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isEqualTo(expected);
    }

    private static void assertGlobError(String pattern, String value, String errorFragment, String... delimiters) {
        val delimiterVals = new Val[delimiters.length];
        for (int i = 0; i < delimiters.length; i++) {
            delimiterVals[i] = Val.of(delimiters[i]);
        }
        val result = PatternsFunctionLibrary.matchGlob(Val.of(pattern), Val.of(value), delimiterVals);
        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains(errorFragment);
    }

    @Nested
    class GlobMatchingWithDelimitersTests {

        @Test
        void whenMatchingWithDefaultDelimiter_thenRespectsDotBoundaries() {
            assertGlobMatches("*.github.com", "api.github.com", true);
            assertGlobMatches("*.github.com", "api.cdn.github.com", false);
            assertGlobMatches("**.github.com", "api.cdn.github.com", true);
        }

        @Test
        void whenMatchingWithCustomDelimiter_thenRespectsCustomBoundaries() {
            assertGlobMatches("user:*:read", "user:admin:read", true, ":");
            assertGlobMatches("user:*:read", "user:admin:data:read", false, ":");
            assertGlobMatches("user:**:read", "user:admin:data:read", true, ":");
        }

        @Test
        void whenMatchingWithMultipleDelimiters_thenRespectsAllBoundaries() {
            assertGlobMatches("a*c", "a.b:c", false, ".", ":");
            assertGlobMatches("a*c", "abc", true, ".", ":");
            assertGlobMatches("a**c", "a.b:c", true, ".", ":");
        }

        @ParameterizedTest
        @CsvSource({
                "cat, cat, true",
                "cat, dog, false",
                "c?t, cat, true",
                "c?t, ct, false",
                "c?t, coat, false"
        })
        void whenMatchingSingleCharWildcard_thenMatchesExactlyOneChar(String pattern, String value, boolean expected) {
            assertGlobMatches(pattern, value, expected);
        }

        @Test
        void whenMatchingCharacterClass_thenMatchesFromSet() {
            assertGlobMatches("[abc]at", "bat", true);
            assertGlobMatches("[abc]at", "cat", true);
            assertGlobMatches("[abc]at", "lat", false);
            assertGlobMatches("[abc]at", "at", false);
        }

        @Test
        void whenMatchingNegatedCharacterClass_thenMatchesOutsideSet() {
            assertGlobMatches("[!abc]at", "cat", false);
            assertGlobMatches("[!abc]at", "lat", true);
            assertGlobMatches("[!abc]at", "dat", true);
        }

        @Test
        void whenMatchingCharacterRange_thenMatchesWithinRange() {
            assertGlobMatches("[a-c]at", "cat", true);
            assertGlobMatches("[a-c]at", "bat", true);
            assertGlobMatches("[a-c]at", "lat", false);
            assertGlobMatches("[0-9]*.txt", "5file.txt", true);
            assertGlobMatches("[0-9]*.txt", "file.txt", false);
        }

        @Test
        void whenMatchingNegatedCharacterRange_thenMatchesOutsideRange() {
            assertGlobMatches("[!a-c]at", "cat", false);
            assertGlobMatches("[!a-c]at", "lat", true);
            assertGlobMatches("[!0-9]*.txt", "file.txt", true);
            assertGlobMatches("[!0-9]*.txt", "5file.txt", false);
        }

        @Test
        void whenMatchingAlternatives_thenMatchesAnyAlternative() {
            assertGlobMatches("{cat,bat,[fr]at}", "cat", true);
            assertGlobMatches("{cat,bat,[fr]at}", "bat", true);
            assertGlobMatches("{cat,bat,[fr]at}", "rat", true);
            assertGlobMatches("{cat,bat,[fr]at}", "fat", true);
            assertGlobMatches("{cat,bat,[fr]at}", "at", false);
            assertGlobMatches("{cat,bat,[fr]at}", "mat", false);
        }

        @Test
        void whenMatchingWithEscape_thenTreatMetacharAsLiteral() {
            assertGlobMatches("file\\*.txt", "file*.txt", true);
            assertGlobMatches("file\\*.txt", "file123.txt", false);
            assertGlobMatches("test\\?mark", "test?mark", true);
            assertGlobMatches("test\\?mark", "testXmark", false);
            assertGlobMatches("data\\[1\\]", "data[1]", true);
            assertGlobMatches("data\\[1\\]", "data1", false);
        }

        @Test
        void whenMatchingComplexPattern_thenHandlesAllFeatures() {
            assertGlobMatches("*.{jpg,png,gif}", "photo.jpg", true);
            assertGlobMatches("*.{jpg,png,gif}", "image.png", true);
            assertGlobMatches("*.{jpg,png,gif}", "file.txt", false);
            assertGlobMatches("[0-9][0-9]-*.log", "12-error.log", true);
            assertGlobMatches("[0-9][0-9]-*.log", "1-error.log", false);
        }

        @Test
        void whenMatchingEmptyPattern_thenMatchesOnlyEmptyString() {
            assertGlobMatches("", "", true);
            assertGlobMatches("", "anything", false);
        }

        @Test
        void whenMatchingPatternWithOnlyWildcard_thenMatchesAnythingWithinSegment() {
            assertGlobMatches("*", "anything", true);
            assertGlobMatches("*", "with.dot", false);
            assertGlobMatches("**", "with.dot", true);
        }
    }

    @Nested
    class GlobMatchingWithoutDelimitersTests {

        @Test
        void whenMatchingWithoutDelimiters_thenWildcardMatchesEverything() {
            assertGlobWithoutDelimitersMatches("*hub.com", "api.cdn.github.com", true);
            assertGlobWithoutDelimitersMatches("*.github.com", "api.github.com", true);
            assertGlobWithoutDelimitersMatches("user:*:read", "user:admin:data:extra:read", true);
        }

        @Test
        void whenMatchingSingleCharWildcard_thenMatchesAnyCharacter() {
            assertGlobWithoutDelimitersMatches("file-[0-9]??.txt", "file-5xy.txt", true);
            assertGlobWithoutDelimitersMatches("a?c", "abc", true);
            assertGlobWithoutDelimitersMatches("a?c", "a:c", true);
        }

        @Test
        void whenMatchingCharacterClasses_thenWorksWithoutDelimiters() {
            assertGlobWithoutDelimitersMatches("[a-z]+", "abc", false);
            assertGlobWithoutDelimitersMatches("[0-9]*", "123", true);
            assertGlobWithoutDelimitersMatches("[!0-9]*", "abc", true);
        }

        @Test
        void whenMatchingAlternatives_thenWorksWithoutDelimiters() {
            assertGlobWithoutDelimitersMatches("{*.jpg,*.png}", "photo.jpg", true);
            assertGlobWithoutDelimitersMatches("{user:*,admin:*}", "user:alice:read", true);
        }
    }

    @Nested
    class GlobEscapingTests {

        @Test
        void whenEscapingGlobMetachars_thenEscapesAllSpecialChars() {
            val result = PatternsFunctionLibrary.escapeGlob(Val.of("alice*bob"));
            assertThat(result.getText()).isEqualTo("alice\\*bob");

            val result2 = PatternsFunctionLibrary.escapeGlob(Val.of("test?mark"));
            assertThat(result2.getText()).isEqualTo("test\\?mark");

            val result3 = PatternsFunctionLibrary.escapeGlob(Val.of("data[1]"));
            assertThat(result3.getText()).isEqualTo("data\\[1\\]");
        }

        @Test
        void whenEscapingAllMetachars_thenEscapesCompletely() {
            val input  = "*?[]{}\\-!";
            val result = PatternsFunctionLibrary.escapeGlob(Val.of(input));
            assertThat(result.getText()).isEqualTo("\\*\\?\\[\\]\\{\\}\\\\\\-\\!");
        }

        @Test
        void whenEscapingAndUsedInPattern_thenMatchesLiterally() {
            val userName = "alice*bob";
            val escaped  = PatternsFunctionLibrary.escapeGlob(Val.of(userName));
            val pattern  = escaped.getText() + ":*:read";

            assertGlobMatches(pattern, "alice*bob:data:read", true, ":");
            assertGlobMatches(pattern, "aliceXbob:data:read", false, ":");
        }

        @Test
        void whenEscapingNonTextValue_thenReturnsError() {
            val result = PatternsFunctionLibrary.escapeGlob(Val.of(42));
            assertThat(result.isError()).isTrue();
            assertThat(result.getMessage()).contains("text value");
        }

        @Test
        void whenEscapingEmptyString_thenReturnsEmptyString() {
            val result = PatternsFunctionLibrary.escapeGlob(Val.of(""));
            assertThat(result.getText()).isEmpty();
        }

        @Test
        void whenEscapingStringWithoutMetachars_thenReturnsUnchanged() {
            val result = PatternsFunctionLibrary.escapeGlob(Val.of("simple"));
            assertThat(result.getText()).isEqualTo("simple");
        }
    }

    @Nested
    class GlobEdgeCasesTests {

        @Test
        void whenPatternTooLong_thenReturnsError() {
            val longPattern = "*".repeat(1001);
            assertGlobError(longPattern, "test", "Pattern too long");
        }

        @Test
        void whenValueTooLong_thenReturnsError() {
            val longValue = "a".repeat(100001);
            assertGlobError("*", longValue, "Input too long");
        }

        @Test
        void whenInvalidInputType_thenReturnsError() {
            val result = PatternsFunctionLibrary.matchGlob(Val.of(42), Val.of("test"));
            assertThat(result.isError()).isTrue();
            assertThat(result.getMessage()).contains("text values");
        }

        @Test
        void whenEmptyDelimiter_thenIgnoresIt() {
            assertGlobMatches("a*c", "a.b.c", true, "");
        }

        @Test
        void whenMultiCharDelimiter_thenRespectsIt() {
            assertGlobMatches("a*c", "a::b::c", false, "::");
            assertGlobMatches("a**c", "a::b::c", true, "::");
        }

        @Test
        void whenNestedAlternatives_thenHandlesCorrectly() {
            assertGlobMatches("{a,{b,c}}", "a", true);
            assertGlobMatches("{a,{b,c}}", "b", true);
            assertGlobMatches("{a,{b,c}}", "c", true);
        }

        @Test
        void whenUnclosedBracket_thenDoesNotMatch() {
            assertGlobMatches("[abc", "a", false);
            assertGlobMatches("test[", "test[", true);
        }

        @Test
        void whenUnclosedBrace_thenDoesNotMatch() {
            assertGlobMatches("{abc", "abc", false);
            assertGlobMatches("test{", "test{", true);
        }

        @Test
        void whenEscapeAtEnd_thenTreatsAsLiteral() {
            assertGlobMatches("test\\", "test\\", true);
        }
    }

    @Nested
    class RegexValidationTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "[a-z]+",
                "\\d{3}-\\d{4}",
                "^test$",
                "(?<name>\\w+)",
                "(a|b|c)"
        })
        void whenValidPattern_thenReturnsTrue(String pattern) {
            val result = PatternsFunctionLibrary.isValidRegex(Val.of(pattern));
            assertThat(result.getBoolean()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "[a-z",
                "(?<name>",
                "\\",
                "*",
                "(?P<invalid)"
        })
        void whenInvalidPattern_thenReturnsFalse(String pattern) {
            val result = PatternsFunctionLibrary.isValidRegex(Val.of(pattern));
            assertThat(result.getBoolean()).isFalse();
        }

        @Test
        void whenPatternTooLong_thenReturnsFalse() {
            val longPattern = "a".repeat(1001);
            val result      = PatternsFunctionLibrary.isValidRegex(Val.of(longPattern));
            assertThat(result.getBoolean()).isFalse();
        }

        @Test
        void whenNonTextValue_thenReturnsFalse() {
            val result = PatternsFunctionLibrary.isValidRegex(Val.of(42));
            assertThat(result.getBoolean()).isFalse();
        }
    }

    @Nested
    class FindMatchesTests {

        @Test
        void whenFindingAllMatches_thenReturnsAllOccurrences() {
            val result = PatternsFunctionLibrary.findMatches(
                    Val.of("[a-z]+@[a-z]+\\.com"),
                    Val.of("Contact: alice@example.com or bob@test.com")
            );
            assertThat(result.isArray()).isTrue();
            val array = result.getArrayNode();
            assertThat(array).hasSize(2);
            assertThat(array.get(0).asText()).isEqualTo("alice@example.com");
            assertThat(array.get(1).asText()).isEqualTo("bob@test.com");
        }

        @Test
        void whenFindingLimitedMatches_thenReturnsUpToLimit() {
            val result = PatternsFunctionLibrary.findMatches(
                    Val.of("\\d+"),
                    Val.of("1 2 3 4 5"),
                    Val.of(2)
            );
            assertThat(result.isArray()).isTrue();
            val array = result.getArrayNode();
            assertThat(array).hasSize(2);
            assertThat(array.get(0).asText()).isEqualTo("1");
            assertThat(array.get(1).asText()).isEqualTo("2");
        }

        @Test
        void whenNoMatches_thenReturnsEmptyArray() {
            val result = PatternsFunctionLibrary.findMatches(
                    Val.of("\\d+"),
                    Val.of("no numbers here")
            );
            assertThat(result.isArray()).isTrue();
            assertThat(result.getArrayNode()).isEmpty();
        }

        @Test
        void whenPatternInvalid_thenReturnsError() {
            val result = PatternsFunctionLibrary.findMatches(
                    Val.of("[invalid"),
                    Val.of("test")
            );
            assertThat(result.isError()).isTrue();
        }

        @Test
        void whenLimitNegative_thenReturnsError() {
            val result = PatternsFunctionLibrary.findMatches(
                    Val.of("\\d+"),
                    Val.of("123"),
                    Val.of(-1)
            );
            assertThat(result.isError()).isTrue();
        }

        @Test
        void whenLimitExceedsMax_thenCapsAtMax() {
            val manyMatches = ".".repeat(50000);
            val result      = PatternsFunctionLibrary.findMatches(
                    Val.of("."),
                    Val.of(manyMatches),
                    Val.of(20000)
            );
            assertThat(result.isArray()).isTrue();
            assertThat(result.getArrayNode().size()).isEqualTo(10000);
        }
    }

    @Nested
    class FindAllSubmatchTests {

        @Test
        void whenFindingSubmatchesWithGroups_thenReturnsCaptureGroups() {
            val result = PatternsFunctionLibrary.findAllSubmatch(
                    Val.of("([a-z]+)@([a-z]+)\\.com"),
                    Val.of("alice@example.com bob@test.com")
            );
            assertThat(result.isArray()).isTrue();
            val array = result.getArrayNode();
            assertThat(array).hasSize(2);

            val firstMatch = array.get(0);
            assertThat(firstMatch.isArray()).isTrue();
            assertThat(firstMatch.get(0).asText()).isEqualTo("alice@example.com");
            assertThat(firstMatch.get(1).asText()).isEqualTo("alice");
            assertThat(firstMatch.get(2).asText()).isEqualTo("example");

            val secondMatch = array.get(1);
            assertThat(secondMatch.get(0).asText()).isEqualTo("bob@test.com");
            assertThat(secondMatch.get(1).asText()).isEqualTo("bob");
            assertThat(secondMatch.get(2).asText()).isEqualTo("test");
        }

        @Test
        void whenFindingSubmatchesWithOptionalGroup_thenHandlesNull() {
            val result = PatternsFunctionLibrary.findAllSubmatch(
                    Val.of("(\\d+)-(\\d+)?"),
                    Val.of("10-20 30- 40")
            );
            assertThat(result.isArray()).isTrue();
            val array = result.getArrayNode();
            assertThat(array.size()).isGreaterThanOrEqualTo(2);

            val firstMatch = array.get(0);
            assertThat(firstMatch.get(0).asText()).isEqualTo("10-20");
            assertThat(firstMatch.get(1).asText()).isEqualTo("10");
            assertThat(firstMatch.get(2).asText()).isEqualTo("20");

            val secondMatch = array.get(1);
            assertThat(secondMatch.get(0).asText()).isEqualTo("30-");
            assertThat(secondMatch.get(1).asText()).isEqualTo("30");
            assertThat(secondMatch.get(2).isNull()).isTrue();
        }

        @Test
        void whenFindingSubmatchesWithLimit_thenReturnsUpToLimit() {
            val result = PatternsFunctionLibrary.findAllSubmatch(
                    Val.of("(\\d+)-(\\d+)"),
                    Val.of("10-20, 30-40, 50-60"),
                    Val.of(1)
            );
            assertThat(result.isArray()).isTrue();
            val array = result.getArrayNode();
            assertThat(array).hasSize(1);
        }

        @Test
        void whenNoGroups_thenReturnsOnlyFullMatch() {
            val result = PatternsFunctionLibrary.findAllSubmatch(
                    Val.of("\\d+"),
                    Val.of("10 20 30")
            );
            assertThat(result.isArray()).isTrue();
            val array = result.getArrayNode();
            assertThat(array.get(0).size()).isEqualTo(1);
        }
    }

    @Nested
    class ReplaceAllTests {

        @Test
        void whenReplacingSimplePattern_thenReplacesAllOccurrences() {
            val result = PatternsFunctionLibrary.replaceAll(
                    Val.of("alice@example.com, bob@test.com"),
                    Val.of("[a-z]+@[a-z]+\\.com"),
                    Val.of("[REDACTED]")
            );
            assertThat(result.getText()).isEqualTo("[REDACTED], [REDACTED]");
        }

        @Test
        void whenReplacingWithCaptureGroups_thenSubstitutesGroups() {
            val result = PatternsFunctionLibrary.replaceAll(
                    Val.of("John Doe, Jane Smith"),
                    Val.of("(\\w+) (\\w+)"),
                    Val.of("$2, $1")
            );
            assertThat(result.getText()).isEqualTo("Doe, John, Smith, Jane");
        }

        @Test
        void whenReplacingWhitespace_thenNormalizes() {
            val result = PatternsFunctionLibrary.replaceAll(
                    Val.of("  hello   world  "),
                    Val.of("\\s+"),
                    Val.of(" ")
            );
            assertThat(result.getText()).isEqualTo(" hello world ");
        }

        @Test
        void whenNoMatches_thenReturnsOriginal() {
            val result = PatternsFunctionLibrary.replaceAll(
                    Val.of("no numbers"),
                    Val.of("\\d+"),
                    Val.of("X")
            );
            assertThat(result.getText()).isEqualTo("no numbers");
        }

        @Test
        void whenReplacementInvalid_thenReturnsError() {
            val result = PatternsFunctionLibrary.replaceAll(
                    Val.of("test"),
                    Val.of("test"),
                    Val.of(42)
            );
            assertThat(result.isError()).isTrue();
        }
    }

    @Nested
    class SplitTests {

        @Test
        void whenSplittingByComma_thenReturnsArray() {
            val result = PatternsFunctionLibrary.split(
                    Val.of(",\\s*"),
                    Val.of("apple, banana,cherry,  date")
            );
            assertThat(result.isArray()).isTrue();
            val array = result.getArrayNode();
            assertThat(array).hasSize(4);
            assertThat(array.get(0).asText()).isEqualTo("apple");
            assertThat(array.get(1).asText()).isEqualTo("banana");
            assertThat(array.get(2).asText()).isEqualTo("cherry");
            assertThat(array.get(3).asText()).isEqualTo("date");
        }

        @Test
        void whenSplittingByWhitespace_thenIgnoresMultiple() {
            val result = PatternsFunctionLibrary.split(
                    Val.of("\\s+"),
                    Val.of("hello   world  test")
            );
            assertThat(result.isArray()).isTrue();
            val array = result.getArrayNode();
            assertThat(array.get(0).asText()).isEqualTo("hello");
            assertThat(array.get(1).asText()).isEqualTo("world");
            assertThat(array.get(2).asText()).isEqualTo("test");
        }

        @Test
        void whenSplittingByAlternatives_thenSplitsOnEither() {
            val result = PatternsFunctionLibrary.split(
                    Val.of("[|:]"),
                    Val.of("name:John|age:30|city:NYC")
            );
            assertThat(result.isArray()).isTrue();
            val array = result.getArrayNode();
            assertThat(array.get(0).asText()).isEqualTo("name");
            assertThat(array.get(1).asText()).isEqualTo("John");
        }

        @Test
        void whenNoMatches_thenReturnsOriginalAsArray() {
            val result = PatternsFunctionLibrary.split(
                    Val.of(","),
                    Val.of("noseparator")
            );
            assertThat(result.isArray()).isTrue();
            val array = result.getArrayNode();
            assertThat(array).hasSize(1);
            assertThat(array.get(0).asText()).isEqualTo("noseparator");
        }
    }

    @Nested
    class TemplateMatchingTests {

        @Test
        void whenMatchingSimpleTemplate_thenExtractsRegexParts() {
            val result = PatternsFunctionLibrary.matchTemplate(
                    Val.of("user-{\\d+}-profile"),
                    Val.of("user-12345-profile"),
                    Val.of("{"),
                    Val.of("}")
            );
            assertThat(result.getBoolean()).isTrue();
        }

        @Test
        void whenTemplateDoesNotMatch_thenReturnsFalse() {
            val result = PatternsFunctionLibrary.matchTemplate(
                    Val.of("prefix-{\\d+}-suffix"),
                    Val.of("prefix-abc-suffix"),
                    Val.of("{"),
                    Val.of("}")
            );
            assertThat(result.getBoolean()).isFalse();
        }

        @Test
        void whenTemplateWithMultiplePatterns_thenMatchesAll() {
            val result = PatternsFunctionLibrary.matchTemplate(
                    Val.of("{[a-z]+}@{[a-z]+}\\.com"),
                    Val.of("admin@example.com"),
                    Val.of("{"),
                    Val.of("}")
            );
            assertThat(result.getBoolean()).isTrue();
        }

        @Test
        void whenUsingCustomDelimiters_thenWorks() {
            val result = PatternsFunctionLibrary.matchTemplate(
                    Val.of("/api/<version>/users/<id>"),
                    Val.of("/api/v2/users/42"),
                    Val.of("<"),
                    Val.of(">")
            );
            assertThat(result.getBoolean()).isFalse();

            val result2 = PatternsFunctionLibrary.matchTemplate(
                    Val.of("/api/<\\w+>/users/<\\d+>"),
                    Val.of("/api/v2/users/42"),
                    Val.of("<"),
                    Val.of(">")
            );
            assertThat(result2.getBoolean()).isTrue();
        }

        @Test
        void whenDelimiterEmpty_thenReturnsError() {
            val result = PatternsFunctionLibrary.matchTemplate(
                    Val.of("test"),
                    Val.of("test"),
                    Val.of(""),
                    Val.of("}")
            );
            assertThat(result.isError()).isTrue();
            assertThat(result.getMessage()).contains("empty");
        }

        @Test
        void whenDelimiterMismatch_thenReturnsError() {
            val result = PatternsFunctionLibrary.matchTemplate(
                    Val.of("test{pattern"),
                    Val.of("test123"),
                    Val.of("{"),
                    Val.of("}")
            );
            assertThat(result.isError()).isTrue();
            assertThat(result.getMessage()).contains("mismatched");
        }

        @Test
        void whenRegexInTemplateInvalid_thenReturnsError() {
            val result = PatternsFunctionLibrary.matchTemplate(
                    Val.of("test{[invalid}"),
                    Val.of("test123"),
                    Val.of("{"),
                    Val.of("}")
            );
            assertThat(result.isError()).isTrue();
        }
    }

    @Nested
    class DoSProtectionTests {

        @Test
        void whenPatternTooLong_thenReturnsError() {
            val longPattern = "a".repeat(1001);
            val result      = PatternsFunctionLibrary.findMatches(Val.of(longPattern), Val.of("test"));
            assertThat(result.isError()).isTrue();
            assertThat(result.getMessage()).contains("too long");
        }

        @Test
        void whenInputTooLong_thenReturnsError() {
            val longInput = "a".repeat(100001);
            val result    = PatternsFunctionLibrary.findMatches(Val.of("a+"), Val.of(longInput));
            assertThat(result.isError()).isTrue();
            assertThat(result.getMessage()).contains("too long");
        }

        @Test
        @Timeout(value = 1, unit = TimeUnit.SECONDS)
        void whenPatternHasNestedQuantifiers_thenReturnsError() {
            val dangerousPattern = "(a+)+";
            val input            = "aaaaaaaaaaaaaaaaaaaaX";
            val result           = PatternsFunctionLibrary.findMatches(Val.of(dangerousPattern), Val.of(input));
            assertThat(result.isError()).isTrue();
            assertThat(result.getMessage()).contains("dangerous");
        }

        @Test
        @Timeout(value = 1, unit = TimeUnit.SECONDS)
        void whenPatternHasManyAlternations_thenReturnsError() {
            val alternatives = "(" + "a|".repeat(101) + "b)";
            val result       = PatternsFunctionLibrary.findMatches(Val.of(alternatives), Val.of("test"));
            assertThat(result.isError()).isTrue();
            assertThat(result.getMessage()).contains("dangerous");
        }

        @Test
        void whenMatchCountExceedsMax_thenCapsAtMax() {
            val manyAs = "a".repeat(20000);
            val result = PatternsFunctionLibrary.findMatches(Val.of("a"), Val.of(manyAs));
            assertThat(result.isArray()).isTrue();
            assertThat(result.getArrayNode().size()).isEqualTo(10000);
        }

        @Test
        @Timeout(value = 1, unit = TimeUnit.SECONDS)
        void whenGlobPatternComplex_thenCompletesQuickly() {
            val complexPattern = "{a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p}*{1,2,3,4,5}";
            val result         = PatternsFunctionLibrary.matchGlob(Val.of(complexPattern), Val.of("test"));
            assertThat(result.isBoolean()).isTrue();
        }
    }

    @Nested
    class ErrorHandlingTests {

        @Test
        void whenPatternIsNull_thenReturnsError() {
            val result = PatternsFunctionLibrary.findMatches(Val.NULL, Val.of("test"));
            assertThat(result.isError()).isTrue();
        }

        @Test
        void whenValueIsNull_thenReturnsError() {
            val result = PatternsFunctionLibrary.findMatches(Val.of("test"), Val.NULL);
            assertThat(result.isError()).isTrue();
        }

        @Test
        void whenPatternIsNumber_thenReturnsError() {
            val result = PatternsFunctionLibrary.matchGlob(Val.of(123), Val.of("test"));
            assertThat(result.isError()).isTrue();
        }

        @Test
        void whenAllArgsInvalid_thenReturnsError() {
            val result = PatternsFunctionLibrary.matchTemplate(
                    Val.of(1),
                    Val.of(2),
                    Val.of(3),
                    Val.of(4)
            );
            assertThat(result.isError()).isTrue();
        }
    }

    @Nested
    class SecurityAndEdgeCaseTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "[*",
                "[+",
                "[.",
                "[^",
                "[$",
                "[(a+)+]"
        })
        void whenCharacterClassContainsRegexMetachars_thenHandlesSafely(String pattern) {
            val result = PatternsFunctionLibrary.matchGlob(Val.of(pattern), Val.of("test"));
            assertThat(result.isBoolean() || result.isError()).isTrue();
        }

        @Test
        void whenDeeplyNestedAlternatives_thenDoesNotStackOverflow() {
            val pattern = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                pattern.append("{");
            }
            pattern.append("a");
            for (int i = 0; i < 100; i++) {
                pattern.append("}");
            }

            val result = PatternsFunctionLibrary.matchGlob(Val.of(pattern.toString()), Val.of("a"));
            assertThat(result.isBoolean() || result.isError()).isTrue();
        }

        @Test
        void whenThousandsOfAlternatives_thenHandlesOrRejects() {
            val alternatives = new StringBuilder("{");
            for (int i = 0; i < 5000; i++) {
                if (i > 0) alternatives.append(',');
                alternatives.append('a').append(i);
            }
            alternatives.append('}');

            val result = PatternsFunctionLibrary.matchGlob(Val.of(alternatives.toString()), Val.of("a2500"));
            assertThat(result.isBoolean() || result.isError()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "[z-a]",
                "[9-0]",
                "[Z-A]"
        })
        void whenInvalidCharacterRange_thenHandlesSafely(String pattern) {
            val result = PatternsFunctionLibrary.matchGlob(Val.of(pattern), Val.of("test"));
            assertThat(result.isBoolean() || result.isError()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "[]",
                "[^]",
                "[-]",
                "[!]"
        })
        void whenEmptyOrEdgeCharacterClass_thenHandlesSafely(String pattern) {
            val result = PatternsFunctionLibrary.matchGlob(Val.of(pattern), Val.of("test"));
            assertThat(result.isBoolean() || result.isError()).isTrue();
        }

        @Test
        void whenDelimiterContainsRegexSpecialChars_thenEscapesProperly() {
            assertGlobMatches("a*c", "a.b.c", false, ".");
            assertGlobMatches("a*c", "a$b$c", false, "$");
            assertGlobMatches("a*c", "a^b^c", false, "^");
            assertGlobMatches("a*c", "a(b)c", false, "(");
            assertGlobMatches("a*c", "a[b]c", false, "[");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "\u0000",
                "\n",
                "\r",
                "\t",
                "\u0001",
                "\u001F"
        })
        void whenPatternContainsControlCharacters_thenHandlesSafely(String controlChar) {
            val pattern = "test" + controlChar + "pattern";
            val result = PatternsFunctionLibrary.matchGlob(Val.of(pattern), Val.of(pattern));
            assertThat(result.isBoolean() || result.isError()).isTrue();
        }

        @Test
        void whenPatternContainsUnicodeCharacters_thenHandlesCorrectly() {
            assertGlobWithoutDelimitersMatches("café*", "café-file.txt", true);
            assertGlobWithoutDelimitersMatches("日本*", "日本語.txt", true);
            assertGlobWithoutDelimitersMatches("🎉*", "🎉party.txt", true);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "\\\\\\\\",
                "\\\\*\\\\",
                "\\\\?\\\\",
                "\\[\\]"
        })
        void whenMultipleEscapes_thenHandlesCorrectly(String pattern) {
            val result = PatternsFunctionLibrary.matchGlob(Val.of(pattern), Val.of("test"));
            assertThat(result.isBoolean()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "{}",
                "{,}",
                "{,,}",
                "{,,,,,}"
        })
        void whenEmptyAlternatives_thenHandlesSafely(String pattern) {
            val result = PatternsFunctionLibrary.matchGlob(Val.of(pattern), Val.of(""));
            assertThat(result.isBoolean()).isTrue();
        }

        @Test
        void whenSingleAlternative_thenMatchesNormally() {
            assertGlobMatches("{test}", "test", true);
            assertGlobMatches("{a}", "a", true);
            assertGlobMatches("{*}", "anything", true);
        }

        @Test
        void whenAlternativesWithWildcards_thenMatchesCorrectly() {
            assertGlobMatches("{*.jpg,*.png,*.gif}", "photo.jpg", true);
            assertGlobMatches("{test*,demo*}", "test123", true);
            assertGlobMatches("{a*b,c*d}", "aXYZb", true);
        }

        @Test
        void whenEscapedMetacharsInCharClass_thenTreatAsLiteral() {
            assertGlobMatches("[\\*]", "*", true);
            assertGlobMatches("[\\?]", "?", true);
            assertGlobMatches("[\\[]", "[", true);
            assertGlobMatches("[\\]]", "]", true);
        }

        @Test
        void whenTemplateContainsMaliciousRegex_thenValidatesOrRejects() {
            val result = PatternsFunctionLibrary.matchTemplate(
                    Val.of("user-{(a+)+}-profile"),
                    Val.of("user-aaaaaaaaaaaaaX-profile"),
                    Val.of("{"),
                    Val.of("}")
            );
            assertThat(result.isBoolean() || result.isError()).isTrue();
        }

        @Test
        void whenTemplateWithOverlappingDelimiters_thenHandlesSafely() {
            val result = PatternsFunctionLibrary.matchTemplate(
                    Val.of("<<test>>"),
                    Val.of("<<test>>"),
                    Val.of("<<"),
                    Val.of(">>")
            );
            assertThat(result.isBoolean() || result.isError()).isTrue();
        }

        @ParameterizedTest
        @MethodSource("repeatedPatternElements")
        void whenRepeatedPatternElements_thenHandlesEfficiently(String pattern) {
            val result = PatternsFunctionLibrary.matchGlob(Val.of(pattern), Val.of("test"));
            assertThat(result.isBoolean() || result.isError()).isTrue();
        }

        static java.util.stream.Stream<String> repeatedPatternElements() {
            return java.util.stream.Stream.of(
                    "*".repeat(100),
                    "?".repeat(100),
                    "[a-z]".repeat(50),
                    "{a,b}".repeat(50)
            );
        }

        @Test
        @Timeout(value = 2, unit = TimeUnit.SECONDS)
        void whenComplexGlobPattern_thenCompletesQuickly() {
            val pattern = "**/*{*.jpg,*.png,*.gif}[0-9][0-9]*";
            val value = "path/to/file/image123.jpg";
            val result = PatternsFunctionLibrary.matchGlob(Val.of(pattern), Val.of(value));
            assertThat(result.isBoolean()).isTrue();
        }

        @Test
        void whenPatternWithAllFeaturesCombined_thenWorks() {
            val pattern = "{*.jpg,test[0-9]??.txt,data\\*.csv}";
            assertGlobMatches(pattern, "photo.jpg", true);
            assertGlobMatches(pattern, "test5ab.txt", true);
            assertGlobMatches(pattern, "data*.csv", true);
            assertGlobMatches(pattern, "other.png", false);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                ".*",
                ".+",
                "^test$",
                "(a|b)"
        })
        void whenPatternLooksLikeRegex_thenTreatsAsGlob(String pattern) {
            val result = PatternsFunctionLibrary.matchGlob(Val.of(pattern), Val.of(pattern));
            assertThat(result.getBoolean()).isTrue();
        }

        @Test
        void whenPatternWithEscapedChars_thenMatchesLiteralChars() {
            assertGlobMatches("\\d+", "d+", true);
            assertGlobMatches("\\w*", "w", true);
            assertGlobMatches("\\w*", "wabc", true);
        }

        @Test
        void whenExtremelyLongCharacterClass_thenHandlesSafely() {
            val charClass = new StringBuilder("[");
            for (char c = 'a'; c <= 'z'; c++) {
                charClass.append(c).append('-').append(c);
            }
            charClass.append("]");

            val result = PatternsFunctionLibrary.matchGlob(Val.of(charClass.toString()), Val.of("a"));
            assertThat(result.isBoolean() || result.isError()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "***",
                "????",
                "[[[[",
                "{{{{",
                "\\\\\\\\",
                "----"
        })
        void whenRepeatedMetacharacters_thenHandlesSafely(String pattern) {
            val result = PatternsFunctionLibrary.matchGlob(Val.of(pattern), Val.of("test"));
            assertThat(result.isBoolean() || result.isError()).isTrue();
        }

        @Test
        void whenAlternativesContainComplexPatterns_thenMatchesCorrectly() {
            assertGlobMatches("{[a-z]*,[0-9]*,test?.txt}", "abc", true);
            assertGlobMatches("{[a-z]*,[0-9]*,test?.txt}", "123", true);
            assertGlobMatches("{[a-z]*,[0-9]*,test?.txt}", "testX.txt", true);
        }

        @Test
        void whenPatternHasMixedDelimiterScenarios_thenWorks() {
            assertGlobMatches("a*c", "a.b:c", false, ".", ":");
            assertGlobMatches("a**c", "a.b:c", true, ".", ":");
            assertGlobMatches("a*b*c", "a.b:c", false, ".", ":");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "(a+)+b",
                "(a*)*b",
                "(a|a)*b",
                "(a|ab)*b",
                "a.*a.*a.*a.*x"
        })
        @Timeout(value = 1, unit = TimeUnit.SECONDS)
        void whenRegexPatternPotentiallyDangerous_thenRejectsOrCompletes(String pattern) {
            val result = PatternsFunctionLibrary.findMatches(
                    Val.of(pattern),
                    Val.of("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaX")
            );
            assertThat(result.isError() || result.isArray()).isTrue();
        }

        @Test
        void whenSplitWithComplexPattern_thenHandlesSafely() {
            val result = PatternsFunctionLibrary.split(
                    Val.of("[,;:|]+"),
                    Val.of("a,b;c:d|e")
            );
            assertThat(result.isArray() || result.isError()).isTrue();
        }

        @Test
        void whenReplaceWithBackreferences_thenWorks() {
            val result = PatternsFunctionLibrary.replaceAll(
                    Val.of("test 123 demo 456"),
                    Val.of("(\\w+) (\\d+)"),
                    Val.of("$2-$1")
            );
            assertThat(result.getText()).contains("123-test");
        }

        @Test
        void whenGlobWithDelimiterAtBoundary_thenHandlesCorrectly() {
            assertGlobMatches("*", ".", false);
            assertGlobMatches("*", "", true);
            assertGlobMatches("**", ".", true);
        }

        @Test
        void whenCharacterClassWithDash_thenHandlesCorrectly() {
            assertGlobMatches("[-abc]", "-", true);
            assertGlobMatches("[abc-]", "-", true);
            assertGlobMatches("[a-c-]", "-", true);
        }
    }


    @Test
    void whenValidatingEmail_thenWorks() {
        val emailPattern = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}";
        val valid        = PatternsFunctionLibrary.findMatches(
                Val.of(emailPattern),
                Val.of("user@example.com")
        );
        assertThat(valid.getArrayNode()).hasSize(1);
    }

    @Test
    void whenParsingArn_thenExtractsComponents() {
        val result = PatternsFunctionLibrary.findAllSubmatch(
                Val.of("arn:aws:([^:]+):([^:]+):([^:]+):(.+)"),
                Val.of("arn:aws:s3:us-east-1:123456789012:bucket/key")
        );
        val match = result.getArrayNode().get(0);
        assertThat(match.get(1).asText()).isEqualTo("s3");
        assertThat(match.get(2).asText()).isEqualTo("us-east-1");
        assertThat(match.get(3).asText()).isEqualTo("123456789012");
        assertThat(match.get(4).asText()).isEqualTo("bucket/key");
    }

    @Test
    void whenMatchingResourcePath_thenWorksWithGlob() {
        assertGlobMatches("api.*.read", "api.users.read", true, ".");
        assertGlobMatches("api.**.read", "api.v1.users.read", true, ".");
        assertGlobMatches("api.*.write", "api.users.read", false, ".");
    }

    @Test
    void whenSanitizingUserInput_thenEscapesCorrectly() {
        val userInput = "project-*-staging";
        val escaped   = PatternsFunctionLibrary.escapeGlob(Val.of(userInput));
        val pattern   = escaped.getText() + ":**";
        assertGlobMatches(pattern, "project-*-staging:module:action", true, ":");
    }

    @Test
    void whenRedactingSensitiveData_thenReplacesCorrectly() {
        val text   = "SSN: 123-45-6789, Phone: 555-1234";
        val result = PatternsFunctionLibrary.replaceAll(
                Val.of(text),
                Val.of("\\d{3}-\\d{2}-\\d{4}"),
                Val.of("[REDACTED]")
        );
        assertThat(result.getText()).contains("[REDACTED]");
        assertThat(result.getText()).doesNotContain("123-45-6789");
    }
}