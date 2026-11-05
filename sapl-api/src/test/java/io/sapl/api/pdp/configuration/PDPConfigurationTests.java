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
package io.sapl.api.pdp.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.api.model.Value;

import lombok.val;

@DisplayName("PDP configurations carry an optional engine version requirement")
class PDPConfigurationTests {

    @Test
    @DisplayName("Existing constructors leave the engine version requirement undeclared")
    void whenNoEngineRequirementIsDeclaredThenExistingConstructorsLeaveItAbsent() {
        val data           = new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        val configurations = List.of(new PDPConfiguration("pdp", "config", CombiningAlgorithm.DEFAULT, List.of(), data),
                new PDPConfiguration("pdp", "config", CombiningAlgorithm.DEFAULT, Value.EMPTY_OBJECT, List.of(), data),
                new PDPConfiguration("pdp", "config", CombiningAlgorithm.DEFAULT, Value.EMPTY_OBJECT, List.of(), data,
                        Map.of(), Map.of(), Set.of()),
                new PDPConfiguration("pdp", "config", CombiningAlgorithm.DEFAULT, Value.EMPTY_OBJECT, List.of(), data,
                        Map.of(), Map.of(), Set.of(), UnknownProvenance.INSTANCE));

        assertThat(configurations).extracting(PDPConfiguration::requiredEngineVersion).containsOnlyNulls();
    }

    @NullSource
    @ValueSource(strings = { ">=4.2.0", ">=4.2.0-0" })
    @ParameterizedTest(name = "Extension updates preserve engine requirement {0}")
    @DisplayName("Changing extension data preserves the engine version requirement")
    void whenExtensionDataChangesThenEngineRequirementIsPreserved(String requirement) {
        val data          = new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        val configuration = new PDPConfiguration("pdp", "config", CombiningAlgorithm.DEFAULT, Value.EMPTY_OBJECT,
                List.of(), data, Map.of(), Map.of(), Set.of(), UnknownProvenance.INSTANCE, requirement);
        val extensions    = Map.<String, Value>of("audit", Value.of("enabled"));
        val changed       = configuration.withExtensions(extensions, Map.of());
        val required      = configuration.withExtensions(extensions, Map.of(), Set.of("audit"));

        assertThat(List.of(configuration, changed, required)).extracting(PDPConfiguration::requiredEngineVersion)
                .containsExactly(requirement, requirement, requirement);
    }

    @Nested
    @DisplayName("Engine version range syntax")
    class EngineVersionRangeSyntaxTests {

        @NullSource
        @ParameterizedTest(name = "Accept engine requirement {0}")
        @DisplayName("Valid ranges and an undeclared requirement are preserved exactly")
        @ValueSource(strings = { "4.2.0", "^4.2.0", "~4.2.0", ">=4.2.0 <5.0.0", "4.x", "4.2.0 - 4.3.0",
                ">=4.2.0 || <3.0.0", "*", ">=4.2.0-0", ">= 4.2.0 < 5.0.0", ">=99.0.0", "  >=4.2.0  " })
        void whenEngineRequirementIsValidOrAbsentThenConstructionPreservesIt(String requirement) {
            val configuration = configurationWithEngineRequirement(requirement);

            assertThat(configuration.requiredEngineVersion()).isEqualTo(requirement);
        }

        @ParameterizedTest(name = "Reject engine requirement {0}")
        @DisplayName("Invalid ranges are rejected when constructing a configuration")
        @ValueSource(strings = { "", " ", "\t", ">=", "not-a-range", "4.2.0 garbage", "^4.2.0 garbage",
                "~4.2.0 garbage", "5.0.01", ">=04.2.0", ">=4.2.0 ||", "|| >=4.2.0", ">=4.2.0 || || <5.0.0" })
        void whenEngineRequirementIsInvalidThenConstructionRejectsIt(String requirement) {
            assertThatThrownBy(() -> configurationWithEngineRequirement(requirement))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requiredEngineVersion");
        }
    }

    private static PDPConfiguration configurationWithEngineRequirement(String requirement) {
        val data = new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        return new PDPConfiguration("pdp", "config", CombiningAlgorithm.DEFAULT, Value.EMPTY_OBJECT, List.of(), data,
                Map.of(), Map.of(), Set.of(), UnknownProvenance.INSTANCE, requirement);
    }
}
