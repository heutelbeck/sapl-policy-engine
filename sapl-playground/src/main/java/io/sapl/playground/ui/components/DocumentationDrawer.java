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
package io.sapl.playground.ui.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

import io.sapl.api.SaplVersion;
import io.sapl.api.documentation.DocumentationBundle;
import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.playground.domain.MarkdownGenerator;
import lombok.Getter;
import lombok.val;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Slide-in drawer component for displaying SAPL documentation.
 * Provides a floating action button that opens a side panel containing
 * documentation for function libraries and policy information points.
 * <p>
 * The drawer is displayed as a non-modal dialog positioned on the right
 * side of the screen. Documentation is organized in tabs, with markdown
 * content converted to HTML for display.
 * <p>
 * Scoped to the UI session to maintain state during the user's interaction
 * with the playground.
 */
@UIScope
@SpringComponent
public class DocumentationDrawer implements Serializable {
    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private static final String CSS_BORDER_RADIUS = "border-radius";
    private static final String CSS_BOTTOM        = "bottom";
    private static final String CSS_BOX_SHADOW    = "box-shadow";
    private static final String CSS_HEIGHT        = "height";
    private static final String CSS_MARGIN        = "margin";
    private static final String CSS_POSITION      = "position";
    private static final String CSS_RIGHT         = "right";
    private static final String CSS_TOP           = "top";
    private static final String CSS_WIDTH         = "width";
    private static final String CSS_Z_INDEX       = "z-index";

    private static final String CSS_VALUE_FIXED         = "fixed";
    private static final String CSS_VALUE_ZERO          = "0";
    private static final String CSS_VALUE_SIZE_24PX     = "24px";
    private static final String CSS_VALUE_SIZE_50PCT    = "50%";
    private static final String CSS_VALUE_SIZE_56PX     = "56px";
    private static final String CSS_VALUE_SIZE_100PCT   = "100%";
    private static final String CSS_VALUE_BUTTON_SHADOW = "0 4px 8px rgba(0,0,0,0.3)";
    private static final String CSS_VALUE_CIRCLE        = "50%";
    private static final String CSS_VALUE_Z_INDEX_1000  = "1000";

    private static final String LABEL_DOCUMENTATION             = "Documentation";
    private static final String LABEL_FUNCTION_LIBRARIES        = "Function Libraries";
    private static final String LABEL_POLICY_INFORMATION_POINTS = "Policy Information Points";

    private static final String TOOLTIP_CLOSE_DOCUMENTATION = "Close Documentation (Esc)";
    private static final String TOOLTIP_OPEN_DOCUMENTATION  = "Open Documentation (Ctrl+/)";

    private final Dialog drawer;

    @Getter
    private final Button toggleButton;

    private final transient DocumentationBundle documentationBundle;

    /**
     * Creates a new documentation drawer component.
     * Initializes the drawer dialog and floating action button with
     * documentation from the provided documentation bundle.
     *
     * @param documentationBundle bundle containing all library documentation
     */
    public DocumentationDrawer(DocumentationBundle documentationBundle) {
        this.documentationBundle = documentationBundle;
        this.drawer              = createDrawer();
        this.toggleButton        = createToggleButton();
    }

    private Button createToggleButton() {
        val button = new Button(VaadinIcon.BOOK.create());
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        button.setAriaLabel(LABEL_DOCUMENTATION);
        button.setTooltipText(TOOLTIP_OPEN_DOCUMENTATION);
        button.getStyle().set(CSS_POSITION, CSS_VALUE_FIXED).set(CSS_BOTTOM, CSS_VALUE_SIZE_24PX)
                .set(CSS_RIGHT, CSS_VALUE_SIZE_24PX).set(CSS_Z_INDEX, CSS_VALUE_Z_INDEX_1000)
                .set(CSS_BORDER_RADIUS, CSS_VALUE_CIRCLE).set(CSS_WIDTH, CSS_VALUE_SIZE_56PX)
                .set(CSS_HEIGHT, CSS_VALUE_SIZE_56PX).set(CSS_BOX_SHADOW, CSS_VALUE_BUTTON_SHADOW);

        button.addClickListener(event -> toggleDrawer());
        return button;
    }

    private Dialog createDrawer() {
        val dialog = new Dialog();
        dialog.setWidth(CSS_VALUE_SIZE_50PCT);
        dialog.setHeight(CSS_VALUE_SIZE_100PCT);
        dialog.setModal(false);
        dialog.setDraggable(false);
        dialog.setResizable(false);

        dialog.getElement().getStyle().set(CSS_POSITION, CSS_VALUE_FIXED).set(CSS_RIGHT, CSS_VALUE_ZERO)
                .set(CSS_TOP, CSS_VALUE_ZERO).set(CSS_MARGIN, CSS_VALUE_ZERO);

        val closeButton = new Button(VaadinIcon.CLOSE.create());
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        closeButton.addClickListener(event -> dialog.close());
        closeButton.setTooltipText(TOOLTIP_CLOSE_DOCUMENTATION);

        dialog.getHeader().add(closeButton);

        val content = createDocumentationContent();
        dialog.add(content);

        return dialog;
    }

    private Component createDocumentationContent() {
        val layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setSpacing(false);

        val tabSheet = new TabSheet();
        tabSheet.setSizeFull();

        val functionLibrariesComponent       = createDocumentationTabSheet(documentationBundle.functionLibraries());
        val policyInformationPointsComponent = createDocumentationTabSheet(
                documentationBundle.policyInformationPoints());

        tabSheet.add(LABEL_FUNCTION_LIBRARIES, functionLibrariesComponent);
        tabSheet.add(LABEL_POLICY_INFORMATION_POINTS, policyInformationPointsComponent);

        layout.add(tabSheet);
        return layout;
    }

    private Component createDocumentationTabSheet(List<LibraryDocumentation> documentations) {
        val tabSheet = new TabSheet();
        tabSheet.setSizeFull();

        for (var documentation : documentations) {
            val name          = documentation.name();
            val markdown      = MarkdownGenerator.generateMarkdownForLibrary(documentation);
            val htmlContent   = MarkdownGenerator.markdownToHtml(markdown);
            val htmlComponent = new Html(MarkdownGenerator.wrapInDiv(htmlContent));
            tabSheet.add(name, htmlComponent);
        }

        return tabSheet;
    }

    private void toggleDrawer() {
        if (drawer.isOpened()) {
            drawer.close();
        } else {
            drawer.open();
        }
    }
}
