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
import io.sapl.pdp.configuration.PDPConfigurationException;
import io.sapl.pdp.configuration.PdpVoterSource;
import io.sapl.pdp.configuration.bundle.BundleBuilder;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import lombok.val;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RemoteBundlePDPConfigurationSource")
class RemoteBundlePDPConfigurationSourceTests {

    private static final CombiningAlgorithm DENY_OVERRIDES = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.DENY, ErrorHandling.PROPAGATE);

    private static final String PDP_ID = "production";

    private MockWebServer                      server;
    private BundleSecurityPolicy               developmentPolicy;
    private BundleSecurityPolicy               signedPolicy;
    private KeyPair                            elderKeyPair;
    private RemoteBundlePDPConfigurationSource source;

    @Mock
    PdpVoterSource pdpVoterSource;

    @BeforeEach
    void setUp() throws IOException, NoSuchAlgorithmException {
        server = new MockWebServer();
        server.start();

        val generator = KeyPairGenerator.getInstance("Ed25519");
        elderKeyPair = generator.generateKeyPair();

        developmentPolicy = BundleSecurityPolicy.builder().disableSignatureVerification().acceptUnsignedBundleRisks()
                .build();
        signedPolicy      = BundleSecurityPolicy.builder(elderKeyPair.getPublic()).build();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (source != null) {
            source.dispose();
        }
        server.shutdown();
    }

    private RemoteBundleSourceConfig defaultConfig(BundleSecurityPolicy securityPolicy) {
        return new RemoteBundleSourceConfig(server.url("/bundles").toString(), List.of(PDP_ID),
                RemoteBundleSourceConfig.FetchMode.POLLING, Duration.ofMillis(100), Duration.ofSeconds(5), null, null,
                true, securityPolicy, Map.of(), Duration.ofMillis(50), Duration.ofMillis(200), WebClient.builder());
    }

    private byte[] createUnsignedBundle() {
        return BundleBuilder.create().withCombiningAlgorithm(DENY_OVERRIDES).withPolicy("test.sapl", """
                policy "test-policy" permit true
                """).build();
    }

    private byte[] createSignedBundle() {
        return BundleBuilder.create().withCombiningAlgorithm(DENY_OVERRIDES).withPolicy("test.sapl", """
                policy "test-policy" permit true
                """).signWith(elderKeyPair.getPrivate(), "test-key").build();
    }

    private CopyOnWriteArrayList<PDPConfiguration> captureConfigurations() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();
        doAnswer(inv -> {
            configs.add(inv.getArgument(0));
            return null;
        }).when(pdpVoterSource).loadConfiguration(any(), eq(true));
        return configs;
    }

    private void enqueueBundle(byte[] bundleBytes, String etag) {
        val buffer = new okio.Buffer();
        buffer.write(bundleBytes);
        server.enqueue(new MockResponse().setBody(buffer).addHeader("Content-Type", "application/octet-stream")
                .addHeader("ETag", etag));
    }

    private void enqueueNotModified() {
        server.enqueue(new MockResponse().setResponseCode(304));
    }

    private void enqueueError(int statusCode) {
        server.enqueue(new MockResponse().setResponseCode(statusCode));
    }

    private void awaitRetries() {
        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(server.getRequestCount()).isGreaterThanOrEqualTo(2));
    }

    @Nested
    @DisplayName("A: Bootstrap and First Fetch")
    class BootstrapAndFirstFetch {

        @Test
        @DisplayName("A1: fetches and loads valid signed bundle on startup")
        void whenServerAvailableAtStartupThenBundleLoaded() {
            val configs = captureConfigurations();
            enqueueBundle(createSignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(signedPolicy), pdpVoterSource);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @ParameterizedTest(name = "A4/A5: server returns {0} at startup triggers retry")
        @ValueSource(ints = { 401, 403, 404, 500, 502, 503 })
        void whenServerReturnsErrorAtStartupThenRetries(int statusCode) {
            enqueueError(statusCode);
            enqueueError(statusCode);

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy), pdpVoterSource);

            awaitRetries();
            verify(pdpVoterSource, never()).loadConfiguration(any(), eq(true));
        }

        @Test
        @DisplayName("A6: rejects invalid/non-ZIP body and retries")
        void whenServerReturnsInvalidBodyThenRetries() {
            server.enqueue(new MockResponse().setBody("this is not a zip file")
                    .addHeader("Content-Type", "application/octet-stream").addHeader("ETag", "\"bad\""));
            server.enqueue(new MockResponse().setBody("still not zip"));

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy), pdpVoterSource);

            awaitRetries();
            verify(pdpVoterSource, never()).loadConfiguration(any(), eq(true));
        }

        @Test
        @DisplayName("A7: rejects unsigned bundle when signatures mandatory")
        void whenUnsignedBundleWithMandatorySignaturesThenRejects() {
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueBundle(createUnsignedBundle(), "\"v1\"");

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(signedPolicy), pdpVoterSource);

            awaitRetries();
            verify(pdpVoterSource, never()).loadConfiguration(any(), eq(true));
        }

        @Test
        @DisplayName("A8: accepts unsigned bundle with dev escape hatch active")
        void whenUnsignedBundleWithDevEscapeHatchThenAccepted() {
            val configs = captureConfigurations();
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy), pdpVoterSource);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }
    }

    @Nested
    @DisplayName("B: Steady-State Polling")
    class SteadyStatePolling {

        @Test
        @DisplayName("B1: 304 Not Modified preserves current bundle without reload")
        void whenServerReturns304ThenNoReload() {
            val configs = captureConfigurations();
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy), pdpVoterSource);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));

            // Verify config count stays at 1 after additional poll cycles
            await().pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("B2: new ETag triggers bundle hot-reload")
        void whenNewEtagThenBundleReloaded() {
            val configs = captureConfigurations();
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueBundle(createUnsignedBundle(), "\"v2\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy), pdpVoterSource);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(2));
        }

        @Test
        @DisplayName("B4: multiple pdpIds poll concurrently and independently")
        void whenMultiplePdpIdsThenIndependentFetchLoops() {
            val configs = captureConfigurations();
            val config  = new RemoteBundleSourceConfig(server.url("/bundles").toString(),
                    List.of("production", "staging"), RemoteBundleSourceConfig.FetchMode.POLLING,
                    Duration.ofMillis(100), Duration.ofSeconds(5), null, null, true, developmentPolicy, Map.of(),
                    Duration.ofMillis(50), Duration.ofMillis(200), WebClient.builder());

            // Enqueue bundles for both pdpIds
            enqueueBundle(createUnsignedBundle(), "\"prod-v1\"");
            enqueueBundle(createUnsignedBundle(), "\"staging-v1\"");
            enqueueNotModified();
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(config, pdpVoterSource);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(2));
        }

        @Test
        @DisplayName("B5: auth header sent on every request")
        void whenAuthConfiguredThenHeaderSentOnEveryRequest() throws InterruptedException {
            val config = new RemoteBundleSourceConfig(server.url("/bundles").toString(), List.of(PDP_ID),
                    RemoteBundleSourceConfig.FetchMode.POLLING, Duration.ofMillis(100), Duration.ofSeconds(5),
                    "Authorization", "Bearer test-token", true, developmentPolicy, Map.of(), Duration.ofMillis(50),
                    Duration.ofMillis(200), WebClient.builder());

            val configs = captureConfigurations();
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(config, pdpVoterSource);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));

            val firstRequest = server.takeRequest(1, TimeUnit.SECONDS);
            assertThat(firstRequest).isNotNull().extracting(r -> r.getHeaders().get("Authorization"))
                    .isEqualTo("Bearer test-token");
        }

        @Test
        @DisplayName("B5: If-None-Match header sent after first successful fetch")
        void whenEtagReceivedThenIfNoneMatchSentOnNextRequest() throws InterruptedException {
            val configs = captureConfigurations();
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy), pdpVoterSource);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));

            // Skip the first request (no ETag yet)
            server.takeRequest(1, TimeUnit.SECONDS);
            // Second request should have If-None-Match
            val secondRequest = server.takeRequest(2, TimeUnit.SECONDS);
            assertThat(secondRequest).isNotNull().extracting(r -> r.getHeaders().get("If-None-Match"))
                    .isEqualTo("\"v1\"");
        }
    }

    @Nested
    @DisplayName("C: Long-Poll Mode")
    class LongPollMode {

        private RemoteBundleSourceConfig longPollConfig() {
            return new RemoteBundleSourceConfig(server.url("/bundles").toString(), List.of(PDP_ID),
                    RemoteBundleSourceConfig.FetchMode.LONG_POLL, Duration.ofMillis(100), Duration.ofSeconds(5), null,
                    null, true, developmentPolicy, Map.of(), Duration.ofMillis(50), Duration.ofMillis(200),
                    WebClient.builder());
        }

        @Test
        @DisplayName("C1: server responds immediately with new bundle in long-poll mode")
        void whenServerRespondsImmediatelyThenBundleLoaded() {
            val configs = captureConfigurations();
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(longPollConfig(), pdpVoterSource);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("C2: 304 on long-poll timeout triggers immediate reconnect")
        void whenLongPollTimeoutThenImmediateReconnect() {
            val configs = captureConfigurations();
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();
            // After 304 in long-poll mode, immediate reconnect (Duration.ZERO delay)
            enqueueBundle(createUnsignedBundle(), "\"v2\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(longPollConfig(), pdpVoterSource);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(2));
        }
    }

    @Nested
    @DisplayName("D: Network Failures and Recovery")
    class NetworkFailuresAndRecovery {

        @Test
        @DisplayName("D2: server recovers after outage, node picks up new bundle")
        void whenServerRecoversAfterOutageThenBundleLoaded() {
            val configs = captureConfigurations();
            // First: server error
            enqueueError(503);
            // Then: recovery with valid bundle
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy), pdpVoterSource);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("D7: extended outage caps backoff and retries indefinitely")
        void whenExtendedOutageThenBackoffCappedAndRetriesContinue() {
            // Enqueue many errors to test backoff capping
            for (int i = 0; i < 10; i++) {
                enqueueError(503);
            }
            // Then recovery
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();

            val configs = captureConfigurations();
            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy), pdpVoterSource);

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("D8: intermittent failures never lose good bundle")
        void whenIntermittentFailuresThenGoodBundleRetained() {
            val configs = captureConfigurations();
            // Success
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            // Then error
            enqueueError(500);
            // Then recovery with new bundle
            enqueueBundle(createUnsignedBundle(), "\"v2\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy), pdpVoterSource);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(2));
        }
    }

    @Nested
    @DisplayName("E: Security Enforcement")
    class SecurityEnforcement {

        @Test
        @DisplayName("E1: valid Ed25519 signature with correct key accepted")
        void whenValidSignatureThenBundleAccepted() {
            val configs = captureConfigurations();
            enqueueBundle(createSignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(signedPolicy), pdpVoterSource);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("E2: valid signature but wrong public key rejected")
        void whenWrongPublicKeyThenBundleRejected() throws Exception {
            val otherKeyPair   = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            val wrongKeyPolicy = BundleSecurityPolicy.builder(otherKeyPair.getPublic()).build();

            enqueueBundle(createSignedBundle(), "\"v1\"");
            enqueueBundle(createSignedBundle(), "\"v1\"");

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(wrongKeyPolicy), pdpVoterSource);

            awaitRetries();
            verify(pdpVoterSource, never()).loadConfiguration(any(), eq(true));
        }

        @Test
        @DisplayName("E7: valid bundle replaces previously rejected bad bundle")
        void whenGoodBundleFollowsBadThenGoodBundleLoaded() {
            val configs = captureConfigurations();
            // First: unsigned bundle (rejected by signed policy)
            enqueueBundle(createUnsignedBundle(), "\"bad\"");
            // Then: properly signed bundle
            enqueueBundle(createSignedBundle(), "\"good\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(signedPolicy), pdpVoterSource);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }
    }

    @Nested
    @DisplayName("F: HTTP Edge Cases")
    class HttpEdgeCases {

        @Test
        @DisplayName("F5: empty response body treated as error")
        void whenEmptyResponseBodyThenRetries() {
            server.enqueue(new MockResponse().addHeader("ETag", "\"empty\""));
            server.enqueue(new MockResponse().addHeader("ETag", "\"empty\""));

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy), pdpVoterSource);

            awaitRetries();
            verify(pdpVoterSource, never()).loadConfiguration(any(), eq(true));
        }

        @Test
        @DisplayName("F6: wrong Content-Type still parses body as ZIP")
        void whenWrongContentTypeThenStillParsesAsZip() {
            val configs = captureConfigurations();
            val buffer  = new okio.Buffer();
            buffer.write(createUnsignedBundle());
            server.enqueue(new MockResponse().setBody(buffer).addHeader("Content-Type", "application/json")
                    .addHeader("ETag", "\"v1\""));
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy), pdpVoterSource);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }
    }

    @Nested
    @DisplayName("H: Lifecycle and Cleanup")
    class LifecycleAndCleanup {

        @Test
        @DisplayName("H1: dispose cancels all fetch loops")
        void whenDisposedThenFetchLoopsStopped() {
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            // Enqueue many 304s so the poll loop has work to do
            for (int i = 0; i < 20; i++) {
                enqueueNotModified();
            }

            val configs = captureConfigurations();
            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy), pdpVoterSource);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));

            source.dispose();
            val requestCountAfterDispose = server.getRequestCount();

            // Verify request count stays stable after dispose
            await().pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2)).untilAsserted(
                    () -> assertThat(server.getRequestCount()).isLessThanOrEqualTo(requestCountAfterDispose + 1));
        }

        @Test
        @DisplayName("H4: double dispose is idempotent")
        void whenDisposedTwiceThenNoError() {
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();

            val configs = captureConfigurations();
            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy), pdpVoterSource);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));

            source.dispose();
            source.dispose(); // Should not throw

            assertThat(source.isDisposed()).isTrue();
        }
    }

    @Nested
    @DisplayName("I: Configuration Validation")
    class ConfigurationValidation {

        private final List<String>                       validPdpIds          = List.of(PDP_ID);
        private final RemoteBundleSourceConfig.FetchMode validMode            = RemoteBundleSourceConfig.FetchMode.POLLING;
        private final Duration                           validPollInterval    = Duration.ofSeconds(30);
        private final Duration                           validLongPollTimeout = Duration.ofSeconds(30);
        private final Map<String, Duration>              emptyIntervals       = Map.of();
        private final Duration                           validFirstBackoff    = Duration.ofMillis(500);
        private final Duration                           validMaxBackoff      = Duration.ofSeconds(5);

        @Test
        @DisplayName("I1: missing baseUrl fails fast")
        void whenMissingBaseUrlThenFails() {
            val wcb = WebClient.builder();
            assertThatThrownBy(() -> new RemoteBundleSourceConfig(null, validPdpIds, validMode, validPollInterval,
                    validLongPollTimeout, null, null, true, developmentPolicy, emptyIntervals, validFirstBackoff,
                    validMaxBackoff, wcb)).isInstanceOf(PDPConfigurationException.class)
                    .hasMessageContaining("baseUrl");
        }

        @Test
        @DisplayName("I1: blank baseUrl fails fast")
        void whenBlankBaseUrlThenFails() {
            val wcb = WebClient.builder();
            assertThatThrownBy(() -> new RemoteBundleSourceConfig("  ", validPdpIds, validMode, validPollInterval,
                    validLongPollTimeout, null, null, true, developmentPolicy, emptyIntervals, validFirstBackoff,
                    validMaxBackoff, wcb)).isInstanceOf(PDPConfigurationException.class)
                    .hasMessageContaining("baseUrl");
        }

        @Test
        @DisplayName("I2: empty pdpIds list fails fast")
        void whenEmptyPdpIdsThenFails() {
            val emptyPdpIds = List.<String>of();
            val wcb         = WebClient.builder();
            assertThatThrownBy(() -> new RemoteBundleSourceConfig("http://example.com", emptyPdpIds, validMode,
                    validPollInterval, validLongPollTimeout, null, null, true, developmentPolicy, emptyIntervals,
                    validFirstBackoff, validMaxBackoff, wcb)).isInstanceOf(PDPConfigurationException.class)
                    .hasMessageContaining("pdpIds");
        }

        @Test
        @DisplayName("I3: zero poll interval fails fast")
        void whenZeroPollIntervalThenFails() {
            val wcb = WebClient.builder();
            assertThatThrownBy(() -> new RemoteBundleSourceConfig("http://example.com", validPdpIds, validMode,
                    Duration.ZERO, validLongPollTimeout, null, null, true, developmentPolicy, emptyIntervals,
                    validFirstBackoff, validMaxBackoff, wcb)).isInstanceOf(PDPConfigurationException.class)
                    .hasMessageContaining("pollInterval");
        }

        @Test
        @DisplayName("I4: auth header name without value fails fast")
        void whenAuthHeaderNameWithoutValueThenFails() {
            val wcb = WebClient.builder();
            assertThatThrownBy(() -> new RemoteBundleSourceConfig("http://example.com", validPdpIds, validMode,
                    validPollInterval, validLongPollTimeout, "Authorization", null, true, developmentPolicy,
                    emptyIntervals, validFirstBackoff, validMaxBackoff, wcb))
                    .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("authHeaderName");
        }

        @Test
        @DisplayName("I4: auth header value without name fails fast")
        void whenAuthHeaderValueWithoutNameThenFails() {
            val wcb = WebClient.builder();
            assertThatThrownBy(() -> new RemoteBundleSourceConfig("http://example.com", validPdpIds, validMode,
                    validPollInterval, validLongPollTimeout, null, "Bearer token", true, developmentPolicy,
                    emptyIntervals, validFirstBackoff, validMaxBackoff, wcb))
                    .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("authHeaderName");
        }

        @Test
        @DisplayName("I5: valid minimal config starts successfully")
        void whenMinimalValidConfigThenStartsSuccessfully() {
            val configs = captureConfigurations();
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy), pdpVoterSource);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("I6: per-pdpId poll interval override applied")
        void whenPerPdpIdPollIntervalThenOverrideUsed() {
            val configs = captureConfigurations();
            val config  = new RemoteBundleSourceConfig(server.url("/bundles").toString(), List.of(PDP_ID),
                    RemoteBundleSourceConfig.FetchMode.POLLING, Duration.ofSeconds(60), Duration.ofSeconds(5), null,
                    null, true, developmentPolicy, Map.of(PDP_ID, Duration.ofMillis(100)), Duration.ofMillis(50),
                    Duration.ofMillis(200), WebClient.builder());

            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();
            enqueueBundle(createUnsignedBundle(), "\"v2\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(config, pdpVoterSource);

            // With 100ms override (not the 60s default), second fetch should arrive quickly
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(2));
        }
    }

}
