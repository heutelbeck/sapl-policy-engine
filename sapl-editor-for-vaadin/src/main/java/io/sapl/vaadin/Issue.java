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
			setDescription(jsonObject.getString(DESCRIPTION));

		if (jsonObject.hasKey(SEVERITY)) {
			String severityString = jsonObject.getString(SEVERITY);
			Severity severity = Severity.INFO;
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
				break;
			}
			setSeverity(severity);
		}

		if (jsonObject.hasKey(LINE))
			setLine((int) jsonObject.getNumber(LINE));

		if (jsonObject.hasKey(COLUMN))
			setColumn((int) jsonObject.getNumber(COLUMN));

		if (jsonObject.hasKey(OFFSET))
			setOffset((int) jsonObject.getNumber(OFFSET));

		if (jsonObject.hasKey(LENGTH))
			setLength((int) jsonObject.getNumber(LENGTH));
	}

}