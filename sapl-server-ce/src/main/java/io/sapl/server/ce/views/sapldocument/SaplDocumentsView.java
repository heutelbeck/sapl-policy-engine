/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.views.sapldocument;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.polymertemplate.Id;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.templatemodel.TemplateModel;

import io.sapl.server.ce.model.sapldocument.SaplDocument;
import io.sapl.server.ce.service.sapldocument.SaplDocumentService;
import io.sapl.server.ce.views.MainView;

/**
 * View for listening and managing SAPL documents. A Designer generated
 * component for the list-sapl-documents template.
 */
@Tag("list-sapl-documents")
@Route(value = SaplDocumentsView.ROUTE, layout = MainView.class)
@JsModule("./list-sapl-documents.js")
@PageTitle("Digital Policies")
public class SaplDocumentsView extends PolymerTemplate<SaplDocumentsView.ListSaplDocumentsModel> {
	public static final String ROUTE = "";

	private final SaplDocumentService saplDocumentService;

	@Id(value = "saplDocumentGrid")
	private Grid<SaplDocument> saplDocumentGrid;

	@Id(value = "createButton")
	private Button createButton;

	public SaplDocumentsView(SaplDocumentService saplDocumentService) {
		this.saplDocumentService = saplDocumentService;

		initUI();
	}

	private void initUI() {
		initSaplDocumentGrid();

		createButton.addClickListener(clickEvent -> {
			saplDocumentService.createDefault();

			// reload grid
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
//			componentsForEntry.add(new Button("Archive", VaadinIcon.ARCHIVE.create()));

			return componentsForEntry;
		});

		// set data provider
		CallbackDataProvider<SaplDocument, Void> dataProvider = DataProvider.fromCallbacks(query -> {
			int offset = query.getOffset();
			int limit = query.getLimit();

			return saplDocumentService.getAll().stream().skip(offset).limit(limit);
		}, query -> (int) saplDocumentService.getAmount());
		saplDocumentGrid.setDataProvider(dataProvider);

		saplDocumentGrid.setAllRowsVisible(true);
	}

	/**
	 * This model binds properties between CreateSaplDocument and
	 * create-sapl-document
	 */
	public interface ListSaplDocumentsModel extends TemplateModel {
	}
}
