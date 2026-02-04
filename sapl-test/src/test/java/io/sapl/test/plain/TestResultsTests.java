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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TestResults tests")
class TestResultsTests {

    @Test
    @DisplayName("success factory creates result with all tests passing")
    void whenCreatingSuccess_thenAllTestsPass() {
        var results = TestResults.success(5);

        assertThat(results).satisfies(r -> {
            assertThat(r.total()).isEqualTo(5);
            assertThat(r.passed()).isEqualTo(5);
            assertThat(r.failed()).isZero();
            assertThat(r.failures()).isEmpty();
            assertThat(r.allPassed()).isTrue();
        });
    }

    @Test
    @DisplayName("withFailures factory creates result with failures")
    void whenCreatingWithFailures_thenFailuresAreRecorded() {
        var failures = Map.<String, Throwable>of("scenario1", new AssertionError("test failed"));

        var results = TestResults.withFailures(3, failures);

        assertThat(results).satisfies(r -> {
            assertThat(r.total()).isEqualTo(3);
            assertThat(r.passed()).isEqualTo(2);
            assertThat(r.failed()).isEqualTo(1);
            assertThat(r.failures()).containsKey("scenario1");
            assertThat(r.allPassed()).isFalse();
        });
    }

    @Test
    @DisplayName("allPassed returns true when no failures")
    void whenNoFailures_thenAllPassedIsTrue() {
        var results = new TestResults(10, 10, 0, Map.of());

        assertThat(results.allPassed()).isTrue();
    }

    @Test
    @DisplayName("allPassed returns false when there are failures")
    void whenFailuresExist_thenAllPassedIsFalse() {
        var results = new TestResults(10, 8, 2, Map.of("s1", new Error(), "s2", new Error()));

        assertThat(results.allPassed()).isFalse();
    }

    @Test
    @DisplayName("record accessors return correct values")
    void whenAccessingRecordComponents_thenValuesAreCorrect() {
        var failure  = new RuntimeException("test");
        var failures = Map.<String, Throwable>of("test1", failure);
        var results  = new TestResults(5, 4, 1, failures);

        assertThat(results).satisfies(r -> {
            assertThat(r.total()).isEqualTo(5);
            assertThat(r.passed()).isEqualTo(4);
            assertThat(r.failed()).isEqualTo(1);
            assertThat(r.failures()).containsEntry("test1", failure);
        });
    }
}
