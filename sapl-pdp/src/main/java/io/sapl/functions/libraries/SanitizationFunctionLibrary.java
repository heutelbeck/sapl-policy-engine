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
package io.sapl.functions.libraries;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.text.Normalizer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Detects SQL injection attempts in policy information point parameters.
 * <p>
 * Based on OWASP SQL Injection Prevention guidelines. OWASP recommends
 * parameterized queries as the primary defense and
 * input validation as secondary defense. This library provides the input
 * validation component.
 */
@UtilityClass
@FunctionLibrary(name = SanitizationFunctionLibrary.NAME, description = SanitizationFunctionLibrary.DESCRIPTION_MD, libraryDocumentation = SanitizationFunctionLibrary.DOCUMENTATION_MD)
public class SanitizationFunctionLibrary {

    public static final String NAME = "sanitize";

    public static final String DESCRIPTION_MD = """
            A library for input sanitization, especially for the detection
            of potential SQL injections in strings.""";

    public static final String DOCUMENTATION_MD = """
            Validates text in PIP parameter expressions to catch SQL injection attempts.

            See OWASP SQL Injection Prevention Guidelines:
            https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html

            OWASP recommends parameterized queries as the primary defense (always use those in your PIPs),
            with input validation as a secondary layer. This library provides the validation layer.

            Consider this expression: ```subject.<some.pip(environment.country)>```. If ```environment.country```
            comes from user input and the PIP builds SQL queries with it, an attacker could inject "US'; DROP TABLE users--"
            to break out of the query. This library detects these patterns and returns an error instead.

            Two functions with different trade-offs:

            assertNoSqlInjection (Balanced)
            Catches injection patterns while allowing normal text. Passes through "O'Brien", "Portland OR Seattle",
            and "What's your name?" but blocks "' OR '1'='1", "admin'--", and "1; DROP TABLE users". Use this for
            names, descriptions, and any other open-ended text.

            assertNoSqlInjectionStrict (Strict)
            Rejects anything with SQL metacharacters or keywords. Passes "JohnDoe" and "Department123" but blocks
            "O'Brien" and "user@example.com". Only use this for codes or identifiers where SQL syntax legitimately
            shouldn't appear.

            Example with HTTP PIP:
            ```
            policy "fetch_user_profile"
            permit action == "read";
                var userId = sanitize.assertNoSqlInjection(environment.userId);
                var request = {
                    "baseUrl": "https://api.example.com",
                    "path": "/users/" + userId
                };
                var user = <http.get(request)>;
                user.department == subject.department;
            ```

            If ```environment.userId``` contains "' OR '1'='1", the sanitization returns an error, the userId
            assignment fails, the where clause evaluates to error, and the policy doesn't apply.

            Example with strict mode for structured identifiers:
            ```
            policy "device_access"
            permit action == "control";
                var deviceId = sanitize.assertNoSqlInjectionStrict(environment.deviceId);
                var request = {
                    "baseUrl": "https://api.example.com",
                    "urlParameters": { "device": deviceId }
                };
                <http.get(request)>.ownerId == subject.id;
            ```

            When to use regex allow-list validation instead:

            If valid inputs match a specific pattern, use the =~ operator directly. This is safer than
            pattern-based detection when possible:

            ```
            // Country codes
            var country = environment.countryCode;
            country =~ "^[A-Z]{2}$";

            // Numeric IDs
            var userId = environment.userId;
            userId =~ "^\\d{1,10}$";

            // Department codes
            var deptCode = environment.deptCode;
            deptCode =~ "^DEPT-[A-Z]{2}-\\d{3}$";
            ```

            Use this library when input is open-ended or when writing an allow-list would be impractical.

            Critical: This is a SECONDARY DEFENSE. Parameterized queries in PIPs are still required as the primary
            defense. Never concatenate user input into SQL strings.

            Complete guide: [OWASP SQL Injection Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html)
            """;

    static final String ERROR_POTENTIAL_SQL_INJECTION_DETECTED = "Potential SQL injection detected in text.";

