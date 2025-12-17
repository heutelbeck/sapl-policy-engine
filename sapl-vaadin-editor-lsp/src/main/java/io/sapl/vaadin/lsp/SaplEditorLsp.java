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

import elemental.json.Json;
import elemental.json.JsonArray;
import elemental.json.JsonObject;
import io.sapl.api.SaplVersion;
import io.sapl.api.coverage.LineCoverageStatus;
import io.sapl.api.coverage.PolicyCoverageData;
import io.sapl.vaadin.DocumentChangedEvent;
import io.sapl.vaadin.DocumentChangedListener;
import io.sapl.vaadin.Issue;
import io.sapl.vaadin.ValidationFinishedEvent;
import io.sapl.vaadin.ValidationFinishedListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * SAPL editor using CodeMirror 6 with Language Server Protocol (LSP)
 * integration.
 * Provides syntax highlighting, diagnostics, and completion via LSP over
 * WebSocket.
 */
@Slf4j
@Tag("sapl-editor-lsp")
@JsModule("./sapl-editor-lsp.js")
@NpmPackage(value = "codemirror", version = "6.0.1")
@NpmPackage(value = "@codemirror/state", version = "6.4.1")
@NpmPackage(value = "@codemirror/view", version = "6.26.3")
@NpmPackage(value = "@codemirror/language", version = "6.10.2")
@NpmPackage(value = "@codemirror/commands", version = "6.5.0")
@NpmPackage(value = "@codemirror/autocomplete", version = "6.16.2")
@NpmPackage(value = "@codemirror/lint", version = "6.8.0")
@NpmPackage(value = "@codemirror/theme-one-dark", version = "6.1.2")
@NpmPackage(value = "@codemirror/merge", version = "6.8.0")
public class SaplEditorLsp extends Component implements HasSize {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private static final String PROP_DOCUMENT             = "document";
    private static final String PROP_LANGUAGE             = "language";
    private static final String PROP_WS_URL               = "wsUrl";
    private static final String PROP_IS_DARK_THEME        = "isDarkTheme";
    private static final String PROP_IS_READ_ONLY         = "isReadOnly";
    private static final String PROP_HAS_LINE_NUMBERS     = "hasLineNumbers";
    private static final String PROP_IS_MERGE_MODE        = "isMergeMode";
    private static final String PROP_MERGE_RIGHT_CONTENT  = "mergeRightContent";
    private static final String PROP_MATCH_BRACKETS       = "matchBrackets";
    private static final String PROP_AUTO_CLOSE_BRACKETS  = "autoCloseBrackets";
    private static final String PROP_HIGHLIGHT_CHANGES    = "highlightChanges";
    private static final String PROP_COLLAPSE_UNCHANGED   = "collapseUnchanged";
    private static final String PROP_CONFIGURATION_ID     = "configurationId";
    private static final String PROP_AUTOCOMPLETE_TRIGGER = "autocompleteTrigger";
    private static final String PROP_AUTOCOMPLETE_DELAY   = "autocompleteDelay";

    @Getter
    private String document;

    private final List<DocumentChangedListener>    documentChangedListeners    = new ArrayList<>();
    private final List<ValidationFinishedListener> validationFinishedListeners = new ArrayList<>();

    /**
     * Creates a SAPL editor with default configuration.
     */
    public SaplEditorLsp() {
        this(new SaplEditorLspConfiguration());
    }

    /**
     * Creates a SAPL editor with the specified configuration.
     *
     * @param config the editor configuration
     */
    public SaplEditorLsp(SaplEditorLspConfiguration config) {
        getElement().getStyle().set(ElementConstants.STYLE_WIDTH, "100%");
        getElement().getStyle().set(ElementConstants.STYLE_HEIGHT, "100%");
        applyConfiguration(config);
    }

    private void applyConfiguration(SaplEditorLspConfiguration config) {
        var element = getElement();
        element.setProperty(PROP_LANGUAGE, config.getLanguage());
        element.setProperty(PROP_IS_DARK_THEME, config.isDarkTheme());
        element.setProperty(PROP_IS_READ_ONLY, config.isReadOnly());
        element.setProperty(PROP_HAS_LINE_NUMBERS, config.isHasLineNumbers());
        element.setProperty(PROP_AUTOCOMPLETE_TRIGGER, config.getAutocompleteTrigger().name().toLowerCase());
        element.setProperty(PROP_AUTOCOMPLETE_DELAY, config.getAutocompleteDelay());
        if (config.getWsUrl() != null) {
            element.setProperty(PROP_WS_URL, config.getWsUrl());
        }
    }

    /**
     * Sets the WebSocket URL for LSP communication.
     *
     * @param wsUrl the WebSocket URL (e.g., "ws://localhost:8080/lsp")
     */
    public void setWsUrl(String wsUrl) {
        getElement().setProperty(PROP_WS_URL, wsUrl);
    }

