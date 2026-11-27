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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import io.sapl.api.model.Value;
import lombok.val;

@DisplayName("DefaultResponseUtility")
class DefaultResponseUtilityTests {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    // Operator pip config whose single-object brokerConfig (resolved when the
    // policy supplies no broker selector) carries the given malformed field.
    private static ObjectNode pipConfigWithBrokerField(String field, JsonNode malformedValue) {
        val broker = JSON.objectNode().set(field, malformedValue);
        return JSON.objectNode().set("brokerConfig", broker);
    }

    @Nested
    @DisplayName("Malformed broker config falls back to defaults")
    class MalformedConfigFallsBack {

        @Test
        @DisplayName("a non-numeric defaultResponseTimeout falls back to the 2000ms default instead of throwing")
        void whenDefaultResponseTimeoutIsMalformedThenDefaultTimeoutUsed() {
            val pipConfig = pipConfigWithBrokerField("defaultResponseTimeout", JSON.objectNode());

            val config = DefaultResponseUtility.getDefaultResponseConfig(pipConfig, Value.UNDEFINED);

            assertThat(config).satisfies(c -> assertThat(c.getDefaultResponseTimeout()).isEqualTo(2000L));
        }

        @Test
        @DisplayName("a non-textual defaultResponse falls back to the undefined default instead of throwing")
        void whenDefaultResponseIsMalformedThenUndefinedDefaultUsed() {
            val pipConfig = pipConfigWithBrokerField("defaultResponse", JSON.arrayNode());

            val config = DefaultResponseUtility.getDefaultResponseConfig(pipConfig, Value.UNDEFINED);

            assertThat(config)
                    .satisfies(c -> assertThat(DefaultResponseUtility.getDefaultValue(c)).isEqualTo(Value.UNDEFINED));
        }
    }
}
