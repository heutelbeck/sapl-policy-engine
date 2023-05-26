/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.extensions.mqtt.SaplMqttClient.ENVIRONMENT_BROKER_ADDRESS;
import static io.sapl.extensions.mqtt.SaplMqttClient.ENVIRONMENT_BROKER_PORT;
import static io.sapl.extensions.mqtt.SaplMqttClient.ENVIRONMENT_CLIENT_ID;
import static io.sapl.extensions.mqtt.util.ConfigUtility.ENVIRONMENT_BROKER_CONFIG;
import static io.sapl.extensions.mqtt.util.ConfigUtility.ENVIRONMENT_BROKER_CONFIG_NAME;
import static io.sapl.extensions.mqtt.util.ConfigUtility.ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME;
import static io.sapl.extensions.mqtt.util.ErrorUtility.ENVIRONMENT_ERROR_RETRY_ATTEMPTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.Val;

class ConfigUtilityTest {

	private static final String ENVIRONMENT_DEFAULT_MESSAGE = "defaultMessage";

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Test
	void when_jsonValueIsSpecifiedInMqttClientConfig_then_returnJsonValue() {
		// GIVEN
		var defaultConfig = JSON.nullNode();
		var mqttPipConfig = JSON.objectNode()
				.set(ENVIRONMENT_DEFAULT_MESSAGE, JSON.objectNode()
						.put("key", "value"));

		// WHEN
		var config = ConfigUtility.getConfigValueOrDefault(mqttPipConfig,
				ENVIRONMENT_DEFAULT_MESSAGE, defaultConfig);

		// THEN
		assertEquals("value", config.get("key").asText());
	}

	@Test
	void when_jsonValueIsNotSpecifiedInMqttClientConfig_then_returnDefaultJsonValue() {
		// GIVEN
		var defaultConfig = JSON.objectNode()
				.put("key", "value");
		var mqttPipConfig = JSON.objectNode();

		// WHEN
		var config = ConfigUtility.getConfigValueOrDefault(mqttPipConfig,
				ENVIRONMENT_DEFAULT_MESSAGE, defaultConfig);

		// THEN
		assertEquals("value", config.get("key").asText());
	}

	@Test
	void when_longValueIsSpecifiedInMqttClientConfig_then_returnLongValue() {
		// GIVEN
		var defaultConfig = 5;
		var mqttPipConfig = JSON.objectNode()
				.put(ENVIRONMENT_ERROR_RETRY_ATTEMPTS, 6)
				.put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
				.set(ENVIRONMENT_BROKER_CONFIG, JSON.arrayNode()
						.add(JSON.objectNode()
								.put(ENVIRONMENT_BROKER_CONFIG_NAME, "production")
								.put(ENVIRONMENT_BROKER_ADDRESS, "localhost")
								.put(ENVIRONMENT_BROKER_PORT, 1883)
								.put(ENVIRONMENT_CLIENT_ID, "mqttPipDefault")));

		// WHEN
		var config = ConfigUtility.getConfigValueOrDefault(mqttPipConfig,
				ENVIRONMENT_ERROR_RETRY_ATTEMPTS, defaultConfig);

		// THEN
		assertEquals(6, config);
	}

	@Test
	void when_getMqttBrokerConfigIsCalledWithEmptyMqttClientConfig_then_throwNoSuchElementException() {
		// GIVEN
		var undefined = Val.UNDEFINED;

		// THEN
		assertThrowsExactly(NoSuchElementException.class,
				() -> ConfigUtility.getMqttBrokerConfig(null, undefined));
	}

	@Test
	void when_gettingMqttBrokerConfigAndNoConfigParamsAndEmptyConfigInPdpConfig_then_throwException() {
		// GIVEN
		var mqttPipConfig = JSON.objectNode()
				.put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
				.set(ENVIRONMENT_BROKER_CONFIG, JSON.nullNode());

		// THEN
		assertThrowsExactly(NoSuchElementException.class,
				() -> ConfigUtility.getMqttBrokerConfig(mqttPipConfig, Val.UNDEFINED));
	}

	@Test
	void when_gettingMqttBrokerConfigAndNoReferenceInConfigParamsAndEmptyConfigInPdpConfig_then_throwException() {
		// GIVEN
		var mqttPipConfig = JSON.objectNode()
				.put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
				.set(ENVIRONMENT_BROKER_CONFIG, JSON.nullNode());

		// THEN
		assertThrowsExactly(NoSuchElementException.class,
				() -> ConfigUtility.getMqttBrokerConfig(mqttPipConfig, Val.FALSE));
	}

	@Test
	void when_gettingMqttBrokerConfigAndReferencingEmptyPdpConfig_then_throwException() {
		// GIVEN
		var mqttPipConfig = JSON.objectNode()
				.put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
				.set(ENVIRONMENT_BROKER_CONFIG, JSON.nullNode());

		// THEN
		assertThrowsExactly(NoSuchElementException.class,
				() -> ConfigUtility.getMqttBrokerConfig(mqttPipConfig, Val.of("reference")));
	}

