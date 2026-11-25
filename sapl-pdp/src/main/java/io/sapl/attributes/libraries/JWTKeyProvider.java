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

import com.heutelbeck.rustregex4j.IsMatch;
import com.heutelbeck.rustregex4j.Regex;
import io.sapl.api.SaplVersion;
import io.sapl.attributes.http.BlockingWebClient;
import io.sapl.attributes.http.HttpUris;
import io.sapl.attributes.tls.PemTrust;
import io.sapl.compiler.util.LinearRegex;
import lombok.experimental.StandardException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.Serial;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Retrieves public keys from a remote JWT authorization server with
 * a TTL-bounded cache. Synchronous: a call to {@link #provide(String,
 * JsonNode)} blocks until the key is available or the request fails.
 * <p>
 * Cache entries are scoped to the resolved key-server URI, kid and TLS trust
 * so a key fetched for one key-server configuration is never served
 * under a different configuration that happens to reference the same
 * kid, and each entry expires by the TTL of the configuration that
 * cached it.
 */
@Slf4j
public class JWTKeyProvider {

    private static final String ERROR_JWT_KEY_CACHING_CONFIGURATION  = "The provided caching configuration was not understood: ";
    private static final String ERROR_TLS_TRUST_UNAVAILABLE          = "The configured JWT key provider does not support custom TLS trust.";
    private static final String WARN_INVALID_KEY_URI                 = "JWT key-server URI must specify a fixed HTTP or HTTPS authority.";
    private static final String WARN_JWT_KEY_SERVER_HTTP             = "JWT public-key server returned HTTP {} for kid '{}' at '{}'. Token signatures cannot be verified.";
    private static final String WARN_JWT_KEY_SERVER_INSECURE_ALLOWED = "Fetching JWT trust anchor for kid '{}' from '{}' over an insecure scheme because 'allowInsecureHttp' is enabled in the key-server configuration. A network attacker on this hop can forge tokens this PIP will trust. Do not use in production.";
    private static final String WARN_JWT_KEY_SERVER_INSECURE_SCHEME  = "JWT public-key server URI '{}' for kid '{}' does not use https. Refusing to fetch the trust anchor over an unauthenticated channel. Token signatures cannot be verified.";
    private static final String WARN_JWT_KEY_SERVER_IO               = "JWT public-key fetch failed for kid '{}' at '{}': {}. Token signatures cannot be verified.";
    private static final String WARN_JWT_KEY_SERVER_OVERSIZE         = "JWT public-key response for kid '{}' from '{}' exceeded the {}-byte limit and was rejected. Token signatures cannot be verified.";
    private static final String WARN_JWT_KID_REJECTED                = "JWT public-key fetch rejected: the key id contains characters outside the permitted set [A-Za-z0-9._-]. Token signatures cannot be verified.";

    // The kid comes from an unverified JWT header and is interpolated into the
    // operator's key-server URI template. Restricting it to base64url-safe
    // characters prevents path, query, authority, or CRLF injection.
    private static final Regex    SAFE_KEY_ID            = LinearRegex.compileWholeTextOrRefuse("[A-Za-z0-9._-]+");
    public static final String    PUBLIC_KEY_URI_KEY     = "uri";
    public static final String    PUBLIC_KEY_METHOD_KEY  = "method";
    public static final String    KEY_CACHING_TTL_MILLIS = "keyCachingTtlMillis";
    public static final String    ALLOW_INSECURE_HTTP    = "allowInsecureHttp";
    static final long             DEFAULT_CACHING_TTL    = 300_000L;
    private static final Duration HTTP_REQUEST_TIMEOUT   = Duration.ofSeconds(10L);
    private static final String   HTTPS_SCHEME           = "https";
    // The response body is attacker-controllable on the key-server hop, so cap it
    // to keep an oversized reply from exhausting the heap.
    private static final int MAX_KEY_RESPONSE_BYTES = 256 * 1024;

    /**
     * Exception indicating a caching error.
     */
    @StandardException
    public static class CachingException extends Exception {
        @Serial
        private static final long serialVersionUID = SaplVersion.VERSION_UID;
    }

    private final ConcurrentMap<CacheKey, CacheEntry> keyCache;
    private final @Nullable BlockingWebClient         webClient;
    private final @Nullable PemTrust                  trust;
    private final Clock                               clock;

    /**
     * Creates a JWTKeyProvider backed by the given {@link HttpClient}
     * and reading the current time from {@code clock} for cache TTL
     * decisions.
     *
     * @param httpClient transport for requests using platform trust
     * @param clock clock for key expiry
     */
    public JWTKeyProvider(HttpClient httpClient, Clock clock) {
        this(new BlockingWebClient(JsonMapper.shared(), httpClient), clock, new ConcurrentHashMap<>(), null);
    }

    /**
     * Creates a JWTKeyProvider with no HTTP client. Intended for stub
     * subclasses in tests that override {@link #provide} and never
     * make HTTP calls.
     *
     * @param clock clock for key expiry
     */
    protected JWTKeyProvider(Clock clock) {
        this(null, clock, new ConcurrentHashMap<>(), null);
    }

    private JWTKeyProvider(@Nullable BlockingWebClient webClient,
            Clock clock,
            ConcurrentMap<CacheKey, CacheEntry> keyCache,
            @Nullable PemTrust trust) {
        this.webClient = webClient;
        this.clock     = clock;
        this.keyCache  = keyCache;
        this.trust     = trust;
    }

