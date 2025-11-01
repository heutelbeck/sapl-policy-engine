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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.sapl.assertj.SaplAssertions.assertThatVal;
import static io.sapl.functions.SanitizationFunctionLibrary.POTENTIAL_SQL_INJECTION_DETECTED;

class SanitizationFunctionLibraryTests {

    // ==================== Balanced Mode Tests ====================

    static Stream<Arguments> balancedModeLegitimateInputs() {
        return Stream.of(
                // Names with apostrophes - common in surnames
                Arguments.of("Abdul Alhazred", "Lovecraftian scholar name"),
                Arguments.of("O'Brien", "Irish surname with apostrophe"),
                Arguments.of("D'Angelo", "Italian surname with apostrophe"),
                Arguments.of("N'Gai", "African name with apostrophe"),

                // Geographic locations with SQL-like words
                Arguments.of("Portland OR Seattle", "City names containing OR"),
                Arguments.of("Alexandria AND Cairo", "City names containing AND"),
                Arguments.of("Dunwich", "New England village"),
                Arguments.of("Innsmouth Harbor", "Coastal town"),

                // Contractions and natural language
                Arguments.of("What's the access level?", "Contraction in question"),
                Arguments.of("They're authorized", "Contraction in statement"),
                Arguments.of("It's valid", "Common contraction"),
                Arguments.of("Hasn't been granted", "Negative contraction"),

                // Structured data without injection patterns
                Arguments.of("Department: HR-001", "Departmental identifier"),
                Arguments.of("Zone: R'lyeh-Deep", "Location with apostrophe"),
                Arguments.of("Site-42-Alpha", "Alphanumeric site code"),
                Arguments.of("User@arkham.edu", "Email address"),

                // Descriptive text
                Arguments.of("Access to Elder Things archive", "Description with SQL keyword"),
                Arguments.of("Select appropriate clearance level", "Instruction with SELECT"),
                Arguments.of("Insert keycard to proceed", "Instruction with INSERT"),
                Arguments.of("Drop items at checkpoint", "Instruction with DROP"),

                // Edge cases
                Arguments.of("", "Empty string"), Arguments.of("   ", "Whitespace only"),
                Arguments.of("123456", "Pure numeric"), Arguments.of("αβγδ", "Greek characters"),
                Arguments.of("Hello, this is a safe string!", "Safe natural language"));
    }

    static Stream<Arguments> balancedModeInjectionAttempts() {
        return Stream.of(
                // Classic quote-based injections
                Arguments.of("' OR '1'='1", "Classic always-true injection"),
                Arguments.of("admin' OR '1'='1'--", "Admin bypass with comment"),
                Arguments.of("' OR 'x'='x", "Alternative equality injection"),
                Arguments.of("' AND 'a'='a", "AND-based injection"),

                // Comment-based injections
                Arguments.of("Nyarlathotep'--", "Name with SQL comment"),
                Arguments.of("user'-- ", "Comment with space"), Arguments.of("Cthulhu'#", "Hash-style comment"),
                Arguments.of("test'--comment", "Comment without space"),
                Arguments.of("username'; --", "Username with comment"),

                // Stacked queries
                Arguments.of("1; DROP TABLE necronomicon", "Stacked DROP command"),
                Arguments.of("valid; DELETE FROM cultists", "Stacked DELETE command"),
                Arguments.of("123; INSERT INTO forbidden_tomes VALUES ('Unaussprechlichen')", "Stacked INSERT"),
                Arguments.of("Azathoth; UPDATE entities SET sanity=0", "Stacked UPDATE"),
                Arguments.of("DROP TABLE students;", "Direct DROP TABLE"),
                Arguments.of("SELECT * FROM users; DROP TABLE students;", "Multiple SQL statements"),
                Arguments.of("SELECT * FROM users; DROP TABLE logs;", "Multiple statements with DROP"),

                // Union-based injections
                Arguments.of("1 UNION SELECT password FROM investigators", "Basic UNION injection"),
                Arguments.of("' UNION ALL SELECT null, null, null--", "UNION ALL with nulls"),
                Arguments.of("Shoggoth' UNION SELECT * FROM deep_ones--", "UNION with wildcard"),
                Arguments.of("UNION SELECT username, password FROM users;", "UNION data exfiltration"),

                // Boolean blind injections
                Arguments.of("1 AND 1=1", "Boolean true condition"), Arguments.of("1 OR 2=2", "Boolean OR condition"),
                Arguments.of("Dagon' AND '1'='1", "Quoted boolean injection"),
                Arguments.of("' OR '5'='5", "Always-true quoted condition"),

                // URL-encoded attacks
                Arguments.of("%27 OR %271%27=%271", "URL-encoded classic injection"),
                Arguments.of("Hastur%27--", "URL-encoded apostrophe with comment"),
                Arguments.of("test%3B DROP TABLE mythos", "URL-encoded semicolon"),
                Arguments.of("user%2D%2Dcomment", "URL-encoded double dash"),
                Arguments.of("%27%23", "URL-encoded apostrophe and hash"),
                Arguments.of("%2d%2d comment", "Lowercase hex-encoded comment"),
                Arguments.of("%2D%2D comment", "Uppercase hex-encoded comment"),

                // Hex-encoded obfuscation
                Arguments.of("0x41646d696e", "Hex-encoded 'Admin'"), Arguments.of("0x44524f50", "Hex-encoded 'DROP'"),
                Arguments.of("user_0x27", "Hex with underscore"),
                Arguments.of("%55%4e%49%4f%4e%20%53%45%4c%45%43%54%20%2a%20%46%52%4f%4d%20%75%73%65%72%73",
                        "Hex-encoded UNION SELECT"),
                Arguments.of("%53%45%4c%45%43%54 * %46%52%4f%4d users", "Partial hex-encoded SELECT"),
                Arguments.of("%44%52%4f%50 %54%41%42%4c%45 students", "Hex-encoded DROP TABLE"),

                // Unicode obfuscation (normalized by NFKC)
                Arguments.of("\uFF33\uFF25\uFF2C\uFF25\uFF23\uFF34 * \uFF26\uFF32\uFF2F\uFF2D outer_gods",
                        "Unicode fullwidth SELECT"),
                Arguments.of("\uFF24\uFF32\uFF2F\uFF30 \uFF34\uFF21\uFF22\uFF2C\uFF25 servitors",
                        "Unicode fullwidth DROP"),

                // Multi-line injections
                Arguments.of("Yog-Sothoth'; \nDROP TABLE dimensions;--", "Newline with injection"),

                // Case variations (patterns are case-insensitive)
                Arguments.of("' oR '1'='1", "Mixed case OR"),
                Arguments.of("ShubNiggurath' UnIoN SeLeCt *", "Mixed case UNION SELECT"),

                // Complex queries with comparison operators
                Arguments.of("Select * from table where name < 'test-1' and date > 12-12-2000",
                        "Complex SELECT with comparison"));
    }

