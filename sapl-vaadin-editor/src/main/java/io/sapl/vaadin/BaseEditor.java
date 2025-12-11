/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.dom.ElementConstants;
import io.sapl.api.SaplVersion;
import lombok.Getter;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for SAPL and JSON editors.
 */
public class BaseEditor extends Component {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private static final String IS_LINT             = "isLint";
    private static final String TEXT_UPDATE_DELAY   = "textUpdateDelay";
    private static final String MATCH_BRACKETS      = "matchBrackets";
    private static final String AUTO_CLOSE_BRACKETS = "autoCloseBrackets";
    private static final String HAS_LINE_NUMBERS    = "hasLineNumbers";
    private static final String IS_DARK_THEME       = "isDarkTheme";
    private static final String IS_READ_ONLY_KEY    = "isReadOnly";

    @Getter
    private String                              document;
    private final List<DocumentChangedListener> documentChangedListeners;
    private final List<EditorClickedListener>   editorClickedListeners;

    /**
     * Creates the editor.
     */
    public BaseEditor() {
        super();
        this.documentChangedListeners = new ArrayList<>();
        this.editorClickedListeners   = new ArrayList<>();
        getElement().getStyle().set(ElementConstants.STYLE_WIDTH, "100%");
        getElement().getStyle().set(ElementConstants.STYLE_HEIGHT, "100%");
    }

    protected static void applyBaseConfiguration(Element element, BaseEditorConfiguration config) {
        element.setProperty(HAS_LINE_NUMBERS, config.isHasLineNumbers());
        element.setProperty(AUTO_CLOSE_BRACKETS, config.isAutoCloseBrackets());
        element.setProperty(MATCH_BRACKETS, config.isMatchBrackets());
        element.setProperty(TEXT_UPDATE_DELAY, config.getTextUpdateDelay());
        element.setProperty(IS_READ_ONLY_KEY, config.isReadOnly());
        element.setProperty(IS_LINT, config.isLint());
        element.setProperty(IS_DARK_THEME, config.isDarkTheme());
    }

    @ClientCallable
    protected void onDocumentChanged(String newValue) {
        document = newValue;
        for (DocumentChangedListener listener : documentChangedListeners) {
            listener.onDocumentChanged(new DocumentChangedEvent(newValue, true));
        }
    }

    @ClientCallable
    protected void onEditorClicked(Integer line, String content) {
        for (EditorClickedListener listener : editorClickedListeners)
            listener.onEditorClicked(new EditorClickedEvent(line, content));
    }

    /**
     * Sets the current document for the editor.
     *
     * @param document
     * The current document.
     */
    public void setDocument(String document) {
        this.document = document;
        Element element = getElement();
        element.callJsFunction("setEditorDocument", element, document);
        for (DocumentChangedListener listener : documentChangedListeners) {
            listener.onDocumentChanged(new DocumentChangedEvent(document, false));
        }
    }

    /**
     * Registers a document changed listener. The document changed event will be
     * raised when the document was changed in
     * the editor.
     *
     * @param listener
     * The listener that will be called upon event invocation.
     */
    public void addDocumentChangedListener(DocumentChangedListener listener) {
        this.documentChangedListeners.add(listener);
    }

    /**
     * Registers an editor clicked listener. The editor clicked event will be raised
     * when the editor was clicked.
     *
     * @param listener
     * The listener that will be called upon event invocation.
     */
    public void addEditorClickedListener(EditorClickedListener listener) {
        this.editorClickedListeners.add(listener);
    }

    /**
     * Removes a registered document changed listener.
     *
     * @param listener
     * The registered listener that should be removed.
     */
    public void removeDocumentChangedListener(DocumentChangedListener listener) {
        this.documentChangedListeners.remove(listener);
    }

    /**
     * Removes a registered editor clicked listener.
     *
     * @param listener
     * The registered listener that should be removed.
     */
    public void removeEditorClickedListener(EditorClickedListener listener) {
        this.editorClickedListeners.remove(listener);
    }

    /**
     * This function enables or disables the read-only mode of the editor.
     *
     * @param isReadOnly
     * set to true if editor should be read only
     */
    public void setReadOnly(Boolean isReadOnly) {
        Element element = getElement();
        element.setProperty(IS_READ_ONLY_KEY, isReadOnly);
    }

    /**
     * This function returns the current read-only status of the editor.
     *
     * @return The current read-only as a Boolean.
     */
    public Boolean isReadOnly() {
        Element element = getElement();
        return element.getProperty(IS_READ_ONLY_KEY, false);
    }

    /**
     * If this function is called the editor scrolls to the bottom of the textarea.
     */
    public void scrollToBottom() {
        Element element = getElement();
        element.callJsFunction("scrollToBottom");
    }

    /**
     * This function enables or disables the Dark Theme of the editor.
     *
     * @param isDarkTheme
     * set to true if editor should be in dark theme
     */
    public void setDarkTheme(Boolean isDarkTheme) {
        Element element = getElement();
        element.setProperty(IS_DARK_THEME, isDarkTheme);
    }

    /**
     * This function returns a Boolean to whether the editor uses the Dark Theme or
     * not.
     *
     * @return Enabled or disabled Dark Theme as a Boolean.
     */
    public Boolean isDarkTheme() {
        Element element = getElement();
        return element.getProperty(IS_DARK_THEME, false);
    }

    /**
     * This function returns a Boolean whether if the editor has linting enabled or
     * not.
     *
     * @return Enabled or disabled lint as a Boolean.
     */
    public Boolean isLint() {
        Element element = getElement();
        return element.getProperty(IS_LINT, true);
    }
}
