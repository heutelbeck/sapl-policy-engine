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
package io.sapl.server.ce.ui.views.librariesdocumentation;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.sapl.api.SaplVersion;
import io.sapl.interpreter.functions.LibraryDocumentation;
import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;
import io.sapl.server.ce.ui.views.MainLayout;
import io.sapl.spring.pdp.embedded.FunctionLibrariesDocumentation;
import io.sapl.spring.pdp.embedded.PolicyInformationPointsDocumentation;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.context.annotation.Conditional;

import java.util.Collection;

@RolesAllowed("ADMIN")
@PageTitle("Libraries Documentation")
@Route(value = LibrariesDocumentationView.ROUTE, layout = MainLayout.class)
@Conditional(SetupFinishedCondition.class)
public class LibrariesDocumentationView extends VerticalLayout {

    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    public static final String ROUTE = "libraries";

    public LibrariesDocumentationView(FunctionLibrariesDocumentation functionLibrariesDocumentation,
            PolicyInformationPointsDocumentation policyInformationPointsDocumentation) {
        setSizeFull();
        final var libsAndPipsTabSheet     = new TabSheet();
        final var functionLibraries       = functionLibraries(functionLibrariesDocumentation.documentation());
        final var policyInformationPoints = policyInformationPoints(
                policyInformationPointsDocumentation.documentation());
        libsAndPipsTabSheet.add("Function Libraries", functionLibraries);
        libsAndPipsTabSheet.add("Policy Information Points", policyInformationPoints);
        add(libsAndPipsTabSheet);
    }

    private Component policyInformationPoints(
            Collection<io.sapl.attributes.documentation.api.LibraryDocumentation> pipDocumentations) {
        final var sheet = new TabSheet();
        for (var pip : pipDocumentations) {
            final var name     = pip.namespace();
            final var markdown = MarkdownGenerator.generateMarkdownForPolicyInformationPoint(pip);
            final var content  = MarkdownGenerator.markdownToHtml(markdown);
            final var html     = new Html(MarkdownGenerator.wrapInDiv(content));
            sheet.add(name, html);
        }
        return sheet;
    }

    private Component functionLibraries(Collection<LibraryDocumentation> libaryDocumentations) {
        final var sheet = new TabSheet();
        for (var library : libaryDocumentations) {
            final var name     = library.getName();
            final var markdown = MarkdownGenerator.generateMarkdownForLibrary(library);
            final var content  = MarkdownGenerator.markdownToHtml(markdown);
            final var html     = new Html(MarkdownGenerator.wrapInDiv(content));
            sheet.add(name, html);
        }
        return sheet;
    }
}
