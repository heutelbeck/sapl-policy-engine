/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.attributeapigui.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.sapl.attributeapigui.connection.ConnectionMode;
import io.sapl.attributeapigui.connection.ConnectionSettings;
import io.sapl.attributeapigui.connection.ConnectionSettingsHolder;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "settings", layout = MainLayout.class)
@Menu(order = 2, icon = "vaadin:cog", title = "Settings")
@PageTitle("Settings")
@RolesAllowed("ADMIN")
public class SettingsView extends VerticalLayout {
    private static final String INFO_OAUTH2_NOT_AVAILABLE_YET = "OAuth2 authentication is not yet available in this GUI.";

    private final TextField                baseUrlField  = new TextField("API url");
    private final ComboBox<ConnectionMode> modeField     = new ComboBox<>("Authentication");
    private final TextField                usernameField = new TextField("Username");
    private final PasswordField            passwordField = new PasswordField("Password");
    private final PasswordField            apiKeyField   = new PasswordField("Api key");

    public SettingsView(ConnectionSettingsHolder settingsHolder) {
        var settings = settingsHolder.get();

        // 1 rem = 16px -> 16pxx32rem = 512px
        setMaxWidth("32rem");
        add(new H2("Connection"));
        add(new Paragraph("Configure the settings needed for to access the SAPL attribute API"));

        // Baser URL with preset settings
        baseUrlField.setPlaceholder("http://localhost:8090");
        baseUrlField.setWidthFull();
        baseUrlField.setValue(settings.baseUrl() != null ? settings.baseUrl() : "");

        // Dropdown with available settings
        modeField.setItems(ConnectionMode.values());
        modeField.setItemLabelGenerator(SettingsView::labelFor);
        // OIDC has no login flow implemented in this GUI yet - keep the enum
        // value for later, but gray it out in the list so it cannot be selected.
        modeField.setRenderer(new ComponentRenderer<>(mode -> {
            var item = new Span(labelFor(mode));
            if (mode == ConnectionMode.OIDC) {
                item.getStyle().set("opacity", "0.5").set("pointer-events", "none");
            }
            return item;
        }));
        modeField.setValue(settings.mode() == ConnectionMode.OIDC ? ConnectionMode.NONE : settings.mode());
        modeField.setWidthFull();

        // Switch the visible fields, OIDC cannot be selected (see renderer above)
        modeField.addValueChangeListener(event -> {
            if (event.getValue() == ConnectionMode.OIDC) {
                modeField.setValue(event.getOldValue());
                Notification.show(INFO_OAUTH2_NOT_AVAILABLE_YET);
                return;
            }
            updateVisibility(event.getValue());
        });

        // Settings of the fields
        usernameField.setWidthFull();
        usernameField.setValue(settings.username() != null ? settings.username() : "");
        passwordField.setWidthFull();
        passwordField.setValue(settings.password() != null ? settings.password() : "");
        apiKeyField.setWidthFull();
        apiKeyField.setValue(settings.apiKey() != null ? settings.apiKey() : "");

        // Set id's for the fields to find them easier in tests
        baseUrlField.setId("settings-base-url");
        usernameField.setId("settings-username");
        passwordField.setId("settings-password");
        apiKeyField.setId("settings-api-key");

        // Save button
        var saveButton = new Button("Save", event -> {
            var updated = new ConnectionSettings(modeField.getValue(), baseUrlField.getValue().trim(),
                    usernameField.getValue().trim(), passwordField.getValue(), apiKeyField.getValue());
            settingsHolder.update(updated);

            var notification = Notification.show("Connection settings saved.");
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        saveButton.setId("settings-save");

        add(baseUrlField, modeField, usernameField, passwordField, apiKeyField, saveButton);

        updateVisibility(modeField.getValue());
    }

    private void updateVisibility(ConnectionMode mode) {
        usernameField.setVisible(mode == ConnectionMode.BASIC);
        passwordField.setVisible(mode == ConnectionMode.BASIC);
        apiKeyField.setVisible(mode == ConnectionMode.API);
    }

    private static String labelFor(ConnectionMode mode) {
        return switch (mode) {
        case NONE  -> "No authentication";
        case BASIC -> "Username + Password";
        case API   -> "API key";
        case OIDC  -> "OpenID Connect";
        };
    }

    ComboBox<ConnectionMode> getModeField() {
        return modeField;
    }
}
