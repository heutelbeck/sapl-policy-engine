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

import io.sapl.api.SaplVersion;
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
import java.util.regex.Pattern;

/**
 * Retrieves public keys from a remote JWT authorization server with
 * a TTL-bounded cache. Synchronous: a call to {@link #provide(String,
 * JsonNode)} blocks until the key is available or the request fails.
 * <p>
 * Cache entries are scoped to the (resolved key-server URI, kid) pair
 * so a key fetched for one key-server configuration is never served
 * under a different configuration that happens to reference the same
 * kid, and each entry expires by the TTL of the configuration that
 * cached it.
 */
@Slf4j
public class JWTKeyProvider {

    private static final String ERROR_JWT_KEY_CACHING_CONFIGURATION  = "The provided caching configuration was not understood: ";
    private static final String WARN_JWT_KEY_SERVER_HTTP             = "JWT public-key server returned HTTP {} for kid '{}' at '{}'. Token signatures cannot be verified.";
    private static final String WARN_JWT_KEY_SERVER_INSECURE_ALLOWED = "Fetching JWT trust anchor for kid '{}' from '{}' over an insecure scheme because 'allowInsecureHttp' is enabled in the key-server configuration. A network attacker on this hop can forge tokens this PIP will trust. Do not use in production.";
    private static final String WARN_JWT_KEY_SERVER_INSECURE_SCHEME  = "JWT public-key server URI '{}' for kid '{}' does not use https; refusing to fetch the trust anchor over an unauthenticated channel. Token signatures cannot be verified. Enable 'allowInsecureHttp' in the key-server configuration only for local development.";
    private static final String WARN_JWT_KEY_SERVER_IO               = "JWT public-key fetch failed for kid '{}' at '{}': {}. Token signatures cannot be verified.";
    private static final String WARN_JWT_KID_REJECTED                = "JWT public-key fetch rejected: the key id contains characters outside the permitted set [A-Za-z0-9._-]. Token signatures cannot be verified.";

    // The kid comes from an unverified JWT header and is interpolated into the
    // operator's key-server URI template. Restricting it to base64url-safe
    // characters prevents path, query, authority, or CRLF injection.
    private static final Pattern  SAFE_KEY_ID            = Pattern.compile("[A-Za-z0-9._-]+");
    public static final String    PUBLIC_KEY_URI_KEY     = "uri";
    public static final String    PUBLIC_KEY_METHOD_KEY  = "method";
    public static final String    KEY_CACHING_TTL_MILLIS = "keyCachingTtlMillis";
    public static final String    ALLOW_INSECURE_HTTP    = "allowInsecureHttp";
    static final long             DEFAULT_CACHING_TTL    = 300_000L;
    private static final Duration HTTP_REQUEST_TIMEOUT   = Duration.ofSeconds(10L);
    private static final String   HTTPS_SCHEME           = "https";

    /**
     * Exception indicating a caching error.
     */
    @StandardException
    public static class CachingException extends Exception {
        @Serial
        private static final long serialVersionUID = SaplVersion.VERSION_UID;
    }

    private final Map<CacheKey, Key> keyCache;
    private final Queue<CacheEntry>  cachingTimes;
    private final HttpClient         httpClient;
    private final Clock              clock;

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

        val jTtl = jPublicKeyServer.get(KEY_CACHING_TTL_MILLIS);
        if (null != jTtl && !jTtl.canConvertToLong()) {
            throw new CachingException(ERROR_JWT_KEY_CACHING_CONFIGURATION + jTtl);
        }

        val jInsecure         = jPublicKeyServer.get(ALLOW_INSECURE_HTTP);
        val allowInsecureHttp = null != jInsecure && jInsecure.isBoolean() && jInsecure.booleanValue();