    @ParameterizedTest(name = "[{index}] {1}: {0}")
    @MethodSource("balancedModeLegitimateInputs")
    void balancedMode_shouldAllowLegitimateInput(String input, String description) {
        final var inputVal = Val.of(input);
        final var result   = SanitizationFunctionLibrary.assertNoSqlInjection(inputVal);

        assertThatVal(result).isEqualTo(inputVal);
    }

    @ParameterizedTest(name = "[{index}] {1}: {0}")
    @MethodSource("balancedModeInjectionAttempts")
    void balancedMode_shouldBlockInjectionAttempts(String input, String description) {
        final var inputVal = Val.of(input);
        final var result   = SanitizationFunctionLibrary.assertNoSqlInjection(inputVal);

        assertThatVal(result).isError(POTENTIAL_SQL_INJECTION_DETECTED);
    }

    @Test
    void balancedMode_shouldHandleEmptyString() {
        final var result = SanitizationFunctionLibrary.assertNoSqlInjection(Val.of(""));

        assertThatVal(result).isEqualTo(Val.of(""));
    }

    // ==================== Strict Mode Tests ====================

    static Stream<Arguments> strictModeValidIdentifiers() {
        return Stream.of(
                // Pure alphanumeric
                Arguments.of("USER123", "Alphanumeric user ID"),
                Arguments.of("Department456", "Alphanumeric department"),
                Arguments.of("SHOGGOTH9000", "Entity identifier"), Arguments.of("Zone42", "Numeric zone"),

                // With allowed separators (hyphen, underscore are not SQL syntax)
                Arguments.of("deep-one-alpha", "Hyphenated identifier"),
                Arguments.of("SITE_19", "Underscore identifier"), Arguments.of("Cthulhu-Spawn-7", "Multi-hyphen code"),

                // Country/region codes
                Arguments.of("US", "Country code"), Arguments.of("GB", "Another country code"),
                Arguments.of("RLYEH", "Fictional location without apostrophe"));
    }

    static Stream<Arguments> strictModeRejectedInputs() {
        return Stream.of(
                // Apostrophes (even in legitimate names)
                Arguments.of("O'Brien", "Apostrophe in surname"), Arguments.of("It's", "Contraction"),
                Arguments.of("R'lyeh", "Apostrophe in location name"),

                // Semicolons
                Arguments.of("Item1; Item2", "Semicolon separator"), Arguments.of("Test;", "Trailing semicolon"),

                // SQL keywords
                Arguments.of("SELECT", "SQL keyword"), Arguments.of("UserOrAdmin", "Contains OR keyword"),
                Arguments.of("Portland OR Seattle", "City names with OR"),
                Arguments.of("Anderson", "Name containing AND"),

                // SQL metacharacters
                Arguments.of("user(*)", "Parentheses and asterisk"), Arguments.of("value*2", "Multiplication operator"),
                Arguments.of("func()", "Function call syntax"),

                // Comments
                Arguments.of("test--comment", "Double dash comment"), Arguments.of("value#note", "Hash comment"),

                // Encoded characters
                Arguments.of("%27", "URL-encoded apostrophe"), Arguments.of("0x41", "Hex-encoded value"),
                Arguments.of("user%20name", "URL-encoded space"),

                // Email addresses
                Arguments.of("user@example.com", "Email with @ symbol"),

                // Actual injection attempts (obviously blocked)
                Arguments.of("' OR '1'='1", "Classic injection"), Arguments.of("1; DROP TABLE", "Stacked query"));
    }

