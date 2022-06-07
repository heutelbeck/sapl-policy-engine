package io.sapl.server.ce.views.pdpconfiguration;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.polymertemplate.Id;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.templatemodel.TemplateModel;

import lombok.Setter;

@Tag("create-variable")
@JsModule("./create-variable.js")
public class CreateVariable extends PolymerTemplate<CreateVariable.CreateVariableModel> {
	@Id(value = "nameTextField")
	private TextField nameTextField;

	@Id(value = "createButton")
	private Button createButton;

	@Id(value = "cancelButton")
	private Button cancelButton;

	@Setter
	private UserConfirmedListener userConfirmedListener;

	public CreateVariable() {
		initUi();
	}

	public String getNameOfVariableToCreate() {
		return nameTextField.getValue();
	}

	private void initUi() {
		createButton.addClickListener((ClickEvent<Button> event) -> {
			setConfirmationResult(true);
		});
		cancelButton.addClickListener((ClickEvent<Button> event) -> {
			setConfirmationResult(false);
		});

		nameTextField.focus();
	}

	private void setConfirmationResult(boolean isConfirmed) {
		if (userConfirmedListener != null) {
			userConfirmedListener.onConfirmationSet(isConfirmed);
		}
	}

	public interface CreateVariableModel extends TemplateModel {
	}

	public interface UserConfirmedListener {
		void onConfirmationSet(boolean isConfirmed);
	}
}
