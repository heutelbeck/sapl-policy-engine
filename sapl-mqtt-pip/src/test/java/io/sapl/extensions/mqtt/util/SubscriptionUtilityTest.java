/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.hivemq.client.internal.mqtt.message.subscribe.MqttSubscription;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;

class SubscriptionUtilityTest {

    @Test
    void when_addingSubscriptionsToSubscriptionListAndSubAckReasonCodeIsError_then_doNotAddSubscription() {
        // GIVEN
        var mqtt5SubscribeMock   = mock(Mqtt5Subscribe.class);
        var mqttSubscriptionMock = mock(MqttSubscription.class);
        var mqttSubscriptionList = List.of(mqttSubscriptionMock);
        doReturn(mqttSubscriptionList).when(mqtt5SubscribeMock).getSubscriptions();

        var mqttClientValues = new MqttClientValues("clientId", null, JsonNodeFactory.instance.objectNode(), null);

        var mqtt5SubAckMock           = mock(Mqtt5SubAck.class);
        var mqtt5SubAckReasonCodeList = List.of(Mqtt5SubAckReasonCode.NOT_AUTHORIZED);
        doReturn(mqtt5SubAckReasonCodeList).when(mqtt5SubAckMock).getReasonCodes();

        // WHEN
        SubscriptionUtility.addSubscriptionsCountToSubscriptionList(mqttClientValues, mqtt5SubAckMock,
                mqtt5SubscribeMock);

        // THEN
        assertTrue(mqttClientValues.isTopicSubscriptionsCountMapEmpty());
    }
}