    @ParameterizedTest(name = "[{index}] {1}: {0}")
    @MethodSource("strictModeValidIdentifiers")
    void strictMode_shouldAllowCleanIdentifiers(String input, String description) {
        final var inputVal = Val.of(input);
        final var result   = SanitizationFunctionLibrary.assertNoSqlInjectionStrict(inputVal);

        assertThatVal(result).isEqualTo(inputVal);
    }

    @ParameterizedTest(name = "[{index}] {1}: {0}")
    @MethodSource("strictModeRejectedInputs")
    void strictMode_shouldRejectAnySqlSyntax(String input, String description) {
        final var inputVal = Val.of(input);
        final var result   = SanitizationFunctionLibrary.assertNoSqlInjectionStrict(inputVal);

        assertThatVal(result).isError(POTENTIAL_SQL_INJECTION_DETECTED);
    }

    @Test
    void strictMode_shouldHandleEmptyString() {
        final var result = SanitizationFunctionLibrary.assertNoSqlInjectionStrict(Val.of(""));

        assertThatVal(result).isEqualTo(Val.of(""));
    }

    // ==================== Comparative Tests ====================

    static Stream<Arguments> balancedVsStrictComparison() {
        return Stream.of(Arguments.of("O'Brien", false, true, "Name with apostrophe"),
                Arguments.of("Portland OR Seattle", false, true, "Cities with OR"),
                Arguments.of("What's this?", false, true, "Contraction"),
                Arguments.of("USER123", false, false, "Clean identifier"),
                Arguments.of("' OR '1'='1", true, true, "Injection attempt"),
                Arguments.of("admin'--", true, true, "Comment injection"));
    }

    @ParameterizedTest(name = "[{index}] {3}: {0}")
    @MethodSource("balancedVsStrictComparison")
    void compareModes_behaviorDifferences(String input, boolean balancedBlocks, boolean strictBlocks,
            String description) {
        final var inputVal       = Val.of(input);
        final var balancedResult = SanitizationFunctionLibrary.assertNoSqlInjection(inputVal);
        final var strictResult   = SanitizationFunctionLibrary.assertNoSqlInjectionStrict(inputVal);

        if (balancedBlocks) {
            assertThatVal(balancedResult).as("Balanced mode should block: " + description)
                    .isError(POTENTIAL_SQL_INJECTION_DETECTED);
        } else {
            assertThatVal(balancedResult).as("Balanced mode should allow: " + description).isEqualTo(inputVal);
        }

        if (strictBlocks) {
            assertThatVal(strictResult).as("Strict mode should block: " + description)
                    .isError(POTENTIAL_SQL_INJECTION_DETECTED);
        } else {
            assertThatVal(strictResult).as("Strict mode should allow: " + description).isEqualTo(inputVal);
        }
    }

    // ==================== Edge Cases & Security ====================

    static Stream<Arguments> edgeCasesAndSecurityTests() {
        return Stream.of(
                // Extremely long inputs
                Arguments.of("A".repeat(10000), false, "Very long alphanumeric"),
                Arguments.of("' OR '1'='1".repeat(1000), true, "Repeated injection pattern"),

                // Whitespace variations
                Arguments.of("   ' OR '1'='1   ", true, "Injection with leading/trailing spaces"),
                Arguments.of("'\t\tOR\t\t'1'='1", true, "Injection with tabs"),

                // Case sensitivity validation
                Arguments.of("sElEcT * FrOm users", true, "Mixed case complete SQL query"),
                Arguments.of("' or '1'='1", true, "Lowercase injection"),

                // Nested patterns
                Arguments.of("test' AND (SELECT 1)='1", true, "Nested subquery injection"),

                // Multiple injection vectors in one string
                Arguments.of("user'; DROP TABLE x;--", true, "Multiple vectors combined"),

                // Zero-width and invisible characters (normalized by NFKC)
                Arguments.of("admin​' OR '1'='1", true, "Zero-width space obfuscation"));
    }

    @ParameterizedTest(name = "[{index}] {2}: {0}")
    @MethodSource("edgeCasesAndSecurityTests")
    void edgeCases_shouldHandleCorrectly(String input, boolean shouldBlock, String description) {
        final var inputVal = Val.of(input);
        final var result   = SanitizationFunctionLibrary.assertNoSqlInjection(inputVal);

        if (shouldBlock) {
            assertThatVal(result).as(description).isError(POTENTIAL_SQL_INJECTION_DETECTED);
        } else {
            assertThatVal(result).as(description).isEqualTo(inputVal);
        }
    }
}
