/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.reactor.Mqtt5ReactorClient;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import reactor.core.publisher.Mono;

/**
 * These data objects are used to store client specific data.
 */
@Data
public final class MqttClientValues {
    private final String               clientId;
    private final Mqtt5ReactorClient   mqttReactorClient;
    private final ObjectNode           mqttBrokerConfig;
    private final Mono<Mqtt5ConnAck>   clientConnection;
    @Getter(AccessLevel.NONE)
    private final Map<String, Integer> topicSubscriptionsCountMap;

    /**
     * Caches the given client specifics.
     * 
     * @param clientId          the referenced mqtt client
     * @param mqttReactorClient the mqtt reactor client
     * @param mqttBrokerConfig  the configuration of the connection to the mqtt
     *                          broker
     * @param clientConnection  the mqtt client connection
     */
    public MqttClientValues(String clientId, Mqtt5ReactorClient mqttReactorClient, ObjectNode mqttBrokerConfig,
            Mono<Mqtt5ConnAck> clientConnection) {
        this.clientId                   = clientId;
        this.mqttReactorClient          = mqttReactorClient;
        this.mqttBrokerConfig           = mqttBrokerConfig.deepCopy();
        this.clientConnection           = clientConnection;
        this.topicSubscriptionsCountMap = new HashMap<>();
    }

    /**
     * Returns a deep copy of the mqtt broker configuration.
     * 
     * @return returns the mqtt broker configuration
     */
    public ObjectNode getMqttBrokerConfig() {
        return this.mqttBrokerConfig.deepCopy();
    }

    /**
     * Adds 1 to the existing count. If there was no entry for the referenced count
     * before, then a new entry will be set to the count of 1.
     * 
     * @param topic the reference for the topic count
     */
    public void countTopicSubscriptionsCountMapUp(String topic) {
        topicSubscriptionsCountMap.merge(topic, 1, Integer::sum);
    }

    /**
     * Reduces the count by one. If the new count would be 0 than the topic
     * reference will be deleted.
     * 
     * @param topic the reference for the topic count
     * @return returns true in case there is a new positive count for the topic
     *         otherwise returns false
     */
    public boolean countTopicSubscriptionsCountMapDown(String topic) {
        int count = topicSubscriptionsCountMap.remove(topic);
        if (count > 1) {
            topicSubscriptionsCountMap.put(topic, count - 1);
            return true;
        }
        return false;
    }

    /**
     * Evaluates whether the topic subscription count map contains any entries.
     * 
     * @return returns true in case the map is empty, otherwise returns false
     */
    public boolean isTopicSubscriptionsCountMapEmpty() {
        return topicSubscriptionsCountMap.isEmpty();
    }
}
