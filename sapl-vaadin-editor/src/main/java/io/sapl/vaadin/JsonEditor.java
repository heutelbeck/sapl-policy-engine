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
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.shared.Registration;
import io.sapl.api.SaplVersion;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A JSON Editor component with syntax highlighting and linting.
 *
 * This version supports an optional merge mode (two-way view). The primary
 * editor
 * on the left remains editable and uses the same validation logic. The right
 * side
 * shows the comparison content when merge mode is enabled.
 */
@Tag("json-editor")
@JsModule("./json-editor.js")
@NpmPackage(value = "jsonlint-webpack", version = "1.1.0")
@NpmPackage(value = "jquery", version = "3.7.1")
@NpmPackage(value = "codemirror", version = "5.65.16")
@NpmPackage(value = "diff-match-patch", version = "1.0.5")
public class JsonEditor extends BaseEditor {

    private static final long serialVersionUID = SaplVersion.VERISION_UID;

    /**
     * Creates the editor component.
     *
     * @param config the editor configuration
     */
    public JsonEditor(JsonEditorConfiguration config) {
        Element element = getElement();
        applyBaseConfiguration(element, config);
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
     * @param text content to append
     */
    public void appendText(String text) {
        getElement().callJsFunction("appendText", text);
    }

    /**
     * Toggles linting on or off.
     *
     * @param isLint indicates whether linting is enabled
     */
    public void setLint(Boolean isLint) {
        getElement().setProperty("isLint", isLint);
    }

    /**
     * Enables or disables merge mode. When called before the client side is
     * attached,
     * the flag is stored and respected on first render.
     *
     * @param enabled whether merge mode should be enabled
     */
    public void setMergeModeEnabled(boolean enabled) {
        getElement().setProperty("mergeEnabled", enabled);
        getElement().callJsFunction("setMergeModeEnabled", enabled);
    }

    /**
     * Enables merge mode.
     */
    public void enableMerge() {
        setMergeModeEnabled(true);
    }

    /**
     * Disables merge mode.
     */
    public void disableMerge() {
        setMergeModeEnabled(false);
    }

    /**
     * Sets the content for the right-hand side in merge mode. If merge mode is
     * active,
     * the content is applied immediately; otherwise it is stored and used upon
     * enabling.
     *
     * @param mergeContent right-hand side content
     */
    public void setMergeRightContent(String mergeContent) {
        getElement().callJsFunction("setMergeRightContent", mergeContent == null ? "" : mergeContent);
    }

    /**
     * Shows or hides the per-chunk revert buttons provided by the merge view.
     *
     * @param visible whether per-chunk revert buttons are visible
     */
    public void setRevertButtonsVisible(boolean visible) {
        getElement().callJsFunction("setMergeOption", "revertButtons", visible);
    }

    /**
     * Enables or disables a custom "sync both sides" control. This is a frontend
     * concern.
     *
     * @param enabled whether the custom sync control is enabled
     */
    public void setSyncBothSidesEnabled(boolean enabled) {
        getElement().setProperty("saplSyncBothEnabled", enabled);
    }

    /**
     * Enables or disables copying from right to left using the per-chunk control.
     * Disabling only one direction requires frontend enforcement.
     *
     * @param enabled whether copying from right to left is enabled
     */
    public void setCopyRightToLeftEnabled(boolean enabled) {
        getElement().setProperty("saplCopyRightToLeftEnabled", enabled);
    }

    /**
     * Enables or disables additional change markers (gutter and line highlights).
     *
     * @param enabled whether change markers are enabled
     */
    public void setChangeMarkersEnabled(boolean enabled) {
        getElement().callJsFunction("enableChangeMarkers", enabled);
    }

    /**
     * Navigates to the next change.
     */
    public void nextChange() {
        getElement().callJsFunction("nextChange");
    }

    /**
     * Navigates to the previous change.
     */
    public void prevChange() {
        getElement().callJsFunction("prevChange");
    }

    /**
     * Navigates to the first change.
     */
    public void firstChange() {
        getElement().callJsFunction("firstChange");
    }

    /**
     * Navigates to the last change.
     */
    public void lastChange() {
        getElement().callJsFunction("lastChange");
    }

    /**
     * Scrolls to the change at the given index in the current diff chunk list.
     *
     * @param index zero-based change index
     */
    public void scrollToChange(int index) {
        getElement().callJsFunction("scrollToChange", index);
    }

    /**
     * Allows or disallows editing the original (right) pane.
     *
     * @param enabled true to allow editing originals
     */
    public void setAllowEditingOriginals(boolean enabled) {
        getElement().callJsFunction("setMergeOption", "allowEditingOriginals", enabled);
    }

    /**
     * Sets the number of context lines to keep when collapsing identical sections.
     * Use a value less than or equal to zero to disable collapsing.
     *
     * @param contextLines number of context lines, or 0/negative to disable
     */
    public void setCollapseContext(int contextLines) {
        if (contextLines <= 0) {
            getElement().callJsFunction("setMergeOption", "collapseIdentical", false);
        } else {
            getElement().callJsFunction("setMergeOption", "collapseIdentical", contextLines);
        }
    }

    /**
     * Enables or disables difference highlighting.
     *
     * @param enabled whether differences are highlighted
     */
    public void setShowDifferences(boolean enabled) {
        getElement().callJsFunction("setMergeOption", "showDifferences", enabled);
    }

    /**
     * Enables or disables aligned connectors between panes.
     *
     * @param align true to use aligned connectors
     */
    public void setConnectAlign(boolean align) {
        getElement().callJsFunction("setMergeOption", "connect", align ? "align" : null);
    }

    /**
     * Registers a listener that is notified when merge mode is toggled on or off.
     *
     * @param listener listener instance
     * @return registration handle
     */
    public Registration addMergeModeToggledListener(ComponentEventListener<MergeModeToggledEvent> listener) {
        return addListener(MergeModeToggledEvent.class, listener);
    }

    /**
     * Registers a listener that is notified when the set of diff chunks changes.
     *
     * @param listener listener instance
     * @return registration handle
     */
    public Registration addDiffChunksChangedListener(ComponentEventListener<DiffChunksChangedEvent> listener) {
        return addListener(DiffChunksChangedEvent.class, listener);
    }

    /**
     * Event fired when merge mode is toggled.
     */
    @DomEvent("sapl-merge-mode-toggled")
    public static class MergeModeToggledEvent extends ComponentEvent<JsonEditor> {
        private final boolean active;

        public MergeModeToggledEvent(JsonEditor source,
                boolean fromClient,
                @EventData("event.detail.active") boolean active) {
            super(source, fromClient);
            this.active = active;
        }

        /**
         * Indicates whether merge mode is active.
         *
         * @return true if active, false otherwise
         */
        public boolean isActive() {
            return active;
        }
    }

    /**
     * Event fired when the diff chunks are recalculated.
     */
    /**
     * Event fired when the diff chunks are recalculated.
     */
    @DomEvent("sapl-merge-chunks")
    public static class DiffChunksChangedEvent extends ComponentEvent<JsonEditor> {

        private final ArrayList<Chunk> chunks;

        public DiffChunksChangedEvent(JsonEditor source,
                boolean fromClient,
                @com.vaadin.flow.component.EventData("event.detail.chunks") elemental.json.JsonArray chunksJson) {
            super(source, fromClient);
            final ArrayList<Chunk> list = new ArrayList<>();
            if (chunksJson != null) {
                for (int i = 0; i < chunksJson.length(); i++) {
                    final elemental.json.JsonObject obj  = chunksJson.getObject(i);
                    final int                       from = obj.hasKey("fromLine") ? (int) obj.getNumber("fromLine") : 0;
                    final int                       to   = obj.hasKey("toLine") ? (int) obj.getNumber("toLine") : from;
                    list.add(new Chunk(from, to));
                }
            }
            this.chunks = list;
        }

        public List<Chunk> getChunks() {
            return Collections.unmodifiableList(chunks);
        }
    }

    /**
     * Data transfer object for a diff chunk.
     */
    public record Chunk(int fromLine, int toLine) implements Serializable {}

}
