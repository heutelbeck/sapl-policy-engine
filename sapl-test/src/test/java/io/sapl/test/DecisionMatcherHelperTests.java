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
package io.sapl.test;

import static io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision.ABSTAIN;
import static io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling.PROPAGATE;
import static io.sapl.api.pdp.CombiningAlgorithm.VotingMode.PRIORITY_DENY;

import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.test.plain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DecisionMatcherHelper tests")
class DecisionMatcherHelperTests {

    private static final String POLICY_PATH         = "/obligation-matcher-tests/obligation-policy.sapl";
    private static final String POSITIVE_TESTS_PATH = "/obligation-matcher-tests/obligation-matchers-positive.sapltest";
    private static final String NEGATIVE_TESTS_PATH = "/obligation-matcher-tests/obligation-matchers-negative.sapltest";

    @Test
    void allPositiveObligationMatcherTestsShouldPass() {
        var policy = loadResource(POLICY_PATH);
        var tests  = loadResource(POSITIVE_TESTS_PATH);

        var config = TestConfiguration.builder()
                .withSaplDocuments(List.of(new SaplDocument("obligation-policy", "obligation-policy", policy, null)))
                .withSaplTestDocuments(List.of(new SaplTestDocument("positive-tests", "positive-tests", tests)))
                .withDefaultAlgorithm(new CombiningAlgorithm(PRIORITY_DENY, ABSTAIN, PROPAGATE)).build();

        var adapter = new PlainTestAdapter();
        var results = adapter.execute(config);

        assertThat(results.allPassed()).withFailMessage(() -> buildFailureMessage(results)).isTrue();
        assertThat(results.passed()).isEqualTo(13);
    }

    @Test
    void allNegativeObligationMatcherTestsShouldFail() {
        var policy = loadResource(POLICY_PATH);
        var tests  = loadResource(NEGATIVE_TESTS_PATH);

        var config = TestConfiguration.builder()
                .withSaplDocuments(List.of(new SaplDocument("obligation-policy", "obligation-policy", policy, null)))
                .withSaplTestDocuments(List.of(new SaplTestDocument("negative-tests", "negative-tests", tests)))
                .withDefaultAlgorithm(new CombiningAlgorithm(PRIORITY_DENY, ABSTAIN, PROPAGATE)).build();

        var adapter = new PlainTestAdapter();
        var results = adapter.execute(config);

        assertThat(results.allPassed()).isFalse();
        assertThat(results.failed()).isEqualTo(7);

        for (var result : results.scenarioResults()) {
            assertThat(result.status())
                    .withFailMessage("Expected scenario '%s' to fail but it passed", result.scenarioName())
                    .isEqualTo(TestStatus.FAILED);
        }
    }

    @Test
    void obligationEqualsWrongValue_shouldFail() {
        var result = runSingleScenario("with obligation equals wrong value should fail",
                "Obligation Matcher Negative Tests");
        assertThat(result.status()).isEqualTo(TestStatus.FAILED);
    }

    @Test
    void obligationContainingNonexistentKey_shouldFail() {
        var result = runSingleScenario("with obligation containing nonexistent key should fail",
                "Obligation Matcher Negative Tests");
        assertThat(result.status()).isEqualTo(TestStatus.FAILED);
    }

    @Test
    void obligationContainingKeyWithWrongValue_shouldFail() {
        var result = runSingleScenario("with obligation containing key with wrong value should fail",
                "Obligation Matcher Negative Tests");
        assertThat(result.status()).isEqualTo(TestStatus.FAILED);
    }

    @Test
    void obligationMatchingObjectWithWrongKeyValue_shouldFail() {
        var result = runSingleScenario("with obligation matching object with wrong key value should fail",
                "Obligation Matcher Negative Tests");
        assertThat(result.status()).isEqualTo(TestStatus.FAILED);
    }

    @Test
    void obligationPresenceWhenNoneExist_shouldFail() {
        var result = runSingleScenario("with obligation when none exist should fail", "No Obligation Tests");
        assertThat(result.status()).isEqualTo(TestStatus.FAILED);
    }

    private ScenarioResult runSingleScenario(String scenarioName, String requirementName) {
        var policy = loadResource(POLICY_PATH);
        var tests  = loadResource(NEGATIVE_TESTS_PATH);

        var config = TestConfiguration.builder()
                .withSaplDocuments(List.of(new SaplDocument("obligation-policy", "obligation-policy", policy, null)))
                .withSaplTestDocuments(List.of(new SaplTestDocument("negative-tests", "negative-tests", tests)))
                .withDefaultAlgorithm(new CombiningAlgorithm(PRIORITY_DENY, ABSTAIN, PROPAGATE)).build();

        var adapter = new PlainTestAdapter();
        var results = adapter.execute(config);

        return results.scenarioResults().stream()
                .filter(r -> r.scenarioName().equals(scenarioName) && r.requirementName().equals(requirementName))
                .findFirst().orElseThrow(() -> new AssertionError("Scenario not found: " + scenarioName));
    }

    private String buildFailureMessage(io.sapl.test.plain.PlainTestResults results) {
        var sb = new StringBuilder("Expected all tests to pass but some failed:\n");
        for (var result : results.scenarioResults()) {
            if (result.status() != TestStatus.PASSED) {
                sb.append("  - ").append(result.requirementName()).append(" > ").append(result.scenarioName())
                        .append(": ").append(result.failureMessage()).append('\n');
            }
        }
        return sb.toString();
    }

    private String loadResource(String path) {
        try (var stream = getClass().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalArgumentException("Resource not found: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
