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
package io.sapl.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.sapl.node.SaplNodeProperties.UserEntry;
import io.sapl.node.apikey.ApiKeyAuthenticationException;
import io.sapl.node.apikey.ApiKeyReactiveAuthenticationManager;
import io.sapl.node.apikey.ApiKeyService;
import io.sapl.node.auth.SaplAuthenticationToken;
import io.sapl.node.auth.SaplUser;
import io.sapl.node.auth.UserLookupService;
import lombok.val;

@DisplayName("SAPL Node Authentication")
class SaplNodeAuthenticationTests {

    private static final String   API_KEY         = "sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j";
    private static final String   INVALID_API_KEY = "invalid-api-key";
    private static final String   ENCODED_API_KEY = "$argon2id$v=19$m=16384,t=2,p=1$FttHTp38SkUUzUA4cA5Epg$QjzIAdvmNGP0auVlkCDpjrgr2LHeM5ul0BYLr7QKwBM";
    private static final String   USER_ID         = "test-user";
    private static final String   PDP_ID          = "test-pdp";
    private final PasswordEncoder passwordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    private CacheManager      cacheManager;
    private UserLookupService userLookupService;

    @BeforeEach
    void setUp() {
        val pdpProperties = mock(SaplNodeProperties.class);
        cacheManager = mock(CacheManager.class);

        val userEntry = new UserEntry();
        userEntry.setId(USER_ID);
        userEntry.setPdpId(PDP_ID);
        userEntry.setApiKey(ENCODED_API_KEY);

        when(pdpProperties.getUsers()).thenReturn(List.of(userEntry));
        userLookupService = new UserLookupService(pdpProperties, passwordEncoder);
    }

    @Nested
    @DisplayName("API Key Authentication")
    class ApiKeyAuthenticationTests {

        @Test
        @DisplayName("returns SaplAuthenticationToken with correct pdpId when valid API key provided")
        void whenValidApiKeyProvided_thenReturnsSaplAuthenticationTokenWithPdpId() {
            val apiKeyReactiveAuthenticationManager = new ApiKeyReactiveAuthenticationManager();
            val exchange                            = MockServerWebExchange
                    .from(MockServerHttpRequest.get("/api/pdp/decide").header("Authorization", "Bearer " + API_KEY));

            val apiKeyService = new ApiKeyService(userLookupService, cacheManager);
            val result        = apiKeyService.getHttpApiKeyAuthenticationConverter().convert(exchange).block();

            assertThat(result).isNotNull().isInstanceOf(SaplAuthenticationToken.class).satisfies(auth -> {
                assertThat(auth.isAuthenticated()).isTrue();
                assertThat(auth.getDetails()).isNull();
                assertThat(auth.getAuthorities()).isNotEmpty();
                assertThat(auth.getCredentials()).isNull();
                assertThat(auth.getName()).isEqualTo(USER_ID);
            });

            val saplAuth = (SaplAuthenticationToken) result;
            assertThat(saplAuth.getPdpId()).isEqualTo(PDP_ID);
            assertThat(saplAuth.getPrincipal()).isInstanceOf(SaplUser.class).extracting(SaplUser::id, SaplUser::pdpId)
                    .containsExactly(USER_ID, PDP_ID);

            val authentication = apiKeyReactiveAuthenticationManager.authenticate(result).block();
            assertThat(authentication).isNotNull().satisfies(auth -> assertThat(auth.isAuthenticated()).isTrue());
        }

        @Test
        @DisplayName("returns cached SaplUser when API key is in cache")
        void whenApiKeyInCache_thenReturnsCachedSaplUser() {
            val cache      = mock(Cache.class);
            val cacheEntry = mock(Cache.ValueWrapper.class);
            val cachedUser = new SaplUser(USER_ID, PDP_ID);

            when(cacheManager.getCache("ApiKeyCache")).thenReturn(cache);
            when(cache.get(API_KEY)).thenReturn(cacheEntry);
            when(cacheEntry.get()).thenReturn(cachedUser);

            val exchange = MockServerWebExchange
                    .from(MockServerHttpRequest.get("/api/pdp/decide").header("Authorization", "Bearer " + API_KEY));

            val apiKeyService = new ApiKeyService(userLookupService, cacheManager);
            val result        = apiKeyService.getHttpApiKeyAuthenticationConverter().convert(exchange).block();

            assertThat(result).isNotNull().isInstanceOf(SaplAuthenticationToken.class)
                    .satisfies(auth -> assertThat(auth.isAuthenticated()).isTrue());

            val saplAuth = (SaplAuthenticationToken) result;
            assertThat(saplAuth.getPdpId()).isEqualTo(PDP_ID);
        }

        @Test
        @DisplayName("returns empty when non-SAPL API key provided")
        void whenNonSaplApiKeyProvided_thenReturnsEmpty() {
            val exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/pdp/decide").header("Authorization", "Bearer " + INVALID_API_KEY));

            val apiKeyService = new ApiKeyService(userLookupService, cacheManager);
            val result        = apiKeyService.getHttpApiKeyAuthenticationConverter().convert(exchange).block();

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("throws exception when SAPL-prefixed API key is invalid")
        void whenInvalidSaplApiKeyProvided_thenThrowsException() {
            val exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/pdp/decide").header("Authorization", "Bearer " + API_KEY + "XYZ"));

            val apiKeyService = new ApiKeyService(userLookupService, cacheManager);
            val action        = apiKeyService.getHttpApiKeyAuthenticationConverter().convert(exchange);

            assertThatThrownBy(action::block).isInstanceOf(ApiKeyAuthenticationException.class);
        }

    }

}
