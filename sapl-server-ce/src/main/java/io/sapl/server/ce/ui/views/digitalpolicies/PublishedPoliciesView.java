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
package io.sapl.server.ce.ui.views.digitalpolicies;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.context.annotation.Conditional;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import io.sapl.api.SaplVersion;
import io.sapl.server.ce.model.sapldocument.PublishedSaplDocument;
import io.sapl.server.ce.model.sapldocument.SaplDocumentService;
import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;
import io.sapl.server.ce.ui.utils.ConfirmUtils;
import io.sapl.server.ce.ui.utils.ErrorNotificationUtils;
import io.sapl.server.ce.ui.views.MainLayout;
import io.sapl.vaadin.SaplEditor;
import io.sapl.vaadin.SaplEditorConfiguration;
import jakarta.annotation.security.RolesAllowed;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RolesAllowed("ADMIN")
@PageTitle("Published Policies")
@Route(value = PublishedPoliciesView.ROUTE, layout = MainLayout.class)
@Conditional(SetupFinishedCondition.class)
public class PublishedPoliciesView extends VerticalLayout {

    private static final long serialVersionUID = SaplVersion.VERISION_UID;

    public static final String ROUTE = "published";

    private transient SaplDocumentService saplDocumentService;

    private final Grid<PublishedSaplDocument> grid                               = new Grid<>();
    private final VerticalLayout              layoutForSelectedPublishedDocument = new VerticalLayout();
    private final TextField                   policyIdTextField                  = new TextField("Policy Identifier");
    private final TextField                   publishedVersionTextField          = new TextField("Published Version");
    private SaplEditor                        saplEditor;

    public PublishedPoliciesView(SaplDocumentService saplDocumentService) {
        this.saplDocumentService = saplDocumentService;

        final var editorConfig = new SaplEditorConfiguration();
        editorConfig.setDarkTheme(true);
        saplEditor = new SaplEditor(editorConfig);
        final var metadataLayout              = new HorizontalLayout(policyIdTextField, publishedVersionTextField);
        final var openEditPageForPolicyButton = new Button("Manage Policy");

        layoutForSelectedPublishedDocument.add(metadataLayout, openEditPageForPolicyButton, saplEditor);
        layoutForSelectedPublishedDocument.setSizeFull();
        final var mainLayout = new SplitLayout(grid, layoutForSelectedPublishedDocument);
        mainLayout.setSizeFull();
        add(mainLayout);

        initGrid();

        layoutForSelectedPublishedDocument.setVisible(false);

        saplEditor.setReadOnly(Boolean.TRUE);
        openEditPageForPolicyButton.addClickListener(e -> {
            PublishedSaplDocument selected = getSelected();

            String uriToNavigateTo = String.format("%s/%s", EditSaplDocumentView.ROUTE, selected.getSaplDocumentId());
            getUI().ifPresent(ui -> ui.navigate(uriToNavigateTo));
        });
    }

    private void initGrid() {
        grid.addColumn(PublishedSaplDocument::getDocumentName).setHeader("Name").setSortable(true);
        grid.getColumns().forEach(col -> col.setAutoWidth(true));
        grid.setMultiSort(false);
        grid.addComponentColumn(publishedDocument -> {
            Button unpublishButton = new Button("Unpublish");
            unpublishButton.addClickListener(clickEvent -> ConfirmUtils.letConfirm("Unpublish Document?", String.format(
                    "Should the document \"%s\" really be unpublished?", publishedDocument.getDocumentName()), () -> {
                        try {
                            saplDocumentService.unpublishPolicy(publishedDocument.getSaplDocumentId());
                        } catch (Exception throwable) {
                            ErrorNotificationUtils.show("The document could not be unpublished.");
                            log.error(String.format("The document with id %s could not be unpublished.",
                                    publishedDocument.getSaplDocumentId()), throwable);
                            return;
                        }
                        grid.getDataProvider().refreshAll();
                    }, () -> {}));

            HorizontalLayout componentsForEntry = new HorizontalLayout();
            componentsForEntry.add(unpublishButton);

            return componentsForEntry;
        });

        CallbackDataProvider<PublishedSaplDocument, Void> dataProvider = DataProvider.fromCallbacks(query -> {
            Stream<PublishedSaplDocument> stream = saplDocumentService.getPublishedSaplDocuments().stream();

            Optional<Comparator<PublishedSaplDocument>> comparator = query.getSortingComparator();
            if (comparator.isPresent()) {
                stream = stream.sorted(comparator.get());
            }

            return stream.skip(query.getOffset()).limit(query.getLimit());
        }, query -> (int) saplDocumentService.getPublishedAmount());
        grid.setSelectionMode(Grid.SelectionMode.SINGLE);
        grid.setAllRowsVisible(true);
        grid.setPageSize(25);
        grid.setItems(dataProvider);
        grid.setMultiSort(false);
        grid.addSelectionListener(selection -> {
            Optional<PublishedSaplDocument> selectedItem = selection.getFirstSelectedItem();
            selectedItem.ifPresentOrElse(selectedPublishedDocument -> {
                layoutForSelectedPublishedDocument.setVisible(true);

                policyIdTextField.setValue(Long.toString(selectedPublishedDocument.getSaplDocumentId()));
                publishedVersionTextField.setValue(Integer.toString(selectedPublishedDocument.getVersion()));
                saplEditor.setDocument(selectedPublishedDocument.getDocument());
            }, () -> layoutForSelectedPublishedDocument.setVisible(false));
        });
    }

    private PublishedSaplDocument getSelected() {
        Optional<PublishedSaplDocument> optionalPersistedPublishedDocument = grid.getSelectedItems().stream()
                .findFirst();
        if (optionalPersistedPublishedDocument.isEmpty()) {
            throw new IllegalStateException("not available if no published document is selected");
        }

        return optionalPersistedPublishedDocument.get();
    }

}
