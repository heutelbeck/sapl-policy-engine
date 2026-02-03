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
package io.sapl.test.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import lombok.val;

@DisplayName("TestResult tests")
class TestResultTests {

    @Test
    @DisplayName("success creates passing result with coverage")
    void whenSuccess_thenPassedIsTrueAndCoverageAvailable() {
        val coverage = new TestCoverageRecord("elder-ritual-test");
        val result   = TestResult.success(coverage);

        assertThat(result.passed()).isTrue();
        assertThat(result.failed()).isFalse();
        assertThat(result.failureMessage()).isNull();
        assertThat(result.failureCause()).isNull();
        assertThat(result.coverage()).isSameAs(coverage);
        assertThat(result.hasCoverage()).isTrue();
    }

    @Test
    @DisplayName("success with null coverage")
    void whenSuccessWithNullCoverage_thenPassedAndNoCoverage() {
        val result = TestResult.success(null);

        assertThat(result.passed()).isTrue();
        assertThat(result.hasCoverage()).isFalse();
        assertThat(result.coverage()).isNull();
    }

    @Test
    @DisplayName("failure with message and cause")
    void whenFailureWithMessageAndCause_thenAllFieldsSet() {
        val cause    = new RuntimeException("Summoning ritual failed");
        val coverage = new TestCoverageRecord("cthulhu-test");
        val result   = TestResult.failure("Assertion failed", cause, coverage);

        assertThat(result.passed()).isFalse();
        assertThat(result.failed()).isTrue();
        assertThat(result.failureMessage()).isEqualTo("Assertion failed");
        assertThat(result.failureCause()).isSameAs(cause);
        assertThat(result.coverage()).isSameAs(coverage);
    }

    @Test
    @DisplayName("failure from exception")
    void whenFailureFromException_thenMessageExtractedFromException() {
        val cause    = new IllegalStateException("Necronomicon pages missing");
        val coverage = new TestCoverageRecord("arkham-test");
        val result   = TestResult.failure(cause, coverage);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureMessage()).isEqualTo("Necronomicon pages missing");
        assertThat(result.failureCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("failure with null coverage")
    void whenFailureWithNullCoverage_thenFailedAndNoCoverage() {
        val result = TestResult.failure("Test failed", null, null);

        assertThat(result.failed()).isTrue();
        assertThat(result.hasCoverage()).isFalse();
    }
}
