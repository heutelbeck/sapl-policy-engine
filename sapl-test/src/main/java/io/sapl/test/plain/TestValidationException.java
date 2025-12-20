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

/**
 * Exception thrown when a test definition contains invalid configuration.
 * <p>
 * This exception indicates a semantic error in the test definition, such as:
 * <ul>
 * <li>Specifying a combining algorithm in a unit test</li>
 * <li>Specifying document selection in a scenario-level given block</li>
 * <li>Registering duplicate mock patterns</li>
 * </ul>
 */
public class TestValidationException extends RuntimeException {

    /**
     * Creates a new validation exception with the specified message.
     *
     * @param message the validation error message
     */
    public TestValidationException(String message) {
        super(message);
    }

    /**
     * Creates a new validation exception with the specified message and cause.
     *
     * @param message the validation error message
     * @param cause the underlying cause
     */
    public TestValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
