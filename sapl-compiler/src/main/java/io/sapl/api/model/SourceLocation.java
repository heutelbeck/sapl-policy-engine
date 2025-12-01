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
 * highlighting. Used for error reporting and debugging to indicate where in a
 * policy document an error occurred.
 *
 * @param documentName the name or identifier of the SAPL document (may be null)
 * @param documentSource the full source text of the document (may be null)
 * @param start the start offset in the document
 * @param end the end offset in the document
 * @param line the line number (1-based)
 */
public record SourceLocation(String documentName, String documentSource, int start, int end, int line)
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
            return "%s:%d [%d-%d]".formatted(documentName, line, start, end);
        }
        return "line %d [%d-%d]".formatted(line, start, end);
    }
}
