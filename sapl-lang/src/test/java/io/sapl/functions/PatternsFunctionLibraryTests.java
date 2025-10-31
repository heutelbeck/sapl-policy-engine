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

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.interpreter.Val;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PatternsFunctionLibraryTests {

    private static Val createDelimitersArray(String... delimiters) {
        if (delimiters.length == 0) {
            return Val.UNDEFINED;
        }
        val arrayNode = JsonNodeFactory.instance.arrayNode();
        for (val delimiter : delimiters) {
            arrayNode.add(delimiter);
        }
        return Val.of(arrayNode);
    }

    private static void assertMatch(String pattern, String value, boolean expected, String... delimiters) {
        val delimitersArray = createDelimitersArray(delimiters);
        val result          = PatternsFunctionLibrary.matchGlob(Val.of(pattern), Val.of(value), delimitersArray);
        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isEqualTo(expected);
    }

    private static void assertMatchWithoutDelimiters(String pattern, String value) {
        val result = PatternsFunctionLibrary.matchGlobWithoutDelimiters(Val.of(pattern), Val.of(value));
        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isTrue();
    }

    private static void assertError(String pattern, String value, String errorFragment, String... delimiters) {
        val delimitersArray = createDelimitersArray(delimiters);
        val result          = PatternsFunctionLibrary.matchGlob(Val.of(pattern), Val.of(value), delimitersArray);
        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains(errorFragment);
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
        val result  = PatternsFunctionLibrary.matchGlob(Val.of(pattern), Val.of(pattern), Val.UNDEFINED);
        assertThat(result.isBoolean()).withFailMessage("Control character should not cause errors: %s", controlChar)
                .isTrue();
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
        val emptyArray = Val.of(JsonNodeFactory.instance.arrayNode());
        val result     = PatternsFunctionLibrary.matchGlob(Val.of("*.com"), Val.of("example.com"), emptyArray);
        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isTrue();
    }

    @Test
    void undefinedDelimitersUsesDefault() {
        val result = PatternsFunctionLibrary.matchGlob(Val.of("*.com"), Val.of("example.com"), Val.UNDEFINED);
        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isTrue();
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
        val validPattern   = PatternsFunctionLibrary.isValidRegex(Val.of("[a-z]+"));
        val invalidPattern = PatternsFunctionLibrary.isValidRegex(Val.of("[a-z"));
        val tooLong        = PatternsFunctionLibrary.isValidRegex(Val.of("a".repeat(1001)));

        assertThat(validPattern.getBoolean()).isTrue();
        assertThat(invalidPattern.getBoolean()).isFalse();
        assertThat(tooLong.getBoolean()).isFalse();
    }

    @Test
    void findMatchesExtractsAllMatches() {
        val result = PatternsFunctionLibrary.findMatches(Val.of("\\d+"), Val.of("abc 123 def 456 ghi 789"));
        assertThat(result.getArrayNode()).hasSize(3).extracting(node -> node.asText()).containsExactly("123", "456",
                "789");
    }

    @Test
    void findMatchesLimitedRespectsLimit() {
        val result = PatternsFunctionLibrary.findMatchesLimited(Val.of("\\d+"), Val.of("123 456 789"), Val.of(2));
        assertThat(result.getArrayNode()).hasSize(2);
    }

    @Test
    void findAllSubmatchExtractsGroups() {
        val result      = PatternsFunctionLibrary.findAllSubmatch(Val.of("(\\d+)-(\\d+)"), Val.of("123-456 789-012"));
        val firstMatch  = result.getArrayNode().get(0);
        val secondMatch = result.getArrayNode().get(1);

        assertThat(firstMatch.get(0).asText()).isEqualTo("123-456");
        assertThat(firstMatch.get(1).asText()).isEqualTo("123");
        assertThat(firstMatch.get(2).asText()).isEqualTo("456");
        assertThat(secondMatch.get(1).asText()).isEqualTo("789");
        assertThat(secondMatch.get(2).asText()).isEqualTo("012");
    }

    @Test
    void findAllSubmatchHandlesNullGroups() {
        val result = PatternsFunctionLibrary.findAllSubmatch(Val.of("(a)?(b)"), Val.of("b ab"));
        val first  = result.getArrayNode().get(0);
        val second = result.getArrayNode().get(1);

        assertThat(first.get(1).isNull()).isTrue();
        assertThat(second.get(1).asText()).isEqualTo("a");
    }

    @Test
    void replaceAllSubstitutesMatches() {
        val result = PatternsFunctionLibrary.replaceAll(Val.of("123 456 789"), Val.of("\\d+"), Val.of("X"));
        assertThat(result.getText()).isEqualTo("X X X");
    }

    @Test
    void replaceAllSupportsBackreferences() {
        val result = PatternsFunctionLibrary.replaceAll(Val.of("John Doe"), Val.of("(\\w+) (\\w+)"), Val.of("$2, $1"));
        assertThat(result.getText()).isEqualTo("Doe, John");
    }

    @Test
    void splitDividesTextByPattern() {
        val result = PatternsFunctionLibrary.split(Val.of(","), Val.of("a,b,c,d"));
        assertThat(result.getArrayNode()).hasSize(4).extracting(node -> node.asText()).containsExactly("a", "b", "c",
                "d");
    }

    @Test
    void splitHandlesComplexPatterns() {
        val result = PatternsFunctionLibrary.split(Val.of("\\s+"), Val.of("word1  word2    word3"));
        assertThat(result.getArrayNode()).hasSize(3).extracting(node -> node.asText()).containsExactly("word1", "word2",
                "word3");
    }

    @Test
    void escapeGlobEscapesMetacharacters() {
        val result = PatternsFunctionLibrary.escapeGlob(Val.of("file*.txt"));
        assertThat(result.getText()).isEqualTo("file\\*.txt");
        assertMatch(result.getText(), "file*.txt", true);
    }

    @Test
    void matchTemplateWithLiteralAndPatterns() {
        val result = PatternsFunctionLibrary.matchTemplate(Val.of("user-{{\\d+}}-file"), Val.of("user-123-file"),
                Val.of("{{"), Val.of("}}"));
        assertThat(result.getBoolean()).isTrue();
    }

    @Test
    void matchTemplateHandlesEscapedLiterals() {
        val result = PatternsFunctionLibrary.matchTemplate(Val.of("file\\*{{\\d+}}"), Val.of("file*42"), Val.of("{{"),
                Val.of("}}"));
        assertThat(result.getBoolean()).isTrue();
    }

    @Test
    void templateWithEmptyDelimitersReturnsError() {
        val result1 = PatternsFunctionLibrary.matchTemplate(Val.of("test{{pattern}}"), Val.of("test123"), Val.of(""),
                Val.of("}}"));
        assertThat(result1.isError()).isTrue();
        assertThat(result1.getMessage()).contains("empty");

        val result2 = PatternsFunctionLibrary.matchTemplate(Val.of("test{{pattern}}"), Val.of("test123"), Val.of("{{"),
                Val.of(""));
        assertThat(result2.isError()).isTrue();
        assertThat(result2.getMessage()).contains("empty");
    }

    @Test
    void unclosedDelimitersInTemplateReturnError() {
        val error = PatternsFunctionLibrary.matchTemplate(Val.of("test{pattern"), Val.of("test123"), Val.of("{"),
                Val.of("}"));
        assertThat(error.isError()).isTrue();
        assertThat(error.getMessage()).contains("mismatched");
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
        val regexResult = PatternsFunctionLibrary.findMatches(Val.of(longRegex), Val.of("test"));
        assertThat(regexResult.isError()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "(a+)+", "(a*)*", "(a|a)*", "(a|ab)*" })
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void dangerousRegexPatternsRejected(String pattern) {
        val result = PatternsFunctionLibrary.findMatches(Val.of(pattern), Val.of("aaaaaaaaaaaaaaaaaaaaX"));
        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains("dangerous");
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void excessiveAlternationsRejected() {
        val manyAlternations = "(" + "a|".repeat(101) + "b)";
        val altResult        = PatternsFunctionLibrary.findMatches(Val.of(manyAlternations), Val.of("test"));
        assertThat(altResult.isError()).isTrue();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void catastrophicBacktrackingDetectedOrCompletes() {
        val catastrophicPattern = "a.*a.*a.*a.*x";
        val input               = "a".repeat(30) + "X";
        val result              = PatternsFunctionLibrary.findMatches(Val.of(catastrophicPattern), Val.of(input));

        assertThat(result.isError() || result.isArray())
                .withFailMessage("Pattern should either be rejected as dangerous or complete within timeout").isTrue();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void complexGlobPatternsCompleteQuickly() {
        val complexPattern = "{a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p}*{1,2,3,4,5}";
        val result         = PatternsFunctionLibrary.matchGlob(Val.of(complexPattern), Val.of("test"), Val.UNDEFINED);
        assertThat(result.isBoolean()).isTrue();

        val deeplyNested = "{".repeat(100) + "a" + "}".repeat(100);
        val nestedResult = PatternsFunctionLibrary.matchGlob(Val.of(deeplyNested), Val.of("a"), Val.UNDEFINED);
        assertThat(nestedResult.isBoolean() || nestedResult.isError()).isTrue();
    }

    @Test
    void patternCacheImprovePerformance() {
        val pattern = "[a-z]+@[a-z]+\\.com";
        val value1  = "alice@example.com";
        val value2  = "bob@test.com";

        val startTime = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            PatternsFunctionLibrary.findMatches(Val.of(pattern), Val.of(value1));
            PatternsFunctionLibrary.findMatches(Val.of(pattern), Val.of(value2));
        }
        val cachedTime = System.nanoTime() - startTime;

        assertThat(cachedTime).isLessThan(TimeUnit.SECONDS.toNanos(1));
    }

    @Test
    void invalidInputTypesRejected() {
        val numericPattern = PatternsFunctionLibrary.matchGlob(Val.of(123), Val.of("test"), Val.UNDEFINED);
        assertThat(numericPattern.isError()).isTrue();

        val nullPattern = PatternsFunctionLibrary.findMatches(Val.NULL, Val.of("test"));
        assertThat(nullPattern.isError()).isTrue();

        val invalidReplacement = PatternsFunctionLibrary.replaceAll(Val.of("test"), Val.of("test"), Val.of(42));
        assertThat(invalidReplacement.isError()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, -100 })
    void negativeLimitsRejected(int limit) {
        val result = PatternsFunctionLibrary.findMatchesLimited(Val.of("\\d+"), Val.of("123"), Val.of(limit));
        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains("negative");
    }

    @Test
    void errorMessagesIncludePositionInformation() {
        val result = PatternsFunctionLibrary.matchGlob(Val.of("test[unclosed"), Val.of("test"), Val.UNDEFINED);

        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).containsAnyOf("position", "starting");
    }

    @ParameterizedTest
    @ValueSource(strings = { "(a+)+", "(a*)*", "(.*)*", "(a+)+b", "{5,10}{5,10}", ".*.*test" })
    void improvedRedosDetectionCatchesMorePatterns(String pattern) {
        val result = PatternsFunctionLibrary.findMatches(Val.of(pattern), Val.of("test"));
        assertThat(result.isError()).withFailMessage("Pattern '%s' should be detected as dangerous", pattern).isTrue();
    }

    @Test
    void realWorldUseCases() {
        assertMatch("api.*.read", "api.users.read", true, ".");
        assertMatch("api.**.read", "api.v1.users.read", true, ".");

        val emails = PatternsFunctionLibrary.findMatches(Val.of("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
                Val.of("user@example.com"));
        assertThat(emails.getArrayNode()).hasSize(1);

        val arn   = PatternsFunctionLibrary.findAllSubmatch(Val.of("arn:aws:([^:]+):([^:]+):([^:]+):(.+)"),
                Val.of("arn:aws:s3:us-east-1:123456789012:bucket/key"));
        val match = arn.getArrayNode().get(0);
        assertThat(match.get(1).asText()).isEqualTo("s3");
        assertThat(match.get(4).asText()).isEqualTo("bucket/key");

        val userInput = "project-*-staging";
        val escaped   = PatternsFunctionLibrary.escapeGlob(Val.of(userInput));
        val pattern   = escaped.getText() + ":**";
        assertMatch(pattern, "project-*-staging:module:action", true, ":");

        val redacted = PatternsFunctionLibrary.replaceAll(Val.of("SSN: 123-45-6789"), Val.of("\\d{3}-\\d{2}-\\d{4}"),
                Val.of("[REDACTED]"));
        assertThat(redacted.getText()).contains("[REDACTED]").doesNotContain("123-45-6789");
    }
}
