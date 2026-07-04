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

@DisplayName("RemoteBundleSourceConfig")
class RemoteBundleSourceConfigTests {

    private static final BundleSecurityPolicy POLICY = BundleSecurityPolicy.builder().disableSignatureVerification()
            .build();

    @ParameterizedTest(name = "{0} -> exposed={1}")
    @CsvSource({ "http://192.0.2.10/bundles, true", "HTTP://192.0.2.10/bundles, true",
            "http://127.0.0.1:8080/bundles, true", "http://localhost:8080/bundles, true",
            "https://192.0.2.10/bundles, false", "https://bundles.example.com/bundles, false" })
    @DisplayName("a credential is exposed over any plaintext http URL")
    void credentialExposureByUrl(String baseUrl, boolean exposed) {
        assertThat(RemoteBundleSourceConfig.credentialIsExposed(baseUrl)).isEqualTo(exposed);
    }

    @Test
    @DisplayName("credentials over plain http are rejected by default")
    void whenCredentialsOverPlainHttpWithoutOptInThenConfigFailsClosed() {
        assertThatExceptionOfType(PDPConfigurationException.class)
                .isThrownBy(() -> config("http://192.0.2.10/bundles", "Authorization", "Bearer secret"))
                .withMessageContaining("plaintext");
    }

