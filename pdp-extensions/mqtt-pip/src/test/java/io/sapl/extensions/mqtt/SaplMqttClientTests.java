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

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.stream.RealTimeScheduler;
import io.sapl.api.test.stream.StreamAssertions;
import io.sapl.extensions.mqtt.util.MqttClientValues;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.api.model.ValueJsonMarshaller.toJsonNode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("SaplMqttClient")
class SaplMqttClientTests {

    @Test
    @DisplayName("a message larger than the configured limit fails closed to an error value")
    void whenPayloadExceedsLimitThenErrorValue() {
        val publish = Mqtt5Publish.builder().topic("sapl/test").payload(new byte[2048]).build();

        val result = SaplMqttClient.decodePublish(publish, 1024);

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("exceeded");
    }

    @Test
    @DisplayName("a message within the configured limit is decoded")
    void whenPayloadWithinLimitThenDecoded() {
        val publish = Mqtt5Publish.builder().topic("sapl/test").payload("hello".getBytes(StandardCharsets.UTF_8))
                .build();

        val result = SaplMqttClient.decodePublish(publish, 1024);

        assertThat(result).isEqualTo(Value.of("hello"));
    }

    private static AttributeAccessContext ctx() {
        val pipConfig = json("""
                {
                  "defaultBrokerConfigName": "default",
                  "brokerConfig": [
                    { "name": "default", "brokerAddress": "localhost", "brokerPort": 1883 }
                  ]
                }
                """);
        val variables = ObjectValue.builder().put("mqttPipConfig", pipConfig).build();
        return new AttributeAccessContext(variables, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
    }

    @Test
    @DisplayName("a policy-supplied QoS outside 0..2 yields an error value and never opens a client (no hang, no cache leak)")
    void whenQosOutOfRangeThenErrorValueAndNoClientOpened() {
        val saplMqttClient  = new SaplMqttClient(Clock.systemUTC(), new RealTimeScheduler(Clock.systemUTC()));
        val cacheSizeBefore = SaplMqttClient.MQTT_CLIENT_CACHE.size();

        try (val stream = saplMqttClient.buildSaplMqttMessageStream(Value.of("test/qos"), ctx(), Value.of(5))) {
            StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(5))
                    .awaitsNext(v -> assertThat(v).isInstanceOf(ErrorValue.class));
        }

        assertThat(SaplMqttClient.MQTT_CLIENT_CACHE).hasSize(cacheSizeBefore);
    }

    private static JsonNode brokerConfig(String configJson) {
        return toJsonNode(json(configJson));
    }

    private static ObjectValue secrets(ObjectValue mqttSecrets) {
        return ObjectValue.builder().put("mqtt", mqttSecrets).build();
    }

    private static String passwordOf(Mqtt5SimpleAuth auth) {
        return auth.getPassword().map(p -> StandardCharsets.UTF_8.decode(p.duplicate()).toString()).orElse("");
    }

    @Nested
    @DisplayName("credential sourcing")
    class CredentialSourcing {

        @Test
        @DisplayName("a policy-supplied password in the broker config is never used as a credential")
        void whenPasswordOnlyInBrokerConfigThenNoPasswordSent() {
            val config = brokerConfig("""
                    { "name": "default", "brokerAddress": "localhost", "password": "policyInjected" }
                    """);

            val auth = SaplMqttClient.buildAuth(config, Value.EMPTY_OBJECT);

            assertThat(passwordOf(auth)).isEmpty();
        }

        @Test
        @DisplayName("a username-only secret does not pair with a policy-supplied config password")
        void whenSecretHasOnlyUsernameThenNoPasswordSent() {
            val config          = brokerConfig("""
                    { "name": "default", "brokerAddress": "localhost", "password": "policyInjected" }
                    """);
            val usernameSecrets = secrets(ObjectValue.builder().put("username", Value.of("alice")).build());

            val auth = SaplMqttClient.buildAuth(config, usernameSecrets);

            assertThat(auth.getUsername()).map(Object::toString).contains("alice");
            assertThat(passwordOf(auth)).isEmpty();
        }

