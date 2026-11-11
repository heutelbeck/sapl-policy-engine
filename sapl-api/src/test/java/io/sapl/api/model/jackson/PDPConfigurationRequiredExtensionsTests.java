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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;

import lombok.val;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@DisplayName("Required extensions survive configuration JSON transport")
class PDPConfigurationRequiredExtensionsTests {

    private static final JsonMapper MAPPER             = JsonMapper.builder().addModule(new SaplJacksonModule())
            .build();
    private static final String     CONFIGURATION_JSON = """
            {"pdpId":"arkham", "configurationId":"revision", "combiningAlgorithm":
              {"votingMode":"UNIQUE", "defaultDecision":"DENY", "errorHandling":"PROPAGATE"}}
            """;

    @Test
    @DisplayName("Required extension names are serialized deterministically and survive reading")
    void whenRequiredExtensionsAreSerializedThenNamesAreSortedAndPreserved() {
        val configuration = new PDPConfiguration("arkham", "revision", CombiningAlgorithm.DEFAULT, List.of(),
                new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT))
                .withExtensions(Map.of(), Map.of(), Set.of("upstreams", "audit"));
        val document      = MAPPER.readTree(MAPPER.writeValueAsString(configuration));

        assertThat(document.path("requiredExtensions").toString()).isEqualTo("[\"audit\",\"upstreams\"]");
        assertThat(MAPPER.readValue(document.toString(), PDPConfiguration.class)).isEqualTo(configuration);
    }

    @Test
    @DisplayName("An explicit requiredExtensions field is read into the configuration")
    void whenRequiredExtensionsAreDeclaredThenTheirNamesAreRetained() {
        val configuration = MAPPER.readValue(configurationJsonWith("requiredExtensions", "[\"upstreams\"]"),
                PDPConfiguration.class);
        assertThat(configuration.requiredExtensions()).containsExactly("upstreams");
    }

    @Test
    @DisplayName("An omitted requiredExtensions field means no required extensions")
    void whenRequiredExtensionsAreAbsentThenTheSetIsEmpty() {
        val configuration = MAPPER.readValue(CONFIGURATION_JSON, PDPConfiguration.class);
        assertThat(configuration.requiredExtensions()).isEmpty();
    }

    @DisplayName("Required extensions must be an array of names")
    @ParameterizedTest(name = "Invalid requiredExtensions value {0}")
    @ValueSource(strings = { "null", "true", "\"upstreams\"", "{}", "[1]", "[\"upstreams\", null]" })
    void whenRequiredExtensionsHaveInvalidShapeThenReadingFails(String declaration) {
        val json   = configurationJsonWith("requiredExtensions", declaration);
        val decode = (ThrowingCallable) () -> MAPPER.readValue(json, PDPConfiguration.class);
        assertThatThrownBy(decode).hasMessageContaining("requiredExtensions");
    }

    @Test
    @DisplayName("Unknown configuration fields are rejected so requirements cannot be silently lost")
    void whenConfigurationHasUnknownFieldThenReadingFails() {
        val json   = configurationJsonWith("unknownRequirement", "[]");
        val decode = (ThrowingCallable) () -> MAPPER.readValue(json, PDPConfiguration.class);
        assertThatThrownBy(decode).hasMessageContaining("unknownRequirement");
    }

    private static String configurationJsonWith(String fieldName, String jsonValue) {
        val document = (ObjectNode) MAPPER.readTree(CONFIGURATION_JSON);
        document.set(fieldName, MAPPER.readTree(jsonValue));
        return document.toString();
    }

}
