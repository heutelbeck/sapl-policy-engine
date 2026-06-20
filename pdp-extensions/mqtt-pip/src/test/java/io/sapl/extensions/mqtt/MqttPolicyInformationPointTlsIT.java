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
import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.stream.RealTimeScheduler;
import io.sapl.api.test.stream.StreamAssertions;
import lombok.val;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.extensions.mqtt.MqttTestUtility.buildMqttPublishMessage;
import static io.sapl.extensions.mqtt.MqttTestUtility.newTlsMosquittoContainer;
import static io.sapl.extensions.mqtt.MqttTestUtility.startPublisher;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test that the MQTT PIP connects to a broker over TLS, verifying
 * the broker certificate against the configured trust store. The broker runs a
 * TLS listener (8883) for the PIP and a plaintext listener (1883) for the test
 * publisher; a message published on the plaintext listener must reach the
 * TLS-subscribed PIP, proving the encrypted subscription works.
 */
@DisplayName("MQTT Policy Information Point over TLS")
class MqttPolicyInformationPointTlsIT {

    @SuppressWarnings("resource")
    static GenericContainer<?>        broker = newTlsMosquittoContainer();
    static String                     brokerHost;
    static volatile int               tlsPort;
    static Mqtt5BlockingClient        publisher;
    static MqttPolicyInformationPoint pip;
    static SaplMqttClient             saplMqttClient;

    private static final AtomicInteger CLIENT_SEQ     = new AtomicInteger();
    private static final String        TRUST_STORE    = Path.of("src/test/resources/tls/truststore.p12")
            .toAbsolutePath().toString();
    private static final String        TRUST_STORE_PW = "changeit";

    @BeforeAll
    static void setUp() {
        broker.start();
        brokerHost     = broker.getHost();
        tlsPort        = broker.getMappedPort(8883);
        publisher      = startPublisher(brokerHost, broker.getMappedPort(1883));
        saplMqttClient = new SaplMqttClient(Clock.systemUTC(), new RealTimeScheduler(Clock.systemUTC()));
        pip            = new MqttPolicyInformationPoint(saplMqttClient);
    }

    @AfterAll
    static void tearDown() {
        if (publisher != null) {
            publisher.disconnect();
        }
        if (saplMqttClient != null) {
            saplMqttClient.close();
        }
        if (broker != null) {
            broker.stop();
        }
    }

    private static AttributeAccessContext tlsCtx() {
        val pipConfig = json("""
                {
                  "defaultBrokerConfigName": "secure",
                  "emitAtRetry": "false",
                  "brokerConfig": [
                    { "name": "secure", "brokerAddress": "%s", "brokerPort": %d, "clientId": "%s",
                      "tls": true, "tlsTrustStore": "%s", "tlsTrustStorePassword": "%s" }
                  ]
                }
                """.formatted(brokerHost, tlsPort, "sapl-tls-" + CLIENT_SEQ.incrementAndGet(), TRUST_STORE,
                TRUST_STORE_PW));
        val variables = ObjectValue.builder().put("mqttPipConfig", pipConfig).build();
        return new AttributeAccessContext(variables, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
    }

    @Test
    @DisplayName("a message reaches the PIP subscribed over a TLS connection verified against the trust store")
    void whenSubscribedOverTlsThenMessageArrives() {
        val topic   = "test/tls/text";
        val message = buildMqttPublishMessage(topic, "secure-hello", true);

        // Retained publish before subscribing so the broker replays it once the TLS
        // subscribe completes, so delivery no longer races the handshake.
        publisher.publish(message);

        try (val stream = pip.messages(Value.of(topic), tlsCtx())) {
            // Skip the leading timer-driven UNDEFINED the PIP emits before the
            // retained message arrives over the slower TLS handshake, then assert
            // the first real value is the message.
            StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(20)).awaitsNextMatching(
                    v -> !(v instanceof UndefinedValue),
                    v -> assertThat(v).isInstanceOf(TextValue.class).isEqualTo(Value.of("secure-hello")));
        }
    }
}
