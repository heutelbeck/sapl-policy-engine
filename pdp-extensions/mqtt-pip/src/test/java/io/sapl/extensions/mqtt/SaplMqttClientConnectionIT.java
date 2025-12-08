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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.embedded.EmbeddedHiveMQ;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.extensions.mqtt.MqttTestUtility.*;

class SaplMqttClientConnectionIT {

    private static final ObjectMapper MAPPER   = new ObjectMapper();
    private static final long         DELAY_MS = 1000L;
    private static final Mqtt5Publish MESSAGE  = buildMqttPublishMessage("topic", "message", false);
    private static final Value        TOPIC    = Value.of("topic");

    @TempDir
    Path configDir;

    @TempDir
    Path dataDir;

    @TempDir
    Path extensionsDir;

    @TempDir
    Path secondaryConfigDir;

    @TempDir
    Path secondaryDataDir;

    @TempDir
    Path secondaryExtensionsDir;

    EmbeddedHiveMQ mqttBroker;
    SaplMqttClient saplMqttClient;

    @BeforeEach
    void beforeEach() {
        this.mqttBroker     = buildAndStartBroker(configDir, dataDir, extensionsDir);
        this.saplMqttClient = new SaplMqttClient();
    }

    @AfterEach
    void afterEach() {
        stopBroker(mqttBroker);
    }

