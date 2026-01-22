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

import com.hivemq.client.mqtt.datatypes.MqttTopic;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;

/**
 * This sapl function library provides functions to check whether mqtt topics
 * are matching against mqtt topics which contain wildcards.
 */
@UtilityClass
@FunctionLibrary(name = MqttFunctionLibrary.NAME, description = MqttFunctionLibrary.DESCRIPTION)
public class MqttFunctionLibrary {

    static final String NAME        = "mqtt";
    static final String DESCRIPTION = "Functions for matching topics to mqtt topics which contain wildcards.";

    private static final String TOPIC_CONTAINS_WILDCARD_ERROR_MESSAGE = "The wildcard topic must not be matched against topics containing wildcards.";
    private static final String WILDCARD_TOPIC_MUST_BE_TEXT           = "The wildcard topic must be a text value.";
    private static final String TOPICS_MUST_BE_TEXT_OR_ARRAY          = "The topics must be a text value or an array of text values.";

    /**
     * This function checks whether all given mqtt topics are matching the wildcard
     * topic.
     *
     * @param wildcardTopic The mqtt topic containing the wildcard.
     * @param topics A single textual mqtt topic or an array of mqtt topics.
     * @return Return true when all given topics are matching the wildcard topic.
     */
    @Function(name = "isMatchingAllTopics", docs = """
            ```isMatchingAllTopics(Text wildcardTopic, Text|Array topics)```:
            Checks whether all ```topics``` match the wildcard ```wildcardTopic```.

            **Example with array:**
            ```sapl
            policy "allTopicsMatchMultilevelWildcardTopic"
            permit
              subject == "firstSubject";
              mqtt.isMatchingAllTopics(resource, ["first/second/third", "first/second/fourth"]);
            ```

            **Example with single topic:**
            ```sapl
            policy "topicMatchesMultilevelWildcardTopic"
            permit
              subject == "firstSubject";
              mqtt.isMatchingAllTopics(resource, "first/second/third");
            ```
            """)
    public static Value isMatchingAllTopics(Value wildcardTopic, Value topics) {
        if (!(wildcardTopic instanceof TextValue wildcardText)) {
            return Value.error(WILDCARD_TOPIC_MUST_BE_TEXT);
        }
        var mqttTopicFilter = MqttTopicFilter.of(wildcardText.value());

        return switch (topics) {
        case ArrayValue arrayTopics -> isMatchingAllTopicsInArray(mqttTopicFilter, arrayTopics);
        case TextValue textTopic    -> isMatchingSingleTopic(mqttTopicFilter, textTopic);
        default                     -> Value.error(TOPICS_MUST_BE_TEXT_OR_ARRAY);
        };
    }

    /**
     * This function checks whether at least one of the given mqtt topics is
     * matching the wildcard topic.
     *
     * @param wildcardTopic The mqtt topic containing the wildcard.
     * @param topics A single textual mqtt topic or an array of mqtt topics.
     * @return Return true when at least one topic is matching the wildcard topic.
     */
    @Function(name = "isMatchingAtLeastOneTopic", docs = """
            ```mqtt.isMatchingAtLeastOneTopic(Text wildcardTopic, Text|Array topics)```
            Checks whether at least one topic in ```topics``` matches the wildcard ```wildcardTopic```.

            **Example with array:**
            ```sapl
            policy "atLeastOneTopicMatchesMultilevelWildcardTopic"
            permit
              subject == "secondSubject";
              mqtt.isMatchingAtLeastOneTopic(resource, ["first/second/third", "first/third"]);
            ```

            **Example with single topic:**
            ```sapl
            policy "topicMatchesMultilevelWildcardTopic"
            permit
              subject == "secondSubject";
              mqtt.isMatchingAtLeastOneTopic(resource, "first/second/third");
            ```
            """)
    public static Value isMatchingAtLeastOneTopic(Value wildcardTopic, Value topics) {
        if (!(wildcardTopic instanceof TextValue wildcardText)) {
            return Value.error(WILDCARD_TOPIC_MUST_BE_TEXT);
        }
        var mqttTopicFilter = MqttTopicFilter.of(wildcardText.value());

        return switch (topics) {
        case ArrayValue arrayTopics -> isMatchingAtLeastOneTopicInArray(mqttTopicFilter, arrayTopics);
        case TextValue textTopic    -> isMatchingSingleTopic(mqttTopicFilter, textTopic);
        default                     -> Value.error(TOPICS_MUST_BE_TEXT_OR_ARRAY);
        };
    }

    private static Value isMatchingSingleTopic(MqttTopicFilter mqttTopicFilter, TextValue topic) {
        if (MqttTopicFilter.of(topic.value()).containsWildcards()) {
            return Value.error(TOPIC_CONTAINS_WILDCARD_ERROR_MESSAGE);
        }
        var mqttTopic = MqttTopic.of(topic.value());
        return Value.of(mqttTopicFilter.matches(mqttTopic));
    }

    private static Value isMatchingAllTopicsInArray(MqttTopicFilter mqttTopicFilter, ArrayValue topics) {
        for (Value topicValue : topics) {
            if (!(topicValue instanceof TextValue topic)) {
                return Value.error("All topics must be text values.");
            }
            if (MqttTopicFilter.of(topic.value()).containsWildcards()) {
                return Value.error(TOPIC_CONTAINS_WILDCARD_ERROR_MESSAGE);
            }
            var mqttTopic = MqttTopic.of(topic.value());
            if (!mqttTopicFilter.matches(mqttTopic)) {
                return Value.FALSE;
            }
        }
        return Value.TRUE;
    }

    private static Value isMatchingAtLeastOneTopicInArray(MqttTopicFilter mqttTopicFilter, ArrayValue topics) {
        for (Value topicValue : topics) {
            if (!(topicValue instanceof TextValue topic)) {
                return Value.error("All topics must be text values.");
            }
            if (MqttTopicFilter.of(topic.value()).containsWildcards()) {
                return Value.error(TOPIC_CONTAINS_WILDCARD_ERROR_MESSAGE);
            }
            var mqttTopic = MqttTopic.of(topic.value());
            if (mqttTopicFilter.matches(mqttTopic)) {
                return Value.TRUE;
            }
        }
        return Value.FALSE;
    }
}
