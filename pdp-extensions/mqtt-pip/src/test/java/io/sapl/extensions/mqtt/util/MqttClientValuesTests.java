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
package io.sapl.extensions.mqtt.util;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("MqttClientValues")
class MqttClientValuesTests {

    @Test
    @DisplayName("getMqttBrokerConfig returns a defensive copy")
    void whenExtractingMqttBrokerConfigThenGetCopy() {
        val client       = mock(Mqtt5AsyncClient.class);
        val brokerConfig = JsonNodeFactory.instance.objectNode();
        brokerConfig.put("key", "value");
        val mqttClientValues = new MqttClientValues("clientId", client, brokerConfig);

        val returned = mqttClientValues.getMqttBrokerConfig();

        assertThat(returned).isNotSameAs(brokerConfig);
        assertThat(returned.get("key").asString()).isEqualTo("value");
    }

    @Test
    @DisplayName("topic count tracks subscribers")
    void whenIncrementAndDecrementThenCountTracks() {
        val mqttClientValues = new MqttClientValues("clientId", mock(Mqtt5AsyncClient.class),
                JsonNodeFactory.instance.objectNode());

        mqttClientValues.incrementTopicSubscribers("foo");
        mqttClientValues.incrementTopicSubscribers("foo");

        assertThat(mqttClientValues.decrementTopicSubscribers("foo")).isTrue();
        assertThat(mqttClientValues.decrementTopicSubscribers("foo")).isFalse();
    }

    @Test
    @DisplayName("broker subscriber count returns new value on decrement")
    void whenIncrementAndDecrementBrokerSubscribersThenReportsNewValue() {
        val mqttClientValues = new MqttClientValues("clientId", mock(Mqtt5AsyncClient.class),
                JsonNodeFactory.instance.objectNode());

        mqttClientValues.incrementBrokerSubscribers();
        mqttClientValues.incrementBrokerSubscribers();

        assertThat(mqttClientValues.decrementBrokerSubscribers()).isEqualTo(1);
        assertThat(mqttClientValues.decrementBrokerSubscribers()).isEqualTo(0);
    }
}
