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
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.dom.Element;
import elemental.json.JsonArray;
import elemental.json.JsonObject;
import io.sapl.api.SaplVersion;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * An editor component for SAPLTest documents supporting code-completion,
 * syntax-highlighting, linting, and an optional
 * merge view.
 */
@Tag("sapl-test-editor")
@JsModule("./sapl-test-editor.js")
@NpmPackage(value = "jquery", version = "3.7.1")
@NpmPackage(value = "codemirror", version = "5.65.16")
public class SaplTestEditor extends BaseEditor implements HasSize {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private final List<ValidationFinishedListener> validationFinishedListeners = new ArrayList<>();

    public SaplTestEditor(SaplTestEditorConfiguration config) {
        Element element = getElement();
        applyBaseConfiguration(element, config);
    }

    // ---------------- Server -> Client API (forwarders to JS) ----------------

    public void setMergeModeEnabled(boolean enabled) {
        getElement().callJsFunction("setMergeModeEnabled", enabled);
    }

    public void enableMergeView() {
        setMergeModeEnabled(true);
    }

    public void disableMergeView() {
        setMergeModeEnabled(false);
    }

    /**
     * Set the right side content (origRight) in merge view.
     */
    public void setMergeRightContent(String content) {
        getElement().callJsFunction("setMergeRightContent", content);
    }

    /**
     * Generic merge option setter. Supported keys: revertButtons (boolean),
     * showDifferences (boolean), connect (null |
     * "align"), collapseIdentical (boolean), allowEditingOriginals (boolean),
     * ignoreWhitespace (boolean)
     */
    public void setMergeOption(String key, Serializable value) {
        getElement().callJsFunction("setMergeOption", key, value);
    }

    public void goToNextChange() {
        getElement().callJsFunction("goToNextChange");
    }

    public void goToPreviousChange() {
        getElement().callJsFunction("goToPreviousChange");
    }

    // ---------------- Validation bridge ----------------

    @ClientCallable
    protected void onValidation(JsonArray jsonIssues) {
        int         length = jsonIssues.length();
        List<Issue> issues = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            JsonObject jsonIssue = jsonIssues.getObject(i);
            issues.add(new Issue(jsonIssue));
        }

        for (ValidationFinishedListener listener : validationFinishedListeners) {
            listener.onValidationFinished(new ValidationFinishedEvent(issues.toArray(new Issue[0])));
        }
    }

    public void addValidationFinishedListener(ValidationFinishedListener listener) {
        this.validationFinishedListeners.add(listener);
    }

    public void removeValidationFinishedListener(ValidationFinishedListener listener) {
        this.validationFinishedListeners.remove(listener);
    }
}
