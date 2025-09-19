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

import com.vaadin.flow.component.*;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import elemental.json.JsonArray;
import io.sapl.api.SaplVersion;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SAPL editor with Xtext integration and optional two-pane merge view.
 */
@Tag("sapl-editor")
@JsModule("./sapl-editor.js")
@NpmPackage(value = "jquery", version = "3.7.1")
@NpmPackage(value = "codemirror", version = "5.65.16")
@NpmPackage(value = "diff-match-patch", version = "1.0.5")
public class SaplEditor extends BaseEditor implements HasSize {

    private static final long serialVersionUID = SaplVersion.VERISION_UID;

    private final List<ValidationFinishedListener> validationFinishedListeners = new ArrayList<>();

    /**
     * Creates the editor.
     *
     * @param config editor configuration
     */
    public SaplEditor(SaplEditorConfiguration config) {
        final var element = getElement();
        applyBaseConfiguration(element, config);
    }

    @ClientCallable
    protected void onValidation(JsonArray jsonIssues) {
        final ArrayList<Object> issues = new ArrayList<>();
        for (int i = 0; jsonIssues != null && i < jsonIssues.length(); i++) {
            final var jsonIssue = jsonIssues.getObject(i);
            final var issue     = new Issue(jsonIssue);
            issues.add(issue);
        }
        final var issueArray = issues.toArray(new Issue[0]);
        for (ValidationFinishedListener listener : validationFinishedListeners) {
            listener.onValidationFinished(new ValidationFinishedEvent(issueArray));
        }
    }

    public void addValidationFinishedListener(ValidationFinishedListener listener) {
        this.validationFinishedListeners.add(listener);
    }

    public void removeValidationFinishedListener(ValidationFinishedListener listener) {
        this.validationFinishedListeners.remove(listener);
    }

    /**
     * Sets the configuration id for code completion/validation context.
     *
     * @param configurationId configuration id
     */
    public void setConfigurationId(String configurationId) {
        getElement().setProperty("configurationId", configurationId);
    }

    // ---------- Merge API parity with JsonEditor ----------

    /**
     * Enable or disable the two-pane merge view. When disabled, Xtext services are
     * active.
     *
     * @param enabled true to enable merge mode
     */
    public void setMergeModeEnabled(boolean enabled) {
        getElement().callJsFunction("setMergeModeEnabled", enabled);
    }

    /**
     * Provide the right-hand document for the merge view.
     *
     * @param content right-side text
     */
    public void setMergeRightContent(String content) {
        getElement().callJsFunction("setMergeRightContent", content);
    }

    /**
     * Toggle visual markers for changed blocks in both panes.
     *
     * @param enabled true to enable markers
     */
    public void setChangeMarkersEnabled(boolean enabled) {
        getElement().callJsFunction("enableChangeMarkers", enabled);
    }

    /**
     * Set a merge option. Supported keys:
     * "revertButtons" (Boolean), "showDifferences" (Boolean),
     * "connect" (null or "align"), "collapseIdentical" (Boolean),
     * "allowEditingOriginals" (Boolean), "ignoreWhitespace" (Boolean)
     *
     * @param option key
     * @param value value
     */
    public void setMergeOption(String option, Serializable value) {
        getElement().callJsFunction("setMergeOption", option, value);
    }

    /**
     * Jump to next diff chunk.
     */
    public void goToNextChange() {
        getElement().callJsFunction("nextChange");
    }

    /**
     * Jump to previous diff chunk.
     */
    public void goToPreviousChange() {
        getElement().callJsFunction("prevChange");
    }

    // ----- Optional: expose chunk ranges to server (parity with JSON) -----

    @DomEvent("sapl-merge-chunks")
    public static class DiffChunksChangedEvent extends ComponentEvent<SaplEditor> {

        private final ArrayList<Chunk> chunks;

        public DiffChunksChangedEvent(SaplEditor source,
                boolean fromClient,
                @EventData("event.detail.chunks") elemental.json.JsonArray chunksJson) {
            super(source, fromClient);
            final ArrayList<Chunk> list = new ArrayList<>();
            if (chunksJson != null) {
                for (int i = 0; i < chunksJson.length(); i++) {
                    final var obj  = chunksJson.getObject(i);
                    final int from = obj.hasKey("fromLine") ? (int) obj.getNumber("fromLine") : 0;
                    final int to   = obj.hasKey("toLine") ? (int) obj.getNumber("toLine") : from;
                    list.add(new Chunk(from, to));
                }
            }
            this.chunks = list;
        }

        public List<Chunk> getChunks() {
            return Collections.unmodifiableList(chunks);
        }
    }

    public record Chunk(int fromLine, int toLine) implements Serializable {}
}