    @Test
    void when_brokerConfigIsInvalid_then_returnValueOfError() {
        // GIVEN
        val mqttPipConfigForUndefinedVal = json("""
                {
                  "defaultBrokerConfigName" : "falseName",
                  "brokerConfig" : [ {
                    "name" : "production",
                    "brokerAddress" : "localhost",
                    "brokerPort" : 1883,
                    "clientId" : "mqttPipDefault"
                  } ]
                }
                """);

        val configForUndefinedVal = Map.of("action", Value.NULL, "environment", Value.NULL, "mqttPipConfig",
                mqttPipConfigForUndefinedVal, "resource", Value.NULL, "subject", Value.NULL);

        // WHEN
        val testSaplMqttClient  = new SaplMqttClient();
        val saplMqttMessageFlux = testSaplMqttClient.buildSaplMqttMessageFlux(TOPIC, configForUndefinedVal);

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(DELAY_MS))
                .expectNextMatches(value -> value instanceof ErrorValue).thenCancel().verify();
    }

    @Test
    @Timeout(45)
    void when_noConfigIsSpecified_then_returnValueOfError() {
        // WHEN
        val emptyPdpConfig      = Map.<String, Value>of();
        val testSaplMqttClient  = new SaplMqttClient();
        val saplMqttMessageFlux = testSaplMqttClient.buildSaplMqttMessageFlux(TOPIC, emptyPdpConfig);

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(DELAY_MS))
                .expectNextMatches(value -> value instanceof ErrorValue).thenCancel().verify();
    }

    @Test
    @Timeout(45)
    void when_connectionIsShared_then_bothMessageFluxWorking() {
        // GIVEN
        val saplMqttMessageFluxFirst  = saplMqttClient.buildSaplMqttMessageFlux(Value.of("topic1"), buildVariables());
        val saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(Value.of("topic2"), buildVariables());

        // WHEN
        val saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond);
        val mqttClient               = startClient();

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge).thenAwait(Duration.ofMillis(DELAY_MS))
                .expectNext(Value.UNDEFINED, Value.UNDEFINED)
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Value.of("message1"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", false)))
                .expectNext(Value.of("message2"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Value.of("message1")).thenCancel().verify();
        mqttClient.disconnect();
    }

    @Test
    @Timeout(45)
    void when_connectionIsNotSharedAnymore_then_singleFluxWorking() {
        // GIVEN
        val saplMqttMessageFluxFirst  = saplMqttClient.buildSaplMqttMessageFlux(Value.of("topic1"), buildVariables());
        val saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(Value.of("topic2"), buildVariables())
                .takeUntil(
                        message -> message instanceof TextValue textValue && "lastMessage".equals(textValue.value()));

        // WHEN
        val saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond);
        val mqttClient               = startClient();

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge).thenAwait(Duration.ofMillis(DELAY_MS))
                .expectNext(Value.UNDEFINED, Value.UNDEFINED)
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Value.of("message1"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", false)))
                .expectNext(Value.of("message2"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "lastMessage", false)))
                .expectNext(Value.of("lastMessage")).thenAwait(Duration.ofMillis(DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", false)))
                .expectNoEvent(Duration.ofMillis(DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Value.of("message1")).thenCancel().verify();
        mqttClient.disconnect();
    }

    @Test
    @Timeout(45)
    void when_connectionIsNotSharedAnymoreAndThenSharedAgain_then_bothMessageFluxWorking() {
        // GIVEN
        val saplMqttMessageFluxFirst  = saplMqttClient.buildSaplMqttMessageFlux(Value.of("topic1"), buildVariables());
        val saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(Value.of("topic2"), buildVariables())
                .takeUntil(
                        message -> message instanceof TextValue textValue && "lastMessage".equals(textValue.value()));
        val testPublisher             = TestPublisher.create();

        // WHEN
        val saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond,
                saplMqttMessageFluxSecond.delaySubscription(testPublisher.flux()));
        val mqttClient               = startClient();

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge).expectNext(Value.UNDEFINED, Value.UNDEFINED)
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Value.of("message1"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", false)))
                .expectNext(Value.of("message2"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "lastMessage", false)))
                .expectNext(Value.of("lastMessage")).thenAwait(Duration.ofMillis(DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Value.of("message1")).then(() -> testPublisher.emit("subscribe"))
                .thenAwait(Duration.ofMillis(DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", false)))
                .expectNext(Value.of("message2"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Value.of("message1")).thenCancel().verify();
        mqttClient.disconnect();
    }

    @Test
    @Timeout(45)
    void when_brokerConnectionLost_then_reconnectToBroker() {
        // WHEN
        val secondaryBroker     = buildBroker(secondaryConfigDir, secondaryDataDir, secondaryExtensionsDir);
        val mqttClient          = startClient();
        val saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(TOPIC, buildVariables());

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(DELAY_MS))
                .then(() -> mqttClient.publish(MESSAGE)).expectNext(Value.of("message"))

                .then(mqttClient::disconnect).thenAwait(Duration.ofMillis(DELAY_MS))

                .then(() -> stopBroker(mqttBroker)).thenAwait(Duration.ofMillis(5 * DELAY_MS))
                .then(() -> startBroker(secondaryBroker)).thenAwait(Duration.ofMillis(2 * DELAY_MS))

                .then(mqttClient::connect).thenAwait(Duration.ofMillis(5 * DELAY_MS))

                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic", "message", true)))
                .expectNext(Value.of("message")).thenCancel().verify();

        mqttClient.disconnect();
        stopBroker(secondaryBroker);
    }

    @Test
    @Timeout(45)
    void when_brokerConnectionLostWhileSharingConnection_then_reconnectToBroker() {
        // GIVEN
        val saplMqttMessageFluxFirst  = saplMqttClient.buildSaplMqttMessageFlux(Value.of("topic1"), buildVariables());
        val saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(Value.of("topic2"), buildVariables());

        // WHEN
        val saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond);

        val secondaryBroker = buildBroker(secondaryConfigDir, secondaryDataDir, secondaryExtensionsDir);
        val mqttClient      = startClient();

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge).thenAwait(Duration.ofMillis(DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Value.of("message1"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", false)))
                .expectNext(Value.of("message2"))

                .then(mqttClient::disconnect).thenAwait(Duration.ofMillis(DELAY_MS))

                .then(() -> stopBroker(mqttBroker)).thenAwait(Duration.ofMillis(5 * DELAY_MS))
                .then(() -> startBroker(secondaryBroker)).thenAwait(Duration.ofMillis(2 * DELAY_MS))

                .then(mqttClient::connect).thenAwait(Duration.ofMillis(5 * DELAY_MS))

                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", true)))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", true)))
                .expectNextMatches(value -> value instanceof TextValue textValue
                        && ("message2".equals(textValue.value()) || "message1".equals(textValue.value())))
                .expectNextMatches(value -> value instanceof TextValue textValue
                        && ("message2".equals(textValue.value()) || "message1".equals(textValue.value())))
                .thenCancel().verify();

        mqttClient.disconnect();
        stopBroker(secondaryBroker);
    }

    @Test
    @Timeout(45)
    void when_sharedReconnectToBroker_then_getMessagesOfMultipleTopics() {
        // GIVEN
        val topicsFirstFlux  = MAPPER.createArrayNode().add("topic1").add("topic2");
        val topicsSecondFlux = MAPPER.createArrayNode().add("topic2").add("topic3");

        val saplMqttMessageFluxFirst  = saplMqttClient
                .buildSaplMqttMessageFlux(ValueJsonMarshaller.fromJsonNode(topicsFirstFlux), buildVariables());
        val saplMqttMessageFluxSecond = saplMqttClient
                .buildSaplMqttMessageFlux(ValueJsonMarshaller.fromJsonNode(topicsSecondFlux), buildVariables())
                .takeUntil(value -> value instanceof TextValue textValue && "message2".equals(textValue.value()));

        // WHEN
        val saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond);

        val secondaryBroker = buildBroker(secondaryConfigDir, secondaryDataDir, secondaryExtensionsDir);
        val mqttClient      = startClient();

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge).thenAwait(Duration.ofMillis(DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Value.of("message1"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic3", "message3", false)))
                .expectNext(Value.of("message3"))

                .then(mqttClient::disconnect).thenAwait(Duration.ofMillis(DELAY_MS))

                .then(() -> stopBroker(mqttBroker)).thenAwait(Duration.ofMillis(5 * DELAY_MS))
                .then(() -> startBroker(secondaryBroker)).thenAwait(Duration.ofMillis(2 * DELAY_MS))

                .then(mqttClient::connect).thenAwait(Duration.ofMillis(5 * DELAY_MS))

                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic3", "message3", true)))
                .expectNext(Value.of("message3"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", true)))
                .expectNext(Value.of("message1"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", true)))
                .expectNext(Value.of("message2")).expectNext(Value.of("message2"))
                .thenAwait(Duration.ofMillis(DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Value.of("message1"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", false)))
                .expectNext(Value.of("message2")).expectNoEvent(Duration.ofMillis(2 * DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic3", "message3", false)))
                .expectNoEvent(Duration.ofMillis(2 * DELAY_MS)).thenCancel().verify();
        mqttClient.disconnect();
        stopBroker(secondaryBroker);
    }

    @Test
    @Timeout(45)
    void when_emitValueUndefinedActivatedAndBrokerConnectionLost_then_reconnectToBrokerAndEmitValueUndefined() {
        // GIVEN
        val secondaryBroker              = buildBroker(secondaryConfigDir, secondaryDataDir, secondaryExtensionsDir);
        val mqttPipConfigForUndefinedVal = json("""
                {
                  "defaultBrokerConfigName" : "production",
                  "brokerConfig" : [ {
                    "name" : "production",
                    "brokerAddress" : "localhost",
                    "brokerPort" : 1883,
                    "clientId" : "mqttPipDefault"
                  } ]
                }
                """);
        val configForUndefinedVal        = Map.of("action", Value.NULL, "environment", Value.NULL, "mqttPipConfig",
                mqttPipConfigForUndefinedVal, "resource", Value.NULL, "subject", Value.NULL);

        // WHEN
        val mqttClient          = startClient();
        val saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(TOPIC, configForUndefinedVal);

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(DELAY_MS))
                .then(() -> mqttClient.publish(MESSAGE)).expectNext(Value.of("message"))

                .then(mqttClient::disconnect).thenAwait(Duration.ofMillis(DELAY_MS))

                .then(() -> stopBroker(mqttBroker)).thenAwait(Duration.ofMillis(5 * DELAY_MS))
                .then(() -> startBroker(secondaryBroker)).thenAwait(Duration.ofMillis(2 * DELAY_MS))

                .then(mqttClient::connect).thenAwait(Duration.ofMillis(5 * DELAY_MS))

                .expectNext(Value.UNDEFINED)
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic", "message", true)))
                .expectNext(Value.of("message"))

                .thenCancel().verify();

        mqttClient.disconnect();
        stopBroker(secondaryBroker);
    }
}
