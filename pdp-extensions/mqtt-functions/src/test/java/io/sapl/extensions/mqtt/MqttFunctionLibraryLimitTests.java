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

import static io.sapl.extensions.mqtt.MqttFunctionLibrary.MAX_TOPIC_FILTER_BYTES;
import static io.sapl.extensions.mqtt.MqttFunctionLibrary.MAX_TOPIC_FILTERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import lombok.val;

@DisplayName("MqttFunctionLibrary topic array limits")
class MqttFunctionLibraryLimitTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource("matchingOperations")
    @DisplayName("topic arrays over the count limit return an error value")
    void whenTopicArrayExceedsLimitThenErrorValue(String name, BiFunction<Value, Value, Value> operation) {
        val topics = repeatedTopicArray(MAX_TOPIC_FILTERS + 1, "building/floor/temperature");

        val result = operation.apply(Value.of("building/#"), topics);

        assertThat(result).isInstanceOfSatisfying(ErrorValue.class,
                error -> assertThat(error.message()).contains("count"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matchingOperations")
    @DisplayName("topic arrays over the byte limit return an error value")
    void whenTopicArrayBytesExceedLimitThenErrorValue(String name, BiFunction<Value, Value, Value> operation) {
        val topics = Value.ofArray(Value.of("a".repeat(MAX_TOPIC_FILTER_BYTES + 1)));

        val result = operation.apply(Value.of("#"), topics);

        assertThat(result).isInstanceOfSatisfying(ErrorValue.class,
                error -> assertThat(error.message()).contains("bytes"));
    }

    static Stream<Arguments> matchingOperations() {
        return Stream.of(
                arguments("all topics", (BiFunction<Value, Value, Value>) MqttFunctionLibrary::isMatchingAllTopics),
                arguments("at least one topic",
                        (BiFunction<Value, Value, Value>) MqttFunctionLibrary::isMatchingAtLeastOneTopic));
    }

    private static Value repeatedTopicArray(int count, String topic) {
        val topics = new Value[count];
        Arrays.fill(topics, Value.of(topic));
        return Value.ofArray(topics);
    }

}
