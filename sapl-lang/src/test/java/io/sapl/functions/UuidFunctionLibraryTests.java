/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions;

import io.sapl.api.interpreter.Val;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidFunctionLibraryTests {

    @Test
    void parse_withValidVersion4Uuid_returnsObjectWithRequiredFieldsOnly() {
        val uuidString = "550e8400-e29b-41d4-a716-446655440000";
        val uuidVal    = Val.of(uuidString);
        val uuid       = UUID.fromString(uuidString);

        val result = UuidFunctionLibrary.parse(uuidVal);

        assertThat(result.isError()).isFalse();
        assertThat(result.get().get("leastSignificantBits").asLong()).isEqualTo(uuid.getLeastSignificantBits());
        assertThat(result.get().get("mostSignificantBits").asLong()).isEqualTo(uuid.getMostSignificantBits());
        assertThat(result.get().get("version").asInt()).isEqualTo(uuid.version());
        assertThat(result.get().get("variant").asInt()).isEqualTo(uuid.variant());
        assertThat(result.get().has("timestamp")).isFalse();
        assertThat(result.get().has("clockSequence")).isFalse();
        assertThat(result.get().has("node")).isFalse();
    }

    @Test
    void parse_withValidVersion1Uuid_includesTimestampClockSequenceAndNode() {
        val uuidString = "c232ab00-9414-11ec-b3c8-9f6bdeced846";
        val uuidVal    = Val.of(uuidString);
        val uuid       = UUID.fromString(uuidString);

        val result = UuidFunctionLibrary.parse(uuidVal);

        assertThat(result.isError()).isFalse();
        assertThat(result.get().has("timestamp")).isTrue();
        assertThat(result.get().has("clockSequence")).isTrue();
        assertThat(result.get().has("node")).isTrue();
        assertThat(result.get().get("timestamp").asLong()).isEqualTo(uuid.timestamp());
        assertThat(result.get().get("clockSequence").asInt()).isEqualTo(uuid.clockSequence());
        assertThat(result.get().get("node").asLong()).isEqualTo(uuid.node());
    }

    @ParameterizedTest
    @ValueSource(strings = { "not-a-valid-uuid", "", "550e8400-e29b-41d4-a716" })
    void parse_withInvalidUuidString_returnsError(String invalidUuid) {
        val invalidUuidVal = Val.of(invalidUuid);

        val result = UuidFunctionLibrary.parse(invalidUuidVal);

        assertThat(result.isError()).isTrue();
    }

    @Test
    void seededRandom_withSameSeed_producesIdenticalUuids() {
        val seed = Val.of(12345L);

        val result1 = UuidFunctionLibrary.seededRandom(seed);
        val result2 = UuidFunctionLibrary.seededRandom(seed);

        assertThat(result1.getText()).isEqualTo(result2.getText());
    }

    @Test
    void seededRandom_withDifferentSeeds_producesDifferentUuids() {
        val seed1 = Val.of(12345L);
        val seed2 = Val.of(67890L);

        val result1 = UuidFunctionLibrary.seededRandom(seed1);
        val result2 = UuidFunctionLibrary.seededRandom(seed2);

        assertThat(result1.getText()).isNotEqualTo(result2.getText());
    }

    @Test
    void seededRandom_producesValidVersion4IetfVariantUuid() {
        val seed = Val.of(42L);

        val result = UuidFunctionLibrary.seededRandom(seed);

        assertThat(result.isError()).isFalse();
        val uuidString = result.getText();
        assertThat(uuidString).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
        val uuid = UUID.fromString(uuidString);
        assertThat(uuid.version()).isEqualTo(4);
        assertThat(uuid.variant()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(longs = { 0L, -12345L, Long.MAX_VALUE, Long.MIN_VALUE })
    void seededRandom_withEdgeCaseSeeds_producesValidVersion4Uuid(long seedValue) {
        val seed = Val.of(seedValue);

        val result = UuidFunctionLibrary.seededRandom(seed);

        assertThat(result.isError()).isFalse();
        val uuid = UUID.fromString(result.getText());
        assertThat(uuid.version()).isEqualTo(4);
        assertThat(uuid.variant()).isEqualTo(2);
    }
}
