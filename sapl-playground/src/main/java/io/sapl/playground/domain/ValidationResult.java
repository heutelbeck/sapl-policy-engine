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
package io.sapl.playground.domain;

/**
 * Represents the result of a validation operation in the playground.
 * Encapsulates validation status, descriptive
 * message, and severity level.
 * <p>
 * Validation results can indicate: - Success: validation passed without issues
 * - Error: validation failed, operation
 * should not proceed - Warning: validation passed but with concerns
 * <p>
 * The isValid flag indicates whether the validation passed (true for SUCCESS
 * and WARNING, false for ERROR). This allows
 * distinguishing between operations that can proceed (with or without warnings)
 * and those that must be blocked.
 *
 * @param isValid
 * whether the validation passed and the operation can proceed
 * @param message
 * descriptive message explaining the validation result
 * @param severity
 * the severity level of the validation result
 */
public record ValidationResult(boolean isValid, String message, Severity severity) {

    /**
     * Severity levels for validation results. Indicates the importance and nature
     * of the validation outcome.
     */
    public enum Severity {
        /**
         * Validation succeeded without issues. The validated content is correct and can
         * be used safely.
         */
        SUCCESS,

        /**
         * Validation failed with errors. The validated content contains problems that
         * prevent its use. Operations
         * should be blocked or rolled back.
         */
        ERROR,

        /**
         * Validation succeeded but with warnings. The validated content is usable but
         * has potential issues that may
         * require attention.
         */
        WARNING
    }

    /**
     * Creates a successful validation result with default "OK" message. Indicates
     * validation passed without any issues.
     *
     * @return validation result with SUCCESS severity and isValid true
     */
    public static ValidationResult success() {
        return new ValidationResult(true, "OK", Severity.SUCCESS);
    }

    /**
     * Creates a successful validation result with a custom message. Indicates
     * validation passed, providing specific
     * success details.
     *
     * @param message
     * descriptive message explaining the successful validation
     *
     * @return validation result with SUCCESS severity and isValid true
     */
    public static ValidationResult success(String message) {
        return new ValidationResult(true, message, Severity.SUCCESS);
    }

    /**
     * Creates an error validation result. Indicates validation failed and the
     * operation should not proceed.
     *
     * @param message
     * descriptive message explaining the validation error
     *
     * @return validation result with ERROR severity and isValid false
     */
    public static ValidationResult error(String message) {
        return new ValidationResult(false, message, Severity.ERROR);
    }

    /**
     * Creates a warning validation result. Indicates validation passed but with
     * concerns that may need attention. The
     * operation can proceed despite the warning.
     *
     * @param message
     * descriptive message explaining the validation warning
     *
     * @return validation result with WARNING severity and isValid true
     */
    public static ValidationResult warning(String message) {
        return new ValidationResult(true, message, Severity.WARNING);
    }
}
