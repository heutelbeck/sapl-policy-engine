/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.PDPConfiguration;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class ResourcesPDPConfigurationSourceTests {

    @Test
    void whenLoadingSinglePdpFromRootLevelFiles_thenCallbackIsInvokedOnce() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        val source = new ResourcesPDPConfigurationSource("/single-pdp-policies", configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("default");
        assertThat(configs.getFirst().combiningAlgorithm()).isEqualTo(CombiningAlgorithm.PERMIT_OVERRIDES);
        assertThat(configs.getFirst().saplDocuments()).hasSize(1);

        source.dispose();
    }

    @Test
    void whenLoadingMultiPdpFromSubdirectories_thenCallbackIsInvokedPerSubdirectory() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        val source = new ResourcesPDPConfigurationSource("/multi-pdp-policies", configs::add);

        assertThat(configs).hasSize(2);

        val pdpIds = configs.stream().map(PDPConfiguration::pdpId).toList();
        assertThat(pdpIds).containsExactlyInAnyOrder("production", "development");

        val productionConfig = configs.stream().filter(c -> "production".equals(c.pdpId())).findFirst().orElseThrow();
        assertThat(productionConfig.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DENY_OVERRIDES);
        assertThat(productionConfig.saplDocuments()).hasSize(1);

        val developmentConfig = configs.stream().filter(c -> "development".equals(c.pdpId())).findFirst().orElseThrow();
        assertThat(developmentConfig.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.PERMIT_UNLESS_DENY);
        assertThat(developmentConfig.saplDocuments()).hasSize(1);

        source.dispose();
    }

    @Test
    void whenLoadingMixedSetup_thenCallbackIsInvokedForBothDefaultAndSubdirectories() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        val source = new ResourcesPDPConfigurationSource("/mixed-pdp-policies", configs::add);

        assertThat(configs).hasSize(2);

        val pdpIds = configs.stream().map(PDPConfiguration::pdpId).toList();
        assertThat(pdpIds).containsExactlyInAnyOrder("default", "tenant-a");

        val defaultConfig = configs.stream().filter(c -> "default".equals(c.pdpId())).findFirst().orElseThrow();
        assertThat(defaultConfig.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DENY_OVERRIDES);

        val tenantConfig = configs.stream().filter(c -> "tenant-a".equals(c.pdpId())).findFirst().orElseThrow();
        assertThat(tenantConfig.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.PERMIT_OVERRIDES);

        source.dispose();
    }

    @Test
    void whenLoadingFromEmptyResourcePath_thenCallbackIsNotInvoked() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        val source = new ResourcesPDPConfigurationSource("/empty-policies", configs::add);

        assertThat(configs).isEmpty();

        source.dispose();
    }

    @Test
    void whenProvidingCustomConfigurationId_thenConfigurationsHaveCorrectId() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        val source = new ResourcesPDPConfigurationSource("/single-pdp-policies", "eldritch-config", configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().configurationId()).isEqualTo("eldritch-config");

        source.dispose();
    }

    @Test
    void whenResourcePathHasLeadingSlash_thenNormalizationHandlesIt() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        val source = new ResourcesPDPConfigurationSource("/single-pdp-policies", configs::add);

        assertThat(configs).isNotEmpty();

        source.dispose();
    }

    @Test
    void whenResourcePathHasNoLeadingSlash_thenNormalizationHandlesIt() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        val source = new ResourcesPDPConfigurationSource("single-pdp-policies", configs::add);

        assertThat(configs).isNotEmpty();

        source.dispose();
    }

    @Test
    void whenDisposeIsCalled_thenIsDisposedReturnsTrue() {
        val source = new ResourcesPDPConfigurationSource("/single-pdp-policies", config -> {});

        assertThat(source.isDisposed()).isFalse();

        source.dispose();

        assertThat(source.isDisposed()).isTrue();
    }

    @Test
    void whenDisposeIsCalledTwice_thenIsIdempotent() {
        val source = new ResourcesPDPConfigurationSource("/single-pdp-policies", config -> {});

        source.dispose();
        source.dispose();

        assertThat(source.isDisposed()).isTrue();
    }

}
