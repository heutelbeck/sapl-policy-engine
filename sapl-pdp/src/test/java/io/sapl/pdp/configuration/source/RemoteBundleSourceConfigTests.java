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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.pdp.configuration.PDPConfigurationException;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import lombok.val;

@DisplayName("RemoteBundleSourceConfig credential exposure")
class RemoteBundleSourceConfigTests {

    private static final BundleSecurityPolicy POLICY = BundleSecurityPolicy.builder().disableSignatureVerification()
            .build();

    @ParameterizedTest(name = "{0} -> exposed={1}")
    @CsvSource({ "http://192.0.2.10/bundles, true", "HTTP://192.0.2.10/bundles, true",
            "https://192.0.2.10/bundles, false", "https://bundles.example.com/bundles, false",
            "http://127.0.0.1:8080/bundles, false", "http://localhost:8080/bundles, false" })
    @DisplayName("a credential is exposed only over non-loopback http")
    void credentialExposureByUrl(String baseUrl, boolean exposed) {
        assertThat(RemoteBundleSourceConfig.credentialIsExposed(baseUrl)).isEqualTo(exposed);
    }

    @Test
    @DisplayName("credentials over plain http are warned about, not rejected, so the config still builds")
    void whenCredentialsOverPlainHttpThenConfigStillBuilds() {
        assertThatCode(() -> config("http://192.0.2.10/bundles", "Authorization", "Bearer secret"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("toString redacts the auth header value")
    void whenToStringThenAuthHeaderValueRedacted() {
        var cfg = config("https://bundles.example.com/bundles", "Authorization", "Bearer super-secret-token");

        assertThat(cfg.toString()).doesNotContain("super-secret-token").contains("REDACTED");
    }

    @ParameterizedTest(name = "longPollTimeout={0}s")
    @ValueSource(longs = { 0L, -10L })
    @DisplayName("a non-positive longPollTimeout is rejected at construction, like every other duration field")
    void whenLongPollTimeoutNonPositiveThenConstructionFails(long seconds) {
        val pdpIds          = List.of("default");
        val pollInterval    = Duration.ofMillis(100);
        val longPollTimeout = Duration.ofSeconds(seconds);
        val pollIntervals   = Map.<String, Duration>of();
        val connectTimeout  = Duration.ofMillis(50);
        val readTimeout     = Duration.ofMillis(200);
        assertThatExceptionOfType(PDPConfigurationException.class)
                .isThrownBy(() -> new RemoteBundleSourceConfig("https://bundles.example.com/bundles", pdpIds,
                        RemoteBundleSourceConfig.FetchMode.LONG_POLL, pollInterval, longPollTimeout, null, null, true,
                        POLICY, pollIntervals, connectTimeout, readTimeout))
                .withMessageContaining("longPollTimeout");
    }

    private static RemoteBundleSourceConfig config(String baseUrl, String authName, String authValue) {
        return new RemoteBundleSourceConfig(baseUrl, List.of("default"), RemoteBundleSourceConfig.FetchMode.POLLING,
                Duration.ofMillis(100), Duration.ofSeconds(5), authName, authValue, true, POLICY, Map.of(),
                Duration.ofMillis(50), Duration.ofMillis(200));
    }
}
