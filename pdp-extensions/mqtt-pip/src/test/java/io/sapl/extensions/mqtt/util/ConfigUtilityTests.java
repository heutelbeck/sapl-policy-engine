/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.extensions.mqtt.util;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static io.sapl.extensions.mqtt.SaplMqttClient.*;
import static io.sapl.extensions.mqtt.util.ConfigUtility.*;
import static io.sapl.extensions.mqtt.util.ErrorUtility.ENVIRONMENT_ERROR_RETRY_ATTEMPTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigUtilityTests {

    private static final String ENVIRONMENT_DEFAULT_MESSAGE = "defaultMessage";

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Test
    void when_jsonValueIsSpecifiedInMqttClientConfig_then_returnJsonValue() {
        // GIVEN
        val defaultConfig = JSON.nullNode();
        val mqttPipConfig = JSON.objectNode().set(ENVIRONMENT_DEFAULT_MESSAGE, JSON.objectNode().put("key", "value"));

        // WHEN
        val config = ConfigUtility.getConfigValueOrDefault(mqttPipConfig, ENVIRONMENT_DEFAULT_MESSAGE, defaultConfig);

        // THEN
        assertThat(config.get("key").asText()).isEqualTo("value");
    }

    @Test
    void when_jsonValueIsNotSpecifiedInMqttClientConfig_then_returnDefaultJsonValue() {
        // GIVEN
        val defaultConfig = JSON.objectNode().put("key", "value");
        val mqttPipConfig = JSON.objectNode();

        // WHEN
        val config = ConfigUtility.getConfigValueOrDefault(mqttPipConfig, ENVIRONMENT_DEFAULT_MESSAGE, defaultConfig);

        // THEN
        assertThat(config.get("key").asText()).isEqualTo("value");
    }

    @Test
    void when_longValueIsSpecifiedInMqttClientConfig_then_returnLongValue() {
        // GIVEN
        val defaultConfig = 5;
        val mqttPipConfig = JSON.objectNode().put(ENVIRONMENT_ERROR_RETRY_ATTEMPTS, 6)
                .put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production").set(ENVIRONMENT_BROKER_CONFIG,
                        JSON.arrayNode()
                                .add(JSON.objectNode().put(ENVIRONMENT_BROKER_CONFIG_NAME, "production")
                                        .put(ENVIRONMENT_BROKER_ADDRESS, "localhost").put(ENVIRONMENT_BROKER_PORT, 1883)
                                        .put(ENVIRONMENT_CLIENT_ID, "mqttPipDefault")));

        // WHEN
        val config = ConfigUtility.getConfigValueOrDefault(mqttPipConfig, ENVIRONMENT_ERROR_RETRY_ATTEMPTS,
                defaultConfig);

        // THEN
        assertThat(config).isEqualTo(6);
    }

    @Test
    void when_getMqttBrokerConfigIsCalledWithEmptyMqttClientConfig_then_throwNoSuchElementException() {
        // GIVEN
        val undefined = Value.UNDEFINED;

        // THEN
        assertThatThrownBy(() -> ConfigUtility.getMqttBrokerConfig(null, undefined))
                .isExactlyInstanceOf(NoSuchElementException.class);
    }

    @Test
    void when_gettingMqttBrokerConfigAndNoConfigParamsAndEmptyConfigInPdpConfig_then_throwException() {
        // GIVEN
        val mqttPipConfig = JSON.objectNode().put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
                .set(ENVIRONMENT_BROKER_CONFIG, JSON.nullNode());

        // THEN
        assertThatThrownBy(() -> ConfigUtility.getMqttBrokerConfig(mqttPipConfig, Value.UNDEFINED))
                .isExactlyInstanceOf(NoSuchElementException.class);
    }

    @Test
    void when_gettingMqttBrokerConfigAndNoReferenceInConfigParamsAndEmptyConfigInPdpConfig_then_throwException() {
        // GIVEN
        val mqttPipConfig = JSON.objectNode().put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
                .set(ENVIRONMENT_BROKER_CONFIG, JSON.nullNode());

        // THEN
        assertThatThrownBy(() -> ConfigUtility.getMqttBrokerConfig(mqttPipConfig, Value.FALSE))
                .isExactlyInstanceOf(NoSuchElementException.class);
    }

    @Test
    void when_gettingMqttBrokerConfigAndReferencingEmptyPdpConfig_then_throwException() {
        // GIVEN
        val mqttPipConfig = JSON.objectNode().put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
                .set(ENVIRONMENT_BROKER_CONFIG, JSON.nullNode());

        // THEN
        assertThatThrownBy(() -> ConfigUtility.getMqttBrokerConfig(mqttPipConfig, Value.of("reference")))
                .isExactlyInstanceOf(NoSuchElementException.class);
    }

    @Test
    void when_gettingMqttBrokerConfigAndBrokerConfigNameDoesNotEqualTheReferencedConfiguration_then_throwException() {
        // GIVEN
        val mqttPipConfig = JSON.objectNode().put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production").set(
                ENVIRONMENT_BROKER_CONFIG,
                JSON.objectNode().put(ENVIRONMENT_BROKER_CONFIG_NAME, "production")
                        .put(ENVIRONMENT_BROKER_ADDRESS, "localhost").put(ENVIRONMENT_BROKER_PORT, 1883)
                        .put(ENVIRONMENT_CLIENT_ID, "mqttPipDefault"));

        // THEN
        assertThatThrownBy(() -> ConfigUtility.getMqttBrokerConfig(mqttPipConfig, Value.of("reference")))
                .isExactlyInstanceOf(NoSuchElementException.class);
    }

    @Test
    void when_gettingMqttBrokerConfigAndReferenceViaParamsButNoReferenceSpecifiedInPdpConfigArray_then_throwException() {
        // GIVEN
        val mqttPipConfig = JSON.objectNode().put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
                .set(ENVIRONMENT_BROKER_CONFIG, JSON.objectNode().put(ENVIRONMENT_BROKER_ADDRESS, "localhost")
                        .put(ENVIRONMENT_BROKER_PORT, 1883).put(ENVIRONMENT_CLIENT_ID, "mqttPipDefault"));

        // THEN
        assertThatThrownBy(() -> ConfigUtility.getMqttBrokerConfig(mqttPipConfig, Value.of("reference")))
                .isExactlyInstanceOf(NoSuchElementException.class);
    }

    @Test
    void when_gettingMqttBrokerConfigAndReferenceIsSpecifiedInParamsButNoReferenceSpecifiedInPdpConfigObject_then_throwException() {
        // GIVEN
        val mqttPipConfig = JSON.objectNode().put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production").set(
                ENVIRONMENT_BROKER_CONFIG,
                JSON.arrayNode().add(JSON.objectNode().put(ENVIRONMENT_BROKER_ADDRESS, "localhost")
                        .put(ENVIRONMENT_BROKER_PORT, 1883).put(ENVIRONMENT_CLIENT_ID, "mqttPipDefault")));

        // THEN
        assertThatThrownBy(() -> ConfigUtility.getMqttBrokerConfig(mqttPipConfig, Value.UNDEFINED))
                .isExactlyInstanceOf(NoSuchElementException.class);
    }

    @Test
    void when_configAsArrayInPdpJsonIsReferencedViaAttributeFinder_then_getReferencedConfig() {
        // GIVEN
        val mqttPipConfig = JSON.objectNode().put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production").set(
                ENVIRONMENT_BROKER_CONFIG,
                JSON.arrayNode()
                        .add(JSON.objectNode().put(ENVIRONMENT_BROKER_CONFIG_NAME, "production")
                                .put(ENVIRONMENT_BROKER_ADDRESS, "localhost").put(ENVIRONMENT_BROKER_PORT, 1883)
                                .put(ENVIRONMENT_CLIENT_ID, "mqttPipDefault"))
                        .add(JSON.objectNode().put(ENVIRONMENT_BROKER_CONFIG_NAME, "broker2")
                                .put(ENVIRONMENT_BROKER_ADDRESS, "localhost").put(ENVIRONMENT_BROKER_PORT, 1883)
                                .put(ENVIRONMENT_CLIENT_ID, "broker2")));
        val brokerConfig  = Value.of("broker2");

        // WHEN
        val config = ConfigUtility.getMqttBrokerConfig(mqttPipConfig, brokerConfig);

        // THEN
        assertThat(config.get(ENVIRONMENT_CLIENT_ID).asText()).isEqualTo("broker2");
    }

    @Test
    void when_configAsObjectInPdpJsonIsReferencedViaAttributeFinder_then_getReferencedConfig() {
        // GIVEN
        val mqttPipConfig = JSON.objectNode().put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production").set(
                ENVIRONMENT_BROKER_CONFIG,
                JSON.objectNode().put(ENVIRONMENT_BROKER_CONFIG_NAME, "broker2")
                        .put(ENVIRONMENT_BROKER_ADDRESS, "localhost").put(ENVIRONMENT_BROKER_PORT, 1883)
                        .put(ENVIRONMENT_CLIENT_ID, "broker2"));
        val brokerConfig  = Value.of("broker2");

        // WHEN
        val config = ConfigUtility.getMqttBrokerConfig(mqttPipConfig, brokerConfig);

        // THEN
        assertThat(config.get(ENVIRONMENT_CLIENT_ID).asText()).isEqualTo("broker2");
    }

    @Test
    void when_configAsObjectInPdpJsonAndNoAttributeFinderParam_then_getConfigFromPdpJson() {
        // GIVEN
        val undefined     = Value.UNDEFINED;
        val mqttPipConfig = JSON.objectNode().put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production").set(
                ENVIRONMENT_BROKER_CONFIG,
                JSON.objectNode().put(ENVIRONMENT_BROKER_CONFIG_NAME, "production")
                        .put(ENVIRONMENT_BROKER_ADDRESS, "localhost").put(ENVIRONMENT_BROKER_PORT, 1883)
                        .put(ENVIRONMENT_CLIENT_ID, "production"));

        // WHEN
        val config = ConfigUtility.getMqttBrokerConfig(mqttPipConfig, undefined);

        // THEN
        assertThat(config.get(ENVIRONMENT_CLIENT_ID).asText()).isEqualTo("production");
    }

}
