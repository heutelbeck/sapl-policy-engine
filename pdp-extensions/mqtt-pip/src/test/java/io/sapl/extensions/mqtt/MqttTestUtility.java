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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PayloadFormatIndicator;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.configuration.service.InternalConfigurations;
import com.hivemq.embedded.EmbeddedHiveMQ;
import com.hivemq.migration.meta.PersistenceType;

import io.sapl.api.interpreter.Val;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
class MqttTestUtility {

    static final String       CLIENT_ID   = "SAPL_MQTT_CLIENT";
    static final String       BROKER_HOST = "localhost";
    static final int          BROKER_PORT = 1883;
    static final ObjectMapper MAPPER      = new ObjectMapper();

    public static EmbeddedHiveMQ buildBroker(Path configDir, Path dataDir, Path extensionsDir) {
        InternalConfigurations.PAYLOAD_PERSISTENCE_TYPE.set(PersistenceType.FILE);
        InternalConfigurations.RETAINED_MESSAGE_PERSISTENCE_TYPE.set(PersistenceType.FILE);
        InternalConfigurations.PERSISTENCE_SHUTDOWN_GRACE_PERIOD_MSEC.set(100);
        InternalConfigurations.PERSISTENCE_SHUTDOWN_GRACE_PERIOD_MSEC.set(1000);
        InternalConfigurations.PERSISTENCE_CLOSE_RETRIES.set(1);
        InternalConfigurations.PERSISTENCE_CLOSE_RETRY_INTERVAL_MSEC.set(1000);

        return EmbeddedHiveMQ.builder().withConfigurationFolder(configDir).withDataFolder(dataDir)
                .withExtensionsFolder(extensionsDir).build();
    }

    @SneakyThrows
    public static EmbeddedHiveMQ startBroker(EmbeddedHiveMQ broker) {
        broker.start().get();
        return broker;
    }

    public static EmbeddedHiveMQ buildAndStartBroker(Path configDir, Path dataDir, Path extensionsDir) {
        return startBroker(buildBroker(configDir, dataDir, extensionsDir));
    }

    public static void stopBroker(EmbeddedHiveMQ broker) {
        try {
            broker.stop().get();
            broker.close();
        } catch (ExecutionException | IllegalStateException | InterruptedException e) {
            // NOP ignore if broker already closed
        }
    }

    public static Mqtt5BlockingClient startClient() {
        final var mqttClient     = Mqtt5Client.builder().identifier(CLIENT_ID).serverHost(BROKER_HOST)
                .serverPort(BROKER_PORT).buildBlocking();
        final var connAckMessage = mqttClient.connect();
        if (connAckMessage.getReasonCode() != Mqtt5ConnAckReasonCode.SUCCESS) {
            throw new IllegalStateException(
                    "Connection to the mqtt broker couldn't be established:" + connAckMessage.getReasonCode());
        }
        return mqttClient;
    }

    @SneakyThrows
    public static Val defaultMqttPipConfig() {
        return Val.ofJson("""
                {
                  "defaultBrokerConfigName" : "production",
                  "emitAtRetry" : "false",
                  "brokerConfig" : [ {
                    "name" : "production",
                    "brokerAddress" : "localhost",
                    "brokerPort" : 1883,
                    "clientId" : "mqttPipDefault"
                  } ]
                }
                """);
    }

    public static Map<String, Val> buildVariables() {
        return Map.of("action", Val.NULL, "environment", Val.NULL, "mqttPipConfig", defaultMqttPipConfig(), "resource",
                Val.NULL, "subject", Val.NULL);
    }

    public static Mqtt5Publish buildMqttPublishMessage(String topic, String payload, boolean retain) {
        return Mqtt5Publish.builder().topic(topic).qos(MqttQos.AT_MOST_ONCE).retain(retain)
                .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8)
                .payload(payload.getBytes(StandardCharsets.UTF_8)).build();
    }

}
