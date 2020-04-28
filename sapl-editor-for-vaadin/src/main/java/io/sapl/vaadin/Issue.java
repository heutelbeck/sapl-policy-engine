package io.sapl.vaadin;

import org.eclipse.xtext.diagnostics.Severity;

import lombok.Data;

@Data
public class Issue {
	private String description;
	private Severity severity;
	private Integer line;
	private Integer column;
	private Integer offset;
	private Integer length;
}