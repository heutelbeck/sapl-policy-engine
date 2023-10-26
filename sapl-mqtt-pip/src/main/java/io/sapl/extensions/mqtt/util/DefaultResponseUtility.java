/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.extensions.mqtt.util.ConfigUtility.getConfigValueOrDefault;
import static io.sapl.extensions.mqtt.util.ConfigUtility.getMqttBrokerConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.Val;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * This utility class provides functions to build the
 * {@link DefaultResponseConfig} and the default response.
 */
@Slf4j
@UtilityClass
public class DefaultResponseUtility {

    /**
     * The reference for the default response setting in configurations.
     */
    public static final String  ENVIRONMENT_DEFAULT_RESPONSE         = "defaultResponse";
    /**
     * The reference for the default response timeout in configurations.
     */
    public static final String  ENVIRONMENT_DEFAULT_RESPONSE_TIMEOUT = "timeoutDuration"; // initial value will be
                                                                                          // published after timeout
    private static final String DEFAULT_RESPONSE_TYPE                = "undefined";
    private static final long   DEFAULT_RESPONSE_TIMEOUT             = 2000;              // in milliseconds

    /**
     * Build the {@link DefaultResponseConfig} of the provided configuration.
     * 
     * @param pipMqttClientConfig the pdp configuration
     * @param pipConfigParams     the configuration provided in the attribute finder
     * @return returns the build {@link DefaultResponseConfig}
     */
    public static DefaultResponseConfig getDefaultResponseConfig(JsonNode pipMqttClientConfig, Val pipConfigParams) {
        // broker config from attribute finder or broker config in pdp.json
        var mqttBrokerConfig       = getMqttBrokerConfig(pipMqttClientConfig, pipConfigParams);
        var defaultResponseType    = getDefaultResponseType(pipMqttClientConfig, mqttBrokerConfig);
        var defaultResponseTimeout = getDefaultResponseTimeout(pipMqttClientConfig, mqttBrokerConfig);

        return new DefaultResponseConfig(defaultResponseTimeout, defaultResponseType);
    }

    /**
     * Build the {@link Val} for the default response.
     * 
     * @param defaultResponseConfig the provided configuration
     * @return returns the {@link Val} for the default response
     */
    public static Val getDefaultVal(DefaultResponseConfig defaultResponseConfig) {
        String defaultResponseType = defaultResponseConfig.getDefaultResponseType();

        if (!(DEFAULT_RESPONSE_TYPE.equals(defaultResponseType) || "error".equals(defaultResponseType))) {
            log.debug("The specified default response type '{}' is illegal. The default configuration will"
                    + "be used: '{}'", defaultResponseType, DEFAULT_RESPONSE_TYPE);
            defaultResponseType = DEFAULT_RESPONSE_TYPE;
        }

        if (DEFAULT_RESPONSE_TYPE.equals(defaultResponseType)) {
            return Val.UNDEFINED;
        } else {
            return Val.error("The sapl mqtt pip has not received any mqtt message yet.");
        }
    }

    private static String getDefaultResponseType(JsonNode pipMqttClientConfig, ObjectNode mqttBrokerConfig) {
        String defaultResponseType;
        if (mqttBrokerConfig.has(ENVIRONMENT_DEFAULT_RESPONSE)) {
            defaultResponseType = mqttBrokerConfig.get(ENVIRONMENT_DEFAULT_RESPONSE).asText();
        } else {
            defaultResponseType = getConfigValueOrDefault(pipMqttClientConfig, ENVIRONMENT_DEFAULT_RESPONSE,
                    DEFAULT_RESPONSE_TYPE);
        }
        return defaultResponseType;
    }

    private static long getDefaultResponseTimeout(JsonNode pipMqttClientConfig, ObjectNode mqttBrokerConfig) {
        long defaultResponseTimeout;
        if (mqttBrokerConfig.has(ENVIRONMENT_DEFAULT_RESPONSE_TIMEOUT)) {
            defaultResponseTimeout = mqttBrokerConfig.get(ENVIRONMENT_DEFAULT_RESPONSE_TIMEOUT).asLong();
        } else {
            defaultResponseTimeout = getConfigValueOrDefault(pipMqttClientConfig, ENVIRONMENT_DEFAULT_RESPONSE_TIMEOUT,
                    DEFAULT_RESPONSE_TIMEOUT);
        }
        return defaultResponseTimeout;
    }
}
