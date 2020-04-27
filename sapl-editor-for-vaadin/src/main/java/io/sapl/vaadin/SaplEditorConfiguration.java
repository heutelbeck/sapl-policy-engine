package io.sapl.vaadin;

import lombok.Data;

@Data
public class SaplEditorConfiguration {
	private boolean hasLineNumbers = true;
	private boolean autoCloseBrackets = true;
	private boolean matchBrackets = true;
	private int textUpdateDelay = 500;
}
