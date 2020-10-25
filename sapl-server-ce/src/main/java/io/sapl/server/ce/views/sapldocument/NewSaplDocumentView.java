package io.sapl.server.ce.views.sapldocument;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.polymertemplate.Id;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.templatemodel.TemplateModel;

import io.sapl.server.ce.model.sapldocument.SaplDocument;
import io.sapl.server.ce.service.sapldocument.SaplDocumentService;
import io.sapl.server.ce.views.AppNavLayout;
import io.sapl.vaadin.SaplEditor;

/**
 * View to create a {@link SaplDocument}.
 */
@Tag("create-sapl-document")
@Route(value = NewSaplDocumentView.ROUTE, layout = AppNavLayout.class)
@JsModule("./create-sapl-document.js")
@PageTitle("Create SAPL document")
public class NewSaplDocumentView extends PolymerTemplate<NewSaplDocumentView.CreateSaplDocumentModel> {
	public static final String ROUTE = "documents/create";

	private final SaplDocumentService saplDocumentService;

	@Id(value = "saplEditor")
	private SaplEditor saplEditor;

	@Id(value = "createButton")
	private Button createButton;

	@Id(value = "cancelButton")
	private Button cancelButton;

	public NewSaplDocumentView(SaplDocumentService saplDocumentService) {
		this.saplDocumentService = saplDocumentService;

		this.initUI();
	}

	private void initUI() {
		this.createButton.addClickListener(clickEvent -> {
			this.createSaplDocumentFromCurrentUi();
		});

		this.cancelButton.addClickListener(clickEvent -> {
			this.cancelButton.getUI().ifPresent(ui -> ui.navigate(SaplDocumentsView.ROUTE));
		});

		this.saplEditor.setDocument(this.getInitialDocumentForSaplEditor());
		this.saplEditor.addValidationFinishedListener(validationFinishedEvent -> {
			boolean isCurrentDocumentValid = validationFinishedEvent.getIssues().length == 0;
			this.createButton.setVisible(isCurrentDocumentValid);
		});
	}

	private void createSaplDocumentFromCurrentUi() {
		SaplDocument createdSaplDocument = this.saplDocumentService.create(this.saplEditor.getDocument());

		String editPageUrl = String.format("documents/edit/%d", createdSaplDocument.getId());
		this.createButton.getUI().ifPresent(ui -> ui.navigate(editPageUrl));
	}

	private String getInitialDocumentForSaplEditor() {
		return "policy \"all deny\"\ndeny";
	}

	/**
	 * This model binds properties between CreateSaplDocument and
	 * create-sapl-document
	 */
	public interface CreateSaplDocumentModel extends TemplateModel {
		// Add setters and getters for template properties here.
	}
}
