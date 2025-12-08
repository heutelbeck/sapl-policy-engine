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
package io.sapl.extensions.mqtt;

import io.sapl.api.attributes.Attribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.Value;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

import java.util.Map;

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

            Subscribe to single or multiple topics with configurable Quality of Service levels
            and broker configurations.

            ## Quality of Service Levels

            MQTT QoS levels determine message delivery guarantees:
            * QoS 0: At most once - fire and forget, no acknowledgment
            * QoS 1: At least once - acknowledged delivery, possible duplicates
            * QoS 2: Exactly once - assured delivery, no duplicates

            ## Configuration

            Configure the PIP through the SAPL environment variables. The `mqttPipConfig`
            variable contains:

            * `brokerConfig`: Single object or array of broker configuration objects
            * `defaultBrokerConfigName`: Default broker configuration name (optional)
            * `defaultResponse`: Default response when no messages arrive - "undefined" or "error" (defaults to "undefined")
            * `defaultResponseTimeout`: Timeout in milliseconds before emitting default response (defaults to 1000ms)
            * `emitAtRetry`: Emit value on reconnection - "true" or "false" (defaults to "false")

            Each broker configuration object contains:
            * `name`: Broker configuration identifier (optional)
            * `brokerAddress`: Hostname or IP address of the MQTT broker
            * `brokerPort`: Port number of the MQTT broker
            * `clientId`: Unique identifier for the MQTT client connection
            * `username`: Username for broker authentication (optional, defaults to empty string)
            * `password`: Password for broker authentication (optional, defaults to empty string)

            Configuration example without authentication:
            ```json
            {
              "defaultBrokerConfigName": "production",
              "defaultResponse": "undefined",
              "defaultResponseTimeout": 5000,
              "emitAtRetry": "false",
              "brokerConfig": [
                {
                  "name": "production",
                  "brokerAddress": "mqtt.example.com",
                  "brokerPort": 1883,
                  "clientId": "sapl-client-prod"
                },
                {
                  "name": "staging",
                  "brokerAddress": "mqtt-staging.example.com",
                  "brokerPort": 1883,
                  "clientId": "sapl-client-staging"
                }
              ]
            }
            ```

            Configuration example with authentication:
            ```json
            {
              "defaultBrokerConfigName": "production",
              "brokerConfig": {
                "name": "production",
                "brokerAddress": "mqtt.example.com",
                "brokerPort": 1883,
                "clientId": "sapl-client-prod",
                "username": "sapl-user",
                "password": "secure-password"
              }
            }
            ```

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

            ## Example Policy

            ```sapl
            policy "temperature_monitoring"
            permit
                action == "monitor"
            where
                var sensors = ["sensors/room1/temp", "sensors/room2/temp"];
                sensors.<mqtt.messages>.celsius < 30.0;
            ```

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
     * @param variables SAPL environment variables containing the MQTT broker
     * configuration in the `mqttPipConfig` variable.
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
            where
              "home/livingroom/temperature".<mqtt.messages>.celsius > 22.0;
            ```

            Example with multiple topics:
            ```sapl
            policy "multiple_sensors"
            permit
            where
              var topics = ["sensors/temperature", "sensors/humidity"];
              topics.<mqtt.messages> != undefined;
            ```

            Example with single-level wildcard:
            ```sapl
            policy "all_room_temperatures"
            permit
            where
              "building/+/temperature".<mqtt.messages>.value > 25.0;
            ```

            Example with multi-level wildcard:
            ```sapl
            policy "all_building_sensors"
            permit
            where
              "building/#".<mqtt.messages>.alert == true;
            ```
            """)
    public Flux<Value> messages(Value topic, Map<String, Value> variables) {
        return saplMqttClient.buildSaplMqttMessageFlux(topic, variables);
    }

    /**
     * Subscribes to MQTT topics with a specified Quality of Service level and emits
     * received messages as a reactive stream.
     *
     * @param topic A topic string or array of topic strings to subscribe to.
     * @param variables SAPL environment variables containing the MQTT broker
     * configuration in the `mqttPipConfig` variable.
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
            where
              "alarms/critical".<mqtt.messages(1)>.severity == "HIGH";
            ```

            Example with QoS 2 for command processing:
            ```sapl
            policy "device_command_processing"
            permit
            where
              var commandTopics = ["device/shutdown", "device/restart"];
              commandTopics.<mqtt.messages(2)>.confirmed == true;
            ```

            Example with QoS 0 for high-frequency sensor data:
            ```sapl
            policy "sensor_stream"
            permit
            where
              "sensors/motion/#".<mqtt.messages(0)> != undefined;
            ```
            """)
    public Flux<Value> messages(Value topic, Map<String, Value> variables, Value qos) {
        return saplMqttClient.buildSaplMqttMessageFlux(topic, variables, qos);
    }

    /**
     * Subscribes to MQTT topics with specified Quality of Service level and custom
     * broker configuration. Supports connecting to multiple MQTT brokers
     * simultaneously or overriding broker settings for specific subscriptions.
     *
     * @param topic A topic string or array of topic strings to subscribe to.
     * @param variables SAPL environment variables containing the MQTT broker
     * configuration in the `mqttPipConfig` variable.
     * @param qos The Quality of Service level (0, 1, or 2).
     * @param mqttPipConfig Configuration object, array of configuration objects, or
     * broker name reference. Can be a direct broker configuration object, an array
     * of broker configurations for multi-broker scenarios, or a string referencing
     * a broker by name from the variables' mqttPipConfig.
     * @return A reactive stream of messages from the subscribed topics.
     */
    @Attribute(name = "messages", docs = """
            Subscribes to MQTT topics with custom broker configuration.

            Reference a specific broker by name or provide an inline configuration. Supports
            multi-broker scenarios and per-subscription broker overrides.

            The `mqttPipConfig` parameter accepts:
            * A string referencing a broker configuration by name
            * A broker configuration object with properties: `brokerAddress`, `brokerPort`, `clientId`, optional `username`, optional `password`
            * An array of broker configuration objects for multi-broker subscriptions

            Example referencing a broker by name:
            ```sapl
            policy "staging_environment_monitoring"
            permit
            where
              var topics = ["sensors/data", "actuators/status"];
              topics.<mqtt.messages(1, "staging")>.operational == true;
            ```

            Example with inline broker configuration:
            ```sapl
            policy "custom_broker_connection"
            permit
            where
              var brokerConfig = {
                  "brokerAddress": "mqtt.internal.example.com",
                  "brokerPort": 1883,
                  "clientId": "policy-specific-client",
                  "username": "device-monitor",
                  "password": "secure-token"
              };
              "devices/status".<mqtt.messages(1, brokerConfig)>.online == true;
            ```

            Example with multiple brokers:
            ```sapl
            policy "distributed_mqtt_network"
            permit
            where
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
    public Flux<Value> messages(Value topic, Map<String, Value> variables, Value qos, Value mqttPipConfig) {
        return saplMqttClient.buildSaplMqttMessageFlux(topic, variables, qos, mqttPipConfig);
    }
}
