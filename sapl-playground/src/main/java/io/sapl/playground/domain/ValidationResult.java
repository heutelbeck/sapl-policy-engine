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
package io.sapl.playground.domain;

/**
 * Represents the result of a validation operation.
 *
 * @param isValid whether the validation succeeded
 * @param message the validation message (error description or success message)
 * @param severity the severity level of the validation result
 */
public record ValidationResult(boolean isValid, String message, Severity severity) {

    /**
     * Severity levels for validation results.
     */
    public enum Severity {
        /** Validation succeeded */
        SUCCESS,
        /** Validation failed with errors */
        ERROR,
        /** Validation succeeded with warnings */
        WARNING
    }

    /**
     * Creates a successful validation result.
     *
     * @return a validation result indicating success
     */
    public static ValidationResult success() {
        return new ValidationResult(true, "OK", Severity.SUCCESS);
    }

    /**
     * Creates a successful validation result with a custom message.
     *
     * @param message the success message
     * @return a validation result indicating success
     */
    public static ValidationResult success(String message) {
        return new ValidationResult(true, message, Severity.SUCCESS);
    }

    /**
     * Creates an error validation result.
     *
     * @param message the error message
     * @return a validation result indicating an error
     */
    public static ValidationResult error(String message) {
        return new ValidationResult(false, message, Severity.ERROR);
    }

    /**
     * Creates a warning validation result.
     *
     * @param message the warning message
     * @return a validation result indicating a warning
     */
    public static ValidationResult warning(String message) {
        return new ValidationResult(true, message, Severity.WARNING);
    }
}
