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
package io.sapl.extensions.mqtt.util;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.hivemq.client.mqtt.datatypes.MqttClientIdentifier;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.NoSuchElementException;
import java.util.Optional;

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
    private static final String DEFAULT_BROKER_CONFIG_NAME             = "default";

    private static final String ERROR_INVALID_QOS              = "The mqtt quality of service level must be a number.";
    private static final String ERROR_NO_VALID_MQTT_PIP_CONFIG = "No valid configuration for mqtt pip client connection provided.";

    /**
     * Tries to look up the value specified under the key in the clientConfig
     * object. If the clientConfig object is no json object or no entry exists under
     * the key than the default value will be returned.
     *
     * @param clientConfig the object to look up the referenced value
     * @param key the key to look up the value in the json object
     * @param defaultConfig the value returned if the referenced object does not
     * exist
     * @return returns the referenced value if existing or else the defaultConfig
     * object
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
     * @param clientConfig the object to look up the referenced value
     * @param key the key to look up the value in the json object
     * @param defaultConfig the value returned if the referenced object does not
     * exist
     * @return returns the referenced value if existing or else the defaultConfig
     * object
     */
    public static String getConfigValueOrDefault(JsonNode clientConfig, String key, String defaultConfig) {
        String   config;
        JsonNode configValue = getConfigValue(clientConfig, key);
        if (configValue != null) {
            config = configValue.asString(defaultConfig);
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
     * @param clientConfig the object to look up the referenced value
     * @param key the key to look up the value in the json object
     * @param defaultConfig the value returned if the referenced object does not
     * exist
     * @return returns the referenced value if existing or else the defaultConfig
     * object
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
     * @param clientConfig the object to look up the referenced value
     * @param key the key to look up the value in the json object
     * @param defaultConfig the value returned if the referenced object does not
     * exist
     * @return returns the referenced value if existing or else the defaultConfig
     * object
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
     * @param clientConfig the object to look up the referenced value
     * @param key the key to look up the value in the json object
     * @param defaultConfig the value returned if the referenced object does not
     * exist
     * @return returns the referenced value if existing or else the defaultConfig
     * object
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
     * @param key the key referencing the searched value
     * @return the referenced value or null
     */
    public static JsonNode getConfigValue(JsonNode clientConfig, String key) {
        JsonNode value = null;
        if (clientConfig != null) {
            value = clientConfig.get(key);
        }
        return value;
    }

    /**
     * Resolves the mqtt broker configuration to use. The policy-supplied attribute
     * finder parameter may only select a broker by name (a
     * {@link io.sapl.api.model.TextValue}) or be absent for the default. Inline
     * broker configuration objects from a policy are rejected earlier, at the
     * attribute boundary, so they never reach this method.
     *
     * @param pipMqttClientConfig the operator pdp configuration
     * @param pipMqttClientConfigVal the policy-supplied broker selector
     * @return the resolved broker configuration object
     * @throws NoSuchElementException if no matching broker configuration is found
     */
    public static ObjectNode getMqttBrokerConfig(JsonNode pipMqttClientConfig, Value pipMqttClientConfigVal) {
        // Inline broker objects are rejected at the attribute boundary. Only
        // name/default reach here.
        val mqttPipBrokerConfig = getBrokerConfigFromPdpConfig(pipMqttClientConfig, null, pipMqttClientConfigVal);

        if (mqttPipBrokerConfig == null) {
            throw new NoSuchElementException(ERROR_NO_VALID_MQTT_PIP_CONFIG);
        }
        return mqttPipBrokerConfig;
    }

    private static ObjectNode getBrokerConfigFromPdpConfig(JsonNode pipMqttClientConfig, ObjectNode mqttPipBrokerConfig,
            Value pipMqttClientConfigVal) {
        JsonNode brokerConfig = getConfigValue(pipMqttClientConfig, ENVIRONMENT_BROKER_CONFIG);
        if (brokerConfig != null) {
            if (pipMqttClientConfigVal instanceof UndefinedValue) {
                mqttPipBrokerConfig = getBrokerConfigIfNoConfigInAttributeFinder(mqttPipBrokerConfig,
                        pipMqttClientConfig, brokerConfig);
            } else if (pipMqttClientConfigVal instanceof TextValue) {
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

    private static ObjectNode getBrokerConfigViaReference(ObjectNode mqttPipBrokerConfig, Value pipMqttClientConfigVal,
            JsonNode brokerConfig) {
        var configName = ((TextValue) pipMqttClientConfigVal).value();
        if (brokerConfig.isArray()) {
            mqttPipBrokerConfig = getBrokerConfig(mqttPipBrokerConfig, brokerConfig, configName);
        } else if (brokerConfig.isObject()) {
            mqttPipBrokerConfig = getBrokerConfigIfAttributeFinderReferenceMatches(mqttPipBrokerConfig, configName,
                    brokerConfig);
        }
        return mqttPipBrokerConfig;
    }

    private static ObjectNode getBrokerConfigIfAttributeFinderReferenceMatches(ObjectNode mqttPipBrokerConfig,
            String configName, JsonNode brokerConfig) {
        JsonNode configDescription = brokerConfig.get(ENVIRONMENT_BROKER_CONFIG_NAME);
        if (configDescription != null) {
            val configDescriptionValue = configDescription.asString();
            if (configDescriptionValue.equals(configName)) {
                mqttPipBrokerConfig = (ObjectNode) brokerConfig;
            }
        }
        return mqttPipBrokerConfig;
    }

    private static ObjectNode getDefaultBroker(ObjectNode mqttPipBrokerConfig, JsonNode pipMqttClientConfig,
            JsonNode brokerConfig) {
        String brokerConfigName = getConfigValueOrDefault(pipMqttClientConfig, ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME,
                DEFAULT_BROKER_CONFIG_NAME);
        mqttPipBrokerConfig = getBrokerConfig(mqttPipBrokerConfig, brokerConfig, brokerConfigName);
        return mqttPipBrokerConfig;
    }

    private static ObjectNode getBrokerConfig(ObjectNode mqttPipClientConfig, JsonNode brokerConfig,
            String brokerConfigName) {
        for (JsonNode brokerConfigs : brokerConfig) {
            JsonNode configDescription = brokerConfigs.get(ENVIRONMENT_BROKER_CONFIG_NAME);
            if (configDescription != null) {
                val configDescriptionValue = configDescription.asString();
                if (configDescriptionValue.equals(brokerConfigName)) {
                    mqttPipClientConfig = (ObjectNode) brokerConfigs;
                    break;
                }
            }
        }
        return mqttPipClientConfig;
    }

    /**
     * Looks up the mqtt client id.
     *
     * @param mqttClient the async mqtt client
     * @return returns the mqtt client id or null
     */
    public static String getClientId(Mqtt5AsyncClient mqttClient) {
        Optional<MqttClientIdentifier> mqttClientConfigOptional = mqttClient.getConfig().getClientIdentifier();
        return mqttClientConfigOptional.map(Object::toString).orElse(null);
    }

    /**
     * Convert the provided parameter to the mqtt quality of service level.
     *
     * @param qosVal the provided qos level as a {@link Value}
     * @return returns the mqtt quality of service level
     */
    public static MqttQos getQos(Value qosVal) {
        if (qosVal instanceof NumberValue number) {
            return MqttQos.fromCode(number.value().intValueExact());
        }
        throw new IllegalArgumentException(ERROR_INVALID_QOS);
    }

}
