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
package io.sapl.functions.libraries;

import io.sapl.api.model.*;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("UuidFunctionLibrary")
class UuidFunctionLibraryTests {

    @Test
    void whenLoadedIntoBrokerThenNoError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.load(new UuidFunctionLibrary())).doesNotThrowAnyException();
    }

    private static final String VALID_UUID_V4 = "550e8400-e29b-41d4-a716-446655440000";
    private static final String VALID_UUID_V1 = "c232ab00-9414-11ec-b3c8-9f6bdeced846";

    @Test
    void whenValidUuidV4ThenParsesCorrectly() {
        val result = UuidFunctionLibrary.parse(Value.of(VALID_UUID_V4));

        assertThat(result).isInstanceOf(ObjectValue.class);
        val obj = (ObjectValue) result;
        assertThat(obj).containsKey("leastSignificantBits").containsKey("mostSignificantBits").containsKey("version")
                .containsKey("variant").containsEntry("version", Value.of(4));
    }

    @Test
    void whenValidUuidV1ThenParsesWithTimestampFields() {
        val result = UuidFunctionLibrary.parse(Value.of(VALID_UUID_V1));

        assertThat(result).isInstanceOf(ObjectValue.class);
        val obj = (ObjectValue) result;
        assertThat(obj).containsKey("leastSignificantBits").containsKey("mostSignificantBits").containsKey("version")
                .containsKey("variant").containsKey("timestamp").containsKey("clockSequence").containsKey("node")
                .containsEntry("version", Value.of(1));
    }

    @Test
    void whenUuidV4ParsedThenDoesNotContainTimestampFields() {
        val result = UuidFunctionLibrary.parse(Value.of(VALID_UUID_V4));

        assertThat(result).isInstanceOf(ObjectValue.class);
        val obj = (ObjectValue) result;
        assertThat(obj).doesNotContainKey("timestamp").doesNotContainKey("clockSequence").doesNotContainKey("node");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = { "not-a-uuid", "12345", "", "550e8400-e29b-41d4-a716",
            "gggggggg-gggg-gggg-gggg-gggggggggggg" })
    void whenInvalidUuidThenReturnsError(String invalidUuid) {
        val result = UuidFunctionLibrary.parse(Value.of(invalidUuid));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).startsWith("Invalid UUID:");
    }

    @Test
    void whenParseAndCompareSignificantBitsThenMatchesJavaUuid() {
        val uuid   = UUID.fromString(VALID_UUID_V4);
        val result = UuidFunctionLibrary.parse(Value.of(VALID_UUID_V4));

        assertThat(result).isInstanceOf(ObjectValue.class);
        val obj = (ObjectValue) result;
        assertThat(((NumberValue) obj.get("leastSignificantBits")).value().longValue())
                .isEqualTo(uuid.getLeastSignificantBits());
        assertThat(((NumberValue) obj.get("mostSignificantBits")).value().longValue())
                .isEqualTo(uuid.getMostSignificantBits());
    }
}
