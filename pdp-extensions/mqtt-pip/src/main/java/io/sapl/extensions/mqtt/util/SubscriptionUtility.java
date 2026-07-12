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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts MQTT topic filters from a SAPL value carrying a single
 * topic string or an array of topic strings.
 */
@UtilityClass
public class SubscriptionUtility {

    private static final String ERROR_MAX_TOPIC_FILTER_BYTES_NOT_POSITIVE = "MQTT maxTopicFilterBytes must be positive.";
    private static final String ERROR_MAX_TOPIC_FILTERS_NOT_POSITIVE      = "MQTT maxTopicFilters must be positive.";
    private static final String ERROR_TOPIC_FILTER_LIMIT_EXCEEDED         = "MQTT topic filter count %d exceeds the configured limit of %d.";
    private static final String ERROR_TOPIC_FILTERS_TOO_LARGE             = "MQTT topic filters exceed the configured limit of %d UTF-8 bytes.";
    private static final String ERROR_TOPIC_NOT_TEXT                      = "An MQTT topic must be a text value.";

    /**
     * Returns the list of topic filters expressed by {@code topic}. A
     * {@link TextValue} yields a single-element list; an
     * {@link ArrayValue} yields one filter per element.
     *
     * @param topic a single topic string or an array of topic strings
     * @return MQTT topic filters extracted from {@code topic}
     * @throws IllegalArgumentException if any topic value is not text or not a
     * valid
     * MQTT topic filter
     */
    public static List<MqttTopicFilter> topicFilters(Value topic) {
        return topicFilters(topic, Integer.MAX_VALUE, Long.MAX_VALUE);
    }

    /**
     * Returns topic filters, enforcing operator-configured count and total byte
     * limits before any broker subscription attempt is opened.
     *
     * @param topic a single topic string or an array of topic strings
     * @param maxFilters maximum number of accepted topic filters
     * @param maxTotalBytes maximum total UTF-8 bytes accepted across all topic
     * filters
     * @return MQTT topic filters extracted from {@code topic}
     * @throws IllegalArgumentException if a limit is non-positive, a limit is
     * exceeded, or any topic value is not text or
     * not a valid MQTT topic filter
     */
    public static List<MqttTopicFilter> topicFilters(Value topic, int maxFilters, long maxTotalBytes) {
        validateLimits(maxFilters, maxTotalBytes);
        val out = new ArrayList<MqttTopicFilter>();
        if (topic instanceof ArrayValue arrayTopics) {
            if (arrayTopics.size() > maxFilters) {
                throw new IllegalArgumentException(
                        ERROR_TOPIC_FILTER_LIMIT_EXCEEDED.formatted(arrayTopics.size(), maxFilters));
            }
            var totalBytes = 0L;
            for (val element : arrayTopics) {
                totalBytes = addTopicFilter(out, element, totalBytes, maxTotalBytes);
            }
        } else {
            addTopicFilter(out, topic, 0L, maxTotalBytes);
        }
        return out;
    }

    private static void validateLimits(int maxFilters, long maxTotalBytes) {
        if (maxFilters <= 0) {
            throw new IllegalArgumentException(ERROR_MAX_TOPIC_FILTERS_NOT_POSITIVE);
        }
        if (maxTotalBytes <= 0L) {
            throw new IllegalArgumentException(ERROR_MAX_TOPIC_FILTER_BYTES_NOT_POSITIVE);
        }
    }

    private static long addTopicFilter(List<MqttTopicFilter> out, Value topic, long totalBytes, long maxTotalBytes) {
        val text     = topicText(topic);
        val newTotal = totalBytes + text.getBytes(StandardCharsets.UTF_8).length;
        if (newTotal > maxTotalBytes) {
            throw new IllegalArgumentException(ERROR_TOPIC_FILTERS_TOO_LARGE.formatted(maxTotalBytes));
        }
        out.add(MqttTopicFilter.of(text));
        return newTotal;
    }

    private static String topicText(Value topic) {
        if (topic instanceof TextValue text) {
            return text.value();
        }
        throw new IllegalArgumentException(ERROR_TOPIC_NOT_TEXT);
    }
}
