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
package io.sapl.server.ce.model.setup;

public enum LoggingLevel {
    TRACE("Detailed information for troubleshooting and debugging during development."),
    DEBUG("Logs useful information for development and debugging purposes"),
    INFO("These messages provide updates on the application's progress and significant events."),
    WARN("Records issues that do not result in errors."),
    ERROR("The application's errors or exceptions that occurred during execution are logged.");

    private final String description;

    LoggingLevel(String s) {
        description = s;
    }

    public String getDescription() {
        return description;
    }

    public static LoggingLevel getByName(Object obj, LoggingLevel defaultLevel) {
        try {
            return LoggingLevel.valueOf(obj.toString());
        } catch (NullPointerException | IllegalArgumentException e) {
            return defaultLevel;
        }
    }
}
