package io.sapl.vaadin;

public class ValidationFinishedEvent {
	private Issue[] issues;
	
	public ValidationFinishedEvent(Issue[] issues) {
		this.issues = issues;
	}
	
	public Issue[] getIssues() {
		return this.issues;
	}
}
