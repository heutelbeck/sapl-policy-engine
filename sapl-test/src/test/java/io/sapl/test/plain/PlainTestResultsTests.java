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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlainTestResults tests")
class PlainTestResultsTests {

    private static final Duration DURATION = Duration.ofMillis(50);

    @Test
    @DisplayName("from factory counts passed scenarios correctly")
    void whenAllScenariosPassed_thenCountsAreCorrect() {
        var results = List.of(ScenarioResult.passed("doc", "req1", "s1", DURATION, null),
                ScenarioResult.passed("doc", "req1", "s2", DURATION, null));

        var plainResults = PlainTestResults.from(results, Map.of());

        assertThat(plainResults.total()).isEqualTo(2);
        assertThat(plainResults.passed()).isEqualTo(2);
        assertThat(plainResults.failed()).isZero();
        assertThat(plainResults.errors()).isZero();
        assertThat(plainResults.allPassed()).isTrue();
    }

    @Test
    @DisplayName("from factory counts failed scenarios correctly")
    void whenScenariosHaveFailures_thenCountsAreCorrect() {
        var results = List.of(ScenarioResult.passed("doc", "req1", "s1", DURATION, null),
                ScenarioResult.failed("doc", "req1", "s2", DURATION, "failed", null));

        var plainResults = PlainTestResults.from(results, Map.of());

        assertThat(plainResults.total()).isEqualTo(2);
        assertThat(plainResults.passed()).isEqualTo(1);
        assertThat(plainResults.failed()).isEqualTo(1);
        assertThat(plainResults.errors()).isZero();
        assertThat(plainResults.allPassed()).isFalse();
    }

    @Test
    @DisplayName("from factory counts error scenarios correctly")
    void whenScenariosHaveErrors_thenCountsAreCorrect() {
        var results = List.of(ScenarioResult.passed("doc", "req1", "s1", DURATION, null),
                ScenarioResult.error("doc", "req1", "s2", DURATION, new RuntimeException("error"), null));

        var plainResults = PlainTestResults.from(results, Map.of());

        assertThat(plainResults.total()).isEqualTo(2);
        assertThat(plainResults.passed()).isEqualTo(1);
        assertThat(plainResults.failed()).isZero();
        assertThat(plainResults.errors()).isEqualTo(1);
        assertThat(plainResults.allPassed()).isFalse();
    }

    @Test
    @DisplayName("from factory counts mixed results correctly")
    void whenScenariosHaveMixedResults_thenCountsAreCorrect() {
        var results = List.of(ScenarioResult.passed("doc", "req1", "s1", DURATION, null),
                ScenarioResult.failed("doc", "req1", "s2", DURATION, "failed", null),
                ScenarioResult.error("doc", "req1", "s3", DURATION, new RuntimeException("error"), null));

        var plainResults = PlainTestResults.from(results, Map.of());

        assertThat(plainResults.total()).isEqualTo(3);
        assertThat(plainResults.passed()).isEqualTo(1);
        assertThat(plainResults.failed()).isEqualTo(1);
        assertThat(plainResults.errors()).isEqualTo(1);
        assertThat(plainResults.allPassed()).isFalse();
    }

    @Test
    @DisplayName("scenarioResults returns list of results")
    void whenAccessingScenarioResults_thenListIsReturned() {
        var result1 = ScenarioResult.passed("doc", "req1", "s1", DURATION, null);
        var result2 = ScenarioResult.passed("doc", "req1", "s2", DURATION, null);
        var results = List.of(result1, result2);

        var plainResults = PlainTestResults.from(results, Map.of());

        assertThat(plainResults.scenarioResults()).containsExactly(result1, result2);
    }

    @Test
    @DisplayName("coverageByDocumentId returns empty map when no coverage")
    void whenNoCoverage_thenMapIsEmpty() {
        var results = List.of(ScenarioResult.passed("doc", "req", "s", DURATION, null));

        var plainResults = PlainTestResults.from(results, Map.of());

        assertThat(plainResults.coverageByDocumentId()).isEmpty();
    }

    @Test
    @DisplayName("record constructor initializes all fields")
    void whenUsingRecordConstructor_thenFieldsAreAccessible() {
        var result       = ScenarioResult.passed("doc", "req", "s", DURATION, null);
        var plainResults = new PlainTestResults(1, 1, 0, 0, List.of(result), null);

        assertThat(plainResults.total()).isEqualTo(1);
        assertThat(plainResults.passed()).isEqualTo(1);
        assertThat(plainResults.failed()).isZero();
        assertThat(plainResults.errors()).isZero();
    }
}
