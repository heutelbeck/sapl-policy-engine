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
package io.sapl.pdp.configuration.source;

import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.pdp.configuration.PDPConfigurationException;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ResourcesPDPConfigurationSource")
class ResourcesPDPConfigurationSourceTests {

    private static final CombiningAlgorithm PERMIT_OVERRIDES   = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT,
            DefaultDecision.PERMIT, ErrorHandling.PROPAGATE);
    private static final CombiningAlgorithm DENY_OVERRIDES     = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.DENY, ErrorHandling.PROPAGATE);
    private static final CombiningAlgorithm PERMIT_UNLESS_DENY = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.PERMIT, ErrorHandling.ABSTAIN);

    private List<PDPConfiguration> captureConfigurations(PDPConfigurationSource src) {
        val capture = new CapturingSubscriber();
        src.subscribe(capture);
        return capture.configs();
    }

    @Test
    void whenLoadingSinglePdpFromRootLevelFilesThenVoterSourceReceivesOneConfig() {
        val source  = new ResourcesPDPConfigurationSource("/single-pdp-policies");
        val configs = captureConfigurations(source);

        assertThat(configs).hasSize(1).first().satisfies(config -> {
            assertThat(config.pdpId()).isEqualTo("default");
            assertThat(config.combiningAlgorithm()).isEqualTo(PERMIT_OVERRIDES);
            assertThat(config.saplDocuments()).hasSize(1);
        });

        source.close();
    }

    @Test
    void whenLoadingMultiPdpFromSubdirectoriesThenVoterSourceReceivesConfigPerSubdirectory() {
        val source = new ResourcesPDPConfigurationSource("/multi-pdp-policies");

        val configs = captureConfigurations(source);
        assertThat(configs).hasSize(2);

        val pdpIds = configs.stream().map(PDPConfiguration::pdpId).toList();
        assertThat(pdpIds).containsExactlyInAnyOrder("production", "development");

        val productionConfig = configs.stream().filter(c -> "production".equals(c.pdpId())).findFirst().orElseThrow();
        assertThat(productionConfig.combiningAlgorithm()).isEqualTo(DENY_OVERRIDES);
        assertThat(productionConfig.saplDocuments()).hasSize(1);

        val developmentConfig = configs.stream().filter(c -> "development".equals(c.pdpId())).findFirst().orElseThrow();
        assertThat(developmentConfig.combiningAlgorithm()).isEqualTo(PERMIT_UNLESS_DENY);
        assertThat(developmentConfig.saplDocuments()).hasSize(1);

        source.close();
    }

    @Test
    void whenLoadingMixedSetupThenVoterSourceReceivesConfigForBothDefaultAndSubdirectories() {
        val source = new ResourcesPDPConfigurationSource("/mixed-pdp-policies");

        val configs = captureConfigurations(source);
        assertThat(configs).hasSize(2);

        val pdpIds = configs.stream().map(PDPConfiguration::pdpId).toList();
        assertThat(pdpIds).containsExactlyInAnyOrder("default", "tenant-a");

        val defaultConfig = configs.stream().filter(c -> "default".equals(c.pdpId())).findFirst().orElseThrow();
        assertThat(defaultConfig.combiningAlgorithm()).isEqualTo(DENY_OVERRIDES);

        val tenantConfig = configs.stream().filter(c -> "tenant-a".equals(c.pdpId())).findFirst().orElseThrow();
        assertThat(tenantConfig.combiningAlgorithm()).isEqualTo(PERMIT_OVERRIDES);

        source.close();
    }

    @ParameterizedTest(name = "path \"{0}\" with no configurations fails fast")
    @ValueSource(strings = { "/empty-policies", "/non-existent-path" })
    void whenLoadingFromEmptyOrNonExistentPathThenFailsFast(String path) {
        val source  = new ResourcesPDPConfigurationSource(path);
        val capture = new CapturingSubscriber();

        assertThatThrownBy(() -> source.subscribe(capture)).isInstanceOf(PDPConfigurationException.class)
                .hasMessageContaining(path);
    }

    @Test
    void whenNoPdpJsonConfigurationIdThenAutoGeneratesResourceId() {
        val source = new ResourcesPDPConfigurationSource("/single-pdp-policies");

        val configs = captureConfigurations(source);
        assertThat(configs).hasSize(1);
        // Auto-generated format: res:<path>@sha256:<hash>
        assertThat(configs.getFirst().configurationId()).startsWith("res:");
        assertThat(configs.getFirst().configurationId()).contains("@sha256:");

        source.close();
    }

    @ParameterizedTest(name = "path \"{0}\" should load single-pdp-policies")
    @ValueSource(strings = { "/single-pdp-policies", "single-pdp-policies", "/single-pdp-policies/",
            "single-pdp-policies/" })
    void whenResourcePathHasVariousSlashFormatsThenNormalizationHandlesIt(String resourcePath) {
        val source = new ResourcesPDPConfigurationSource(resourcePath);

        val configs = captureConfigurations(source);
        assertThat(configs).isNotEmpty().first().extracting(PDPConfiguration::pdpId).isEqualTo("default");

        source.close();
    }

    @Test
    void whenDisposeIsCalledThenIsDisposedReturnsTrue() {
        val source = new ResourcesPDPConfigurationSource("/single-pdp-policies");

        val configs = captureConfigurations(source);
        assertThat(source.isClosed()).isFalse();

        source.close();

        assertThat(source.isClosed()).isTrue();
    }

    @Test
    void whenDisposeIsCalledTwiceThenIsIdempotent() {
        val source = new ResourcesPDPConfigurationSource("/single-pdp-policies");

        captureConfigurations(source);

        source.close();
        source.close();

        assertThat(source.isClosed()).isTrue();
    }

}
