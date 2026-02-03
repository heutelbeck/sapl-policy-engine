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

import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.embedded.EmbeddedHiveMQ;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.nio.file.Path;
import java.time.Duration;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.extensions.mqtt.MqttTestUtility.*;

@Timeout(30)
class SaplMqttClientSubscriptionsIT {

    private static final long DELAY_MS = 800L;

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
        saplMqttClient.close();
        stopBroker(mqttBroker);
    }

    @Test
    void when_subscribeToMultipleTopicsOnSingleFlux_then_getMessagesOfMultipleTopics() {
        // GIVEN
        val topics = json("[\"topic1\",\"topic2\"]");

        // WHEN
        val saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(topics, buildVariables())
                .filter(value -> !(value instanceof UndefinedValue));

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(2 * DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Value.of("message1"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", false)))
                .expectNext(Value.of("message2")).thenCancel().verify();
    }

    @Test
    void when_subscribeToMultipleTopicsOnDifferentFlux_then_getMessagesOfMultipleTopics() {
        // GIVEN
        val topicsFirstFlux  = json("[\"topic1\",\"topic2\"]");
        val topicsSecondFlux = json("[\"topic2\",\"topic3\"]");

        val saplMqttMessageFluxFirst  = saplMqttClient.buildSaplMqttMessageFlux(topicsFirstFlux, buildVariables());
        val saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(topicsSecondFlux, buildVariables());

        // WHEN
        val saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond)
                .filter(value -> !(value instanceof UndefinedValue));

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge).thenAwait(Duration.ofMillis(2 * DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Value.of("message1"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", false)))
                .expectNext(Value.of("message2")).expectNext(Value.of("message2"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic3", "message3", false)))
                .expectNext(Value.of("message3")).thenCancel().verify();
    }

    @Test
    void when_oneFluxIsCancelledWhileSubscribingToSingleTopics_then_getMessagesOfLeftTopics() {
        // GIVEN
        val saplMqttMessageFluxFirst  = saplMqttClient.buildSaplMqttMessageFlux(Value.of("topic"), buildVariables());
        val saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(Value.of("topic"), buildVariables())
                .takeUntil(value -> value instanceof TextValue textValue && "message".equals(textValue.value()));

        // WHEN
        val saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond)
                .filter(value -> !(value instanceof UndefinedValue));

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge).thenAwait(Duration.ofMillis(2 * DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic", "message", false)))
                .expectNext(Value.of("message")).expectNext(Value.of("message")).thenAwait(Duration.ofMillis(DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic", "message", false)))
                .expectNext(Value.of("message")).expectNoEvent(Duration.ofMillis(2 * DELAY_MS)).thenCancel().verify();
    }

    @Test
    void when_oneFluxIsCancelledWhileSubscribingToMultipleTopics_then_getMessagesOfLeftTopics() {
        // GIVEN
        val topicsFirstFlux  = json("[\"topic1\",\"topic2\"]");
        val topicsSecondFlux = json("[\"topic2\",\"topic3\"]");

        val saplMqttMessageFluxFirst  = saplMqttClient.buildSaplMqttMessageFlux(topicsFirstFlux, buildVariables());
        val saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(topicsSecondFlux, buildVariables())
                .takeUntil(value -> value instanceof TextValue textValue && "message2".equals(textValue.value()));
        // WHEN
        val saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond)
                .filter(value -> !(value instanceof UndefinedValue));

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge).thenAwait(Duration.ofMillis(2 * DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Value.of("message1"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic3", "message3", false)))
                .expectNext(Value.of("message3"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", false)))
                .expectNext(Value.of("message2")).expectNext(Value.of("message2"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic3", "message3", false)))
                .expectNoEvent(Duration.ofMillis(2 * DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
                .expectNext(Value.of("message1"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", false)))
                .expectNext(Value.of("message2")).thenCancel().verify();
    }

    @Test
    void when_subscribingWithSingleLevelWildcard_then_getMessagesMatchingTopicsOfSingleLevelWildcard() {
        // GIVEN

        // WHEN
        val saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Value.of("level1/+/level3"), buildVariables())
                .filter(value -> !(value instanceof UndefinedValue));

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(2 * DELAY_MS))
                .then(() -> mqttClient
                        .publish(buildMqttPublishMessage("level1/singleLevelWildcard/level3", "message1", false)))
                .expectNext(Value.of("message1")).thenCancel().verify();
    }

    @Test
    void when_subscribingWithMultiLevelWildcard_then_getMessagesMatchingTopicsOfMultiLevelWildcard() {
        // GIVEN

        // WHEN
        val saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Value.of("level1/#"), buildVariables())
                .filter(value -> !(value instanceof UndefinedValue));

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(2 * DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("level1/multiLevelWildcard", "message1", false)))
                .expectNext(Value.of("message1"))
                .then(() -> mqttClient
                        .publish(buildMqttPublishMessage("level1/multiLevelWildcard/level3", "message2", false)))
                .expectNext(Value.of("message2")).thenCancel().verify();
    }

    @Test
    void when_unsubscribingTopicOnSharedConnectionWithMultiLevelWildcard_then_getMessagesMatchingTopicsOfMultiLevelWildcard() {
        // GIVEN
        val saplMqttMessageFluxFirst  = saplMqttClient.buildSaplMqttMessageFlux(Value.of("level1/#"), buildVariables());
        val saplMqttMessageFluxSecond = saplMqttClient
                .buildSaplMqttMessageFlux(Value.of("level1/level2"), buildVariables())
                .takeUntil(value -> value instanceof TextValue textValue && "message1".equals(textValue.value()));

        // WHEN
        val saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond)
                .filter(value -> !(value instanceof UndefinedValue));

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge).thenAwait(Duration.ofMillis(2 * DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("level1/level2", "message1", false)))
                .expectNext(Value.of("message1")).expectNext(Value.of("message1"))
                .thenAwait(Duration.ofMillis(DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("level1/level2", "message1", false)))
                .expectNext(Value.of("message1")).expectNoEvent(Duration.ofMillis(2 * DELAY_MS)).thenCancel().verify();
    }

    @Test
    void when_unsubscribingMultiLevelWildcardTopicOnSharedConnectionWithSimpleTopic_then_getMessagesMatchingSimpleTopic() {
        // GIVEN
        val saplMqttMessageFluxFirst  = saplMqttClient.buildSaplMqttMessageFlux(Value.of("level1/level2"),
                buildVariables());
        val saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(Value.of("level1/#"), buildVariables())
                .takeUntil(value -> value instanceof TextValue textValue && "message1".equals(textValue.value()));

        // WHEN
        val saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond)
                .filter(value -> !(value instanceof UndefinedValue));

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge).thenAwait(Duration.ofMillis(2 * DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("level1/level2", "message1", false)))
                .expectNext(Value.of("message1")).expectNext(Value.of("message1"))
                .thenAwait(Duration.ofMillis(DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("level1/level2", "message1", false)))
                .expectNext(Value.of("message1"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("level1/xxx", "message1", false)))
                .expectNoEvent(Duration.ofMillis(2 * DELAY_MS)).thenCancel().verify();
    }

    @Test
    void when_unsubscribingSingleLevelWildcardTopicOnSharedConnectionWithSimpleTopic_then_getMessagesMatchingSimpleTopic() {
        // GIVEN
        val saplMqttMessageFluxFirst  = saplMqttClient.buildSaplMqttMessageFlux(Value.of("level1/level2/level3"),
                buildVariables());
        val saplMqttMessageFluxSecond = saplMqttClient
                .buildSaplMqttMessageFlux(Value.of("level1/+/level3"), buildVariables())
                .takeUntil(value -> value instanceof TextValue textValue && "message1".equals(textValue.value()));

        // WHEN
        val saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond)
                .filter(value -> !(value instanceof UndefinedValue));

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge).thenAwait(Duration.ofMillis(2 * DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("level1/level2/level3", "message1", false)))
                .expectNext(Value.of("message1")).expectNext(Value.of("message1"))
                .thenAwait(Duration.ofMillis(DELAY_MS))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("level1/level2/level3", "message1", false)))
                .expectNext(Value.of("message1"))
                .then(() -> mqttClient.publish(buildMqttPublishMessage("level1/xxx/level3", "message1", false)))
                .expectNoEvent(Duration.ofMillis(2 * DELAY_MS)).thenCancel().verify();
    }
}
