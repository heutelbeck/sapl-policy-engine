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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.sapl.functions.libraries.SanitizationFunctionLibrary.POTENTIAL_SQL_INJECTION_DETECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SanitizationFunctionLibraryTests {

    @Test
    void when_loadedIntoBroker_then_noError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.loadStaticFunctionLibrary(SanitizationFunctionLibrary.class))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "[{index}] {1}: {0}")
    @MethodSource("balancedModeLegitimateInputs")
    void assertNoSqlInjection_whenLegitimateInput_thenReturnsInputUnchanged(String input, String description) {
        val inputVal = Value.of(input);

        val result = SanitizationFunctionLibrary.assertNoSqlInjection(inputVal);

        assertThat(result).isEqualTo(inputVal);
    }

    @ParameterizedTest(name = "[{index}] {1}: {0}")
    @MethodSource("balancedModeInjectionAttempts")
    void assertNoSqlInjection_whenInjectionAttempt_thenReturnsError(String input, String description) {
        val inputVal = Value.of(input);

        val result = SanitizationFunctionLibrary.assertNoSqlInjection(inputVal);

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).isEqualTo(POTENTIAL_SQL_INJECTION_DETECTED);
    }

    @Test
    void assertNoSqlInjection_whenEmptyString_thenReturnsInputUnchanged() {
        val inputVal = Value.of("");

        val result = SanitizationFunctionLibrary.assertNoSqlInjection(inputVal);

        assertThat(result).isEqualTo(inputVal);
    }

    @ParameterizedTest(name = "[{index}] {1}: {0}")
    @MethodSource("strictModeValidIdentifiers")
    void assertNoSqlInjectionStrict_whenCleanIdentifier_thenReturnsInputUnchanged(String input, String description) {
        val inputVal = Value.of(input);

        val result = SanitizationFunctionLibrary.assertNoSqlInjectionStrict(inputVal);

        assertThat(result).isEqualTo(inputVal);
    }

    @ParameterizedTest(name = "[{index}] {1}: {0}")
    @MethodSource("strictModeRejectedInputs")
    void assertNoSqlInjectionStrict_whenAnySqlSyntax_thenReturnsError(String input, String description) {
        val inputVal = Value.of(input);

        val result = SanitizationFunctionLibrary.assertNoSqlInjectionStrict(inputVal);

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).isEqualTo(POTENTIAL_SQL_INJECTION_DETECTED);
    }

    @Test
    void assertNoSqlInjectionStrict_whenEmptyString_thenReturnsInputUnchanged() {
        val inputVal = Value.of("");

        val result = SanitizationFunctionLibrary.assertNoSqlInjectionStrict(inputVal);

        assertThat(result).isEqualTo(inputVal);
    }

    @ParameterizedTest(name = "[{index}] {3}: {0}")
    @MethodSource("balancedVsStrictComparison")
    void compareModes_whenDifferentInputs_thenBehavesAsExpected(String input, boolean balancedBlocks,
            boolean strictBlocks, String description) {
        val inputVal       = Value.of(input);
        val balancedResult = SanitizationFunctionLibrary.assertNoSqlInjection(inputVal);
        val strictResult   = SanitizationFunctionLibrary.assertNoSqlInjectionStrict(inputVal);

        if (balancedBlocks) {
            assertThat(balancedResult).isInstanceOf(ErrorValue.class);
        } else {
            assertThat(balancedResult).isEqualTo(inputVal);
        }

        if (strictBlocks) {
            assertThat(strictResult).isInstanceOf(ErrorValue.class);
        } else {
            assertThat(strictResult).isEqualTo(inputVal);
        }
    }

    @ParameterizedTest(name = "[{index}] {2}: {0}")
    @MethodSource("edgeCasesAndSecurityTests")
    void assertNoSqlInjection_whenEdgeCases_thenHandlesCorrectly(String input, boolean shouldBlock,
            String description) {
        val inputVal = Value.of(input);

        val result = SanitizationFunctionLibrary.assertNoSqlInjection(inputVal);

        if (shouldBlock) {
            assertThat(result).isInstanceOf(ErrorValue.class);
        } else {
            assertThat(result).isEqualTo(inputVal);
        }
    }

    private static Stream<Arguments> balancedModeLegitimateInputs() {
        return Stream.of(arguments("Abdul Alhazred", "Lovecraftian scholar name"),
                arguments("O'Brien", "Irish surname with apostrophe"),
                arguments("D'Angelo", "Italian surname with apostrophe"),
                arguments("N'Gai", "African name with apostrophe"),
                arguments("Portland OR Seattle", "City names containing OR"),
                arguments("Alexandria AND Cairo", "City names containing AND"),
                arguments("Dunwich", "New England village"), arguments("Innsmouth Harbor", "Coastal town"),
                arguments("What's the access level?", "Contraction in question"),
                arguments("They're authorized", "Contraction in statement"),
                arguments("It's valid", "Common contraction"), arguments("Hasn't been granted", "Negative contraction"),
                arguments("Department: HR-001", "Departmental identifier"),
                arguments("Zone: R'lyeh-Deep", "Location with apostrophe"),
                arguments("Site-42-Alpha", "Alphanumeric site code"), arguments("User@arkham.edu", "Email address"),
                arguments("Access to Elder Things archive", "Description with SQL keyword"),
                arguments("Select appropriate clearance level", "Instruction with SELECT"),
                arguments("Insert keycard to proceed", "Instruction with INSERT"),
                arguments("Drop items at checkpoint", "Instruction with DROP"), arguments("", "Empty string"),
                arguments("   ", "Whitespace only"), arguments("123456", "Pure numeric"),
                arguments("αβγδ", "Greek characters"),
                arguments("Hello, this is a safe string!", "Safe natural language"));
    }

    private static Stream<Arguments> balancedModeInjectionAttempts() {
        return Stream.of(arguments("' OR '1'='1", "Classic always-true injection"),
                arguments("admin' OR '1'='1'--", "Admin bypass with comment"),
                arguments("' OR 'x'='x", "Alternative equality injection"),
                arguments("' AND 'a'='a", "AND-based injection"), arguments("Nyarlathotep'--", "Name with SQL comment"),
                arguments("user'-- ", "Comment with space"), arguments("Cthulhu'#", "Hash-style comment"),
                arguments("test'--comment", "Comment without space"),
                arguments("username'; --", "Username with comment"),
                arguments("1; DROP TABLE necronomicon", "Stacked DROP command"),
                arguments("valid; DELETE FROM cultists", "Stacked DELETE command"),
                arguments("123; INSERT INTO forbidden_tomes VALUES ('Unaussprechlichen')", "Stacked INSERT"),
                arguments("Azathoth; UPDATE entities SET sanity=0", "Stacked UPDATE"),
                arguments("DROP TABLE students;", "Direct DROP TABLE"),
                arguments("SELECT * FROM users; DROP TABLE students;", "Multiple SQL statements"),
                arguments("SELECT * FROM users; DROP TABLE logs;", "Multiple statements with DROP"),
                arguments("1 UNION SELECT password FROM investigators", "Basic UNION injection"),
                arguments("' UNION ALL SELECT null, null, null--", "UNION ALL with nulls"),
                arguments("Shoggoth' UNION SELECT * FROM deep_ones--", "UNION with wildcard"),
                arguments("UNION SELECT username, password FROM users;", "UNION data exfiltration"),
                arguments("1 AND 1=1", "Boolean true condition"), arguments("1 OR 2=2", "Boolean OR condition"),
                arguments("Dagon' AND '1'='1", "Quoted boolean injection"),
                arguments("' OR '5'='5", "Always-true quoted condition"),
                arguments("%27 OR %271%27=%271", "URL-encoded classic injection"),
                arguments("Hastur%27--", "URL-encoded apostrophe with comment"),
                arguments("test%3B DROP TABLE mythos", "URL-encoded semicolon"),
                arguments("user%2D%2Dcomment", "URL-encoded double dash"),
                arguments("%27%23", "URL-encoded apostrophe and hash"),
                arguments("%2d%2d comment", "Lowercase hex-encoded comment"),
                arguments("%2D%2D comment", "Uppercase hex-encoded comment"),
                arguments("0x41646d696e", "Hex-encoded Admin"), arguments("0x44524f50", "Hex-encoded DROP"),
                arguments("user_0x27", "Hex with underscore"),
                arguments("%55%4e%49%4f%4e%20%53%45%4c%45%43%54%20%2a%20%46%52%4f%4d%20%75%73%65%72%73",
                        "Hex-encoded UNION SELECT"),
                arguments("%53%45%4c%45%43%54 * %46%52%4f%4d users", "Partial hex-encoded SELECT"),
                arguments("%44%52%4f%50 %54%41%42%4c%45 students", "Hex-encoded DROP TABLE"),
                arguments("ＳＥＬＥＣＴ * ＦＲＯＭ outer_gods", "Unicode fullwidth SELECT"),
                arguments("ＤＲＯＰ ＴＡＢＬＥ servitors", "Unicode fullwidth DROP"),
                arguments("Yog-Sothoth'; \nDROP TABLE dimensions;--", "Newline with injection"),
                arguments("' oR '1'='1", "Mixed case OR"),
                arguments("ShubNiggurath' UnIoN SeLeCt *", "Mixed case UNION SELECT"),
                arguments("Select * from table where name < 'test-1' and date > 12-12-2000",
                        "Complex SELECT with comparison"));
    }

    private static Stream<Arguments> strictModeValidIdentifiers() {
        return Stream.of(arguments("USER123", "Alphanumeric user ID"),
                arguments("Department456", "Alphanumeric department"), arguments("SHOGGOTH9000", "Entity identifier"),
                arguments("Zone42", "Numeric zone"), arguments("deep-one-alpha", "Hyphenated identifier"),
                arguments("SITE_19", "Underscore identifier"), arguments("Cthulhu-Spawn-7", "Multi-hyphen code"),
                arguments("US", "Country code"), arguments("GB", "Another country code"),
                arguments("RLYEH", "Fictional location without apostrophe"));
    }

    private static Stream<Arguments> strictModeRejectedInputs() {
        return Stream.of(arguments("O'Brien", "Apostrophe in surname"), arguments("It's", "Contraction"),
                arguments("R'lyeh", "Apostrophe in location name"), arguments("Item1; Item2", "Semicolon separator"),
                arguments("Test;", "Trailing semicolon"), arguments("SELECT", "SQL keyword"),
                arguments("UserOrAdmin", "Contains OR keyword"), arguments("Portland OR Seattle", "City names with OR"),
                arguments("Anderson", "Name containing AND"), arguments("user(*)", "Parentheses and asterisk"),
                arguments("value*2", "Multiplication operator"), arguments("func()", "Function call syntax"),
                arguments("test--comment", "Double dash comment"), arguments("value#note", "Hash comment"),
                arguments("%27", "URL-encoded apostrophe"), arguments("0x41", "Hex-encoded value"),
                arguments("user%20name", "URL-encoded space"), arguments("user@example.com", "Email with @ symbol"),
                arguments("' OR '1'='1", "Classic injection"), arguments("1; DROP TABLE", "Stacked query"));
    }

    private static Stream<Arguments> balancedVsStrictComparison() {
        return Stream.of(arguments("O'Brien", false, true, "Name with apostrophe"),
                arguments("Portland OR Seattle", false, true, "Cities with OR"),
                arguments("What's this?", false, true, "Contraction"),
                arguments("USER123", false, false, "Clean identifier"),
                arguments("' OR '1'='1", true, true, "Injection attempt"),
                arguments("admin'--", true, true, "Comment injection"));
    }

    private static Stream<Arguments> edgeCasesAndSecurityTests() {
        return Stream.of(arguments("A".repeat(10000), false, "Very long alphanumeric"),
                arguments("' OR '1'='1".repeat(1000), true, "Repeated injection pattern"),
                arguments("   ' OR '1'='1   ", true, "Injection with leading/trailing spaces"),
                arguments("'\t\tOR\t\t'1'='1", true, "Injection with tabs"),
                arguments("sElEcT * FrOm users", true, "Mixed case complete SQL query"),
                arguments("' or '1'='1", true, "Lowercase injection"),
                arguments("test' AND (SELECT 1)='1", true, "Nested subquery injection"),
                arguments("user'; DROP TABLE x;--", true, "Multiple vectors combined"),
                arguments("admin​' OR '1'='1", true, "Zero-width space obfuscation"));
    }
}
