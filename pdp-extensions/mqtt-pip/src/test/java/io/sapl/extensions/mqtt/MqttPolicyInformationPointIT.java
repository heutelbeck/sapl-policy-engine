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

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PayloadFormatIndicator;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.stream.RealTimeScheduler;
import io.sapl.api.test.stream.StreamAssertions;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.extensions.mqtt.MqttTestUtility.buildMqttPublishMessage;
import static io.sapl.extensions.mqtt.MqttTestUtility.newMosquittoContainer;
import static io.sapl.extensions.mqtt.MqttTestUtility.startPublisher;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the MQTT PIP against a Mosquitto broker
 * running in a Testcontainer.
 */
@DisplayName("MQTT Policy Information Point")
class MqttPolicyInformationPointIT {

    @SuppressWarnings("resource")
    static GenericContainer<?>        broker = newMosquittoContainer();
    static String                     brokerHost;
    static int                        brokerPort;
    static Mqtt5BlockingClient        publisher;
    static MqttPolicyInformationPoint pip;
    static SaplMqttClient             saplMqttClient;

    private static final AtomicInteger CLIENT_SEQ = new AtomicInteger();

    @BeforeAll
    static void setUp() {
        broker.start();
        brokerHost     = broker.getHost();
        brokerPort     = broker.getMappedPort(1883);
        publisher      = startPublisher(brokerHost, brokerPort);
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

    private static AttributeAccessContext ctx(String clientId) {
        val pipConfig = json("""
                {
                  "defaultBrokerConfigName": "production",
                  "emitAtRetry": "false",
                  "brokerConfig": [
                    { "name": "production", "brokerAddress": "%s", "brokerPort": %d, "clientId": "%s" }
                  ]
                }
                """.formatted(brokerHost, brokerPort, clientId));
        val variables = ObjectValue.builder().put("mqtt", pipConfig).build();
        return new AttributeAccessContext(variables, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
    }

    private static AttributeAccessContext freshCtx() {
        return ctx("sapl-pip-" + CLIENT_SEQ.incrementAndGet());
    }

    private static void publishLater(Mqtt5Publish message, long delayMs) {
        Thread.startVirtualThread(() -> {
            try {
                // No condition to await: this thread produces a timed event, so the publish
                // must really be deferred.
                Thread.sleep(delayMs);
                publisher.publish(message);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private static Mqtt5Publish jsonPublish(String topic, String json) {
        return Mqtt5Publish.builder().topic(topic).qos(MqttQos.AT_MOST_ONCE)
                .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8).contentType("application/json")
                .payload(json.getBytes(StandardCharsets.UTF_8)).build();
    }

    private static AttributeAccessContext ctxForPort(int brokerPort) {
        val pipConfig = json("""
                {
                  "defaultBrokerConfigName": "down",
                  "emitAtRetry": "false",
                  "brokerConfig": [
                    { "name": "down", "brokerAddress": "%s", "brokerPort": %d, "clientId": "%s" }
                  ]
                }
                """.formatted(brokerHost, brokerPort, "sapl-down-" + CLIENT_SEQ.incrementAndGet()));
        val variables = ObjectValue.builder().put("mqtt", pipConfig).build();
        return new AttributeAccessContext(variables, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
    }

    @Test
    @DisplayName("an unreachable broker yields an error value and the failed client is evicted from the cache by the failure path, not leaked until close")
    void whenBrokerUnreachableThenErrorValueAndCacheEvicted() throws Exception {
        final int closedPort;
        try (val probe = new ServerSocket(0)) {
            closedPort = probe.getLocalPort();
        } // closed here: the port is now free, so a connect attempt is refused

        val before   = saplMqttClient.cache().size();
        val sawError = new AtomicBoolean(false);
        val stream   = pip.messages(Value.of("test/unreachable"), ctxForPort(closedPort));
        val drainer  = Thread.startVirtualThread(() -> {
                         try {
                             Value value;
                             while ((value = stream.awaitNext()) != null) {
                                 if (value instanceof ErrorValue) {
                                     sawError.set(true);
                                 }
                             }
                         } catch (InterruptedException e) {
                             Thread.currentThread().interrupt();
                         }
                     });
        try {
            // The connect refusal drives the failure path, which emits an error
            // and releases the cache entry without waiting for the stream close.
            Awaitility.await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
                assertThat(sawError).isTrue();
                assertThat(saplMqttClient.cache()).hasSize(before);
            });
        } finally {
            stream.close();
            drainer.interrupt();
        }
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("subscribe + publish: a published message arrives as a TextValue")
        void whenMessagePublishedThenStreamEmitsIt() {
            val topic   = "test/happy/text";
            val message = buildMqttPublishMessage(topic, "hello", true);

            // Retained publish before subscribing so delivery does not race the subscribe.
            publisher.publish(message);
            try (val stream = pip.messages(Value.of(topic), freshCtx())) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(10))
                        .awaitsNext(v -> assertThat(v).isInstanceOf(TextValue.class).isEqualTo(Value.of("hello")));
            }
        }

        @Test
        @DisplayName("subscribe to multiple topics: messages from any topic surface")
        void whenSubscribedToArrayOfTopicsThenAnyEmits() {
            val topicA = "test/array/a";
            val topicB = "test/array/b";

            try (val stream = pip.messages(Value.ofArray(Value.of(topicA), Value.of(topicB)), freshCtx())) {
                publishLater(buildMqttPublishMessage(topicB, "from-b", false), 500L);
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(10))
                        .awaitsNext(v -> assertThat(v).isEqualTo(Value.of("from-b")));
            }
        }
    }

    @Nested
    @DisplayName("Payload decoding")
    class PayloadDecoding {

        @Test
        @DisplayName("JSON content-type: payload becomes parsed Value")
        void whenJsonContentTypeThenPayloadParsed() {
            val topic   = "test/payload/json";
            val message = jsonPublish(topic, "{\"temperature\":22.5,\"unit\":\"C\"}");

            try (val stream = pip.messages(Value.of(topic), freshCtx())) {
                publishLater(message, 500L);
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(10)).awaitsNext(v -> {
                    val obj = (ObjectValue) v;
                    assertThat(((NumberValue) obj.get("temperature")).value().doubleValue()).isEqualTo(22.5);
                    assertThat(((TextValue) obj.get("unit")).value()).isEqualTo("C");
                });
            }
        }

        @Test
        @DisplayName("invalid JSON with JSON content-type yields ErrorValue")
        void whenInvalidJsonThenErrorValue() {
            val topic   = "test/payload/bad-json";
            val message = jsonPublish(topic, "{not valid json}");

            try (val stream = pip.messages(Value.of(topic), freshCtx())) {
                publishLater(message, 500L);
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(10))
                        .awaitsNext(v -> assertThat(v).isInstanceOf(ErrorValue.class));
            }
        }

