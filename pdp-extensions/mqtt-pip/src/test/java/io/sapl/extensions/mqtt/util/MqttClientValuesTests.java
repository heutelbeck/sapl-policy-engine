/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hivemq.client.internal.mqtt.reactor.MqttReactorClient;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import lombok.val;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;

class MqttClientValuesTests {

    @Test
    void when_extractingMqttBrokerConfig_then_getCopy() {
        // GIVEN
        val mqttReactorClientMock = mock(MqttReactorClient.class);
        val brokerConfig          = JsonNodeFactory.instance.objectNode();
        brokerConfig.put("key", "value");
        val mqtt5ConnAckMock     = mock(Mqtt5ConnAck.class);
        val mqtt5ConnAckMonoMock = Mono.just(mqtt5ConnAckMock);
        val mqttClientValues     = new MqttClientValues("clientId", mqttReactorClientMock, brokerConfig,
                mqtt5ConnAckMonoMock);

        // WHEN
        ObjectNode mqttBrokerConfig = mqttClientValues.getMqttBrokerConfig();

        // THEN
        assertNotSame(mqttBrokerConfig, brokerConfig);
    }
}
