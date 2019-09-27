package io.sapl.grammar.web;

import com.vaadin.annotations.JavaScript;
import com.vaadin.annotations.StyleSheet;
import com.vaadin.data.HasValue;
import com.vaadin.shared.Registration;
import com.vaadin.ui.AbstractJavaScriptComponent;
import com.vaadin.util.ReflectTools;

/**
 * This class implements a Vaadin component to provide a web-based and JavaScript-powered
 * editor for policies written in SAPL. It is instrumenting Orion Code Edit as front-end
 * and Xtext services to provide code assist and syntax validation functionality.
 *
 * The SAPLTextArea could be used as surrogate for a regular Vaadin TextArea.
 */
@StyleSheet({ "vaadin://../webjars/codemirror/5.39.2/lib/codemirror.css",
		"vaadin://../webjars/codemirror/5.39.2/addon/hint/show-hint.css",
		"vaadin://../xtext/2.16.0/xtext-codemirror.css", "vaadin://../sapl-text-area-style.css" })
@JavaScript({ "vaadin://../webjars/requirejs/2.2.0/require.min.js", "vaadin://../sapl-text-area.js",
		"vaadin://../sapl-text-area-connector.js" })
public class SAPLTextArea extends AbstractJavaScriptComponent implements HasValue<String> {

	/**
	 * generated serialVersionUID
	 */
	private static final long serialVersionUID = -397371210998778511L;

	/**
	 * Default constructor.
	 */
	public SAPLTextArea() {
		// inject a JavaSctipt method that can be called by the JS-Connector to
		// propagate changes from Client to Server.
		addFunction("setText", arguments -> {
			String oldValue = getState().getText();
			getState().setText(arguments.getString(0));
			fireEvent(new ValueChangeEvent<>(this, this, oldValue, true));
		});
	}

	/**
	 * @see com.vaadin.data.HasValue#getValue()
	 */
	@Override
	public String getValue() {
		return getState().getText();
	}

	/**
	 * @see com.vaadin.data.HasValue#setValue(java.lang.Object)
	 */
	@Override
	public void setValue(String aValue) {
		getState().setText(aValue);
		callFunction("stateChanged");
	}

	/**
	 * @see com.vaadin.ui.AbstractJavaScriptComponent#getState()
	 */
	@Override
	protected SAPLTextAreaState getState() {
		return (SAPLTextAreaState) super.getState();
	}

	/**
	 * @see com.vaadin.data.HasValue#addValueChangeListener(com.vaadin.data.HasValue.ValueChangeListener)
	 */
	@Override
	public Registration addValueChangeListener(ValueChangeListener<String> aListener) {
		return addListener(ValueChangeEvent.class, aListener,
				ReflectTools.findMethod(ValueChangeListener.class, "valueChange", ValueChangeEvent.class));
	}

	/**
	 * @see com.vaadin.ui.AbstractComponent#setReadOnly(boolean)
	 */
	@Override
	public void setReadOnly(boolean readOnly) {
		super.setReadOnly(readOnly);
	}

	/**
	 * @see com.vaadin.ui.AbstractComponent#isReadOnly()
	 */
	@Override
	public boolean isReadOnly() {
		return super.isReadOnly();
	}

	/**
	 * @see com.vaadin.ui.AbstractComponent#setRequiredIndicatorVisible(boolean)
	 */
	@Override
	public void setRequiredIndicatorVisible(boolean visible) {
		super.setRequiredIndicatorVisible(visible);
	}

	/**
	 * @see com.vaadin.ui.AbstractComponent#isRequiredIndicatorVisible()
	 */
	@Override
	public boolean isRequiredIndicatorVisible() {
		return super.isRequiredIndicatorVisible();
	}

	/**
	 * @see com.vaadin.data.HasValue#getEmptyValue()
	 */
	@Override
	public String getEmptyValue() {
		return "";
	}

	/**
	 * @see com.vaadin.data.HasValue#isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		return HasValue.super.isEmpty();
	}

	/**
	 * @see com.vaadin.data.HasValue#clear()
	 */
	@Override
	public void clear() {
		HasValue.super.clear();
	}

}
