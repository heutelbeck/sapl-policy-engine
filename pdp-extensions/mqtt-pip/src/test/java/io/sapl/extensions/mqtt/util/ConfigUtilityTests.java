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

import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.extensions.mqtt.SaplMqttClient;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("ConfigUtility")
class ConfigUtilityTests {

    private static final String          ENVIRONMENT_DEFAULT_MESSAGE = "defaultMessage";
    private static final JsonNodeFactory JSON                        = JsonNodeFactory.instance;

    private static ObjectNode brokerEntry(String name, String address, int port, String clientId) {
        return JSON.objectNode().put(ConfigUtility.ENVIRONMENT_BROKER_CONFIG_NAME, name)
                .put(SaplMqttClient.ENVIRONMENT_BROKER_ADDRESS, address)
                .put(SaplMqttClient.ENVIRONMENT_BROKER_PORT, port).put(SaplMqttClient.ENVIRONMENT_CLIENT_ID, clientId);
    }

    @Nested
    @DisplayName("getConfigValueOrDefault")
    class ConfigValueLookup {

        @Test
        @DisplayName("when key present in config then existing JSON value returned")
        void whenJsonKeyPresentThenReturnsExistingValue() {
            val defaultConfig = JSON.nullNode();
            val mqttConfig    = JSON.objectNode().set(ENVIRONMENT_DEFAULT_MESSAGE,
                    JSON.objectNode().put("key", "value"));

            val config = ConfigUtility.getConfigValueOrDefault(mqttConfig, ENVIRONMENT_DEFAULT_MESSAGE, defaultConfig);

            assertThat(config.get("key").asString()).isEqualTo("value");
        }

        @Test
        @DisplayName("when key absent from config then default JSON value returned")
        void whenJsonKeyAbsentThenReturnsDefault() {
            val defaultConfig = JSON.objectNode().put("key", "value");
            val mqttConfig    = JSON.objectNode();

            val config = ConfigUtility.getConfigValueOrDefault(mqttConfig, ENVIRONMENT_DEFAULT_MESSAGE, defaultConfig);

            assertThat(config.get("key").asString()).isEqualTo("value");
        }

        @Test
        @DisplayName("when long key present in config then existing long value returned")
        void whenLongKeyPresentThenReturnsExistingValue() {
            val mqttConfig = JSON.objectNode().put("someLongKey", 6L);

            val config = ConfigUtility.getConfigValueOrDefault(mqttConfig, "someLongKey", 5L);

            assertThat(config).isEqualTo(6L);
        }
    }

    @Nested
    @DisplayName("getMqttBrokerConfig: invalid inputs")
    class InvalidBrokerConfig {

        static Stream<Arguments> invalidBrokerConfigCases() {
            val nullBrokerConfig             = JSON.objectNode()
                    .put(ConfigUtility.ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
                    .set(ConfigUtility.ENVIRONMENT_BROKER_CONFIG, JSON.nullNode());
            val arrayWithoutMatchingName     = JSON.objectNode()
                    .put(ConfigUtility.ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
                    .set(ConfigUtility.ENVIRONMENT_BROKER_CONFIG,
                            JSON.arrayNode()
                                    .add(JSON.objectNode().put(SaplMqttClient.ENVIRONMENT_BROKER_ADDRESS, "localhost")
                                            .put(SaplMqttClient.ENVIRONMENT_BROKER_PORT, 1883)
                                            .put(SaplMqttClient.ENVIRONMENT_CLIENT_ID, "mqttPipDefault")));
            val objectWithMismatchedName     = JSON.objectNode()
                    .put(ConfigUtility.ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
                    .set(ConfigUtility.ENVIRONMENT_BROKER_CONFIG,
                            brokerEntry("production", "localhost", 1883, "mqttPipDefault"));
            val objectWithoutNameButPropsSet = JSON.objectNode()
                    .put(ConfigUtility.ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
                    .set(ConfigUtility.ENVIRONMENT_BROKER_CONFIG,
                            JSON.objectNode().put(SaplMqttClient.ENVIRONMENT_BROKER_ADDRESS, "localhost")
                                    .put(SaplMqttClient.ENVIRONMENT_BROKER_PORT, 1883)
                                    .put(SaplMqttClient.ENVIRONMENT_CLIENT_ID, "mqttPipDefault"));

            return Stream.of(arguments("null pdp-config and undefined param", null, Value.UNDEFINED),
                    arguments("null brokerConfig with undefined param", nullBrokerConfig, Value.UNDEFINED),
                    arguments("null brokerConfig with non-text param", nullBrokerConfig, Value.FALSE),
                    arguments("null brokerConfig with text reference", nullBrokerConfig, Value.of("reference")),
                    arguments("array brokerConfig with no matching name", arrayWithoutMatchingName, Value.UNDEFINED),
                    arguments("object brokerConfig with mismatched name", objectWithMismatchedName,
                            Value.of("reference")),
                    arguments("object brokerConfig without name field referenced by text", objectWithoutNameButPropsSet,
                            Value.of("reference")));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidBrokerConfigCases")
        @DisplayName("throws NoSuchElementException")
        void whenBrokerConfigInvalidThenThrowsNoSuchElement(String description, ObjectNode pdpConfig,
                Value attributeFinderParam) {
            assertThatThrownBy(() -> ConfigUtility.getMqttBrokerConfig(pdpConfig, attributeFinderParam))
                    .isExactlyInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("getMqttBrokerConfig: resolution paths")
    class BrokerConfigResolution {

        @ParameterizedTest(name = "{0}")
        @MethodSource("selectors")
        @DisplayName("broker selection uses the endpoint map key")
        void whenSelectingBrokerThenUsesNamedEntry(String description, String defaultName, Value selector,
                String expected) {
            val brokers = JSON.objectNode();
            brokers.set("default", JSON.objectNode().put("clientId", "default"));
            brokers.set("production", JSON.objectNode().put("clientId", "production"));
            val mqtt = JSON.objectNode().set("brokers", brokers);
            if (defaultName != null) {
                mqtt.put("default", defaultName);
            }
            assertThat(ConfigUtility.getMqttBrokerConfig(mqtt, selector).get("clientId").asString())
                    .isEqualTo(expected);
        }

        static Stream<Arguments> selectors() {
            return Stream.of(arguments("explicit broker", null, Value.of("production"), "production"),
                    arguments("operator default", "production", Value.UNDEFINED, "production"),
                    arguments("implicit default", null, Value.UNDEFINED, "default"));
        }
    }

    @Nested
    @DisplayName("getQos")
    class GetQos {

        @Test
        @DisplayName("a non-number qos is rejected with a domain error, not a class cast")
        void whenQosIsNotANumberThenThrows() {
            val notANumber = Value.of("two");

            assertThatThrownBy(() -> ConfigUtility.getQos(notANumber)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a fractional qos is rejected instead of being truncated to a valid level")
        void whenQosIsFractionalThenThrows() {
            val fractionalQos = Value.of(BigDecimal.valueOf(1.5));

            assertThatThrownBy(() -> ConfigUtility.getQos(fractionalQos)).isInstanceOf(ArithmeticException.class);
        }

        @Test
        @DisplayName("a qos beyond the int range is rejected instead of overflowing into a valid level")
        void whenQosExceedsIntRangeThenThrows() {
            val outOfRangeQos = Value.of(BigDecimal.valueOf(Integer.MAX_VALUE).add(BigDecimal.ONE));

            assertThatThrownBy(() -> ConfigUtility.getQos(outOfRangeQos)).isInstanceOf(ArithmeticException.class);
        }
    }
}