    private static final Pattern SQL_METACHARACTERS    = Pattern.compile("[';*()@]");
    private static final Pattern SQL_DML_DDL_KEYWORDS  = Pattern.compile(
            "\\b(SELECT|INSERT|DELETE|UPDATE|DROP|UNION|ALTER|EXEC|EXECUTE|TRUNCATE|CREATE|REPLACE)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_LOGICAL_OPERATORS = Pattern.compile("(OR|AND|NOT|XOR)", Pattern.CASE_INSENSITIVE);

    private static final Pattern URL_ENCODED_CHARACTERS = Pattern.compile("%[0-9a-f]{2}", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEX_ENCODED_STRINGS    = Pattern.compile("0x[0-9a-f]{2,}", Pattern.CASE_INSENSITIVE);

    private static final Pattern SQL_COMMENT_MARKERS = Pattern.compile("--|#");

    private static final Pattern SQL_SELECT_FROM_STATEMENT = Pattern
            .compile("\\bSELECT\\s+\\S++(?:\\s*+,\\s*+(?!FROM\\b)\\S++)*+\\s+FROM\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_INSERT_INTO_STATEMENT = Pattern.compile("\\bINSERT\\s+INTO\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_DELETE_FROM_STATEMENT = Pattern.compile("\\bDELETE\\s+FROM\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_UPDATE_SET_STATEMENT  = Pattern.compile("\\bUPDATE\\s+\\w+\\s+SET\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern QUOTE_FOLLOWED_BY_SQL_KEYWORD    = Pattern
            .compile("'\\s*(OR|AND|UNION|SELECT|INSERT|DELETE|UPDATE|DROP|--|#)", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED_BOOLEAN_OR_AND_EXPRESSION = Pattern
            .compile("'\\s*(OR|AND)\\s*'[^']*'\\s*=\\s*'", Pattern.CASE_INSENSITIVE);

    private static final Pattern SQL_COMMENT_WITH_CONTENT = Pattern.compile("(--|#)\\s*(\\w|$)");

    private static final Pattern SEMICOLON_FOLLOWED_BY_SQL_COMMAND = Pattern.compile(
            ";\\s*(SELECT|INSERT|DELETE|UPDATE|DROP|UNION|CREATE|ALTER|TRUNCATE|EXEC|EXECUTE)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DROP_OR_TRUNCATE_TABLE = Pattern.compile("\\b(DROP|TRUNCATE)\\s+TABLE\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATE_OR_ALTER_TABLE  = Pattern.compile("\\b(CREATE|ALTER)\\s+TABLE\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern UNION_SELECT_STATEMENT = Pattern.compile("\\bUNION\\s+(ALL\\s+)?SELECT\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern BOOLEAN_NUMERIC_EQUALITY = Pattern.compile("(OR|AND)\\s+\\d+\\s*=\\s*\\d+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BOOLEAN_STRING_EQUALITY  = Pattern.compile("(OR|AND)\\s+'[^']*'\\s*=\\s*'[^']*'",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SQL_SPECIFIC_URL_ENCODED_CHARS = Pattern.compile("%27|%3B|%2D%2D|%23",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LONG_URL_ENCODED_SEQUENCES     = Pattern.compile("(%[0-9a-f]{2}){3,}",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern[] STRICT_SQL_PATTERNS = { SQL_METACHARACTERS, SQL_COMMENT_MARKERS,
            SQL_DML_DDL_KEYWORDS, SQL_LOGICAL_OPERATORS, URL_ENCODED_CHARACTERS, HEX_ENCODED_STRINGS };

    private static final Pattern[] BALANCED_SQL_PATTERNS = { SQL_SELECT_FROM_STATEMENT, SQL_INSERT_INTO_STATEMENT,
            SQL_DELETE_FROM_STATEMENT, SQL_UPDATE_SET_STATEMENT, QUOTE_FOLLOWED_BY_SQL_KEYWORD,
            QUOTED_BOOLEAN_OR_AND_EXPRESSION, SQL_COMMENT_WITH_CONTENT, SEMICOLON_FOLLOWED_BY_SQL_COMMAND,
            DROP_OR_TRUNCATE_TABLE, CREATE_OR_ALTER_TABLE, UNION_SELECT_STATEMENT, BOOLEAN_NUMERIC_EQUALITY,
            BOOLEAN_STRING_EQUALITY, SQL_SPECIFIC_URL_ENCODED_CHARS, LONG_URL_ENCODED_SEQUENCES, HEX_ENCODED_STRINGS };

    private static final Predicate<String> STRICT_SQL_INJECTION_PREDICATE   = createInjectionPredicate(
            STRICT_SQL_PATTERNS);
    private static final Predicate<String> BALANCED_SQL_INJECTION_PREDICATE = createInjectionPredicate(
            BALANCED_SQL_PATTERNS);

    private static Predicate<String> createInjectionPredicate(Pattern[] patterns) {
        return userInput -> {
            if (userInput == null || userInput.isEmpty()) {
                return false;
            }
            val normalizedInput = Normalizer.normalize(userInput, Normalizer.Form.NFKC);
            for (Pattern pattern : patterns) {
                if (pattern.matcher(normalizedInput).find()) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * Checks text for SQL injection patterns using balanced detection.
     *
     * @param inputToSanitize
     * the text to validate
     *
     * @return the input unchanged if clean, or an ErrorValue if injection patterns
     * are detected
     */
    @Function(docs = """
            ```sanitize.assertNoSqlInjection(TEXT inputToSanitize)```

            Checks text for SQL injection patterns using balanced detection. Catches injection attempts while
            allowing normal text that happens to contain SQL-like words or punctuation.

            Detects complete SQL statements (SELECT...FROM, INSERT INTO, etc.), injection patterns like quoted
            expressions and stacked queries, and encoding tricks (URL-encoded, hex-encoded, Unicode obfuscation).
            At the same time, it allows apostrophes in names (O'Brien, D'Angelo), SQL keywords used normally
            (Portland OR Seattle), and contractions (What's your name?).

            Takes a TEXT value and returns it unchanged if clean, or an error if injection patterns are detected.

            These inputs pass through:
            ```
            sanitize.assertNoSqlInjection("O'Brien")                    // Apostrophe in name
            sanitize.assertNoSqlInjection("Portland OR Seattle")        // OR as word
            sanitize.assertNoSqlInjection("What's your name?")          // Contractions
            sanitize.assertNoSqlInjection("Department: HR-001")         // Structured data
            ```

            These get blocked:
            ```
            sanitize.assertNoSqlInjection("' OR '1'='1")                // Classic injection
            sanitize.assertNoSqlInjection("admin'--")                   // Comment injection
            sanitize.assertNoSqlInjection("1; DROP TABLE users")        // Stacked query
            sanitize.assertNoSqlInjection("1' UNION SELECT * FROM")     // Union injection
            sanitize.assertNoSqlInjection("SELECT * FROM users")        // Complete SQL query
            ```

            Example usage:
            ```
            policy "fetch_user_profile"
            permit action == "read";
                var userId = sanitize.assertNoSqlInjection(environment.userId);
                var request = {
                    "baseUrl": "https://api.example.com",
                    "path": "/users/" + userId
                };
                var user = <http.get(request)>;
                user.department == subject.department;
            ```

            Use assertNoSqlInjectionStrict if the input should be a structured identifier where SQL syntax never belongs.
            """)
    public static Value assertNoSqlInjection(TextValue inputToSanitize) {
        val potentialInjectionDetected = BALANCED_SQL_INJECTION_PREDICATE.test(inputToSanitize.value());
        if (potentialInjectionDetected) {
            return Value.error(ERROR_POTENTIAL_SQL_INJECTION_DETECTED);
        }
        return inputToSanitize;
    }

    /**
     * Checks text using strict detection.
     *
     * @param inputToSanitize
     * the text to validate
     *
     * @return the input unchanged if clean, or an ErrorValue if any SQL syntax is
     * found
     */
    @Function(docs = """
            ```sanitize.assertNoSqlInjectionStrict(TEXT inputToSanitize)```

            Checks text using strict detection. Rejects anything with SQL metacharacters or keywords, even when harmless.
            Gives maximum security but produces false positives on legitimate text.

            Blocks single quotes, semicolons, SQL metacharacters, SQL keywords (SELECT, INSERT, DROP), logical
            operators (OR, AND, NOT), and URL-encoded characters. Only safe for structured identifiers that shouldn't
            contain SQL syntax.

            Takes a TEXT value and returns it unchanged if clean, or an error if any SQL syntax is found.

            These inputs pass through:
            ```
            sanitize.assertNoSqlInjectionStrict("USER123")              // Alphanumeric ID
            sanitize.assertNoSqlInjectionStrict("dept-hr")              // Hyphenated code
            sanitize.assertNoSqlInjectionStrict("US")                   // Country code
            ```

            These get rejected (even though some are harmless):
            ```
            sanitize.assertNoSqlInjectionStrict("O'Brien")              // Apostrophe (blocked)
            sanitize.assertNoSqlInjectionStrict("Portland OR Seattle")  // Contains OR (blocked)
            sanitize.assertNoSqlInjectionStrict("user@email.com")       // Special chars (blocked)
            ```

            Example usage:
            ```
            policy "device_access"
            permit action == "control";
                var deviceId = sanitize.assertNoSqlInjectionStrict(environment.deviceId);
                var request = {
                    "baseUrl": "https://api.example.com",
                    "urlParameters": { "device": deviceId }
                };
                <http.get(request)>.ownerId == subject.id;
            ```

            Zero tolerance for SQL syntax means higher security but more false positives. It rejects legitimate text
            with apostrophes or SQL-like words. Only use this when input should be a code or identifier where SQL
            syntax legitimately shouldn't appear.

            For natural language or user names, use assertNoSqlInjection instead.
            """)
    public static Value assertNoSqlInjectionStrict(TextValue inputToSanitize) {
        val potentialInjectionDetected = STRICT_SQL_INJECTION_PREDICATE.test(inputToSanitize.value());
        if (potentialInjectionDetected) {
            return Value.error(ERROR_POTENTIAL_SQL_INJECTION_DETECTED);
        }
        return inputToSanitize;
    }
}