    @Test
    @DisplayName("credentials over plain http build only with the explicit insecure opt-in")
    void whenCredentialsOverPlainHttpWithOptInThenConfigBuilds() {
        assertThatCode(() -> config("http://192.0.2.10/bundles", "Authorization", "Bearer secret", true))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("toString redacts the auth header value")
    void whenToStringThenAuthHeaderValueRedacted() {
        val cfg = config("https://bundles.example.com/bundles", "Authorization", "Bearer super-secret-token");

        assertThat(cfg.toString()).doesNotContain("super-secret-token").contains("REDACTED");
    }

    @Test
    @DisplayName("base URLs with userinfo are rejected without echoing the credential")
    void whenBaseUrlContainsUserInfoThenConfigFailsClosed() {
        assertThatExceptionOfType(PDPConfigurationException.class)
                .isThrownBy(() -> config("https://user:secret@bundles.example.com/bundles", null, null))
                .withMessageContaining("userinfo")
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain("secret"));
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

    @ParameterizedTest(name = "invalid pdpId: {0}")
    @ValueSource(strings = { "../admin", "tenant?x=1", "a/b", "with space" })
    @DisplayName("a pdpId that fails validation is rejected at construction, consistent with directory sources")
    void whenInvalidPdpIdThenConstructionFails(String pdpId) {
        val invalid         = List.of(pdpId);
        val pollInterval    = Duration.ofMillis(100);
        val longPollTimeout = Duration.ofSeconds(5);
        val pollIntervals   = Map.<String, Duration>of();
        val connectTimeout  = Duration.ofMillis(50);
        val readTimeout     = Duration.ofMillis(200);
        assertThatExceptionOfType(PDPConfigurationException.class)
                .isThrownBy(() -> new RemoteBundleSourceConfig("https://bundles.example.com/bundles", invalid,
                        RemoteBundleSourceConfig.FetchMode.POLLING, pollInterval, longPollTimeout, null, null, true,
                        POLICY, pollIntervals, connectTimeout, readTimeout))
                .withMessageContaining("PDP identifier");
    }

    @ParameterizedTest(name = "override={0}ms")
    @ValueSource(longs = { 0L, -100L })
    @DisplayName("a non-positive per-pdpId poll interval override is rejected at construction")
    void whenPdpIdPollIntervalOverrideNonPositiveThenConstructionFails(long millis) {
        val pollIntervals  = Map.of("default", Duration.ofMillis(millis));
        val pdpIds         = List.of("default");
        val firstBackoff   = Duration.ofMillis(100);
        val maxBackoff     = Duration.ofSeconds(5);
        val connectTimeout = Duration.ofMillis(50);
        val readTimeout    = Duration.ofMillis(200);

        assertThatExceptionOfType(PDPConfigurationException.class)
                .isThrownBy(() -> new RemoteBundleSourceConfig("https://bundles.example.com/bundles", pdpIds,
                        RemoteBundleSourceConfig.FetchMode.POLLING, firstBackoff, maxBackoff, null, null, true, POLICY,
                        pollIntervals, connectTimeout, readTimeout))
                .withMessageContaining("pdpIdPollIntervals");
    }

    @Test
    @DisplayName("MULTI mode requires a realm")
    void whenMultiWithoutRealmThenConstructionFails() {
        assertThatExceptionOfType(PDPConfigurationException.class).isThrownBy(() -> multiConfig(null, "index"))
                .withMessageContaining("realm");
    }

    @Test
    @DisplayName("MULTI mode requires an index path")
    void whenMultiWithoutIndexPathThenConstructionFails() {
        assertThatExceptionOfType(PDPConfigurationException.class).isThrownBy(() -> multiConfig("acme", null))
                .withMessageContaining("indexPath");
    }

    @Test
    @DisplayName("MULTI mode builds with an empty pdpIds list and exposes realm and index path")
    void whenMultiWithRealmAndIndexPathThenBuildsWithoutPdpIds() {
        val cfg = multiConfig("acme", "index");
        assertThat(cfg.realm()).isEqualTo("acme");
        assertThat(cfg.indexPath()).isEqualTo("index");
        assertThat(cfg.pdpIds()).isEmpty();
    }

    @Test
    @DisplayName("single mode still requires a non-empty pdpIds list")
    void whenSingleModeWithEmptyPdpIdsThenConstructionFails() {
        assertThatExceptionOfType(PDPConfigurationException.class)
                .isThrownBy(() -> new RemoteBundleSourceConfig("https://bundles.example.com/bundles", List.of(),
                        RemoteBundleSourceConfig.FetchMode.POLLING, Duration.ofMillis(100), Duration.ofSeconds(5), null,
                        null, false, true, POLICY, Map.of(), Duration.ofMillis(100), Duration.ofSeconds(5)))
                .withMessageContaining("pdpIds");
    }

    private static RemoteBundleSourceConfig multiConfig(String realm, String indexPath) {
        return new RemoteBundleSourceConfig("https://regent.example.com/realms/acme/", List.of(),
                RemoteBundleSourceConfig.FetchMode.MULTI, Duration.ofMillis(100), Duration.ofSeconds(5), null, null,
                false, true, POLICY, Map.of(), Duration.ofMillis(100), Duration.ofSeconds(5), realm, indexPath);
    }

    @Test
    @DisplayName("a per-pdpId poll interval override for an unknown pdpId is rejected")
    void whenPdpIdPollIntervalOverrideUsesUnknownPdpIdThenConstructionFails() {
        val pollIntervals  = Map.of("staging", Duration.ofSeconds(5));
        val pdpIds         = List.of("production");
        val firstBackoff   = Duration.ofMillis(100);
        val maxBackoff     = Duration.ofSeconds(5);
        val connectTimeout = Duration.ofMillis(50);
        val readTimeout    = Duration.ofMillis(200);

        assertThatExceptionOfType(PDPConfigurationException.class)
                .isThrownBy(() -> new RemoteBundleSourceConfig("https://bundles.example.com/bundles", pdpIds,
                        RemoteBundleSourceConfig.FetchMode.POLLING, firstBackoff, maxBackoff, null, null, true, POLICY,
                        pollIntervals, connectTimeout, readTimeout))
                .withMessageContaining("unknown pdpId");
    }

    @Test
    @DisplayName("sub-second poll intervals are accepted when explicitly configured")
    void whenPollIntervalsAreSubSecondButPositiveThenConstructionSucceeds() {
        val pollIntervals = Map.of("default", Duration.ofMillis(100));

        assertThatCode(() -> new RemoteBundleSourceConfig("https://bundles.example.com/bundles", List.of("default"),
                RemoteBundleSourceConfig.FetchMode.POLLING, Duration.ofMillis(100), Duration.ofSeconds(5), null, null,
                true, POLICY, pollIntervals, Duration.ofMillis(50), Duration.ofMillis(200))).doesNotThrowAnyException();
    }

    private static RemoteBundleSourceConfig config(String baseUrl, String authName, String authValue) {
        return new RemoteBundleSourceConfig(baseUrl, List.of("default"), RemoteBundleSourceConfig.FetchMode.POLLING,
                Duration.ofMillis(100), Duration.ofSeconds(5), authName, authValue, true, POLICY, Map.of(),
                Duration.ofMillis(50), Duration.ofMillis(200));
    }

    private static RemoteBundleSourceConfig config(String baseUrl, String authName, String authValue,
            boolean allowInsecureHttp) {
        return new RemoteBundleSourceConfig(baseUrl, List.of("default"), RemoteBundleSourceConfig.FetchMode.POLLING,
                Duration.ofMillis(100), Duration.ofSeconds(5), authName, authValue, allowInsecureHttp, true, POLICY,
                Map.of(), Duration.ofMillis(50), Duration.ofMillis(200));
    }
}
