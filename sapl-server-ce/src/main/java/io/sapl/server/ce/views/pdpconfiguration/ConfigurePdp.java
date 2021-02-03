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
package io.sapl.server.ce.views.pdpconfiguration;

import javax.annotation.PostConstruct;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dialog.Dialog;
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

import io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm;
import io.sapl.server.ce.model.pdpconfiguration.Variable;
import io.sapl.server.ce.service.pdpconfiguration.CombiningAlgorithmService;
import io.sapl.server.ce.service.pdpconfiguration.DuplicatedVariableNameException;
import io.sapl.server.ce.service.pdpconfiguration.InvalidVariableNameException;
import io.sapl.server.ce.service.pdpconfiguration.VariablesService;
import io.sapl.server.ce.views.MainView;
import io.sapl.server.ce.views.utils.confirm.ConfirmUtils;
import io.sapl.server.ce.views.utils.error.ErrorNotificationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A Designer generated component for the configure-pdp template.
 *
 * Designer will add and remove fields with @Id mappings but does not overwrite
 * or otherwise change this file.
 */
@Tag("configure-pdp")
@Slf4j
@Route(value = ConfigurePdp.ROUTE, layout = MainView.class)
@JsModule("./configure-pdp.js")
@PageTitle("PDP Configuration")
@RequiredArgsConstructor
public class ConfigurePdp extends PolymerTemplate<ConfigurePdp.ConfigurePdpModel> {
	public static final String ROUTE = "pdp-config";

	private final CombiningAlgorithmService combiningAlgorithmService;
	private final VariablesService variablesService;

	@Id(value = "comboBoxCombAlgo")
	private ComboBox<String> comboBoxCombAlgo;

	@Id(value = "variablesGrid")
	private Grid<Variable> variablesGrid;

	@Id(value = "createVariableButton")
	private Button createVariableButton;

	private boolean isIgnoringNextCombiningAlgorithmComboBoxChange;

	@PostConstruct
	public void postConstruct() {
		initUi();
	}

	private void initUi() {
		initUiForCombiningAlgorithm();
		initUiForVariables();
	}

	private void initUiForCombiningAlgorithm() {
		PolicyDocumentCombiningAlgorithm[] availableCombiningAlgorithms = combiningAlgorithmService.getAvailable();
		String[] availableCombiningAlgorithmsAsStrings = PolicyDocumentCombiningAlgorithmEncoding
				.encode(availableCombiningAlgorithms);

		comboBoxCombAlgo.setItems(availableCombiningAlgorithmsAsStrings);

		PolicyDocumentCombiningAlgorithm selectedCombiningAlgorithm = combiningAlgorithmService.getSelected();
		comboBoxCombAlgo.setValue(PolicyDocumentCombiningAlgorithmEncoding.encode(selectedCombiningAlgorithm));

		comboBoxCombAlgo.addValueChangeListener(changedEvent -> {
			if (isIgnoringNextCombiningAlgorithmComboBoxChange) {
				isIgnoringNextCombiningAlgorithmComboBoxChange = false;
				return;
			}

			String encodedEntry = changedEvent.getValue();
			PolicyDocumentCombiningAlgorithm newCombiningAlgorithm = PolicyDocumentCombiningAlgorithmEncoding
					.decode(encodedEntry);

			ConfirmUtils.letConfirm(
					"The combining algorithm describes how to come to the final decision while evaluating all published policies.\n\nPlease consider the consequences and confirm the action.",
					() -> {
						combiningAlgorithmService.setSelected(newCombiningAlgorithm);
					}, () -> {
						isIgnoringNextCombiningAlgorithmComboBoxChange = true;

						String oldCombiningAlgorithmAsString = changedEvent.getOldValue();
						comboBoxCombAlgo.setValue(oldCombiningAlgorithmAsString);
					});
		});
	}

	private void initUiForVariables() {
		createVariableButton.addClickListener(clickEvent -> {
			showDialogForVariableCreation();
		});

		initVariablesGrid();
	}

	private void showDialogForVariableCreation() {
		CreateVariable dialogContent = new CreateVariable();

		Dialog createDialog = new Dialog(dialogContent);
		createDialog.setWidth("600px");
		createDialog.setModal(true);
		createDialog.setCloseOnEsc(false);
		createDialog.setCloseOnOutsideClick(false);

		dialogContent.setUserConfirmedListener(isConfirmed -> {
			if (isConfirmed) {
				String name = dialogContent.getNameOfVariableToCreate();
				try {
					variablesService.create(name);
				} catch (InvalidVariableNameException ex) {
					log.error("cannot create variable due to invalid name", ex);
					ErrorNotificationUtils.show(String.format("The name is invalid (min length: %d, max length: %d).",
							VariablesService.MIN_NAME_LENGTH, VariablesService.MAX_NAME_LENGTH));
					return;
				} catch (DuplicatedVariableNameException ex) {
					log.error("cannot create variable due to duplicated name", ex);
					ErrorNotificationUtils.show("The name is already used by another variable.");
					return;
				}

				// reload grid after creation
				variablesGrid.getDataProvider().refreshAll();
			}

			createDialog.close();
		});

		createDialog.open();
	}

	private void initVariablesGrid() {
		// add columns
		variablesGrid.addColumn(Variable::getName).setHeader("Name");
		variablesGrid.addColumn(Variable::getJsonValue).setHeader("JSON Value");
		variablesGrid.addComponentColumn(variable -> {
			Button editButton = new Button("Edit", VaadinIcon.EDIT.create());
			editButton.addClickListener(clickEvent -> {
				String uriToNavigateTo = String.format("%s/%d", EditVariableView.ROUTE, variable.getId());
				editButton.getUI().ifPresent(ui -> ui.navigate(uriToNavigateTo));
			});
			editButton.setThemeName("primary");

			Button deleteButton = new Button("Delete", VaadinIcon.FILE_REMOVE.create());
			deleteButton.addClickListener(clickEvent -> {
				variablesService.delete(variable.getId());

				// trigger refreshing variable grid
				variablesGrid.getDataProvider().refreshAll();
			});
			deleteButton.setThemeName("primary");

			HorizontalLayout componentsForEntry = new HorizontalLayout();
			componentsForEntry.add(editButton);
			componentsForEntry.add(deleteButton);

			return componentsForEntry;
		});

		// set data provider
		CallbackDataProvider<Variable, Void> dataProvider = DataProvider.fromCallbacks(query -> {
			int offset = query.getOffset();
			int limit = query.getLimit();

			return variablesService.getAll().stream().skip(offset).limit(limit);
		}, query -> (int) variablesService.getAmount());
		variablesGrid.setDataProvider(dataProvider);

		variablesGrid.setHeightByRows(true);
	}

	/**
	 * This model binds properties between ConfigurePdp and configure-pdp
	 */
	public interface ConfigurePdpModel extends TemplateModel {
	}
}
