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

import io.sapl.api.test.stream.MutableClock;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Key;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JWTKeyProvider key cache is scoped per key-server configuration and is https-by-default")
class JWTKeyProviderCacheScopingTests {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Instant    NOW    = Instant.parse("2026-01-01T00:00:00Z");

    private static final String KID = "k1";

    private static final Key KEY_A = mock(Key.class);
    private static final Key KEY_B = mock(Key.class);

    private static final String URI_A = "https://idp-a/keys/{kid}";
    private static final String URI_B = "https://idp-b/keys/{kid}";

    @Nested
    @DisplayName("composite (key-server URI, kid) cache scoping")
    class CacheScoping {

        @Test
        @DisplayName("a key cached for one key server is not served under a different key server with a colliding kid")
        void whenKidCollidesAcrossKeyServersThenKeysAreIsolated() {
            val httpClient = mock(HttpClient.class);
            val provider   = new JWTKeyProvider(httpClient, new MutableClock(NOW));

            provider.cache(URI_A.replace("{kid}", KID), KID, KEY_A, 300_000L);

            assertThat(provider.isCached(URI_A.replace("{kid}", KID), KID)).isTrue();
            assertThat(provider.isCached(URI_B.replace("{kid}", KID), KID)).isFalse();
        }

        @Test
        @DisplayName("a cache miss under a foreign key-server URI triggers a fresh fetch instead of reusing the colliding key")
        void whenKidCollidesThenForeignConfigStillFetches() throws Exception {
            val httpClient = mock(HttpClient.class);
            val provider   = new JWTKeyProvider(httpClient, new MutableClock(NOW));

            @SuppressWarnings("unchecked")
            val response = (HttpResponse<String>) mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(404);
            doReturn(response).when(httpClient).send(any(HttpRequest.class), any());

            provider.cache(URI_A.replace("{kid}", KID), KID, KEY_A, 300_000L);

            val configB = MAPPER.readTree("{\"uri\":\"" + URI_B + "\"}");
            val result  = provider.provide(KID, configB);

            assertThat(result).isEmpty();
            verify(httpClient).send(any(HttpRequest.class), any());
        }
    }

    @Nested
    @DisplayName("per-entry TTL expiry")
    class PerEntryTtl {

        @Test
        @DisplayName("each cache entry expires by its own TTL, not a globally shared last TTL")
        void whenEntriesHaveDifferentTtlThenEachExpiresIndependently() {
            val clock    = new MutableClock(NOW);
            val provider = new JWTKeyProvider(mock(HttpClient.class), clock);

            val uriShort = URI_A.replace("{kid}", KID);
            val uriLong  = URI_B.replace("{kid}", KID);

            provider.cache(uriShort, KID, KEY_A, 1_000L);
            provider.cache(uriLong, KID, KEY_B, 600_000L);

            clock.setInstant(NOW.plusMillis(2_000L));

            assertThat(provider.isCached(uriShort, KID)).isFalse();
            assertThat(provider.isCached(uriLong, KID)).isTrue();
        }
    }

    @Nested
    @DisplayName("concurrent cache eviction during lookup")
    class ConcurrentEvictionRace {

        @Test
        @DisplayName("a cache entry evicted between the cached-check and the read does not throw, it falls back to a fresh fetch")
        void whenEntryIsEvictedBetweenCheckAndReadThenNoNullPointerAndFreshFetch() throws Exception {
            val httpClient = mock(HttpClient.class);

            @SuppressWarnings("unchecked")
            val response = (HttpResponse<String>) mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(404);
            doReturn(response).when(httpClient).send(any(HttpRequest.class), any());

            val provider = new JWTKeyProvider(httpClient, new MutableClock(NOW)) {
                @Override
                public boolean isCached(String keyServerUri, String kid) {
                    // Models a concurrent pruneCache() that removes the entry
                    // in the window between observing it as cached and reading
                    // it back: the cached-check reports a hit, but the entry is
                    // already gone by the time the value is read. The lookup
                    // must survive this race rather than dereferencing null.
                    return true;
                }
            };

            val config = MAPPER.readTree("{\"uri\":\"" + URI_A + "\"}");
            val result = provider.provide(KID, config);

            assertThat(result).isEmpty();
            verify(httpClient).send(any(HttpRequest.class), any());
        }
    }

    @Nested
    @DisplayName("https-by-default with explicit named opt-in")
    class SchemeEnforcement {

        @Test
        @DisplayName("a non-https key-server URI is rejected by default and no request is made")
        void whenKeyServerUriIsHttpThenRejectedAndNoFetch() throws Exception {
            val httpClient = mock(HttpClient.class);
            val provider   = new JWTKeyProvider(httpClient, new MutableClock(NOW));

            val config = MAPPER.readTree("{\"uri\":\"http://idp-a/keys/{kid}\"}");
            val result = provider.provide(KID, config);

            assertThat(result).isEmpty();
            verify(httpClient, never()).send(any(HttpRequest.class), any());
        }

        @Test
        @DisplayName("a non-https key-server URI is honored when allowInsecureHttp is explicitly enabled")
        void whenAllowInsecureHttpIsTrueThenHttpIsFetched() throws Exception {
            val httpClient = mock(HttpClient.class);
            val provider   = new JWTKeyProvider(httpClient, new MutableClock(NOW));

            @SuppressWarnings("unchecked")
            val response = (HttpResponse<String>) mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(404);
            doReturn(response).when(httpClient).send(any(HttpRequest.class), any());

            val config = MAPPER.readTree("{\"uri\":\"http://idp-a/keys/{kid}\",\"allowInsecureHttp\":true}");
            val result = provider.provide(KID, config);

            assertThat(result).isEmpty();
            verify(httpClient).send(any(HttpRequest.class), any());
        }
    }
}
