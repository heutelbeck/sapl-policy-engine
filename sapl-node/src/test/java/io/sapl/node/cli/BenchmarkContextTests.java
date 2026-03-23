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
package io.sapl.node.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import lombok.val;

@DisplayName("BenchmarkContext")
class BenchmarkContextTests {

    @Nested
    @DisplayName("JSON round-trip serialization")
    class JsonRoundTripTests {

        @Test
        @DisplayName("serializes and deserializes with all fields preserved")
        void whenRoundTrip_thenAllFieldsPreserved() {
            val original     = new BenchmarkContext("{\"subject\":\"alice\"}", "/tmp/policies", "DIRECTORY");
            val json         = original.toJson();
            val deserialized = BenchmarkContext.fromJson(json);
            assertThat(deserialized).satisfies(ctx -> {
                assertThat(ctx.subscriptionJson()).isEqualTo("{\"subject\":\"alice\"}");
                assertThat(ctx.policiesPath()).isEqualTo("/tmp/policies");
                assertThat(ctx.configType()).isEqualTo("DIRECTORY");
            });
        }

        @Test
        @DisplayName("serializes BUNDLES config type")
        void whenBundlesType_thenPreservedInRoundTrip() {
            val original     = new BenchmarkContext("{}", "/bundles", "BUNDLES");
            val deserialized = BenchmarkContext.fromJson(original.toJson());
            assertThat(deserialized.configType()).isEqualTo("BUNDLES");
        }

        @Test
        @DisplayName("toJson produces valid JSON containing all field values")
        void whenToJson_thenContainsAllFields() {
            val ctx  = new BenchmarkContext("{\"sub\":1}", "/path", "DIRECTORY");
            val json = ctx.toJson();
            assertThat(json).contains("subscriptionJson", "policiesPath", "configType", "/path", "DIRECTORY");
        }

    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("fromJson rejects malformed JSON")
        void whenMalformedJson_thenThrows() {
            assertThatThrownBy(() -> BenchmarkContext.fromJson("not valid json")).isInstanceOf(RuntimeException.class);
        }

    }

}
