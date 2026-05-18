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

import tools.jackson.databind.node.ObjectNode;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-broker connection cache entry. Holds the async MQTT client, the
 * broker configuration that produced it, and reference counters used to
 * decide when to unsubscribe a topic and when to disconnect the client.
 */
@Data
public final class MqttClientValues {
    private final String               clientId;
    private final Mqtt5AsyncClient     mqttAsyncClient;
    private final ObjectNode           mqttBrokerConfig;
    @Getter(AccessLevel.NONE)
    private final Map<String, Integer> topicSubscriptionsCountMap;
    @Getter(AccessLevel.NONE)
    private final AtomicInteger        brokerSubscribers;
    private final List<Runnable>       onDisconnectCallbacks;

    public MqttClientValues(String clientId,
            Mqtt5AsyncClient mqttAsyncClient,
            ObjectNode mqttBrokerConfig,
            List<Runnable> onDisconnectCallbacks) {
        this.clientId                   = clientId;
        this.mqttAsyncClient            = mqttAsyncClient;
        this.mqttBrokerConfig           = mqttBrokerConfig.deepCopy();
        this.topicSubscriptionsCountMap = new ConcurrentHashMap<>();
        this.brokerSubscribers          = new AtomicInteger(0);
        this.onDisconnectCallbacks      = onDisconnectCallbacks;
    }

    public MqttClientValues(String clientId, Mqtt5AsyncClient mqttAsyncClient, ObjectNode mqttBrokerConfig) {
        this(clientId, mqttAsyncClient, mqttBrokerConfig, new CopyOnWriteArrayList<>());
    }

    /**
     * Returns a defensive deep copy of the cached broker configuration.
     */
    public ObjectNode getMqttBrokerConfig() {
        return this.mqttBrokerConfig.deepCopy();
    }

    /**
     * Increments the per-broker subscriber count.
     */
    public void incrementBrokerSubscribers() {
        brokerSubscribers.incrementAndGet();
    }

    /**
     * Decrements the per-broker subscriber count and returns the new
     * value.
     */
    public int decrementBrokerSubscribers() {
        return brokerSubscribers.decrementAndGet();
    }

    /**
     * Adds 1 to the existing topic count. If there was no entry for the
     * referenced topic before, sets the count to 1.
     */
    public void incrementTopicSubscribers(String topic) {
        topicSubscriptionsCountMap.merge(topic, 1, Integer::sum);
    }

    /**
     * Reduces the topic count by one. If the new count would be zero,
     * the topic entry is removed.
     *
     * @return {@code true} if a positive count remains for the topic,
     * {@code false} if the topic entry was removed.
     */
    public boolean decrementTopicSubscribers(String topic) {
        int[] newCount = { 0 };
        topicSubscriptionsCountMap.compute(topic, (k, count) -> {
            if (count == null || count <= 1) {
                newCount[0] = 0;
                return null;
            }
            newCount[0] = count - 1;
            return newCount[0];
        });
        return newCount[0] > 0;
    }
}
