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

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.dom.Element;

/**
 * A JSON Editor component with syntax highlighting and linting.
 */
@Tag("json-editor")
@JsModule("./json-editor.js")
@NpmPackage(value = "jsonlint-webpack", version = "1.1.0")
@NpmPackage(value = "jquery", version = "3.7.1")
@NpmPackage(value = "codemirror", version = "5.65.16")
public class JsonEditor extends BaseEditor {

    private static final long serialVersionUID = 5820153273838122172L;

    /**
     * Creates the editor component.
     *
     * @param config the editor configuration-
     */
    public JsonEditor(JsonEditorConfiguration config) {
        Element element = getElement();
        applyBaseConfiguration(element, config);
    }

    /**
     * Refreshes the editor.
     */
    public void refresh() {
        Element element = getElement();
        element.callJsFunction("onRefreshEditor");
    }

    /**
     * Appends text to the end of the editor contents.
     *
     * @param text some text
     */
    public void appendText(String text) {
        Element element = getElement();
        element.callJsFunction("appendText", text);
    }

    /**
     * Toggles linting on or off.
     *
     * @param isLint indicate if linting is to be activates.
     */
    public void setLint(Boolean isLint) {
        Element element = getElement();
        element.setProperty("isLint", isLint);
    }

}
