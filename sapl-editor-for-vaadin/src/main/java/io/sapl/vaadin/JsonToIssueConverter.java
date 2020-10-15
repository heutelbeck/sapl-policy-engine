package io.sapl.vaadin;

import org.eclipse.xtext.diagnostics.Severity;

import elemental.json.JsonObject;
import lombok.experimental.UtilityClass;

@UtilityClass
public class JsonToIssueConverter {

	private static final String DESCRIPTION = "description";
	private static final String SEVERITY = "severity";
	private static final String LINE = "line";
	private static final String COLUMN = "column";
	private static final String OFFSET = "offset";
	private static final String LENGTH = "length";

	public static Issue convert(JsonObject jsonObject) {
		Issue issue = new Issue();

		if (jsonObject.hasKey(DESCRIPTION))
			issue.setDescription(jsonObject.getString(DESCRIPTION));

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
			issue.setSeverity(severity);
		}

		if (jsonObject.hasKey(LINE))
			issue.setLine((int) jsonObject.getNumber(LINE));

		if (jsonObject.hasKey(COLUMN))
			issue.setColumn((int) jsonObject.getNumber(COLUMN));

		if (jsonObject.hasKey(OFFSET))
			issue.setOffset((int) jsonObject.getNumber(OFFSET));

		if (jsonObject.hasKey(LENGTH))
			issue.setLength((int) jsonObject.getNumber(LENGTH));

		return issue;
	}

}
