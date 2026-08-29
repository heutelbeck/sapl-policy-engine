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
package io.sapl.attributeapigui.connection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.attributeapigui.config.AttributeApiConnectionProperties;

class ConnectionSettingsTests {
    private static final String DEFAULT_CONNECTION_NAME = "CustomName";
    private static final String DEFAULT_HOST            = "http://localhost:8090";
    private static final String DEFAULT_USERNAME        = "username";
    private static final String DEFAULT_PASSWORD        = "password";
    private static final String DEFAULT_APIKEY          = "sapl_1111111111111111";

    @Test
    @DisplayName("Mode NONE is set in the settings")
    void whenModeNoneWithBaseURLIsSetThenConfigure() {
        var settings = new ConnectionSettings(ConnectionMode.NONE, DEFAULT_HOST, null, null, null);
        assertThat(settings.isConfigured()).isTrue();
    }

    @Test
    @DisplayName("Mode BASIC is set in the settings")
    void whenModeBasicWithBaseURLAndUserIsSetThenConfigure() {
        var settings = new ConnectionSettings(ConnectionMode.BASIC, DEFAULT_HOST, DEFAULT_USERNAME, DEFAULT_PASSWORD,
                null);
        assertThat(settings.isConfigured()).isTrue();
        assertThat(settings.username()).isEqualTo(DEFAULT_USERNAME);
        assertThat(settings.password()).isEqualTo(DEFAULT_PASSWORD);
    }

    @Test
    @DisplayName("Mode API is set in the settings")
    void whenModeApiWithBaseURLAndApiKeyIsSetThenConfigure() {
        var settings = new ConnectionSettings(ConnectionMode.API, DEFAULT_HOST, null, null, DEFAULT_APIKEY);
        assertThat(settings.isConfigured()).isTrue();
        assertThat(settings.apiKey()).isEqualTo(DEFAULT_APIKEY);
    }

    @Test
    @DisplayName("Mode None is set in the settings but base url is empty")
    void whenModeNoneWithEmptyBaseURIsSetThenFalse() {
        var settings = new ConnectionSettings(ConnectionMode.NONE, "", null, null, null);
        assertThat(settings.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("from method maps properties with method none and base url")
    void whenFromMethodIsUsedWithNoneThenConnectionSettingsAreRight() {
        var properties = new AttributeApiConnectionProperties();
        properties.setName(DEFAULT_CONNECTION_NAME);
        properties.setMethod(ConnectionMode.NONE);
        properties.setBaseUrl(DEFAULT_HOST);

        var settings = ConnectionSettings.from(properties);
        assertThat(settings.isConfigured()).isTrue();
        assertThat(settings.mode()).isEqualTo(ConnectionMode.NONE);
        assertThat(settings.baseUrl()).isEqualTo(DEFAULT_HOST);
    }

    @Test
    @DisplayName("from method returns false with method none and missing url")
    void whenFromMethodWithNoneAndBlankURLIsUsedThenConnectionSettingsAreFalse() {
        var properties = new AttributeApiConnectionProperties();
        properties.setName(DEFAULT_CONNECTION_NAME);
        properties.setMethod(ConnectionMode.NONE);
        properties.setBaseUrl(null);

        var settings = ConnectionSettings.from(properties);
        assertThat(settings.mode()).isEqualTo(ConnectionMode.NONE);
        assertThat(settings.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("from method maps properties with method basic and set user")
    void whenFromMethodWithBasicAndUserIsUsedThenConnectionSettingsAreRight() {
        var properties = new AttributeApiConnectionProperties();
        properties.setMethod(ConnectionMode.BASIC);
        properties.setName(DEFAULT_CONNECTION_NAME);
        properties.setBaseUrl(DEFAULT_HOST);
        properties.setUsername(DEFAULT_USERNAME);
        properties.setPassword(DEFAULT_PASSWORD);

        var settings = ConnectionSettings.from(properties);
        assertThat(settings.isConfigured()).isTrue();
        assertThat(settings.mode()).isEqualTo(ConnectionMode.BASIC);
        assertThat(settings.baseUrl()).isEqualTo(DEFAULT_HOST);
        assertThat(settings.username()).isEqualTo(DEFAULT_USERNAME);
        assertThat(settings.password()).isEqualTo(DEFAULT_PASSWORD);
    }

    @Test
    @DisplayName("from method maps properties with method basic and set user")
    void whenFromMethodWithApiAndApiKeyIsUsedThenConnectionSettingsAreRight() {
        var properties = new AttributeApiConnectionProperties();
        properties.setName(DEFAULT_CONNECTION_NAME);
        properties.setMethod(ConnectionMode.API);
        properties.setBaseUrl(DEFAULT_HOST);
        properties.setApiKey(DEFAULT_APIKEY);

        var settings = ConnectionSettings.from(properties);
        assertThat(settings.isConfigured()).isTrue();
        assertThat(settings.mode()).isEqualTo(ConnectionMode.API);
        assertThat(settings.baseUrl()).isEqualTo(DEFAULT_HOST);
        assertThat(settings.apiKey()).isEqualTo(DEFAULT_APIKEY);
    }

    @Test
    @DisplayName("Connection settings are properly held and switched in the registry class for the UI")
    void whenConnectionRegistryIsUsedThenConnectionRotationIsCorrect() {
        var properties = new AttributeApiConnectionProperties();
        properties.setName(DEFAULT_CONNECTION_NAME);
        properties.setMethod(ConnectionMode.NONE);
        properties.setBaseUrl(DEFAULT_HOST);
        var registry = new ConnectionRegistry(properties);
        assertThat(registry.getActiveConnection().name()).isEqualTo(DEFAULT_CONNECTION_NAME);

        var basicConnection = new SavedConnection(UUID.randomUUID().toString(), "Basic",
                new ConnectionSettings(ConnectionMode.BASIC, DEFAULT_HOST, DEFAULT_USERNAME, DEFAULT_PASSWORD, null));
        registry.addConnection(basicConnection);
        registry.setActiveId(basicConnection.id());
        assertThat(registry.getActiveConnection()).isEqualTo(basicConnection);

        var apiConnection = new SavedConnection(UUID.randomUUID().toString(), "Api",
                new ConnectionSettings(ConnectionMode.API, DEFAULT_HOST, null, null, DEFAULT_APIKEY));
        registry.addConnection(apiConnection);
        registry.setActiveId(apiConnection.id());
        assertThat(registry.getActiveConnection()).isEqualTo(apiConnection);
    }
}
