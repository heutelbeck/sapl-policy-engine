package io.sapl.vaadin;

import java.util.Arrays;

public class ValidationFinishedEvent {
	private Issue[] issues;
	
	public ValidationFinishedEvent(Issue[] issues) {
		this.issues = Arrays.copyOf(issues, issues.length);
	}
	
	public Issue[] getIssues() {
		return Arrays.copyOf(issues, issues.length);
	}
}
