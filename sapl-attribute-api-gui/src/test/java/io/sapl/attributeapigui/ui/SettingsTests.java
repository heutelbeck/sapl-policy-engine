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

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import io.sapl.attributeapigui.GuiApplication;
import io.sapl.attributeapigui.connection.ConnectionMode;
import io.sapl.attributeapigui.connection.ConnectionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = GuiApplication.class, properties = { "io.sapl.attribute-api-gui.admin-username=admin",
        "io.sapl.attribute-api-gui.admin-password=admin",
        "spring.autoconfigure.exclude=org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration" })
class SettingsTests extends SpringBrowserlessTest {
    @Autowired
    private ObjectProvider<ConnectionRegistry> registryProvider;

    @Test
    @WithUserDetails("admin")
    @DisplayName("Settings view is reachable with an authenticated user")
    void whenAuthenticatedThenSettingsViewIsReachable() {
        navigate(SettingsView.class);

        assertThat(getCurrentView()).isInstanceOf(SettingsView.class);
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("Saving a new connection with no authentication adds it to the registry")
    void whenAuthenticatedThenNewConnectionCanBeSavedWithNoAuth() {
        var settingsView = navigate(SettingsView.class);
        var modeField    = settingsView.getModeField();
        var nameField    = find(TextField.class).id("settings-connection-name");
        var saveButton   = find(Button.class).id("settings-save");
        var registry     = registryProvider.getObject();

        test(nameField).setValue("No-Auth Connection");
        test(modeField).selectItem("No authentication");
        test(saveButton).click();

        var saved = registry.getSavedConnection().stream().filter(c -> "No-Auth Connection".equals(c.name()))
                .findFirst().orElseThrow();
        assertThat(saved.settings().mode()).isEqualTo(ConnectionMode.NONE);
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("Saving a new connection with basic authentication adds it to the registry")
    void whenAuthenticatedThenNewConnectionCanBeSavedWithBasicAuth() {
        var settingsView = navigate(SettingsView.class);
        var modeField    = settingsView.getModeField();
        var nameField    = find(TextField.class).id("settings-connection-name");
        var saveButton   = find(Button.class).id("settings-save");
        var registry     = registryProvider.getObject();

        test(nameField).setValue("Basic-Auth Connection");
        test(modeField).selectItem("Username + Password");

        // Fields are only visible after clicking
        var usernameField = find(TextField.class).id("settings-username");
        var passwordField = find(PasswordField.class).id("settings-password");

        test(usernameField).setValue("api-user");
        test(passwordField).setValue("api-secret");
        test(saveButton).click();

        var saved = registry.getSavedConnection().stream().filter(c -> "Basic-Auth Connection".equals(c.name()))
                .findFirst().orElseThrow();
        assertThat(saved.settings().mode()).isEqualTo(ConnectionMode.BASIC);
        assertThat(saved.settings().username()).isEqualTo("api-user");
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("Saving a new connection with api key authentication adds it to the registry")
    void whenAuthenticatedThenNewConnectionCanBeSavedWithApiKeyAuth() {
        var settingsView = navigate(SettingsView.class);
        var modeField    = settingsView.getModeField();
        var nameField    = find(TextField.class).id("settings-connection-name");
        var saveButton   = find(Button.class).id("settings-save");
        var registry     = registryProvider.getObject();

        test(nameField).setValue("Api-Key Connection");
        test(modeField).selectItem("API key");
        var apiKeyField = find(PasswordField.class).id("settings-api-key");

        test(apiKeyField).setValue("sapl_12345xyz");
        test(saveButton).click();

        var saved = registry.getSavedConnection().stream().filter(c -> "Api-Key Connection".equals(c.name()))
                .findFirst().orElseThrow();
        assertThat(saved.settings().mode()).isEqualTo(ConnectionMode.API);
        assertThat(saved.settings().apiKey()).isEqualTo("sapl_12345xyz");
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("Saving a connection without a name shows a validation error and nothing is added")
    void whenConnectionNameIsBlankThenValidationErrorIsShownAndNothingIsSaved() {
        var settingsView = navigate(SettingsView.class);
        var modeField    = settingsView.getModeField();
        var saveButton   = find(Button.class).id("settings-save");
        var registry     = registryProvider.getObject();
        var before       = registry.getSavedConnection().size();

        test(modeField).selectItem("No authentication");
        test(saveButton).click();

        var nameField = find(TextField.class).id("settings-connection-name");
        assertThat(nameField.isInvalid()).isTrue();
        assertThat(registry.getSavedConnection()).hasSize(before);
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("Deleting a connection causes an UI refresh and last connection is not possible")
    void whenConnectionIsDeletedThenUIRefreshedAndDeleteLastConnectionNotPossible() {
        var settingsView = navigate(SettingsView.class);
        var registry     = registryProvider.getObject();

        // Add a second connection to see if the refresh is working. Delete it afterwards
        test(find(TextField.class).id("settings-connection-name")).setValue("delete-connection");
        test(settingsView.getModeField()).selectItem("No authentication");
        test(find(Button.class).id("settings-save")).click();

        var grid = settingsView.getSavedConnections();
        assertThat(test(grid).size()).isEqualTo(2);

        // Delete the second connection by clicking into the right column and row
        var deleteButton = (Button) test(grid).getCellComponent(1, 3);
        test(deleteButton).click();

        assertThat(test(grid).size()).isEqualTo(1);
        assertThat(registry.getSavedConnection()).noneMatch(c -> "delete-connection".equals(c.name()));

        // Now try to delete the last connection
        deleteButton = (Button) test(grid).getCellComponent(0, 3);
        assertThat(deleteButton.isEnabled()).isFalse();
    }
}
