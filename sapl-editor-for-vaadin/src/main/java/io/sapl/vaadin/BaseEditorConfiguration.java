package io.sapl.vaadin;

import lombok.Data;

/**
 * Base configuration object to initialize the editor.
 */
@Data
public class BaseEditorConfiguration {
	private boolean hasLineNumbers = true;
	private boolean autoCloseBrackets = true;
	private boolean matchBrackets = true;
	private int textUpdateDelay = 500;
}
