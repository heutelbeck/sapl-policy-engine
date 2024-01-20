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
package io.sapl.extensions.mqtt.util;

import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hivemq.client.mqtt.datatypes.MqttClientIdentifier;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.reactor.Mqtt5ReactorClient;

import io.sapl.api.interpreter.Val;
import lombok.experimental.UtilityClass;

/**
 * This utility class provides functions to look up configurations from json
 * objects, to handle broker configurations and provides even further utility
 * functions.
 */
@UtilityClass
public class ConfigUtility {

    /**
     * The reference for the default broker configuration name in the
     * configurations.
     */
    public static final String  ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME = "defaultBrokerConfigName";
    /**
     * The reference for the broker configuration name in the configurations.
     */
    public static final String  ENVIRONMENT_BROKER_CONFIG_NAME         = "name";
    /**
     * The reference for the broker configuration settings in configurations.
     */
    public static final String  ENVIRONMENT_BROKER_CONFIG              = "brokerConfig";
    private static final String ENVIRONMENT_PASSWORD                   = "password";
    private static final String DEFAULT_BROKER_CONFIG_NAME             = "default";
    private static final String DEFAULT_PASSWORD                       = "";

    // Methods to get configurations from json

    /**
     * Tries to look up the value specified under the key in the clientConfig
     * object. If the clientConfig object is no json object or no entry exists under
     * the key than the default value will be returned.
     *
     * @param clientConfig  the object to look up the referenced value
     * @param key           the key to look up the value in the json object
     * @param defaultConfig the value returned if the referenced object does not
     *                      exist
     * @return returns the referenced value if existing or else the defaultConfig
     *         object
     */
    public static JsonNode getConfigValueOrDefault(JsonNode clientConfig, String key, JsonNode defaultConfig) {
        JsonNode config;
        JsonNode configValue = getConfigValue(clientConfig, key);
        if (configValue != null) {
            config = configValue;
        } else {
            config = defaultConfig;
        }
        return config;
    }

    /**
     * Tries to look up the value specified under the key in the clientConfig
     * object. If the clientConfig object is no json object or no entry exists under
     * the key than the default value will be returned.
     *
     * @param clientConfig  the object to look up the referenced value
     * @param key           the key to look up the value in the json object
     * @param defaultConfig the value returned if the referenced object does not
     *                      exist
     * @return returns the referenced value if existing or else the defaultConfig
     *         object
     */
    public static String getConfigValueOrDefault(JsonNode clientConfig, String key, String defaultConfig) {
        String   config;
        JsonNode configValue = getConfigValue(clientConfig, key);
        if (configValue != null) {
            config = configValue.asText(defaultConfig);
        } else {
            config = defaultConfig;
        }
        return config;
    }

    /**
     * Tries to look up the value specified under the key in the clientConfig
     * object. If the clientConfig object is no json object or no entry exists under
     * the key than the default value will be returned.
     *
     * @param clientConfig  the object to look up the referenced value
     * @param key           the key to look up the value in the json object
     * @param defaultConfig the value returned if the referenced object does not
     *                      exist
     * @return returns the referenced value if existing or else the defaultConfig
     *         object
     */
    public static int getConfigValueOrDefault(JsonNode clientConfig, String key, int defaultConfig) {
        int      config;
        JsonNode configValue = getConfigValue(clientConfig, key);
        if (configValue != null) {
            config = configValue.asInt(defaultConfig);
        } else {
            config = defaultConfig;
        }
        return config;
    }

    /**
     * Tries to look up the value specified under the key in the clientConfig
     * object. If the clientConfig object is no json object or no entry exists under
     * the key than the default value will be returned.
     *
     * @param clientConfig  the object to look up the referenced value
     * @param key           the key to look up the value in the json object
     * @param defaultConfig the value returned if the referenced object does not
     *                      exist
     * @return returns the referenced value if existing or else the defaultConfig
     *         object
     */
    public static long getConfigValueOrDefault(JsonNode clientConfig, String key, long defaultConfig) {
        long     config;
        JsonNode configValue = getConfigValue(clientConfig, key);
        if (configValue != null) {
            config = configValue.asLong(defaultConfig);
        } else {
            config = defaultConfig;
        }
        return config;
    }

