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
package io.sapl.playground;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import io.sapl.attributes.documentation.api.PolicyInformationPointDocumentationProvider;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.functions.LibraryDocumentation;
import lombok.Getter;
import lombok.val;

import java.io.Serializable;
import java.util.Collection;

/*
 * Provides a slide-in drawer for displaying documentation about
 * function libraries and policy information points.
 */
public class DocumentationDrawer implements Serializable {

    private final Dialog drawer;
    /*
     * Gets the floating action button that toggles the drawer.
     *
     * @return the toggle button component
     */
    @Getter
    private final Button                                                toggleButton;
    private final transient PolicyInformationPointDocumentationProvider pipDocumentationProvider;
    private final transient FunctionContext                             functionContext;

    public DocumentationDrawer(PolicyInformationPointDocumentationProvider pipDocumentationProvider,
            FunctionContext functionContext) {
        this.pipDocumentationProvider = pipDocumentationProvider;
        this.functionContext          = functionContext;
        this.drawer                   = createDrawer();
        this.toggleButton             = createToggleButton();
    }

    private Button createToggleButton() {
        val button = new Button(VaadinIcon.BOOK.create());
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        button.setAriaLabel("Documentation");
        button.setTooltipText("Open Documentation (Ctrl+/)");
        button.getStyle().set("position", "fixed").set("bottom", "24px").set("right", "24px").set("z-index", "1000")
                .set("border-radius", "50%").set("width", "56px").set("height", "56px")
                .set("box-shadow", "0 4px 8px rgba(0,0,0,0.3)");

        button.addClickListener(e -> toggleDrawer());
        return button;
    }

    private Dialog createDrawer() {
        val dialog = new Dialog();
        dialog.setWidth("50%");
        dialog.setHeight("100%");
        dialog.setModal(false);
        dialog.setDraggable(false);
        dialog.setResizable(false);

        dialog.getElement().getStyle().set("position", "fixed").set("right", "0").set("top", "0").set("margin", "0");

        val closeButton = new Button(VaadinIcon.CLOSE.create());
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        closeButton.addClickListener(e -> dialog.close());
        closeButton.setTooltipText("Close Documentation (Esc)");

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

        val functionLibraries       = functionLibraries(functionContext.getDocumentation());
        val policyInformationPoints = policyInformationPoints(pipDocumentationProvider.getDocumentation());

        tabSheet.add("Function Libraries", functionLibraries);
        tabSheet.add("Policy Information Points", policyInformationPoints);

        layout.add(tabSheet);
        return layout;
    }

    private Component functionLibraries(Collection<LibraryDocumentation> libraryDocumentations) {
        val sheet = new TabSheet();
        sheet.setSizeFull();
        for (var library : libraryDocumentations) {
            val name     = library.getName();
            val markdown = MarkdownGenerator.generateMarkdownForLibrary(library);
            val content  = MarkdownGenerator.markdownToHtml(markdown);
            val html     = new Html(MarkdownGenerator.wrapInDiv(content));
            sheet.add(name, html);
        }
        return sheet;
    }

    private Component policyInformationPoints(
            Collection<io.sapl.attributes.documentation.api.LibraryDocumentation> pipDocumentations) {
        val sheet = new TabSheet();
        sheet.setSizeFull();
        for (var pip : pipDocumentations) {
            val name     = pip.namespace();
            val markdown = MarkdownGenerator.generateMarkdownForPolicyInformationPoint(pip);
            val content  = MarkdownGenerator.markdownToHtml(markdown);
            val html     = new Html(MarkdownGenerator.wrapInDiv(content));
            sheet.add(name, html);
        }
        return sheet;
    }

    private void toggleDrawer() {
        if (drawer.isOpened()) {
            drawer.close();
        } else {
            drawer.open();
        }
    }
}
