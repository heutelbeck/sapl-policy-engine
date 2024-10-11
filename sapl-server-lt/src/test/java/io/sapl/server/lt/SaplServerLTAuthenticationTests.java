/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.lt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.rsocket.api.PayloadExchange;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.rsocket.metadata.CompositeMetadataCodec;
import io.rsocket.util.DefaultPayload;
import io.sapl.server.lt.apikey.ApiKeyAuthenticationException;
import io.sapl.server.lt.apikey.ApiKeyReactiveAuthenticationManager;
import io.sapl.server.lt.apikey.ApiKeyService;

class SaplServerLTAuthenticationTests {
    private static final String   API_KEY         = "sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j";
    private static final String   INVALIC_API_KEY = "invalid-adpi-key";
    private static final String   ENCODED_API_KEY = "$argon2id$v=19$m=16384,t=2,p=1$FttHTp38SkUUzUA4cA5Epg$QjzIAdvmNGP0auVlkCDpjrgr2LHeM5ul0BYLr7QKwBM";
    private final PasswordEncoder passwordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @Test
    void whenConnectingThoughtHttp_withApiKey_thenAuthenticationIsProvided() {
        final var pdpProperties                       = mock(SAPLServerLTProperties.class);
        final var cacheManager                        = mock(CacheManager.class);
        final var apiKeyReactiveAuthenticationManager = new ApiKeyReactiveAuthenticationManager();

        when(pdpProperties.getAllowedApiKeys()).thenReturn(List.of(ENCODED_API_KEY));
        final var exchange = MockServerWebExchange
                .from(MockServerHttpRequest.get("/api/pdp/decide").header("Authorization", "Bearer " + API_KEY));

        final var apiKeyService = new ApiKeyService(pdpProperties, passwordEncoder, cacheManager);
        final var result        = apiKeyService.getHttpApiKeyAuthenticationConverter().convert(exchange).block();
        assertThat(result).isNotNull();
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getDetails()).isNull();
        assertThat(result.getAuthorities()).isNotNull();
        assertThat(result.getCredentials()).isNotNull();
        assertThat(result.getPrincipal()).isNotNull();
        assertThat(result.getName()).isNotNull();

