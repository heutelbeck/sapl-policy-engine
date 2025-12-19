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
package io.sapl.test.plain;

import io.sapl.test.coverage.TestCoverageRecord;

import java.util.List;
import java.util.Map;

/**
 * Aggregated results from test execution.
 *
 * @param total total number of scenarios executed
 * @param passed number of passed scenarios
 * @param failed number of failed scenarios
 * @param errors number of scenarios with errors
 * @param scenarioResults per-scenario results
 * @param coverageByDocumentId coverage data aggregated by SAPL document ID
 */
public record PlainTestResults(
        int total,
        int passed,
        int failed,
        int errors,
        List<ScenarioResult> scenarioResults,
        Map<String, TestCoverageRecord> coverageByDocumentId) {

    /**
     * Checks if all tests passed (no failures, no errors).
     */
    public boolean allPassed() {
        return failed == 0 && errors == 0;
    }

    /**
     * Creates results from a list of scenario results.
     */
    public static PlainTestResults from(List<ScenarioResult> results, Map<String, TestCoverageRecord> coverage) {
        int passedCount = 0;
        int failedCount = 0;
        int errorCount  = 0;

        for (var result : results) {
            switch (result.status()) {
            case PASSED -> passedCount++;
            case FAILED -> failedCount++;
            case ERROR  -> errorCount++;
            }
        }

        return new PlainTestResults(results.size(), passedCount, failedCount, errorCount, List.copyOf(results),
                Map.copyOf(coverage));
    }
}
