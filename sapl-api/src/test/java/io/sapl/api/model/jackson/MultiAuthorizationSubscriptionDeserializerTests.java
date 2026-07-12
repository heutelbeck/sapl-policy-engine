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
package io.sapl.api.model.jackson;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.pdp.MultiAuthorizationSubscription;
import lombok.val;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("MultiAuthorizationSubscription deserialization")
class MultiAuthorizationSubscriptionDeserializerTests {

    private static JsonMapper mapper;

    @BeforeAll
    static void setup() {
        mapper = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
    }

    @Test
    @DisplayName("Duplicate subscription IDs in the JSON body are rejected as a clean Jackson databind error, not a raw runtime exception")
    void whenDeserializingMultiSubscriptionWithDuplicateIdThenThrowsDatabindException() {
        val              json        = """
                {
                    "read-tome": {"subject":"scholar","action":"read","resource":"necronomicon"},
                    "read-tome": {"subject":"scholar","action":"read","resource":"pnakotic_manuscripts"}
                }""";
        ThrowingCallable deserialize = () -> mapper.readValue(json, MultiAuthorizationSubscription.class);

        assertThatThrownBy(deserialize).isInstanceOf(DatabindException.class).hasMessageContaining("read-tome");
    }

    @Test
    @DisplayName("Empty subscription IDs in the JSON body are rejected as invalid correlation IDs")
    void whenDeserializingMultiSubscriptionWithEmptyIdThenThrowsDatabindException() {
        val              json        = """
                {
                    "": {"subject":"scholar","action":"read","resource":"necronomicon"}
                }""";
        ThrowingCallable deserialize = () -> mapper.readValue(json, MultiAuthorizationSubscription.class);

        assertThatThrownBy(deserialize).isInstanceOf(DatabindException.class).hasMessageContaining("empty");
    }
}
