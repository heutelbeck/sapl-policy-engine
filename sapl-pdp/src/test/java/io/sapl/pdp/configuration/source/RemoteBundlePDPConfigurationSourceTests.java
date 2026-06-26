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
import io.sapl.pdp.configuration.bundle.BundleBuilder;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import com.github.valfirst.slf4jtest.LoggingEvent;
import com.github.valfirst.slf4jtest.TestLoggerFactory;
import lombok.val;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

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

    @BeforeEach
    void setUp() throws IOException, NoSuchAlgorithmException {
        server = new MockWebServer();
        server.start();

        val generator = KeyPairGenerator.getInstance("Ed25519");
        elderKeyPair = generator.generateKeyPair();

        developmentPolicy = BundleSecurityPolicy.builder().disableSignatureVerification().build();
        signedPolicy      = BundleSecurityPolicy.builder(elderKeyPair.getPublic()).build();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (source != null) {
            source.close();
        }
        server.shutdown();
    }

    private RemoteBundleSourceConfig defaultConfig(BundleSecurityPolicy securityPolicy) {
        return new RemoteBundleSourceConfig(server.url("/bundles").toString(), List.of(PDP_ID),
                RemoteBundleSourceConfig.FetchMode.POLLING, Duration.ofMillis(100), Duration.ofSeconds(5), null, null,
                true, securityPolicy, Map.of(), Duration.ofMillis(50), Duration.ofMillis(200));
    }

    private byte[] createUnsignedBundle() {
        return BundleBuilder.create().withCombiningAlgorithm(DENY_OVERRIDES).withPolicy("test.sapl", """
                policy "test-policy" permit true;
                """).build();
    }

    private byte[] createSignedBundle() {
        return BundleBuilder.create().withCombiningAlgorithm(DENY_OVERRIDES).withPolicy("test.sapl", """
                policy "test-policy" permit true;
                """).signWith(elderKeyPair.getPrivate(), "test-key").build();
    }

    private List<PDPConfiguration> captureConfigurations(PDPConfigurationSource src) {
        val capture = new CapturingSubscriber();
        src.subscribe(capture);
        return capture.configs();
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
            enqueueBundle(createSignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(signedPolicy));
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @ParameterizedTest(name = "A4/A5: server returns {0} at startup triggers retry")
        @ValueSource(ints = { 401, 403, 404, 500, 502, 503 })
        void whenServerReturnsErrorAtStartupThenRetries(int statusCode) {
            enqueueError(statusCode);
            enqueueError(statusCode);

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            awaitRetries();
            assertThat(configs).isEmpty();
        }

        @Test
        @DisplayName("A6: rejects invalid/non-ZIP body and retries")
        void whenServerReturnsInvalidBodyThenRetries() {
            server.enqueue(new MockResponse().setBody("this is not a zip file")
                    .addHeader("Content-Type", "application/octet-stream").addHeader("ETag", "\"bad\""));
            server.enqueue(new MockResponse().setBody("still not zip"));

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            awaitRetries();
            assertThat(configs).isEmpty();
        }

        @Test
        @DisplayName("A7: rejects unsigned bundle when signatures mandatory")
        void whenUnsignedBundleWithMandatorySignaturesThenRejects() {
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueBundle(createUnsignedBundle(), "\"v1\"");

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(signedPolicy));
            val configs = captureConfigurations(source);

            awaitRetries();
            assertThat(configs).isEmpty();
        }

        @Test
        @DisplayName("A8: accepts unsigned bundle with dev escape hatch active")
        void whenUnsignedBundleWithDevEscapeHatchThenAccepted() {
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("A2: server unreachable at startup retries without crashing")
        void whenServerUnreachableAtStartupThenRetriesWithoutCrashing() {
            // Point to a port where nothing is listening
            val config = new RemoteBundleSourceConfig("http://localhost:1/bundles", List.of(PDP_ID),
                    RemoteBundleSourceConfig.FetchMode.POLLING, Duration.ofMillis(100), Duration.ofSeconds(5), null,
                    null, true, developmentPolicy, Map.of(), Duration.ofMillis(50), Duration.ofMillis(200));

            source = new RemoteBundlePDPConfigurationSource(config);

            // Source stays alive and retries without crashing
            await().pollDelay(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                assertThat(source.isClosed()).isFalse();
                assertThat(captureConfigurations(source)).isEmpty();
            });
        }
    }

    @Nested
    @DisplayName("B: Steady-State Polling")
    class SteadyStatePolling {

        @Test
        @DisplayName("B1: 304 Not Modified preserves current bundle without reload")
        void whenServerReturns304ThenNoReload() {
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));

            // Verify config count stays at 1 after additional poll cycles
            await().pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("B2: new ETag triggers bundle hot-reload")
        void whenNewEtagThenBundleReloaded() {
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueBundle(createUnsignedBundle(), "\"v2\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(2));
        }

        @Test
        @DisplayName("B4: multiple pdpIds poll concurrently and independently")
        void whenMultiplePdpIdsThenIndependentFetchLoops() {
            val config = new RemoteBundleSourceConfig(server.url("/bundles").toString(),
                    List.of("production", "staging"), RemoteBundleSourceConfig.FetchMode.POLLING,
                    Duration.ofMillis(100), Duration.ofSeconds(5), null, null, true, developmentPolicy, Map.of(),
                    Duration.ofMillis(50), Duration.ofMillis(200));

            // Enqueue bundles for both pdpIds
            enqueueBundle(createUnsignedBundle(), "\"prod-v1\"");
            enqueueBundle(createUnsignedBundle(), "\"staging-v1\"");
            enqueueNotModified();
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(config);

            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(2));
        }

        @Test
        @DisplayName("B5: auth header sent on every request")
        void whenAuthConfiguredThenHeaderSentOnEveryRequest() throws InterruptedException {
            val config = new RemoteBundleSourceConfig(server.url("/bundles").toString(), List.of(PDP_ID),
                    RemoteBundleSourceConfig.FetchMode.POLLING, Duration.ofMillis(100), Duration.ofSeconds(5),
                    "Authorization", "Bearer test-token", true, developmentPolicy, Map.of(), Duration.ofMillis(50),
                    Duration.ofMillis(200));
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(config);

            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));

            val firstRequest = server.takeRequest(1, TimeUnit.SECONDS);
            assertThat(firstRequest).isNotNull().extracting(r -> r.getHeaders().get("Authorization"))
                    .isEqualTo("Bearer test-token");
        }

        @Test
        @DisplayName("B5: If-None-Match header sent after first successful fetch")
        void whenEtagReceivedThenIfNoneMatchSentOnNextRequest() throws InterruptedException {
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));

            // Skip the first request (no ETag yet)
            server.takeRequest(1, TimeUnit.SECONDS);
            // Second request should have If-None-Match
            val secondRequest = server.takeRequest(2, TimeUnit.SECONDS);
            assertThat(secondRequest).isNotNull().extracting(r -> r.getHeaders().get("If-None-Match"))
                    .isEqualTo("\"v1\"");
        }

        @Test
        @DisplayName("B3: poll interval controls request frequency")
        void whenPollIntervalConfiguredThenRequestFrequencyMatches() {
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            for (int i = 0; i < 20; i++) {
                enqueueNotModified();
            }

            val config = new RemoteBundleSourceConfig(server.url("/bundles").toString(), List.of(PDP_ID),
                    RemoteBundleSourceConfig.FetchMode.POLLING, Duration.ofMillis(200), Duration.ofSeconds(5), null,
                    null, true, developmentPolicy, Map.of(), Duration.ofMillis(50), Duration.ofMillis(200));

            source = new RemoteBundlePDPConfigurationSource(config);
            // Subscribe so the fetch loop activates and starts polling on the configured
            // cadence.
            captureConfigurations(source);

            // With 200ms poll interval, after 2 seconds expect roughly 10 requests
            // (generous margin)
            await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(3))
                    .untilAsserted(() -> assertThat(server.getRequestCount()).isBetween(5, 20));
        }
    }

    @Nested
    @DisplayName("C: Long-Poll Mode")
    class LongPollMode {

        private RemoteBundleSourceConfig longPollConfig() {
            return new RemoteBundleSourceConfig(server.url("/bundles").toString(), List.of(PDP_ID),
                    RemoteBundleSourceConfig.FetchMode.LONG_POLL, Duration.ofMillis(100), Duration.ofSeconds(5), null,
                    null, true, developmentPolicy, Map.of(), Duration.ofMillis(50), Duration.ofMillis(200));
        }

        @Test
        @DisplayName("C1: server responds immediately with new bundle in long-poll mode")
        void whenServerRespondsImmediatelyThenBundleLoaded() {
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(longPollConfig());
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("C2: 304 on long-poll timeout triggers immediate reconnect")
        void whenLongPollTimeoutThenImmediateReconnect() {
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();
            // After 304 in long-poll mode, immediate reconnect (Duration.ZERO delay)
            enqueueBundle(createUnsignedBundle(), "\"v2\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(longPollConfig());
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(2));
        }

        @Test
        @DisplayName("C3: delayed server response still loads bundle in long-poll mode")
        void whenDelayedResponseThenBundleStillLoaded() {
            val buffer = new okio.Buffer();
            buffer.write(createUnsignedBundle());
            server.enqueue(new MockResponse().setBody(buffer).setBodyDelay(500, TimeUnit.MILLISECONDS)
                    .addHeader("Content-Type", "application/octet-stream").addHeader("ETag", "\"v1\""));
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(longPollConfig());
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("C4: graceful fallback when server responds immediately 304 in long-poll mode")
        void whenServerDoesNotSupportLongPollThenFallsBackToPollBehavior() {
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            // Server responds immediately with 304 (no long-poll support)
            enqueueNotModified();
            enqueueNotModified();
            // New bundle available on next poll
            enqueueBundle(createUnsignedBundle(), "\"v2\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(longPollConfig());
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(2));
        }

        @Test
        @DisplayName("C5: connection drop during long-poll hold triggers retry and recovery")
        void whenConnectionDropsDuringLongPollHoldThenRetriesAndRecovers() {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(longPollConfig());
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }
    }

    @Nested
    @DisplayName("D: Network Failures and Recovery")
    class NetworkFailuresAndRecovery {

        @Test
        @DisplayName("D1: serves stale bundle when server becomes unavailable after successful fetch")
        void whenServerBecomesUnavailableThenKeepsServingStaleBundle() {
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            // Server returns errors after first successful fetch
            for (int i = 0; i < 10; i++) {
                enqueueError(503);
            }

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            // Wait for initial bundle load
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));

            // Verify no additional configs loaded despite errors (stale bundle retained)
            await().pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertThat(configs).hasSize(1));

            // Source is still alive (not disposed by errors)
            assertThat(source.isClosed()).isFalse();
        }

        @Test
        @DisplayName("D2: server recovers after outage, node picks up new bundle")
        void whenServerRecoversAfterOutageThenBundleLoaded() {
            // First: server error
            enqueueError(503);
            // Then: recovery with valid bundle
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

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

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("D8: intermittent failures never lose good bundle")
        void whenIntermittentFailuresThenGoodBundleRetained() {
            // Success
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            // Then error
            enqueueError(500);
            // Then recovery with new bundle
            enqueueBundle(createUnsignedBundle(), "\"v2\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(2));
        }

        @Test
        @DisplayName("D4: connection reset mid-transfer triggers retry and recovery")
        void whenConnectionResetDuringResponseBodyThenRetriesAndRecovers() {
            val buffer = new okio.Buffer();
            buffer.write(createUnsignedBundle());
            server.enqueue(
                    new MockResponse().setBody(buffer).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                            .addHeader("Content-Type", "application/octet-stream").addHeader("ETag", "\"v1\""));
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("D5: DNS resolution failure retries without crashing")
        void whenDnsResolutionFailsThenRetriesWithoutCrashing() {
            val config = new RemoteBundleSourceConfig("http://nonexistent.invalid/bundles", List.of(PDP_ID),
                    RemoteBundleSourceConfig.FetchMode.POLLING, Duration.ofMillis(100), Duration.ofSeconds(5), null,
                    null, true, developmentPolicy, Map.of(), Duration.ofMillis(50), Duration.ofMillis(200));

            source = new RemoteBundlePDPConfigurationSource(config);

            await().pollDelay(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                assertThat(source.isClosed()).isFalse();
                assertThat(captureConfigurations(source)).isEmpty();
            });
        }

        @Test
        @DisplayName("D6: server that never responds triggers timeout and retry")
        void whenServerNeverRespondsThenTimeoutAndRetry() {
            val config = new RemoteBundleSourceConfig(server.url("/bundles").toString(), List.of(PDP_ID),
                    RemoteBundleSourceConfig.FetchMode.LONG_POLL, Duration.ofMillis(100), Duration.ofMillis(500), null,
                    null, true, developmentPolicy, Map.of(), Duration.ofMillis(50), Duration.ofMillis(200));

            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(config);

            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }
    }

    @Nested
    @DisplayName("E: Security Enforcement")
    class SecurityEnforcement {

        @Test
        @DisplayName("E1: valid Ed25519 signature with correct key accepted")
        void whenValidSignatureThenBundleAccepted() {
            enqueueBundle(createSignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(signedPolicy));
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("E2: valid signature but wrong public key rejected")
        void whenWrongPublicKeyThenBundleRejected() throws Exception {
            val otherKeyPair   = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            val wrongKeyPolicy = BundleSecurityPolicy.builder(otherKeyPair.getPublic()).build();

            enqueueBundle(createSignedBundle(), "\"v1\"");
            enqueueBundle(createSignedBundle(), "\"v1\"");

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(wrongKeyPolicy));
            val configs = captureConfigurations(source);

            awaitRetries();
            assertThat(configs).isEmpty();
        }

        @Test
        @DisplayName("E3: tampered signed bundle rejected due to integrity check failure")
        void whenTamperedSignedBundleThenRejected() {
            val bundle = createSignedBundle();
            // Tamper with bundle content (corrupts ZIP or signature)
            bundle[bundle.length / 2] ^= 0xFF;
            enqueueBundle(bundle, "\"v1\"");
            enqueueBundle(bundle, "\"v1\"");

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(signedPolicy));
            val configs = captureConfigurations(source);

            awaitRetries();
            assertThat(configs).isEmpty();
        }

        @Test
        @DisplayName("E5: malformed non-ZIP body rejected and retries")
        void whenMalformedZipThenRejected() {
            val garbage = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05 };
            enqueueBundle(garbage, "\"v1\"");
            enqueueBundle(garbage, "\"v1\"");

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            awaitRetries();
            assertThat(configs).isEmpty();
        }

        @Test
        @DisplayName("E7: valid bundle replaces previously rejected bad bundle")
        void whenGoodBundleFollowsBadThenGoodBundleLoaded() {
            // First: unsigned bundle (rejected by signed policy)
            enqueueBundle(createUnsignedBundle(), "\"bad\"");
            // Then: properly signed bundle
            enqueueBundle(createSignedBundle(), "\"good\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(signedPolicy));
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("E8: tenant-specific key from catalogue accepted for matching pdpId")
        void whenTenantSpecificKeyMatchesThenBundleAccepted() throws Exception {
            val tenantKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            val bundle        = BundleBuilder.create().withCombiningAlgorithm(DENY_OVERRIDES)
                    .withPolicy("test.sapl", """
                            policy "test-policy" permit true;
                            """).signWith(tenantKeyPair.getPrivate(), "tenant-key").build();

            val tenantPolicy = BundleSecurityPolicy.builder()
                    .withKeyCatalogue(Map.of("tenant-key", tenantKeyPair.getPublic()))
                    .withTenantTrust(Map.of(PDP_ID, Set.of("tenant-key"))).build();

            enqueueBundle(bundle, "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(tenantPolicy));
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("E8: bundle signed with untrusted key rejected for tenant")
        void whenBundleSignedWithUntrustedKeyForTenantThenRejected() throws Exception {
            val trustedKeyPair  = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            val untrustedBundle = createSignedBundle(); // signed with elderKeyPair, key ID "test-key"

            val tenantPolicy = BundleSecurityPolicy.builder()
                    .withKeyCatalogue(Map.of("trusted-key", trustedKeyPair.getPublic()))
                    .withTenantTrust(Map.of(PDP_ID, Set.of("trusted-key"))).build();

            enqueueBundle(untrustedBundle, "\"v1\"");
            enqueueBundle(untrustedBundle, "\"v1\"");

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(tenantPolicy));
            val configs = captureConfigurations(source);

            awaitRetries();
            assertThat(configs).isEmpty();
        }
    }

    @Nested
    @DisplayName("F: HTTP Edge Cases")
    class HttpEdgeCases {

        @ParameterizedTest(name = "F1/F2: {0} redirect followed to serve bundle")
        @ValueSource(ints = { 301, 302, 307, 308 })
        void whenRedirectThenFollowedAndBundleLoaded(int statusCode) {
            server.enqueue(new MockResponse().setResponseCode(statusCode).setHeader("Location",
                    server.url("/redirected/production").toString()));
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("F3: redirect treated as error when followRedirects disabled")
        void whenRedirectWithFollowDisabledThenTreatedAsError() {
            val config = new RemoteBundleSourceConfig(server.url("/bundles").toString(), List.of(PDP_ID),
                    RemoteBundleSourceConfig.FetchMode.POLLING, Duration.ofMillis(100), Duration.ofSeconds(5), null,
                    null, false, developmentPolicy, Map.of(), Duration.ofMillis(50), Duration.ofMillis(200));

            server.enqueue(
                    new MockResponse().setResponseCode(301).setHeader("Location", server.url("/other").toString()));
            server.enqueue(
                    new MockResponse().setResponseCode(301).setHeader("Location", server.url("/other").toString()));

            source = new RemoteBundlePDPConfigurationSource(config);
            val configs = captureConfigurations(source);

            awaitRetries();
            assertThat(configs).isEmpty();
        }

        @Test
        @DisplayName("F4: a redirect is not followed when an auth header is configured, so the credential is never replayed to the redirect target")
        void whenRedirectWithAuthHeaderThenNotFollowedAndCredentialNotReplayed() throws Exception {
            // followRedirects is enabled, but a custom auth header is configured. Following
            // a
            // redirect would replay that credential to a cross-origin target, so the client
            // must not follow. The first fetch is redirected (treated as an error). The
            // retry
            // serves a valid bundle from the original, configured URL.
            val config = new RemoteBundleSourceConfig(server.url("/bundles").toString(), List.of(PDP_ID),
                    RemoteBundleSourceConfig.FetchMode.POLLING, Duration.ofMillis(100), Duration.ofSeconds(5),
                    "X-Auth-Token", "secret", true, developmentPolicy, Map.of(), Duration.ofMillis(50),
                    Duration.ofMillis(200));

            server.enqueue(
                    new MockResponse().setResponseCode(301).setHeader("Location", server.url("/other").toString()));
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(config);
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));

            // Every request must target the configured base URL and carry the credential.
            // None may hit the redirect target (which would be a cross-origin credential
            // replay).
            RecordedRequest request;
            while ((request = server.takeRequest(50, TimeUnit.MILLISECONDS)) != null) {
                assertThat(request.getPath()).doesNotStartWith("/other");
                assertThat(request.getHeader("X-Auth-Token")).isEqualTo("secret");
            }
        }

        @Test
        @DisplayName("F5: empty response body treated as error")
        void whenEmptyResponseBodyThenRetries() {
            server.enqueue(new MockResponse().addHeader("ETag", "\"empty\""));
            server.enqueue(new MockResponse().addHeader("ETag", "\"empty\""));

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            awaitRetries();
            assertThat(configs).isEmpty();
        }

        @Test
        @DisplayName("F6: wrong Content-Type still parses body as ZIP")
        void whenWrongContentTypeThenStillParsesAsZip() {
            val buffer = new okio.Buffer();
            buffer.write(createUnsignedBundle());
            server.enqueue(new MockResponse().setBody(buffer).addHeader("Content-Type", "application/json")
                    .addHeader("ETag", "\"v1\""));
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("F4: redirect chain handled gracefully with eventual error recovery")
        void whenRedirectChainThenHandledAndRetryRecovers() {
            for (int i = 0; i < 5; i++) {
                server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location",
                        server.url("/hop-" + i).toString()));
            }
            server.enqueue(new MockResponse().setResponseCode(500));
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("F7: large bundle with many policies loads successfully")
        void whenLargeBundleThenLoadedSuccessfully() {
            val builder = BundleBuilder.create().withCombiningAlgorithm(DENY_OVERRIDES);
            for (int i = 0; i < 100; i++) {
                builder.withPolicy("policy-" + i + ".sapl", "policy \"policy-" + i + "\" permit true;\n");
            }
            val largeBundle = builder.build();

            enqueueBundle(largeBundle, "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

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

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));

            source.close();
            val requestCountAfterDispose = server.getRequestCount();

            // Verify request count stays stable after dispose
            await().pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2)).untilAsserted(
                    () -> assertThat(server.getRequestCount()).isLessThanOrEqualTo(requestCountAfterDispose + 1));
        }

        @Test
        @DisplayName("H2: shutdown during active fetch cancels in-flight request")
        void whenDisposedDuringActiveFetchThenCancelled() {
            // Enqueue a slow response to keep a fetch in flight
            server.enqueue(new MockResponse().setBody("slow").setBodyDelay(10, TimeUnit.SECONDS));

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            // Wait for the request to be sent
            await().atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertThat(server.getRequestCount()).isGreaterThanOrEqualTo(1));

            // Dispose while fetch is in flight
            source.close();

            assertThat(source.isClosed()).isTrue();
            assertThat(configs).isEmpty();
        }

        @Test
        @DisplayName("H4: double dispose is idempotent")
        void whenDisposedTwiceThenNoError() {
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));

            source.close();
            source.close(); // Should not throw

            assertThat(source.isClosed()).isTrue();
        }

        @Test
        @DisplayName("H3: shutdown during backoff wait cancels retry loop")
        void whenDisposedDuringBackoffWaitThenCancelled() {
            enqueueError(503);
            enqueueError(503);
            enqueueError(503);

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            // Subscribe so the fetch loop activates and starts hammering the server.
            captureConfigurations(source);

            await().atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertThat(server.getRequestCount()).isGreaterThanOrEqualTo(2));

            source.close();
            val requestCountAfterDispose = server.getRequestCount();

            await().pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2)).untilAsserted(
                    () -> assertThat(server.getRequestCount()).isLessThanOrEqualTo(requestCountAfterDispose + 1));

            assertThat(source.isClosed()).isTrue();
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
            assertThatThrownBy(() -> new RemoteBundleSourceConfig(null, validPdpIds, validMode, validPollInterval,
                    validLongPollTimeout, null, null, true, developmentPolicy, emptyIntervals, validFirstBackoff,
                    validMaxBackoff)).isInstanceOf(PDPConfigurationException.class).hasMessageContaining("baseUrl");
        }

        @Test
        @DisplayName("I1: blank baseUrl fails fast")
        void whenBlankBaseUrlThenFails() {
            assertThatThrownBy(() -> new RemoteBundleSourceConfig("  ", validPdpIds, validMode, validPollInterval,
                    validLongPollTimeout, null, null, true, developmentPolicy, emptyIntervals, validFirstBackoff,
                    validMaxBackoff)).isInstanceOf(PDPConfigurationException.class).hasMessageContaining("baseUrl");
        }

        @Test
        @DisplayName("I2: empty pdpIds list fails fast")
        void whenEmptyPdpIdsThenFails() {
            val emptyPdpIds = List.<String>of();
            assertThatThrownBy(() -> new RemoteBundleSourceConfig("http://example.com", emptyPdpIds, validMode,
                    validPollInterval, validLongPollTimeout, null, null, true, developmentPolicy, emptyIntervals,
                    validFirstBackoff, validMaxBackoff)).isInstanceOf(PDPConfigurationException.class)
                    .hasMessageContaining("pdpIds");
        }

        @Test
        @DisplayName("I3: zero poll interval fails fast")
        void whenZeroPollIntervalThenFails() {
            assertThatThrownBy(() -> new RemoteBundleSourceConfig("http://example.com", validPdpIds, validMode,
                    Duration.ZERO, validLongPollTimeout, null, null, true, developmentPolicy, emptyIntervals,
                    validFirstBackoff, validMaxBackoff)).isInstanceOf(PDPConfigurationException.class)
                    .hasMessageContaining("pollInterval");
        }

        @Test
        @DisplayName("I4: auth header name without value fails fast")
        void whenAuthHeaderNameWithoutValueThenFails() {
            assertThatThrownBy(() -> new RemoteBundleSourceConfig("http://example.com", validPdpIds, validMode,
                    validPollInterval, validLongPollTimeout, "Authorization", null, true, developmentPolicy,
                    emptyIntervals, validFirstBackoff, validMaxBackoff)).isInstanceOf(PDPConfigurationException.class)
                    .hasMessageContaining("authHeaderName");
        }

        @Test
        @DisplayName("I4: auth header value without name fails fast")
        void whenAuthHeaderValueWithoutNameThenFails() {
            assertThatThrownBy(() -> new RemoteBundleSourceConfig("http://example.com", validPdpIds, validMode,
                    validPollInterval, validLongPollTimeout, null, "Bearer token", true, developmentPolicy,
                    emptyIntervals, validFirstBackoff, validMaxBackoff)).isInstanceOf(PDPConfigurationException.class)
                    .hasMessageContaining("authHeaderName");
        }

        @Test
        @DisplayName("I5: valid minimal config starts successfully")
        void whenMinimalValidConfigThenStartsSuccessfully() {
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("I6: per-pdpId poll interval override applied")
        void whenPerPdpIdPollIntervalThenOverrideUsed() {
            val config = new RemoteBundleSourceConfig(server.url("/bundles").toString(), List.of(PDP_ID),
                    RemoteBundleSourceConfig.FetchMode.POLLING, Duration.ofSeconds(60), Duration.ofSeconds(5), null,
                    null, true, developmentPolicy, Map.of(PDP_ID, Duration.ofMillis(100)), Duration.ofMillis(50),
                    Duration.ofMillis(200));

            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();
            enqueueBundle(createUnsignedBundle(), "\"v2\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(config);

            val configs = captureConfigurations(source);

            // With 100ms override (not the 60s default), second fetch should arrive quickly
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(2));
        }
    }

    @Nested
    @DisplayName("resource lifecycle and rate limiting")
    class ResourceLifecycleAndRateLimitingTests {

        @Test
        @DisplayName("close releases the underlying HttpClient")
        void whenClosedThenHttpClientIsTerminated() {
            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));

            source.close();

            assertThat(source.httpClientTerminated()).isTrue();
        }

        @Test
        @DisplayName("long-poll mode keeps a non-zero minimum delay floor between fetches")
        void whenLongPollModeThenPollDelayHasMinimumFloor() {
            source = new RemoteBundlePDPConfigurationSource(longPollConfig(developmentPolicy));

            val pollDelay = source.getPollDelay(PDP_ID);

            assertThat(pollDelay.isZero()).isFalse();
        }
    }

    private RemoteBundleSourceConfig longPollConfig(BundleSecurityPolicy securityPolicy) {
        return new RemoteBundleSourceConfig(server.url("/bundles").toString(), List.of(PDP_ID),
                RemoteBundleSourceConfig.FetchMode.LONG_POLL, Duration.ofMillis(100), Duration.ofSeconds(5), null, null,
                true, securityPolicy, Map.of(), Duration.ofMillis(50), Duration.ofMillis(200));
    }

    @Nested
    @DisplayName("G: Credential Redaction")
    class CredentialRedaction {

        @Test
        @DisplayName("an auth header value with illegal characters is never written to the logs")
        void whenAuthHeaderValueHasIllegalCharactersThenCredentialNotLogged() {
            TestLoggerFactory.clearAll();
            val poisonedCredential = "Bearer SUPER-SECRET-TOKEN\r\nX-Injected: evil";
            val config             = new RemoteBundleSourceConfig(server.url("/bundles").toString(), List.of(PDP_ID),
                    RemoteBundleSourceConfig.FetchMode.POLLING, Duration.ofMillis(100), Duration.ofSeconds(5),
                    "Authorization", poisonedCredential, true, developmentPolicy, Map.of(), Duration.ofMillis(50),
                    Duration.ofMillis(200));

            source = new RemoteBundlePDPConfigurationSource(config);
            captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(TestLoggerFactory.getAllLoggingEvents())
                            .extracting(LoggingEvent::getFormattedMessage)
                            .anyMatch(message -> message.startsWith("Fetch failed for pdpId"))
                            .noneMatch(message -> message.contains("SUPER-SECRET-TOKEN")));
            assertThat(source.isClosed()).isFalse();
        }
    }

}
