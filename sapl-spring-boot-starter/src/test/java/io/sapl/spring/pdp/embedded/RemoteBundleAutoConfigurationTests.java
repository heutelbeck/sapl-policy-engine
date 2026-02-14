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
package io.sapl.spring.pdp.embedded;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.sapl.pdp.DynamicPolicyDecisionPoint;
import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties.RemoteBundleProperties;
import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties.RemoteFetchMode;
import lombok.val;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Remote Bundle Auto-Configuration Tests")
class RemoteBundleAutoConfigurationTests {

    private static final String PREFIX = "io.sapl.pdp.embedded.";

    private final ApplicationContextRunner propertiesRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesOnlyConfiguration.class);

    private final ApplicationContextRunner autoConfigRunner = new ApplicationContextRunner()
            .withBean(JsonMapper.class, JsonMapper::new)
            .withConfiguration(AutoConfigurations.of(PDPAutoConfiguration.class));

    @EnableConfigurationProperties(EmbeddedPDPProperties.class)
    static class PropertiesOnlyConfiguration {

    }

    @Nested
    @DisplayName("Property Binding")
    class PropertyBindingTests {

        @Test
        @DisplayName("remote bundle properties have correct defaults")
        void whenNoRemoteBundlePropertiesSetThenDefaultsApply() {
            propertiesRunner.run(context -> {
                val props = context.getBean(EmbeddedPDPProperties.class).getRemoteBundles();
                assertThat(props).satisfies(p -> {
                    assertThat(p.getBaseUrl()).isNull();
                    assertThat(p.getPdpIds()).isEmpty();
                    assertThat(p.getMode()).isEqualTo(RemoteFetchMode.POLLING);
                    assertThat(p.getPollInterval()).isEqualTo(Duration.ofSeconds(30));
                    assertThat(p.getLongPollTimeout()).isEqualTo(Duration.ofSeconds(30));
                    assertThat(p.getAuthHeaderName()).isNull();
                    assertThat(p.getAuthHeaderValue()).isNull();
                    assertThat(p.isFollowRedirects()).isTrue();
                    assertThat(p.getPdpIdPollIntervals()).isEmpty();
                    assertThat(p.getFirstBackoff()).isEqualTo(Duration.ofMillis(500));
                    assertThat(p.getMaxBackoff()).isEqualTo(Duration.ofSeconds(5));
                });
            });
        }

        @Test
        @DisplayName("all remote bundle properties bind from configuration")
        void whenAllRemoteBundlePropertiesSetThenValuesBound() {
            propertiesRunner.withPropertyValues(PREFIX + "remoteBundles.baseUrl=https://pap.example.com/bundles",
                    PREFIX + "remoteBundles.pdpIds[0]=production", PREFIX + "remoteBundles.pdpIds[1]=staging",
                    PREFIX + "remoteBundles.mode=LONG_POLL", PREFIX + "remoteBundles.pollInterval=10s",
                    PREFIX + "remoteBundles.longPollTimeout=60s", PREFIX + "remoteBundles.authHeaderName=Authorization",
                    PREFIX + "remoteBundles.authHeaderValue=Bearer token123",
                    PREFIX + "remoteBundles.followRedirects=false", PREFIX + "remoteBundles.firstBackoff=1s",
                    PREFIX + "remoteBundles.maxBackoff=30s").run(context -> {
                        val props = context.getBean(EmbeddedPDPProperties.class).getRemoteBundles();
                        assertThat(props).satisfies(p -> {
                            assertThat(p.getBaseUrl()).isEqualTo("https://pap.example.com/bundles");
                            assertThat(p.getPdpIds()).containsExactly("production", "staging");
                            assertThat(p.getMode()).isEqualTo(RemoteFetchMode.LONG_POLL);
                            assertThat(p.getPollInterval()).isEqualTo(Duration.ofSeconds(10));
                            assertThat(p.getLongPollTimeout()).isEqualTo(Duration.ofSeconds(60));
                            assertThat(p.getAuthHeaderName()).isEqualTo("Authorization");
                            assertThat(p.getAuthHeaderValue()).isEqualTo("Bearer token123");
                            assertThat(p.isFollowRedirects()).isFalse();
                            assertThat(p.getFirstBackoff()).isEqualTo(Duration.ofSeconds(1));
                            assertThat(p.getMaxBackoff()).isEqualTo(Duration.ofSeconds(30));
                        });
                    });
        }

        @Test
        @DisplayName("per-pdpId poll interval overrides bind from configuration")
        void whenPdpIdPollIntervalsSetThenMapBinds() {
            propertiesRunner.withPropertyValues(PREFIX + "remoteBundles.pdpIdPollIntervals.production=5s",
                    PREFIX + "remoteBundles.pdpIdPollIntervals.staging=60s").run(context -> {
                        val intervals = context.getBean(EmbeddedPDPProperties.class).getRemoteBundles()
                                .getPdpIdPollIntervals();
                        assertThat(intervals).hasSize(2).containsEntry("production", Duration.ofSeconds(5))
                                .containsEntry("staging", Duration.ofSeconds(60));
                    });
        }

        @Test
        @DisplayName("bundle security properties bind for unsigned escape hatch")
        void whenUnsignedEscapeHatchConfiguredThenPropertiesBind() {
            propertiesRunner.withPropertyValues(PREFIX + "bundleSecurity.allowUnsigned=true",
                    PREFIX + "bundleSecurity.acceptRisks=true").run(context -> {
                        val security = context.getBean(EmbeddedPDPProperties.class).getBundleSecurity();
                        assertThat(security.isAllowUnsigned()).isTrue();
                        assertThat(security.isAcceptRisks()).isTrue();
                    });
        }

    }

    @Nested
    @DisplayName("Auto-Configuration")
    class AutoConfigurationTests {

        @Test
        @DisplayName("REMOTE_BUNDLES with valid config creates PDP")
        void whenRemoteBundlesWithValidConfigThenPdpCreated() {
            autoConfigRunner.withPropertyValues(PREFIX + "pdpConfigType=REMOTE_BUNDLES",
                    PREFIX + "remoteBundles.baseUrl=http://localhost:1/bundles",
                    PREFIX + "remoteBundles.pdpIds[0]=default", PREFIX + "bundleSecurity.allowUnsigned=true",
                    PREFIX + "bundleSecurity.acceptRisks=true").run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(DynamicPolicyDecisionPoint.class);
                    });
        }

        @Test
        @DisplayName("REMOTE_BUNDLES without security config fails context")
        void whenRemoteBundlesWithoutSecurityThenContextFails() {
            autoConfigRunner.withPropertyValues(PREFIX + "pdpConfigType=REMOTE_BUNDLES",
                    PREFIX + "remoteBundles.baseUrl=http://localhost:1/bundles",
                    PREFIX + "remoteBundles.pdpIds[0]=default").run(context -> assertThat(context).hasFailed());
        }

    }

}
