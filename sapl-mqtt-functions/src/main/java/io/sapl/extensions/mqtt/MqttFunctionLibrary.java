/*
 * Streaming Attribute Policy Language (SAPL) Engine
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
package io.sapl.extensions.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.hivemq.client.mqtt.datatypes.MqttTopic;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.Text;

/**
 * This sapl function library provides functions to check whether mqtt topics
 * are matching against mqtt topics which contain wildcards.
 */
@FunctionLibrary(name = MqttFunctionLibrary.NAME, description = MqttFunctionLibrary.DESCRIPTION)
public class MqttFunctionLibrary {

    static final String NAME        = "mqtt";
    static final String DESCRIPTION = "Functions for matching topics to wildcard topics.";

    private static final String TOPIC_CONTAINS_WILDCARD_ERROR_MESSAGE = "The wildcard topic must not be matched against topics containing wildcards.";

    /**
     * This function checks whether all given mqtt topics are matching the wildcard
     * topic.
     *
     * @param wildcardTopic The mqtt topic containing the wildcard.
     * @param topics        A single textual mqtt topic or an array of mqtt topics.
     * @return Return true when all given topics are matching the wildcard topic.
     */
    @Function(name = "isMatchingAllTopics", docs = "Checks whether all the topics match the wildcard.")
    public Val isMatchingAllTopics(@Text Val wildcardTopic, @Text @Array Val topics) {
        var mqttTopicFilter = buildMqttTopicFilter(wildcardTopic);

        if (topics.isTextual()) {
            return isMatchingSingleTopic(mqttTopicFilter, topics);
        } else {
            var topicsArray = topics.getArrayNode();
            return isMatchingAllTopics(mqttTopicFilter, topicsArray);
        }
    }

    /**
     * This function checks whether at least one of the given mqtt topics is
     * matching the wildcard topic.
     *
     * @param wildcardTopic The mqtt topic containing the wildcard.
     * @param topics        A single textual mqtt topic or an array of mqtt topics.
     * @return Return true when all given topics are matching the wildcard topic.
     */
    @Function(name = "isMatchingAtLeastOneTopic", docs = "Checks whether at least one topic matches the wildcard.")
    public Val isMatchingAtLeastOneTopic(@Text Val wildcardTopic, @Text @Array Val topics) {
        var mqttTopicFilter = buildMqttTopicFilter(wildcardTopic);

        if (topics.isTextual()) {
            return isMatchingSingleTopic(mqttTopicFilter, topics);
        } else {
            var topicsArray = topics.getArrayNode();
            return isMatchingAtLeastOneTopic(mqttTopicFilter, topicsArray);
        }
    }

    private Val isMatchingSingleTopic(MqttTopicFilter mqttTopicFilter, Val topic) {
        if (MqttTopicFilter.of(topic.getText()).containsWildcards()) {
            return Val.error(TOPIC_CONTAINS_WILDCARD_ERROR_MESSAGE);
        } else {
            var mqttTopic = MqttTopic.of(topic.getText());
            return Val.of(mqttTopicFilter.matches(mqttTopic));
        }
    }

    private Val isMatchingAllTopics(MqttTopicFilter mqttTopicFilter, ArrayNode topicsArray) {
        var isMatching = true;
        for (JsonNode topic : topicsArray) {
            if (MqttTopicFilter.of(topic.asText()).containsWildcards()) {
                return Val.error(TOPIC_CONTAINS_WILDCARD_ERROR_MESSAGE);
            }
            var mqttTopic = MqttTopic.of(topic.asText());
            if (!mqttTopicFilter.matches(mqttTopic)) {
                isMatching = false;
            }
        }
        return Val.of(isMatching);
    }

    private Val isMatchingAtLeastOneTopic(MqttTopicFilter mqttTopicFilter, ArrayNode topicsArray) {
        var isMatching = false;
        for (JsonNode topic : topicsArray) {
            if (MqttTopicFilter.of(topic.asText()).containsWildcards()) {
                return Val.error(TOPIC_CONTAINS_WILDCARD_ERROR_MESSAGE);
            }
            var mqttTopic = MqttTopic.of(topic.asText());
            if (mqttTopicFilter.matches(mqttTopic)) {
                isMatching = true;
            }
        }
        return Val.of(isMatching);
    }

    private MqttTopicFilter buildMqttTopicFilter(Val wildcardTopic) {
        return MqttTopicFilter.of(wildcardTopic.getText());
    }
}
