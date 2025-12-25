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
package io.sapl.vaadin;

import elemental.json.JsonObject;
import io.sapl.api.SaplVersion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * Describes a code issue identified by the validation process in the editor.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Issue implements Serializable {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private static final String DESCRIPTION_KEY  = "description";
    private static final String SEVERITY_KEY     = "severity";
    private static final String LINE_KEY         = "line";
    private static final String COLUMN_KEY       = "column";
    private static final String OFFSET_KEY       = "offset";
    private static final String LENGTH_KEY       = "length";
    private static final String START_LINE_KEY   = "startLine";
    private static final String START_COLUMN_KEY = "startColumn";
    private static final String END_LINE_KEY     = "endLine";
    private static final String END_COLUMN_KEY   = "endColumn";

    private String        description;
    private IssueSeverity severity;
    private Integer       line;
    private Integer       column;
    private Integer       offset;
    private Integer       length;

    /**
     * Creates an Issue object from a JSON representation.
     *
     * @param jsonObject a JSON issue description.
     */
    public Issue(JsonObject jsonObject) {
        if (jsonObject.hasKey(DESCRIPTION_KEY)) {
            description = jsonObject.getString(DESCRIPTION_KEY);
        }

        if (jsonObject.hasKey(SEVERITY_KEY)) {
            var severityString = jsonObject.getString(SEVERITY_KEY);
            severity = parseSeverity(severityString);
        }

        // Support both 'line' and 'startLine' keys
        if (jsonObject.hasKey(LINE_KEY)) {
            line = (int) jsonObject.getNumber(LINE_KEY);
        } else if (jsonObject.hasKey(START_LINE_KEY)) {
            line = (int) jsonObject.getNumber(START_LINE_KEY);
        }

        // Support both 'column' and 'startColumn' keys
        if (jsonObject.hasKey(COLUMN_KEY)) {
            column = (int) jsonObject.getNumber(COLUMN_KEY);
        } else if (jsonObject.hasKey(START_COLUMN_KEY)) {
            column = (int) jsonObject.getNumber(START_COLUMN_KEY);
        }

        if (jsonObject.hasKey(OFFSET_KEY)) {
            offset = (int) jsonObject.getNumber(OFFSET_KEY);
        }

        if (jsonObject.hasKey(LENGTH_KEY)) {
            length = (int) jsonObject.getNumber(LENGTH_KEY);
        }
    }

    private IssueSeverity parseSeverity(String severityString) {
        if (severityString == null) {
            return IssueSeverity.INFO;
        }
        return switch (severityString.toUpperCase()) {
        case "ERROR"   -> IssueSeverity.ERROR;
        case "WARNING" -> IssueSeverity.WARNING;
        case "HINT"    -> IssueSeverity.HINT;
        case "IGNORE"  -> IssueSeverity.IGNORE;
        default        -> IssueSeverity.INFO;
        };
    }
}
