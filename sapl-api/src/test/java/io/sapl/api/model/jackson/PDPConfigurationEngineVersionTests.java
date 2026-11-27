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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.api.pdp.configuration.UnknownProvenance;

import lombok.val;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@DisplayName("Configuration JSON preserves optional engine version requirements")
class PDPConfigurationEngineVersionTests {

    private static final JsonMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    private static final String CONFIGURATION_JSON = """
            {
              "pdpId": "pdp",
              "configurationId": "config",
              "combiningAlgorithm": {
                "votingMode": "PRIORITY_DENY",
                "defaultDecision": "DENY",
                "errorHandling": "PROPAGATE"
              },
              "saplDocuments": [],
              "variables": {},
              "secrets": {}
            }
            """;

    @NullSource
    @ValueSource(strings = { ">=4.2.0", "^4.2.0", ">=4.2.0-0" })
    @DisplayName("Reading configuration JSON preserves a declared requirement or its absence")
    @ParameterizedTest(name = "Read engine requirement {0}, with null representing a missing field")
    void whenReadingConfigurationThenOptionalEngineRequirementIsPreserved(String requirement) {
        val document = (ObjectNode) MAPPER.readTree(CONFIGURATION_JSON);
        if (requirement != null) {
            document.put("requiredEngineVersion", requirement);
        }

        val configuration = MAPPER.readValue(document.toString(), PDPConfiguration.class);

        assertThat(configuration.requiredEngineVersion()).isEqualTo(requirement);
    }

    @NullSource
    @ValueSource(strings = { ">=4.2.0", "^4.2.0", ">=4.2.0-0" })
    @DisplayName("Writing and reading a configuration preserves its optional engine requirement")
    @ParameterizedTest(name = "Round trip engine requirement {0}, with null representing no declaration")
    void whenConfigurationRoundTripsThenOptionalEngineRequirementIsPreserved(String requirement) {
        val data          = new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        val configuration = new PDPConfiguration("pdp", "config", CombiningAlgorithm.DEFAULT, Value.EMPTY_OBJECT,
                List.of(), data, Map.of(), Map.of(), Set.of(), UnknownProvenance.INSTANCE, requirement);

        val json     = MAPPER.writeValueAsString(configuration);
        val restored = MAPPER.readValue(json, PDPConfiguration.class);

        assertThat(restored.requiredEngineVersion()).isEqualTo(requirement);
    }

    @Test
    @DisplayName("An explicit null engine requirement is treated as undeclared")
    void whenEngineRequirementIsNullThenDeserializationLeavesItUndeclared() {
        val document = (ObjectNode) MAPPER.readTree(CONFIGURATION_JSON);
        document.putNull("requiredEngineVersion");

        val configuration = MAPPER.readValue(document.toString(), PDPConfiguration.class);

        assertThat(configuration.requiredEngineVersion()).isNull();
    }

    @Test
    @DisplayName("An undeclared engine requirement is omitted from JSON")
    void whenEngineRequirementIsUndeclaredThenSerializationOmitsIt() {
        val data          = new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        val configuration = new PDPConfiguration("pdp", "config", CombiningAlgorithm.DEFAULT, List.of(), data);

        val json = MAPPER.readTree(MAPPER.writeValueAsString(configuration));

        assertThat(json.has("requiredEngineVersion")).isFalse();
    }

    @ParameterizedTest(name = "Reject malformed engine requirement {0} in JSON")
    @DisplayName("The record rejects malformed version ranges during deserialization")
    @ValueSource(strings = { "", "not-a-range", "5.0.01", ">=4.2.0 garbage", ">=4.2.0 ||" })
    void whenEngineRequirementIsMalformedThenDeserializationRejectsIt(String requirement) {
        val document = (ObjectNode) MAPPER.readTree(CONFIGURATION_JSON);
        document.put("requiredEngineVersion", requirement);
        val json = document.toString();

        assertThatThrownBy(() -> MAPPER.readValue(json, PDPConfiguration.class))
                .hasMessageContaining("requiredEngineVersion");
    }

    @ValueSource(strings = { "42", "true", "[]", "{}" })
    @ParameterizedTest(name = "Reject nonstring engine requirement {0}")
    @DisplayName("An engine requirement in JSON must be a string or null")
    void whenEngineRequirementHasWrongJsonTypeThenDeserializationRejectsIt(String value) {
        val document = (ObjectNode) MAPPER.readTree(CONFIGURATION_JSON);
        document.set("requiredEngineVersion", MAPPER.readTree(value));
        val json = document.toString();

        assertThatThrownBy(() -> MAPPER.readValue(json, PDPConfiguration.class))
                .hasMessageContaining("requiredEngineVersion");
    }
}
