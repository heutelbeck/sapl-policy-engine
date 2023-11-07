/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import org.eclipse.xtext.diagnostics.Severity;

import elemental.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Describes a code issue identified by the linting process in the editor.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Issue {
    private static final String DESCRIPTION_KEY = "description";
    private static final String SEVERITY_KEY    = "severity";
    private static final String LINE_KEY        = "line";
    private static final String COLUMN_KEY      = "column";
    private static final String OFFSET_KEY      = "offset";
    private static final String LENGTH_KEY      = "length";

    private String   description;
    private Severity severity;
    private Integer  line;
    private Integer  column;
    private Integer  offset;
    private Integer  length;

    /**
     * Creates an Issue object from a JSON representation.
     *
     * @param jsonObject a JSON issue description.
     */
    public Issue(JsonObject jsonObject) {

        if (jsonObject.hasKey(DESCRIPTION_KEY))
            description = jsonObject.getString(DESCRIPTION_KEY);

        if (jsonObject.hasKey(SEVERITY_KEY)) {
            String severityString = jsonObject.getString(SEVERITY_KEY);
            switch (severityString) {
            case "error" -> severity = Severity.ERROR;
            case "warning" -> severity = Severity.WARNING;
            case "ignore" -> severity = Severity.IGNORE;
            default -> severity = Severity.INFO;
            }
        }

        if (jsonObject.hasKey(LINE_KEY))
            line = (int) jsonObject.getNumber(LINE_KEY);

        if (jsonObject.hasKey(COLUMN_KEY))
            column = (int) jsonObject.getNumber(COLUMN_KEY);

        if (jsonObject.hasKey(OFFSET_KEY))
            offset = (int) jsonObject.getNumber(OFFSET_KEY);

        if (jsonObject.hasKey(LENGTH_KEY))
            length = (int) jsonObject.getNumber(LENGTH_KEY);
    }

}
