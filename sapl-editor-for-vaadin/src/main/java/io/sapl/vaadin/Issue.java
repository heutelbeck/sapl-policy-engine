/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Issue {
	private static final String DESCRIPTION = "description";
	private static final String SEVERITY = "severity";
	private static final String LINE = "line";
	private static final String COLUMN = "column";
	private static final String OFFSET = "offset";
	private static final String LENGTH = "length";

	private String description;
	private Severity severity;
	private Integer line;
	private Integer column;
	private Integer offset;
	private Integer length;

	public Issue(JsonObject jsonObject) {

		if (jsonObject.hasKey(DESCRIPTION))
			description = jsonObject.getString(DESCRIPTION);

		if (jsonObject.hasKey(SEVERITY)) {
			String severityString = jsonObject.getString(SEVERITY);
			switch (severityString) {
			case "error":
				severity = Severity.ERROR;
				break;
			case "warning":
				severity = Severity.WARNING;
				break;
			case "ignore":
				severity = Severity.IGNORE;
				break;
			default:
				severity = Severity.INFO;
				break;
			}
		}

		if (jsonObject.hasKey(LINE))
			line = (int) jsonObject.getNumber(LINE);

		if (jsonObject.hasKey(COLUMN))
			column = (int) jsonObject.getNumber(COLUMN);

		if (jsonObject.hasKey(OFFSET))
			offset = (int) jsonObject.getNumber(OFFSET);

		if (jsonObject.hasKey(LENGTH))
			length = (int) jsonObject.getNumber(LENGTH);
	}

}