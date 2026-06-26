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

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PayloadFormatIndicator;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Test helpers for the MQTT IT: a Testcontainer-managed Eclipse
 * Mosquitto MQTT broker, MQTT publish helpers, and a small client
 * builder.
 */
@UtilityClass
class MqttTestUtility {

    static final String CLIENT_ID       = "SAPL_MQTT_TEST_PUBLISHER";
    static final String MOSQUITTO_IMAGE = "eclipse-mosquitto:2.0";

    public static GenericContainer<?> newMosquittoContainer() {
        return new GenericContainer<>(DockerImageName.parse(MOSQUITTO_IMAGE)).withExposedPorts(1883)
                .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf")
                .waitingFor(Wait.forLogMessage(".*mosquitto version.*", 1).withStartupTimeout(Duration.ofMinutes(2L)));
    }

    /**
     * A Mosquitto broker with a plaintext listener on 1883 (for the test
     * publisher) and a TLS listener on 8883 (for the PIP under test). The
     * server certificate and key are the committed self-signed test material
     * under {@code src/test/resources/tls}.
     *
     * @return the configured, not-yet-started container
     */
    public static GenericContainer<?> newTlsMosquittoContainer() {
        return new GenericContainer<>(DockerImageName.parse(MOSQUITTO_IMAGE)).withExposedPorts(1883, 8883)
                .withCopyFileToContainer(MountableFile.forClasspathResource("tls/mosquitto-tls.conf"),
                        "/mosquitto/config/mosquitto.conf")
                .withCopyFileToContainer(MountableFile.forClasspathResource("tls/server.crt"),
                        "/mosquitto/certs/server.crt")
                .withCopyFileToContainer(MountableFile.forClasspathResource("tls/server.key"),
                        "/mosquitto/certs/server.key")
                .withCommand("mosquitto", "-c", "/mosquitto/config/mosquitto.conf")
                .waitingFor(Wait.forLogMessage(".*mosquitto version.*", 1).withStartupTimeout(Duration.ofMinutes(2L)));
    }

    public static Mqtt5BlockingClient startPublisher(String host, int port) {
        val mqttClient     = Mqtt5Client.builder().identifier(CLIENT_ID).serverHost(host).serverPort(port)
                .buildBlocking();
        val connAckMessage = mqttClient.connect();
        if (connAckMessage.getReasonCode() != Mqtt5ConnAckReasonCode.SUCCESS) {
            throw new IllegalStateException(
                    "Connection to the mqtt broker couldn't be established: " + connAckMessage.getReasonCode());
        }
        return mqttClient;
    }

    public static Mqtt5Publish buildMqttPublishMessage(String topic, String payload, boolean retain) {
        return Mqtt5Publish.builder().topic(topic).qos(MqttQos.AT_MOST_ONCE).retain(retain)
                .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8)
                .payload(payload.getBytes(StandardCharsets.UTF_8)).build();
    }
}
