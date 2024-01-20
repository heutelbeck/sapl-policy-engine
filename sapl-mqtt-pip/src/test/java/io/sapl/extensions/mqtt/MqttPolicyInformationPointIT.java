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
import static io.sapl.extensions.mqtt.MqttTestUtility.startClient;
import static io.sapl.extensions.mqtt.MqttTestUtility.stopBroker;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PayloadFormatIndicator;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.embedded.EmbeddedHiveMQ;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.interpreter.InitializationException;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.PolicyDecisionPointFactory;
import reactor.test.StepVerifier;

class MqttPolicyInformationPointIT {

    private static final String          MESSAGE        = "message";
    private static final JsonNodeFactory JSON           = JsonNodeFactory.instance;
    private static final String          SUBJECT        = "subjectName";
    private static final String          TOPIC          = "single_topic";
    private static final ObjectNode      RESOURCE       = buildJsonResource();
    private static final Mqtt5Publish    publishMessage = buildMqttPublishMessage();

    @TempDir
    Path configDir;

    @TempDir
    Path dataDir;

    @TempDir
    Path extensionsDir;

    private EmbeddedHiveMQ              mqttBroker;
    private Mqtt5BlockingClient         mqttClient;
    private EmbeddedPolicyDecisionPoint pdp;

    @BeforeEach
    void beforeEach() throws InitializationException, InterruptedException, ExecutionException {
        this.mqttBroker = buildAndStartBroker(configDir, dataDir, extensionsDir);
        mqttClient      = startClient();
        this.pdp        = buildPdp();
    }

    @AfterEach
    void tearDown() throws Exception {
        pdp.destroy();
        mqttClient.disconnect();
        stopBroker(mqttBroker);
    }

    @Timeout(15)
    @ParameterizedTest
    @ValueSource(strings = { "actionWithoutParams", "actionWithQos", "actionNameWithQosAndConfig" })
    void when_messagesIsCalled_then_getPublishedMessages(String action) {
        // GIVEN
        AuthorizationSubscription authzSubscription = AuthorizationSubscription.of(SUBJECT, action, RESOURCE);

        // WHEN
        var pdpDecisionFlux = pdp.decide(authzSubscription);

        // THEN
        StepVerifier.create(pdpDecisionFlux).thenAwait(Duration.ofMillis(1000))
                .then(() -> mqttClient.publish(publishMessage))
                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.PERMIT).thenCancel()
                .verify();
    }

    private static EmbeddedPolicyDecisionPoint buildPdp() throws InitializationException {
        return PolicyDecisionPointFactory.filesystemPolicyDecisionPoint("src/test/resources/pipPolicies", List::of,
                () -> List.of(MqttPolicyInformationPoint.class), List::of, List::of);
    }

    private static Mqtt5Publish buildMqttPublishMessage() {
        return Mqtt5Publish.builder().topic(TOPIC).qos(MqttQos.AT_MOST_ONCE)
                .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8)
                .payload(MESSAGE.getBytes(StandardCharsets.UTF_8)).build();
    }

    private static ObjectNode buildJsonResource() {
        ObjectNode resource = JSON.objectNode();
        resource.put("topic", TOPIC);
        return resource;
    }
}
