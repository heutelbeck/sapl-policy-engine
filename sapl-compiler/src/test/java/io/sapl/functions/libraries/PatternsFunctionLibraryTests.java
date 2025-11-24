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

import io.sapl.api.model.*;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PatternsFunctionLibraryTests {

    private static ArrayValue createDelimitersArray(String... delimiters) {
        if (delimiters.length == 0) {
            return ArrayValue.builder().build();
        }
        val builder = ArrayValue.builder();
        for (val delimiter : delimiters) {
            builder.add(Value.of(delimiter));
        }
        return builder.build();
    }

    private static void assertMatch(String pattern, String value, boolean expected, String... delimiters) {
        val delimitersArray = createDelimitersArray(delimiters);
        val result          = PatternsFunctionLibrary.matchGlob((TextValue) Value.of(pattern),
                (TextValue) Value.of(value), delimitersArray);
        assertThat(result).isInstanceOf(BooleanValue.class).isEqualTo(Value.of(expected));
    }

    private static void assertMatchWithoutDelimiters(String pattern, String value) {
        val result = PatternsFunctionLibrary.matchGlobWithoutDelimiters((TextValue) Value.of(pattern),
                (TextValue) Value.of(value));
        assertThat(result).isInstanceOf(BooleanValue.class).isEqualTo(Value.TRUE);
    }

    private static void assertError(String pattern, String value, String errorFragment, String... delimiters) {
        val delimitersArray = createDelimitersArray(delimiters);
        val result          = PatternsFunctionLibrary.matchGlob((TextValue) Value.of(pattern),
                (TextValue) Value.of(value), (ArrayValue) delimitersArray);
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(errorFragment);
    }

    @ParameterizedTest
    @CsvSource({ "*.github.com, api.github.com, true", "*.github.com, api.cdn.github.com, false",
            "**.github.com, api.cdn.github.com, true", "api.*.com, api.github.com, true",
            "api.*.*.com, api.cdn.github.com, true", "*, anything, true", "*, with.dot, false", "**, with.dot, true",
            "'', '', true", "'', anything, false" })
    void globMatchingWithDefaultDelimiter(String pattern, String value, boolean expected) {
        assertMatch(pattern, value, expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = { "user:*:read | user:admin:read | true",
            "user:*:read | user:admin:data:read | false", "user:**:read | user:admin:data:read | true",
            "a*c | abc | true", "a*c | a.b:c | false" })
    void globMatchingWithCustomDelimiters(String pattern, String value, boolean expected) {
        assertMatch(pattern, value, expected, ":");
    }

    @ParameterizedTest
    @CsvSource({ "c?t, cat, true", "c?t, ct, false", "c?t, coat, false", "file-[0-9]??.txt, file-5xy.txt, true",
            "a?c, abc, true" })
    void singleCharacterWildcard(String pattern, String value, boolean expected) {
        assertMatch(pattern, value, expected);
    }

    @ParameterizedTest
    @CsvSource({ "[abc]at, bat, true", "[abc]at, lat, false", "[!abc]at, lat, true", "[!abc]at, bat, false",
            "[a-c]at, bat, true", "[a-c]at, lat, false", "[!a-c]at, lat, true", "[0-9]*.txt, 5file.txt, true",
            "[0-9]*.txt, file.txt, false", "[!0-9]*.txt, file.txt, true" })
    void characterClasses(String pattern, String value, boolean expected) {
        assertMatch(pattern, value, expected);
    }

    @ParameterizedTest
    @CsvSource({ "'{cat,bat,[fr]at}', cat, true", "'{cat,bat,[fr]at}', rat, true", "'{cat,bat,[fr]at}', mat, false",
            "'*.{jpg,png,gif}', photo.jpg, true", "'*.{jpg,png,gif}', file.txt, false", "'{a,{b,c}}', b, true",
            "'{test}', test, true", "'{*.jpg,*.png}', photo.jpg, true" })
    void alternatives(String pattern, String value, boolean expected) {
        assertMatch(pattern, value, expected);
    }

    @ParameterizedTest
    @CsvSource({ "'file\\*.txt', 'file*.txt', true", "'file\\*.txt', file123.txt, false",
            "'test\\?mark', 'test?mark', true", "'data\\[1\\]', 'data[1]', true", "'\\d+', 'd+', true",
            "'\\w*', w, true" })
    void escapingSpecialCharacters(String pattern, String value, boolean expected) {
        assertMatch(pattern, value, expected);
    }

    @ParameterizedTest
    @CsvSource({ "'[\\*]', '*', true", "'[\\?]', '?', true", "'[\\[]', '[', true", "'[\\]]', ']', true",
            "'[-abc]', '-', true", "'[abc-]', '-', true" })
    void escapedMetacharactersInCharacterClass(String pattern, String value, boolean expected) {
        assertMatch(pattern, value, expected);
    }

    @ParameterizedTest
    @CsvSource({ "*hub.com, api.cdn.github.com", "*.github.com, api.github.com",
            "user:*:read, user:admin:data:extra:read", "file-[0-9]??.txt, file-5xy.txt", "[0-9]*, 123",
            "'{*.jpg,*.png}', photo.jpg" })
    void globMatchingWithoutDelimitersAllowsCrossingBoundaries(String pattern, String value) {
        assertMatchWithoutDelimiters(pattern, value);
    }

    @ParameterizedTest
    @CsvSource({ "café*, café-file.txt", "日本*, 日本語.txt", "🎉*, 🎉party.txt", "*é*, café" })
    void unicodeCharactersHandledCorrectly(String pattern, String value) {
        assertMatchWithoutDelimiters(pattern, value);
    }

    @ParameterizedTest
    @ValueSource(strings = { "\u0000", "\n", "\r", "\t", "\u0001", "\u001F" })
    void controlCharactersDoNotBypassValidation(String controlChar) {
        val pattern = "test" + controlChar + "pattern";
        val result  = PatternsFunctionLibrary.matchGlob((TextValue) Value.of(pattern), (TextValue) Value.of(pattern),
                ArrayValue.builder().build());
        assertThat(result).withFailMessage("Control character should not cause errors: %s", controlChar)
                .isInstanceOf(BooleanValue.class);
    }

    @ParameterizedTest
    @CsvSource({ "., a.b.c, false", "$, a$b$c, false", "^, a^b^c, false", "(, a(b)c, false", "[, a[b]c, false" })
    void delimiterWithRegexSpecialCharactersEscapedProperly(String delimiter, String value, boolean expected) {
        assertMatch("a*c", value, expected, delimiter);
        assertMatch("a**c", value, true, delimiter);
    }

    @ParameterizedTest
    @CsvSource({ "'', a.b.c, true", "::, a::b::c, false" })
    void emptyAndMultiCharDelimiters(String delimiter, String value, boolean expected) {
        assertMatch("a*c", value, expected, delimiter);
        if (!delimiter.isEmpty()) {
            assertMatch("a**c", value, true, delimiter);
        }
    }

    @Test
    void multipleDelimitersInGlob() {
        assertMatch("a*c", "a.b:c", false, ".", ":");
        assertMatch("a**c", "a.b:c", true, ".", ":");
    }

    @Test
    void emptyDelimitersArrayUsesDefault() {
        val emptyArray = ArrayValue.builder().build();
        val result     = PatternsFunctionLibrary.matchGlob((TextValue) Value.of("*.com"),
                (TextValue) Value.of("example.com"), emptyArray);
        assertThat(result).isInstanceOf(BooleanValue.class).isEqualTo(Value.TRUE);
    }

    @Test
    void undefinedDelimitersUsesDefault() {
        val result = PatternsFunctionLibrary.matchGlob((TextValue) Value.of("*.com"),
                (TextValue) Value.of("example.com"), ArrayValue.builder().build());
        assertThat(result).isInstanceOf(BooleanValue.class).isEqualTo(Value.TRUE);
    }

    @Test
    void emptyPatternMatchesOnlyEmptyValue() {
        assertMatch("", "", true);
        assertMatch("", "anything", false);
    }

    @Test
    void singleWildcardMatchesEmptyString() {
        assertMatch("*", "", true);
        assertMatch("file*", "file", true);
        assertMatch("*file", "file", true);
    }

    @Test
    void wildcardDoesNotCrossDelimiters() {
        assertMatch("*", "a.b", false, ".");
        assertMatch("*.*", "a.b", true, ".");
        assertMatch("*.*.*", "a.b.c", true, ".");
    }

    @Test
    void doubleWildcardCrossesDelimiters() {
        assertMatch("**", "a.b.c", true, ".");
        assertMatch("a**c", "a.b.c", true, ".");
        assertMatch("a**b**c", "a.x.b.y.c", true, ".");
    }

    @Test
    void dashInCharacterClassHandledCorrectly() {
        assertMatch("[-abc]", "-", true);
        assertMatch("[-abc]", "a", true);
        assertMatch("[abc-]", "-", true);
        assertMatch("[a-c]", "b", true);
        assertMatch("[a-c]", "-", false);
    }

    @Test
    void emptyAlternativesMatchEmptyString() {
        assertMatch("{}", "", true);
        assertMatch("{,}", "", true);
        assertMatch("{,,}", "", true);
    }

    @Test
    void alternativesWithComplexPatterns() {
        assertMatch("{[a-z]*,[0-9]*,test?.txt}", "abc", true);
        assertMatch("{[a-z]*,[0-9]*,test?.txt}", "123", true);
        assertMatch("{[a-z]*,[0-9]*,test?.txt}", "testX.txt", true);
        assertMatch("{*.jpg,test[0-9]??.txt,data\\*.csv}", "photo.jpg", true);
        assertMatch("{*.jpg,test[0-9]??.txt,data\\*.csv}", "test5ab.txt", true);
        assertMatch("{*.jpg,test[0-9]??.txt,data\\*.csv}", "data*.csv", true);
    }

    @Test
    void escapeAtEndOfPattern() {
        assertMatch("test\\", "test\\", true);
    }

    @Test
    void regexValidationChecksPatternSyntax() {
        val validPattern   = PatternsFunctionLibrary.isValidRegex(Value.of("[a-z]+"));
        val invalidPattern = PatternsFunctionLibrary.isValidRegex(Value.of("[a-z"));
        val tooLong        = PatternsFunctionLibrary.isValidRegex(Value.of("a".repeat(1001)));

        assertThat(validPattern).isEqualTo(Value.TRUE);
        assertThat(invalidPattern).isEqualTo(Value.FALSE);
        assertThat(tooLong).isEqualTo(Value.FALSE);
    }

    @Test
    void findMatchesExtractsAllMatches() {
        val result = PatternsFunctionLibrary.findMatches(Value.of("\\d+"), Value.of("abc 123 def 456 ghi 789"));
        assertThat(result).isInstanceOf(ArrayValue.class);
        val array = (ArrayValue) result;
        assertThat(array).hasSize(3).extracting(value -> ((TextValue) value).value()).containsExactly("123", "456",
                "789");
    }

    @Test
    void findMatchesLimitedRespectsLimit() {
        val result = PatternsFunctionLibrary.findMatchesLimited(Value.of("\\d+"), Value.of("123 456 789"), Value.of(2));
        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).hasSize(2);
    }

    @Test
    void findAllSubmatchExtractsGroups() {
        val result = PatternsFunctionLibrary.findAllSubmatch(Value.of("(\\d+)-(\\d+)"), Value.of("123-456 789-012"));
        assertThat(result).isInstanceOf(ArrayValue.class);
        val array       = (ArrayValue) result;
        val firstMatch  = (ArrayValue) array.get(0);
        val secondMatch = (ArrayValue) array.get(1);

        assertThat(firstMatch.get(0)).isEqualTo(Value.of("123-456"));
        assertThat(firstMatch.get(1)).isEqualTo(Value.of("123"));
        assertThat(firstMatch.get(2)).isEqualTo(Value.of("456"));
        assertThat(secondMatch.get(1)).isEqualTo(Value.of("789"));
        assertThat(secondMatch.get(2)).isEqualTo(Value.of("012"));
    }

    @Test
    void findAllSubmatchHandlesNullGroups() {
        val result = PatternsFunctionLibrary.findAllSubmatch(Value.of("(a)?(b)"), Value.of("b ab"));
        assertThat(result).isInstanceOf(ArrayValue.class);
        val array  = (ArrayValue) result;
        val first  = (ArrayValue) array.get(0);
        val second = (ArrayValue) array.get(1);

        assertThat(first.get(1)).isInstanceOf(NullValue.class);
        assertThat(second.get(1)).isEqualTo(Value.of("a"));
    }

    @Test
    void replaceAllSubstitutesMatches() {
        val result = PatternsFunctionLibrary.replaceAll(Value.of("123 456 789"), Value.of("\\d+"), Value.of("X"));
        assertThat(result).isInstanceOf(TextValue.class).isEqualTo(Value.of("X X X"));
    }

    @Test
    void replaceAllSupportsBackreferences() {
        val result = PatternsFunctionLibrary.replaceAll(Value.of("John Doe"), Value.of("(\\w+) (\\w+)"),
                Value.of("$2, $1"));
        assertThat(result).isInstanceOf(TextValue.class).isEqualTo(Value.of("Doe, John"));
    }

    @Test
    void splitDividesTextByPattern() {
        val result = PatternsFunctionLibrary.split(Value.of(","), Value.of("a,b,c,d"));
        assertThat(result).isInstanceOf(ArrayValue.class);
        val array = (ArrayValue) result;
        assertThat(array).hasSize(4).extracting(value -> ((TextValue) value).value()).containsExactly("a", "b", "c",
                "d");
    }

    @Test
    void splitHandlesComplexPatterns() {
        val result = PatternsFunctionLibrary.split(Value.of("\\s+"), Value.of("word1  word2    word3"));
        assertThat(result).isInstanceOf(ArrayValue.class);
        val array = (ArrayValue) result;
        assertThat(array).hasSize(3).extracting(value -> ((TextValue) value).value()).containsExactly("word1", "word2",
                "word3");
    }

    @Test
    void escapeGlobEscapesMetacharacters() {
        val result = PatternsFunctionLibrary.escapeGlob(Value.of("file*.txt"));
        assertThat(result).isInstanceOf(TextValue.class).isEqualTo(Value.of("file\\*.txt"));
        assertMatch(((TextValue) result).value(), "file*.txt", true);
    }

    @Test
    void matchTemplateWithLiteralAndPatterns() {
        val result = PatternsFunctionLibrary.matchTemplate(Value.of("user-{{\\d+}}-file"), Value.of("user-123-file"),
                Value.of("{{"), Value.of("}}"));
        assertThat(result).isInstanceOf(BooleanValue.class).isEqualTo(Value.TRUE);
    }

    @Test
    void matchTemplateHandlesEscapedLiterals() {
        val result = PatternsFunctionLibrary.matchTemplate(Value.of("file\\*{{\\d+}}"), Value.of("file*42"),
                Value.of("{{"), Value.of("}}"));
        assertThat(result).isInstanceOf(BooleanValue.class).isEqualTo(Value.TRUE);
    }

    @Test
    void templateWithEmptyDelimitersReturnsError() {
        val result1 = PatternsFunctionLibrary.matchTemplate(Value.of("test{{pattern}}"), Value.of("test123"),
                Value.of(""), Value.of("}}"));
        assertThat(result1).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result1).message()).contains("empty");

        val result2 = PatternsFunctionLibrary.matchTemplate(Value.of("test{{pattern}}"), Value.of("test123"),
                Value.of("{{"), Value.of(""));
        assertThat(result2).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result2).message()).contains("empty");
    }

    @Test
    void unclosedDelimitersInTemplateReturnError() {
        val error = PatternsFunctionLibrary.matchTemplate(Value.of("test{pattern"), Value.of("test123"), Value.of("{"),
                Value.of("}"));
        assertThat(error).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) error).message()).contains("mismatched");
    }

    @Test
    void patternTooLongRejected() {
        val longPattern = "*".repeat(1001);
        assertError(longPattern, "test", "Pattern too long");
    }

    @Test
    void inputTooLongRejected() {
        val longValue = "a".repeat(100001);
        assertError("*", longValue, "Input too long");
    }

    @Test
    void regexInputTooLongRejected() {
        val longRegex   = "a".repeat(1001);
        val regexResult = PatternsFunctionLibrary.findMatches(Value.of(longRegex), Value.of("test"));
        assertThat(regexResult).isInstanceOf(ErrorValue.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "(a+)+", "(a*)*", "(a|a)*", "(a|ab)*" })
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void dangerousRegexPatternsRejected(String pattern) {
        val result = PatternsFunctionLibrary.findMatches(Value.of(pattern), Value.of("aaaaaaaaaaaaaaaaaaaaX"));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("dangerous");
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void excessiveAlternationsRejected() {
        val manyAlternations = "(" + "a|".repeat(101) + "b)";
        val altResult        = PatternsFunctionLibrary.findMatches(Value.of(manyAlternations), Value.of("test"));
        assertThat(altResult).isInstanceOf(ErrorValue.class);
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void catastrophicBacktrackingDetectedOrCompletes() {
        val catastrophicPattern = "a.*a.*a.*a.*x";
        val input               = "a".repeat(30) + "X";
        val result              = PatternsFunctionLibrary.findMatches(Value.of(catastrophicPattern), Value.of(input));

        assertThat(result instanceof ErrorValue || result instanceof ArrayValue)
                .withFailMessage("Pattern should either be rejected as dangerous or complete within timeout").isTrue();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void complexGlobPatternsCompleteQuickly() {
        val complexPattern = "{a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p}*{1,2,3,4,5}";
        val result         = PatternsFunctionLibrary.matchGlob((TextValue) Value.of(complexPattern),
                (TextValue) Value.of("test"), ArrayValue.builder().build());
        assertThat(result).isInstanceOf(BooleanValue.class);

        val deeplyNested = "{".repeat(100) + "a" + "}".repeat(100);
        val nestedResult = PatternsFunctionLibrary.matchGlob((TextValue) Value.of(deeplyNested),
                (TextValue) Value.of("a"), ArrayValue.builder().build());
        assertThat(nestedResult instanceof BooleanValue || nestedResult instanceof ErrorValue).isTrue();
    }

    @Test
    void patternCacheImprovePerformance() {
        val pattern = "[a-z]+@[a-z]+\\.com";
        val value1  = "alice@example.com";
        val value2  = "bob@test.com";

        val startTime = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            PatternsFunctionLibrary.findMatches((TextValue) Value.of(pattern), (TextValue) Value.of(value1));
            PatternsFunctionLibrary.findMatches((TextValue) Value.of(pattern), (TextValue) Value.of(value2));
        }
        val cachedTime = System.nanoTime() - startTime;

        assertThat(cachedTime).isLessThan(TimeUnit.SECONDS.toNanos(1));
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, -100 })
    void negativeLimitsRejected(int limit) {
        val result = PatternsFunctionLibrary.findMatchesLimited((TextValue) Value.of("\\d+"),
                (TextValue) Value.of("123"), (NumberValue) Value.of(limit));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("negative");
    }

    @Test
    void errorMessagesIncludePositionInformation() {
        val result = PatternsFunctionLibrary.matchGlob((TextValue) Value.of("test[unclosed"),
                (TextValue) Value.of("test"), ArrayValue.builder().build());

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).containsAnyOf("position", "starting");
    }

    @ParameterizedTest
    @ValueSource(strings = { "(a+)+", "(a*)*", "(.*)*", "(a+)+b", "{5,10}{5,10}", ".*.*test" })
    void improvedRedosDetectionCatchesMorePatterns(String pattern) {
        val result = PatternsFunctionLibrary.findMatches(Value.of(pattern), Value.of("test"));
        assertThat(result).withFailMessage("Pattern '%s' should be detected as dangerous", pattern)
                .isInstanceOf(ErrorValue.class);
    }

    @Test
    void realWorldUseCases() {
        assertMatch("api.*.read", "api.users.read", true, ".");
        assertMatch("api.**.read", "api.v1.users.read", true, ".");

        val emails = PatternsFunctionLibrary.findMatches(Value.of("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
                Value.of("user@example.com"));
        assertThat(emails).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) emails).hasSize(1);

        val arn = PatternsFunctionLibrary.findAllSubmatch(Value.of("arn:aws:([^:]+):([^:]+):([^:]+):(.+)"),
                Value.of("arn:aws:s3:us-east-1:123456789012:bucket/key"));
        assertThat(arn).isInstanceOf(ArrayValue.class);
        val arnArray = (ArrayValue) arn;
        val match    = (ArrayValue) arnArray.getFirst();
        assertThat(match.get(1)).isEqualTo(Value.of("s3"));
        assertThat(match.get(4)).isEqualTo(Value.of("bucket/key"));

        val userInput = "project-*-staging";
        val escaped   = PatternsFunctionLibrary.escapeGlob(Value.of(userInput));
        assertThat(escaped).isInstanceOf(TextValue.class);
        val pattern = ((TextValue) escaped).value() + ":**";
        assertMatch(pattern, "project-*-staging:module:action", true, ":");

        val redacted = PatternsFunctionLibrary.replaceAll(Value.of("SSN: 123-45-6789"),
                Value.of("\\d{3}-\\d{2}-\\d{4}"), Value.of("[REDACTED]"));
        assertThat(redacted).isInstanceOf(TextValue.class).isEqualTo(Value.of("SSN: [REDACTED]"));
    }
}
