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
package io.sapl.test.plain;

import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("PlainTestAdapter tests")
class PlainTestAdapterTests {

    private static final SaplDocument     PERMIT_ALL_POLICY = SaplDocument.of("permit-all",
            "policy \"permit-all\" permit");
    private static final PlainTestAdapter ADAPTER           = new PlainTestAdapter();

    @Test
    @DisplayName("execute returns empty results when no test documents")
    void whenNoTestDocuments_thenReturnsEmptyResults() {
        val config  = TestConfiguration.builder().build();
        val results = ADAPTER.execute(config);

        assertThat(results.total()).isZero();
        assertThat(results.allPassed()).isTrue();
    }

    @Test
    @DisplayName("execute handles parse error in test document")
    void whenTestDocumentHasParseError_thenCreatesErrorResult() {
        val invalidTest = new SaplTestDocument("invalid", "invalid", "this is not valid SAPLTEST syntax");
        val config      = TestConfiguration.builder().withSaplTestDocument(invalidTest).build();
        val results     = ADAPTER.execute(config);

        assertThat(results.total()).isOne();
        assertThat(results.errors()).isOne();
        assertThat(results.allPassed()).isFalse();
        assertThat(results.scenarioResults().getFirst().status()).isEqualTo(TestStatus.ERROR);
    }

    @Test
    @DisplayName("adapter instance can be reused")
    void whenAdapterReused_thenWorksCorrectly() {
        val config1  = TestConfiguration.builder().build();
        val config2  = TestConfiguration.builder().build();
        val results1 = ADAPTER.execute(config1);
        val results2 = ADAPTER.execute(config2);

        assertThat(results1.total()).isZero();
        assertThat(results2.total()).isZero();
    }

    @Test
    @DisplayName("execute runs passing test scenario")
    void whenValidTestScenario_thenReturnsPassedResult() {
        val testCode = """
                requirement "test" {
                    given
                        - document "permit-all"
                    scenario "simple permit test"
                        when "alice" attempts "read" on "data"
                        expect decision is permit;
                }
                """;
        val results  = executeWithPermitAllPolicy(testCode);

        assertSinglePassedTest(results);
    }

    @Test
    @DisplayName("execute runs failing test scenario")
    void whenTestExpectationNotMet_thenReturnsFailedResult() {
        val policy   = SaplDocument.of("deny-all", "policy \"deny-all\" deny");
        val testCode = """
                requirement "test" {
                    given
                        - document "deny-all"
                    scenario "expects permit but gets deny"
                        when "alice" attempts "read" on "data"
                        expect decision is permit;
                }
                """;
        val testDoc  = new SaplTestDocument("test", "test", testCode);
        val config   = TestConfiguration.builder().withSaplDocument(policy).withSaplTestDocument(testDoc).build();
        val results  = ADAPTER.execute(config);

        assertThat(results.total()).isOne();
        assertThat(results.failed()).isOne();
        assertThat(results.allPassed()).isFalse();
    }

    @Test
    @DisplayName("execute runs multiple scenarios in one requirement")
    void whenMultipleScenariosInRequirement_thenAllAreExecuted() {
        val policy   = SaplDocument.of("permit-read", """
                policy "permit-read" permit action == "read";
                """);
        val testCode = """
                requirement "read access" {
                    given
                        - document "permit-read"
                    scenario "alice can read"
                        when "alice" attempts "read" on "data"
                        expect decision is permit;
                    scenario "bob can read"
                        when "bob" attempts "read" on "data"
                        expect decision is permit;
                }
                """;
        val testDoc  = new SaplTestDocument("test", "test", testCode);
        val config   = TestConfiguration.builder().withSaplDocument(policy).withSaplTestDocument(testDoc).build();
        val results  = ADAPTER.execute(config);

        assertThat(results.total()).isEqualTo(2);
        assertThat(results.passed()).isEqualTo(2);
        assertThat(results.allPassed()).isTrue();
    }

    @Test
    @DisplayName("execute reports correct counts with mixed results")
    void whenMixedPassFail_thenCountsAreCorrect() {
        val testCode = """
                requirement "mixed" {
                    given
                        - document "permit-all"
                    scenario "should pass"
                        when "alice" attempts "read" on "data"
                        expect decision is permit;
                    scenario "should fail"
                        when "alice" attempts "read" on "data"
                        expect decision is deny;
                }
                """;
        val results  = executeWithPermitAllPolicy(testCode);

        assertThat(results.total()).isEqualTo(2);
        assertThat(results.passed()).isOne();
        assertThat(results.failed()).isOne();
        assertThat(results.allPassed()).isFalse();
    }

    static Stream<Arguments> secretsTestCases() {
        return Stream.of(arguments("secrets in given block", """
                requirement "secrets test" {
                    given
                        - document "permit-all"
                        - secrets { "apiKey": "secret123", "dbPassword": "pass456" }
                    scenario "with secrets"
                        when "alice" attempts "read" on "data"
                        expect decision is permit;
                }
                """), arguments("secrets in authorization subscription", """
                requirement "subscription secrets test" {
                    given
                        - document "permit-all"
                    scenario "with subscription secrets"
                        when "alice" attempts "read" on "data" with secrets { "token": "abc123" }
                        expect decision is permit;
                }
                """), arguments("secrets from config and scenario level", """
                requirement "combined secrets test" {
                    given
                        - document "permit-all"
                        - secrets { "scenarioSecret": "value1" }
                    scenario "with combined secrets"
                        when "alice" attempts "read" on "data"
                        expect decision is permit;
                }
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("secretsTestCases")
    @DisplayName("execute handles secrets correctly")
    void whenSecretsProvided_thenScenarioExecutesSuccessfully(String description, String testCode) {
        val testDoc = new SaplTestDocument("test", "test", testCode);
        val config  = TestConfiguration.builder().withSaplDocument(PERMIT_ALL_POLICY).withSaplTestDocument(testDoc)
                .withSecret("configSecret", Value.of("configValue")).build();
        val results = ADAPTER.execute(config);

        assertSinglePassedTest(results);
    }

    private static PlainTestResults executeWithPermitAllPolicy(String testCode) {
        val testDoc = new SaplTestDocument("test", "test", testCode);
        val config  = TestConfiguration.builder().withSaplDocument(PERMIT_ALL_POLICY).withSaplTestDocument(testDoc)
                .build();
        return ADAPTER.execute(config);
    }

    private static void assertSinglePassedTest(PlainTestResults results) {
        assertThat(results.total()).isOne();
        assertThat(results.passed()).isOne();
        assertThat(results.allPassed()).isTrue();
    }
}
