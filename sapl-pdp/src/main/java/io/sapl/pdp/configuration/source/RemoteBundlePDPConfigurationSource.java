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
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static io.sapl.pdp.configuration.source.RemoteBundleSourceConfig.FetchMode.LONG_POLL;

/**
 * PDP configuration source that fetches {@code .saplbundle} files from a remote
 * HTTP server.
 * <p>
 * For each configured PDP ID, an independent fetch loop runs concurrently.
 * Change detection uses HTTP conditional requests (ETag / If-None-Match).
 * Bundle parsing and signature verification are delegated to
 * {@link BundleParser}. Fetched bundles are emitted as
 * {@link ConfigurationEvent.Load} to subscribers.
 * </p>
 * <h2>Fetch Loop</h2>
 * <p>
 * Each loop uses Reactor's {@link Retry#backoff(long, Duration)} for error
 * recovery (exponential backoff with jitter, reset on success via
 * {@code transientErrors}) and {@code repeatWhen} for poll interval control.
 * </p>
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe. ETags are stored in a
 * {@link ConcurrentHashMap}. All fetch loops run on Reactor schedulers.
 * </p>
 *
 * @see RemoteBundleSourceConfig
 * @see BundleParser
 */
@Slf4j
public final class RemoteBundlePDPConfigurationSource implements PDPConfigurationSource {

    private static final String BUNDLE_EXTENSION          = ".saplbundle";
    private static final String ERROR_EMPTY_RESPONSE_BODY = "Server returned 200 with empty body.";

    private static final String WARN_FETCH_FAILED = "Fetch failed for pdpId '{}' (retry #{}): {}";

    private static final int MAX_BUNDLE_RESPONSE_BYTES = 16 * 1024 * 1024;

    private static final Duration POLLING_RESPONSE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration LONG_POLL_TIMEOUT_BUFFER = Duration.ofSeconds(5);

    private final RemoteBundleSourceConfig          config;
    private final WebClient                         webClient;
    private final ConcurrentHashMap<String, String> etags         = new ConcurrentHashMap<>();
    private final Disposable.Composite              subscriptions = Disposables.composite();
    private final Set<Consumer<ConfigurationEvent>> subscribers   = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean                     activated     = new AtomicBoolean(false);
    private final AtomicBoolean                     closed        = new AtomicBoolean(false);

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

        this.webClient = buildWebClient();
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
     * Cancels all fetch loops and releases HTTP connections.
     * <p>
     * Idempotent: subsequent calls after the first have no effect.
     * </p>
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            subscriptions.dispose();
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

    private WebClient buildWebClient() {
        val responseTimeout = config.mode() == LONG_POLL ? config.longPollTimeout().plus(LONG_POLL_TIMEOUT_BUFFER)
                : POLLING_RESPONSE_TIMEOUT;

        val httpClient = HttpClient.create().responseTimeout(responseTimeout).followRedirect(config.followRedirects());

        return config.webClientBuilder().baseUrl(config.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_BUNDLE_RESPONSE_BYTES)).build();
    }

    private void startFetchLoops() {
        for (val pdpId : config.pdpIds()) {
            val subscription = createFetchLoop(pdpId).subscribe();
            subscriptions.add(subscription);
            log.info("Started remote bundle fetch loop for pdpId '{}'.", pdpId);
        }
    }

    private Mono<Void> createFetchLoop(String pdpId) {
        val retrySpec = Retry.backoff(Long.MAX_VALUE, config.firstBackoff()).maxBackoff(config.maxBackoff())
                .transientErrors(true).doBeforeRetry(signal -> log.warn(WARN_FETCH_FAILED, pdpId,
                        signal.totalRetriesInARow() + 1, signal.failure().getMessage()));

        return Mono.defer(() -> attemptFetch(pdpId)).retryWhen(retrySpec)
                .repeatWhen(companion -> companion.flatMap(tick -> Mono.delay(getPollDelay(pdpId)))).then();
    }

    private Mono<Void> attemptFetch(String pdpId) {
        return webClient.get().uri("/{pdpId}", pdpId).headers(headers -> configureRequestHeaders(pdpId, headers))
                .exchangeToMono(response -> handleResponse(pdpId, response));
    }

    private void configureRequestHeaders(String pdpId, HttpHeaders headers) {
        val etag = etags.get(pdpId);
        if (etag != null) {
            headers.set(HttpHeaders.IF_NONE_MATCH, etag);
        }
        val authName  = config.authHeaderName();
        val authValue = config.authHeaderValue();
        if (authName != null && authValue != null) {
            headers.set(authName, authValue);
        }
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    private Mono<Void> handleResponse(String pdpId, ClientResponse response) {
        val statusCode = response.statusCode().value();

        if (statusCode == HttpStatus.NOT_MODIFIED.value()) {
            log.debug("Bundle unchanged for pdpId '{}' (304 Not Modified).", pdpId);
            return response.releaseBody();
        }

        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(byte[].class)
                    .switchIfEmpty(Mono.error(new PDPConfigurationException(ERROR_EMPTY_RESPONSE_BODY)))
                    .publishOn(Schedulers.boundedElastic())
                    .doOnNext(bytes -> loadBundle(pdpId, bytes, response.headers().asHttpHeaders().getETag())).then();
        }

        return response.createError().then();
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

    private Duration getPollDelay(String pdpId) {
        if (config.mode() == LONG_POLL) {
            return Duration.ZERO;
        }
        return config.pdpIdPollIntervals().getOrDefault(pdpId, config.pollInterval());
    }

}
