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

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScenarioResult tests")
class ScenarioResultTests {

    private static final String   DOC_ID      = "test-doc";
    private static final String   REQUIREMENT = "test requirement";
    private static final String   SCENARIO    = "test scenario";
    private static final Duration DURATION    = Duration.ofMillis(100);

    @Test
    @DisplayName("passed factory creates passed result")
    void whenCreatingPassed_thenStatusIsPassed() {
        var result = ScenarioResult.passed(DOC_ID, REQUIREMENT, SCENARIO, DURATION, null);

        assertThat(result.status()).isEqualTo(TestStatus.PASSED);
        assertThat(result.isPassed()).isTrue();
        assertThat(result.isFailed()).isFalse();
        assertThat(result.isError()).isFalse();
        assertThat(result.failureMessage()).isNull();
        assertThat(result.failureCause()).isNull();
    }

    @Test
    @DisplayName("failed factory creates failed result with message")
    void whenCreatingFailed_thenStatusIsFailed() {
        var message = "assertion failed";
        var result  = ScenarioResult.failed(DOC_ID, REQUIREMENT, SCENARIO, DURATION, message, null);

        assertThat(result.status()).isEqualTo(TestStatus.FAILED);
        assertThat(result.isPassed()).isFalse();
        assertThat(result.isFailed()).isTrue();
        assertThat(result.isError()).isFalse();
        assertThat(result.failureMessage()).isEqualTo(message);
        assertThat(result.failureCause()).isNull();
    }

    @Test
    @DisplayName("error factory creates error result with cause")
    void whenCreatingError_thenStatusIsError() {
        var cause  = new RuntimeException("execution error");
        var result = ScenarioResult.error(DOC_ID, REQUIREMENT, SCENARIO, DURATION, cause, null);

        assertThat(result.status()).isEqualTo(TestStatus.ERROR);
        assertThat(result.isPassed()).isFalse();
        assertThat(result.isFailed()).isFalse();
        assertThat(result.isError()).isTrue();
        assertThat(result.failureMessage()).isEqualTo("execution error");
        assertThat(result.failureCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("fullName returns combined requirement and scenario name")
    void whenGettingFullName_thenReturnsFormattedName() {
        var result = ScenarioResult.passed(DOC_ID, REQUIREMENT, SCENARIO, DURATION, null);

        assertThat(result.fullName()).isEqualTo("test requirement > test scenario");
    }

    @Test
    @DisplayName("record accessors return correct values")
    void whenAccessingRecordComponents_thenValuesAreCorrect() {
        var result = ScenarioResult.passed(DOC_ID, REQUIREMENT, SCENARIO, DURATION, null);

        assertThat(result.saplTestDocumentId()).isEqualTo(DOC_ID);
        assertThat(result.requirementName()).isEqualTo(REQUIREMENT);
        assertThat(result.scenarioName()).isEqualTo(SCENARIO);
        assertThat(result.duration()).isEqualTo(DURATION);
        assertThat(result.coverage()).isNull();
    }
}
