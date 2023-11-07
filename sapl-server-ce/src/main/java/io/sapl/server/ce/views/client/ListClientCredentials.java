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
package io.sapl.server.ce.views.client;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
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

import io.sapl.server.ce.model.ClientCredentials;
import io.sapl.server.ce.service.ClientCredentialsService;
import io.sapl.server.ce.views.MainView;
import io.sapl.server.ce.views.utils.confirm.ConfirmUtils;
import io.sapl.server.ce.views.utils.error.ErrorNotificationUtils;
import lombok.NonNull;
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

	@Id(value = "createButton")
	private Button createButton;

	@PostConstruct
	private void postConstruct() {
		initUi();
	}

	private void initUi() {
		createButton.addClickListener((clickEvent) -> {
			Tuple2<ClientCredentials, String> clientCredentialsWithSecret;
			try {
				clientCredentialsWithSecret = clientCredentialsService.createDefault();
			} catch (Throwable throwable) {
				getUI().ifPresent((ui) -> {
					ui.access(() -> {
						ErrorNotificationUtils.show("The client cannot be created due to an internal error.");
					});
				});
				return;
			}

			getUI().ifPresent((ui) -> {
				ui.access(() -> {
					showDialogForCreatedVariable(clientCredentialsWithSecret.getT1().getKey(),
							clientCredentialsWithSecret.getT2());
					clientCredentialsGrid.getDataProvider().refreshAll();
				});
			});
		});

		initClientCredentialsGrid();
	}

	private void initClientCredentialsGrid() {
		clientCredentialsGrid.addColumn(ClientCredentials::getKey).setHeader("Key").setSortable(true);

		clientCredentialsGrid.addComponentColumn(currentClientCredential -> {
			Button deleteButton = new Button("Remove", VaadinIcon.FILE_REMOVE.create());
			deleteButton.setThemeName("primary");
			deleteButton.addClickListener(clickEvent -> {
				ConfirmUtils
						.letConfirm(String.format("Should the client credentials with key \"%s\" really be deleted?",
								currentClientCredential.getKey()), () -> {
									long idOfClientToRemove = currentClientCredential.getId();
									try {
										clientCredentialsService.delete(idOfClientToRemove);
									} catch (Throwable throwable) {
										getUI().ifPresent((ui) -> {
											ui.access(() -> {
												ErrorNotificationUtils
														.show("The client cannot be deleted due to an internal error.");
											});
										});
										return;
									}

									getUI().ifPresent((ui) -> {
										ui.access(() -> {
											clientCredentialsGrid.getDataProvider().refreshAll();
										});
									});
								}, null);
			});

			HorizontalLayout componentsForEntry = new HorizontalLayout();
			componentsForEntry.add(deleteButton);

			return componentsForEntry;
		});

		// set data provider
		CallbackDataProvider<ClientCredentials, Void> dataProvider = DataProvider.fromCallbacks(query -> {
			Stream<ClientCredentials> stream = clientCredentialsService.getAll().stream();

			Optional<Comparator<ClientCredentials>> optionalCompatator = query.getSortingComparator();
			if (optionalCompatator.isPresent()) {
				stream = stream.sorted(optionalCompatator.get());
			}

			return stream.skip(query.getOffset()).limit(query.getLimit());
		}, query -> (int) clientCredentialsService.getAmount());
		clientCredentialsGrid.setDataProvider(dataProvider);
	}

	private void showDialogForCreatedVariable(@NonNull String key, @NonNull String secret) {
		ShowClientSecret dialogContent = new ShowClientSecret(key, secret);

		Dialog dialog = new Dialog(dialogContent);
		dialog.setWidth("600px");
		dialog.setModal(true);
		dialog.setCloseOnEsc(false);
		dialog.setCloseOnOutsideClick(false);

		dialogContent.setOnClosingListener(dialog::close);

		dialog.open();
	}

	public interface ListClientCredentialsModel extends TemplateModel {
	}
}
