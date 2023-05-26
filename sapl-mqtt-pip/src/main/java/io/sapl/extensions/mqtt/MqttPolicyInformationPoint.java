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
package io.sapl.extensions.mqtt;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.Int;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;
import reactor.core.publisher.Flux;

/**
 * This policy information point allows the user to receive mqtt messages of
 * subscribed topics from a mqtt broker.
 */
@PolicyInformationPoint(name = MqttPolicyInformationPoint.NAME, description = MqttPolicyInformationPoint.DESCRIPTION)
public class MqttPolicyInformationPoint {

	static final String NAME        = "mqtt";
	static final String DESCRIPTION = "PIP for subscribing to mqtt topics.";

	private static final SaplMqttClient saplMqttClient = new SaplMqttClient();

	/**
	 * This method returns a reactive stream of mqtt messages of one or many
	 * subscribed topics. Within this method the subscription of topics is 'at most
	 * once' by default. Example for sapl attribute: {@code topic.<mqtt.messages>}
	 *
	 * @param topic  A string or array of topic(s) for subscription.
	 * @param variables Specified environment variables for configuration of the
	 *               included mqtt client.
	 * @return A {@link Flux} of messages of the subscribed topic(s).
	 */
	@Attribute(name = "messages", docs = "Subscribes to topic(s) with certain quality of service level")
	public Flux<Val> messages(@Text @Array Val topic, Map<String, JsonNode> variables) {
		return saplMqttClient.buildSaplMqttMessageFlux(topic, variables);
	}

	/**
	 * This method returns a reactive stream of mqtt messages of one or many
	 * subscribed topics. Example for sapl attribute:
	 * {@code topic.<mqtt.messages(0)>}
	 *
	 * @param topic  A string or array of topic(s) for subscription.
	 * @param variables Specified environment variables for configuration of the
	 *               included mqtt client.
	 * @param qos    The quality of service level of the mqtt subscription to the
	 *               broker. Possible values: 0, 1, 2.
	 * @return A {@link Flux} of messages of the subscribed topic(s).
	 */
	@Attribute(name = "messages", docs = "Subscribes to topic(s) with certain quality of service level")
	public Flux<Val> messages(@Text @Array Val topic, Map<String, JsonNode> variables, @Int Val qos) {
		return saplMqttClient.buildSaplMqttMessageFlux(topic, variables, qos);
	}

	/**
	 * This method returns a reactive stream of mqtt messages of one or many
	 * subscribed topics. Example for sapl attribute:
	 * {@code topic.<mqtt.messages(0, resource.mqttPipConfig)>}
	 *
	 * @param topic         A string or array of topic(s) for subscription.
	 * @param variables        Specified environment variables for configuration of the
	 *                      included mqtt client.
	 * @param qos           The quality of service level of the mqtt subscription to
	 *                      the broker. Possible values: 0, 1, 2.
	 * @param mqttPipConfig An {@link ArrayNode} of {@link ObjectNode}s or only a
	 *                      single {@link ObjectNode} containing configurations for
	 *                      the pip as a mqtt client. Each {@link ObjectNode}
	 *                      specifies the configuration of a single mqtt client.
	 *                      Therefore, it is possible for the pip to build multiple
	 *                      mqtt clients, that is the pip can subscribe to topics by
	 *                      different brokers.
	 * @return A {@link Flux} of messages of the subscribed topic(s).
	 */
	@Attribute(name = "messages", docs = "Subscribes to topic(s) with certain quality of service level")
	public Flux<Val> messages(@Text @Array Val topic, Map<String, JsonNode> variables, @Int Val qos,
			@Text @Array @JsonObject Val mqttPipConfig) {
		return saplMqttClient.buildSaplMqttMessageFlux(topic, variables, qos, mqttPipConfig);
	}
}