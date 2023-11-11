/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import static io.sapl.extensions.mqtt.MqttTestUtility.buildBroker;
import static io.sapl.extensions.mqtt.MqttTestUtility.buildMqttPublishMessage;
import static io.sapl.extensions.mqtt.MqttTestUtility.buildVariables;
import static io.sapl.extensions.mqtt.MqttTestUtility.startBroker;
import static io.sapl.extensions.mqtt.MqttTestUtility.startClient;
import static io.sapl.extensions.mqtt.MqttTestUtility.stopBroker;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.embedded.EmbeddedHiveMQ;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.InitializationException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;

@Slf4j
class SaplMqttClientConnectionIT {

    private final static ObjectMapper MAPPER   = new ObjectMapper();
    private final static long         DELAY_MS = 1000L;
    private final static Mqtt5Publish MESSAGE  = buildMqttPublishMessage("topic", "message", false);
    private final static Val          TOPIC    = Val.of("topic");

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
    void when_brokerConfigIsInvalid_then_returnValOfError() throws InitializationException, JsonProcessingException {
        // GIVEN
        var mqttPipConfigForUndefinedVal = MAPPER.readTree("""
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

        var configForUndefinedVal = Map.of("action", MAPPER.nullNode(), "environment", MAPPER.nullNode(),
                "mqttPipConfig", mqttPipConfigForUndefinedVal, "resource", MAPPER.nullNode(), "subject",
                MAPPER.nullNode());

        log.info("PIP Config:\n" + MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(configForUndefinedVal));

        // WHEN
        var saplMqttClient      = new SaplMqttClient();
        var saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(TOPIC, configForUndefinedVal);

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(1 * DELAY_MS))
                .expectNextMatches(Val::isError).thenCancel().verify();
    }

    @Test
    @Timeout(45)
    void when_noConfigIsSpecified_then_returnValOfError() {
        // WHEN
        var emptyPdpConfig      = Map.<String, JsonNode>of();
        var saplMqttClient      = new SaplMqttClient();
        var saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(TOPIC, emptyPdpConfig);

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(1 * DELAY_MS))
                .expectNextMatches(Val::isError).thenCancel().verify();
    }

    @Test
    @Timeout(45)
    void when_connectionIsShared_then_bothMessageFluxWorking() throws InitializationException {
        // GIVEN
        var saplMqttMessageFluxFirst  = saplMqttClient.buildSaplMqttMessageFlux(Val.of("topic1"), buildVariables());
        var saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(Val.of("topic2"), buildVariables());

        // WHEN
        var saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond);
        var mqttClient               = startClient();

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge).thenAwait(Duration.ofMillis(1 * DELAY_MS))
                .expectNext(Val.UNDEFINED, Val.UNDEFINED)
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Val.of("message1"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", false)))
                .expectNext(Val.of("message2"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Val.of("message1")).thenCancel().verify();
        mqttClient.disconnect();
    }

    @Test
    @Timeout(45)
    void when_connectionIsNotSharedAnymore_then_singleFluxWorking() throws InitializationException {
        // GIVEN
        var saplMqttMessageFluxFirst  = saplMqttClient.buildSaplMqttMessageFlux(Val.of("topic1"), buildVariables());
        var saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(Val.of("topic2"), buildVariables())
                .takeUntil(message -> "lastMessage".equals(message.getText()));

        // WHEN
        var saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond);
        var mqttClient               = startClient();

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge).thenAwait(Duration.ofMillis(1 * DELAY_MS))
                .expectNext(Val.UNDEFINED, Val.UNDEFINED)
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Val.of("message1"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", false)))
                .expectNext(Val.of("message2"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "lastMessage", false)))
                .expectNext(Val.of("lastMessage")).thenAwait(Duration.ofMillis(1 * DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", false)))
                .expectNoEvent(Duration.ofMillis(1 * DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Val.of("message1")).thenCancel().verify();
        mqttClient.disconnect();
    }

    @Test
    @Timeout(45)
    void when_connectionIsNotSharedAnymoreAndThenSharedAgain_then_bothMessageFluxWorking()
            throws InitializationException {
        // GIVEN
        var saplMqttMessageFluxFirst  = saplMqttClient.buildSaplMqttMessageFlux(Val.of("topic1"), buildVariables());
        var saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(Val.of("topic2"), buildVariables())
                .takeUntil(message -> "lastMessage".equals(message.getText()));
        var testPublisher             = TestPublisher.create();

        // WHEN
        var saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond,
                saplMqttMessageFluxSecond.delaySubscription(testPublisher.flux()));
        var mqttClient               = startClient();

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge).expectNext(Val.UNDEFINED, Val.UNDEFINED)
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Val.of("message1"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", false)))
                .expectNext(Val.of("message2"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "lastMessage", false)))
                .expectNext(Val.of("lastMessage")).thenAwait(Duration.ofMillis(1 * DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Val.of("message1")).then(() -> testPublisher.emit("subscribe"))
                .thenAwait(Duration.ofMillis(1 * DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", false)))
                .expectNext(Val.of("message2"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Val.of("message1")).thenCancel().verify();
        mqttClient.disconnect();
    }

    @Test
    @Timeout(45)
    void when_brokerConnectionLost_then_reconnectToBroker()
            throws InitializationException, InterruptedException, ExecutionException {
        // WHEN
        var secondaryBroker     = buildBroker(secondaryConfigDir, secondaryDataDir, secondaryExtensionsDir);
        var mqttClient          = startClient();
        var saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(TOPIC, buildVariables());

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(1 * DELAY_MS))
                .then(() -> mqttClient.publish(MESSAGE)).expectNext(Val.of("message"))

                .then(mqttClient::disconnect).thenAwait(Duration.ofMillis(1 * DELAY_MS))

                .then(() -> stopBroker(mqttBroker)).thenAwait(Duration.ofMillis(5 * DELAY_MS))
                .then(() -> startBroker(secondaryBroker)).thenAwait(Duration.ofMillis(2 * DELAY_MS))

                .then(mqttClient::connect).thenAwait(Duration.ofMillis(5 * DELAY_MS))

                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic", "message", true)))
                .expectNext(Val.of("message")).thenCancel().verify();

        mqttClient.disconnect();
        stopBroker(secondaryBroker);
    }

    @Test
    @Timeout(45)
    void when_brokerConnectionLostWhileSharingConnection_then_reconnectToBroker()
            throws InitializationException, InterruptedException, ExecutionException {
        // GIVEN
        var saplMqttMessageFluxFirst  = saplMqttClient.buildSaplMqttMessageFlux(Val.of("topic1"), buildVariables());
        var saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(Val.of("topic2"), buildVariables());

        // WHEN
        var saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond);

        var secondaryBroker = buildBroker(secondaryConfigDir, secondaryDataDir, secondaryExtensionsDir);
        var mqttClient      = startClient();

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge).thenAwait(Duration.ofMillis(1 * DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Val.of("message1"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", false)))
                .expectNext(Val.of("message2"))

                .then(mqttClient::disconnect).thenAwait(Duration.ofMillis(1 * DELAY_MS))

                .then(() -> stopBroker(mqttBroker)).thenAwait(Duration.ofMillis(5 * DELAY_MS))
                .then(() -> startBroker(secondaryBroker)).thenAwait(Duration.ofMillis(2 * DELAY_MS))

                .then(mqttClient::connect).thenAwait(Duration.ofMillis(5 * DELAY_MS))

                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", true)))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", true)))
                .expectNextMatches(value -> "message2".equals(value.getText()) || "message1".equals(value.getText()))
                .expectNextMatches(value -> "message2".equals(value.getText()) || "message1".equals(value.getText()))
                .thenCancel().verify();

        mqttClient.disconnect();
        stopBroker(secondaryBroker);
    }

    @Test
    @Timeout(45)
    void when_sharedReconnectToBroker_then_getMessagesOfMultipleTopics()
            throws InitializationException, InterruptedException, ExecutionException {
        // GIVEN
        var topicsFirstFlux  = MAPPER.createArrayNode().add("topic1").add("topic2");
        var topicsSecondFlux = MAPPER.createArrayNode().add("topic2").add("topic3");

        var saplMqttMessageFluxFirst  = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topicsFirstFlux),
                buildVariables());
        var saplMqttMessageFluxSecond = saplMqttClient
                .buildSaplMqttMessageFlux(Val.of(topicsSecondFlux), buildVariables())
                .takeUntil(value -> "message2".equals(value.getText()));

        // WHEN
        var saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond);

        var secondaryBroker = buildBroker(secondaryConfigDir, secondaryDataDir, secondaryExtensionsDir);
        var mqttClient      = startClient();

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge).thenAwait(Duration.ofMillis(1 * DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Val.of("message1"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic3", "message3", false)))
                .expectNext(Val.of("message3"))

                .then(mqttClient::disconnect).thenAwait(Duration.ofMillis(1 * DELAY_MS))

                .then(() -> stopBroker(mqttBroker)).thenAwait(Duration.ofMillis(5 * DELAY_MS))
                .then(() -> startBroker(secondaryBroker)).thenAwait(Duration.ofMillis(2 * DELAY_MS))

                .then(mqttClient::connect).thenAwait(Duration.ofMillis(5 * DELAY_MS))

                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic3", "message3", true)))
                .expectNext(Val.of("message3"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", true)))
                .expectNext(Val.of("message1"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", true)))
                .expectNext(Val.of("message2")).expectNext(Val.of("message2"))
                .thenAwait(Duration.ofMillis(1 * DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Val.of("message1"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", false)))
                .expectNext(Val.of("message2")).expectNoEvent(Duration.ofMillis(2 * DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic3", "message3", false)))
                .expectNoEvent(Duration.ofMillis(2 * DELAY_MS)).thenCancel().verify();
        mqttClient.disconnect();
        stopBroker(secondaryBroker);
    }

    @Test
    @Timeout(45)
    void when_emitValUndefinedActivatedAndBrokerConnectionLost_then_reconnectToBrokerAndEmitValUndefined()
            throws JsonProcessingException {
        // GIVEN
        var secondaryBroker              = buildBroker(secondaryConfigDir, secondaryDataDir, secondaryExtensionsDir);
        var mqttPipConfigForUndefinedVal = MAPPER.readTree("""
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
        var configForUndefinedVal        = Map.of("action", MAPPER.nullNode(), "environment", MAPPER.nullNode(),
                "mqttPipConfig", mqttPipConfigForUndefinedVal, "resource", MAPPER.nullNode(), "subject",
                MAPPER.nullNode());

        // WHEN
        var mqttClient          = startClient();
        var saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(TOPIC, configForUndefinedVal);

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(1 * DELAY_MS))
                .then(() -> mqttClient.publish(MESSAGE)).expectNext(Val.of("message"))

                .then(mqttClient::disconnect).thenAwait(Duration.ofMillis(1 * DELAY_MS))

                .then(() -> stopBroker(mqttBroker)).thenAwait(Duration.ofMillis(5 * DELAY_MS))
                .then(() -> startBroker(secondaryBroker)).thenAwait(Duration.ofMillis(2 * DELAY_MS))

                .then(mqttClient::connect).thenAwait(Duration.ofMillis(5 * DELAY_MS))

                .expectNext(Val.UNDEFINED)
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic", "message", true)))
                .expectNext(Val.of("message"))

                .thenCancel().verify();

        mqttClient.disconnect();
        stopBroker(secondaryBroker);
    }
}
