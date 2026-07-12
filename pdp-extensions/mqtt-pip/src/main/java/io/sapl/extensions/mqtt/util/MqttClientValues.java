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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

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
    @Getter(AccessLevel.NONE)
    private final ReentrantLock        topicTransitionLock = new ReentrantLock(true);
    // Resolved once by the subscriber that owns this shared client: normally on a
    // successful connect, exceptionally on a connect failure. Reusing subscribers
    // wait on it instead of connecting the shared client a second time.
    @Getter(AccessLevel.NONE)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private final CompletableFuture<Void> connectionEstablished = new CompletableFuture<>();

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

    /**
     * Broker-side subscribe call run under the topic transition lock. May fail
     * with the same checked exceptions as the underlying MQTT subscribe future.
     */
    @FunctionalInterface
    public interface BrokerSubscribe {
        void run() throws InterruptedException, ExecutionException, TimeoutException;
    }

    /**
     * Runs the broker-side subscribe and the topic-count increment as one
     * critical section, so a concurrent unsubscribe on the same topic cannot
     * apply between the subscribe and the count update. The broker subscribe
     * runs first, so a failing subscribe leaves the count untouched.
     *
     * @param topic the topic filter being subscribed
     * @param brokerSubscribe the broker-side subscribe call
     * @throws InterruptedException if the subscribe future is interrupted
     * @throws ExecutionException if the subscribe future completes
     * exceptionally
     * @throws TimeoutException if the subscribe future times out
     */
    public void subscribeTopicAtomically(String topic, BrokerSubscribe brokerSubscribe)
            throws InterruptedException, ExecutionException, TimeoutException {
        topicTransitionLock.lock();
        try {
            brokerSubscribe.run();
            incrementTopicSubscribers(topic);
        } finally {
            topicTransitionLock.unlock();
        }
    }

    /**
     * Runs the topic-count decrement and, when the last subscriber for the
     * topic leaves, the broker-side unsubscribe as one critical section, so a
     * concurrent subscribe on the same topic cannot apply between the count
     * update and the unsubscribe.
     *
     * @param topic the topic filter being released
     * @param brokerUnsubscribe the broker-side unsubscribe call, run only when
     * the topic count reaches zero
     */
    public void unsubscribeTopicAtomically(String topic, Runnable brokerUnsubscribe) {
        topicTransitionLock.lock();
        try {
            if (!decrementTopicSubscribers(topic)) {
                brokerUnsubscribe.run();
            }
        } finally {
            topicTransitionLock.unlock();
        }
    }

    /**
     * Signals that the owning subscriber connected the shared client, releasing
     * any reusing subscribers waiting to subscribe.
     */
    public void markConnectionEstablished() {
        connectionEstablished.complete(null);
    }

    /**
     * Signals that the owning subscriber failed to connect the shared client, so
     * reusing subscribers fail with the same cause instead of waiting.
     *
     * @param cause the connect failure
     */
    public void markConnectionFailed(Throwable cause) {
        connectionEstablished.completeExceptionally(cause);
    }

    /**
     * Waits until the owning subscriber has established the shared connection.
     *
     * @param timeoutMs the maximum time to wait in milliseconds
     * @throws InterruptedException if interrupted while waiting
     * @throws ExecutionException if the connect failed
     * @throws TimeoutException if the connection was not established in time
     */
    public void awaitConnectionEstablished(long timeoutMs)
            throws InterruptedException, ExecutionException, TimeoutException {
        connectionEstablished.get(timeoutMs, TimeUnit.MILLISECONDS);
    }
}
