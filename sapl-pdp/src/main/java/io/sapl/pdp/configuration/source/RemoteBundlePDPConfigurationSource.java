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

import io.sapl.pdp.configuration.PDPConfigurationException;
import io.sapl.pdp.configuration.bundle.BundleParser;
import io.sapl.pdp.configuration.bundle.BundleSignatureException;
import io.sapl.pdp.configuration.realm.InMemoryRealmSequenceStore;
import io.sapl.pdp.configuration.realm.RealmIndex;
import io.sapl.pdp.configuration.realm.RealmIndexEntry;
import io.sapl.pdp.configuration.realm.RealmIndexException;
import io.sapl.pdp.configuration.realm.RealmIndexVerifier;
import io.sapl.pdp.configuration.realm.RealmSequenceStore;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static io.sapl.pdp.configuration.source.RemoteBundleSourceConfig.FetchMode.LONG_POLL;
import static io.sapl.pdp.configuration.source.RemoteBundleSourceConfig.FetchMode.MULTI;

/**
 * PDP configuration source that fetches {@code .saplbundle} files from a
 * remote HTTP server using the JDK's {@link HttpClient} on virtual threads.
 * <p>
 * For each configured PDP ID, an independent fetch loop runs on its own
 * virtual thread. Change detection uses HTTP conditional requests
 * (ETag / If-None-Match). Bundle parsing and signature verification are
 * delegated to {@link BundleParser}. Fetched bundles are emitted as
 * {@link ConfigurationEvent.NewConfiguration} to subscribers.
 * </p>
 * <h2>Fetch Loop</h2>
 * <p>
 * Each loop runs straight-line blocking code on a virtual thread, sleeping
 * between iterations via {@link Thread#sleep(Duration)}. Errors trigger
 * exponential backoff with 50% jitter capped at
 * {@link RemoteBundleSourceConfig#maxBackoff()}, with the backoff resetting
 * to {@link RemoteBundleSourceConfig#firstBackoff()} on the first successful
 * fetch.
 * </p>
 * <h2>Cancellation</h2>
 * <p>
 * {@link #close()} interrupts every fetch thread; in-flight blocking
 * {@link HttpClient#send} calls unblock with an {@link IOException} or
 * {@link InterruptedException}, the loop exits cleanly.
 * </p>
 * <h2>Thread Safety</h2>
 * <p>
 * Thread-safe. ETags are stored in a {@link ConcurrentHashMap}. The
 * subscriber set uses {@link ConcurrentHashMap#newKeySet()}. The thread list
 * uses {@link CopyOnWriteArrayList}.
 * </p>
 *
 * @see RemoteBundleSourceConfig
 * @see BundleParser
 */
@Slf4j
public final class RemoteBundlePDPConfigurationSource implements PDPConfigurationSource {

    // A key for the index ETag that cannot collide with any pdpId (which match
    // ^[a-zA-Z0-9._-]+$), so bundle and index ETags share one map safely.
    private static final String INDEX_ETAG_KEY = "\u0000realm-index";

    private static final String BUNDLE_EXTENSION              = ".saplbundle";
    private static final String CONTENT_TYPE_BUNDLE           = "application/octet-stream";
    private static final String CONTENT_TYPE_INDEX            = "application/jose";
    private static final String ERROR_EMPTY_RESPONSE_BODY     = "Server returned 200 with empty body.";
    private static final String ERROR_HTTP_STATUS             = "Server returned HTTP %d for '%s'.";
    private static final String ERROR_ILLEGAL_AUTH_HEADER     = "Configured auth header for '%s' contains characters that are illegal in an HTTP header.";
    private static final String ERROR_UNEXPECTED_NOT_MODIFIED = "Server returned 304 for '%s' without a matching conditional request.";

