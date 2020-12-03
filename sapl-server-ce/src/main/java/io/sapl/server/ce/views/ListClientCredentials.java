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
package io.sapl.server.ce.views;

import java.util.Optional;

import javax.annotation.PostConstruct;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.polymertemplate.Id;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.templatemodel.TemplateModel;

import io.sapl.server.ce.model.ClientCredentials;
import io.sapl.server.ce.service.ClientCredentialsService;
import io.sapl.server.ce.views.utils.confirm.ConfirmUtils;
import io.sapl.server.ce.views.utils.error.ErrorNotificationUtils;
import lombok.RequiredArgsConstructor;

/**
 * A Designer generated component for the list-client-credentials template.
 *
 * Designer will add and remove fields with @Id mappings but does not overwrite
 * or otherwise change this file.
 */
@Tag("list-client-credentials")
@Route(value = ListClientCredentials.ROUTE, layout = MainView.class)
@JsModule("./list-client-credentials.js")
@PageTitle("Client Credentials")
@RequiredArgsConstructor
public class ListClientCredentials extends PolymerTemplate<ListClientCredentials.ListClientCredentialsModel> {
	public static final String ROUTE = "clients";

	private final ClientCredentialsService clientCredentialsService;

	private Long currentCredentialsId;

	@Id(value = "clientCredentialsGrid")
	private Grid<ClientCredentials> clientCredentialsGrid;

	@Id(value = "currentKeyTextField")
	private TextField currentKeyTextField;

	@Id(value = "currentSecretPasswordField")
	private PasswordField currentSecretPasswordField;

	@Id(value = "editCurrentClientCredentialsLayout")
	private VerticalLayout editCurrentClientCredentialsLayout;

	@Id(value = "createButton")
	private Button createButton;

	@Id(value = "isChangingSecretCheckBox")
	private Checkbox isChangingSecretCheckBox;

	@Id(value = "saveCurrentCredentialsButton")
	private Button saveCurrentCredentialsButton;

	@PostConstruct
	private void postConstruct() {
		initUi();
	}

	private void initUi() {
		editCurrentClientCredentialsLayout.setVisible(false);

		createButton.addClickListener((clickEvent) -> {
			clientCredentialsService.createDefault();
			refreshClientCredentialsGrid();
		});

		isChangingSecretCheckBox.addValueChangeListener(valueChangeEvent -> {
			currentSecretPasswordField.setEnabled(valueChangeEvent.getValue());
		});

		saveCurrentCredentialsButton.addClickListener((clickEvent) -> {
			String key = currentKeyTextField.getValue();

			String secret;
			if (isChangingSecretCheckBox.getValue()) {
				secret = currentSecretPasswordField.getValue();
				if (secret == null || secret.length() == 0) {
					ErrorNotificationUtils.show("secret must be set");
					return;
				}
			} else {
				secret = null;
			}

			try {
				clientCredentialsService.edit(currentCredentialsId, key, secret);
			} catch (IllegalArgumentException ex) {
				ErrorNotificationUtils.show(ex);
				return;
			}

			refreshClientCredentialsGrid();
		});

		initClientCredentialsGrid();
	}

	private void initClientCredentialsGrid() {
		clientCredentialsGrid.addColumn(ClientCredentials::getKey).setHeader("Key");

		clientCredentialsGrid.addComponentColumn(currentClientCredential -> {
			Button deleteButton = new Button("Remove", VaadinIcon.FILE_REMOVE.create());
			deleteButton.setThemeName("primary");
			deleteButton.addClickListener(clickEvent -> {
				//@formatter:off
				ConfirmUtils.letConfirm(
						String.format("Should the client credentials with key \"%s\" really be deleted?", currentClientCredential.getKey()),
						() -> {
							clientCredentialsService.delete(currentClientCredential.getId());
							refreshClientCredentialsGrid();
						},
						null);
				//@formatter:on
			});

			HorizontalLayout componentsForEntry = new HorizontalLayout();
			componentsForEntry.add(deleteButton);

			return componentsForEntry;
		});

		clientCredentialsGrid.addSelectionListener(selection -> {
			Optional<ClientCredentials> firstSelectedItemAsOptional = selection.getFirstSelectedItem();
			firstSelectedItemAsOptional.ifPresentOrElse(clientCredentials -> {
				currentCredentialsId = clientCredentials.getId();

				editCurrentClientCredentialsLayout.setVisible(true);

				currentKeyTextField.setValue(clientCredentials.getKey());
				currentSecretPasswordField.setValue("");
				isChangingSecretCheckBox.setValue(Boolean.FALSE);
				currentSecretPasswordField.setEnabled(false);
			}, () -> {
				editCurrentClientCredentialsLayout.setVisible(false);
			});
		});

		// set data provider
		CallbackDataProvider<ClientCredentials, Void> dataProvider = DataProvider.fromCallbacks(query -> {
			int offset = query.getOffset();
			int limit = query.getLimit();

			return clientCredentialsService.getAll().stream().skip(offset).limit(limit);
		}, query -> (int) clientCredentialsService.getAmount());
		clientCredentialsGrid.setDataProvider(dataProvider);
	}

	private void refreshClientCredentialsGrid() {
		clientCredentialsGrid.getDataProvider().refreshAll();
		editCurrentClientCredentialsLayout.setVisible(false);
	}

	/**
	 * This model binds properties between ListClientCredentials and
	 * list-client-credentials
	 */
	public interface ListClientCredentialsModel extends TemplateModel {
	}
}
