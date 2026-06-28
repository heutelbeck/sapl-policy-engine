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

import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SubscriptionUtility")
class SubscriptionUtilityTests {

    @Test
    @DisplayName("single text topic produces one filter")
    void whenSingleTextTopicThenOneFilter() {
        val filters = SubscriptionUtility.topicFilters(Value.of("home/livingroom/temperature"));

        assertThat(filters).hasSize(1);
        assertThat(filters.getFirst().toString()).isEqualTo("home/livingroom/temperature");
    }

    @Test
    @DisplayName("array of topics produces one filter per element, in order")
    void whenArrayOfTopicsThenOneFilterPerElement() {
        val filters = SubscriptionUtility
                .topicFilters(Value.ofArray(Value.of("sensors/temperature"), Value.of("sensors/humidity")));

        assertThat(filters).hasSize(2);
        assertThat(filters.get(0).toString()).isEqualTo("sensors/temperature");
        assertThat(filters.get(1).toString()).isEqualTo("sensors/humidity");
    }

    @Test
    @DisplayName("topic count over the configured limit is rejected")
    void whenTopicCountExceedsLimitThenThrows() {
        val topics = Value.ofArray(Value.of("sensors/temperature"), Value.of("sensors/humidity"));

        assertThatThrownBy(() -> SubscriptionUtility.topicFilters(topics, 1, 1024L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("count");
    }

    @Test
    @DisplayName("topic byte length over the configured limit is rejected")
    void whenTopicBytesExceedLimitThenThrows() {
        val topic = Value.of("sensors/temperature");

        assertThatThrownBy(() -> SubscriptionUtility.topicFilters(topic, 1, 4L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bytes");
    }

    @Test
    @DisplayName("non-positive topic filter limits are rejected")
    void whenTopicLimitsAreNonPositiveThenThrows() {
        val topic = Value.of("sensors/temperature");

        assertThatThrownBy(() -> SubscriptionUtility.topicFilters(topic, 0, 1024L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxTopicFilters");
        assertThatThrownBy(() -> SubscriptionUtility.topicFilters(topic, 1, 0L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxTopicFilterBytes");
    }

    @Test
    @DisplayName("MQTT wildcard syntax is preserved in the filter")
    void whenWildcardTopicThenWildcardPreserved() {
        val filters = SubscriptionUtility.topicFilters(Value.of("building/+/temperature"));

        assertThat(filters).hasSize(1);
        assertThat(filters.getFirst().toString()).isEqualTo("building/+/temperature");
    }

    @Test
    @DisplayName("a non-text topic is rejected with a domain error, not a class cast")
    void whenTopicIsNotTextThenThrows() {
        val notText = Value.of(5);

        assertThatThrownBy(() -> SubscriptionUtility.topicFilters(notText))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a non-text array element is rejected with a domain error, not a class cast")
    void whenArrayElementIsNotTextThenThrows() {
        val topics = Value.ofArray(Value.of("sensors/temperature"), Value.of(7));

        assertThatThrownBy(() -> SubscriptionUtility.topicFilters(topics)).isInstanceOf(IllegalArgumentException.class);
    }
}
