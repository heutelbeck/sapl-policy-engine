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
package io.sapl.pdp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.api.pdp.configuration.UnknownProvenance;
import io.sapl.secrets.Keyring;

import lombok.val;

@DisplayName("Secrets processing preserves optional engine version requirements")
class SecretsUnsealingEngineVersionTests {

    @NullSource
    @ValueSource(strings = { ">=4.2.0", ">=4.2.0-0" })
    @ParameterizedTest(name = "Preserve engine requirement {0} while processing secrets")
    @DisplayName("Creating a configuration with processed secrets retains the declared engine requirement")
    void whenSecretsAreProcessedThenEngineRequirementIsPreserved(String requirement) {
        val data          = new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        val configuration = new PDPConfiguration("pdp", "config", CombiningAlgorithm.DEFAULT, Value.EMPTY_OBJECT,
                List.of(), data, Map.of(), Map.of(), Set.of(), UnknownProvenance.INSTANCE, requirement);

        val processed = SecretsUnsealing.process(Keyring.of(), true, configuration);

        assertThat(processed).isNotSameAs(configuration).returns(requirement, PDPConfiguration::requiredEngineVersion);
    }
}
