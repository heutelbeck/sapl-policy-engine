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

import io.sapl.pdp.configuration.bundle.BundleParser;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static io.sapl.pdp.configuration.source.RemoteBundleSourceConfig.FetchMode.LONG_POLL;

/**
 * PDP configuration source that fetches {@code .saplbundle} files from a
 * remote HTTP server using the JDK's {@link HttpClient} on virtual threads.
 * <p>
 * For each configured PDP ID, an independent fetch loop runs on its own
 * virtual thread. Change detection uses HTTP conditional requests
 * (ETag / If-None-Match). Bundle parsing and signature verification are
 * delegated to {@link BundleParser}. Fetched bundles are emitted as
 * {@link ConfigurationEvent.Load} to subscribers.
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

    private static final String BUNDLE_EXTENSION          = ".saplbundle";
    private static final String ERROR_EMPTY_RESPONSE_BODY = "Server returned 200 with empty body.";
    private static final String ERROR_HTTP_STATUS         = "Server returned HTTP %d for pdpId '%s'.";
    private static final String ERROR_ILLEGAL_AUTH_HEADER = "Configured auth header for pdpId '%s' contains characters that are illegal in an HTTP header.";

    private static final String WARN_FETCH_FAILED                      = "Fetch failed for pdpId '{}' (retry #{}): {}";
    private static final String WARN_REDIRECTS_DISABLED_FOR_CREDENTIAL = "Disabling redirect following for bundle source '{}' because a custom auth header is configured. A redirect would replay the credential to a cross-origin target. Point at the final URL to keep redirects.";

    private static final int MAX_BUNDLE_RESPONSE_BYTES = 16 * 1024 * 1024;

    private static final Duration CONNECT_TIMEOUT          = Duration.ofSeconds(10);
    private static final Duration POLLING_RESPONSE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration LONG_POLL_TIMEOUT_BUFFER = Duration.ofSeconds(5);
    private static final Duration LONG_POLL_MIN_DELAY      = Duration.ofSeconds(1);

    private final RemoteBundleSourceConfig          config;
    private final HttpClient                        httpClient;
    private final ConcurrentHashMap<String, String> etags       = new ConcurrentHashMap<>();
    private final List<Thread>                      loopThreads = new CopyOnWriteArrayList<>();
    private final Set<Consumer<ConfigurationEvent>> subscribers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean                     activated   = new AtomicBoolean(false);
    private final AtomicBoolean                     closed      = new AtomicBoolean(false);

    /**
     * Creates a remote bundle source.
     *
     * @param config the remote bundle configuration
     * @throws NullPointerException if {@code config} is null
     * @throws io.sapl.pdp.configuration.bundle.BundleSignatureException if
     * the security policy is invalid
     */
    public RemoteBundlePDPConfigurationSource(@NonNull RemoteBundleSourceConfig config) {
        this.config = Objects.requireNonNull(config, "config");
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
            subscriber.accept(event);
        }
    }

    private void startFetchLoops() {
        for (val pdpId : config.pdpIds()) {
            val thread = Thread.ofVirtual().name("sapl-bundle-fetch-" + pdpId).start(() -> runFetchLoop(pdpId));
            loopThreads.add(thread);
            log.info("Started remote bundle fetch loop for pdpId '{}'.", pdpId);
        }
    }

    private void runFetchLoop(String pdpId) {
        Duration backoff    = config.firstBackoff();
        long     retryCount = 0;
        while (!closed.get() && !Thread.currentThread().isInterrupted()) {
            try {
                attemptFetch(pdpId);
                backoff    = config.firstBackoff();
                retryCount = 0;
                val pollDelay = getPollDelay(pdpId);
                if (!pollDelay.isZero()) {
                    Thread.sleep(pollDelay);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                retryCount++;
                log.warn(WARN_FETCH_FAILED, pdpId, retryCount, e.getMessage());
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

    private void attemptFetch(String pdpId) throws IOException, InterruptedException {
        val request  = buildRequest(pdpId);
        val response = httpClient.send(request, info -> new BoundedByteArrayBodySubscriber(MAX_BUNDLE_RESPONSE_BYTES));
        handleResponse(pdpId, response);
    }

    private HttpRequest buildRequest(String pdpId) {
        val baseUrl   = config.baseUrl();
        val separator = baseUrl.endsWith("/") ? "" : "/";
        val uri       = URI.create(baseUrl + separator + pdpId);
        val builder   = HttpRequest.newBuilder(uri).GET().header("Accept", "application/octet-stream")
                .timeout(responseTimeout());
        val etag      = etags.get(pdpId);
        if (etag != null) {
            builder.header("If-None-Match", etag);
        }
        val authName  = config.authHeaderName();
        val authValue = config.authHeaderValue();
        if (authName != null && authValue != null) {
            try {
                builder.header(authName, authValue);
            } catch (IllegalArgumentException e) {
                // The rejected value is the credential. Never propagate it into a message that
                // reaches the logs.
                throw new IllegalArgumentException(ERROR_ILLEGAL_AUTH_HEADER.formatted(pdpId));
            }
        }
        return builder.build();
    }

    private Duration responseTimeout() {
        if (config.mode() == LONG_POLL) {
            return config.longPollTimeout().plus(LONG_POLL_TIMEOUT_BUFFER);
        }
        return POLLING_RESPONSE_TIMEOUT;
    }

    private void handleResponse(String pdpId, HttpResponse<byte[]> response) throws IOException {
        val status = response.statusCode();
        if (status == 304) {
            log.debug("Bundle unchanged for pdpId '{}' (304 Not Modified).", pdpId);
            return;
        }
        if (status >= 200 && status < 300) {
            val bytes = response.body();
            if (bytes == null || bytes.length == 0) {
                throw new IOException(ERROR_EMPTY_RESPONSE_BODY);
            }
            val etag = response.headers().firstValue("ETag").orElse(null);
            loadBundle(pdpId, bytes, etag);
            return;
        }
        throw new IOException(ERROR_HTTP_STATUS.formatted(status, pdpId));
    }

    private void loadBundle(String pdpId, byte[] bundleBytes, @Nullable String etag) {
        val effectivePdpId = stripBundleExtension(pdpId);
        val configuration  = BundleParser.parse(bundleBytes, effectivePdpId, config.securityPolicy());
        emit(new ConfigurationEvent.Load(configuration, true));
        if (etag != null) {
            etags.put(pdpId, etag);
        }
        log.info("Loaded remote bundle for pdpId '{}' ({} bytes, ETag: {}).", pdpId, bundleBytes.length, etag);
    }

    private static String stripBundleExtension(String pdpId) {
        return pdpId.endsWith(BUNDLE_EXTENSION) ? pdpId.substring(0, pdpId.length() - BUNDLE_EXTENSION.length())
                : pdpId;
    }

    boolean httpClientTerminated() {
        return httpClient.isTerminated();
    }

    Duration getPollDelay(String pdpId) {
        if (config.mode() == LONG_POLL) {
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
