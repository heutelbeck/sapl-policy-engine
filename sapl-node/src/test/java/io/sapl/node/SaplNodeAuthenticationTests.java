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

import io.sapl.node.apikey.ApiKeyAuthenticationException;
import io.sapl.node.apikey.ApiKeyReactiveAuthenticationManager;
import io.sapl.node.apikey.ApiKeyService;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SaplNodeAuthenticationTests {
    private static final String   API_KEY         = "sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j";
    private static final String   INVALIC_API_KEY = "invalid-adpi-key";
    private static final String   ENCODED_API_KEY = "$argon2id$v=19$m=16384,t=2,p=1$FttHTp38SkUUzUA4cA5Epg$QjzIAdvmNGP0auVlkCDpjrgr2LHeM5ul0BYLr7QKwBM";
    private final PasswordEncoder passwordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @Test
    void whenConnectingThoughtHttp_withApiKey_thenAuthenticationIsProvided() {
        val pdpProperties                       = mock(SaplNodeProperties.class);
        val cacheManager                        = mock(CacheManager.class);
        val apiKeyReactiveAuthenticationManager = new ApiKeyReactiveAuthenticationManager();

        when(pdpProperties.getAllowedApiKeys()).thenReturn(List.of(ENCODED_API_KEY));
        val exchange = MockServerWebExchange
                .from(MockServerHttpRequest.get("/api/pdp/decide").header("Authorization", "Bearer " + API_KEY));

        val apiKeyService = new ApiKeyService(pdpProperties, passwordEncoder, cacheManager);
        val result        = apiKeyService.getHttpApiKeyAuthenticationConverter().convert(exchange).block();
        assertThat(result).isNotNull();
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getDetails()).isNull();
        assertThat(result.getAuthorities()).isNotNull();
        assertThat(result.getCredentials()).isNotNull();
        assertThat(result.getPrincipal()).isNotNull();
        assertThat(result.getName()).isNotNull();

        // test ApiKeyReactiveAuthenticationManager
        assertThat(apiKeyReactiveAuthenticationManager.authenticate(result)).isNotNull();
        val authentication = apiKeyReactiveAuthenticationManager.authenticate(result).block();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
    }

    @Test
    void whenConnectingThoughtHttp_withCachedApiKey_thenAuthenticationIsProvided() {
        val pdpProperties = mock(SaplNodeProperties.class);
        val cacheManager  = mock(CacheManager.class);
        val cache         = mock(Cache.class);
        val cacheEntry    = mock(Cache.ValueWrapper.class);

        // mock cache manager
        when(cacheManager.getCache("ApiKeyCache")).thenReturn(cache);
        when(cache.get(API_KEY)).thenReturn(cacheEntry);
        when(cacheEntry.get()).thenReturn(ENCODED_API_KEY);

        when(pdpProperties.getAllowedApiKeys()).thenReturn(List.of(ENCODED_API_KEY));
        val exchange = MockServerWebExchange
                .from(MockServerHttpRequest.get("/api/pdp/decide").header("Authorization", "Bearer " + API_KEY));

        val apiKeyService = new ApiKeyService(pdpProperties, passwordEncoder, cacheManager);
        val result        = apiKeyService.getHttpApiKeyAuthenticationConverter().convert(exchange).block();
        assertThat(result).isNotNull();
        assertThat(result.isAuthenticated()).isTrue();
    }

    @Test
    void whenConnectingThoughtHttp_withInvalidApiKey_thenAuthenticationIsNotProvided() {
        val pdpProperties = mock(SaplNodeProperties.class);
        val cacheManager  = mock(CacheManager.class);

        when(pdpProperties.getAllowedApiKeys()).thenReturn(List.of(ENCODED_API_KEY));
        val exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/pdp/decide").header("Authorization", "Bearer " + INVALIC_API_KEY));

        val apiKeyService = new ApiKeyService(pdpProperties, passwordEncoder, cacheManager);
        val result        = apiKeyService.getHttpApiKeyAuthenticationConverter().convert(exchange).block();
        assertThat(result).isNull();
    }

    @Test
    void whenConnectingThoughtHttp_withInvalidSaplApiKey_thenExceptionIsRaised() {
        val pdpProperties = mock(SaplNodeProperties.class);
        val cacheManager  = mock(CacheManager.class);

        when(pdpProperties.getAllowedApiKeys()).thenReturn(List.of(ENCODED_API_KEY));
        val exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/pdp/decide").header("Authorization", "Bearer " + API_KEY + "XYZ"));

        val apiKeyService = new ApiKeyService(pdpProperties, passwordEncoder, cacheManager);
        val action        = apiKeyService.getHttpApiKeyAuthenticationConverter().convert(exchange);
        assertThatThrownBy(action::block).isInstanceOf(ApiKeyAuthenticationException.class);
    }

}
