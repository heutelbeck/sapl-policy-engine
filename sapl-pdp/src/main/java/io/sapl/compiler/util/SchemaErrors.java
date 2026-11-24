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
package io.sapl.compiler.util;

import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Describes why a JSON Schema could not be loaded or applied, in plain words
 * without the names of exception classes.
 */
@UtilityClass
public class SchemaErrors {

    private static final String ERROR_INVALID_REGULAR_EXPRESSION = "The schema holds an invalid regular expression: %s.";
    private static final String ERROR_MATCHING_FAILED            = "Matching the regular expression '%s' of the schema failed: %s.";

    private static final String NO_REASON_GIVEN = "no reason given";

    /**
     * Describes an exception thrown while a schema was loaded or applied. A
     * regular expression {@link LinearRegex} refused anywhere in the causes is
     * described by the reason {@link LinearRegex#describe} gives, a text it
     * could not search by the pattern and the reason. Any other
     * exception is described by its message, or by the message of its cause if
     * the message only names the cause.
     *
     * @param error the exception thrown by the schema validator
     * @param otherErrorFormat the format of the description of an exception other
     * than a refused regular expression, with one {@code %s} for its message
     * @return the description
     */
    public static String describe(Throwable error, String otherErrorFormat) {
        for (var cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof RegexRefusedException refusal) {
                return ERROR_INVALID_REGULAR_EXPRESSION.formatted(refusal.getDescription());
            }
            if (cause instanceof RegexSearchFailedException failure) {
                return ERROR_MATCHING_FAILED.formatted(failure.getPattern(), failure.getDescription());
            }
        }
        return otherErrorFormat.formatted(plainMessage(error));
    }

    private static String plainMessage(Throwable error) {
        var current = error;
        while (current.getCause() != null && current.getCause().toString().equals(current.getMessage())) {
            current = current.getCause();
        }
        val message = current.getMessage();
        return message == null ? NO_REASON_GIVEN : message;
    }
}
