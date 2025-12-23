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
package io.sapl.server.ce.ui.views.librariesdocumentation;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.sapl.api.SaplVersion;
import io.sapl.api.documentation.DocumentationBundle;
import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;
import io.sapl.server.ce.ui.views.MainLayout;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.context.annotation.Conditional;

import java.io.Serial;
import java.util.List;

/**
 * View for displaying documentation of available function libraries and
 * policy information points.
 */
@RolesAllowed("ADMIN")
@PageTitle("Libraries Documentation")
@Route(value = LibrariesDocumentationView.ROUTE, layout = MainLayout.class)
@Conditional(SetupFinishedCondition.class)
public class LibrariesDocumentationView extends VerticalLayout {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    public static final String ROUTE = "libraries";

    public LibrariesDocumentationView(DocumentationBundle documentationBundle) {
        setSizeFull();
        var libsAndPipsTabSheet = new TabSheet();
        libsAndPipsTabSheet.add("Function Libraries", createLibraryTabs(documentationBundle.functionLibraries()));
        libsAndPipsTabSheet.add("Policy Information Points",
                createLibraryTabs(documentationBundle.policyInformationPoints()));
        add(libsAndPipsTabSheet);
    }

    private Component createLibraryTabs(List<LibraryDocumentation> libraries) {
        var sheet = new TabSheet();
        for (var library : libraries) {
            var name     = library.name();
            var markdown = MarkdownGenerator.generateMarkdownForLibrary(library);
            var content  = MarkdownGenerator.markdownToHtml(markdown);
            var html     = new Html(MarkdownGenerator.wrapInDiv(content));
            sheet.add(name, html);
        }
        return sheet;
    }
}
