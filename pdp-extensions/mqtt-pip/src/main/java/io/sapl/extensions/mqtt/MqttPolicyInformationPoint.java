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
package io.sapl.extensions.mqtt;

import io.sapl.api.attributes.Attribute;
import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.Value;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * Policy Information Point for subscribing to MQTT topics and receiving
 * messages
 * from MQTT brokers.
 */
@RequiredArgsConstructor
@PolicyInformationPoint(name = MqttPolicyInformationPoint.NAME, description = MqttPolicyInformationPoint.DESCRIPTION, pipDocumentation = MqttPolicyInformationPoint.DOCUMENTATION)
public class MqttPolicyInformationPoint {

    static final String NAME          = "mqtt";
    static final String DESCRIPTION   = "Policy Information Point for subscribing to MQTT topics.";
    static final String DOCUMENTATION = """
            This Policy Information Point subscribes to MQTT topics and returns messages from
            MQTT brokers as a reactive stream of attribute values.

            ## Attribute Invocation

            The `mqtt.messages` attribute accepts up to three parameters:

            | Policy syntax | Meaning |
            |---|---|
            | `topic.<mqtt.messages>` | Subscribe with QoS 0, using the default broker. |
            | `topic.<mqtt.messages(qos)>` | Subscribe with the given QoS level, using the default broker. |
            | `topic.<mqtt.messages(qos, "staging")>` | Subscribe with the given QoS, selecting the broker named `"staging"` from the `brokerConfig` array. |
            | `topic.<mqtt.messages(qos, { ... })>` | Subscribe with the given QoS, using the inline object as broker configuration. |

            The topic can be a single topic string or an array of topic strings.

            ## Quality of Service Levels

            MQTT QoS levels determine message delivery guarantees:
            * QoS 0: At most once - fire and forget, no acknowledgment
            * QoS 1: At least once - acknowledged delivery, possible duplicates
            * QoS 2: Exactly once - assured delivery, no duplicates

            ## Broker Configuration

            Configure the PIP through the `mqttPipConfig` SAPL environment variable in `pdp.json`.

            Top-level settings:
            * `brokerConfig`: A single broker configuration object, or an array of named broker
              configuration objects for multi-broker setups
            * `defaultBrokerConfigName`: The `name` of the broker to use when no broker is
              specified in the policy (defaults to `"default"`)
            * `defaultResponse`: Response when no messages arrive before timeout --
              `"undefined"` or `"error"` (defaults to `"undefined"`)
            * `defaultResponseTimeout`: Timeout in milliseconds before emitting the default
              response (defaults to 1000)
            * `emitAtRetry`: Emit value on reconnection -- `"true"` or `"false"` (defaults to `"false"`)

            Each broker configuration object contains:
            * `name`: Broker identifier used for broker selection and secrets matching (see below)
            * `brokerAddress`: Hostname or IP address of the MQTT broker
            * `brokerPort`: Port number of the MQTT broker
            * `clientId`: Unique identifier for the MQTT client connection

            Example `pdp.json` with two named brokers:
            ```json
            {
              "variables": {
                "mqttPipConfig": {
                  "defaultBrokerConfigName": "production",
                  "brokerConfig": [
                    {
                      "name": "production",
                      "brokerAddress": "mqtt.example.com",
                      "brokerPort": 1883,
                      "clientId": "sapl-prod"
                    },
                    {
                      "name": "staging",
                      "brokerAddress": "mqtt-staging.example.com",
                      "brokerPort": 1883,
                      "clientId": "sapl-staging"
                    }
                  ]
                }
              }
            }
            ```

            ## Broker Selection

            When the policy does not specify a broker (e.g. `topic.<mqtt.messages>`):
            1. The PDP reads `defaultBrokerConfigName` from `mqttPipConfig`.
               If not set, the default name is `"default"`.
            2. The `brokerConfig` array is searched for a broker whose `name` matches.
            3. If `brokerConfig` is a single object (not an array), it is used directly
               without name matching.

            When the policy specifies a broker name (e.g. `topic.<mqtt.messages(1, "staging")>`):
            1. The `brokerConfig` array is searched for a broker whose `name` matches `"staging"`.
            2. If no match is found, an error is returned.

            When the policy provides an inline broker object
            (e.g. `topic.<mqtt.messages(1, { "brokerAddress": "...", ... })>`):
            1. The inline object is used directly as the broker configuration.

            ## Secrets Configuration

            Broker credentials are sourced exclusively from the `secrets` section in `pdp.json`.
            They are never read from broker configuration objects or policy parameters.

            The broker `name` field is the join key between the broker configuration and the
            secrets. For a broker with `"name": "staging"`, the PDP looks up
            `secrets.mqtt.staging` for that broker's credentials.

            Credential resolution order:
            1. **Per-broker secrets**: If the resolved broker config has a `name` field, look
               for `secrets.mqtt.<name>` (e.g. `secrets.mqtt.production`).
            2. **Flat secrets**: If no per-broker match, check whether `secrets.mqtt` directly
               contains `username`/`password` fields.
            3. **Anonymous**: If no secrets are found at all, connect with empty credentials.

            Multi-broker secrets example:
            ```json
            {
              "secrets": {
                "mqtt": {
                  "production": { "username": "prod-user", "password": "prod-secret" },
                  "staging": { "username": "staging-user", "password": "staging-secret" }
                }
              }
            }
            ```

            Single-broker or flat secrets example (used when no per-broker key matches):
            ```json
            {
              "secrets": {
                "mqtt": { "username": "sapl-user", "password": "secure-password" }
              }
            }
            ```

            ## Complete pdp.json Example

            ```json
            {
              "variables": {
                "mqttPipConfig": {
                  "defaultBrokerConfigName": "production",
                  "brokerConfig": [
                    { "name": "production", "brokerAddress": "mqtt.example.com", "brokerPort": 1883, "clientId": "sapl-prod" },
                    { "name": "staging", "brokerAddress": "mqtt-staging.example.com", "brokerPort": 1883, "clientId": "sapl-staging" }
                  ]
                }
              },
              "secrets": {
                "mqtt": {
                  "production": { "username": "prod-user", "password": "prod-secret" },
                  "staging": { "username": "staging-user", "password": "staging-secret" }
                }
              }
            }
            ```

            With this configuration:
            * `"sensors/#".<mqtt.messages>` connects to `mqtt.example.com` as `prod-user`
              (default broker is `"production"`).
            * `"sensors/#".<mqtt.messages(1, "staging")>` connects to `mqtt-staging.example.com`
              as `staging-user`.
            * `"sensors/#".<mqtt.messages(1, { "brokerAddress": "other.host", "brokerPort": 1883, "clientId": "custom" })>`
              connects to `other.host` with flat secrets fallback (or anonymous if no flat secrets).

            ## Message Format

            Received messages are automatically converted based on their MQTT payload format:
            * Messages with content type `application/json` are parsed as JSON values
            * UTF-8 encoded text messages are returned as text values
            * Binary payloads are returned as arrays of byte values (as integers)

            ## Topic Wildcards

            The PIP supports MQTT topic wildcards for flexible subscriptions:
            * `+` - Single-level wildcard (matches one topic level)
            * `#` - Multi-level wildcard (matches zero or more topic levels, must be last)

            Examples:
            * `sensors/+/temperature` matches `sensors/room1/temperature` and `sensors/room2/temperature`
            * `building/#` matches `building/floor1/room1` and `building/floor2/room3/sensor5`

            ## Reconnection Behavior

            The PIP automatically handles broker reconnection in case of connection loss.
            When reconnection occurs, the PIP re-subscribes to all active topics and continues
            emitting messages.
            """;

