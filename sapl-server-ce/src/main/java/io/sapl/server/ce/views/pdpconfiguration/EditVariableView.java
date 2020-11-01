package io.sapl.server.ce.views.pdpconfiguration;

import org.springframework.beans.factory.annotation.Autowired;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.polymertemplate.Id;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.templatemodel.TemplateModel;

import io.sapl.server.ce.model.pdpconfiguration.Variable;
import io.sapl.server.ce.service.pdpconfiguration.DuplicatedVariableNameException;
import io.sapl.server.ce.service.pdpconfiguration.InvalidJsonException;
import io.sapl.server.ce.service.pdpconfiguration.VariablesService;
import io.sapl.server.ce.views.AppNavLayout;
import io.sapl.server.ce.views.utils.error.ErrorNotificationUtils;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * A Designer generated component for the edit-variable template.
 *
 * Designer will add and remove fields with @Id mappings but does not overwrite
 * or otherwise change this file.
 */
@Tag("edit-variable")
@Route(value = EditVariableView.ROUTE, layout = AppNavLayout.class)
@Slf4j
@JsModule("./edit-variable.js")
@PageTitle("Edit Variable")
@NoArgsConstructor
public class EditVariableView extends PolymerTemplate<EditVariableView.EditVariableModel>
		implements HasUrlParameter<Long> {
	public static final String ROUTE = "configuration/variables/edit";

	@Autowired
	private VariablesService variableService;

	private long variableId;

	@Id(value = "nameTextArea")
	private TextArea nameTextArea;

	@Id(value = "jsonValueTextArea")
	private TextArea jsonValueTextArea;

	@Id(value = "editButton")
	private Button editButton;

	@Id(value = "cancelButton")
	private Button cancelButton;

	/**
	 * The {@link Variable} to edit.
	 */
	@NonNull
	private Variable variable;

	@Override
	public void setParameter(BeforeEvent event, Long parameter) {
		this.variableId = parameter;

		this.reloadVariable();
		this.addListener();
	}

	private void reloadVariable() {
		this.variable = this.variableService.getById(this.variableId);

		this.setUI();
	}

	/**
	 * Imports the previously set instance of {@link Variable} to the UI.
	 */
	private void setUI() {
		this.nameTextArea.setValue(this.variable.getName());
		this.jsonValueTextArea.setValue(this.variable.getJsonValue());
	}

	private void addListener() {
		editButton.addClickListener(clickEvent -> {
			String name = nameTextArea.getValue();
			String jsonValue = jsonValueTextArea.getValue();

			try {
				variableService.edit(this.variableId, name, jsonValue);
			} catch (InvalidJsonException ex) {
				log.error("cannot edit variable due to invalid json", ex);
				ErrorNotificationUtils.show("Value contains invalid JSON");
				return;
			} catch (DuplicatedVariableNameException ex) {
				log.error("cannot edit variable due to duplicated name", ex);
				ErrorNotificationUtils.show("Name is already used by another name");
				return;
			}

			cancelButton.getUI().ifPresent(ui -> ui.navigate(ConfigurePdp.ROUTE));
		});

		cancelButton.addClickListener(clickEvent -> {
			getUI().ifPresent(ui -> ui.navigate(ConfigurePdp.ROUTE));
		});
	}

	public interface EditVariableModel extends TemplateModel {
	}
}
