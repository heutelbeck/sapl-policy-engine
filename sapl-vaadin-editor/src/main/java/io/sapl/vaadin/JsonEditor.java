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

import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.shared.Registration;
import io.sapl.api.SaplVersion;

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
@CssImport("codemirror/addon/merge/merge.css")
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
     * Enables or disables merge mode. The right-hand side shows the content
     * previously provided
     * via {@link #setMergeRightContent(String)} (empty if not set).
     */
    public void setMergeModeEnabled(boolean enabled) {
        // Set a property so the Lit element sees it before firstUpdated()
        getElement().setProperty("mergeEnabled", enabled);
        // If already attached, switch immediately
        getElement().callJsFunction("setMergeModeEnabled", enabled);
    }

    /**
     * Enables merge mode. The right-hand side shows the content previously provided
     * via {@link #setMergeRightContent(String)} (empty if not set).
     */
    public void enableMerge() {
        setMergeModeEnabled(false);
    }

    /**
     * Disables merge mode and returns to a single-editor view.
     */
    public void disableMerge() {
        setMergeModeEnabled(true);
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
     * Registers a listener that is notified when merge mode is toggled on or off.
     *
     * @param listener listener instance
     * @return registration handle
     */
    public Registration addMergeModeToggledListener(ComponentEventListener<MergeModeToggledEvent> listener) {
        return addListener(MergeModeToggledEvent.class, listener);
    }

    /**
     * Registers a listener that is notified when a changed chunk is copied or
     * reverted.
     *
     * @param listener listener instance
     * @return registration handle
     */
    public Registration addMergeChunkRevertedListener(ComponentEventListener<MergeChunkRevertedEvent> listener) {
        return addListener(MergeChunkRevertedEvent.class, listener);
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

        public boolean isActive() {
            return active;
        }
    }

    /**
     * Event fired when a changed chunk is reverted or copied.
     */
    @DomEvent("sapl-merge-chunk-reverted")
    public static class MergeChunkRevertedEvent extends ComponentEvent<JsonEditor> {
        private final int    fromLine;
        private final int    toLine;
        private final String direction;

        public MergeChunkRevertedEvent(JsonEditor source,
                boolean fromClient,
                @EventData("event.detail.fromLine") int fromLine,
                @EventData("event.detail.toLine") int toLine,
                @EventData("event.detail.direction") String direction) {
            super(source, fromClient);
            this.fromLine  = fromLine;
            this.toLine    = toLine;
            this.direction = direction;
        }

        public int getFromLine() {
            return fromLine;
        }

        public int getToLine() {
            return toLine;
        }

        public String getDirection() {
            return direction;
        }
    }

    /**
     * Legacy combined API retained for compatibility.
     *
     * @param active whether merge mode is enabled
     * @param mergeContent content for the right-hand side
     */
    @Deprecated
    public void setMergeMode(boolean active, String mergeContent) {
        setMergeRightContent(mergeContent);
        if (active) {
            enableMerge();
        } else {
            disableMerge();
        }
    }

    /**
     * Legacy method retained for compatibility.
     *
     * @param mergeContent right-hand side content
     */
    @Deprecated
    public void updateMergeRightContent(String mergeContent) {
        setMergeRightContent(mergeContent);
    }
}