	@Test
	void when_gettingMqttBrokerConfigAndBrokerConfigNameDoesNotEqualTheReferencedConfiguration_then_throwException() {
		// GIVEN
		var mqttPipConfig = JSON.objectNode()
				.put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
				.set(ENVIRONMENT_BROKER_CONFIG, JSON.objectNode()
						.put(ENVIRONMENT_BROKER_CONFIG_NAME, "production")
						.put(ENVIRONMENT_BROKER_ADDRESS, "localhost")
						.put(ENVIRONMENT_BROKER_PORT, 1883)
						.put(ENVIRONMENT_CLIENT_ID, "mqttPipDefault"));

		// THEN
		assertThrowsExactly(NoSuchElementException.class,
				() -> ConfigUtility.getMqttBrokerConfig(mqttPipConfig, Val.of("reference")));
	}

	@Test
	void when_gettingMqttBrokerConfigAndReferenceViaParamsButNoReferenceSpecifiedInPdpConfigArray_then_throwException() {
		// GIVEN
		var mqttPipConfig = JSON.objectNode()
				.put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
				.set(ENVIRONMENT_BROKER_CONFIG, JSON.objectNode()
						.put(ENVIRONMENT_BROKER_ADDRESS, "localhost")
						.put(ENVIRONMENT_BROKER_PORT, 1883)
						.put(ENVIRONMENT_CLIENT_ID, "mqttPipDefault"));

		// THEN
		assertThrowsExactly(NoSuchElementException.class,
				() -> ConfigUtility.getMqttBrokerConfig(mqttPipConfig, Val.of("reference")));
	}

	@Test
	void when_gettingMqttBrokerConfigAndReferenceIsSpecifiedInParamsButNoReferenceSpecifiedInPdpConfigObject_then_throwException() {
		// GIVEN
		var mqttPipConfig = JSON.objectNode()
				.put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
				.set(ENVIRONMENT_BROKER_CONFIG, JSON.arrayNode()
						.add(JSON.objectNode()
								.put(ENVIRONMENT_BROKER_ADDRESS, "localhost")
								.put(ENVIRONMENT_BROKER_PORT, 1883)
								.put(ENVIRONMENT_CLIENT_ID, "mqttPipDefault")));

		// THEN
		assertThrowsExactly(NoSuchElementException.class,
				() -> ConfigUtility.getMqttBrokerConfig(mqttPipConfig, Val.UNDEFINED));
	}

	@Test
	void when_configAsArrayInPdpJsonIsReferencedViaAttributeFinder_then_getReferencedConfig() {
		// GIVEN
		var mqttPipConfig = JSON.objectNode()
				.put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
				.set(ENVIRONMENT_BROKER_CONFIG, JSON.arrayNode()
						.add(JSON.objectNode()
								.put(ENVIRONMENT_BROKER_CONFIG_NAME, "production")
								.put(ENVIRONMENT_BROKER_ADDRESS, "localhost")
								.put(ENVIRONMENT_BROKER_PORT, 1883)
								.put(ENVIRONMENT_CLIENT_ID, "mqttPipDefault"))
						.add(JSON.objectNode()
								.put(ENVIRONMENT_BROKER_CONFIG_NAME, "broker2")
								.put(ENVIRONMENT_BROKER_ADDRESS, "localhost")
								.put(ENVIRONMENT_BROKER_PORT, 1883)
								.put(ENVIRONMENT_CLIENT_ID, "broker2")));
		var brokerConfig  = Val.of("broker2");

		// WHEN
		var config = ConfigUtility.getMqttBrokerConfig(mqttPipConfig, brokerConfig);

		// THEN
		assertEquals("broker2", config.get(ENVIRONMENT_CLIENT_ID).asText());
	}

	@Test
	void when_configAsObjectInPdpJsonIsReferencedViaAttributeFinder_then_getReferencedConfig() {
		// GIVEN
		var mqttPipConfig = JSON.objectNode()
				.put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
				.set(ENVIRONMENT_BROKER_CONFIG, JSON.objectNode()
						.put(ENVIRONMENT_BROKER_CONFIG_NAME, "broker2")
						.put(ENVIRONMENT_BROKER_ADDRESS, "localhost")
						.put(ENVIRONMENT_BROKER_PORT, 1883)
						.put(ENVIRONMENT_CLIENT_ID, "broker2"));
		var brokerConfig  = Val.of("broker2");

		// WHEN
		var config = ConfigUtility.getMqttBrokerConfig(mqttPipConfig, brokerConfig);

		// THEN
		assertEquals("broker2", config.get(ENVIRONMENT_CLIENT_ID).asText());
	}

	@Test
	void when_configAsObjectInPdpJsonAndNoAttributeFinderParam_then_getConfigFromPdpJson() {
		// GIVEN
		var undefined     = Val.UNDEFINED;
		var mqttPipConfig = JSON.objectNode()
				.put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
				.set(ENVIRONMENT_BROKER_CONFIG, JSON.objectNode()
						.put(ENVIRONMENT_BROKER_CONFIG_NAME, "production")
						.put(ENVIRONMENT_BROKER_ADDRESS, "localhost")
						.put(ENVIRONMENT_BROKER_PORT, 1883)
						.put(ENVIRONMENT_CLIENT_ID, "production"));

		// WHEN
		var config = ConfigUtility.getMqttBrokerConfig(mqttPipConfig, undefined);

		// THEN
		assertEquals("production", config.get(ENVIRONMENT_CLIENT_ID).asText());
	}

}