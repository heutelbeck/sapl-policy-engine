/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;

import elemental.json.JsonArray;

/**
 * An editor component for SAPL documents supporting code-completion,
 * syntax-highlighting, and linting.
 */
@Tag("sapl-editor")
@JsModule("./sapl-editor.js")
@NpmPackage(value = "codemirror", version = "5.65.16")
public class SaplEditor extends BaseEditor {

    private List<ValidationFinishedListener> validationFinishedListeners = new ArrayList<>();

    /**
     * Creates an editor component.
     *
     * @param config the editor settings-
     */
    public SaplEditor(SaplEditorConfiguration config) {
        var element = getElement();
        applyBaseConfiguration(element, config);
    }

    @ClientCallable
    protected void onValidation(JsonArray jsonIssues) {

        ArrayList<Object> issues = new ArrayList<>();
        for (int i = 0; jsonIssues != null && i < jsonIssues.length(); i++) {
            var jsonIssue = jsonIssues.getObject(i);
            var issue     = new Issue(jsonIssue);
            issues.add(issue);
        }

        for (ValidationFinishedListener listener : validationFinishedListeners) {
            var issueArray = issues.toArray(new Issue[0]);
            listener.onValidationFinished(new ValidationFinishedEvent(issueArray));
        }
    }

    /**
     * Registers a validation finished listener. The validation changed event will
     * be raised after the document was changed and the validation took place. The
     * event object contains a list with all validation issues of the document.
     *
     * @param listener the event listener
     */
    public void addValidationFinishedListener(ValidationFinishedListener listener) {
        this.validationFinishedListeners.add(listener);
    }

    /**
     * Removes a registered validation finished listener.
     *
     * @param listener The registered listener that should be removed.
     */
    public void removeValidationFinishedListener(ValidationFinishedListener listener) {
        this.validationFinishedListeners.remove(listener);
    }
}
