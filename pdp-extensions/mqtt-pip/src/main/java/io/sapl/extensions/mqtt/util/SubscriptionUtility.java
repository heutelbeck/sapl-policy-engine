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

import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts MQTT topic filters from a SAPL value carrying a single
 * topic string or an array of topic strings.
 */
@UtilityClass
public class SubscriptionUtility {

    private static final String ERROR_TOPIC_NOT_TEXT = "An mqtt topic must be a text value.";

    /**
     * Returns the list of topic filters expressed by {@code topic}. A
     * {@link TextValue} yields a single-element list; an
     * {@link ArrayValue} yields one filter per element.
     */
    public static List<MqttTopicFilter> topicFilters(Value topic) {
        val out = new ArrayList<MqttTopicFilter>();
        if (topic instanceof ArrayValue arrayTopics) {
            for (val element : arrayTopics) {
                out.add(topicFilter(element));
            }
        } else {
            out.add(topicFilter(topic));
        }
        return out;
    }

    private static MqttTopicFilter topicFilter(Value topic) {
        if (topic instanceof TextValue text) {
            return MqttTopicFilter.of(text.value());
        }
        throw new IllegalArgumentException(ERROR_TOPIC_NOT_TEXT);
    }
}
