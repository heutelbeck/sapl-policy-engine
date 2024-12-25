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
package io.sapl.functions.sanitization;

import static io.sapl.assertj.SaplAssertions.assertThatVal;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.interpreter.Val;

class SanitizationFunctionLibraryTests {

    static Stream<Arguments> sqlInjectionTestCases() {
        // @formatter:off
        return Stream.of(
            Arguments.of("Select * from table where name < 'test-1' and date > 12-12-2000", true),
            Arguments.of("DROP TABLE students;", true),
            Arguments.of("' OR '1'='1", true),
            Arguments.of("%27 OR %271%3D%271", true), // Encoded version of ' OR '1'='1
            Arguments.of("Hello, this is a safe string!", false),
            Arguments.of("SELECT * FROM users; DROP TABLE students;", true),
            Arguments.of("UNION SELECT username, password FROM users;", true),
            Arguments.of("username'; --", true),
            Arguments.of("SELECT * FROM users; DROP TABLE logs;", true),
            Arguments.of("%55%4e%49%4f%4e%20%53%45%4c%45%43%54%20%2a%20%46%52%4f%4d%20%75%73%65%72%73", true), // Hex-encoded UNION SELECT * FROM users
            Arguments.of("%53%45%4c%45%43%54 * %46%52%4f%4d users", true), // Hex-encoded SELECT * FROM users
            Arguments.of("%44%52%4f%50 %54%41%42%4c%45 students", true), // Hex-encoded DROP TABLE students
            Arguments.of("%2d%2d comment", true), // Encoded -- comment
            Arguments.of("ＳＥＬＥＣＴ * ＦＲＯＭ users;", true), // Unicode obfuscation of SELECT * FROM users
            Arguments.of("ＤＲＯＰ ＴＡＢＬＥ students;", true) // Unicode obfuscation of DROP TABLE students
            );
        // @formatter:on
    }

    @ParameterizedTest
    @MethodSource("sqlInjectionTestCases")
    void testSqlInjectionPatterns(String input, boolean isInjection) {
        final var inputVal       = Val.of(input);
        final var sanitizedInput = SanitizationFunctionLibrary.assertNoSqlInjection(inputVal);
        if (isInjection) {
            assertThatVal(sanitizedInput).isError(SanitizationFunctionLibrary.POTENTIAL_SQL_INJECTION_DETECTED_IN_TEXT);
        } else {
            assertThatVal(sanitizedInput).isEqualTo(inputVal);
        }
    }
}