    /**
     * Tries to look up the value specified under the key in the clientConfig
     * object. If the clientConfig object is no json object or no entry exists under
     * the key than the default value will be returned.
     *
     * @param clientConfig  the object to look up the referenced value
     * @param key           the key to look up the value in the json object
     * @param defaultConfig the value returned if the referenced object does not
     *                      exist
     * @return returns the referenced value if existing or else the defaultConfig
     *         object
     */
    public static boolean getConfigValueOrDefault(JsonNode clientConfig, String key, boolean defaultConfig) {
        boolean  config;
        JsonNode configValue = getConfigValue(clientConfig, key);
        if (configValue != null) {
            config = configValue.asBoolean(defaultConfig);
        } else {
            config = defaultConfig;
        }
        return config;
    }

    /**
     * Looks up the referenced value in the clientConfig object. If no value exists
     * or the clientConfig object is no json object than null will be returned.
     *
     * @param clientConfig the object to look up the referenced value
     * @param key          the key referencing the searched value
     * @return the referenced value or null
     */
    public static JsonNode getConfigValue(JsonNode clientConfig, String key) {
        JsonNode value = null;
        if (clientConfig != null) {
            value = clientConfig.get(key);
        }
        return value;
    }

    // Methods to handle broker configurations

    /**
     * Evaluates the provided configuration for the mqtt broker configuration.
     *
     * @param pipMqttClientConfig    the pdp configuration
     * @param pipMqttClientConfigVal {@link Val} of the configuration in the
     *                               attribute finder
     * @return Returns a json object containing the mqtt broker config. If no valid
     *         configuration was provided in the configurations than a
     *         {@link NoSuchElementException} will be thrown.
     */
    public static ObjectNode getMqttBrokerConfig(JsonNode pipMqttClientConfig, Val pipMqttClientConfigVal) {
        // return the broker config from attribute finder param or from pdp.json
        ObjectNode mqttPipBrokerConfig = null;

        // handling broker config for pip
        if (pipMqttClientConfigVal.isObject()) { // if config is specified in policy
            mqttPipBrokerConfig = pipMqttClientConfigVal.getObjectNode();
        } else { // config in pdp.json
            mqttPipBrokerConfig = getBrokerConfigFromPdpConfig(pipMqttClientConfig, mqttPipBrokerConfig,
                    pipMqttClientConfigVal);
        }

        if (mqttPipBrokerConfig == null) {
            throw new NoSuchElementException("No valid configuration for mqtt pip client connection provided.");
        }
        return mqttPipBrokerConfig;
    }

    private static ObjectNode getBrokerConfigFromPdpConfig(JsonNode pipMqttClientConfig, ObjectNode mqttPipBrokerConfig,
            Val pipMqttClientConfigVal) {
        JsonNode brokerConfig = getConfigValue(pipMqttClientConfig, ENVIRONMENT_BROKER_CONFIG);
        if (brokerConfig != null) { // broker config is specified in pdp.json
            if (pipMqttClientConfigVal.isUndefined()) { // no broker config in attribute finder
                mqttPipBrokerConfig = getBrokerConfigIfNoConfigInAttributeFinder(mqttPipBrokerConfig,
                        pipMqttClientConfig, brokerConfig);
            } else if (pipMqttClientConfigVal.isTextual()) { // if attribute finder contains reference to
                                                             // configDescription in pdp.json
                mqttPipBrokerConfig = getBrokerConfigViaReference(mqttPipBrokerConfig, pipMqttClientConfigVal,
                        brokerConfig);
            }
        }
        return mqttPipBrokerConfig;
    }

