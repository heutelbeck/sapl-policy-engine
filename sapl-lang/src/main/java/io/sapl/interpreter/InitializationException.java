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
package io.sapl.interpreter;

import io.sapl.api.SaplVersion;

/**
 * Indicates an error during function context setup or function evaluation.
 */
public class InitializationException extends Exception {

    private static final long serialVersionUID = SaplVersion.VERISION_UID;

    /**
     * Create a new FunctionException
     *
     * @param message a message
     */
    public InitializationException(String message) {
        super(message);
    }

    /**
     * Create a new PolicyEvaluationException
     *
     * @param format format string
     * @param args arguments for format string
     */
    public InitializationException(String format, Object... args) {
        super(String.format(format, args));
    }

    /**
     * Create a new PolicyEvaluationException
     *
     * @param format format string
     * @param args arguments for format string
     */
    public InitializationException(Throwable cause, String format, Object... args) {
        super(String.format(format, args), cause);
    }

}