    private final SaplMqttClient saplMqttClient;

    /**
     * Subscribes to MQTT topics and emits received messages as a reactive stream.
     * Uses QoS level 0 (at most once) by default.
     *
     * @param topic A topic string or array of topic strings to subscribe to.
     * @param ctx the attribute access context containing MQTT broker configuration.
     * @return A reactive stream of messages from the subscribed topics.
     */
    @Attribute(name = "messages", docs = """
            Subscribes to MQTT topics and emits messages as they arrive. Uses QoS level 0
            (at most once) by default.

            Accepts a single topic string or an array of topic strings. MQTT wildcards work
            in topic filters.

            Example with single topic:
            ```sapl
            policy "single_temperature_sensor"
            permit
              "home/livingroom/temperature".<mqtt.messages>.celsius > 22.0;
            ```

            Example with multiple topics:
            ```sapl
            policy "multiple_sensors"
            permit
              var topics = ["sensors/temperature", "sensors/humidity"];
              topics.<mqtt.messages> != undefined;
            ```

            Example with single-level wildcard:
            ```sapl
            policy "all_room_temperatures"
            permit
              "building/+/temperature".<mqtt.messages>.value > 25.0;
            ```

            Example with multi-level wildcard:
            ```sapl
            policy "all_building_sensors"
            permit
              "building/#".<mqtt.messages>.alert == true;
            ```
            """)
    public Flux<Value> messages(Value topic, AttributeAccessContext ctx) {
        return saplMqttClient.buildSaplMqttMessageFlux(topic, ctx);
    }