    JWTKeyProvider withTrust(@Nullable PemTrust selected) {
        if (selected == null) {
            return this;
        }
        if (webClient == null) {
            throw new IllegalArgumentException(ERROR_TLS_TRUST_UNAVAILABLE);
        }
        return new JWTKeyProvider(webClient.withTrust("jwt.publicKeyServer", selected), clock, keyCache, selected);
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
        // A key id the engine cannot search is not known to be safe.
        if (kid == null || !(SAFE_KEY_ID.isMatch(kid) instanceof IsMatch(var safe) && safe)) {
            log.warn(WARN_JWT_KID_REJECTED);
            return Optional.empty();
        }
        val resolvedUri = resolveKeyServerUri(jPublicKeyServer, kid);
        if (resolvedUri == null) {
            log.warn(WARN_INVALID_KEY_URI);
            return Optional.empty();
        }

        val jMethod = jPublicKeyServer.get(PUBLIC_KEY_METHOD_KEY);
        var sMethod = "GET";
        if (null != jMethod && jMethod.isString()) {
            sMethod = jMethod.asString();
        }

        cachingTtlMillis(jPublicKeyServer);
        val jInsecure         = jPublicKeyServer.get(ALLOW_INSECURE_HTTP);
        val allowInsecureHttp = null != jInsecure && jInsecure.isBoolean() && jInsecure.booleanValue();

        return fetchPublicKey(kid, resolvedUri, sMethod, allowInsecureHttp);
    }

    /**
     * Resolves the key-server URI for the given key id by interpolating
     * the {@code {kid}} placeholder in the configured URI template. Used
     * as the URI component of the cache key so cached keys are scoped to
     * the configuration that fetched them.
     *
     * @param jPublicKeyServer the key-server configuration node
     * @param kid the key id
     * @return the resolved URI, or {@code null} if no valid fixed authority is configured
     */
    public static String resolveKeyServerUri(JsonNode jPublicKeyServer, String kid) {
        val uri = jPublicKeyServer.get(PUBLIC_KEY_URI_KEY);
        if (uri == null || !uri.isString() || kid == null) {
            return null;
        }
        val template = uri.asString();
        // An encoded placeholder parses in paths and queries, but must never select the authority.
        try {
            val fixed = HttpUris.create(template.replace("{kid}", "%7Bkid%7D"));
            if (fixed.getHost() == null || fixed.getRawAuthority().contains("%7Bkid%7D")
                    || !("http".equalsIgnoreCase(fixed.getScheme())
                            || HTTPS_SCHEME.equalsIgnoreCase(fixed.getScheme()))) {
                return null;
            }
            return template.replace("{kid}", kid);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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
        pruneCache();
        keyCache.putIfAbsent(new CacheKey(keyServerUri, kid, trust),
                new CacheEntry(key, clock.instant(), normalizeTtl(ttlMillis)));
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
        return keyCache.containsKey(new CacheKey(keyServerUri, kid, trust));
    }

    private static long normalizeTtl(long ttlMillis) {
        return ttlMillis >= 0L ? ttlMillis : DEFAULT_CACHING_TTL;
    }

    private Optional<Key> fetchPublicKey(String kid, String resolvedUri, String publicKeyRequestMethod,
            boolean allowInsecureHttp) {
        val destination = HttpUris.create(resolvedUri);
        val secure      = HTTPS_SCHEME.equalsIgnoreCase(destination.getScheme());
        if (!secure && (!allowInsecureHttp || trust != null)) {
            log.warn(WARN_JWT_KEY_SERVER_INSECURE_SCHEME, resolvedUri, kid);
            return Optional.empty();
        }

        pruneCache();
        val cached = keyCache.get(new CacheKey(resolvedUri, kid, trust));
        if (null != cached) {
            return Optional.of(cached.key());
        }

        if (!secure) {
            log.warn(WARN_JWT_KEY_SERVER_INSECURE_ALLOWED, kid, resolvedUri);
        }

        val               builder = HttpRequest.newBuilder().uri(destination).timeout(HTTP_REQUEST_TIMEOUT);
        final HttpRequest request;
        if ("post".equalsIgnoreCase(publicKeyRequestMethod)) {
            request = builder.POST(HttpRequest.BodyPublishers.noBody()).build();
        } else {
            request = builder.GET().build();
        }

        if (webClient == null) {
            return Optional.empty();
        }
        try {
            val          response = webClient.send(request);
            final byte[] body;
            try (val in = response.body()) {
                if (response.statusCode() >= 400) {
                    log.warn(WARN_JWT_KEY_SERVER_HTTP, response.statusCode(), kid, resolvedUri);
                    return Optional.empty();
                }
                body = in.readNBytes(MAX_KEY_RESPONSE_BYTES + 1);
            }
            if (body.length > MAX_KEY_RESPONSE_BYTES) {
                log.warn(WARN_JWT_KEY_SERVER_OVERSIZE, kid, resolvedUri, MAX_KEY_RESPONSE_BYTES);
                return Optional.empty();
            }
            val key = JWTEncodingDecodingUtils.encodedX509ToPublicKey(new String(body, StandardCharsets.UTF_8));
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

    private void pruneCache() {
        val now = clock.instant();
        keyCache.values().removeIf(entry -> entry.hasExpired(now));
    }

    private record CacheKey(String keyServerUri, String kid, @Nullable PemTrust trust) {}

    private record CacheEntry(Key key, Instant cachingTime, long ttlMillis) {
        boolean hasExpired(Instant now) {
            return cachingTime.plusMillis(ttlMillis).isBefore(now);
        }
    }
}
