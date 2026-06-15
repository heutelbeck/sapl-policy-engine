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

import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.stream.RealTimeScheduler;
import io.sapl.api.test.stream.StreamAssertions;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static org.assertj.core.api.Assertions.assertThat;

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
}
