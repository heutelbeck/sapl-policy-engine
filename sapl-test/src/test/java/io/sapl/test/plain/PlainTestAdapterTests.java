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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlainTestAdapter tests")
class PlainTestAdapterTests {

    @Test
    @DisplayName("execute returns empty results when no test documents")
    void whenNoTestDocuments_thenReturnsEmptyResults() {
        var config  = TestConfiguration.builder().build();
        var adapter = new PlainTestAdapter();

        var results = adapter.execute(config);

        assertThat(results.total()).isZero();
        assertThat(results.allPassed()).isTrue();
    }

    @Test
    @DisplayName("execute handles parse error in test document")
    void whenTestDocumentHasParseError_thenCreatesErrorResult() {
        var invalidTest = new SaplTestDocument("invalid", "invalid", "this is not valid SAPLTEST syntax");
        var config      = TestConfiguration.builder().withSaplTestDocument(invalidTest).build();
        var adapter     = new PlainTestAdapter();

        var results = adapter.execute(config);

        assertThat(results.total()).isOne();
        assertThat(results.errors()).isOne();
        assertThat(results.allPassed()).isFalse();
        assertThat(results.scenarioResults().getFirst().status()).isEqualTo(TestStatus.ERROR);
    }

    @Test
    @DisplayName("adapter instance can be reused")
    void whenAdapterReused_thenWorksCorrectly() {
        var config1 = TestConfiguration.builder().build();
        var config2 = TestConfiguration.builder().build();
        var adapter = new PlainTestAdapter();

        var results1 = adapter.execute(config1);
        var results2 = adapter.execute(config2);

        assertThat(results1.total()).isZero();
        assertThat(results2.total()).isZero();
    }

    @Test
    @DisplayName("execute runs passing test scenario")
    void whenValidTestScenario_thenReturnsPassedResult() {
        var policy   = SaplDocument.of("permit-all", "policy \"permit-all\" permit");
        var testCode = """
                requirement "test" {
                    given
                        - document "permit-all"
                    scenario "simple permit test"
                        when "alice" attempts "read" on "data"
                        expect decision is permit;
                }
                """;
        var testDoc  = new SaplTestDocument("test", "test", testCode);
        var config   = TestConfiguration.builder().withSaplDocument(policy).withSaplTestDocument(testDoc).build();
        var adapter  = new PlainTestAdapter();

        var results = adapter.execute(config);

        assertThat(results.total()).isOne();
        assertThat(results.passed()).isOne();
        assertThat(results.allPassed()).isTrue();
    }

    @Test
    @DisplayName("execute runs failing test scenario")
    void whenTestExpectationNotMet_thenReturnsFailedResult() {
        var policy   = SaplDocument.of("deny-all", "policy \"deny-all\" deny");
        var testCode = """
                requirement "test" {
                    given
                        - document "deny-all"
                    scenario "expects permit but gets deny"
                        when "alice" attempts "read" on "data"
                        expect decision is permit;
                }
                """;
        var testDoc  = new SaplTestDocument("test", "test", testCode);
        var config   = TestConfiguration.builder().withSaplDocument(policy).withSaplTestDocument(testDoc).build();
        var adapter  = new PlainTestAdapter();

        var results = adapter.execute(config);

        assertThat(results.total()).isOne();
        assertThat(results.failed()).isOne();
        assertThat(results.allPassed()).isFalse();
    }

    @Test
    @DisplayName("execute runs multiple scenarios in one requirement")
    void whenMultipleScenariosInRequirement_thenAllAreExecuted() {
        var policy   = SaplDocument.of("permit-read", """
                policy "permit-read" permit action == "read";
                """);
        var testCode = """
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
        var testDoc  = new SaplTestDocument("test", "test", testCode);
        var config   = TestConfiguration.builder().withSaplDocument(policy).withSaplTestDocument(testDoc).build();
        var adapter  = new PlainTestAdapter();

        var results = adapter.execute(config);

        assertThat(results.total()).isEqualTo(2);
        assertThat(results.passed()).isEqualTo(2);
        assertThat(results.allPassed()).isTrue();
    }

    @Test
    @DisplayName("execute reports correct counts with mixed results")
    void whenMixedPassFail_thenCountsAreCorrect() {
        var policy   = SaplDocument.of("permit-all", "policy \"permit-all\" permit");
        var testCode = """
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
        var testDoc  = new SaplTestDocument("test", "test", testCode);
        var config   = TestConfiguration.builder().withSaplDocument(policy).withSaplTestDocument(testDoc).build();
        var adapter  = new PlainTestAdapter();

        var results = adapter.execute(config);

        assertThat(results.total()).isEqualTo(2);
        assertThat(results.passed()).isOne();
        assertThat(results.failed()).isOne();
        assertThat(results.allPassed()).isFalse();
    }
}
