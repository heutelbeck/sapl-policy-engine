/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.ui.views.clientcredentials;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.context.annotation.Conditional;
import org.vaadin.lineawesome.LineAwesomeIcon;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import io.sapl.api.SaplVersion;
import io.sapl.server.ce.model.clients.ClientCredentials;
import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;
import io.sapl.server.ce.security.ClientDetailsService;
import io.sapl.server.ce.ui.utils.ConfirmUtils;
import io.sapl.server.ce.ui.utils.ErrorNotificationUtils;
import io.sapl.server.ce.ui.views.MainLayout;
import jakarta.annotation.security.RolesAllowed;
import lombok.NonNull;
import reactor.util.function.Tuple2;

@RolesAllowed("ADMIN")
@PageTitle("Client Credentials")
@Route(value = ClientCredentialsView.ROUTE, layout = MainLayout.class)
@Conditional(SetupFinishedCondition.class)
public class ClientCredentialsView extends VerticalLayout {

    private static final long serialVersionUID = SaplVersion.VERISION_UID;

    public static final String ROUTE = "clients";

    private transient ClientDetailsService clientCredentialsService;

    private final Grid<ClientCredentials> clientCredentialsGrid = new Grid<>();

    public ClientCredentialsView(ClientDetailsService clientCredentialsService) {
        this.clientCredentialsService = clientCredentialsService;
        final var createButtons         = new HorizontalLayout();
        final var newBasicClientButton  = new Button("New Basic Client");
        final var newApiKeyClientButton = new Button("New ApiKey Client");
        createButtons.add(newBasicClientButton, newApiKeyClientButton);
        add(createButtons, clientCredentialsGrid);
        clientCredentialsGrid.getStyle().set("font-family", "\"Courier\", monospace");

        newBasicClientButton.addClickListener(e -> createBasicClient());
        newApiKeyClientButton.addClickListener(e -> createApiKeyClient());

        initClientCredentialsGrid();
    }

    private void createBasicClient() {
        Tuple2<ClientCredentials, String> clientCredentialsWithSecret;
        try {
            clientCredentialsWithSecret = clientCredentialsService.createBasicDefault();
        } catch (Exception e) {
            ErrorNotificationUtils.show("The client cannot be created due to an internal error. " + e.getMessage());
            return;
        }
        showDialogForCreatedBasicClient(clientCredentialsWithSecret.getT1().getKey(),
                clientCredentialsWithSecret.getT2());
        clientCredentialsGrid.getDataProvider().refreshAll();
    }

    private void createApiKeyClient() {
        String clientCredentialsWithApiKey;
        try {
            clientCredentialsWithApiKey = clientCredentialsService.createApiKeyDefault();
        } catch (Exception e) {
            ErrorNotificationUtils.show("The client cannot be created due to an internal error. " + e.getMessage());
            return;
        }
        showDialogForCreatedApiKeyClient(clientCredentialsWithApiKey);
        clientCredentialsGrid.getDataProvider().refreshAll();
    }

    private void initClientCredentialsGrid() {
        clientCredentialsGrid.addColumn(ClientCredentials::getKey).setHeader("Key").setSortable(true);
        clientCredentialsGrid.addColumn(ClientCredentials::getAuthType).setHeader("Auth Type").setSortable(true);

        clientCredentialsGrid.addComponentColumn(currentClientCredential -> {
            Button deleteButton = new Button("Delete", LineAwesomeIcon.TRASH_SOLID.create());
            deleteButton.setThemeName("primary");
            deleteButton.addClickListener(clickEvent -> deleteClient(currentClientCredential));

            HorizontalLayout componentsForEntry = new HorizontalLayout();
            componentsForEntry.add(deleteButton);

            return componentsForEntry;
        });

        // set data provider
        CallbackDataProvider<ClientCredentials, Void> dataProvider = DataProvider.fromCallbacks(query -> {
            Stream<ClientCredentials> stream = clientCredentialsService.getAll().stream();

            Optional<Comparator<ClientCredentials>> optionalComparator = query.getSortingComparator();
            if (optionalComparator.isPresent()) {
                stream = stream.sorted(optionalComparator.get());
            }

            return stream.skip(query.getOffset()).limit(query.getLimit());
        }, query -> (int) clientCredentialsService.getAmount());
        clientCredentialsGrid.setItems(dataProvider);
    }

    private void deleteClient(ClientCredentials currentClientCredential) {
        ConfirmUtils.letConfirm("Delete Client",
                String.format("Should the client credentials with key \"%s\" really be deleted?",
                        currentClientCredential.getKey()),
                () -> executeDeletionOfClient(currentClientCredential), () -> {});
    }

    private void executeDeletionOfClient(ClientCredentials currentClientCredential) {
        try {
            clientCredentialsService.delete(currentClientCredential);
        } catch (Exception e) {
            ErrorNotificationUtils.show("The client cannot be deleted due to an internal error. " + e.getMessage());
            return;
        }
        clientCredentialsGrid.getDataProvider().refreshAll();
    }

    private void showDialogForCreatedBasicClient(@NonNull String key, @NonNull String secret) {
        final var layout = new VerticalLayout();
        final var text   = new Span(
                "A new Basic client has been created. The following secret will only be shown once and is not recoverable. Make sure to write it down.");

        final var keyField = new TextField("Client Key");
        keyField.setValue(key);
        keyField.setReadOnly(true);
        keyField.setWidthFull();

        final var secretField = new TextField("Client Secret");
        secretField.setValue(secret);
        secretField.setReadOnly(true);
        secretField.setWidthFull();

        layout.add(text, keyField, secretField);

        Dialog dialog = new Dialog(layout);
        dialog.setHeaderTitle("Client Created");
        final var closeButton = new Button(new Icon("lumo", "cross"), e -> dialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getHeader().add(closeButton);
        dialog.setWidth("600px");
        dialog.setModal(true);
        dialog.setCloseOnEsc(false);
        dialog.setCloseOnOutsideClick(false);
        dialog.open();
    }

    private void showDialogForCreatedApiKeyClient(@NonNull String apiKey) {
        final var layout = new VerticalLayout();
        final var text   = new Span(
                "A new ApiKey client has been created. The following secret will only be shown once and is not recoverable. Make sure to write it down.");

        final var apikeyField = new TextArea("API Key");
        apikeyField.setValue(apiKey);
        apikeyField.setReadOnly(true);
        apikeyField.setWidthFull();

        layout.add(text, apikeyField);

        Dialog dialog = new Dialog(layout);
        dialog.setHeaderTitle("Client Created");
        final var closeButton = new Button(new Icon("lumo", "cross"), e -> dialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getHeader().add(closeButton);
        dialog.setWidth("600px");
        dialog.setModal(true);
        dialog.setCloseOnEsc(false);
        dialog.setCloseOnOutsideClick(false);
        dialog.open();
    }

}
