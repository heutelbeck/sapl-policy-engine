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
package io.sapl.node.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import io.sapl.node.SaplNodeProperties.UserEntry;
import io.sapl.node.auth.SaplAuthenticationToken;
import io.sapl.node.auth.SaplUser;
import io.sapl.node.auth.UserLookupService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.val;

/**
 * Specifications for {@link ApiKeyService}.
 * <p>
 * Covers two domain responsibilities: the header-parse boundary that
 * decides whether a request carries a SAPL API key (and so must NOT
 * route OAuth2 Bearer or Basic credentials through this path), and the
 * converter contract for hit, miss, and cache-hit cases.
 */
@DisplayName("ApiKeyService")
@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTests {

    private static final String VALID_KEY     = "sapl_kid123_secretpart";
    private static final String BEARER_VALID  = "Bearer " + VALID_KEY;
    private static final String AUTHORIZATION = "Authorization";

    @Mock
    private UserLookupService userLookupService;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @Mock
    private HttpServletRequest request;

    @Nested
    @DisplayName("getApiKeyToken (header parse boundary)")
    class GetApiKeyTokenTests {

        @Test
        @DisplayName("Bearer with sapl_ prefix yields the prefix-stripped key")
        void whenBearerWithSaplPrefixThenTokenExtracted() {
            when(request.getHeader(AUTHORIZATION)).thenReturn(BEARER_VALID);

            assertThat(ApiKeyService.getApiKeyToken(request)).contains(VALID_KEY);
        }

        @Test
        @DisplayName("Bearer without sapl_ prefix (e.g. an OAuth2 JWT) is not recognised as an API key")
        void whenBearerWithoutSaplPrefixThenEmpty() {
            when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer eyJhbGciOiJSUzI1NiJ9.signed.jwt");

            assertThat(ApiKeyService.getApiKeyToken(request)).isEmpty();
        }

        @Test
        @DisplayName("Basic credentials are not recognised as an API key")
        void whenBasicCredentialThenEmpty() {
            when(request.getHeader(AUTHORIZATION)).thenReturn("Basic dXNlcjpwYXNz");

            assertThat(ApiKeyService.getApiKeyToken(request)).isEmpty();
        }

        @Test
        @DisplayName("missing Authorization header yields no token (no NPE)")
        void whenNoAuthorizationHeaderThenEmpty() {
            when(request.getHeader(AUTHORIZATION)).thenReturn(null);

            assertThat(ApiKeyService.getApiKeyToken(request)).isEmpty();
        }
    }

    @Nested
    @DisplayName("converter (lookup, cache, reject)")
    class ConverterTests {

        @Test
        @DisplayName("request without an API key returns null so the filter chain does not authenticate this request")
        void whenNoApiKeyOnRequestThenConverterReturnsNull() {
            when(request.getHeader(AUTHORIZATION)).thenReturn(null);
            val service = new ApiKeyService(userLookupService, cacheManager);

            assertThat(service.getHttpApiKeyAuthenticationConverter().convert(request)).isNull();
            verify(userLookupService, never()).findByApiKey(any());
        }

        @Test
        @DisplayName("valid API key with matching user yields an authenticated token bound to that user")
        void whenValidKeyMatchesUserThenAuthenticatedTokenBoundToUser() {
            when(request.getHeader(AUTHORIZATION)).thenReturn(BEARER_VALID);
            when(cacheManager.getCache(ApiKeyService.API_KEY_CACHE)).thenReturn(cache);
            when(cache.get(VALID_KEY)).thenReturn(null);
            val userEntry = userEntryWith("alice", "tenant-a");
            val saplUser  = new SaplUser("alice", "tenant-a");
            when(userLookupService.findByApiKey(VALID_KEY)).thenReturn(Optional.of(userEntry));
            when(userLookupService.toSaplUser(userEntry)).thenReturn(saplUser);
            val service = new ApiKeyService(userLookupService, cacheManager);

            val authentication = service.getHttpApiKeyAuthenticationConverter().convert(request);

            assertThat(authentication).isInstanceOfSatisfying(SaplAuthenticationToken.class,
                    t -> assertThat(t.getPrincipal()).isEqualTo(saplUser));
            verify(cache).put(VALID_KEY, saplUser);
        }

        @Test
        @DisplayName("unknown API key is rejected with ApiKeyAuthenticationException")
        void whenUnknownKeyThenApiKeyAuthenticationException() {
            when(request.getHeader(AUTHORIZATION)).thenReturn(BEARER_VALID);
            when(cacheManager.getCache(ApiKeyService.API_KEY_CACHE)).thenReturn(cache);
            when(cache.get(VALID_KEY)).thenReturn(null);
            when(userLookupService.findByApiKey(VALID_KEY)).thenReturn(Optional.empty());
            val service   = new ApiKeyService(userLookupService, cacheManager);
            val converter = service.getHttpApiKeyAuthenticationConverter();

            assertThatThrownBy(() -> converter.convert(request)).isInstanceOf(ApiKeyAuthenticationException.class);
        }

        @Test
        @DisplayName("cached SaplUser bypasses the user lookup")
        void whenKeyAlreadyCachedThenUserLookupServiceNotCalled() {
            when(request.getHeader(AUTHORIZATION)).thenReturn(BEARER_VALID);
            when(cacheManager.getCache(ApiKeyService.API_KEY_CACHE)).thenReturn(cache);
            val cachedUser = new SaplUser("bob", "tenant-b");
            val cacheValue = new SimpleValueWrapper(cachedUser);
            when(cache.get(VALID_KEY)).thenReturn(cacheValue);
            val service = new ApiKeyService(userLookupService, cacheManager);

            val authentication = service.getHttpApiKeyAuthenticationConverter().convert(request);

            assertThat(authentication).isInstanceOfSatisfying(SaplAuthenticationToken.class,
                    t -> assertThat(t.getPrincipal()).isEqualTo(cachedUser));
            verify(userLookupService, never()).findByApiKey(any());
        }

        @Test
        @DisplayName("when no API_KEY_CACHE is configured the service still falls through to the lookup")
        void whenCacheManagerHasNoApiKeyCacheThenLookupStillPerformed() {
            when(request.getHeader(AUTHORIZATION)).thenReturn(BEARER_VALID);
            when(cacheManager.getCache(ApiKeyService.API_KEY_CACHE)).thenReturn(null);
            val userEntry = userEntryWith("carol", "tenant-c");
            val saplUser  = new SaplUser("carol", "tenant-c");
            when(userLookupService.findByApiKey(VALID_KEY)).thenReturn(Optional.of(userEntry));
            when(userLookupService.toSaplUser(userEntry)).thenReturn(saplUser);
            val service = new ApiKeyService(userLookupService, cacheManager);

            val authentication = service.getHttpApiKeyAuthenticationConverter().convert(request);

            assertThat(authentication).isInstanceOfSatisfying(SaplAuthenticationToken.class,
                    t -> assertThat(t.getPrincipal()).isEqualTo(saplUser));
            verify(userLookupService, times(1)).findByApiKey(VALID_KEY);
        }

        @Test
        @DisplayName("cache entry whose value is not a SaplUser is treated as a miss and the real lookup runs")
        void whenCacheEntryIsNotSaplUserThenFallbackToLookup() {
            when(request.getHeader(AUTHORIZATION)).thenReturn(BEARER_VALID);
            when(cacheManager.getCache(ApiKeyService.API_KEY_CACHE)).thenReturn(cache);
            when(cache.get(VALID_KEY)).thenReturn(new SimpleValueWrapper("not-a-sapl-user"));
            val userEntry = userEntryWith("dave", "tenant-d");
            val saplUser  = new SaplUser("dave", "tenant-d");
            when(userLookupService.findByApiKey(VALID_KEY)).thenReturn(Optional.of(userEntry));
            when(userLookupService.toSaplUser(userEntry)).thenReturn(saplUser);
            val service = new ApiKeyService(userLookupService, cacheManager);

            val authentication = service.getHttpApiKeyAuthenticationConverter().convert(request);

            assertThat(authentication).isInstanceOfSatisfying(SaplAuthenticationToken.class,
                    t -> assertThat(t.getPrincipal()).isEqualTo(saplUser));
            verify(cache).put(eq(VALID_KEY), eq(saplUser));
        }
    }

    private static UserEntry userEntryWith(String id, String pdpId) {
        val userEntry = new UserEntry();
        userEntry.setId(id);
        userEntry.setPdpId(pdpId);
        return userEntry;
    }

    private static final class SimpleValueWrapper implements Cache.ValueWrapper {

        private final Object value;

        SimpleValueWrapper(Object value) {
            this.value = value;
        }

        @Override
        public Object get() {
            return value;
        }
    }
}
