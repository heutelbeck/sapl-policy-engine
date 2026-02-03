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
package io.sapl.api.model;

import io.sapl.api.SaplVersion;

import java.io.Serial;
import java.io.Serializable;

/**
 * Represents a location in SAPL metadata code with full context for error
 * highlighting. Used for error reporting, debugging, and coverage tracking
 * to indicate where in a policy document an expression occurs.
 *
 * @param documentName
 * the name or identifier of the SAPL document (may be null)
 * @param documentSource
 * the full metadata text of the document (may be null)
 * @param start
 * the start character offset in the document (0-based)
 * @param end
 * the end character offset in the document (exclusive)
 * @param line
 * the start line number (1-based)
 * @param column
 * the start column number (1-based)
 * @param endLine
 * the end line number (1-based), same as line for single-line expressions
 * @param endColumn
 * the end column number (1-based, exclusive)
 */
public record SourceLocation(
        String documentName,
        String documentSource,
        int start,
        int end,
        int line,
        int column,
        int endLine,
        int endColumn) implements Serializable {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * Creates a SourceLocation without column information (for backwards
     * compatibility).
     *
     * @param documentName
     * the name or identifier of the SAPL document
     * @param documentSource
     * the full metadata text of the document
     * @param start
     * the start character offset
     * @param end
     * the end character offset
     * @param line
     * the start line number
     * @param endLine
     * the end line number
     */
    public SourceLocation(String documentName, String documentSource, int start, int end, int line, int endLine) {
        this(documentName, documentSource, start, end, line, 0, endLine, 0);
    }

    /**
     * Creates a SourceLocation spanning from the start of the first location to the
     * end of the second location.
     *
     * @param first the starting location
     * @param last the ending location
     * @return a new SourceLocation spanning from first to last
     */
    public static SourceLocation spanning(SourceLocation first, SourceLocation last) {
        return new SourceLocation(first.documentName(), first.documentSource(), first.start(), last.end(), first.line(),
                first.column(), last.endLine(), last.endColumn());
    }

    /**
     * Returns a human-readable string representation of the metadata location.
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
