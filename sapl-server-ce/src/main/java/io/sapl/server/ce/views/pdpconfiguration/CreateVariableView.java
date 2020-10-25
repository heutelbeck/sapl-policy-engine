package io.sapl.server.ce.views.pdpconfiguration;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.polymertemplate.Id;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.templatemodel.TemplateModel;

import io.sapl.server.ce.service.pdpconfiguration.DuplicatedVariableNameException;
import io.sapl.server.ce.service.pdpconfiguration.InvalidJsonException;
import io.sapl.server.ce.service.pdpconfiguration.VariablesService;
import io.sapl.server.ce.utils.error.ErrorNotificationUtils;
import io.sapl.server.ce.views.AppNavLayout;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * A Designer generated component for the create-variable template.
 *
 * Designer will add and remove fields with @Id mappings but
 * does not overwrite or otherwise change this file.
 */
@Slf4j
@Tag("create-variable")
@Route(value = CreateVariableView.ROUTE, layout = AppNavLayout.class)
@JsModule("./create-variable.js")
@PageTitle("Create Variable")
public class CreateVariableView extends PolymerTemplate<CreateVariableView.CreateVariableModel> {
	public static final String ROUTE = "configuration/variables/create";

	private final VariablesService variableService;

	@Id(value = "nameTextArea")
	private TextArea nameTextArea;

	@Id(value = "jsonValueTextArea")
	private TextArea jsonValueTextArea;

	@Id(value = "createButton")
	private Button createButton;

	@Id(value = "cancelButton")
	private Button cancelButton;

	public CreateVariableView(@NonNull VariablesService variableService) {
		this.variableService = variableService;

		this.initUI();
    }

	private void initUI() {
		this.createButton.addClickListener(clickEvent -> {
			String name = this.nameTextArea.getValue();
			String jsonValue = this.jsonValueTextArea.getValue();
			try {
				this.variableService.create(name, jsonValue);
			} catch (InvalidJsonException ex) {
				log.error("cannot create variable due to invalid json", ex);
				ErrorNotificationUtils.show("Value contains invalid JSON");
				return;
			} catch (DuplicatedVariableNameException ex) {
				log.error("cannot create variable due to duplicated name", ex);
				ErrorNotificationUtils.show("Name is already used by another name");
				return;
			}

			this.cancelButton.getUI().ifPresent(ui -> ui.navigate(VariablesView.ROUTE));
		});

		this.cancelButton.addClickListener(clickEvent -> {
			this.cancelButton.getUI().ifPresent(ui -> ui.navigate(VariablesView.ROUTE));
		});
	}

	public interface CreateVariableModel extends TemplateModel {
    }
}
