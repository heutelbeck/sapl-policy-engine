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
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.api.pdp.configuration.PdpRuntime;
import io.sapl.api.pdp.configuration.UnknownProvenance;

import lombok.val;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Configuration JSON preserves the runtimes a configuration expects")
class PDPConfigurationExpectedRuntimeTests {

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
              "secrets": {}%s
            }
            """;

    @MethodSource
    @ParameterizedTest(name = "{0}")
    @DisplayName("Reading configuration JSON yields the expected runtimes, none if the field is absent, null or empty")
    void whenReadingConfigurationThenExpectedRuntimesArePreserved(String description, String field,
            Set<PdpRuntime> expected) {
        val configuration = MAPPER.readValue(CONFIGURATION_JSON.formatted(field), PDPConfiguration.class);

        assertThat(configuration.expectedPdpRuntime()).isEqualTo(expected);
    }

    static Stream<Arguments> whenReadingConfigurationThenExpectedRuntimesArePreserved() {
        return Stream.of(arguments("no field", "", Set.of()), arguments("null", """
                , "expectedPdpRuntime": null""", Set.of()), arguments("an empty array", """
                , "expectedPdpRuntime": []""", Set.of()), arguments("the Java runtime", """
                , "expectedPdpRuntime": ["java"]""", Set.of(PdpRuntime.JAVA)), arguments("both runtimes", """
                , "expectedPdpRuntime": ["rust", "java"]""", Set.of(PdpRuntime.JAVA, PdpRuntime.RUST)),
                arguments("a runtime named twice", """
                        , "expectedPdpRuntime": ["rust", "rust"]""", Set.of(PdpRuntime.RUST)));
    }

    @ParameterizedTest(name = "reject {0}")
    @DisplayName("Reading configuration JSON rejects anything but an array of the names of runtimes")
    @ValueSource(strings = { "\"java\"", "42", "{}", "[1]", "[\"java\", null]", "[\"Java\"]", "[\"go\"]",
            "[[\"java\"]]" })
    void whenExpectedRuntimesAreMalformedThenDeserializationRejectsThem(String literal) {
        val json = CONFIGURATION_JSON.formatted(", \"expectedPdpRuntime\": " + literal);

        assertThatThrownBy(() -> MAPPER.readValue(json, PDPConfiguration.class)).isInstanceOf(JacksonException.class)
                .hasMessageContaining("expectedPdpRuntime must be an array of the runtime names 'java' and 'rust'.");
    }

    @Test
    @DisplayName("Writing and reading a configuration preserves its expected runtimes, written in sorted order")
    void whenConfigurationRoundTripsThenExpectedRuntimesArePreserved() {
        val data          = new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        val configuration = new PDPConfiguration("pdp", "config", CombiningAlgorithm.DEFAULT, Value.EMPTY_OBJECT,
                List.of(), data, Map.of(), Map.of(), Set.of(), UnknownProvenance.INSTANCE, null,
                Set.of(PdpRuntime.RUST, PdpRuntime.JAVA));

        val json     = MAPPER.writeValueAsString(configuration);
        val restored = MAPPER.readValue(json, PDPConfiguration.class);

        assertThat(MAPPER.readTree(json).get("expectedPdpRuntime")).hasToString("[\"java\",\"rust\"]");
        assertThat(restored.expectedPdpRuntime()).containsExactlyInAnyOrder(PdpRuntime.JAVA, PdpRuntime.RUST);
    }

    @Test
    @DisplayName("A configuration that expects no particular runtime omits the field from JSON")
    void whenNoRuntimeIsExpectedThenSerializationOmitsTheField() {
        val data          = new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        val configuration = new PDPConfiguration("pdp", "config", CombiningAlgorithm.DEFAULT, List.of(), data);

        val json = MAPPER.readTree(MAPPER.writeValueAsString(configuration));

        assertThat(json.has("expectedPdpRuntime")).isFalse();
    }

}
