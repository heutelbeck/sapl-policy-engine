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
import io.sapl.attributeapigui.connection.ConnectionSettingsHolder;
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
    private ObjectProvider<ConnectionSettingsHolder> settingsHolderProvider;

    @Test
    @WithUserDetails("admin")
    @DisplayName("Settings view is reachable with an authenticated user")
    void whenAuthenticatedThenSettingsViewIsReachable() {
        navigate(SettingsView.class);

        assertThat(getCurrentView()).isInstanceOf(SettingsView.class);
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("Settings are set to no authentication")
    void whenAuthenticatedThenSettingsCanSetToNoAuth() {
        var settingsView   = navigate(SettingsView.class);
        var modeField      = settingsView.getModeField();
        var saveButton     = find(Button.class).id("settings-save");
        var settingsHolder = settingsHolderProvider.getObject();

        test(modeField).selectItem("No authentication");
        test(saveButton).click();
        assertThat(settingsHolder.get().mode()).isEqualTo(ConnectionMode.NONE);
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("Settings are set to basic authentication")
    void whenAuthenticatedThenSettingsCanSetToBasicAuth() {
        var settingsView   = navigate(SettingsView.class);
        var modeField      = settingsView.getModeField();
        var saveButton     = find(Button.class).id("settings-save");
        var settingsHolder = settingsHolderProvider.getObject();

        test(modeField).selectItem("Username + Password");

        // Fields are only visible after clicking
        var usernameField = find(TextField.class).id("settings-username");
        var passwordField = find(PasswordField.class).id("settings-password");

        test(usernameField).setValue("api-user");
        test(passwordField).setValue("api-secret");
        test(saveButton).click();
        assertThat(settingsHolder.get().mode()).isEqualTo(ConnectionMode.BASIC);
        assertThat(settingsHolder.get().username()).isEqualTo("api-user");
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("Settings are set to api key authentication")
    void whenAuthenticatedThenSettingsCanSetToApiKeyAuth() {
        var settingsView   = navigate(SettingsView.class);
        var modeField      = settingsView.getModeField();
        var saveButton     = find(Button.class).id("settings-save");
        var settingsHolder = settingsHolderProvider.getObject();

        test(modeField).selectItem("API key");
        var apiKeyField = find(PasswordField.class).id("settings-api-key");

        test(apiKeyField).setValue("sapl_12345xyz");
        test(saveButton).click();
        assertThat(settingsHolder.get().mode()).isEqualTo(ConnectionMode.API);
        assertThat(settingsHolder.get().apiKey()).isEqualTo("sapl_12345xyz");
    }
}
