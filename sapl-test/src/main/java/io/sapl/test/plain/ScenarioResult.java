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

import io.sapl.test.coverage.TestCoverageRecord;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * Result of a single scenario execution.
 *
 * @param saplTestDocumentId the ID of the test document containing this
 * scenario
 * @param requirementName the name of the requirement containing this scenario
 * @param scenarioName the name of this scenario
 * @param status the execution status
 * @param duration the execution duration
 * @param failureMessage failure description if failed/error, null if passed
 * @param failureCause the exception that caused failure, null if passed
 * @param coverage coverage data for this scenario, null if coverage disabled
 */
public record ScenarioResult(
        String saplTestDocumentId,
        String requirementName,
        String scenarioName,
        TestStatus status,
        Duration duration,
        @Nullable String failureMessage,
        @Nullable Throwable failureCause,
        @Nullable TestCoverageRecord coverage) {

    /**
     * Creates a passed scenario result.
     */
    public static ScenarioResult passed(String testDocId, String requirement, String scenario, Duration duration,
            @Nullable TestCoverageRecord coverage) {
        return new ScenarioResult(testDocId, requirement, scenario, TestStatus.PASSED, duration, null, null, coverage);
    }

    /**
     * Creates a failed scenario result.
     */
    public static ScenarioResult failed(String testDocId, String requirement, String scenario, Duration duration,
            String message, @Nullable TestCoverageRecord coverage) {
        return new ScenarioResult(testDocId, requirement, scenario, TestStatus.FAILED, duration, message, null,
                coverage);
    }

    /**
     * Creates an error scenario result.
     */
    public static ScenarioResult error(String testDocId, String requirement, String scenario, Duration duration,
            Throwable cause, @Nullable TestCoverageRecord coverage) {
        return new ScenarioResult(testDocId, requirement, scenario, TestStatus.ERROR, duration, cause.getMessage(),
                cause, coverage);
    }

    /**
     * Returns the full name of this scenario (requirement &gt; scenario).
     */
    public String fullName() {
        return requirementName + " > " + scenarioName;
    }

    /**
     * Checks if this scenario passed.
     */
    public boolean isPassed() {
        return status == TestStatus.PASSED;
    }

    /**
     * Checks if this scenario failed.
     */
    public boolean isFailed() {
        return status == TestStatus.FAILED;
    }

    /**
     * Checks if this scenario had an error.
     */
    public boolean isError() {
        return status == TestStatus.ERROR;
    }
}
