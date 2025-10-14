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
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

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

    @Test
    void globMatchingWithoutDelimitersAllowsCrossingBoundaries() {
        assertMatchWithoutDelimiters("*hub.com", "api.cdn.github.com");
        assertMatchWithoutDelimiters("*.github.com", "api.github.com");
        assertMatchWithoutDelimiters("user:*:read", "user:admin:data:extra:read");
        assertMatchWithoutDelimiters("file-[0-9]??.txt", "file-5xy.txt");
        assertMatchWithoutDelimiters("[0-9]*", "123");
        assertMatchWithoutDelimiters("{*.jpg,*.png}", "photo.jpg");
    }

    @Test
    void unicodeCharactersHandledCorrectly() {
        assertMatchWithoutDelimiters("café*", "café-file.txt");
        assertMatchWithoutDelimiters("日本*", "日本語.txt");
        assertMatchWithoutDelimiters("🎉*", "🎉party.txt");
        assertMatchWithoutDelimiters("*é*", "café");
    }

    @Test
    void controlCharactersDoNotBypassValidation() {
        val controlChars = new String[] { "\u0000", "\n", "\r", "\t", "\u0001", "\u001F" };
        for (val controlChar : controlChars) {
            val pattern = "test" + controlChar + "pattern";
            val result  = PatternsFunctionLibrary.matchGlob(Val.of(pattern), Val.of(pattern), Val.UNDEFINED);
            assertThat(result.isBoolean()).withFailMessage("Control character should not cause errors: %s", controlChar)
                    .isTrue();
        }
    }

    @Test
    void delimiterWithRegexSpecialCharactersEscapedProperly() {
        assertMatch("a*c", "a.b.c", false, ".");
        assertMatch("a*c", "a$b$c", false, "$");
        assertMatch("a*c", "a^b^c", false, "^");
        assertMatch("a*c", "a(b)c", false, "(");
        assertMatch("a*c", "a[b]c", false, "[");
        assertMatch("a**c", "a.b.c", true, ".");
    }

    @Test
    void emptyAndEdgeDelimiters() {
        assertMatch("a*c", "a.b.c", true, "");
        assertMatch("a*c", "a::b::c", false, "::");
        assertMatch("a**c", "a::b::c", true, "::");
        assertMatch("a*b*c", "a.b:c", false, ".", ":");
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
    void nonTextualDelimitersReturnError() {
        val arrayNode = JsonNodeFactory.instance.arrayNode();
        arrayNode.add(".");
        arrayNode.add(123);
        val delimitersWithNumber = Val.of(arrayNode);

        val result = PatternsFunctionLibrary.matchGlob(Val.of("*"), Val.of("test"), delimitersWithNumber);
        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains("text");
    }

    @Test
    void nonArrayDelimitersUsesDefault() {
        val result = PatternsFunctionLibrary.matchGlob(Val.of("*.com"), Val.of("test.com"), Val.of("invalid"));
        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("unclosedBracketsAndBraces")
    void malformedPatternsReturnErrors(String pattern) {
        val result = PatternsFunctionLibrary.matchGlob(Val.of(pattern), Val.of("test"), Val.UNDEFINED);
        assertThat(result.isError())
                .withFailMessage("Malformed pattern '%s' should return error for safety in policy engine", pattern)
                .isTrue();
        assertThat(result.getMessage()).containsAnyOf("Unclosed", "position");
    }

    static Stream<String> unclosedBracketsAndBraces() {
        return Stream.of("[abc", "test[", "{abc", "test{");
    }

    @ParameterizedTest
    @MethodSource("edgeCasePatterns")
    void edgeCasePatternsHandledConsistently(String pattern) {
        val result = PatternsFunctionLibrary.matchGlob(Val.of(pattern), Val.of("test"), Val.UNDEFINED);
        assertThat(result.isBoolean() || result.isError()).isTrue();
    }

    static Stream<String> edgeCasePatterns() {
        return Stream.of("*".repeat(100), "?".repeat(100), "[a-z]".repeat(50), "{a,b}".repeat(50), "***", "????",
                "[[[[", "[*", "[+", "[.", "[^", "[$", "[(a+)+]", "[z-a]", "[9-0]", "[Z-A]", "[]", "[^]", "[!]");
    }

    @Test
    void escapeGlobPreventsPatternInjection() {
        val userName = "alice*bob";
        val escaped  = PatternsFunctionLibrary.escapeGlob(Val.of(userName));
        assertThat(escaped.getText()).isEqualTo("alice\\*bob");

        val pattern = escaped.getText() + ":*:read";
        assertMatch(pattern, "alice*bob:data:read", true, ":");
        assertMatch(pattern, "aliceXbob:data:read", false, ":");

        val allMetachars = "*?[]{}\\-!";
        val allEscaped   = PatternsFunctionLibrary.escapeGlob(Val.of(allMetachars));
        assertThat(allEscaped.getText()).isEqualTo("\\*\\?\\[\\]\\{\\}\\\\\\-\\!");
    }

    @ParameterizedTest
    @ValueSource(strings = { "[a-z]+", "\\d{3}-\\d{4}", "^test$", "(?<n>\\w+)", "(a|b|c)" })
    void validRegexPatternsAccepted(String pattern) {
        val result = PatternsFunctionLibrary.isValidRegex(Val.of(pattern));
        assertThat(result.getBoolean()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "[a-z", "(?<n>", "\\", "*", "(?P<invalid)" })
    void invalidRegexPatternsRejected(String pattern) {
        val result = PatternsFunctionLibrary.isValidRegex(Val.of(pattern));
        assertThat(result.getBoolean()).isFalse();
    }

    @Test
    void findMatchesExtractsAllOccurrences() {
        val result = PatternsFunctionLibrary.findMatches(Val.of("[a-z]+@[a-z]+\\.com"),
                Val.of("Contact: alice@example.com or bob@test.com"));
        assertThat(result.getArrayNode()).hasSize(2);
        assertThat(result.getArrayNode().get(0).asText()).isEqualTo("alice@example.com");
        assertThat(result.getArrayNode().get(1).asText()).isEqualTo("bob@test.com");
    }

    @Test
    void findMatchesRespectsLimit() {
        val result = PatternsFunctionLibrary.findMatchesLimited(Val.of("\\d+"), Val.of("1 2 3 4 5"), Val.of(2));
        assertThat(result.getArrayNode()).hasSize(2);
        assertThat(result.getArrayNode().get(0).asText()).isEqualTo("1");

        val capped = PatternsFunctionLibrary.findMatchesLimited(Val.of("."), Val.of(".".repeat(50000)), Val.of(20000));
        assertThat(capped.getArrayNode().size()).isEqualTo(10000);
    }

    @Test
    void findAllSubmatchCapturesGroups() {
        val result     = PatternsFunctionLibrary.findAllSubmatch(Val.of("([a-z]+)@([a-z]+)\\.com"),
                Val.of("alice@example.com bob@test.com"));
        val firstMatch = result.getArrayNode().get(0);
        assertThat(firstMatch.get(0).asText()).isEqualTo("alice@example.com");
        assertThat(firstMatch.get(1).asText()).isEqualTo("alice");
        assertThat(firstMatch.get(2).asText()).isEqualTo("example");
    }

    @Test
    void findAllSubmatchHandlesOptionalGroups() {
        val result     = PatternsFunctionLibrary.findAllSubmatch(Val.of("(\\d+)-(\\d+)?"), Val.of("10-20 30- 40"));
        val firstMatch = result.getArrayNode().get(0);
        assertThat(firstMatch.get(2).asText()).isEqualTo("20");

        val secondMatch = result.getArrayNode().get(1);
        assertThat(secondMatch.get(2).isNull()).isTrue();
    }

    @Test
    void replaceAllWithBackreferences() {
        val result = PatternsFunctionLibrary.replaceAll(Val.of("John Doe, Jane Smith"), Val.of("(\\w+) (\\w+)"),
                Val.of("$2, $1"));
        assertThat(result.isTextual()).isTrue();
        assertThat(result.getText()).isEqualTo("Doe, John, Smith, Jane");

        val literalDollar = PatternsFunctionLibrary.replaceAll(Val.of("price: 100"), Val.of("\\d+"), Val.of("\\$50"));
        assertThat(literalDollar.isTextual()).isTrue();
        assertThat(literalDollar.getText()).isEqualTo("price: $50");
    }

    @Test
    void replaceAllNormalizesWhitespace() {
        val result = PatternsFunctionLibrary.replaceAll(Val.of("  hello   world  "), Val.of("\\s+"), Val.of(" "));
        assertThat(result.getText()).isEqualTo(" hello world ");
    }

    @Test
    void splitByPattern() {
        val result = PatternsFunctionLibrary.split(Val.of(",\\s*"), Val.of("apple, banana,cherry,  date"));
        assertThat(result.getArrayNode()).hasSize(4);
        assertThat(result.getArrayNode().get(0).asText()).isEqualTo("apple");

        val leadingDelimiter = PatternsFunctionLibrary.split(Val.of(","), Val.of(",a,b"));
        assertThat(leadingDelimiter.getArrayNode().get(0).asText()).isEmpty();

        val consecutive = PatternsFunctionLibrary.split(Val.of(","), Val.of("a,,b"));
        assertThat(consecutive.getArrayNode().get(1).asText()).isEmpty();
    }

    @Test
    void matchTemplateWithLiteralAndRegexParts() {
        val result = PatternsFunctionLibrary.matchTemplate(Val.of("user-{\\d+}-profile"), Val.of("user-12345-profile"),
                Val.of("{"), Val.of("}"));
        assertThat(result.getBoolean()).isTrue();

        val emailMatch = PatternsFunctionLibrary.matchTemplate(Val.of("{[a-z]+}@{[a-z]+}\\.com"),
                Val.of("admin@example.com"), Val.of("{"), Val.of("}"));
        assertThat(emailMatch.getBoolean()).isTrue();

        val noMatch = PatternsFunctionLibrary.matchTemplate(Val.of("prefix-{\\d+}-suffix"), Val.of("prefix-abc-suffix"),
                Val.of("{"), Val.of("}"));
        assertThat(noMatch.getBoolean()).isFalse();
    }

    @Test
    void templateMatchingWithCustomDelimiters() {
        val result = PatternsFunctionLibrary.matchTemplate(Val.of("/api/<\\w+>/users/<\\d+>"),
                Val.of("/api/v2/users/42"), Val.of("<"), Val.of(">"));
        assertThat(result.getBoolean()).isTrue();
    }

    @Test
    void templateLiteralPortionsEscapedCorrectly() {
        val result = PatternsFunctionLibrary.matchTemplate(Val.of("user.{\\d+}.profile"), Val.of("user.123.profile"),
                Val.of("{"), Val.of("}"));
        assertThat(result.getBoolean()).withFailMessage("Dots in template should be literal, not regex wildcards")
                .isTrue();

        val noMatch = PatternsFunctionLibrary.matchTemplate(Val.of("user.{\\d+}.profile"), Val.of("userX123Xprofile"),
                Val.of("{"), Val.of("}"));
        assertThat(noMatch.getBoolean()).withFailMessage("Dots should NOT match any character").isFalse();

        val specialChars = PatternsFunctionLibrary.matchTemplate(Val.of("file*.{\\d+}"), Val.of("file*.123"),
                Val.of("{"), Val.of("}"));
        assertThat(specialChars.getBoolean()).withFailMessage("Asterisk in literal portion should be literal").isTrue();
    }

    @Test
    void templateWithAllRegexMetacharactersInLiteralPortion() {
        val template = ".^$*+?()[]{\\d+}\\|test";
        val value    = ".^$*+?()[]123|test";

        val result = PatternsFunctionLibrary.matchTemplate(Val.of(template), Val.of(value), Val.of("{"), Val.of("}"));
        assertThat(result.getBoolean()).isTrue();
    }

    @Test
    void patternTooLongRejected() {
        val longPattern = "*".repeat(1001);
        assertError(longPattern, "test", "Pattern too long");

        val longRegex   = "a".repeat(1001);
        val regexResult = PatternsFunctionLibrary.findMatches(Val.of(longRegex), Val.of("test"));
        assertThat(regexResult.isError()).isTrue();
    }

    @Test
    void inputTooLongRejected() {
        val longValue = "a".repeat(100001);
        assertError("*", longValue, "Input too long");
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void dangerousRegexPatternsRejected() {
        val dangerousPatterns = new String[] { "(a+)+", "(a*)*", "(a|a)*", "(a|ab)*" };
        for (val pattern : dangerousPatterns) {
            val result = PatternsFunctionLibrary.findMatches(Val.of(pattern), Val.of("aaaaaaaaaaaaaaaaaaaaX"));
            assertThat(result.isError()).isTrue();
            assertThat(result.getMessage()).contains("dangerous");
        }

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
        assertThat(redacted.getText()).contains("[REDACTED]");
        assertThat(redacted.getText()).doesNotContain("123-45-6789");
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
    void unclosedDelimitersInTemplateReturnError() {
        val error = PatternsFunctionLibrary.matchTemplate(Val.of("test{pattern"), Val.of("test123"), Val.of("{"),
                Val.of("}"));
        assertThat(error.isError()).isTrue();
        assertThat(error.getMessage()).contains("mismatched");
    }

    @Test
    void emptyDelimitersInTemplateReturnError() {
        val error = PatternsFunctionLibrary.matchTemplate(Val.of("test"), Val.of("test"), Val.of(""), Val.of("}"));
        assertThat(error.isError()).isTrue();
        assertThat(error.getMessage()).contains("empty");
    }

    @Test
    void negativeLimitsRejected() {
        val result = PatternsFunctionLibrary.findMatchesLimited(Val.of("\\d+"), Val.of("123"), Val.of(-1));
        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains("negative");
    }

    @Test
    void escapeAtEndOfPattern() {
        assertMatch("test\\", "test\\", true);
    }

    @Test
    void multipleDelimitersInGlob() {
        assertMatch("a*c", "a.b:c", false, ".", ":");
        assertMatch("a**c", "a.b:c", true, ".", ":");
    }

    @Test
    void errorMessagesIncludePositionInformation() {
        val result = PatternsFunctionLibrary.matchGlob(Val.of("test[unclosed"), Val.of("test"), Val.UNDEFINED);

        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).containsAnyOf("position", "starting");
    }

    @Test
    void improvedRedosDetectionCatchesMorePatterns() {
        val suspiciousPatterns = new String[] { "(a+)+", "(a*)*", "(.*)*", "(a+)+b", "{5,10}{5,10}", ".*.*test" };

        for (val pattern : suspiciousPatterns) {
            val result = PatternsFunctionLibrary.findMatches(Val.of(pattern), Val.of("test"));
            assertThat(result.isError()).withFailMessage("Pattern '%s' should be detected as dangerous", pattern)
                    .isTrue();
        }
    }
}
