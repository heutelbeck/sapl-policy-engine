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
package io.sapl.pdp.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.api.pdp.configuration.UnknownProvenance;

import lombok.val;

@DisplayName("Configuration hashes include declared engine version requirements")
class ConfigurationIdsEngineVersionTests {

    private static final String HASH_WITHOUT_ENGINE_REQUIREMENT = "fab3e13632399ef3";

    @Test
    @DisplayName("An undeclared engine requirement uses the default configuration hash")
    void whenEngineRequirementIsAbsentThenDefaultHashIsUsed() {
        val actual = hash(configuration(null));

        assertThat(actual).isEqualTo(HASH_WITHOUT_ENGINE_REQUIREMENT);
    }

    @ValueSource(strings = { ">=4.2.0", "^4.2.0", ">=4.2.0-0", "*" })
    @ParameterizedTest(name = "Declaring requirement {0} changes the hash")
    @DisplayName("Declaring an engine requirement changes the configuration identity")
    void whenEngineRequirementIsDeclaredThenHashDiffersFromUndeclared(String requirement) {
        val undeclared = hash(configuration(null));
        val declared   = hash(configuration(requirement));

        assertThat(declared).isNotEqualTo(undeclared);
    }

    @ValueSource(strings = { "^4.2.0", ">=4.2.0-0", "  >=4.2.0  " })
    @ParameterizedTest(name = "Changing the declaration to {0} changes the hash")
    @DisplayName("Different engine requirement declarations have different configuration identities")
    void whenEngineRequirementDeclarationChangesThenHashChanges(String replacement) {
        val original = hash(configuration(">=4.2.0"));
        val changed  = hash(configuration(replacement));

        assertThat(changed).isNotEqualTo(original);
    }

    private static String hash(PDPConfiguration configuration) {
        return ConfigurationIds.contentHash16(ConfigurationIds.entriesOf(configuration));
    }

    private static PDPConfiguration configuration(String requirement) {
        val data = new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        return new PDPConfiguration("pdp", "config", CombiningAlgorithm.DEFAULT, Value.EMPTY_OBJECT, List.of(), data,
                Map.of(), Map.of(), Set.of(), UnknownProvenance.INSTANCE, requirement);
    }
}