    private static final String WARN_CREDENTIAL_WITHHELD_CROSS_ORIGIN  = "Withholding the configured auth credential from '{}' because it is not on the bundle source origin '{}'. A credential is only ever sent to the configured origin.";
    private static final String WARN_FETCH_FAILED                      = "Fetch failed for '{}' (retry #{}): {}";
    private static final String WARN_INDEX_REJECTED                    = "Realm index rejected, keeping current configuration: {}";
    private static final String WARN_INVALID_INDEX_URL                 = "Ignoring realm index entry for pdpId '{}' because its URL '{}' is not a valid http or https URL.";
    private static final String WARN_REDIRECTS_DISABLED_FOR_CREDENTIAL = "Disabling redirect following for bundle source '{}' because a custom auth header is configured. A redirect would replay the credential to a cross-origin target. Point at the final URL to keep redirects.";
    private static final String WARN_STALE_BUNDLE_REJECTED             = "Rejected bundle for pdpId '{}': its signing time {} is older than the currently loaded bundle's {}. Keeping the current configuration.";
    private static final String WARN_EXPIRED_NO_CONTACT                = "Remote bundle for pdpId '{}' had no successful contact for over {}. Failing closed and dropping the last-good configuration.";
    private static final String WARN_STALE_NO_CONTACT                  = "Remote bundle for pdpId '{}' had no successful contact for over {}. Keeping the last-good configuration and marking it stale.";
    private static final String WARN_SUBSCRIBER_THREW                  = "A subscriber threw while handling a configuration event: {}";

    private static final String EXPIRED_REASON = "No successful contact with the bundle server within the configured fail-closed threshold.";
    private static final String STALE_REASON   = "No successful contact with the bundle server within the configured staleness threshold.";

    private static final int MAX_BUNDLE_RESPONSE_BYTES = 256 * 1024 * 1024;

    private static final Duration CONNECT_TIMEOUT          = Duration.ofSeconds(10);
    private static final Duration POLLING_RESPONSE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration LONG_POLL_TIMEOUT_BUFFER = Duration.ofSeconds(5);
    private static final Duration LONG_POLL_MIN_DELAY      = Duration.ofSeconds(1);

    private final RemoteBundleSourceConfig          config;
    private final URI                               baseUri;
    private final HttpClient                        httpClient;
    private final InstantSource                     clock;
    private final RealmSequenceStore                sequenceStore;
    private final ConcurrentHashMap<String, String> etags       = new ConcurrentHashMap<>();
    private final List<Thread>                      loopThreads = new CopyOnWriteArrayList<>();
    private final Set<Consumer<ConfigurationEvent>> subscribers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean                     activated   = new AtomicBoolean(false);
    private final AtomicBoolean                     closed      = new AtomicBoolean(false);

    // MULTI mode: one autonomous child fetch loop per pdpId, bound to the URL
    // the signed index assigns. The map is mutated only on the index-monitor
    // thread. A binding pointing at the mutable latest endpoint tracks updates,
    // a binding pointing at an immutable version pins the pdpId to it.
    private final Map<String, ChildLoop> childLoops = new HashMap<>();

    // Per pdpId signing time of the currently loaded bundle. Within one binding
    // the signing time must never move backwards, which defends against replay
    // of an older, validly signed bundle at a mutable URL. A signed rebinding
    // through the index resets the guard, so deliberate pins and rollbacks work.
    private final ConcurrentHashMap<String, Instant> lastSignedAt = new ConcurrentHashMap<>();

    // Per-pdpId wall-clock time of the last successful contact (a 200 or 304), and the
    // sets of pdpIds already escalated to STALE and to fail-closed for the current
    // outage. A successful contact resets the escalations so a recovery is reported again.
    private final ConcurrentHashMap<String, Instant> lastContact         = new ConcurrentHashMap<>();
    private final Set<String>                        staleEscalated      = ConcurrentHashMap.newKeySet();
    private final Set<String>                        failClosedEscalated = ConcurrentHashMap.newKeySet();

    private record ChildLoop(String url, Thread thread) {}

