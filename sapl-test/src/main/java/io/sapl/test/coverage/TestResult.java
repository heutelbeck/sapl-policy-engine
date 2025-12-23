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

import org.jspecify.annotations.Nullable;

/**
 * Result of a test execution including pass/fail status and coverage data.
 * <p>
 * Provides programmatic access to test outcomes for PAP server integration
 * and automated test processing.
 *
 * @param passed true if all assertions passed
 * @param failureMessage failure description if test failed, null otherwise
 * @param failureCause the exception that caused failure, null if passed
 * @param coverage the coverage data collected during test execution, null if
 * coverage disabled
 */
public record TestResult(
        boolean passed,
        @Nullable String failureMessage,
        @Nullable Throwable failureCause,
        @Nullable TestCoverageRecord coverage) {

    /**
     * Creates a successful test result.
     *
     * @param coverage the coverage data (may be null if coverage disabled)
     * @return a passing test result
     */
    public static TestResult success(@Nullable TestCoverageRecord coverage) {
        return new TestResult(true, null, null, coverage);
    }

    /**
     * Creates a failed test result.
     *
     * @param message the failure message
     * @param cause the exception that caused the failure
     * @param coverage the coverage data (may be null if coverage disabled)
     * @return a failing test result
     */
    public static TestResult failure(String message, @Nullable Throwable cause, @Nullable TestCoverageRecord coverage) {
        return new TestResult(false, message, cause, coverage);
    }

    /**
     * Creates a failed test result from an exception.
     *
     * @param cause the exception that caused the failure
     * @param coverage the coverage data (may be null if coverage disabled)
     * @return a failing test result
     */
    public static TestResult failure(Throwable cause, @Nullable TestCoverageRecord coverage) {
        return new TestResult(false, cause.getMessage(), cause, coverage);
    }

    /**
     * Checks if the test failed.
     *
     * @return true if the test failed
     */
    public boolean failed() {
        return !passed;
    }

    /**
     * Checks if coverage data is available.
     *
     * @return true if coverage was collected
     */
    public boolean hasCoverage() {
        return coverage != null;
    }
}
