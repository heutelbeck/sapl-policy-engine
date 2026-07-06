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
import io.sapl.pdp.configuration.realm.InMemoryRealmSequenceStore;
import io.sapl.pdp.configuration.realm.RealmIndex;
import io.sapl.pdp.configuration.realm.RealmIndexEntry;
import io.sapl.pdp.configuration.realm.RealmIndexSigner;
import io.sapl.pdp.configuration.source.PDPConfigurationSource.ConfigurationEvent;
import com.github.valfirst.slf4jtest.LoggingEvent;
import com.github.valfirst.slf4jtest.TestLoggerFactory;
import lombok.val;
import okhttp3.mockwebserver.Dispatcher;
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
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
    @DisplayName("MULTI: realm index")
    class MultiRealmIndex {

        private final Map<String, Entry>  entries                 = new ConcurrentHashMap<>();
        private final Map<String, byte[]> archive                 = new ConcurrentHashMap<>();
        private final Map<String, String> pinnedUrls              = new ConcurrentHashMap<>();
        private final AtomicInteger       bundleFailuresRemaining = new AtomicInteger(0);
        private final AtomicLong          sequence                = new AtomicLong(1);
        private volatile PrivateKey       indexSigningKey;
        private volatile String           failingPdpId;

        @BeforeEach
        void multiSetUp() {
            indexSigningKey = elderKeyPair.getPrivate();
            server.setDispatcher(realmDispatcher());
        }

        @Test
        @DisplayName("discovers the realm's bundles from the signed index and loads them")
        void whenIndexListsBundlesThenAllAreLoaded() {
            putEntry("orders", "orders@1");
            putEntry("billing", "billing@1");
            val capture = subscribe();
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(capture.configs())
                    .extracting(PDPConfiguration::pdpId).containsExactlyInAnyOrder("orders", "billing"));
        }

        @Test
        @DisplayName("a bundle added to the index is loaded on the next poll")
        void whenEntryAddedThenLoaded() {
            putEntry("orders", "orders@1");
            val capture = subscribe();
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(capture.configs()).hasSize(1));
            putEntry("billing", "billing@1");
            await().atMost(Duration.ofSeconds(3)).untilAsserted(
                    () -> assertThat(capture.configs()).extracting(PDPConfiguration::pdpId).contains("billing"));
        }

        @Test
        @DisplayName("a changed configId reloads the bundle")
        void whenConfigIdChangesThenReloaded() {
            putEntry("orders", "orders@1");
            val capture = subscribe();
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(capture.configs()).hasSize(1));
            putEntry("orders", "orders@2");
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(capture.configs())
                    .extracting(PDPConfiguration::configurationId).contains("orders@2"));
        }

        @Test
        @DisplayName("a bundle removed from the index is removed")
        void whenEntryRemovedThenRemoveEmitted() {
            putEntry("orders", "orders@1");
            putEntry("billing", "billing@1");
            val capture = subscribe();
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(capture.configs()).hasSize(2));
            entries.remove("billing");
            publish();
            await().atMost(Duration.ofSeconds(3))
                    .untilAsserted(() -> assertThat(capture.removedPdpIds()).contains("billing"));
        }

        @Test
        @DisplayName("an index with a stale sequence (rollback) is ignored")
        void whenSequenceRollsBackThenIgnored() {
            putEntry("orders", "orders@1");
            putEntry("billing", "billing@1");
            val capture = subscribe();
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(capture.configs()).hasSize(2));
            entries.remove("billing");
            sequence.set(0);
            // The rolled-back index must stay ignored across several polls, not just once.
            await().atMost(Duration.ofSeconds(2)).during(Duration.ofMillis(600))
                    .untilAsserted(() -> assertThat(capture.removedPdpIds()).doesNotContain("billing"));
        }

        @Test
        @DisplayName("an index signed by an untrusted key is ignored")
        void whenIndexSignedByUntrustedKeyThenIgnored() throws Exception {
            indexSigningKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPrivate();
            putEntry("orders", "orders@1");
            val capture = subscribe();
            // The untrusted index must stay ignored across several polls, not just once.
            await().atMost(Duration.ofSeconds(2)).during(Duration.ofMillis(600))
                    .untilAsserted(() -> assertThat(capture.configs()).isEmpty());
        }

        @Test
        @DisplayName("a transiently failing bundle fetch is retried until resolved and does not block other bundles")
        void whenBundleFetchFailsTransientlyThenRetriedWithoutBlockingOthers() {
            failingPdpId = "orders";
            bundleFailuresRemaining.set(3);
            putEntry("orders", "orders@1");
            putEntry("billing", "billing@1");
            val capture = subscribe();
            // The healthy bundle loads even while the failing one still errors.
            await().atMost(Duration.ofSeconds(3)).untilAsserted(
                    () -> assertThat(capture.configs()).extracting(PDPConfiguration::pdpId).contains("billing"));
            // The failing bundle is retried on subsequent polls, with the index
            // unchanged (304), until the fetch succeeds.
            await().atMost(Duration.ofSeconds(5)).untilAsserted(
                    () -> assertThat(capture.configs()).extracting(PDPConfiguration::pdpId).contains("orders"));
        }

        @Test
        @DisplayName("a subscriber that throws does not prevent other subscribers from receiving events")
        void whenSubscriberThrowsThenOtherSubscribersStillReceiveEvents() {
            putEntry("orders", "orders@1");
            source = new RemoteBundlePDPConfigurationSource(multiConfig());
            source.subscribe(event -> {
                throw new IllegalStateException("hostile subscriber");
            });
            val capture = new CapturingSubscriber();
            source.subscribe(capture);
            await().atMost(Duration.ofSeconds(3)).untilAsserted(
                    () -> assertThat(capture.configs()).extracting(PDPConfiguration::pdpId).contains("orders"));
        }

        @Test
        @DisplayName("a signed rebinding to an immutable version pins the pdpId to that version (rollback)")
        void whenIndexRebindsToImmutableVersionThenPinnedVersionLoaded() {
            putEntry("orders", "orders@1");
            awaitLaterSigningInstant();
            putEntry("orders", "orders@2");
            val capture = subscribe();
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(lastConfigFor(capture, "orders"))
                    .isNotNull().extracting(PDPConfiguration::configurationId).isEqualTo("orders@2"));

            pin("orders", "orders@1");

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(lastConfigFor(capture, "orders"))
                    .extracting(PDPConfiguration::configurationId).isEqualTo("orders@1"));
        }

        @Test
        @DisplayName("replaying an older, validly signed bundle at the latest endpoint is rejected")
        void whenOlderBundleReplayedAtLatestThenRejected() {
            putEntry("orders", "orders@1");
            awaitLaterSigningInstant();
            val capture = subscribe();
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(capture.configs()).hasSize(1));
            putEntry("orders", "orders@2");
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(lastConfigFor(capture, "orders"))
                    .extracting(PDPConfiguration::configurationId).isEqualTo("orders@2"));

            val loadsBeforeReplay = capture.configs().size();
            replayAtLatest("orders", "orders@1");

            // The replayed bytes are re-offered on every poll (their ETag is never stored),
            // so the rejection must hold across several fetch cycles, not just one.
            await().atMost(Duration.ofSeconds(4)).during(Duration.ofMillis(2500))
                    .untilAsserted(() -> assertThat(capture.configs()).hasSize(loadsBeforeReplay));
            assertThat(lastConfigFor(capture, "orders")).extracting(PDPConfiguration::configurationId)
                    .isEqualTo("orders@2");
        }

        @Test
        @DisplayName("a malformed bundle URL in the index is skipped without wedging the other entries")
        void whenIndexEntryHasMalformedUrlThenSkippedAndOthersLoad() {
            putEntry("orders", "orders@1");
            // billing is listed but bound to a URL that fails to parse.
            val billingBytes = buildBundle("billing@1");
            archive.put("billing/billing@1", billingBytes);
            entries.put("billing", new Entry("billing", "billing@1", billingBytes));
            pinnedUrls.put("billing", "http://bad host/bundles/billing");
            publish();
            val capture = subscribe();

            await().atMost(Duration.ofSeconds(3)).untilAsserted(
                    () -> assertThat(capture.configs()).extracting(PDPConfiguration::pdpId).contains("orders"));

            // Reconciliation is not wedged by the bad entry: a later valid entry still loads.
            putEntry("shipping", "shipping@1");
            await().atMost(Duration.ofSeconds(3)).untilAsserted(
                    () -> assertThat(capture.configs()).extracting(PDPConfiguration::pdpId).contains("shipping"));

            assertThat(capture.configs()).extracting(PDPConfiguration::pdpId).doesNotContain("billing");
        }

        @Test
        @DisplayName("the auth credential is withheld from an index-supplied cross-origin bundle URL")
        void whenIndexBindsCrossOriginUrlThenCredentialWithheld() throws IOException {
            try (val bundleHost = new MockWebServer()) {
                bundleHost.start();
                val ordersBytes = buildBundle("orders@1");
                val authSeen    = new AtomicReference<String>("UNSET");
                bundleHost.setDispatcher(new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        authSeen.set(request.getHeader("Authorization"));
                        return bundleResponse(request, "orders@1", ordersBytes);
                    }
                });
                entries.put("orders", new Entry("orders", "orders@1", ordersBytes));
                pinnedUrls.put("orders", bundleHost.url("/bundles/orders").toString());
                publish();

                val authConfig = new RemoteBundleSourceConfig(server.url("/realms/acme").toString(), List.of(),
                        RemoteBundleSourceConfig.FetchMode.MULTI, Duration.ofMillis(100), Duration.ofSeconds(5),
                        "Authorization", "Bearer super-secret", true, true, signedPolicy, Map.of(),
                        Duration.ofMillis(50), Duration.ofMillis(200), "acme", "index");
                source = new RemoteBundlePDPConfigurationSource(authConfig);
                val capture = new CapturingSubscriber();
                source.subscribe(capture);

                await().atMost(Duration.ofSeconds(3)).untilAsserted(
                        () -> assertThat(capture.configs()).extracting(PDPConfiguration::pdpId).contains("orders"));

                // The cross-origin bundle host must never have seen the credential.
                assertThat(authSeen.get()).isNull();
            }
        }

        @Test
        @DisplayName("a bundle removed while its fetch is in flight does not load after the removal")
        void whenChildRemovedWhileFetchInFlightThenNoLateLoad() throws InterruptedException {
            putEntry("orders", "orders@1");
            val billingBytes = buildBundle("billing@1");
            archive.put("billing/billing@1", billingBytes);
            entries.put("billing", new Entry("billing", "billing@1", billingBytes));
            val base       = realmDispatcher();
            val release    = new CountDownLatch(1);
            val billingHit = new CountDownLatch(1);
            server.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                    val path = request.getPath();
                    if (path != null && path.endsWith("/bundles/billing")) {
                        // Hold billing's response open so its fetch is in flight across the removal.
                        billingHit.countDown();
                        release.await();
                        return bundleResponse(request, "billing@1", billingBytes);
                    }
                    return base.dispatch(request);
                }
            });
            publish();

            try {
                val capture = subscribe();
                await().atMost(Duration.ofSeconds(3)).untilAsserted(
                        () -> assertThat(capture.configs()).extracting(PDPConfiguration::pdpId).contains("orders"));
                assertThat(billingHit.await(3, TimeUnit.SECONDS)).isTrue();

                // Remove billing from the index while its fetch is still parked in flight.
                entries.remove("billing");
                publish();
                await().atMost(Duration.ofSeconds(3))
                        .untilAsserted(() -> assertThat(capture.removedPdpIds()).contains("billing"));

                // Releasing the held response must not resurrect the removed child. Assert
                // billing stays absent for a window rather than sleeping and checking once.
                release.countDown();
                await().atMost(Duration.ofSeconds(2)).during(Duration.ofMillis(500))
                        .untilAsserted(() -> assertThat(capture.configs()).extracting(PDPConfiguration::pdpId)
                                .doesNotContain("billing"));
            } finally {
                release.countDown();
            }
        }

        @Test
        @DisplayName("the injected realm sequence store provides the anti-rollback floor")
        void whenSequenceStoreSeededThenLowerSequenceIndexRejectedUntilNewerArrives() {
            val store = new InMemoryRealmSequenceStore();
            store.recordAcceptedSequence("acme", 1000);
            putEntry("orders", "orders@1"); // the served index sequence starts well below the seeded floor
            source = new RemoteBundlePDPConfigurationSource(multiConfig(), store);
            val capture = new CapturingSubscriber();
            source.subscribe(capture);

            // Indexes below the seeded floor are rejected, so nothing loads.
            await().atMost(Duration.ofSeconds(2)).during(Duration.ofMillis(500))
                    .untilAsserted(() -> assertThat(capture.configs()).isEmpty());

            // An index newer than the floor is accepted, confirming the floor came from the store.
            sequence.set(2000);
            await().atMost(Duration.ofSeconds(3)).untilAsserted(
                    () -> assertThat(capture.configs()).extracting(PDPConfiguration::pdpId).contains("orders"));
        }

        private CapturingSubscriber subscribe() {
            source = new RemoteBundlePDPConfigurationSource(multiConfig());
            val capture = new CapturingSubscriber();
            source.subscribe(capture);
            return capture;
        }

        private void putEntry(String pdpId, String configId) {
            val bytes = buildBundle(configId);
            archive.put(pdpId + "/" + configId, bytes);
            entries.put(pdpId, new Entry(pdpId, configId, bytes));
            publish();
        }

        private void publish() {
            sequence.incrementAndGet();
        }

        // Bundles are signed with Instant.now(). Waiting for the clock to advance
        // guarantees the next bundle gets a strictly later signing time.
        private void awaitLaterSigningInstant() {
            val floor = Instant.now();
            await().atMost(Duration.ofSeconds(1)).until(() -> Instant.now().isAfter(floor));
        }

        private void pin(String pdpId, String configId) {
            pinnedUrls.put(pdpId,
                    server.url("/realms/acme/bundles/" + pdpId + "/" + configId + ".saplbundle").toString());
            publish();
        }

        private void replayAtLatest(String pdpId, String configId) {
            // Swap the latest endpoint's content back to archived bytes without an
            // index change, simulating a replay of an older, validly signed bundle.
            entries.put(pdpId, new Entry(pdpId, configId, archive.get(pdpId + "/" + configId)));
        }

        private PDPConfiguration lastConfigFor(CapturingSubscriber capture, String pdpId) {
            return capture.configs().stream().filter(config -> pdpId.equals(config.pdpId())).reduce((a, b) -> b)
                    .orElse(null);
        }

        private RemoteBundleSourceConfig multiConfig() {
            return new RemoteBundleSourceConfig(server.url("/realms/acme").toString(), List.of(),
                    RemoteBundleSourceConfig.FetchMode.MULTI, Duration.ofMillis(100), Duration.ofSeconds(5), null, null,
                    true, true, signedPolicy, Map.of(), Duration.ofMillis(50), Duration.ofMillis(200), "acme", "index");
        }

        private byte[] buildBundle(String configId) {
            return BundleBuilder.create()
                    .withPdpJson(
                            """
                                    { "configurationId": "%s", "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" } }"""
                                    .formatted(configId))
                    .withPolicy("test.sapl", "policy \"p\" permit true;")
                    .signWith(elderKeyPair.getPrivate(), "test-key").build();
        }

        private Dispatcher realmDispatcher() {
            return new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    val path = request.getPath();
                    if (path == null) {
                        return new MockResponse().setResponseCode(404);
                    }
                    if (path.endsWith("/index")) {
                        return indexResponse(request);
                    }
                    for (val archived : archive.entrySet()) {
                        if (path.endsWith("/bundles/" + archived.getKey() + ".saplbundle")) {
                            return bundleResponse(request, archived.getKey().split("/")[1], archived.getValue());
                        }
                    }
                    for (val entry : entries.values()) {
                        if (path.endsWith("/bundles/" + entry.pdpId())) {
                            if (entry.pdpId().equals(failingPdpId) && bundleFailuresRemaining.getAndDecrement() > 0) {
                                return new MockResponse().setResponseCode(500);
                            }
                            return bundleResponse(request, entry.configId(), entry.bytes());
                        }
                    }
                    return new MockResponse().setResponseCode(404);
                }
            };
        }

        private MockResponse bundleResponse(RecordedRequest request, String configId, byte[] bytes) {
            val etag = "\"" + configId + "\"";
            if (etag.equals(request.getHeader("If-None-Match"))) {
                return new MockResponse().setResponseCode(304);
            }
            val buffer = new okio.Buffer();
            buffer.write(bytes);
            return new MockResponse().setBody(buffer).addHeader("Content-Type", "application/octet-stream")
                    .addHeader("ETag", etag);
        }

        private MockResponse indexResponse(RecordedRequest request) {
            val currentSequence = sequence.get();
            val etag            = "\"" + currentSequence + "\"";
            if (etag.equals(request.getHeader("If-None-Match"))) {
                return new MockResponse().setResponseCode(304);
            }
            val bundles = new ArrayList<RealmIndexEntry>();
            for (val entry : entries.values()) {
                val url = pinnedUrls.getOrDefault(entry.pdpId(),
                        server.url("/realms/acme/bundles/" + entry.pdpId()).toString());
                bundles.add(new RealmIndexEntry(entry.pdpId(), url));
            }
            val index = new RealmIndex("acme", currentSequence, "2026-07-04T00:00:00Z", bundles);
            val jws   = RealmIndexSigner.sign(index, indexSigningKey, "test-key");
            return new MockResponse().setBody(jws).addHeader("Content-Type", "application/jose").addHeader("ETag",
                    etag);
        }

        private record Entry(String pdpId, String configId, byte[] bytes) {}
    }

    @Nested
    @DisplayName("Bootstrap and First Fetch")
    class BootstrapAndFirstFetch {

        @Test
        @DisplayName("fetches and loads valid signed bundle on startup")
        void whenServerAvailableAtStartupThenBundleLoaded() {
            enqueueBundle(createSignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(signedPolicy));
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @ParameterizedTest(name = "server returns {0} at startup triggers retry")
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
        @DisplayName("rejects invalid/non-ZIP body and retries")
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
        @DisplayName("rejects unsigned bundle when signatures mandatory")
        void whenUnsignedBundleWithMandatorySignaturesThenRejects() {
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueBundle(createUnsignedBundle(), "\"v1\"");

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(signedPolicy));
            val configs = captureConfigurations(source);

            awaitRetries();
            assertThat(configs).isEmpty();
        }

        @Test
        @DisplayName("accepts unsigned bundle with dev escape hatch active")
        void whenUnsignedBundleWithDevEscapeHatchThenAccepted() {
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("server unreachable at startup retries without crashing")
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
    @DisplayName("Steady-State Polling")
    class SteadyStatePolling {

        @Test
        @DisplayName("304 Not Modified preserves current bundle without reload")
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
        @DisplayName("new ETag triggers bundle hot-reload")
        void whenNewEtagThenBundleReloaded() {
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueBundle(createUnsignedBundle(), "\"v2\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(2));
        }

        @Test
        @DisplayName("multiple pdpIds poll concurrently and independently")
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
        @DisplayName("auth header sent on every request")
        void whenAuthConfiguredThenHeaderSentOnEveryRequest() throws InterruptedException {
            val config = new RemoteBundleSourceConfig(server.url("/bundles").toString(), List.of(PDP_ID),
                    RemoteBundleSourceConfig.FetchMode.POLLING, Duration.ofMillis(100), Duration.ofSeconds(5),
                    "Authorization", "Bearer test-token", true, true, developmentPolicy, Map.of(),
                    Duration.ofMillis(50), Duration.ofMillis(200));
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
        @DisplayName("If-None-Match header sent after first successful fetch")
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
        @DisplayName("poll interval controls request frequency")
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
    @DisplayName("Long-Poll Mode")
    class LongPollMode {

        private RemoteBundleSourceConfig longPollConfig() {
            return new RemoteBundleSourceConfig(server.url("/bundles").toString(), List.of(PDP_ID),
                    RemoteBundleSourceConfig.FetchMode.LONG_POLL, Duration.ofMillis(100), Duration.ofSeconds(5), null,
                    null, true, developmentPolicy, Map.of(), Duration.ofMillis(50), Duration.ofMillis(200));
        }

        @Test
        @DisplayName("server responds immediately with new bundle in long-poll mode")
        void whenServerRespondsImmediatelyThenBundleLoaded() {
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(longPollConfig());
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("304 on long-poll timeout triggers immediate reconnect")
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
        @DisplayName("delayed server response still loads bundle in long-poll mode")
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
        @DisplayName("graceful fallback when server responds immediately 304 in long-poll mode")
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
        @DisplayName("connection drop during long-poll hold triggers retry and recovery")
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
    @DisplayName("Network Failures and Recovery")
    class NetworkFailuresAndRecovery {

        @Test
        @DisplayName("serves stale bundle when server becomes unavailable after successful fetch")
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
        @DisplayName("server recovers after outage, node picks up new bundle")
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
        @DisplayName("extended outage caps backoff and retries indefinitely")
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
        @DisplayName("intermittent failures never lose good bundle")
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
        @DisplayName("connection reset mid-transfer triggers retry and recovery")
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
        @DisplayName("DNS resolution failure retries without crashing")
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
        @DisplayName("server that never responds triggers timeout and retry")
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
    @DisplayName("Security Enforcement")
    class SecurityEnforcement {

        @Test
        @DisplayName("valid Ed25519 signature with correct key accepted")
        void whenValidSignatureThenBundleAccepted() {
            enqueueBundle(createSignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(signedPolicy));
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("valid signature but wrong public key rejected")
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
        @DisplayName("tampered signed bundle rejected due to integrity check failure")
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
        @DisplayName("malformed non-ZIP body rejected and retries")
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
        @DisplayName("valid bundle replaces previously rejected bad bundle")
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
        @DisplayName("tenant-specific key from catalogue accepted for matching pdpId")
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
        @DisplayName("bundle signed with untrusted key rejected for tenant")
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
    @DisplayName("HTTP Edge Cases")
    class HttpEdgeCases {

        @ParameterizedTest(name = "{0} redirect followed to serve bundle")
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
        @DisplayName("redirect treated as error when followRedirects disabled")
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
        @DisplayName("a 304 to an unconditional request is treated as a failure, not a fresh contact")
        void whenNotModifiedWithoutPriorEtagThenTreatedAsError() {
            server.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    return new MockResponse().setResponseCode(304);
                }
            });

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            awaitRetries();
            assertThat(configs).isEmpty();
            assertThat(source.isClosed()).isFalse();
        }

        @Test
        @DisplayName("a redirect is not followed when an auth header is configured, so the credential is never replayed to the redirect target")
        void whenRedirectWithAuthHeaderThenNotFollowedAndCredentialNotReplayed() throws Exception {
            // followRedirects is enabled, but a custom auth header is configured. Following
            // a
            // redirect would replay that credential to a cross-origin target, so the client
            // must not follow. The first fetch is redirected (treated as an error). The
            // retry
            // serves a valid bundle from the original, configured URL.
            val config = new RemoteBundleSourceConfig(server.url("/bundles").toString(), List.of(PDP_ID),
                    RemoteBundleSourceConfig.FetchMode.POLLING, Duration.ofMillis(100), Duration.ofSeconds(5),
                    "X-Auth-Token", "secret", true, true, developmentPolicy, Map.of(), Duration.ofMillis(50),
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
        @DisplayName("empty response body treated as error")
        void whenEmptyResponseBodyThenRetries() {
            server.enqueue(new MockResponse().addHeader("ETag", "\"empty\""));
            server.enqueue(new MockResponse().addHeader("ETag", "\"empty\""));

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            awaitRetries();
            assertThat(configs).isEmpty();
        }

        @Test
        @DisplayName("wrong Content-Type still parses body as ZIP")
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
        @DisplayName("redirect chain handled gracefully with eventual error recovery")
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
        @DisplayName("large bundle with many policies loads successfully")
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
    @DisplayName("Lifecycle and Cleanup")
    class LifecycleAndCleanup {

        @Test
        @DisplayName("dispose cancels all fetch loops")
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
        @DisplayName("shutdown during active fetch cancels in-flight request")
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
        @DisplayName("double dispose is idempotent")
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
        @DisplayName("shutdown during backoff wait cancels retry loop")
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
    @DisplayName("Configuration Validation")
    class ConfigurationValidation {

        private final List<String>                       validPdpIds          = List.of(PDP_ID);
        private final RemoteBundleSourceConfig.FetchMode validMode            = RemoteBundleSourceConfig.FetchMode.POLLING;
        private final Duration                           validPollInterval    = Duration.ofSeconds(30);
        private final Duration                           validLongPollTimeout = Duration.ofSeconds(30);
        private final Map<String, Duration>              emptyIntervals       = Map.of();
        private final Duration                           validFirstBackoff    = Duration.ofMillis(500);
        private final Duration                           validMaxBackoff      = Duration.ofSeconds(5);

        @Test
        @DisplayName("missing baseUrl fails fast")
        void whenMissingBaseUrlThenFails() {
            assertThatThrownBy(() -> new RemoteBundleSourceConfig(null, validPdpIds, validMode, validPollInterval,
                    validLongPollTimeout, null, null, true, developmentPolicy, emptyIntervals, validFirstBackoff,
                    validMaxBackoff)).isInstanceOf(PDPConfigurationException.class).hasMessageContaining("baseUrl");
        }

        @Test
        @DisplayName("blank baseUrl fails fast")
        void whenBlankBaseUrlThenFails() {
            assertThatThrownBy(() -> new RemoteBundleSourceConfig("  ", validPdpIds, validMode, validPollInterval,
                    validLongPollTimeout, null, null, true, developmentPolicy, emptyIntervals, validFirstBackoff,
                    validMaxBackoff)).isInstanceOf(PDPConfigurationException.class).hasMessageContaining("baseUrl");
        }

        @Test
        @DisplayName("empty pdpIds list fails fast")
        void whenEmptyPdpIdsThenFails() {
            val emptyPdpIds = List.<String>of();
            assertThatThrownBy(() -> new RemoteBundleSourceConfig("http://example.com", emptyPdpIds, validMode,
                    validPollInterval, validLongPollTimeout, null, null, true, developmentPolicy, emptyIntervals,
                    validFirstBackoff, validMaxBackoff)).isInstanceOf(PDPConfigurationException.class)
                    .hasMessageContaining("pdpIds");
        }

        @Test
        @DisplayName("zero poll interval fails fast")
        void whenZeroPollIntervalThenFails() {
            assertThatThrownBy(() -> new RemoteBundleSourceConfig("http://example.com", validPdpIds, validMode,
                    Duration.ZERO, validLongPollTimeout, null, null, true, developmentPolicy, emptyIntervals,
                    validFirstBackoff, validMaxBackoff)).isInstanceOf(PDPConfigurationException.class)
                    .hasMessageContaining("pollInterval");
        }

        @Test
        @DisplayName("auth header name without value fails fast")
        void whenAuthHeaderNameWithoutValueThenFails() {
            assertThatThrownBy(() -> new RemoteBundleSourceConfig("http://example.com", validPdpIds, validMode,
                    validPollInterval, validLongPollTimeout, "Authorization", null, true, developmentPolicy,
                    emptyIntervals, validFirstBackoff, validMaxBackoff)).isInstanceOf(PDPConfigurationException.class)
                    .hasMessageContaining("authHeaderName");
        }

        @Test
        @DisplayName("auth header value without name fails fast")
        void whenAuthHeaderValueWithoutNameThenFails() {
            assertThatThrownBy(() -> new RemoteBundleSourceConfig("http://example.com", validPdpIds, validMode,
                    validPollInterval, validLongPollTimeout, null, "Bearer token", true, developmentPolicy,
                    emptyIntervals, validFirstBackoff, validMaxBackoff)).isInstanceOf(PDPConfigurationException.class)
                    .hasMessageContaining("authHeaderName");
        }

        @Test
        @DisplayName("valid minimal config starts successfully")
        void whenMinimalValidConfigThenStartsSuccessfully() {
            enqueueBundle(createUnsignedBundle(), "\"v1\"");
            enqueueNotModified();

            source = new RemoteBundlePDPConfigurationSource(defaultConfig(developmentPolicy));
            val configs = captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSize(1));
        }

        @Test
        @DisplayName("per-pdpId poll interval override applied")
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

            val pollDelay = source.getPollDelay(PDP_ID, true);

            assertThat(pollDelay.isZero()).isFalse();
        }
    }

    private RemoteBundleSourceConfig longPollConfig(BundleSecurityPolicy securityPolicy) {
        return new RemoteBundleSourceConfig(server.url("/bundles").toString(), List.of(PDP_ID),
                RemoteBundleSourceConfig.FetchMode.LONG_POLL, Duration.ofMillis(100), Duration.ofSeconds(5), null, null,
                true, securityPolicy, Map.of(), Duration.ofMillis(50), Duration.ofMillis(200));
    }

    @Nested
    @DisplayName("Credential Redaction")
    class CredentialRedaction {

        @Test
        @DisplayName("an auth header value with illegal characters is never written to the logs")
        void whenAuthHeaderValueHasIllegalCharactersThenCredentialNotLogged() {
            TestLoggerFactory.clearAll();
            val poisonedCredential = "Bearer SUPER-SECRET-TOKEN\r\nX-Injected: evil";
            val config             = new RemoteBundleSourceConfig(server.url("/bundles").toString(), List.of(PDP_ID),
                    RemoteBundleSourceConfig.FetchMode.POLLING, Duration.ofMillis(100), Duration.ofSeconds(5),
                    "Authorization", poisonedCredential, true, true, developmentPolicy, Map.of(), Duration.ofMillis(50),
                    Duration.ofMillis(200));

            source = new RemoteBundlePDPConfigurationSource(config);
            captureConfigurations(source);

            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(TestLoggerFactory.getAllLoggingEvents())
                            .extracting(LoggingEvent::getFormattedMessage)
                            .anyMatch(message -> message.startsWith("Fetch failed for"))
                            .noneMatch(message -> message.contains("SUPER-SECRET-TOKEN")));
            assertThat(source.isClosed()).isFalse();
        }
    }

    @Nested
    @DisplayName("Transport Freshness")
    class TransportFreshness {

        private final MutableClock clock = new MutableClock();

        private RemoteBundleSourceConfig stalenessConfig(Duration staleAfterNoContact) {
            return stalenessConfig(staleAfterNoContact, null);
        }

        private RemoteBundleSourceConfig stalenessConfig(Duration staleAfterNoContact,
                Duration failClosedAfterNoContact) {
            return new RemoteBundleSourceConfig(server.url("/bundles").toString(), List.of(PDP_ID),
                    RemoteBundleSourceConfig.FetchMode.POLLING, Duration.ofMillis(50), Duration.ofSeconds(5), null,
                    null, false, true, developmentPolicy, Map.of(), Duration.ofMillis(20), Duration.ofMillis(80), null,
                    null, staleAfterNoContact, failClosedAfterNoContact);
        }

        @Test
        @DisplayName("no successful contact past the staleness threshold escalates to a stale error and recovers on the next contact")
        void whenNoContactPastThresholdThenStaleErrorThenRecovery() {
            val serving = new AtomicBoolean(false);
            server.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    if (serving.get()) {
                        val buffer = new okio.Buffer();
                        buffer.write(createUnsignedBundle());
                        return new MockResponse().setBody(buffer).addHeader("ETag", "v1");
                    }
                    return new MockResponse().setResponseCode(503);
                }
            });

            source = new RemoteBundlePDPConfigurationSource(stalenessConfig(Duration.ofSeconds(30)), clock);
            val capture = new CapturingSubscriber();
            source.subscribe(capture);

            // The loop anchors the freshness clock at its start instant, so waiting for a
            // first request guarantees the anchor is in place before the clock advances.
            await().atMost(Duration.ofSeconds(3))
                    .untilAsserted(() -> assertThat(server.getRequestCount()).isGreaterThanOrEqualTo(1));

            clock.advance(Duration.ofSeconds(31));

            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(capture.errors())
                    .extracting(ConfigurationEvent.ConfigurationError::pdpId).contains(PDP_ID));

            serving.set(true);
            await().atMost(Duration.ofSeconds(3)).untilAsserted(
                    () -> assertThat(capture.configs()).extracting(PDPConfiguration::pdpId).contains(PDP_ID));
        }

        @Test
        @DisplayName("no successful contact past the configured fail-closed threshold escalates to an expiry event")
        void whenNoContactPastFailClosedThresholdThenExpired() {
            server.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    return new MockResponse().setResponseCode(503);
                }
            });

            source = new RemoteBundlePDPConfigurationSource(
                    stalenessConfig(Duration.ofSeconds(30), Duration.ofSeconds(90)), clock);
            val capture = new CapturingSubscriber();
            source.subscribe(capture);

            await().atMost(Duration.ofSeconds(3))
                    .untilAsserted(() -> assertThat(server.getRequestCount()).isGreaterThanOrEqualTo(1));

            clock.advance(Duration.ofSeconds(31));
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(capture.errors())
                    .extracting(ConfigurationEvent.ConfigurationError::pdpId).contains(PDP_ID));
            assertThat(capture.expirations()).isEmpty();

            clock.advance(Duration.ofSeconds(60));
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(capture.expirations())
                    .extracting(ConfigurationEvent.ConfigurationExpired::pdpId).contains(PDP_ID));
        }
    }

    private static final class MutableClock implements InstantSource {

        private volatile Instant now = Instant.EPOCH;

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }
    }

}
