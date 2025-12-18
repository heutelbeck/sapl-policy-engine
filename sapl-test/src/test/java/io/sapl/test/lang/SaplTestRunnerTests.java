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
package io.sapl.test.lang;

import static io.sapl.compiler.StringsUtil.unquoteString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SaplTestRunnerTests {

    @Test
    void whenRunningTest_thenReturnsResults() {
        var testDefinition = """
                requirement "basic test" {
                    scenario "scenario one"
                        given
                            - policy "policy.sapl"
                        when subject "user" attempts action "read" on resource "data"
                        expect permit;
                }
                """;

        var ast     = SaplTestParser.parse(testDefinition);
        var results = SaplTestRunner.run(ast);

        // Currently returns error results because execution is not yet implemented
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().requirementName()).isEqualTo("basic test");
        assertThat(results.getFirst().scenarioName()).isEqualTo("scenario one");
    }

    @Test
    void whenMultipleScenarios_thenReturnsResultForEach() {
        var testDefinition = """
                requirement "multi-scenario" {
                    scenario "first"
                        when subject "a" attempts action "b" on resource "c"
                        expect permit;

                    scenario "second"
                        when subject "x" attempts action "y" on resource "z"
                        expect deny;
                }
                """;

        var ast     = SaplTestParser.parse(testDefinition);
        var results = SaplTestRunner.run(ast);

        assertThat(results).hasSize(2);
    }

    static Stream<Arguments> unquoteStringTestCases() {
        return Stream.of(arguments("\"double quoted\"", "double quoted"), arguments("'single quoted'", "single quoted"),
                arguments("no quotes", "no quotes"), arguments("\"\"", ""), arguments("''", ""), arguments(null, null),
                arguments("x", "x"));
    }

    @ParameterizedTest
    @MethodSource("unquoteStringTestCases")
    void whenUnquoteString(String input, String expected) {
        assertThat(unquoteString(input)).isEqualTo(expected);
    }

    @Test
    void whenTestResultPassed_thenHasCorrectStatus() {
        var result = TestResult.passed("req", "scenario");

        assertThat(result.isPassed()).isTrue();
        assertThat(result.isFailed()).isFalse();
        assertThat(result.isError()).isFalse();
        assertThat(result.fullName()).isEqualTo("req > scenario");
    }

    @Test
    void whenTestResultFailed_thenHasCorrectStatus() {
        var result = TestResult.failed("req", "scenario", "assertion failed");

        assertThat(result.isPassed()).isFalse();
        assertThat(result.isFailed()).isTrue();
        assertThat(result.isError()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("assertion failed");
    }

    @Test
    void whenTestResultError_thenHasCorrectStatus() {
        var exception = new RuntimeException("something broke");
        var result    = TestResult.error("req", "scenario", exception);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.isFailed()).isFalse();
        assertThat(result.isError()).isTrue();
        assertThat(result.exception()).isSameAs(exception);
    }

}
