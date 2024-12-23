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
            A library for input sanitization, e.g.,
            detection of potential SQL injections in strings.""";

    public static final String DOCUMENTATION_MD = """
            This library contains some functions to guard policy information points from injection attacks by
            wrapping text values in an expression handed down as parameters of the policy information points.

            For example, in the expression ```subject.<some.pip(environment.country)>``` the value of ```environment.country```
            maybe based on user input. In case the attribute ```<some.pip(parameter)>``` is implemented by building a
            SQL query containing the text value of the ```parameter```, the policy information point may be susceptible to
            an SQL injection attack.

            A common approach to mitigate against this class of attacks is to sanitize the input and to check for suspicious
            patterns in the provided user input.

            This library implements functions, that can be used to wrap a text value. In case the contents of the text appears
            to be suspicious, the function returns an error value. Else, it simply returns the original value.

            For example, the expression
            ```subject.<some.pip(sanitize.assertNoSqlKeywords("Robert'); DROP TABLES students;--"))>```
            will return an error to be handed over to the policy information point, instead of the provided text value.
            The error will be handled and potentially be propagated, where appropriate.
            """;

    static final String KEYWORD_FOUND_ERROR            = """
            Input sanitization error. Detected potential SQL injection attack: \\
            SQL keyword found in input string.""";
    static final String CONTROL_CHARACTERS_FOUND_ERROR = """
            Input sanitization error. Detected potential SQL injection attack: \\
            SQL control character found in input string.""";

    // true if SQL contains something except numbers, letters, less and greater
    // than, star, equal, prime, whitespace, brackets, comma, minus
    private static final String REGEX_CONTROL_CHARS = "(?i)^(?![0-9a-z<>*='\\s(),-]*$).*$";

    // true if SQL contains update, delete...
    private static final String REGEX_KEYWORDS = "(?i).*\\b(UPDATE|DELETE|TRUNCATE|DROP|ALTER|CREATE|INSERT|MERGE|CALL|EXEC|RENAME|SET|BEGIN|COMMIT|ROLLBACK|GRANT)\\b.*";

    private static final Predicate<String> patternControlChars = Pattern.compile(REGEX_CONTROL_CHARS)
            .asMatchPredicate();
    private static final Predicate<String> patternSelect       = Pattern.compile(REGEX_KEYWORDS).asMatchPredicate();

    @Function(docs = """
            ```sanitize.assertNoSql(TEXT inputToSanitize)```:
            Returns an error, if ```inputToSanitize``` contains any SQL control characters except
            ```<```, ```>```, ```*```, ```=```, ```'```, ```(```, ```)```, ```,```, ```-``` and whitespaces or if it contains
            any of the following SQL keywords: ```UPDATE```, ```DELETE```, ```TRUNCATE```, ```DROP```, ```ALTER```,
            ```CREATE```, ```INSERT```, ```MERGE```, ```CALL```, ```EXEC```, ```RENAME```, ```SET```, ```BEGIN```,
            ```COMMIT```, ```ROLLBACK```, ```GRANT```.

            If none of these text components indicating a potential SQL injections attack are present, the function returns
            ```inputToSanitize```.

            **Example:**

            The expression
            ```subject.<some.pip(sanitize.assertNoSqlKeywords("Robert'); DROP TABLES students;--"))>```
            will return an error to be handed over to the policy information point, instead of the provided text value.
            The error will be handled and potentially be propagated, where appropriate.
            """)
    public Val assertNoSql(@Text Val inputToSanitize) {
        final var noControlChars = assertNoSqlControlChars(inputToSanitize);
        if (noControlChars.isError()) {
            return noControlChars;
        }
        return assertNoSqlKeywords(inputToSanitize);
    }

    @Function(docs = """
            ```sanitize.assertNoSqlKeywords(TEXT inputToSanitize)```:
            Returns an error, if ```inputToSanitize```  contains any SQL control characters except
            ```<```, ```>```, ```*```, ```=```, ```'```, ```(```, ```)```, ```,```, ```-``` and whitespaces.

            If none of these text components indicating a potential SQL injections attack are present, the function returns
            ```inputToSanitize```.

            **Example:**

            The expression
            ```subject.<some.pip(sanitize.assertNoSqlControlChars("Select * from table where name = @setvalue = 1"))>```
            will return an error to be handed over to the policy information point, instead of the provided text value.
            The error will be handled and potentially be propagated, where appropriate.
            """)
    public Val assertNoSqlControlChars(@Text Val inputToSanitize) {
        return validate(inputToSanitize, patternControlChars, CONTROL_CHARACTERS_FOUND_ERROR);
    }

    @Function(docs = """
            ```sanitize.assertNoSqlKeywords(TEXT inputToSanitize)```:
            Returns an error, if ```inputToSanitize``` contains
            any of the following SQL keywords: ```UPDATE```, ```DELETE```, ```TRUNCATE```, ```DROP```, ```ALTER```,
            ```CREATE```, ```INSERT```, ```MERGE```, ```CALL```, ```EXEC```, ```RENAME```, ```SET```, ```BEGIN```,
            ```COMMIT```, ```ROLLBACK```, ```GRANT```.

            If none of these text components indicating a potential SQL injections attack are present, the function returns
            ```inputToSanitize```.

            **Example:**

            The expression
            ```subject.<some.pip(sanitize.assertNoSqlKeywords("Robert'); DROP TABLES students;--"))>```
            will return an error to be handed over to the policy information point, instead of the provided text value.
            The error will be handled and potentially be propagated, where appropriate.
            """)
    public Val assertNoSqlKeywords(@Text Val inputToSanitize) {
        return validate(inputToSanitize, patternSelect, KEYWORD_FOUND_ERROR);
    }

    private Val validate(Val input, Predicate<String> pattern, String errorMessage) {
        if (pattern.test(input.getText())) {
            return Val.error(errorMessage);
        } else {
            return input;
        }
    }
}
