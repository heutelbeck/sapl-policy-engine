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
package io.sapl.vaadin.graph;

import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.icon.VaadinIcon;

import io.sapl.api.SaplVersion;
import lombok.val;

/**
 * Interactive JSON graph visualization component with maximize capability.
 *
 * <p>
 * Displays JSON data as a hierarchical tree graph using D3.js. Features
 * include:
 * <ul>
 * <li>Interactive pan and zoom controls</li>
 * <li>Maximize to fullscreen dialog</li>
 * <li>Zoom state preservation across maximize/restore</li>
 * <li>Color-coded nodes by data type</li>
 * <li>Horizontal tree layout with orthogonal connections</li>
 * </ul>
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * JsonGraphVisualization graph = new JsonGraphVisualization();
 * graph.setSizeFull();
 * graph.setJsonData("{\"name\": \"example\", \"value\": 42}");
 * add(graph);
 * }</pre>
 *
 * <p>
 * The component uses Vaadin's Dialog for maximize functionality, ensuring
 * proper
 * layout integration without affecting the parent layout's structure.
 */
@Tag("json-graph-visualization")
@JsModule("./json-graph-component.ts")
@NpmPackage(value = "d3", version = "7.9.0")
public class JsonGraphVisualization extends Component implements HasSize, HasStyle {
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private static final String DIALOG_OPEN_ATTRIBUTE      = "dialog-open";
    private static final String HIDE_BUTTON_ATTRIBUTE      = "hide-maximize-button";
    private static final String INITIAL_TRANSFORM_PROPERTY = "initialTransform";

    private Dialog                 maximizeDialog;
    private JsonGraphVisualization maximizedVisualization;
    private String                 currentJsonData = "{}";

    /**
     * Creates a new JSON graph visualization component.
     *
     * <p>
     * The component is initialized with full size and positioned relatively
     * within its parent container. A maximize button is automatically added
     * to the top-right corner.
     */
    public JsonGraphVisualization() {
        setSizeFull();
        getElement().getStyle().set("display", "block").set("position", "relative");
    }

    /**
     * Sets the JSON data to visualize in the graph.
     *
     * <p>
     * The JSON string is parsed and rendered as a hierarchical tree structure.
     * If the maximize dialog is currently open, the data is synchronized to both
     * the main and maximized visualizations.
     *
     * @param jsonData the JSON string to visualize, must be valid JSON
     * @throws IllegalArgumentException if jsonData is null
     */
    public void setJsonData(String jsonData) {
        if (jsonData == null) {
            throw new IllegalArgumentException("JSON data cannot be null");
        }

        this.currentJsonData = jsonData;
        getElement().setProperty("jsonData", jsonData);

        if (maximizedVisualization != null) {
            maximizedVisualization.getElement().setProperty("jsonData", jsonData);
        }
    }

    /**
     * Gets the current JSON data being visualized.
     *
     * @return the JSON string currently displayed, never null
     */
    public String getJsonData() {
        return currentJsonData;
    }

    /**
     * Opens the maximized view in a fullscreen dialog.
     *
     * <p>
     * This method is called from the client-side when the user clicks the
     * maximize button. The current zoom and pan state is preserved and applied
     * to the maximized view. The dialog is modal and non-resizable.
     *
     * <p>
     * This method must be annotated with {@link ClientCallable} to be invoked
     * from JavaScript.
     */
    @ClientCallable
    public void showMaximized() {
        getElement().executeJs("const component = this; return component.getZoomTransform();").then(String.class,
                this::openMaximizedDialog);
    }

    /**
     * Opens the maximize dialog with the given zoom transform.
     *
     * @param transform the serialized D3 zoom transform to apply
     */
    private void openMaximizedDialog(String transform) {
        if (maximizeDialog == null) {
            maximizeDialog = createMaximizeDialog();
            maximizeDialog.addOpenedChangeListener(this::handleDialogOpenedChange);
        }

        maximizedVisualization.getElement().setProperty(INITIAL_TRANSFORM_PROPERTY, transform);
        maximizedVisualization.setJsonData(currentJsonData);
        maximizeDialog.open();
    }

    /**
     * Creates and configures the maximize dialog.
     *
     * @return a configured Dialog instance with embedded visualization
     */
    private Dialog createMaximizeDialog() {
        val dialog = new Dialog();
        dialog.setWidth("100%");
        dialog.setHeight("100%");
        dialog.setDraggable(false);
        dialog.setResizable(false);
        dialog.setModal(true);

        maximizedVisualization = createMaximizedVisualization();
        dialog.add(maximizedVisualization);

        val closeButton = createCloseButton(dialog);
        dialog.getHeader().add(closeButton);

        return dialog;
    }

    /**
     * Creates the visualization instance for the maximize dialog.
     *
     * @return a configured JsonGraphVisualization without maximize button
     */
    private JsonGraphVisualization createMaximizedVisualization() {
        val visualization = new JsonGraphVisualization();
        visualization.setSizeFull();
        visualization.getElement().setAttribute(HIDE_BUTTON_ATTRIBUTE, "true");
        return visualization;
    }

    /**
     * Creates the close button for the dialog header.
     *
     * @param dialog the dialog to close when button is clicked
     * @return a configured Button with icon and styling
     */
    private Button createCloseButton(Dialog dialog) {
        val button = new Button(VaadinIcon.COMPRESS_SQUARE.create());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        button.addClickListener(event -> dialog.close());
        button.setTooltipText("Exit maximize");
        return button;
    }

    /**
     * Handles dialog opened/closed state changes.
     *
     * <p>
     * When opened, hides the maximize button on the main visualization.
     * When closed, restores the button and applies the saved zoom state.
     *
     * @param event the dialog opened change event
     */
    private void handleDialogOpenedChange(Dialog.OpenedChangeEvent event) {
        if (event.isOpened()) {
            getElement().setAttribute(DIALOG_OPEN_ATTRIBUTE, "true");
        } else {
            handleDialogClosed();
        }
    }

    /**
     * Handles cleanup when the dialog is closed.
     *
     * <p>
     * Saves the zoom transform from the maximized view and restores it
     * to the main visualization.
     */
    private void handleDialogClosed() {
        getElement().removeAttribute(DIALOG_OPEN_ATTRIBUTE);

        if (maximizedVisualization != null) {
            maximizedVisualization.getElement()
                    .executeJs("const component = this; return component.getZoomTransform();")
                    .then(String.class, this::restoreZoomTransform);
        }
    }

    /**
     * Restores the zoom transform to the main visualization.
     *
     * @param transform the serialized D3 zoom transform to restore
     */
    private void restoreZoomTransform(String transform) {
        getElement().setProperty(INITIAL_TRANSFORM_PROPERTY, transform);
    }

    /**
     * Registers client-side event listeners on component attach.
     *
     * <p>
     * Sets up a listener for the 'maximize-clicked' event dispatched
     * from the TypeScript component.
     *
     * @param attachEvent the attach event
     */
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);

        getElement().executeJs("const component = this;" + "component.addEventListener('maximize-clicked', () => {"
                + "  component.$server.showMaximized();" + "});");
    }
}
