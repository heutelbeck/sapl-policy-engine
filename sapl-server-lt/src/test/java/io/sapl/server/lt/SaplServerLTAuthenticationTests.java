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

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.rsocket.metadata.CompositeMetadataCodec;
import io.rsocket.util.DefaultPayload;
import io.sapl.server.lt.apikey.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.rsocket.api.PayloadExchange;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SaplServerLTAuthenticationTests {

    @Test
    void whenConnectingThoughtHttp_withApiKey_thenAuthenticationIsProvided(){
        var apiKey = "sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j";
        var encodedApiKey = "$argon2id$v=19$m=16384,t=2,p=1$FttHTp38SkUUzUA4cA5Epg$QjzIAdvmNGP0auVlkCDpjrgr2LHeM5ul0BYLr7QKwBM";
        var pdpProperties = mock(SAPLServerLTProperties.class);
        var passwordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        var cacheManager = mock(CacheManager.class);

        when(pdpProperties.getAllowedApiKeys()).thenReturn(List.of(encodedApiKey));
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest
                        .get("/api/pdp/decide")
                        .header("Authorization", "Bearer " + apiKey));

        var apiKeyService = new ApiKeyService(pdpProperties, passwordEncoder, cacheManager);
        var result = apiKeyService
                        .getHttpApiKeyAuthenticationConverter()
                        .convert(exchange)
                        .block();
        assertThat(result).isNotNull();
        assertThat(result.isAuthenticated()).isTrue();
    }


    @Test
    void whenConnectingThoughtRSocket_withApiKey_thenAuthenticationIsProvided(){
        var apiKey = "sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j";
        var encodedApiKey = "$argon2id$v=19$m=16384,t=2,p=1$FttHTp38SkUUzUA4cA5Epg$QjzIAdvmNGP0auVlkCDpjrgr2LHeM5ul0BYLr7QKwBM";
        var pdpProperties = mock(SAPLServerLTProperties.class);
        var passwordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        var cacheManager = mock(CacheManager.class);
        var payloadExchange = mock(PayloadExchange.class);
        when(pdpProperties.getAllowedApiKeys()).thenReturn(List.of(encodedApiKey));

        // build payload with metadata
        CompositeByteBuf metadata = ByteBufAllocator.DEFAULT.compositeBuffer();
        CompositeMetadataCodec.encodeAndAddMetadata(metadata, ByteBufAllocator.DEFAULT,
                "messaging/Bearer", Unpooled.buffer().writeBytes(apiKey.getBytes()));

        var payload = DefaultPayload.create(Unpooled.buffer().writeBytes(apiKey.getBytes()), metadata);
        when(payloadExchange.getPayload()).thenReturn(payload);

        // execute unit test
        var apiKeyService = new ApiKeyService(pdpProperties, passwordEncoder, cacheManager);
        var result = apiKeyService
                .getRsocketApiKeyAuthenticationConverter()
                .convert(payloadExchange)
                .block();
        assertThat(result).isNotNull();
        assertThat(result.isAuthenticated()).isTrue();
    }
}
