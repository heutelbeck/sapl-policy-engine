package io.sapl.grammar.web;

import com.vaadin.shared.ui.JavaScriptComponentState;

/**
 * This simple class implements the state of {@link SAPLTextArea}.
 */
public class SAPLTextAreaState extends JavaScriptComponentState {

	private static final long serialVersionUID = -2012847498611689509L;

	/**
	 * the text value
	 */
	private String text = "";

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

}
