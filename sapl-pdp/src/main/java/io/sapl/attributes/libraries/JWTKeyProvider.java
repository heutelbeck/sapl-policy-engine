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
package io.sapl.attributes.libraries;

import tools.jackson.databind.JsonNode;
import io.sapl.api.SaplVersion;
import lombok.Getter;
import lombok.experimental.StandardException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import reactor.core.publisher.Mono;

import java.io.Serial;
import java.security.Key;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Class for retrieving public keys from the JWT Authorization Server.
 */
@Slf4j
public class JWTKeyProvider {

    private static final String ERROR_JWT_KEY_CACHING_CONFIGURATION = "The provided caching configuration was not understood: ";
    private static final String ERROR_JWT_KEY_SERVER_HTTP           = "Error trying to retrieve a public key: ";

    public static final String PUBLIC_KEY_URI_KEY     = "uri";
    public static final String PUBLIC_KEY_METHOD_KEY  = "method";
    public static final String KEY_CACHING_TTL_MILLIS = "keyCachingTtlMillis";
    static final long          DEFAULT_CACHING_TTL    = 300000L;

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
    private final WebClient         webClient;
    private long                    lastTTL = DEFAULT_CACHING_TTL;

    /**
     * Creates a JWTKeyProvider.
     *
     * @param builder a WebClient builder.
     */
    public JWTKeyProvider(WebClient.Builder builder) {
        webClient    = builder.build();
        keyCache     = new ConcurrentHashMap<>();
        cachingTimes = new ConcurrentLinkedQueue<>();
    }

    /**
     * Creates a JWTKeyProvider without a WebClient. Intended for dummy/stub
     * subclasses that override {@link #provide} and never make HTTP calls.
     */
    protected JWTKeyProvider() {
        webClient    = null;
        keyCache     = new ConcurrentHashMap<>();
        cachingTimes = new ConcurrentLinkedQueue<>();
    }

    /**
     * Fetches the public key of a server.
     *
     * @param kid the key id
     * @param jPublicKeyServer the key server
     * @return the public key
     * @throws CachingException on errors
     */
    public Mono<Key> provide(String kid, JsonNode jPublicKeyServer) throws CachingException {

        final var jUri = jPublicKeyServer.get(PUBLIC_KEY_URI_KEY);
        if (null == jUri)
            return Mono.empty();

        final var jMethod = jPublicKeyServer.get(PUBLIC_KEY_METHOD_KEY);
        var       sMethod = "GET";
        if (null != jMethod && jMethod.isString())
            sMethod = jMethod.asString();

        final var sUri = jUri.asString();
        final var jTTL = jPublicKeyServer.get(KEY_CACHING_TTL_MILLIS);
        var       lTTL = DEFAULT_CACHING_TTL;
        if (null != jTTL) {
            if (jTTL.canConvertToLong()) {
                lTTL = jTTL.longValue();
            } else {
                throw new CachingException(ERROR_JWT_KEY_CACHING_CONFIGURATION + jTTL);
            }
        }

        setTtlMillis(lTTL);
        return fetchPublicKey(kid, sUri, sMethod);
    }

    /**
     * Put key into cache.
     *
     * @param kid the key id
     * @param key the key
     */
    public void cache(String kid, Key key) {

        if (isCached(kid))
            return;

        keyCache.put(kid, key);
        cachingTimes.add(new CacheEntry(kid));
    }

    /**
     * Checks if the key is in the cache.
     *
     * @param kid key id
     * @return true, if the cache contains the key with the given id.
     */
    public boolean isCached(String kid) {
        pruneCache();
        return keyCache.containsKey(kid);
    }

    /**
     * Sets the cache TTL.
     *
     * @param newTtlMillis time to live for cache entries.
     */
    public void setTtlMillis(long newTtlMillis) {
        lastTTL = newTtlMillis >= 0L ? newTtlMillis : DEFAULT_CACHING_TTL;
    }

    /**
     * Fetches public key from remote authentication server.
     *
     * @param kid ID of public key to fetch
     * @param publicKeyURI URI to request the public key
     * @param publicKeyRequestMethod HTTP request method: GET or POST
     * @return public key or empty
     */
    private Mono<Key> fetchPublicKey(String kid, String publicKeyURI, String publicKeyRequestMethod) {
        final ResponseSpec response;

        if (isCached(kid)) {
            return Mono.just(keyCache.get(kid));
        }

        if ("post".equalsIgnoreCase(publicKeyRequestMethod)) {
            response = webClient.post().uri(publicKeyURI, kid).retrieve();
        } else {
            response = webClient.get().uri(publicKeyURI, kid).retrieve();
        }

        return response.onStatus(HttpStatusCode::isError, this::handleHttpError).bodyToMono(String.class)
                .map(JWTEncodingDecodingUtils::encodedX509ToPublicKey).filter(Optional::isPresent).map(Optional::get);
    }

    private Mono<? extends Throwable> handleHttpError(ClientResponse response) {
        log.trace(ERROR_JWT_KEY_SERVER_HTTP + response.statusCode());
        return Mono.empty();
    }

    /**
     * Remove all keys from cache that are older than ttlMillis before now.
     */
    private void pruneCache() {
        final var pruneTime   = Instant.now().minusMillis(lastTTL);
        var       oldestEntry = cachingTimes.peek();
        while (null != oldestEntry && oldestEntry.wasCachedBefore(pruneTime)) {
            keyCache.remove(oldestEntry.getKeyId());
            cachingTimes.poll();
            oldestEntry = cachingTimes.peek();
        }
    }

    private static class CacheEntry {

        @Getter
        private final String keyId;

        private final Instant cachingTime;

        CacheEntry(String keyId) {
            this.keyId  = keyId;
            cachingTime = Instant.now();
        }

        boolean wasCachedBefore(Instant instant) {
            return cachingTime.isBefore(instant);
        }

    }

}