    private static ObjectNode getBrokerConfigIfNoConfigInAttributeFinder(ObjectNode mqttPipBrokerConfig,
            JsonNode pipMqttClientConfig, JsonNode brokerConfig) {
        if (brokerConfig.isArray()) {
            mqttPipBrokerConfig = getDefaultBroker(mqttPipBrokerConfig, pipMqttClientConfig, brokerConfig);
        } else if (brokerConfig.isObject()) {
            mqttPipBrokerConfig = (ObjectNode) brokerConfig;
        }
        return mqttPipBrokerConfig;
    }

    private static ObjectNode getBrokerConfigViaReference(ObjectNode mqttPipBrokerConfig, Val pipMqttClientConfigVal,
            JsonNode brokerConfig) {
        if (brokerConfig.isArray()) {
            mqttPipBrokerConfig = getBrokerConfig(mqttPipBrokerConfig, brokerConfig, pipMqttClientConfigVal.getText());
        } else if (brokerConfig.isObject()) {
            mqttPipBrokerConfig = getBrokerConfigIfAttributeFinderReferenceMatches(mqttPipBrokerConfig,
                    pipMqttClientConfigVal, brokerConfig);
        }
        return mqttPipBrokerConfig;
    }

    private static ObjectNode getBrokerConfigIfAttributeFinderReferenceMatches(ObjectNode mqttPipBrokerConfig,
            Val pipMqttClientConfigVal, JsonNode brokerConfig) {
        JsonNode configDescription = brokerConfig.get(ENVIRONMENT_BROKER_CONFIG_NAME);
        if (configDescription != null) {
            String configDescriptionValue = configDescription.asText();
            if (configDescriptionValue.equals(pipMqttClientConfigVal.getText())) {
                mqttPipBrokerConfig = (ObjectNode) brokerConfig;
            }
        }
        return mqttPipBrokerConfig;
    }

    private static ObjectNode getDefaultBroker(ObjectNode mqttPipBrokerConfig, JsonNode pipMqttClientConfig,
            JsonNode brokerConfig) {
        // get default broker config name
        String brokerConfigName = getConfigValueOrDefault(pipMqttClientConfig, ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME,
                DEFAULT_BROKER_CONFIG_NAME);

        // get default broker config
        mqttPipBrokerConfig = getBrokerConfig(mqttPipBrokerConfig, brokerConfig, brokerConfigName);
        return mqttPipBrokerConfig;
    }

    private static ObjectNode getBrokerConfig(ObjectNode mqttPipClientConfig, JsonNode brokerConfig,
            String brokerConfigName) {
        for (JsonNode brokerConfigs : brokerConfig) {
            JsonNode configDescription = brokerConfigs.get(ENVIRONMENT_BROKER_CONFIG_NAME);
            if (configDescription != null) {
                String configDescriptionValue = configDescription.asText();
                if (configDescriptionValue.equals(brokerConfigName)) {
                    mqttPipClientConfig = (ObjectNode) brokerConfigs;
                    break;
                }
            }
        }
        return mqttPipClientConfig;
    }

    // More configuration helper functions

    /**
     * Looks up the mqtt client id.
     *
     * @param mqttClientReactor the reactive mqtt client
     * @return returns the mqtt client id or null
     */
    public static String getClientId(Mqtt5ReactorClient mqttClientReactor) {
        Optional<MqttClientIdentifier> mqttClientConfigOptional = mqttClientReactor.getConfig().getClientIdentifier();
        return mqttClientConfigOptional.map(Object::toString).orElse(null);
    }

    /**
     * Convert the provided parameter to the mqtt quality of service level.
     *
     * @param qosVal the provided qos level as a {@link Val}
     * @return returns the mqtt quality of service level
     */
    public static MqttQos getQos(Val qosVal) {
        var qos = qosVal.get().asInt();
        return MqttQos.fromCode(qos);
    }

    /**
     * Looks up the password.
     *
     * @param config the provided configuration containing the password
     * @return returns the looked up password
     */
    public static byte[] getPassword(JsonNode config) {
        String password = getConfigValueOrDefault(config, ENVIRONMENT_PASSWORD, DEFAULT_PASSWORD);
        return password.getBytes(StandardCharsets.UTF_8);
    }
}
