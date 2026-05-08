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
package io.sapl.attributes.libraries.vnext;

import io.sapl.api.SaplVersion;
import lombok.Getter;
import lombok.experimental.StandardException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.Serial;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Key;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Retrieves public keys from a remote JWT authorization server with
 * a TTL-bounded cache. Synchronous: a call to {@link #provide(String,
 * JsonNode)} blocks until the key is available or the request fails.
 */
@Slf4j
public class JWTKeyProvider {

    private static final String ERROR_JWT_KEY_CACHING_CONFIGURATION = "The provided caching configuration was not understood: ";
    private static final String ERROR_JWT_KEY_SERVER_HTTP           = "Error trying to retrieve a public key: HTTP {}";
    private static final String ERROR_JWT_KEY_SERVER_IO             = "I/O error retrieving a public key: {}";

    public static final String    PUBLIC_KEY_URI_KEY     = "uri";
    public static final String    PUBLIC_KEY_METHOD_KEY  = "method";
    public static final String    KEY_CACHING_TTL_MILLIS = "keyCachingTtlMillis";
    static final long             DEFAULT_CACHING_TTL    = 300_000L;
    private static final Duration HTTP_REQUEST_TIMEOUT   = Duration.ofSeconds(10L);

    /**
     * Exception indicating a caching error.
     */
    @StandardException
    public static class CachingException extends Exception {
        @Serial
        private static final long serialVersionUID = SaplVersion.VERSION_UID;
    }

    private final Map<String, Key>  keyCache;
    private final Queue<CacheEntry> cachingTimes;
    private final HttpClient        httpClient;
    private final Clock             clock;
    private long                    lastTtl = DEFAULT_CACHING_TTL;

    /**
     * Creates a JWTKeyProvider backed by the given {@link HttpClient}
     * and reading the current time from {@code clock} for cache TTL
     * decisions.
     */
    public JWTKeyProvider(HttpClient httpClient, Clock clock) {
        this.httpClient   = httpClient;
        this.clock        = clock;
        this.keyCache     = new ConcurrentHashMap<>();
        this.cachingTimes = new ConcurrentLinkedQueue<>();
    }

    /**
     * Creates a JWTKeyProvider with no HTTP client. Intended for stub
     * subclasses in tests that override {@link #provide} and never
     * make HTTP calls.
     */
    protected JWTKeyProvider(Clock clock) {
        this.httpClient   = null;
        this.clock        = clock;
        this.keyCache     = new ConcurrentHashMap<>();
        this.cachingTimes = new ConcurrentLinkedQueue<>();
    }

    /**
     * Fetches the public key for the given key id. Returns
     * {@link Optional#empty()} if the key cannot be retrieved.
     *
     * @param kid the key id
     * @param jPublicKeyServer the key-server configuration node
     * @return the public key, or empty if not available
     * @throws CachingException if the caching configuration is invalid
     */
    public Optional<Key> provide(String kid, JsonNode jPublicKeyServer) throws CachingException {
        val jUri = jPublicKeyServer.get(PUBLIC_KEY_URI_KEY);
        if (null == jUri) {
            return Optional.empty();
        }

        val jMethod = jPublicKeyServer.get(PUBLIC_KEY_METHOD_KEY);
        var sMethod = "GET";
        if (null != jMethod && jMethod.isString()) {
            sMethod = jMethod.asString();
        }

        val sUri = jUri.asString();
        val jTtl = jPublicKeyServer.get(KEY_CACHING_TTL_MILLIS);
        var lTtl = DEFAULT_CACHING_TTL;
        if (null != jTtl) {
            if (jTtl.canConvertToLong()) {
                lTtl = jTtl.longValue();
            } else {
                throw new CachingException(ERROR_JWT_KEY_CACHING_CONFIGURATION + jTtl);
            }
        }

        setTtlMillis(lTtl);
        return fetchPublicKey(kid, sUri, sMethod);
    }

    /**
     * Inserts the key into the cache, ignoring duplicates.
     */
    public void cache(String kid, Key key) {
        if (isCached(kid)) {
            return;
        }
        keyCache.put(kid, key);
        cachingTimes.add(new CacheEntry(kid, clock.instant()));
    }

    /**
     * Returns {@code true} if the cache currently holds a key under
     * the given id (after pruning stale entries).
     */
    public boolean isCached(String kid) {
        pruneCache();
        return keyCache.containsKey(kid);
    }

    /**
     * Sets the cache TTL in milliseconds. Negative values are treated
     * as {@link #DEFAULT_CACHING_TTL}.
     */
    public void setTtlMillis(long newTtlMillis) {
        lastTtl = newTtlMillis >= 0L ? newTtlMillis : DEFAULT_CACHING_TTL;
    }

    private Optional<Key> fetchPublicKey(String kid, String publicKeyUri, String publicKeyRequestMethod) {
        if (isCached(kid)) {
            return Optional.of(keyCache.get(kid));
        }

        val resolvedUri = publicKeyUri.replace("{id}", kid);
        val builder     = HttpRequest.newBuilder().uri(URI.create(resolvedUri)).timeout(HTTP_REQUEST_TIMEOUT);
        val request     = "post".equalsIgnoreCase(publicKeyRequestMethod)
                ? builder.POST(HttpRequest.BodyPublishers.noBody()).build()
                : builder.GET().build();

        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.trace(ERROR_JWT_KEY_SERVER_HTTP, response.statusCode());
                return Optional.empty();
            }
            return JWTEncodingDecodingUtils.encodedX509ToPublicKey(response.body());
        } catch (IOException e) {
            log.trace(ERROR_JWT_KEY_SERVER_IO, e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /**
     * Removes cache entries older than the configured TTL.
     */
    private void pruneCache() {
        val pruneTime   = clock.instant().minusMillis(lastTtl);
        var oldestEntry = cachingTimes.peek();
        while (null != oldestEntry && oldestEntry.wasCachedBefore(pruneTime)) {
            keyCache.remove(oldestEntry.getKeyId());
            cachingTimes.poll();
            oldestEntry = cachingTimes.peek();
        }
    }

    private static final class CacheEntry {

        @Getter
        private final String keyId;

        private final Instant cachingTime;

        CacheEntry(String keyId, Instant cachingTime) {
            this.keyId       = keyId;
            this.cachingTime = cachingTime;
        }

        boolean wasCachedBefore(Instant instant) {
            return cachingTime.isBefore(instant);
        }
    }
}
