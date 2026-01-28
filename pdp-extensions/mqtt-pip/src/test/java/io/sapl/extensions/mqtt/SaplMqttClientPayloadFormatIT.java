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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PayloadFormatIndicator;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.embedded.EmbeddedHiveMQ;
import io.sapl.api.model.*;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.extensions.mqtt.MqttTestUtility.*;
import static io.sapl.extensions.mqtt.util.PayloadFormatUtility.convertBytesToArrayValue;

class SaplMqttClientPayloadFormatIT {

    private static final String          BYTE_ARRAY_TOPIC = "byteArrayTopic";
    private static final String          JSON_TOPIC       = "jsonTopic";
    private static final long            DELAY_MS         = 1000L;
    private static final JsonNodeFactory JSON_FACTORY     = JsonNodeFactory.instance;

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
    void when_mqttMessageContentTypeIsJson_then_getValueOfJson() {
        // GIVEN
        val topic       = ArrayValue.builder().add(Value.of(JSON_TOPIC)).build();
        val jsonMessage = JSON_FACTORY.arrayNode().add("message1").add(JSON_FACTORY.objectNode().put("key", "value"));

        // WHEN
        val saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(topic, buildVariables())
                .filter(value -> !(value instanceof UndefinedValue));

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(2 * DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishJsonMessage(jsonMessage)))
                .expectNextMatches(value -> ValueJsonMarshaller.toJsonNode(value).equals(jsonMessage)).thenCancel()
                .verify();
    }

    @Test
    @Timeout(10)
    void when_inconsistentMqttMessageIsPublished_then_getValueOfError() {
        // GIVEN
        val topic       = json("[\"topic\"]");
        val jsonMessage = "{test}";
        val mqttMessage = Mqtt5Publish.builder().topic("topic").qos(MqttQos.AT_MOST_ONCE).retain(true)
                .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8)
                .payload(jsonMessage.getBytes(StandardCharsets.UTF_8)).contentType("application/json").build();

        // WHEN
        val saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(topic, buildVariables())
                .filter(value -> !(value instanceof UndefinedValue));

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(2 * DELAY_MS))
                .then(() -> mqttClient.publish(mqttMessage)).expectNextMatches(ErrorValue.class::isInstance)
                .thenCancel().verify();
    }

    @Test
    @Timeout(10)
    void when_mqttMessagePayloadIsFormatIsByteArray_then_getArrayOfBytesAsIntegers() {
        // GIVEN
        val topic   = ArrayValue.builder().add(Value.of(BYTE_ARRAY_TOPIC)).build();
        val message = "byteArray";

        // WHEN
        val saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(topic, buildVariables())
                .filter(value -> !(value instanceof UndefinedValue));

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(2 * DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishByteArrayMessageWithIndicator(message)))
                .expectNextMatches(valueArray -> valueArray
                        .equals(convertBytesToArrayValue(message.getBytes(StandardCharsets.UTF_8))))
                .thenCancel().verify();
    }

    @Test
    @Timeout(10)
    void when_mqttMessagePayloadIsUtf8EncodedAndNoFormatIndicatorSet_then_getPayloadAsText() {
        // GIVEN
        val topic   = ArrayValue.builder().add(Value.of(BYTE_ARRAY_TOPIC)).build();
        val message = "byteArray";

        // WHEN
        val saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(topic, buildVariables())
                .filter(value -> !(value instanceof UndefinedValue));

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(1500))
                .then(() -> mqttClient.publish(buildMqttPublishByteArrayMessageWithoutIndicator(message)))
                .expectNextMatches(value -> value instanceof TextValue textValue && message.equals(textValue.value()))
                .thenCancel().verify();
    }

    @Test
    @Timeout(10)
    void when_mqttMessagePayloadIsNonValidUtf8EncodedAndNoFormatIndicatorSet_then_getPayloadAsBytes() {
        // GIVEN
        val topic   = ArrayValue.builder().add(Value.of(BYTE_ARRAY_TOPIC)).build();
        val message = "ßß";

        // WHEN
        val saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(topic, buildVariables())
                .filter(value -> !(value instanceof UndefinedValue));

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(1500))
                .then(() -> mqttClient
                        .publish(buildMqttPublishByteArrayMessageWithoutIndicatorAndNoValidEncoding(message)))
                .expectNextMatches(valueArray -> valueArray
                        .equals(convertBytesToArrayValue(message.getBytes(StandardCharsets.UTF_16))))
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
