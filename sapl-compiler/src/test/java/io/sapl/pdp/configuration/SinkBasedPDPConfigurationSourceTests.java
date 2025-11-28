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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SinkBasedPDPConfigurationSourceTests {

    @Test
    void whenCreated_thenAvailablePdpIdsIsEmpty() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();
        val source  = new SinkBasedPDPConfigurationSource(configs::add);

        assertThat(source.getAvailablePdpIds()).isEmpty();

        source.dispose();
    }

    @Test
    void whenPushingConfiguration_thenCallbackIsInvoked() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();
        val source  = new SinkBasedPDPConfigurationSource(configs::add);

        val config = createConfig("arkham-pdp", "v1");
        source.pushConfiguration(config);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("arkham-pdp");
        assertThat(configs.getFirst().configurationId()).isEqualTo("v1");

        source.dispose();
    }

    @Test
    void whenPushingMultipleConfigurations_thenCallbackIsInvokedForEach() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();
        val source  = new SinkBasedPDPConfigurationSource(configs::add);

        val config1 = createConfig("innsmouth-pdp", "v1");
        val config2 = createConfig("innsmouth-pdp", "v2");

        source.pushConfiguration(config1);
        source.pushConfiguration(config2);

        assertThat(configs).hasSize(2);
        assertThat(configs.get(0).configurationId()).isEqualTo("v1");
        assertThat(configs.get(1).configurationId()).isEqualTo("v2");

        source.dispose();
    }

    @Test
    void whenPushingToMultiplePdps_thenCallbackIsInvokedForEach() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();
        val source  = new SinkBasedPDPConfigurationSource(configs::add);

        val productionConfig  = createConfig("production", "prod-v1");
        val developmentConfig = createConfig("development", "dev-v1");

        source.pushConfiguration(productionConfig);
        source.pushConfiguration(developmentConfig);

        assertThat(configs).hasSize(2);

        val pdpIds = configs.stream().map(PDPConfiguration::pdpId).toList();
        assertThat(pdpIds).containsExactly("production", "development");

        source.dispose();
    }

    @Test
    void whenPushingConfiguration_thenAvailablePdpIdsIsUpdated() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();
        val source  = new SinkBasedPDPConfigurationSource(configs::add);

        val config = createConfig("miskatonic-pdp", "v1");
        source.pushConfiguration(config);

        assertThat(source.getAvailablePdpIds()).containsExactly("miskatonic-pdp");

        source.dispose();
    }

    @Test
    void whenRemovingConfiguration_thenPdpIdIsRemovedFromAvailableIds() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();
        val source  = new SinkBasedPDPConfigurationSource(configs::add);

        val config = createConfig("dunwich-pdp", "v1");
        source.pushConfiguration(config);

        assertThat(source.getAvailablePdpIds()).containsExactly("dunwich-pdp");

        source.removeConfiguration("dunwich-pdp");

        assertThat(source.getAvailablePdpIds()).isEmpty();

        source.dispose();
    }

    @Test
    void whenRemovingNonexistentPdp_thenNoEffect() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();
        val source  = new SinkBasedPDPConfigurationSource(configs::add);

        source.removeConfiguration("nonexistent");

        assertThat(source.getAvailablePdpIds()).isEmpty();

        source.dispose();
    }

    @Test
    void whenMultiplePdpsExist_thenAvailablePdpIdsContainsAll() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();
        val source  = new SinkBasedPDPConfigurationSource(configs::add);

        source.pushConfiguration(createConfig("pdp-alpha", "v1"));
        source.pushConfiguration(createConfig("pdp-beta", "v1"));
        source.pushConfiguration(createConfig("pdp-gamma", "v1"));

        assertThat(source.getAvailablePdpIds()).containsExactlyInAnyOrder("pdp-alpha", "pdp-beta", "pdp-gamma");

        source.dispose();
    }

    @Test
    void whenDisposing_thenAvailablePdpIdsIsCleared() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();
        val source  = new SinkBasedPDPConfigurationSource(configs::add);

        source.pushConfiguration(createConfig("pdp-1", "v1"));
        source.pushConfiguration(createConfig("pdp-2", "v1"));

        assertThat(source.getAvailablePdpIds()).hasSize(2);

        source.dispose();

        assertThat(source.getAvailablePdpIds()).isEmpty();
        assertThat(source.isDisposed()).isTrue();
    }

    @Test
    void whenPushingToDisposedSource_thenThrowsException() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();
        val source  = new SinkBasedPDPConfigurationSource(configs::add);

        source.dispose();

        val config = createConfig("post-dispose-pdp", "v1");
        assertThatThrownBy(() -> source.pushConfiguration(config)).isInstanceOf(PDPConfigurationException.class)
                .hasMessageContaining("disposed");
    }

    @Test
    void whenDisposeIsCalledTwice_thenIsIdempotent() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();
        val source  = new SinkBasedPDPConfigurationSource(configs::add);

        source.dispose();
        source.dispose();

        assertThat(source.isDisposed()).isTrue();
    }

    private PDPConfiguration createConfig(String pdpId, String configurationId) {
        return new PDPConfiguration(pdpId, configurationId, CombiningAlgorithm.DENY_OVERRIDES, List.of(), Map.of());
    }

}