        @Test
        @DisplayName("UTF-8 payload without format indicator becomes TextValue")
        void whenUtf8PayloadAndNoFormatIndicatorThenTextValue() {
            val topic   = "test/payload/utf8-noindicator";
            val message = Mqtt5Publish.builder().topic(topic).qos(MqttQos.AT_MOST_ONCE)
                    .payload("plain text".getBytes(StandardCharsets.UTF_8)).build();

            try (val stream = pip.messages(Value.of(topic), freshCtx())) {
                publishLater(message, 500L);
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(10))
                        .awaitsNext(v -> assertThat(v).isEqualTo(Value.of("plain text")));
            }
        }

        @Test
        @DisplayName("non-UTF-8 binary payload becomes ArrayValue of bytes")
        void whenBinaryPayloadThenArrayOfBytes() {
            val topic       = "test/payload/binary";
            val invalidUtf8 = new byte[] { (byte) 0xFF, (byte) 0xFE, (byte) 0xFD };
            val message     = Mqtt5Publish.builder().topic(topic).qos(MqttQos.AT_MOST_ONCE).payload(invalidUtf8)
                    .build();

            try (val stream = pip.messages(Value.of(topic), freshCtx())) {
                publishLater(message, 500L);
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(10))
                        .awaitsNext(v -> assertThat(v).isInstanceOf(ArrayValue.class));
            }
        }
    }

    @Nested
    @DisplayName("Default-response timer")
    class DefaultResponseTimer {

        private static AttributeAccessContext ctxWithDefaultResponse(String type, long timeoutMs) {
            val pipConfig = json("""
                    {
                      "defaultBrokerConfigName": "production",
                      "emitAtRetry": "false",
                      "defaultResponse": "%s",
                      "defaultResponseTimeout": %d,
                      "brokerConfig": [
                        { "name": "production", "brokerAddress": "%s", "brokerPort": %d, "clientId": "%s" }
                      ]
                    }
                    """.formatted(type, timeoutMs, brokerHost, brokerPort,
                    "sapl-pip-default-" + CLIENT_SEQ.incrementAndGet()));
            val variables = ObjectValue.builder().put("mqtt", pipConfig).build();
            return new AttributeAccessContext(variables, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        }

        @Test
        @DisplayName("no message before timeout emits UNDEFINED")
        void whenNoMessageBeforeTimeoutThenUndefined() {
            try (val stream = pip.messages(Value.of("test/default/silent-undefined"),
                    ctxWithDefaultResponse("undefined", 300L))) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(5))
                        .awaitsNext(v -> assertThat(v).isEqualTo(Value.UNDEFINED));
            }
        }

        @Test
        @DisplayName("no message before timeout with error type emits ErrorValue")
        void whenNoMessageBeforeTimeoutAndErrorTypeThenErrorValue() {
            try (val stream = pip.messages(Value.of("test/default/silent-error"),
                    ctxWithDefaultResponse("error", 300L))) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(5))
                        .awaitsNext(v -> assertThat(v).isInstanceOf(ErrorValue.class));
            }
        }

        @Test
        @DisplayName("message arriving before timeout suppresses the default emission")
        void whenMessageBeforeTimeoutThenNoDefaultEmission() {
            val topic = "test/default/preempted";
            try (val stream = pip.messages(Value.of(topic), ctxWithDefaultResponse("undefined", 3000L))) {
                publishLater(buildMqttPublishMessage(topic, "preempt", false), 500L);
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(10))
                        .awaitsNext(v -> assertThat(v).isEqualTo(Value.of("preempt")));
            }
        }
    }

    @Nested
    @DisplayName("MQTT wildcards")
    class Wildcards {

        @Test
        @DisplayName("single-level wildcard '+' matches one segment")
        void whenSingleLevelWildcardThenMatchesOneSegment() {
            val publishTopic   = "building/floor1/temperature";
            val subscribeTopic = "building/+/temperature";

            try (val stream = pip.messages(Value.of(subscribeTopic), freshCtx())) {
                publishLater(buildMqttPublishMessage(publishTopic, "21.5", false), 500L);
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(10))
                        .awaitsNext(v -> assertThat(v).isEqualTo(Value.of("21.5")));
            }
        }

        @Test
        @DisplayName("multi-level wildcard '#' matches deep paths")
        void whenMultiLevelWildcardThenMatchesDeepPaths() {
            val publishTopic   = "sensors/floor1/room2/temperature";
            val subscribeTopic = "sensors/#";

            try (val stream = pip.messages(Value.of(subscribeTopic), freshCtx())) {
                publishLater(buildMqttPublishMessage(publishTopic, "deep", false), 500L);
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(10))
                        .awaitsNext(v -> assertThat(v).isEqualTo(Value.of("deep")));
            }
        }
    }

    @Nested
    @DisplayName("Error paths")
    class ErrorPaths {

        @Test
        @DisplayName("missing mqtt config yields an error stream")
        void whenNoMqttPipConfigThenErrorValue() {
            val emptyCtx = new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);

            try (val stream = pip.messages(Value.of("any/topic"), emptyCtx)) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(5))
                        .awaitsNext(v -> assertThat(v).isInstanceOf(ErrorValue.class));
            }
        }

        @Test
        @DisplayName("connection failure to unreachable broker emits a non-message Value (default or error)")
        void whenBrokerUnreachableThenNonMessageValue() {
            // Set a long default-response timeout so we surface the actual connect error
            // rather than the timer-driven UNDEFINED.
            val pipConfig = json("""
                    {
                      "defaultBrokerConfigName": "ghost",
                      "emitAtRetry": "false",
                      "defaultResponseTimeout": 30000,
                      "brokerConfig": [
                        { "name": "ghost", "brokerAddress": "127.0.0.1", "brokerPort": 1, "clientId": "ghost-client" }
                      ]
                    }
                    """);
            val variables = ObjectValue.builder().put("mqtt", pipConfig).build();
            val ctx       = new AttributeAccessContext(variables, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);

            try (val stream = pip.messages(Value.of("any/topic"), ctx)) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(15))
                        .awaitsNext(v -> assertThat(v).isInstanceOf(ErrorValue.class));
            }
        }
    }

    @Nested
    @DisplayName("Connection sharing")
    class ConnectionSharing {

        @Test
        @DisplayName("two streams subscribing to the same broker share one client cache entry")
        void whenTwoStreamsOnSameBrokerThenCacheEntryShared() {
            // Both subscriptions must be alive at the same time for connection
            // sharing to be observable. A default-response context makes each
            // stream emit a deterministic UNDEFINED placeholder once subscribed,
            // so awaitsNext registers the subscriber without closing the stream.
            val ctx = sharedBrokerCtxWithDefaultResponse();
            try (val s1 = pip.messages(Value.of("test/share/a"), ctx)) {
                StreamAssertions.assertThat(s1).withinTimeout(Duration.ofSeconds(5))
                        .awaitsNext(v -> assertThat(v).isEqualTo(Value.UNDEFINED));
                val sizeWithOne = saplMqttClient.cache().size();
                try (val s2 = pip.messages(Value.of("test/share/b"), ctx)) {
                    StreamAssertions.assertThat(s2).withinTimeout(Duration.ofSeconds(5))
                            .awaitsNext(v -> assertThat(v).isEqualTo(Value.UNDEFINED));
                    // s1 and s2 are both still open here. Adding a second subscriber
                    // to the same broker must not create a second cache entry.
                    val sizeWithTwo = saplMqttClient.cache().size();
                    assertThat(sizeWithTwo).isEqualTo(sizeWithOne);
                }
            }
        }

        private static AttributeAccessContext sharedBrokerCtxWithDefaultResponse() {
            val pipConfig = json("""
                    {
                      "defaultBrokerConfigName": "production",
                      "emitAtRetry": "false",
                      "defaultResponse": "undefined",
                      "defaultResponseTimeout": 300,
                      "brokerConfig": [
                        { "name": "production", "brokerAddress": "%s", "brokerPort": %d, "clientId": "%s" }
                      ]
                    }
                    """.formatted(brokerHost, brokerPort, "sapl-pip-share-" + CLIENT_SEQ.incrementAndGet()));
            val variables = ObjectValue.builder().put("mqtt", pipConfig).build();
            return new AttributeAccessContext(variables, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        }

        @Test
        @DisplayName("when last subscriber closes the broker entry is evicted from the cache")
        void whenLastSubscriberClosesThenCacheEntryEvicted() {
            val ctx    = freshCtx();
            val before = saplMqttClient.cache().size();
            try (val s = pip.messages(Value.of("test/eviction/topic"), ctx)) {
                StreamAssertions.assertThat(s).withinTimeout(Duration.ofSeconds(3)).drain();
                assertThat(saplMqttClient.cache()).hasSizeGreaterThanOrEqualTo(before);
            }
            Awaitility.await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(saplMqttClient.cache()).hasSize(before));
        }
    }
}
