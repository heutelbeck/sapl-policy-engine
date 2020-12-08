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
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.polymertemplate.Id;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.templatemodel.TemplateModel;

import io.sapl.server.ce.model.ClientCredentials;
import io.sapl.server.ce.service.ClientCredentialsService;
import io.sapl.server.ce.views.utils.confirm.ConfirmUtils;
import lombok.RequiredArgsConstructor;
import reactor.util.function.Tuple2;

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

	@Id(value = "clientCredentialsGrid")
	private Grid<ClientCredentials> clientCredentialsGrid;

	@Id(value = "keyTextField")
	private TextField keyTextField;

	@Id(value = "secretTextField")
	private TextField secretTextField;

	@Id(value = "secretHintDiv")
	private Div secretHintDiv;

	@Id(value = "showCurrentClientCredentialsLayout")
	private VerticalLayout showCurrentClientCredentialsLayout;

	@Id(value = "createButton")
	private Button createButton;

	@PostConstruct
	private void postConstruct() {
		initUi();
	}

	private void initUi() {
		showCurrentClientCredentialsLayout.setVisible(false);

		createButton.addClickListener((clickEvent) -> {
			Tuple2<ClientCredentials, String> createdClientCredentialsWithNonEncodedSecret = clientCredentialsService
					.createDefault();
			refreshClientCredentialsGrid();

			ClientCredentials createdClientCredentials = createdClientCredentialsWithNonEncodedSecret.getT1();
			clientCredentialsGrid.select(createdClientCredentials);

			keyTextField.setValue(createdClientCredentials.getKey());
			secretTextField.setValue(createdClientCredentialsWithNonEncodedSecret.getT2());
			setVisibilityOfComponentsForSecret(true);
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
				showCurrentClientCredentialsLayout.setVisible(true);

				keyTextField.setValue(clientCredentials.getKey());
				setVisibilityOfComponentsForSecret(false);
			}, () -> {
				showCurrentClientCredentialsLayout.setVisible(false);
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
		showCurrentClientCredentialsLayout.setVisible(false);
	}

	private void setVisibilityOfComponentsForSecret(boolean isVisible) {
		secretTextField.setVisible(isVisible);
		secretHintDiv.setVisible(isVisible);
	}

	/**
	 * This model binds properties between ListClientCredentials and
	 * list-client-credentials
	 */
	public interface ListClientCredentialsModel extends TemplateModel {
	}
}
