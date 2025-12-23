/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.vaadin.lsp;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.dom.ElementConstants;

import io.sapl.api.SaplVersion;
import io.sapl.vaadin.DocumentChangedEvent;
import io.sapl.vaadin.DocumentChangedListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON editor using CodeMirror 6 with syntax highlighting, linting, and merge
 * view.
 */
@Slf4j
@Tag("json-editor-lsp")
@JsModule("./json-editor-lsp.js")
@NpmPackage(value = "@codemirror/lang-json", version = "6.0.1")
@NpmPackage(value = "@codemirror/merge", version = "6.8.0")
public class JsonEditorLsp extends Component implements HasSize {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private static final String PROP_IS_DARK_THEME       = "isDarkTheme";
    private static final String PROP_IS_READ_ONLY        = "isReadOnly";
    private static final String PROP_HAS_LINE_NUMBERS    = "hasLineNumbers";
    private static final String PROP_IS_LINT             = "isLint";
    private static final String PROP_IS_MERGE_MODE       = "isMergeMode";
    private static final String PROP_MERGE_RIGHT_CONTENT = "mergeRightContent";
    private static final String PROP_MATCH_BRACKETS      = "matchBrackets";
    private static final String PROP_AUTO_CLOSE_BRACKETS = "autoCloseBrackets";
    private static final String PROP_HIGHLIGHT_CHANGES   = "highlightChanges";
    private static final String PROP_COLLAPSE_UNCHANGED  = "collapseUnchanged";

    @Getter
    private String document;

    private final List<DocumentChangedListener> documentChangedListeners = new ArrayList<>();

    /**
     * Creates a JSON editor with default configuration.
     */
    public JsonEditorLsp() {
        this(new JsonEditorLspConfiguration());
    }

    /**
     * Creates a JSON editor with the specified configuration.
     *
     * @param config the editor configuration
     */
    public JsonEditorLsp(JsonEditorLspConfiguration config) {
        getElement().getStyle().set(ElementConstants.STYLE_WIDTH, "100%");
        getElement().getStyle().set(ElementConstants.STYLE_HEIGHT, "100%");
        applyConfiguration(config);
    }

    private void applyConfiguration(JsonEditorLspConfiguration config) {
        Element element = getElement();
        element.setProperty(PROP_IS_DARK_THEME, config.isDarkTheme());
        element.setProperty(PROP_IS_READ_ONLY, config.isReadOnly());
        element.setProperty(PROP_HAS_LINE_NUMBERS, config.isHasLineNumbers());
        element.setProperty(PROP_IS_LINT, config.isLint());
    }

    /**
     * Sets the document content.
     *
     * @param document the JSON document text
     */
    public void setDocument(String document) {
        this.document = document;
        Element element = getElement();
        element.callJsFunction("setEditorDocument", element, document);
        for (var listener : documentChangedListeners) {
            listener.onDocumentChanged(new DocumentChangedEvent(document, false));
        }
    }

    /**
     * Enables or disables the dark theme.
     *
     * @param isDarkTheme true for dark theme
     */
    public void setDarkTheme(boolean isDarkTheme) {
        getElement().callJsFunction("setDarkTheme", isDarkTheme);
    }

    /**
     * Returns whether dark theme is enabled.
     *
     * @return true if dark theme is enabled
     */
    public boolean isDarkTheme() {
        return getElement().getProperty(PROP_IS_DARK_THEME, false);
    }

    /**
     * Enables or disables read-only mode.
     *
     * @param isReadOnly true for read-only mode
     */
    public void setReadOnly(boolean isReadOnly) {
        getElement().callJsFunction("setReadOnly", isReadOnly);
    }

    /**
     * Returns whether the editor is read-only.
     *
     * @return true if read-only
     */
    public boolean isReadOnly() {
        return getElement().getProperty(PROP_IS_READ_ONLY, false);
    }

    /**
     * Enables or disables JSON linting.
     *
     * @param isLint true to enable linting
     */
    public void setLint(boolean isLint) {
        getElement().callJsFunction("setLint", isLint);
    }

    /**
     * Returns whether linting is enabled.
     *
     * @return true if linting is enabled
     */
    public boolean isLint() {
        return getElement().getProperty(PROP_IS_LINT, true);
    }

    /**
     * Enables or disables merge mode (side-by-side diff view).
     *
     * @param enabled true to enable merge mode
     */
    public void setMergeModeEnabled(boolean enabled) {
        getElement().callJsFunction("setMergeModeEnabled", enabled);
    }

    /**
     * Returns whether merge mode is enabled.
     *
     * @return true if merge mode is enabled
     */
    public boolean isMergeModeEnabled() {
        return getElement().getProperty(PROP_IS_MERGE_MODE, false);
    }

    /**
     * Sets the content for the right side of the merge view.
     *
     * @param content the content for the right pane
     */
    public void setMergeRightContent(String content) {
        getElement().callJsFunction("setMergeRightContent", content);
    }

    /**
     * Gets the content from the right side of the merge view.
     *
     * @return the right pane content
     */
    public String getMergeRightContent() {
        return getElement().getProperty(PROP_MERGE_RIGHT_CONTENT, "");
    }

