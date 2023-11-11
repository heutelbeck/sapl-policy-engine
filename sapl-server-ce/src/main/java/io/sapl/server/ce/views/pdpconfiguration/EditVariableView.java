/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.views.pdpconfiguration;

import java.util.Optional;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.polymertemplate.Id;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.templatemodel.TemplateModel;

import io.sapl.server.ce.model.pdpconfiguration.Variable;
import io.sapl.server.ce.service.pdpconfiguration.DuplicatedVariableNameException;
import io.sapl.server.ce.service.pdpconfiguration.InvalidJsonException;
import io.sapl.server.ce.service.pdpconfiguration.InvalidVariableNameException;
import io.sapl.server.ce.service.pdpconfiguration.VariablesService;
import io.sapl.server.ce.views.MainView;
import io.sapl.server.ce.views.utils.error.ErrorNotificationUtils;
import io.sapl.vaadin.JsonEditor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Tag("edit-variable")
@Route(value = EditVariableView.ROUTE, layout = MainView.class)
@Slf4j
@JsModule("./edit-variable.js")
@PageTitle("Edit Variable")
@RequiredArgsConstructor
public class EditVariableView extends PolymerTemplate<EditVariableView.EditVariableModel>
		implements HasUrlParameter<Long> {
	public static final String ROUTE = "pdp-config/edit-variable";

	private final VariablesService variableService;

	private long variableId;

	@Id(value = "nameTextField")
	private TextField nameTextField;

	@Id(value = "jsonEditor")
	private JsonEditor jsonEditor;

	@Id(value = "editButton")
	private Button editButton;

	@Id(value = "cancelButton")
	private Button cancelButton;

	/**
	 * The {@link Variable} to edit.
	 */
	private Variable variable;

	@Override
	public void setParameter(BeforeEvent event, Long parameter) {
		variableId = parameter;

		reloadVariable();
		addListener();

		addAttachListener((__) -> {
			if (variable == null) {
				log.warn("variable with id {} is not existing, redirect to list view", parameter);
				getUI().ifPresent(ui -> ui.navigate(ConfigurePdp.ROUTE));
			}
		});
	}

	private void reloadVariable() {
		Optional<Variable> optionalVariable = variableService.getById(variableId);
		if (optionalVariable.isEmpty()) {
			// Vaadin UI object is not available yet, redirect to list view via attach listener
			return;
		}

		variable = optionalVariable.get();

		setUI();
	}

	/**
	 * Imports the previously set instance of {@link Variable} to the UI.
	 */
	private void setUI() {
		nameTextField.setValue(variable.getName());
		jsonEditor.setDocument(variable.getJsonValue());
	}

	private void addListener() {
		editButton.addClickListener(clickEvent -> {
			String name = nameTextField.getValue();
			String jsonValue = jsonEditor.getDocument();

			try {
				variableService.edit(variableId, name, jsonValue);
			} catch (InvalidJsonException ex) {
				log.error("cannot edit variable due to invalid json", ex);
				ErrorNotificationUtils.show("The value of the variable contains invalid JSON.");
				return;
			} catch (InvalidVariableNameException ex) {
				log.error("cannot create variable due to invalid name", ex);
				ErrorNotificationUtils.show(String.format("The name is invalid (min length: %d, max length: %d).",
						VariablesService.MIN_NAME_LENGTH, VariablesService.MAX_NAME_LENGTH));
				return;
			} catch (DuplicatedVariableNameException ex) {
				log.error("cannot edit variable due to duplicated name", ex);
				ErrorNotificationUtils.show("The name is already used by another variable.");
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
