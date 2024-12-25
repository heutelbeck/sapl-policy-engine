/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions.sanitization;

import java.util.function.Predicate;
import java.util.regex.Pattern;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;

@UtilityClass
@FunctionLibrary(name = SanitizationFunctionLibrary.NAME, description = SanitizationFunctionLibrary.DESCRIPTION_MD)
public class SanitizationFunctionLibrary {

    public static final String NAME = "sanitize";

    public static final String DESCRIPTION_MD = """
            A library for input sanitization, expecially for the detection
            of potential SQL injections in strings.""";

    public static final String DOCUMENTATION_MD = """
            This library contains functions to guard policy information points from injection attacks by
            wrapping text values in expressions handed down as parameters of the policy information points.

            For example, in the expression ```subject.<some.pip(environment.country)>``` the value of ```environment.country```
            maybe based on user input. In case the attribute ```<some.pip(parameter)>``` is implemented by building a
            SQL query containing the text value of the ```parameter```, the policy information point may be susceptible to
            an SQL injection attack.

            A common approach to mitigate against this class of attacks is to sanitize the input and check for suspicious
            patterns in the provided user input.

            This library implements functions to wrap a text value. In case the contents of the text appear
            suspicious, the function returns an error value. Otherwise, it simply returns the original value.

            For example, the expression
            ```subject.<some.pip(sanitize.assertNoSqlInjection("Robert'); DROP TABLES students;--"))>```
            will return an error that will be handed over to the policy information point instead of the provided text value.
            The error will be handled and potentially propagated where appropriate.
            """;

    private static final Pattern[] SQL_INJECTION_PATTERNS                   = {
            // Detects single quotes, semicolons, or comment indicators that may terminate a
            // query
            Pattern.compile(".*([';]).*", Pattern.CASE_INSENSITIVE),

            // Detects SQL comments using -- or #
            Pattern.compile(".*(--|#).*", Pattern.CASE_INSENSITIVE),

            // Detects logical operators OR and AND, which are often used in injection
            // attacks
            Pattern.compile(".*(\\bOR\\b|\\bAND\\b).*", Pattern.CASE_INSENSITIVE),

            // Detects common SQL keywords associated with data manipulation or schema
            // modification
            Pattern.compile(".*\\b(SELECT|INSERT|DELETE|UPDATE|DROP|UNION|ALTER|EXEC|EXECUTE)\\b.*",
                    Pattern.CASE_INSENSITIVE),

            // Detects special SQL-related characters like *, parentheses, and semicolons
            Pattern.compile("[\\*();]", Pattern.CASE_INSENSITIVE),

            // Detects logical operators (OR/AND) surrounded by quotes, which is a common
            // injection pattern
            Pattern.compile(".*(['\"][\\s]*\\b(OR|AND)\\b[\\s]*['\"]).*", Pattern.CASE_INSENSITIVE),

            // Detects encoded inputs like %27 (') or %3B (;)
            Pattern.compile(".*(%[0-9A-Fa-f]{2}|0x[0-9A-Fa-f]+).*", Pattern.CASE_INSENSITIVE) };
    static final String            POTENTIAL_SQL_INJECTION_DETECTED_IN_TEXT = "Potential SQL injection detected in text";

    private static final Predicate<String> SQL_INJECTION_PREDICATE = userInput -> {
        if (userInput == null || userInput.isEmpty()) {
            return false;
        }
        // Normalize input to handle Unicode obfuscation
        final var normalizedInput = java.text.Normalizer.normalize(userInput, java.text.Normalizer.Form.NFKC);
        for (Pattern regex : SQL_INJECTION_PATTERNS) {
            final var matcher = regex.matcher(normalizedInput);
            if (matcher.matches()) {
                return true;
            }
        }
        return false;
    };

    @Function(docs = """
            ```sanitize.assertNoSqlInjection(TEXT inputToSanitize)```
            Checks the provided text for patterns commonly associated with SQL injection attacks.
            If any suspicious patterns are detected, the function returns an error.

            **Parameters:**
            - `inputToSanitize` (TEXT): The input text to be analyzed.

            **Returns:**
            - The original input if no suspicious patterns are detected.
            - An error if the input contains patterns associated with SQL injection attacks.

            **Examples:**
            - Safe Input: ```sanitize.assertNoSqlInjection("Hello World")``` returns "Hello World".
            - Injection Attempt: ```sanitize.assertNoSqlInjection("' OR '1'='1")``` returns an error.
            """)
    public Val assertNoSqlInjection(@Text Val inputToSanitize) {
        final var potentialInjectionDetected = SQL_INJECTION_PREDICATE.test(inputToSanitize.getText());
        if (potentialInjectionDetected) {
            return Val.error(POTENTIAL_SQL_INJECTION_DETECTED_IN_TEXT);
        }
        return inputToSanitize;
    }
}