        return fetchPublicKey(kid, jUri.asString(), sMethod, allowInsecureHttp);
    }

    /**
     * Resolves the key-server URI for the given key id by interpolating
     * the {@code {kid}} placeholder in the configured URI template. Used
     * as the URI component of the cache key so cached keys are scoped to
     * the configuration that fetched them.
     *
     * @param jPublicKeyServer the key-server configuration node
     * @param kid the key id
     * @return the resolved URI, or {@code null} if no URI is configured
     */
    public static String resolveKeyServerUri(JsonNode jPublicKeyServer, String kid) {
        val jUri = jPublicKeyServer.get(PUBLIC_KEY_URI_KEY);
        if (null == jUri) {
            return null;
        }
        return jUri.asString().replace("{kid}", kid);
    }

    /**
     * Reads the configured cache TTL in milliseconds, falling back to
     * {@link #DEFAULT_CACHING_TTL} when the value is absent.
     *
     * @param jPublicKeyServer the key-server configuration node
     * @return the TTL in milliseconds
     * @throws CachingException if the configured TTL is not a number
     */
    public static long cachingTtlMillis(JsonNode jPublicKeyServer) throws CachingException {
        val jTtl = jPublicKeyServer.get(KEY_CACHING_TTL_MILLIS);
        if (null == jTtl) {
            return DEFAULT_CACHING_TTL;
        }
        if (!jTtl.canConvertToLong()) {
            throw new CachingException(ERROR_JWT_KEY_CACHING_CONFIGURATION + jTtl);
        }
        return jTtl.longValue();
    }

    /**
     * Inserts the key into the cache under the (resolved key-server URI,
     * kid) pair, ignoring duplicates. The entry expires after the given
     * TTL in milliseconds, independently of any other entry.
     *
     * @param keyServerUri the resolved key-server URI the key was fetched from
     * @param kid the key id
     * @param key the public key to cache
     * @param ttlMillis the lifetime of this entry in milliseconds
     */
    public void cache(String keyServerUri, String kid, Key key, long ttlMillis) {
        if (isCached(keyServerUri, kid)) {
            return;
        }
        val cacheKey = new CacheKey(keyServerUri, kid);
        keyCache.put(cacheKey, key);
        cachingTimes.add(new CacheEntry(cacheKey, clock.instant(), normalizeTtl(ttlMillis)));
    }

    /**
     * Returns {@code true} if the cache currently holds a key under the
     * given (resolved key-server URI, kid) pair (after pruning expired
     * entries).
     *
     * @param keyServerUri the resolved key-server URI
     * @param kid the key id
     * @return whether a non-expired key is cached for this pair
     */
    public boolean isCached(String keyServerUri, String kid) {
        pruneCache();
        return keyCache.containsKey(new CacheKey(keyServerUri, kid));
    }

    private static long normalizeTtl(long ttlMillis) {
        return ttlMillis >= 0L ? ttlMillis : DEFAULT_CACHING_TTL;
    }

    private Optional<Key> fetchPublicKey(String kid, String publicKeyUri, String publicKeyRequestMethod,
            boolean allowInsecureHttp) {
        if (!SAFE_KEY_ID.matcher(kid).matches()) {
            log.warn(WARN_JWT_KID_REJECTED);
            return Optional.empty();
        }

        val resolvedUri = publicKeyUri.replace("{kid}", kid);
        if (isCached(resolvedUri, kid)) {
            return Optional.of(keyCache.get(new CacheKey(resolvedUri, kid)));
        }

        if (!isSecureScheme(resolvedUri)) {
            if (!allowInsecureHttp) {
                log.warn(WARN_JWT_KEY_SERVER_INSECURE_SCHEME, resolvedUri, kid);
                return Optional.empty();
            }
            log.warn(WARN_JWT_KEY_SERVER_INSECURE_ALLOWED, kid, resolvedUri);
        }

        val               builder = HttpRequest.newBuilder().uri(URI.create(resolvedUri)).timeout(HTTP_REQUEST_TIMEOUT);
        final HttpRequest request;
        if ("post".equalsIgnoreCase(publicKeyRequestMethod)) {
            request = builder.POST(HttpRequest.BodyPublishers.noBody()).build();
        } else {
            request = builder.GET().build();
        }

        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn(WARN_JWT_KEY_SERVER_HTTP, response.statusCode(), kid, resolvedUri);
                return Optional.empty();
            }
            val key = JWTEncodingDecodingUtils.encodedX509ToPublicKey(response.body());
            if (key.isEmpty()) {
                log.warn("JWT public-key response for kid '{}' from '{}' could not be decoded as an X509 public key.",
                        kid, resolvedUri);
            }
            return key;
        } catch (IOException e) {
            log.warn(WARN_JWT_KEY_SERVER_IO, kid, resolvedUri, e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static boolean isSecureScheme(String uri) {
        try {
            return HTTPS_SCHEME.equalsIgnoreCase(URI.create(uri).getScheme());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Removes every cache entry whose own TTL has elapsed. Entries may
     * carry different TTLs, so the whole set is scanned rather than only
     * the oldest-inserted head.
     */
    private void pruneCache() {
        val now = clock.instant();
        cachingTimes.removeIf(entry -> {
            if (entry.hasExpired(now)) {
                keyCache.remove(entry.cacheKey());
                return true;
            }
            return false;
        });
    }

    private record CacheKey(String keyServerUri, String kid) {}

    private record CacheEntry(CacheKey cacheKey, Instant cachingTime, long ttlMillis) {
        boolean hasExpired(Instant now) {
            return cachingTime.plusMillis(ttlMillis).isBefore(now);
        }
    }
}
