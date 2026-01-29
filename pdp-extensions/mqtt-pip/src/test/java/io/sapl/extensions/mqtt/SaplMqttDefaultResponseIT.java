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
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.extensions.mqtt.util.DefaultResponseConfig;
import io.sapl.extensions.mqtt.util.DefaultResponseUtility;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.extensions.mqtt.MqttTestUtility.*;
import static org.assertj.core.api.Assertions.assertThat;

class SaplMqttDefaultResponseIT {

    private static final long DELAY_MS = 1000L;

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
        this.mqttClient.disconnect();
        this.saplMqttClient.close();
        stopBroker(this.mqttBroker);
    }

    @Test
    void when_subscribingWithDefaultConfigAndBrokerDoesNotSendMessage_then_getDefaultUndefined() {
        // GIVEN
        val topics = json("[\"topic1\",\"topic2\"]");

        // WHEN
        Flux<Value> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(topics, buildVariables());

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(DELAY_MS)).expectNext(Value.UNDEFINED)
                .thenCancel().verify();
    }

    @Test
    void when_subscribingWithConfigDefaultResponseErrorAndBrokerDoesNotSendMessage_then_getDefaultError() {
        // GIVEN
        val topics = json("[\"topic1\",\"topic2\"]");

        // WHEN
        Flux<Value> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(topics, buildCustomConfig());

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(DELAY_MS))
                .expectNextMatches(ErrorValue.class::isInstance).thenCancel().verify();
    }

    @Test
    void when_subscribingWithDefaultResponseTypeSpecifiedInAttributeFinderParams_then_useThisDefaultResponseType() {
        // GIVEN
        val topics       = json("[\"topic1\",\"topic2\"]");
        val configParams = json("""
                {
                  "brokerAddress": "localhost",
                  "brokerPort": 1883,
                  "clientId": "clientId",
                  "defaultResponse": "error"
                }
                """);

        // WHEN
        Flux<Value> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(topics, buildVariables(), Value.of(0),
                configParams);

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(DELAY_MS))
                .expectNextMatches(ErrorValue.class::isInstance).thenCancel().verify();
    }

    @Test
    void when_subscribingWithDefaultResponseTimeoutSpecifiedInAttributeFinderParams_then_useThisDefaultResponseTimeout() {
        // GIVEN
        val topics       = json("[\"topic1\",\"topic2\"]");
        val configParams = json("""
                {
                  "brokerAddress": "localhost",
                  "brokerPort": 1883,
                  "clientId": "clientId",
                  "defaultResponse": "error",
                  "timeoutDuration": %d
                }
                """.formatted(8 * DELAY_MS));

        // WHEN
        Flux<Value> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(topics, buildVariables(), Value.of(0),
                configParams);

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(10 * DELAY_MS))
                .expectNextMatches(ErrorValue.class::isInstance).thenCancel().verify();
    }

    @Test
    void when_specifyingIllegalDefaultResponseType_then_usingDefaultResponseType() {
        // GIVEN
        DefaultResponseConfig defaultResponseConfig = new DefaultResponseConfig(5000, "illegal");

        // WHEN
        Value defaultValue = DefaultResponseUtility.getDefaultValue(defaultResponseConfig);

        // THEN
        assertThat(defaultValue).isInstanceOf(UndefinedValue.class);
    }

    private static Value customPipConfig() {
        return json("""
                {
                  "defaultBrokerConfigName" : "production",
                  "emitAtRetry" : "false",
                  "defaultResponse" : "error",
                  "brokerConfig" : [ {
                    "name" : "production",
                    "brokerAddress" : "localhost",
                    "brokerPort" : 1883,
                    "clientId" : "mqttPipDefault"
                  } ]
                }
                """);
    }

    private static Map<String, Value> buildCustomConfig() {
        return Map.of("action", Value.NULL, "environment", Value.NULL, "mqttPipConfig", customPipConfig(), "resource",
                Value.NULL, "subject", Value.NULL);
    }
}
