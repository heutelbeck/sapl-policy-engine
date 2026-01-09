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

import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.test.plain.PlainTestAdapter;
import io.sapl.test.plain.PlainTestResults;
import io.sapl.test.plain.SaplDocument;
import io.sapl.test.plain.SaplTestDocument;
import io.sapl.test.plain.ScenarioResult;
import io.sapl.test.plain.TestConfiguration;
import io.sapl.test.plain.TestStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Resource matcher tests")
class ResourceMatcherTests {

    private static final String TRANSFORM_POLICY_PATH = "/resource-matcher-tests/resource-transform-policy.sapl";
    private static final String TEXT_POLICY_PATH      = "/resource-matcher-tests/resource-text-policy.sapl";
    private static final String NUMBER_POLICY_PATH    = "/resource-matcher-tests/resource-number-policy.sapl";
    private static final String NO_RESOURCE_POLICY    = "/resource-matcher-tests/no-resource-policy.sapl";
    private static final String POSITIVE_TESTS_PATH   = "/resource-matcher-tests/resource-matchers-positive.sapltest";
    private static final String NEGATIVE_TESTS_PATH   = "/resource-matcher-tests/resource-matchers-negative.sapltest";

    @Test
    @DisplayName("all positive resource matcher tests should pass")
    void allPositiveResourceMatcherTestsShouldPass() {
        var results = runPositiveTests();

        assertThat(results.allPassed()).withFailMessage(() -> buildFailureMessage(results)).isTrue();
        assertThat(results.passed()).isEqualTo(9);
    }

    @Test
    @DisplayName("all negative resource matcher tests should fail")
    void allNegativeResourceMatcherTestsShouldFail() {
        var results = runNegativeTests();

        assertThat(results.allPassed()).isFalse();
        assertThat(results.failed()).isEqualTo(7);
        results.scenarioResults()
                .forEach(result -> assertThat(result.status())
                        .withFailMessage("Expected scenario '%s' to fail but it passed", result.scenarioName())
                        .isEqualTo(TestStatus.FAILED));
    }

    @Test
    @DisplayName("resource equals wrong value should fail")
    void resourceEqualsWrongValue_shouldFail() {
        var result = runSingleScenario("with resource equals wrong value should fail",
                "Resource Matcher Negative Tests - Object");
        assertThat(result.status()).isEqualTo(TestStatus.FAILED);
    }

    @Test
    @DisplayName("resource matching object with wrong key value should fail")
    void resourceMatchingObjectWithWrongKeyValue_shouldFail() {
        var result = runSingleScenario("with resource matching object with wrong key value should fail",
                "Resource Matcher Negative Tests - Object");
        assertThat(result.status()).isEqualTo(TestStatus.FAILED);
    }

    @Test
    @DisplayName("resource presence when none exist should fail")
    void resourcePresenceWhenNoneExist_shouldFail() {
        var result = runSingleScenario("with resource when none exist should fail", "No Resource Tests");
        assertThat(result.status()).isEqualTo(TestStatus.FAILED);
    }

    private PlainTestResults runPositiveTests() {
        var transformPolicy = loadResource(TRANSFORM_POLICY_PATH);
        var textPolicy      = loadResource(TEXT_POLICY_PATH);
        var numberPolicy    = loadResource(NUMBER_POLICY_PATH);
        var tests           = loadResource(POSITIVE_TESTS_PATH);

        var config = TestConfiguration.builder()
                .withSaplDocuments(List.of(
                        new SaplDocument("resource-transform-policy", "resource-transform-policy", transformPolicy,
                                null),
                        new SaplDocument("resource-text-policy", "resource-text-policy", textPolicy, null),
                        new SaplDocument("resource-number-policy", "resource-number-policy", numberPolicy, null)))
                .withSaplTestDocuments(List.of(new SaplTestDocument("positive-tests", "positive-tests", tests)))
                .withDefaultAlgorithm(CombiningAlgorithm.PERMIT_OVERRIDES).build();

        return new PlainTestAdapter().execute(config);
    }

    private PlainTestResults runNegativeTests() {
        var transformPolicy  = loadResource(TRANSFORM_POLICY_PATH);
        var textPolicy       = loadResource(TEXT_POLICY_PATH);
        var noResourcePolicy = loadResource(NO_RESOURCE_POLICY);
        var tests            = loadResource(NEGATIVE_TESTS_PATH);

        var config = TestConfiguration.builder()
                .withSaplDocuments(List.of(
                        new SaplDocument("resource-transform-policy", "resource-transform-policy", transformPolicy,
                                null),
                        new SaplDocument("resource-text-policy", "resource-text-policy", textPolicy, null),
                        new SaplDocument("no-resource-policy", "no-resource-policy", noResourcePolicy, null)))
                .withSaplTestDocuments(List.of(new SaplTestDocument("negative-tests", "negative-tests", tests)))
                .withDefaultAlgorithm(CombiningAlgorithm.PERMIT_OVERRIDES).build();

        return new PlainTestAdapter().execute(config);
    }

    private ScenarioResult runSingleScenario(String scenarioName, String requirementName) {
        return runNegativeTests().scenarioResults().stream()
                .filter(r -> r.scenarioName().equals(scenarioName) && r.requirementName().equals(requirementName))
                .findFirst().orElseThrow(() -> new AssertionError("Scenario not found: " + scenarioName));
    }

    private String buildFailureMessage(PlainTestResults results) {
        var sb = new StringBuilder("Expected all tests to pass but some failed:\n");
        for (var result : results.scenarioResults()) {
            if (result.status() != TestStatus.PASSED) {
                sb.append("  - ").append(result.requirementName()).append(" > ").append(result.scenarioName())
                        .append(": ").append(result.failureMessage()).append("\n");
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