        // test ApiKeyReactiveAuthenticationManager
        assertThat(apiKeyReactiveAuthenticationManager.authenticate(result)).isNotNull();
        final var authentication = apiKeyReactiveAuthenticationManager.authenticate(result).block();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
    }

    @Test
    void whenConnectingThoughtRSocket_withApiKey_thenAuthenticationIsProvided() {
        final var pdpProperties   = mock(SAPLServerLTProperties.class);
        final var cacheManager    = mock(CacheManager.class);
        final var cache           = mock(Cache.class);
        final var payloadExchange = mock(PayloadExchange.class);
        // test with empty cache
        when(cacheManager.getCache("ApiKeyCache")).thenReturn(cache);
        when(pdpProperties.getAllowedApiKeys()).thenReturn(List.of(ENCODED_API_KEY));

        // build payload with metadata
        CompositeByteBuf metadata = ByteBufAllocator.DEFAULT.compositeBuffer();
        CompositeMetadataCodec.encodeAndAddMetadata(metadata, ByteBufAllocator.DEFAULT, "messaging/Bearer",
                Unpooled.buffer().writeBytes(API_KEY.getBytes(StandardCharsets.UTF_8)));

        final var payload = DefaultPayload
                .create(Unpooled.buffer().writeBytes(API_KEY.getBytes(StandardCharsets.UTF_8)), metadata);
        when(payloadExchange.getPayload()).thenReturn(payload);

        // execute unit test
        final var apiKeyService = new ApiKeyService(pdpProperties, passwordEncoder, cacheManager);
        final var result        = apiKeyService.getRsocketApiKeyAuthenticationConverter().convert(payloadExchange)
                .block();
        assertThat(result).isNotNull();
        assertThat(result.isAuthenticated()).isTrue();
    }

    @Test
    void whenConnectingThoughtHttp_withCachedApiKey_thenAuthenticationIsProvided() {
        final var pdpProperties = mock(SAPLServerLTProperties.class);
        final var cacheManager  = mock(CacheManager.class);
        final var cache         = mock(Cache.class);
        final var cacheEntry    = mock(Cache.ValueWrapper.class);

        // mock cache manager
        when(cacheManager.getCache("ApiKeyCache")).thenReturn(cache);
        when(cache.get(API_KEY)).thenReturn(cacheEntry);
        when(cacheEntry.get()).thenReturn(ENCODED_API_KEY);

        when(pdpProperties.getAllowedApiKeys()).thenReturn(List.of(ENCODED_API_KEY));
        final var exchange = MockServerWebExchange
                .from(MockServerHttpRequest.get("/api/pdp/decide").header("Authorization", "Bearer " + API_KEY));

        final var apiKeyService = new ApiKeyService(pdpProperties, passwordEncoder, cacheManager);
        final var result        = apiKeyService.getHttpApiKeyAuthenticationConverter().convert(exchange).block();
        assertThat(result).isNotNull();
        assertThat(result.isAuthenticated()).isTrue();
    }

    @Test
    void whenConnectingThoughtHttp_withInvalidApiKey_thenAuthenticationIsNotProvided() {
        final var pdpProperties = mock(SAPLServerLTProperties.class);
        final var cacheManager  = mock(CacheManager.class);

        when(pdpProperties.getAllowedApiKeys()).thenReturn(List.of(ENCODED_API_KEY));
        final var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/pdp/decide").header("Authorization", "Bearer " + INVALIC_API_KEY));

        final var apiKeyService = new ApiKeyService(pdpProperties, passwordEncoder, cacheManager);
        final var result        = apiKeyService.getHttpApiKeyAuthenticationConverter().convert(exchange).block();
        assertThat(result).isNull();
    }

    @Test
    void whenConnectingThoughtRsocket_withInvalidApiKey_thenAuthenticationIsNotProvided() {
        final var pdpProperties   = mock(SAPLServerLTProperties.class);
        final var cacheManager    = mock(CacheManager.class);
        final var cache           = mock(Cache.class);
        final var payloadExchange = mock(PayloadExchange.class);
        // test with empty cache
        when(cacheManager.getCache("ApiKeyCache")).thenReturn(cache);
        when(pdpProperties.getAllowedApiKeys()).thenReturn(List.of(ENCODED_API_KEY));

        // build payload with metadata
        CompositeByteBuf metadata = ByteBufAllocator.DEFAULT.compositeBuffer();
        CompositeMetadataCodec.encodeAndAddMetadata(metadata, ByteBufAllocator.DEFAULT, "messaging/unknown",
                Unpooled.buffer().writeBytes(INVALIC_API_KEY.getBytes(StandardCharsets.UTF_8)));

        final var payload = DefaultPayload
                .create(Unpooled.buffer().writeBytes(API_KEY.getBytes(StandardCharsets.UTF_8)), metadata);
        when(payloadExchange.getPayload()).thenReturn(payload);

        // execute unit test
        final var apiKeyService = new ApiKeyService(pdpProperties, passwordEncoder, cacheManager);
        final var result        = apiKeyService.getRsocketApiKeyAuthenticationConverter().convert(payloadExchange)
                .block();
        assertThat(result).isNull();
    }

    @Test
    void whenConnectingThoughtHttp_withInvalidSaplApiKey_thenExceptionIsRaised() {
        final var pdpProperties = mock(SAPLServerLTProperties.class);
        final var cacheManager  = mock(CacheManager.class);

        when(pdpProperties.getAllowedApiKeys()).thenReturn(List.of(ENCODED_API_KEY));
        final var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/pdp/decide").header("Authorization", "Bearer " + API_KEY + "XYZ"));

        final var apiKeyService = new ApiKeyService(pdpProperties, passwordEncoder, cacheManager);
        final var action        = apiKeyService.getHttpApiKeyAuthenticationConverter().convert(exchange);
        assertThatThrownBy(() -> action.block()).isInstanceOf(ApiKeyAuthenticationException.class);
    }

    @Test
    void whenConnectingThoughtRSocket_withInvalidApiKey_thenAuthenticationIsProvided() {
        final var pdpProperties         = mock(SAPLServerLTProperties.class);
        final var argon2PasswordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        final var cacheManager          = mock(CacheManager.class);
        final var cache                 = mock(Cache.class);
        final var payloadExchange       = mock(PayloadExchange.class);

        // test with empty cache
        when(cacheManager.getCache("ApiKeyCache")).thenReturn(cache);
        when(pdpProperties.getAllowedApiKeys()).thenReturn(List.of(ENCODED_API_KEY));

        // build payload with metadata
        CompositeByteBuf metadata = ByteBufAllocator.DEFAULT.compositeBuffer();
        CompositeMetadataCodec.encodeAndAddMetadata(metadata, ByteBufAllocator.DEFAULT, "messaging/Bearer",
                Unpooled.buffer().writeBytes(INVALIC_API_KEY.getBytes(StandardCharsets.UTF_8)));

        final var payload = DefaultPayload
                .create(Unpooled.buffer().writeBytes(INVALIC_API_KEY.getBytes(StandardCharsets.UTF_8)), metadata);
        when(payloadExchange.getPayload()).thenReturn(payload);

        // execute unit test
        final var apiKeyService = new ApiKeyService(pdpProperties, argon2PasswordEncoder, cacheManager);
        final var action        = apiKeyService.getRsocketApiKeyAuthenticationConverter().convert(payloadExchange);
        assertThatThrownBy(() -> action.block()).isInstanceOf(ApiKeyAuthenticationException.class);
    }
}
