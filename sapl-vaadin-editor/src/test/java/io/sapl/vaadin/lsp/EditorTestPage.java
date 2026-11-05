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

import java.util.ArrayList;
import java.util.List;

import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.flow.component.Component;

import io.sapl.vaadin.DocumentChangedEvent;
import io.sapl.vaadin.DocumentChangedListener;
import io.sapl.vaadin.ValidationFinishedEvent;
import io.sapl.vaadin.ValidationFinishedListener;

import lombok.val;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

/** Java component access. Client callbacks bypass CodeMirror, transport and JavaScript execution. */
final class EditorTestPage {

    private final BrowserlessUIContext          window;
    private final Component                     editor;
    private final List<DocumentChangedEvent>    documentChanges    = new ArrayList<>();
    private final List<ValidationFinishedEvent> validationResults  = new ArrayList<>();
    private final DocumentChangedListener       documentObserver   = documentChanges::add;
    private final ValidationFinishedListener    validationObserver = validationResults::add;

    EditorTestPage(BrowserlessUIContext window, EditorKind kind) {
        this.window = window;
        editor      = window.find(kind.componentType()).single();
        switch (editor) {
        case SaplEditorLsp sapl -> {
            sapl.addDocumentChangedListener(documentObserver);
            sapl.addValidationFinishedListener(validationObserver);
        }
        case JsonEditor json    -> json.addDocumentChangedListener(documentObserver);
        default                 -> throw new IllegalArgumentException("Unsupported editor");
        }
    }

    void loadDocument(String document) {
        window.activate();
        switch (editor) {
        case SaplEditorLsp sapl -> sapl.setDocument(document);
        case JsonEditor json    -> json.setDocument(document);
        default                 -> throw new IllegalArgumentException("Unsupported editor");
        }
    }

    void editDocument(String document) {
        window.activate();
        switch (editor) {
        case SaplEditorLsp sapl -> sapl.onDocumentChanged(document);
        case JsonEditor json    -> json.onDocumentChanged(document);
        default                 -> throw new IllegalArgumentException("Unsupported editor");
        }
    }

    String document() {
        window.activate();
        return switch (editor) {
        case SaplEditorLsp sapl -> sapl.getDocument();
        case JsonEditor json    -> json.getDocument();
        default                 -> throw new IllegalArgumentException("Unsupported editor");
        };
    }

    void changePresentation(boolean wrapping, boolean followColorScheme, String fontSize) {
        window.activate();
        switch (editor) {
        case SaplEditorLsp sapl -> {
            sapl.setLineWrapping(wrapping);
            sapl.setFollowColorScheme(followColorScheme);
            sapl.setFontSize(fontSize);
        }
        case JsonEditor json    -> {
            json.setLineWrapping(wrapping);
            json.setFollowColorScheme(followColorScheme);
            json.setFontSize(fontSize);
        }
        default                 -> throw new IllegalArgumentException("Unsupported editor");
        }
    }

    Presentation presentation() {
        window.activate();
        val fontSize = editor.getElement().getStyle().get("font-size");
        return switch (editor) {
        case SaplEditorLsp sapl -> new Presentation(sapl.isReadOnly(), sapl.isDarkTheme(), sapl.isLineWrapping(),
                sapl.isFollowColorScheme(), fontSize);
        case JsonEditor json    -> new Presentation(json.isReadOnly(), json.isDarkTheme(), json.isLineWrapping(),
                json.isFollowColorScheme(), fontSize);
        default                 -> throw new IllegalArgumentException("Unsupported editor");
        };
    }

    void stopObservingDocument() {
        window.activate();
        switch (editor) {
        case SaplEditorLsp sapl -> sapl.removeDocumentChangedListener(documentObserver);
        case JsonEditor json    -> json.removeDocumentChangedListener(documentObserver);
        default                 -> throw new IllegalArgumentException("Unsupported editor");
        }
    }

    void reportValidation(String payload) {
        window.activate();
        val issues = payload == null ? null : (ArrayNode) JsonMapper.builder().build().readTree(payload);
        ((SaplEditorLsp) editor).onValidation(issues);
    }

    void stopObservingValidation() {
        window.activate();
        ((SaplEditorLsp) editor).removeValidationFinishedListener(validationObserver);
    }

    List<DocumentChangedEvent> documentChanges() {
        return List.copyOf(documentChanges);
    }

    List<ValidationFinishedEvent> validationResults() {
        return List.copyOf(validationResults);
    }

    String language() {
        window.activate();
        return editor.getElement().getProperty("language");
    }

    String languageServerUrl() {
        window.activate();
        return editor.getElement().getProperty("wsUrl");
    }

    record Presentation(
            boolean readOnly,
            boolean darkTheme,
            boolean wrapping,
            boolean followColorScheme,
            String fontSize) {}

    enum EditorKind {
        SAPL,
        SAPLTEST,
        JSON;

        Class<? extends Component> componentType() {
            return switch (this) {
            case SAPL     -> SaplEditorLsp.class;
            case SAPLTEST -> SaplTestEditorLsp.class;
            case JSON     -> JsonEditor.class;
            };
        }

        Component create(boolean configured) {
            if (this == JSON) {
                val options = new JsonEditorConfiguration();
                options.setReadOnly(configured);
                options.setDarkTheme(configured);
                options.setLineWrapping(configured);
                options.setFollowColorScheme(configured);
                options.setFontSize(configured ? "13px" : null);
                return new JsonEditor(options);
            }
            val options = new SaplEditorLspConfiguration();
            options.setReadOnly(configured);
            options.setDarkTheme(configured);
            options.setLineWrapping(configured);
            options.setFollowColorScheme(configured);
            options.setFontSize(configured ? "13px" : null);
            if (configured) {
                options.setWsUrl("/sapl-lsp");
            }
            return this == SAPL ? new SaplEditorLsp(options) : new SaplTestEditorLsp(options);
        }
    }
}
