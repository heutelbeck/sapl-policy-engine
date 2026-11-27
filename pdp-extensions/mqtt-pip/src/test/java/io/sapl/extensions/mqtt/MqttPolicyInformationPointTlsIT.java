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
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.stream.RealTimeScheduler;
import io.sapl.api.test.stream.StreamAssertions;
import lombok.val;
import okhttp3.tls.HeldCertificate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.GenericContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
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
 * the broker certificate against the configured PEM trust anchors. The broker runs a
 * TLS listener (8883) for the PIP and a plaintext listener (1883) for the test
 * publisher. A message published on the plaintext listener must reach the
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

    private static final AtomicInteger CLIENT_SEQ = new AtomicInteger();
    private static String              brokerCertificate;

    @BeforeAll
    static void setUp() throws IOException {
        brokerCertificate = Files.readString(Path.of("src/test/resources/tls/server.crt"));
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

    private static AttributeAccessContext tlsCtx(boolean include) {
        val pipConfig = json("""
                {
                  "default": "secure",
                  "emitAtRetry": "false",
                  "brokers": {
                    "secure": { "brokerAddress": "%s", "brokerPort": %d, "clientId": "%s",
                      "tls": true, "trustProfile": "broker" }
                  }
                }
                """.formatted(brokerHost, tlsPort, "sapl-tls-" + CLIENT_SEQ.incrementAndGet()));
        val profile   = ObjectValue.builder().put("trustedCaPem", Value.of(brokerCertificate))
                .put("includeDefaultTrustRoots", Value.of(include)).build();
        val variables = ObjectValue.builder().put("mqtt", pipConfig)
                .put("tlsTrustProfiles", ObjectValue.builder().put("broker", profile).build()).build();
        return new AttributeAccessContext(variables, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
    }

    @ParameterizedTest(name = "include platform roots = {0}")
    @ValueSource(booleans = { false, true })
    @DisplayName("removing broker trust cannot reuse an existing TLS subscription")
    void whenTrustRemovedThenNewSubscriptionRejectsPrivateBroker(boolean include) {
        val topic = "test/tls/rotation";
        publisher.publish(buildMqttPublishMessage(topic, "before-rotation", true));
        val trusted     = tlsCtx(include);
        val replacement = new HeldCertificate.Builder().certificateAuthority(1).build();
        val profiles    = ObjectValue.builder()
                .put("broker",
                        ObjectValue.builder().put("trustedCaPem", Value.of(replacement.certificatePem())).build())
                .build();
        val changed     = ObjectValue.builder().putAll(trusted.variables()).put("tlsTrustProfiles", profiles).build();
        val removed     = new AttributeAccessContext(changed, Value.EMPTY_OBJECT, trusted.variables());
        try (val first = pip.messages(Value.of(topic), trusted)) {
            StreamAssertions.assertThat(first).withinTimeout(Duration.ofSeconds(20)).awaitsNextMatching(
                    v -> !(v instanceof UndefinedValue), v -> assertThat(v).isEqualTo(Value.of("before-rotation")));
            try (val second = pip.messages(Value.of(topic), removed)) {
                StreamAssertions.assertThat(second).withinTimeout(Duration.ofSeconds(20)).awaitsNextMatching(
                        v -> !(v instanceof UndefinedValue), v -> assertThat(v).isInstanceOf(ErrorValue.class));
            }
        }
    }

    @ParameterizedTest(name = "include platform roots = {0}")
    @ValueSource(booleans = { false, true })
    @DisplayName("a message reaches the PIP subscribed over a TLS connection verified against the configured PEM")
    void whenSubscribedOverTlsThenMessageArrives(boolean include) {
        val topic   = "test/tls/text";
        val message = buildMqttPublishMessage(topic, "secure-hello", true);

        // Retained publish before subscribing so the broker replays it once the TLS
        // subscribe completes, so delivery no longer races the handshake.
        publisher.publish(message);

        try (val stream = pip.messages(Value.of(topic), tlsCtx(include))) {
            // Skip the leading timer-driven UNDEFINED the PIP emits before the
            // retained message arrives over the slower TLS handshake, then assert
            // the first real value is the message.
            StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(20)).awaitsNextMatching(
                    v -> !(v instanceof UndefinedValue),
                    v -> assertThat(v).isInstanceOf(TextValue.class).isEqualTo(Value.of("secure-hello")));
        }
    }
}
