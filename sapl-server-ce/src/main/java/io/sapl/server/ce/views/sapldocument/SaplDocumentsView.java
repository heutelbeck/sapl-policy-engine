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
import io.sapl.server.ce.views.AppNavLayout;

/**
 * View for listening and managing SAPL documents. A Designer generated
 * component for the list-sapl-documents template.
 */
@Tag("list-sapl-documents")
@Route(value = SaplDocumentsView.ROUTE, layout = AppNavLayout.class)
@JsModule("./list-sapl-documents.js")
@PageTitle("SAPL Documents")
public class SaplDocumentsView extends PolymerTemplate<SaplDocumentsView.ListSaplDocumentsModel> {
	public static final String ROUTE = "documents";

	private final SaplDocumentService saplDocumentService;

	@Id(value = "saplDocumentGrid")
	private Grid<SaplDocument> saplDocumentGrid;

	@Id(value = "createButton")
	private Button createButton;

	public SaplDocumentsView(SaplDocumentService saplDocumentService) {
		this.saplDocumentService = saplDocumentService;

		this.initUI();
	}

	private void initUI() {
		this.initSaplDocumentGrid();

		this.createButton.addClickListener(clickEvent -> {
			this.createButton.getUI().ifPresent(ui -> ui.navigate(NewSaplDocumentView.ROUTE));
		});
	}

	private void initSaplDocumentGrid() {
		// add columns
		this.saplDocumentGrid.addColumn(SaplDocument::getName).setHeader("Name");
		this.saplDocumentGrid.addColumn(SaplDocument::getCurrentVersionNumber).setHeader("Version");
		this.saplDocumentGrid.addColumn(SaplDocument::getPublishedVersionNumberAsString).setHeader("Published Version");
		this.saplDocumentGrid.addColumn(SaplDocument::getLastModified).setHeader("Last Modified");
		this.saplDocumentGrid.addColumn(SaplDocument::getTypeAsString).setHeader("Type");
		this.saplDocumentGrid.getColumns().forEach(col -> col.setAutoWidth(true));
		this.saplDocumentGrid.addComponentColumn(saplDocument -> {
			Button editButton = new Button("Edit", VaadinIcon.EDIT.create());
			editButton.addClickListener(clickEvent -> {
				String uriToNavigateTo = String.format("%s/%d", EditSaplDocumentView.ROUTE, saplDocument.getId());
				editButton.getUI().ifPresent(ui -> ui.navigate(uriToNavigateTo));
			});

			HorizontalLayout componentsForEntry = new HorizontalLayout();
			componentsForEntry.add(editButton);
//			componentsForEntry.add(new Button("Archive", VaadinIcon.ARCHIVE.create()));

			return componentsForEntry;
		});

		// set data provider
		CallbackDataProvider<SaplDocument, Void> dataProvider = DataProvider.fromCallbacks(query -> {
			int offset = query.getOffset();
			int limit = query.getLimit();

			return this.saplDocumentService.getAll().stream().skip(offset).limit(limit);
		}, query -> (int) this.saplDocumentService.getAmount());
		this.saplDocumentGrid.setDataProvider(dataProvider);

		this.saplDocumentGrid.setHeightByRows(true);
	}

	/**
	 * This model binds properties between CreateSaplDocument and
	 * create-sapl-document
	 */
	public interface ListSaplDocumentsModel extends TemplateModel {
	}
}
