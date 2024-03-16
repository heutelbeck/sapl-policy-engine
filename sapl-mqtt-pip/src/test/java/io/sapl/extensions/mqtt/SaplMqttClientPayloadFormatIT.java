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
package io.sapl.extensions.mqtt;

import static io.sapl.extensions.mqtt.MqttTestUtility.buildAndStartBroker;
import static io.sapl.extensions.mqtt.MqttTestUtility.buildVariables;
import static io.sapl.extensions.mqtt.MqttTestUtility.startClient;
import static io.sapl.extensions.mqtt.MqttTestUtility.stopBroker;
import static io.sapl.extensions.mqtt.util.PayloadFormatUtility.convertBytesToArrayNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PayloadFormatIndicator;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.embedded.EmbeddedHiveMQ;

import io.sapl.api.interpreter.Val;
import reactor.test.StepVerifier;

class SaplMqttClientPayloadFormatIT {

    private static final String          BYTE_ARRAY_TOPIC = "byteArrayTopic";
    private static final String          JSON_TOPIC       = "jsonTopic";
    private static final long            DELAY_MS         = 1000L;
    private static final JsonNodeFactory JSON             = JsonNodeFactory.instance;

    @TempDir
    Path configDir;

    @TempDir
    Path dataDir;

    @TempDir
    Path extensionsDir;

    EmbeddedHiveMQ      mqttBroker;
    Mqtt5BlockingClient mqttClient;
    SaplMqttClient      saplMqttClient;

    @BeforeEach
    void beforeEach() {
        this.mqttBroker     = buildAndStartBroker(configDir, dataDir, extensionsDir);
        this.mqttClient     = startClient();
        this.saplMqttClient = new SaplMqttClient();
    }

    @AfterEach
    void afterEach() {
        mqttClient.disconnect();
        stopBroker(mqttBroker);
    }

    @Test
    @Timeout(15)
    void when_mqttMessageContentTypeIsJson_then_getValOfJson() {
        // GIVEN
        var topic       = JSON.arrayNode().add(JSON_TOPIC);
        var jsonMessage = JSON.arrayNode().add("message1").add(JSON.objectNode().put("key", "value"));

        // WHEN
        var saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topic), buildVariables())
                .filter(val -> !val.isUndefined());

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(2 * DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishJsonMessage(jsonMessage)))
                .expectNextMatches((value) -> value.get().equals(jsonMessage)).thenCancel().verify();
    }

    @Test
    @Timeout(10)
    void when_inconsistentMqttMessageIsPublished_then_getValOfError() {
        // GIVEN
        var topic       = JSON.arrayNode().add("topic");
        var jsonMessage = "{test}";
        var mqttMessage = Mqtt5Publish.builder().topic("topic").qos(MqttQos.AT_MOST_ONCE).retain(true)
                .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8)
                .payload(jsonMessage.getBytes(StandardCharsets.UTF_8)).contentType("application/json").build();

        // WHEN
        var saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topic), buildVariables())
                .filter(val -> !val.isUndefined());

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(2 * DELAY_MS))
                .then(() -> mqttClient.publish(mqttMessage))
                .expectNextMatches((value) -> Objects.equals(value.getValType(), Val.error(null, null).getValType()))
                .thenCancel().verify();
    }

    @Test
    @Timeout(10)
    void when_mqttMessagePayloadIsFormatIsByteArray_then_getArrayOfBytesAsIntegers() {
        // GIVEN
        var topic   = JSON.arrayNode().add(BYTE_ARRAY_TOPIC);
        var message = "byteArray";

        // WHEN
        var saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topic), buildVariables())
                .filter(val -> !val.isUndefined());

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(2 * DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishByteArrayMessageWithIndicator(message)))
                .expectNextMatches((valueArray) -> valueArray.get()
                        .equals(convertBytesToArrayNode(message.getBytes(StandardCharsets.UTF_8))))
                .thenCancel().verify();
    }

    @Test
    @Timeout(10)
    void when_mqttMessagePayloadIsUtf8EncodedAndNoFormatIndicatorSet_then_getPayloadAsText() {
        // GIVEN
        var topic   = JSON.arrayNode().add(BYTE_ARRAY_TOPIC);
        var message = "byteArray";

        // WHEN
        var saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topic), buildVariables())
                .filter(val -> !val.isUndefined());

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(1500))
                .then(() -> mqttClient.publish(buildMqttPublishByteArrayMessageWithoutIndicator(message)))
                .expectNextMatches((valueArray) -> valueArray.get().textValue().equals(message)).thenCancel().verify();
    }

    @Test
    @Timeout(10)
    void when_mqttMessagePayloadIsNonValidUtf8EncodedAndNoFormatIndicatorSet_then_getPayloadAsBytes() {
        // GIVEN
        var topic   = JSON.arrayNode().add(BYTE_ARRAY_TOPIC);
        var message = "ßß";

        // WHEN
        var saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topic), buildVariables())
                .filter(val -> !val.isUndefined());

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(1500))
                .then(() -> mqttClient
                        .publish(buildMqttPublishByteArrayMessageWithoutIndicatorAndNoValidEncoding(message)))
                .expectNextMatches((valueArray) -> valueArray.get()
                        .equals(convertBytesToArrayNode(message.getBytes(StandardCharsets.UTF_16))))
                .thenCancel().verify();
    }

    private static Mqtt5Publish buildMqttPublishJsonMessage(JsonNode payload) {
        return Mqtt5Publish.builder().topic(JSON_TOPIC).qos(MqttQos.AT_MOST_ONCE).retain(true)
                .payload(payload.toString().getBytes(StandardCharsets.UTF_8))
                .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8).contentType("application/json").build();
    }

    private static Mqtt5Publish buildMqttPublishByteArrayMessageWithIndicator(String payload) {
        return Mqtt5Publish.builder().topic(BYTE_ARRAY_TOPIC).qos(MqttQos.AT_MOST_ONCE).retain(true)
                .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UNSPECIFIED)
                .payload(payload.getBytes(StandardCharsets.UTF_8)).build();
    }

    private static Mqtt5Publish buildMqttPublishByteArrayMessageWithoutIndicator(String payload) {
        return Mqtt5Publish.builder().topic(BYTE_ARRAY_TOPIC).qos(MqttQos.AT_MOST_ONCE).retain(true)
                .payload(payload.getBytes(StandardCharsets.UTF_8)).build();
    }

    private static Mqtt5Publish buildMqttPublishByteArrayMessageWithoutIndicatorAndNoValidEncoding(String payload) {
        return Mqtt5Publish.builder().topic(BYTE_ARRAY_TOPIC).qos(MqttQos.AT_MOST_ONCE).retain(true)
                .payload(payload.getBytes(StandardCharsets.UTF_16)).build();
    }
}