    /**
     * Sets the language mode (sapl or sapltest).
     *
     * @param language "sapl" or "sapltest"
     */
    public void setLanguage(String language) {
        getElement().setProperty(PROP_LANGUAGE, language);
    }

    /**
     * Sets the document content.
     *
     * @param document the document text
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
     * Sets the configuration ID for this editor instance.
     * The configuration ID is used to identify the context for policy evaluation.
     *
     * @param configurationId the configuration identifier
     */
    public void setConfigurationId(String configurationId) {
        getElement().setProperty(PROP_CONFIGURATION_ID, configurationId);
        getElement().callJsFunction("setConfigurationId", configurationId);
    }

    /**
     * Gets the configuration ID for this editor instance.
     *
     * @return the configuration identifier
     */
    public String getConfigurationId() {
        return getElement().getProperty(PROP_CONFIGURATION_ID, "");
    }

    /**
     * Sets the autocomplete trigger mode.
     * Note: This rebuilds the editor to apply the change.
     *
     * @param trigger the trigger mode (MANUAL for Ctrl+Space only, ON_TYPING for
     * automatic)
     */
    public void setAutocompleteTrigger(SaplEditorLspConfiguration.AutocompleteTrigger trigger) {
        getElement().setProperty(PROP_AUTOCOMPLETE_TRIGGER, trigger.name().toLowerCase());
        getElement().callJsFunction("setAutocompleteTrigger", trigger.name().toLowerCase());
    }

    /**
     * Sets the autocomplete delay for ON_TYPING mode.
     *
     * @param delayMs delay in milliseconds before autocomplete activates
     */
    public void setAutocompleteDelay(int delayMs) {
        getElement().setProperty(PROP_AUTOCOMPLETE_DELAY, delayMs);
        getElement().callJsFunction("setAutocompleteDelay", delayMs);
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
     * Sets the revert controls direction in merge mode.
     * This controls which direction changes can be reverted.
     *
     * @param direction "a-to-b", "b-to-a", or null to disable revert controls
     */
    public void setRevertControls(String direction) {
        getElement().callJsFunction("setRevertControls", direction);
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
     * Sets coverage highlighting for lines in the editor based on aggregated
     * test coverage data.
     * Coverage is automatically cleared when the document is edited.
     *
     * @param coverageData the aggregated policy coverage data from test execution
     */
    public void setCoverage(PolicyCoverageData coverageData) {
        if (coverageData == null) {
            clearCoverage();
            return;
        }
        var lineCoverage = coverageData.getLineCoverage();
        if (lineCoverage.isEmpty()) {
            clearCoverage();
            return;
        }
        var jsonArray = Json.createArray();
        var index     = 0;
        for (var item : lineCoverage) {
            if (item.status() == LineCoverageStatus.IRRELEVANT) {
                continue;
            }
            var jsonItem = Json.createObject();
            jsonItem.put("line", item.line());
            jsonItem.put("status", mapCoverageStatus(item.status()));
            var summary = item.getSummary();
            if (summary != null) {
                jsonItem.put("summary", summary);
            }
            jsonArray.set(index++, jsonItem);
        }
        getElement().callJsFunction("setCoverageData", jsonArray);
    }

    private static String mapCoverageStatus(LineCoverageStatus status) {
        return switch (status) {
        case FULLY_COVERED     -> "covered";
        case PARTIALLY_COVERED -> "partial";
        case NOT_COVERED       -> "uncovered";
        case IRRELEVANT        -> "ignored";
        };
    }

    /**
     * Clears all coverage highlighting from the editor.
     */
    public void clearCoverage() {
        getElement().callJsFunction("clearCoverage");
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
     * Registers a validation finished listener.
     *
     * @param listener the listener
     */
    public void addValidationFinishedListener(ValidationFinishedListener listener) {
        validationFinishedListeners.add(listener);
    }

    /**
     * Removes a validation finished listener.
     *
     * @param listener the listener to remove
     */
    public void removeValidationFinishedListener(ValidationFinishedListener listener) {
        validationFinishedListeners.remove(listener);
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

    /**
     * Called from JavaScript when validation results are received from LSP.
     *
     * @param jsonIssues array of diagnostic issues
     */
    @ClientCallable
    protected void onValidation(JsonArray jsonIssues) {
        var issues = new ArrayList<Issue>();
        for (int i = 0; jsonIssues != null && i < jsonIssues.length(); i++) {
            JsonObject obj = jsonIssues.getObject(i);
            issues.add(new Issue(obj));
        }

        log.debug("LSP validation completed with {} issues", issues.size());

        var issueArray = issues.toArray(new Issue[0]);
        for (var listener : validationFinishedListeners) {
            listener.onValidationFinished(new ValidationFinishedEvent(issueArray));
        }
    }
}