    /**
     * Enables or disables bracket matching highlighting.
     *
     * @param enabled true to enable bracket matching
     */
    public void setMatchBrackets(boolean enabled) {
        getElement().callJsFunction("setMatchBrackets", enabled);
    }

    /**
     * Returns whether bracket matching is enabled.
     *
     * @return true if bracket matching is enabled
     */
    public boolean isMatchBrackets() {
        return getElement().getProperty(PROP_MATCH_BRACKETS, true);
    }

    /**
     * Enables or disables auto-closing of brackets.
     *
     * @param enabled true to enable auto-close brackets
     */
    public void setAutoCloseBrackets(boolean enabled) {
        getElement().callJsFunction("setAutoCloseBrackets", enabled);
    }

    /**
     * Returns whether auto-close brackets is enabled.
     *
     * @return true if auto-close brackets is enabled
     */
    public boolean isAutoCloseBrackets() {
        return getElement().getProperty(PROP_AUTO_CLOSE_BRACKETS, true);
    }

    /**
     * Enables or disables highlighting of changes in merge mode.
     *
     * @param enabled true to highlight changes
     */
    public void setHighlightChanges(boolean enabled) {
        getElement().callJsFunction("setHighlightChanges", enabled);
    }

    /**
     * Returns whether change highlighting is enabled in merge mode.
     *
     * @return true if change highlighting is enabled
     */
    public boolean isHighlightChanges() {
        return getElement().getProperty(PROP_HIGHLIGHT_CHANGES, true);
    }

    /**
     * Enables or disables collapsing of unchanged regions in merge mode.
     *
     * @param enabled true to collapse unchanged regions
     */
    public void setCollapseUnchanged(boolean enabled) {
        getElement().callJsFunction("setCollapseUnchanged", enabled);
    }

    /**
     * Returns whether unchanged region collapsing is enabled in merge mode.
     *
     * @return true if collapsing is enabled
     */
    public boolean isCollapseUnchanged() {
        return getElement().getProperty(PROP_COLLAPSE_UNCHANGED, false);
    }

    /**
     * Enables or disables synchronized scrolling between the two editors in merge
     * mode.
     * When enabled, scrolling one editor will scroll the other proportionally.
     *
     * @param enabled true to enable synchronized scrolling
     */
    public void setSyncScroll(boolean enabled) {
        getElement().callJsFunction("setSyncScroll", enabled);
    }

    /**
     * Enables or disables the change gutter in merge mode.
     * The gutter shows markers next to changed lines.
     *
     * @param enabled true to show the change gutter
     */
    public void setGutter(boolean enabled) {
        getElement().callJsFunction("setGutter", enabled);
    }

    /**
     * Sets the number of context lines to show around changes when collapsing
     * unchanged regions.
     *
     * @param margin number of lines to show (default: 3)
     */
    public void setCollapseUnchangedMargin(int margin) {
        getElement().callJsFunction("setCollapseUnchangedMargin", margin);
    }

    /**
     * Sets a merge view option with a string value.
     * Supported options: "revertControls", "syncScroll"
     *
     * @param option the option name
     * @param value the string value, or null to unset
     */
    public void setMergeOption(String option, String value) {
        getElement().callJsFunction("setMergeOption", option, value);
    }

    /**
     * Sets a merge view option with a boolean value.
     * Supported options: "gutter", "highlightChanges", "collapseUnchanged",
     * "syncScroll"
     *
     * @param option the option name
     * @param value the boolean value
     */
    public void setMergeOption(String option, boolean value) {
        getElement().callJsFunction("setMergeOption", option, value);
    }

    /**
     * Navigates to the next difference in merge mode.
     */
    public void goToNextChange() {
        getElement().callJsFunction("goToNextChange");
    }

    /**
     * Navigates to the previous difference in merge mode.
     */
    public void goToPreviousChange() {
        getElement().callJsFunction("goToPreviousChange");
    }

    /**
     * Scrolls the editor to the bottom.
     */
    public void scrollToBottom() {
        getElement().callJsFunction("scrollToBottom");
    }

    /**
     * Refreshes the editor.
     */
    public void refresh() {
        getElement().callJsFunction("onRefreshEditor");
    }

    /**
     * Appends text to the end of the editor contents.
     *
     * @param text text to append
     */
    public void appendText(String text) {
        getElement().callJsFunction("appendText", text);
    }

    /**
     * Registers a document changed listener.
     *
     * @param listener the listener
     */
    public void addDocumentChangedListener(DocumentChangedListener listener) {
        documentChangedListeners.add(listener);
    }

    /**
     * Removes a document changed listener.
     *
     * @param listener the listener to remove
     */
    public void removeDocumentChangedListener(DocumentChangedListener listener) {
        documentChangedListeners.remove(listener);
    }

    /**
     * Called from JavaScript when the document changes.
     *
     * @param newValue the new document content
     */
    @ClientCallable
    protected void onDocumentChanged(String newValue) {
        document = newValue;
        for (var listener : documentChangedListeners) {
            listener.onDocumentChanged(new DocumentChangedEvent(newValue, true));
        }
    }
}