    /**
     * Subscribes to MQTT topics with a specified Quality of Service level and emits
     * received messages as a reactive stream.
     *
     * @param topic A topic string or array of topic strings to subscribe to.
     * @param ctx the attribute access context containing MQTT broker configuration.
     * @param qos The Quality of Service level (0, 1, or 2).
     * @return A reactive stream of messages from the subscribed topics.
     */
    @Attribute(name = "messages", docs = """
            Subscribes to MQTT topics with a specified Quality of Service level.

            QoS levels and their trade-offs:
            * QoS 0: At most once - fastest but may lose messages
            * QoS 1: At least once - acknowledged delivery, may receive duplicates
            * QoS 2: Exactly once - slowest but guaranteed

            Example with QoS 1 for reliable monitoring:
            ```sapl
            policy "critical_alarm_monitoring"
            permit
              "alarms/critical".<mqtt.messages(1)>.severity == "HIGH";
            ```

            Example with QoS 2 for command processing:
            ```sapl
            policy "device_command_processing"
            permit
              var commandTopics = ["device/shutdown", "device/restart"];
              commandTopics.<mqtt.messages(2)>.confirmed == true;
            ```

            Example with QoS 0 for high-frequency sensor data:
            ```sapl
            policy "sensor_stream"
            permit
              "sensors/motion/#".<mqtt.messages(0)> != undefined;
            ```
            """)
    public Flux<Value> messages(Value topic, AttributeAccessContext ctx, Value qos) {
        return saplMqttClient.buildSaplMqttMessageFlux(topic, ctx, qos);
    }

    /**
     * Subscribes to MQTT topics with specified Quality of Service level and custom
     * broker configuration. Supports connecting to multiple MQTT brokers
     * simultaneously or overriding broker settings for specific subscriptions.
     *
     * @param topic A topic string or array of topic strings to subscribe to.
     * @param ctx the attribute access context containing MQTT broker configuration.
     * @param qos The Quality of Service level (0, 1, or 2).
     * @param mqttPipConfig Configuration object, array of configuration objects, or
     * broker name reference.
     * @return A reactive stream of messages from the subscribed topics.
     */
    @Attribute(name = "messages", docs = """
            Subscribes to MQTT topics with custom broker configuration.

            Reference a specific broker by name or provide an inline configuration. Supports
            multi-broker scenarios and per-subscription broker overrides.

            The `mqttPipConfig` parameter accepts:
            * A string referencing a broker configuration by name
            * A broker configuration object with properties: `brokerAddress`, `brokerPort`, `clientId`
            * An array of broker configuration objects for multi-broker subscriptions

            Example referencing a broker by name:
            ```sapl
            policy "staging_environment_monitoring"
            permit
              var topics = ["sensors/data", "actuators/status"];
              topics.<mqtt.messages(1, "staging")>.operational == true;
            ```

            Example with inline broker configuration:
            ```sapl
            policy "custom_broker_connection"
            permit
              var brokerConfig = {
                  "brokerAddress": "mqtt.internal.example.com",
                  "brokerPort": 1883,
                  "clientId": "policy-specific-client"
              };
              "devices/status".<mqtt.messages(1, brokerConfig)>.online == true;
            ```

            Example with multiple brokers:
            ```sapl
            policy "distributed_mqtt_network"
            permit
              var brokers = [
                  {
                      "name": "datacenter1",
                      "brokerAddress": "mqtt-dc1.example.com",
                      "brokerPort": 1883,
                      "clientId": "sapl-dc1"
                  },
                  {
                      "name": "datacenter2",
                      "brokerAddress": "mqtt-dc2.example.com",
                      "brokerPort": 1883,
                      "clientId": "sapl-dc2"
                  }
              ];
              "sensors/#".<mqtt.messages(2, brokers)>.status == "OK";
            ```
            """)
    public Flux<Value> messages(Value topic, AttributeAccessContext ctx, Value qos, Value mqttPipConfig) {
        return saplMqttClient.buildSaplMqttMessageFlux(topic, ctx, qos, mqttPipConfig);
    }
}