        @Test
        @DisplayName("a password configured in secrets is used as the credential")
        void whenSecretHasPasswordThenItIsUsed() {
            val config          = brokerConfig("""
                    { "name": "default", "brokerAddress": "localhost" }
                    """);
            val passwordSecrets = secrets(ObjectValue.builder().put("username", Value.of("alice"))
                    .put("password", Value.of("fromSecrets")).build());

            val auth = SaplMqttClient.buildAuth(config, passwordSecrets);

            assertThat(passwordOf(auth)).isEqualTo("fromSecrets");
        }
    }

    @Nested
    @DisplayName("insecure transport detection")
    class InsecureTransportDetection {

        private static final ObjectValue CREDENTIAL_SECRETS = ObjectValue.builder().put("mqtt",
                ObjectValue.builder().put("username", Value.of("alice")).put("password", Value.of("secret")).build())
                .build();

        @Test
        @DisplayName("credentials over a plaintext connection are flagged as insecure")
        void whenCredentialsAndNoTlsThenInsecure() {
            val auth = SaplMqttClient.buildAuth(brokerConfig("{ \"name\": \"default\" }"), CREDENTIAL_SECRETS);

            assertThat(SaplMqttClient.carriesCredentialsOverPlaintext(false, auth)).isTrue();
        }

        @Test
        @DisplayName("credentials over a TLS connection are not flagged as insecure")
        void whenCredentialsAndTlsThenNotInsecure() {
            val auth = SaplMqttClient.buildAuth(brokerConfig("{ \"name\": \"default\" }"), CREDENTIAL_SECRETS);

            assertThat(SaplMqttClient.carriesCredentialsOverPlaintext(true, auth)).isFalse();
        }

        @Test
        @DisplayName("an anonymous plaintext connection carries no credentials and is not flagged")
        void whenNoCredentialsAndNoTlsThenNotInsecure() {
            val auth = SaplMqttClient.buildAuth(brokerConfig("{ \"name\": \"default\" }"), Value.EMPTY_OBJECT);

            assertThat(SaplMqttClient.carriesCredentialsOverPlaintext(false, auth)).isFalse();
        }
    }

    @Test
    @DisplayName("reconnect sentinel emission is off by default, matching the documented default")
    void whenEmitAtRetryUnsetThenDefaultsToFalse() {
        assertThat(SaplMqttClient.DEFAULT_EMIT_AT_RETRY).isFalse();
    }

    @Nested
    @DisplayName("shared-client registration")
    class SharedClientRegistration {

        private static MqttClientValues candidate() {
            return new MqttClientValues("clientId", mock(Mqtt5AsyncClient.class),
                    JsonNodeFactory.instance.objectNode().put("brokerAddress", "localhost"));
        }

        private static JsonNode brokerKey() {
            return JsonNodeFactory.instance.objectNode().put("brokerAddress", "localhost").put("token",
                    java.util.UUID.randomUUID().toString());
        }

        @Test
        @DisplayName("the first subscriber inserts the prebuilt client and counts itself")
        void whenFirstSubscriberThenPrebuiltClientInserted() {
            val key      = brokerKey();
            val prebuilt = candidate();

            val registered = SaplMqttClient.registerSubscriber(key, prebuilt);

            assertThat(registered).isSameAs(prebuilt);
            assertThat(SaplMqttClient.MQTT_CLIENT_CACHE).containsEntry(key, prebuilt);
        }

        @Test
        @DisplayName("a second subscriber reuses the cached client and discards its surplus candidate")
        void whenSecondSubscriberThenSurplusCandidateDiscarded() {
            val key    = brokerKey();
            val first  = candidate();
            val second = candidate();
            SaplMqttClient.registerSubscriber(key, first);

            val registered = SaplMqttClient.registerSubscriber(key, second);

            assertThat(registered).isSameAs(first).isNotSameAs(second);
            assertThat(registered.decrementBrokerSubscribers()).isEqualTo(1);
        }
    }
}
