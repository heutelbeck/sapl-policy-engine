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

import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.pdp.configuration.PdpVoterSource;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("ResourcesPDPConfigurationSource")
@ExtendWith(MockitoExtension.class)
class ResourcesPDPConfigurationSourceTests {

    private static final CombiningAlgorithm PERMIT_OVERRIDES   = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT,
            DefaultDecision.PERMIT, ErrorHandling.PROPAGATE);
    private static final CombiningAlgorithm DENY_OVERRIDES     = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.DENY, ErrorHandling.PROPAGATE);
    private static final CombiningAlgorithm PERMIT_UNLESS_DENY = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.PERMIT, ErrorHandling.ABSTAIN);

    @Mock
    PdpVoterSource pdpVoterSource;

    private CopyOnWriteArrayList<PDPConfiguration> captureConfigurations() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();
        doAnswer(inv -> {
            configs.add(inv.getArgument(0));
            return null;
        }).when(pdpVoterSource).loadConfiguration(any(), eq(true));
        return configs;
    }

    @Test
    void whenLoadingSinglePdpFromRootLevelFilesThenVoterSourceReceivesOneConfig() {
        val configs = captureConfigurations();

        val source = new ResourcesPDPConfigurationSource("/single-pdp-policies", pdpVoterSource);

        assertThat(configs).hasSize(1).first().satisfies(config -> {
            assertThat(config.pdpId()).isEqualTo("default");
            assertThat(config.combiningAlgorithm()).isEqualTo(PERMIT_OVERRIDES);
            assertThat(config.saplDocuments()).hasSize(1);
        });

        source.dispose();
    }

    @Test
    void whenLoadingMultiPdpFromSubdirectoriesThenVoterSourceReceivesConfigPerSubdirectory() {
        val configs = captureConfigurations();

        val source = new ResourcesPDPConfigurationSource("/multi-pdp-policies", pdpVoterSource);

        assertThat(configs).hasSize(2);

        val pdpIds = configs.stream().map(PDPConfiguration::pdpId).toList();
        assertThat(pdpIds).containsExactlyInAnyOrder("production", "development");

        val productionConfig = configs.stream().filter(c -> "production".equals(c.pdpId())).findFirst().orElseThrow();
        assertThat(productionConfig.combiningAlgorithm()).isEqualTo(DENY_OVERRIDES);
        assertThat(productionConfig.saplDocuments()).hasSize(1);

        val developmentConfig = configs.stream().filter(c -> "development".equals(c.pdpId())).findFirst().orElseThrow();
        assertThat(developmentConfig.combiningAlgorithm()).isEqualTo(PERMIT_UNLESS_DENY);
        assertThat(developmentConfig.saplDocuments()).hasSize(1);

        source.dispose();
    }

    @Test
    void whenLoadingMixedSetupThenVoterSourceReceivesConfigForBothDefaultAndSubdirectories() {
        val configs = captureConfigurations();

        val source = new ResourcesPDPConfigurationSource("/mixed-pdp-policies", pdpVoterSource);

        assertThat(configs).hasSize(2);

        val pdpIds = configs.stream().map(PDPConfiguration::pdpId).toList();
        assertThat(pdpIds).containsExactlyInAnyOrder("default", "tenant-a");

        val defaultConfig = configs.stream().filter(c -> "default".equals(c.pdpId())).findFirst().orElseThrow();
        assertThat(defaultConfig.combiningAlgorithm()).isEqualTo(DENY_OVERRIDES);

        val tenantConfig = configs.stream().filter(c -> "tenant-a".equals(c.pdpId())).findFirst().orElseThrow();
        assertThat(tenantConfig.combiningAlgorithm()).isEqualTo(PERMIT_OVERRIDES);

        source.dispose();
    }

    @Test
    void whenLoadingFromEmptyOrNonExistentPathThenVoterSourceNotInvoked() {
        val emptySource       = new ResourcesPDPConfigurationSource("/empty-policies", pdpVoterSource);
        val nonExistentSource = new ResourcesPDPConfigurationSource("/non-existent-path", pdpVoterSource);

        verifyNoInteractions(pdpVoterSource);

        emptySource.dispose();
        nonExistentSource.dispose();
    }

    @Test
    void whenNoPdpJsonConfigurationIdThenAutoGeneratesResourceId() {
        val configs = captureConfigurations();

        val source = new ResourcesPDPConfigurationSource("/single-pdp-policies", pdpVoterSource);

        assertThat(configs).hasSize(1);
        // Auto-generated format: res:<path>@sha256:<hash>
        assertThat(configs.getFirst().configurationId()).startsWith("res:");
        assertThat(configs.getFirst().configurationId()).contains("@sha256:");

        source.dispose();
    }

    @ParameterizedTest(name = "path \"{0}\" should load single-pdp-policies")
    @ValueSource(strings = { "/single-pdp-policies", "single-pdp-policies", "/single-pdp-policies/",
            "single-pdp-policies/" })
    void whenResourcePathHasVariousSlashFormatsThenNormalizationHandlesIt(String resourcePath) {
        val configs = captureConfigurations();

        val source = new ResourcesPDPConfigurationSource(resourcePath, pdpVoterSource);

        assertThat(configs).isNotEmpty().first().extracting(PDPConfiguration::pdpId).isEqualTo("default");

        source.dispose();
    }

    @Test
    void whenDisposeIsCalledThenIsDisposedReturnsTrue() {
        captureConfigurations();

        val source = new ResourcesPDPConfigurationSource("/single-pdp-policies", pdpVoterSource);

        assertThat(source.isDisposed()).isFalse();

        source.dispose();

        assertThat(source.isDisposed()).isTrue();
    }

    @Test
    void whenDisposeIsCalledTwiceThenIsIdempotent() {
        captureConfigurations();

        val source = new ResourcesPDPConfigurationSource("/single-pdp-policies", pdpVoterSource);

        source.dispose();
        source.dispose();

        assertThat(source.isDisposed()).isTrue();
    }

}
