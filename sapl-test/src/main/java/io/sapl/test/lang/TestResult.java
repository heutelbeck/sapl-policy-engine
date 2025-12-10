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
package io.sapl.test.lang;

/**
 * Result of executing a single test scenario.
 *
 * @param requirementName the name of the requirement containing this scenario
 * @param scenarioName the name of the scenario
 * @param status the execution status
 * @param errorMessage error message if status is FAILED or ERROR, null
 * otherwise
 * @param exception the exception if status is ERROR, null otherwise
 */
public record TestResult(
        String requirementName,
        String scenarioName,
        Status status,
        String errorMessage,
        Throwable exception) {

    public enum Status {
        PASSED,
        FAILED,
        ERROR
    }

    public static TestResult passed(String requirementName, String scenarioName) {
        return new TestResult(requirementName, scenarioName, Status.PASSED, null, null);
    }

    public static TestResult failed(String requirementName, String scenarioName, String message) {
        return new TestResult(requirementName, scenarioName, Status.FAILED, message, null);
    }

    public static TestResult error(String requirementName, String scenarioName, Throwable exception) {
        return new TestResult(requirementName, scenarioName, Status.ERROR, exception.getMessage(), exception);
    }

    public String fullName() {
        return requirementName + " > " + scenarioName;
    }

    public boolean isPassed() {
        return status == Status.PASSED;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }
}
