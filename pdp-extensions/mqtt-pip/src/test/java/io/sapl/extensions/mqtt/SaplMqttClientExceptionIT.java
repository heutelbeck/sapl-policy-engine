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

import com.hivemq.embedded.EmbeddedHiveMQ;
import io.sapl.api.model.Value;
import io.sapl.extensions.mqtt.util.DefaultResponseUtility;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import reactor.test.StepVerifier;

import java.nio.file.Path;
import java.time.Duration;

import static io.sapl.extensions.mqtt.MqttTestUtility.buildAndStartBroker;
import static io.sapl.extensions.mqtt.MqttTestUtility.buildVariables;
import static io.sapl.extensions.mqtt.MqttTestUtility.stopBroker;
import static org.mockito.ArgumentMatchers.any;

class SaplMqttClientExceptionIT {

    private static final long DELAY_MS = 500L;

    @TempDir
    Path configDir;

    @TempDir
    Path dataDir;

    @TempDir
    Path extensionsDir;

    EmbeddedHiveMQ mqttBroker;
    SaplMqttClient saplMqttClient;

    @BeforeEach
    void beforeEach() {
        this.mqttBroker     = buildAndStartBroker(configDir, dataDir, extensionsDir);
        this.saplMqttClient = new SaplMqttClient();
    }

    @AfterEach
    void afterEach() {
        SaplMqttClient.MQTT_CLIENT_CACHE.clear();
        SaplMqttClient.DEFAULT_RESPONSE_CONFIG_CACHE.clear();
        stopBroker(mqttBroker);
    }

    @Test
    void when_exceptionOccursWhileBuildingMessageFlux_then_returnFluxWithValueOfError() {
        // GIVEN
        val topics = "topic";

        // WHEN
        val saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Value.of(topics), null);

        // THEN
        StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(DELAY_MS))
                .expectNext(Value.error("Failed to build stream of messages.")).thenCancel().verify();
    }

    @Test
    void when_exceptionOccursInTheMessageFlux_then_returnFluxWithValueOfError() {
        // GIVEN
        val topics = "topic";

        // WHEN
        try (MockedStatic<DefaultResponseUtility> defaultResponseUtilityMockedStatic = Mockito
                .mockStatic(DefaultResponseUtility.class)) {
            defaultResponseUtilityMockedStatic.when(() -> DefaultResponseUtility.getDefaultResponseConfig(any(), any()))
                    .thenThrow(new RuntimeException("Error in stream"));

            val saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Value.of(topics), buildVariables());

            // THEN
            StepVerifier.create(saplMqttMessageFlux).thenAwait(Duration.ofMillis(DELAY_MS))
                    .expectNext(Value.error("Error in stream")).thenCancel().verify();
        }
    }
}
