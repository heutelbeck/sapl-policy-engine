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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
        assertThat(mqttClientValues.decrementBrokerSubscribers()).isZero();
    }

    private static MqttClientValues newValues() {
        return new MqttClientValues("clientId", mock(Mqtt5AsyncClient.class), JsonNodeFactory.instance.objectNode());
    }

    @Test
    @DisplayName("a failing broker subscribe leaves the topic count untouched")
    void whenBrokerSubscribeFailsThenCountUnchanged() {
        val values = newValues();

        assertThat(catchSubscribeException(values, "foo", () -> {
            throw new java.util.concurrent.ExecutionException(new RuntimeException("broker rejected"));
        })).isInstanceOf(java.util.concurrent.ExecutionException.class);

        // No phantom count remains, so a later first subscribe is still treated as
        // the topic's first subscriber.
        assertThat(values.decrementTopicSubscribers("foo")).isFalse();
    }

    @Test
    @DisplayName("the last subscriber leaving a topic triggers the broker unsubscribe exactly once")
    void whenLastSubscriberLeavesThenBrokerUnsubscribeRuns() throws Exception {
        val values     = newValues();
        val unsubCount = new AtomicInteger(0);
        values.subscribeTopicAtomically("foo", () -> { /* broker subscribe succeeds */ });
        values.subscribeTopicAtomically("foo", () -> { /* second subscriber */ });

        values.unsubscribeTopicAtomically("foo", unsubCount::incrementAndGet);
        values.unsubscribeTopicAtomically("foo", unsubCount::incrementAndGet);

        assertThat(unsubCount).hasValue(1);
    }

    @Test
    @DisplayName("a subscribe and an unsubscribe on the same topic never run their broker calls concurrently")
    void whenSubscribeAndUnsubscribeRaceThenBrokerCallsAreSerialized() throws Exception {
        // Model the run1-031 race: subscriber A (already counted) tears down while
        // subscriber B opens on the same shared client and topic. The common lock
        // must make A's {decrement + broker unsubscribe} and B's {broker subscribe
        // + increment} mutually exclusive, so the two broker calls never overlap
        // and the final count matches the final broker subscription state.
        val values   = newValues();
        val inBroker = new AtomicInteger(0);
        val overlap  = new AtomicBoolean(false);
        val bReady   = new CountDownLatch(1);
        values.subscribeTopicAtomically("foo", MqttClientValuesTests::noop);

        val brokerCall = (Runnable) () -> {
            if (inBroker.incrementAndGet() > 1) {
                overlap.set(true);
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            inBroker.decrementAndGet();
        };

        val subscriberB = new Thread(() -> {
            bReady.countDown();
            try {
                values.subscribeTopicAtomically("foo", brokerCall::run);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        val teardownA = new Thread(() -> {
            try {
                bReady.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            values.unsubscribeTopicAtomically("foo", brokerCall);
        });

        subscriberB.start();
        teardownA.start();
        subscriberB.join(2000);
        teardownA.join(2000);

        assertThat(overlap).as("broker subscribe and unsubscribe must not run concurrently").isFalse();
    }

    private static void noop() {
        // Broker subscribe that completes immediately for setup.
    }

    private static Throwable catchSubscribeException(MqttClientValues values, String topic,
            MqttClientValues.BrokerSubscribe action) {
        try {
            values.subscribeTopicAtomically(topic, action);
            return null;
        } catch (Exception e) {
            return e;
        }
    }
}
