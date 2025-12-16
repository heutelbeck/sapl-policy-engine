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
package io.sapl.api.model;

import io.sapl.api.SaplVersion;

import java.io.Serial;
import java.io.Serializable;

/**
 * Represents a location in SAPL source code with full context for error
 * highlighting. Used for error reporting, debugging, and coverage tracking
 * to indicate where in a policy document an expression occurs.
 *
 * @param documentName
 * the name or identifier of the SAPL document (may be null)
 * @param documentSource
 * the full source text of the document (may be null)
 * @param start
 * the start character offset in the document (0-based)
 * @param end
 * the end character offset in the document (exclusive)
 * @param line
 * the start line number (1-based)
 * @param endLine
 * the end line number (1-based), same as line for single-line expressions
 */
public record SourceLocation(String documentName, String documentSource, int start, int end, int line, int endLine)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * Returns a human-readable string representation of the source location.
     *
     * @return formatted location string
     */
    @Override
    public String toString() {
        if (documentName != null) {
            if (line == endLine) {
                return "%s:%d [%d-%d]".formatted(documentName, line, start, end);
            }
            return "%s:%d-%d [%d-%d]".formatted(documentName, line, endLine, start, end);
        }
        if (line == endLine) {
            return "line %d [%d-%d]".formatted(line, start, end);
        }
        return "lines %d-%d [%d-%d]".formatted(line, endLine, start, end);
    }
}
