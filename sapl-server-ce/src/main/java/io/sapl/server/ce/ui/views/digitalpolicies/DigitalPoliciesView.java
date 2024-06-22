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
package io.sapl.server.ce.ui.views.digitalpolicies;

import org.springframework.context.annotation.Conditional;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import io.sapl.api.SaplVersion;
import io.sapl.server.ce.model.sapldocument.SaplDocument;
import io.sapl.server.ce.model.sapldocument.SaplDocumentService;
import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;
import io.sapl.server.ce.ui.views.MainLayout;
import jakarta.annotation.security.RolesAllowed;

/**
 * View for listening and managing SAPL documents. A Designer generated
 * component for the list-sapl-documents template.
 */
@RolesAllowed("ADMIN")
@PageTitle("Digital Policies")
@Route(value = DigitalPoliciesView.ROUTE, layout = MainLayout.class)
@Conditional(SetupFinishedCondition.class)
public class DigitalPoliciesView extends VerticalLayout {

    private static final long serialVersionUID = SaplVersion.VERISION_UID;

    public static final String ROUTE = "";

    private transient SaplDocumentService saplDocumentService;

    private final Grid<SaplDocument> saplDocumentGrid = new Grid<>();

    public DigitalPoliciesView(SaplDocumentService saplDocumentService) {
        this.saplDocumentService = saplDocumentService;

        var createButton = new Button("Create");
        add(createButton, saplDocumentGrid);

        initSaplDocumentGrid();

        createButton.addClickListener(clickEvent -> {
            saplDocumentService.createDefault();
            saplDocumentGrid.getDataProvider().refreshAll();
        });
    }

    private void initSaplDocumentGrid() {
        // add columns
        saplDocumentGrid.addColumn(SaplDocument::getName).setHeader("Name");
        saplDocumentGrid.addColumn(SaplDocument::getCurrentVersionNumber).setHeader("Version");
        saplDocumentGrid.addColumn(SaplDocument::getPublishedVersionNumberAsString).setHeader("Published Version");
        saplDocumentGrid.addColumn(SaplDocument::getLastModified).setHeader("Last Modified");
        saplDocumentGrid.addColumn(SaplDocument::getTypeAsString).setHeader("Type");
        saplDocumentGrid.getColumns().forEach(col -> col.setAutoWidth(true));
        saplDocumentGrid.addComponentColumn(saplDocument -> {
            Button editButton = new Button("Edit", VaadinIcon.EDIT.create());
            editButton.addClickListener(clickEvent -> {
                String uriToNavigateTo = String.format("%s/%d", EditSaplDocumentView.ROUTE, saplDocument.getId());
                editButton.getUI().ifPresent(ui -> ui.navigate(uriToNavigateTo));
            });
            editButton.setThemeName("primary");

            HorizontalLayout componentsForEntry = new HorizontalLayout();
            componentsForEntry.add(editButton);
            return componentsForEntry;
        });

        // set data provider
        CallbackDataProvider<SaplDocument, Void> dataProvider = DataProvider.fromCallbacks(query -> {
            int offset = query.getOffset();
            int limit  = query.getLimit();

            return saplDocumentService.getAll().stream().skip(offset).limit(limit);
        }, query -> (int) saplDocumentService.getAmount());
        saplDocumentGrid.setItems(dataProvider);

        saplDocumentGrid.setAllRowsVisible(true);
    }

}
