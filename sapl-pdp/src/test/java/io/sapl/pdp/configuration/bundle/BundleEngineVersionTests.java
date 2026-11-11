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
package io.sapl.pdp.configuration.bundle;

import static io.sapl.pdp.configuration.bundle.BundleManifest.attributionOfText;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.api.pdp.configuration.UnknownProvenance;
import io.sapl.pdp.configuration.PDPConfigurationLoader;

import lombok.val;

@ExtendWith(MockitoExtension.class)
@DisplayName("Bundle provenance preserves optional engine version requirements")
class BundleEngineVersionTests {

    @Mock
    private MockedStatic<PDPConfigurationLoader> configurationLoader;

    @NullSource
    @ValueSource(strings = { ">=4.2.0", ">=4.2.0-0" })
    @ParameterizedTest(name = "Preserve engine requirement {0} while adding bundle provenance")
    @DisplayName("Adding bundle provenance retains the loaded configuration's engine requirement")
    void whenBundleProvenanceIsAddedThenEngineRequirementIsPreserved(String requirement) {
        val documents = Map.<String, String>of();
        val data      = new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        val loaded    = new PDPConfiguration("pdp", "config", CombiningAlgorithm.DEFAULT, Value.EMPTY_OBJECT, List.of(),
                data, Map.of(), Map.of(), Set.of(), UnknownProvenance.INSTANCE, requirement);
        val manifest  = BundleManifest.builder().created(Instant.parse("2026-01-01T00:00:00Z"))
                .configurationId("config").attribution(attributionOfText("engine-version-tests")).files(Map.of())
                .buildUnsigned();
        val bundle    = new Bundle("{}", null, false, documents, Map.of(), Map.of(), Map.of(), manifest);
        val policy    = BundleSecurityPolicy.builder().disableSignatureVerification().build();
        configurationLoader.when(() -> PDPConfigurationLoader.loadFromBundle("{}", null, documents, "pdp", "config"))
                .thenReturn(loaded);

        val converted = bundle.toPDPConfiguration("pdp", policy);

        assertThat(converted).isNotSameAs(loaded).returns(requirement, PDPConfiguration::requiredEngineVersion)
                .extracting(PDPConfiguration::provenance).isInstanceOf(BundleProvenance.class);
    }
}