    /**
     * Creates a remote bundle source.
     *
     * @param config the remote bundle configuration
     * @throws NullPointerException if {@code config} is null
     * @throws io.sapl.pdp.configuration.bundle.BundleSignatureException if
     * the security policy is invalid
     */
    public RemoteBundlePDPConfigurationSource(@NonNull RemoteBundleSourceConfig config) {
        this(config, new InMemoryRealmSequenceStore());
    }

    /**
     * Creates a remote bundle source with a specific realm sequence store.
     *
     * @param config the remote bundle configuration
     * @param sequenceStore the anti-rollback sequence store (a persistent one
     * preserves the baseline across restarts)
     * @throws NullPointerException if any argument is null
     * @throws io.sapl.pdp.configuration.bundle.BundleSignatureException if
     * the security policy is invalid
     */
    public RemoteBundlePDPConfigurationSource(@NonNull RemoteBundleSourceConfig config,
            @NonNull RealmSequenceStore sequenceStore) {
        this(config, Clock.systemUTC(), sequenceStore);
    }

    // Package-private clock-injecting constructor for controllable-time tests. This clock
    // is the source's own wall-clock for freshness timing only, deliberately separate from
    // the PDP temporal clock and the observability timestamp source, neither of which is
    // appropriate for config polling.
    RemoteBundlePDPConfigurationSource(@NonNull RemoteBundleSourceConfig config, @NonNull InstantSource clock) {
        this(config, clock, new InMemoryRealmSequenceStore());
    }

    RemoteBundlePDPConfigurationSource(@NonNull RemoteBundleSourceConfig config,
            @NonNull InstantSource clock,
            @NonNull RealmSequenceStore sequenceStore) {
        this.config        = Objects.requireNonNull(config, "config");
        this.clock         = Objects.requireNonNull(clock, "clock");
        this.sequenceStore = Objects.requireNonNull(sequenceStore, "sequenceStore");
        this.baseUri       = URI.create(config.baseUrl());
        config.securityPolicy().validate();
        this.httpClient = HttpClient.newBuilder().followRedirects(redirectPolicy(config))
                .connectTimeout(CONNECT_TIMEOUT).build();
    }

    private static HttpClient.Redirect redirectPolicy(RemoteBundleSourceConfig config) {
        if (!config.followRedirects()) {
            return HttpClient.Redirect.NEVER;
        }
        if (config.authHeaderName() != null) {
            log.warn(WARN_REDIRECTS_DISABLED_FOR_CREDENTIAL, config.baseUrl());
            return HttpClient.Redirect.NEVER;
        }
        return HttpClient.Redirect.NORMAL;
    }

    @Override
    public void subscribe(@NonNull Consumer<ConfigurationEvent> listener) {
        if (closed.get()) {
            return;
        }
        subscribers.add(listener);
        if (activated.compareAndSet(false, true)) {
            startFetchLoops();
        }
    }

    @Override
    public void unsubscribe(@NonNull Consumer<ConfigurationEvent> listener) {
        subscribers.remove(listener);
    }

