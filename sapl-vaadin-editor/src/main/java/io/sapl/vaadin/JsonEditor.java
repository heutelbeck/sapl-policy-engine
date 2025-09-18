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
// src/main/java/io/sapl/vaadin/JsonEditor.java
/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.sapl.vaadin;

import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.dom.Element;

import io.sapl.api.SaplVersion;

import java.io.Serializable;

/**
 * A JSON Editor component with syntax highlighting, linting, and optional
 * two-pane merge view.
 */
@Tag("json-editor")
@JsModule("./json-editor.js")
@NpmPackage(value = "jsonlint-webpack", version = "1.1.0")
@NpmPackage(value = "jquery", version = "3.7.1")
@NpmPackage(value = "codemirror", version = "5.65.16")
@NpmPackage(value = "diff-match-patch", version = "1.0.5")
public class JsonEditor extends BaseEditor implements HasSize {

    private static final long serialVersionUID = SaplVersion.VERISION_UID;

    private boolean mergeModeEnabled;
    private String  mergeRightContent;

    /**
     * Creates the editor component.
     *
     * @param config the editor configuration
     */
    public JsonEditor(JsonEditorConfiguration config) {
        applyBaseConfiguration(getElement(), config);
        this.mergeModeEnabled  = false;
        this.mergeRightContent = "";
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
     * Toggles linting on or off.
     *
     * @param isLint indicates if linting is to be activated
     */
    public void setLint(Boolean isLint) {
        getElement().setProperty("isLint", isLint);
    }

    /**
     * Enables or disables the merge mode without changing the right side content.
     *
     * @param enabled indicates whether merge mode is enabled
     */
    public void setMergeModeEnabled(boolean enabled) {
        this.mergeModeEnabled = enabled;
        getElement().callJsFunction("setMergeModeEnabled", enabled);
    }

    /**
     * Returns if merge mode is enabled.
     *
     * @return true if merge mode is enabled
     */
    public boolean isMergeModeEnabled() {
        return mergeModeEnabled;
    }

    /**
     * Sets the content on the right side in merge mode.
     *
     * @param content content for the right side of the merge view
     */
    public void setMergeRightContent(String content) {
        this.mergeRightContent = content == null ? "" : content;
        getElement().callJsFunction("setMergeRightContent", this.mergeRightContent);
    }

    /**
     * Returns the current right side content used in merge mode.
     *
     * @return content of the right side merge document
     */
    public String getMergeRightContent() {
        return mergeRightContent;
    }

    /**
     * Enables or disables display of custom change markers in both panes.
     *
     * @param enabled indicates whether custom change markers should be shown
     */
    public void setChangeMarkersEnabled(boolean enabled) {
        getElement().callJsFunction("enableChangeMarkers", enabled);
    }

    /**
     * Sets a merge option that maps to CodeMirror MergeView options.
     * Supported options: "revertButtons" (boolean), "showDifferences" (boolean),
     * "connect" (null or "align"), "collapseIdentical" (boolean),
     * "allowEditingOriginals" (boolean), "ignoreWhitespace" (boolean).
     *
     * @param option option name
     * @param value option value
     */
    public void setMergeOption(String option, Serializable value) {
        getElement().callJsFunction("setMergeOption", option, value);
    }

    /**
     * Navigates to next change in merge mode.
     */
    public void goToNextChange() {
        getElement().callJsFunction("nextChange");
    }

    /**
     * Navigates to previous change in merge mode.
     */
    public void goToPreviousChange() {
        getElement().callJsFunction("prevChange");
    }
}
