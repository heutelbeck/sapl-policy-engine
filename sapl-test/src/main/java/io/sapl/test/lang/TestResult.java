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
package io.sapl.test.lang;

import org.jspecify.annotations.Nullable;

/**
 * Result of a single scenario execution.
 *
 * @param requirementName the name of the requirement
 * @param scenarioName the name of the scenario
 * @param status the test status (PASSED, FAILED, ERROR)
 * @param errorMessage the error message if failed or error, null otherwise
 * @param exception the exception if error, null otherwise
 */
public record TestResult(
        String requirementName,
        String scenarioName,
        Status status,
        @Nullable String errorMessage,
        @Nullable Throwable exception) {

    /**
     * Test status.
     */
    public enum Status {
        /** Test passed successfully. */
        PASSED,
        /** Test failed due to assertion failure. */
        FAILED,
        /** Test errored due to unexpected exception. */
        ERROR
    }

    /**
     * Creates a passed test result.
     *
     * @param requirementName the requirement name
     * @param scenarioName the scenario name
     * @return a passing test result
     */
    public static TestResult passed(String requirementName, String scenarioName) {
        return new TestResult(requirementName, scenarioName, Status.PASSED, null, null);
    }

    /**
     * Creates a failed test result.
     *
     * @param requirementName the requirement name
     * @param scenarioName the scenario name
     * @param message the failure message
     * @return a failing test result
     */
    public static TestResult failed(String requirementName, String scenarioName, String message) {
        return new TestResult(requirementName, scenarioName, Status.FAILED, message, null);
    }

    /**
     * Creates an error test result.
     *
     * @param requirementName the requirement name
     * @param scenarioName the scenario name
     * @param exception the exception that caused the error
     * @return an error test result
     */
    public static TestResult error(String requirementName, String scenarioName, Throwable exception) {
        return new TestResult(requirementName, scenarioName, Status.ERROR, exception.getMessage(), exception);
    }

    /**
     * Checks if the test passed.
     *
     * @return true if passed
     */
    public boolean isPassed() {
        return status == Status.PASSED;
    }

    /**
     * Checks if the test failed.
     *
     * @return true if failed
     */
    public boolean isFailed() {
        return status == Status.FAILED;
    }

    /**
     * Checks if the test errored.
     *
     * @return true if error
     */
    public boolean isError() {
        return status == Status.ERROR;
    }

    /**
     * Gets the full name of the test.
     *
     * @return "requirementName &gt; scenarioName"
     */
    public String fullName() {
        return requirementName + " > " + scenarioName;
    }

}