    /**
     * Cancels all fetch loops by interrupting their virtual threads and
     * clears the subscriber set. Idempotent: subsequent calls have no
     * effect.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            for (val thread : loopThreads) {
                thread.interrupt();
            }
            httpClient.close();
            subscribers.clear();
            log.debug("Closed remote bundle configuration source.");
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    private void emit(ConfigurationEvent event) {
        if (closed.get()) {
            return;
        }
        for (val subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (RuntimeException e) {
                // Isolate subscribers: a throwing one must not skip the others,
                // and its failure must not read as a transport error in the
                // fetch loop.
                log.warn(WARN_SUBSCRIBER_THREW, e.getMessage());
            }
        }
    }

    private void startFetchLoops() {
        if (config.mode() == MULTI) {
            val thread = Thread.ofVirtual().name("sapl-bundle-index-monitor").start(this::runIndexLoop);
            loopThreads.add(thread);
            log.info("Started remote bundle index monitor for realm '{}'.", config.realm());
            return;
        }
        for (val pdpId : config.pdpIds()) {
            val thread = Thread.ofVirtual().name("sapl-bundle-fetch-" + pdpId)
                    .start(() -> runFetchLoop(pdpId, bundleUri(pdpId), config.mode() == LONG_POLL));
            loopThreads.add(thread);
            log.info("Started remote bundle fetch loop for pdpId '{}'.", pdpId);
        }
    }

    private void runFetchLoop(String pdpId, URI uri, boolean longPoll) {
        // Start the freshness clock at loop start so a pdpId that never connects still
        // escalates after the threshold.
        lastContact.putIfAbsent(stripBundleExtension(pdpId), clock.instant());
        Duration backoff    = config.firstBackoff();
        long     retryCount = 0;
        while (!closed.get() && !Thread.currentThread().isInterrupted()) {
            try {
                attemptFetch(pdpId, uri, longPoll);
                backoff    = config.firstBackoff();
                retryCount = 0;
                val pollDelay = getPollDelay(pdpId, longPoll);
                if (!pollDelay.isZero()) {
                    Thread.sleep(pollDelay);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                retryCount++;
                log.warn(WARN_FETCH_FAILED, pdpId, retryCount, e.getMessage());
                maybeEscalateStaleness(pdpId);
                try {
                    Thread.sleep(jitter(backoff));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                backoff = nextBackoff(backoff);
            }
        }
    }

    private void runIndexLoop() {
        Duration backoff    = config.firstBackoff();
        long     retryCount = 0;
        while (!closed.get() && !Thread.currentThread().isInterrupted()) {
            try {
                attemptIndexFetch();
                backoff    = config.firstBackoff();
                retryCount = 0;
                val pollDelay = config.pollInterval();
                if (!pollDelay.isZero()) {
                    Thread.sleep(pollDelay);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                retryCount++;
                log.warn(WARN_FETCH_FAILED, config.realm(), retryCount, e.getMessage());
                try {
                    Thread.sleep(jitter(backoff));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                backoff = nextBackoff(backoff);
            }
        }
    }

    private void attemptFetch(String pdpId, URI uri, boolean longPoll) throws IOException, InterruptedException {
        val request  = buildRequest(uri, pdpId, CONTENT_TYPE_BUNDLE, longPoll);
        val response = httpClient.send(request, info -> new BoundedByteArrayBodySubscriber(MAX_BUNDLE_RESPONSE_BYTES));
        handleResponse(pdpId, response);
    }

    private void attemptIndexFetch() throws IOException, InterruptedException {
        // The index loop only runs in MULTI mode, whose config validation guarantees a realm.
        val realm    = Objects.requireNonNull(config.realm());
        val request  = buildRequest(indexUri(), INDEX_ETAG_KEY, CONTENT_TYPE_INDEX, false);
        val response = httpClient.send(request, info -> new BoundedByteArrayBodySubscriber(MAX_BUNDLE_RESPONSE_BYTES));
        val status   = response.statusCode();
        if (status == 304) {
            log.debug("Realm index unchanged for realm '{}' (304 Not Modified).", realm);
            return;
        }
        if (status < 200 || status >= 300) {
            throw new IOException(ERROR_HTTP_STATUS.formatted(status, realm));
        }
        val bytes = response.body();
        if (bytes == null || bytes.length == 0) {
            throw new IOException(ERROR_EMPTY_RESPONSE_BODY);
        }
        final RealmIndex index;
        try {
            index = RealmIndexVerifier.verify(new String(bytes, StandardCharsets.UTF_8), config.securityPolicy(), realm,
                    sequenceStore.lastAcceptedSequence(realm));
        } catch (RealmIndexException e) {
            log.warn(WARN_INDEX_REJECTED, e.getMessage());
            return;
        }
        reconcile(index);
        // Commit acceptance only after reconciliation ran, so an interrupted
        // reconciliation is repeated for the same sequence on the next poll.
        sequenceStore.recordAcceptedSequence(realm, index.sequence());
        response.headers().firstValue("ETag").ifPresent(etag -> etags.put(INDEX_ETAG_KEY, etag));
    }

    private void reconcile(RealmIndex index) throws InterruptedException {
        val desired = new HashMap<String, String>();
        val present = new HashSet<String>();
        for (val entry : index.bundles()) {
            present.add(entry.pdpId());
            if (isValidBundleUrl(entry.url())) {
                desired.put(entry.pdpId(), entry.url());
            } else {
                // A single malformed URL must not abort the pass and strand the other
                // entries. Skip it and keep any existing binding for this pdpId.
                log.warn(WARN_INVALID_INDEX_URL, entry.pdpId(), entry.url());
            }
        }
        for (val pdpId : Set.copyOf(childLoops.keySet())) {
            val child = childLoops.get(pdpId);
            if (!present.contains(pdpId)) {
                stopChild(pdpId, child);
                emit(new ConfigurationEvent.ConfigurationRemoved(pdpId));
                log.info("Removed remote bundle for pdpId '{}' (no longer in the realm index).", pdpId);
                continue;
            }
            val targetUrl = desired.get(pdpId);
            if (targetUrl != null && !targetUrl.equals(child.url())) {
                // A signed rebinding: reset ETag and freshness state so the new
                // binding loads whatever version it points at, including a pin
                // to an older version.
                stopChild(pdpId, child);
                startChild(pdpId, targetUrl);
                log.info("Rebound remote bundle for pdpId '{}' to '{}'.", pdpId, targetUrl);
            }
        }
        for (val entry : desired.entrySet()) {
            if (!childLoops.containsKey(entry.getKey())) {
                startChild(entry.getKey(), entry.getValue());
            }
        }
    }

    private void startChild(String pdpId, String url) {
        // Children always long-poll: a holding server delivers updates instantly,
        // and a server without long-poll support degrades to plain polling.
        val thread = Thread.ofVirtual().name("sapl-bundle-fetch-" + pdpId)
                .start(() -> runFetchLoop(pdpId, URI.create(url), true));
        childLoops.put(pdpId, new ChildLoop(url, thread));
        loopThreads.add(thread);
        log.info("Started remote bundle fetch loop for pdpId '{}' at '{}'.", pdpId, url);
    }

    private void stopChild(String pdpId, ChildLoop child) throws InterruptedException {
        child.thread().interrupt();
        // Wait for the child to fully terminate before dropping its state. Because join()
        // returns only after the thread has ended, all of the child's emits precede the
        // ConfigurationRemoved that follows in reconcile, so a removed or rebound pdpId can
        // never be resurrected by a late event, and its state is cleared with no concurrent
        // writer. The child ends promptly: the interrupt unblocks the in-flight request,
        // and each iteration is otherwise bounded by the request timeout.
        child.thread().join();
        loopThreads.remove(child.thread());
        childLoops.remove(pdpId);
        etags.remove(pdpId);
        lastSignedAt.remove(pdpId);
        lastContact.remove(stripBundleExtension(pdpId));
        staleEscalated.remove(stripBundleExtension(pdpId));
        failClosedEscalated.remove(stripBundleExtension(pdpId));
    }

    private HttpRequest buildRequest(URI uri, String etagKey, String accept, boolean longPoll) {
        val builder = HttpRequest.newBuilder(uri).GET().header("Accept", accept).timeout(responseTimeout(longPoll));
        val etag    = etags.get(etagKey);
        if (etag != null) {
            builder.header("If-None-Match", etag);
        }
        val authName  = config.authHeaderName();
        val authValue = config.authHeaderValue();
        if (authName != null && authValue != null) {
            // Bind the credential's audience to the configured origin. A MULTI index
            // supplies absolute bundle URLs, and a cross-origin one must not be handed
            // the credential, just as a cross-origin redirect must not (redirects are
            // disabled for the same reason when a credential is set).
            if (!sameOrigin(uri, baseUri)) {
                log.warn(WARN_CREDENTIAL_WITHHELD_CROSS_ORIGIN, uri, baseUri);
            } else {
                try {
                    builder.header(authName, authValue);
                } catch (IllegalArgumentException e) {
                    // The rejected value is the credential. Keep the original stack trace for
                    // diagnostics, but never the original message or cause, which may carry
                    // the credential into the logs.
                    val sanitized = new IllegalArgumentException(ERROR_ILLEGAL_AUTH_HEADER.formatted(etagKey));
                    sanitized.setStackTrace(e.getStackTrace());
                    throw sanitized;
                }
            }
        }
        return builder.build();
    }

    private URI bundleUri(String pdpId) {
        return URI.create(withSeparator(config.baseUrl()) + pdpId);
    }

    private URI indexUri() {
        return URI.create(withSeparator(config.baseUrl()) + config.indexPath());
    }

    private static String withSeparator(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    // Same web origin: scheme, host, and effective port all match. Used to keep the
    // auth credential from travelling to an index-supplied cross-origin bundle URL.
    private static boolean sameOrigin(URI a, URI b) {
        return equalsIgnoreCase(a.getScheme(), b.getScheme()) && equalsIgnoreCase(a.getHost(), b.getHost())
                && effectivePort(a) == effectivePort(b);
    }

    private static boolean equalsIgnoreCase(@Nullable String a, @Nullable String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        val scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return 80;
        }
        return -1;
    }

    // A realm-index bundle URL must be an absolute http or https URL with a host. An
    // invalid one is skipped during reconciliation rather than aborting the whole pass.
    private static boolean isValidBundleUrl(String url) {
        try {
            val uri    = URI.create(url);
            val scheme = uri.getScheme();
            return uri.isAbsolute() && uri.getHost() != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Duration responseTimeout(boolean longPoll) {
        if (longPoll) {
            return config.longPollTimeout().plus(LONG_POLL_TIMEOUT_BUFFER);
        }
        return POLLING_RESPONSE_TIMEOUT;
    }

    private void handleResponse(String pdpId, HttpResponse<byte[]> response) throws IOException {
        val status = response.statusCode();
        if (status == 304) {
            if (!etags.containsKey(pdpId)) {
                // A 304 to a request that carried no If-None-Match is a protocol violation.
                // Treat it as a failure so a misbehaving server cannot mask an ongoing
                // outage by reporting a fresh contact that never delivered a bundle.
                throw new IOException(ERROR_UNEXPECTED_NOT_MODIFIED.formatted(pdpId));
            }
            recordContact(pdpId);
            log.debug("Bundle unchanged for pdpId '{}' (304 Not Modified).", pdpId);
            return;
        }
        if (status >= 200 && status < 300) {
            val bytes = response.body();
            if (bytes == null || bytes.length == 0) {
                throw new IOException(ERROR_EMPTY_RESPONSE_BODY);
            }
            recordContact(pdpId);
            val etag = response.headers().firstValue("ETag").orElse(null);
            loadBundle(pdpId, bytes, etag);
            return;
        }
        throw new IOException(ERROR_HTTP_STATUS.formatted(status, pdpId));
    }

    private void loadBundle(String pdpId, byte[] bundleBytes, @Nullable String etag) {
        val                       effectivePdpId = stripBundleExtension(pdpId);
        BundleParser.ParsedBundle parsed;
        try {
            parsed = BundleParser.parseWithMetadata(bundleBytes, effectivePdpId, config.securityPolicy());
        } catch (PDPConfigurationException | BundleSignatureException e) {
            // A fully fetched bundle that is definitively broken (bad signature or
            // malformed). Retrying the same bytes will not help, so report it and store
            // the ETag so it is not re-processed until the bundle changes. Transport
            // failures never reach here; they are retried by the fetch loop.
            log.warn("Rejected remote bundle for pdpId '{}': {}.", effectivePdpId, e.getMessage());
            emit(new ConfigurationEvent.ConfigurationError(effectivePdpId,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            if (etag != null) {
                etags.put(pdpId, etag);
            }
            return;
        }
        if (isStaleReplay(effectivePdpId, parsed.signedAt())) {
            // Do not store the ETag: the stale response must not silence the
            // fetch loop into 304s while the replay is ongoing.
            log.warn(WARN_STALE_BUNDLE_REJECTED, effectivePdpId, parsed.signedAt(), lastSignedAt.get(effectivePdpId));
            return;
        }
        emit(new ConfigurationEvent.NewConfiguration(parsed.configuration()));
        val signedAt = parsed.signedAt();
        if (signedAt != null) {
            lastSignedAt.put(effectivePdpId, signedAt);
        }
        if (etag != null) {
            etags.put(pdpId, etag);
        }
        log.info("Loaded remote bundle for pdpId '{}' ({} bytes, ETag: {}).", pdpId, bundleBytes.length, etag);
    }

    private boolean isStaleReplay(String pdpId, @Nullable Instant signedAt) {
        if (signedAt == null) {
            // Unsigned bundles carry no signing time, the guard does not apply.
            return false;
        }
        val lastSeen = lastSignedAt.get(pdpId);
        return lastSeen != null && signedAt.isBefore(lastSeen);
    }

    private void recordContact(String pdpId) {
        val key = stripBundleExtension(pdpId);
        lastContact.put(key, clock.instant());
        staleEscalated.remove(key);
        failClosedEscalated.remove(key);
    }

    private void maybeEscalateStaleness(String pdpId) {
        val key   = stripBundleExtension(pdpId);
        val since = lastContact.get(key);
        if (since == null) {
            return;
        }
        val elapsed = Duration.between(since, clock.instant());
        if (elapsed.compareTo(config.staleAfterNoContact()) >= 0 && staleEscalated.add(key)) {
            log.warn(WARN_STALE_NO_CONTACT, key, config.staleAfterNoContact());
            etags.remove(pdpId);
            emit(new ConfigurationEvent.ConfigurationError(key, STALE_REASON));
        }
        val failClosedAfter = config.failClosedAfterNoContact();
        if (failClosedAfter != null && elapsed.compareTo(failClosedAfter) >= 0 && failClosedEscalated.add(key)) {
            log.warn(WARN_EXPIRED_NO_CONTACT, key, failClosedAfter);
            etags.remove(pdpId);
            emit(new ConfigurationEvent.ConfigurationExpired(key, EXPIRED_REASON));
        }
    }

    private static String stripBundleExtension(String pdpId) {
        return pdpId.endsWith(BUNDLE_EXTENSION) ? pdpId.substring(0, pdpId.length() - BUNDLE_EXTENSION.length())
                : pdpId;
    }

    boolean httpClientTerminated() {
        return httpClient.isTerminated();
    }

    Duration getPollDelay(String pdpId, boolean longPoll) {
        if (longPoll) {
            // Floor between iterations so a server that answers immediately cannot turn
            // the long-poll loop into a hot fetch spin.
            return LONG_POLL_MIN_DELAY;
        }
        return config.pdpIdPollIntervals().getOrDefault(pdpId, config.pollInterval());
    }

    private Duration nextBackoff(Duration current) {
        val doubled = current.multipliedBy(2);
        return doubled.compareTo(config.maxBackoff()) > 0 ? config.maxBackoff() : doubled;
    }

    private static Duration jitter(Duration base) {
        val millis      = base.toMillis();
        val jitterRange = millis / 2;
        if (jitterRange == 0) {
            return base;
        }
        val jittered = millis + ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1);
        return Duration.ofMillis(Math.max(1, jittered));
    }
}
