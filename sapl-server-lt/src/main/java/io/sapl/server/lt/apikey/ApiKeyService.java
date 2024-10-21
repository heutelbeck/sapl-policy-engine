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
package io.sapl.server.lt.apikey;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.cache.CacheManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.rsocket.authentication.PayloadExchangeAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import io.netty.buffer.ByteBuf;
import io.rsocket.metadata.CompositeMetadata;
import io.sapl.server.lt.SAPLServerLTProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {
    private final SAPLServerLTProperties pdpProperties;
    private final PasswordEncoder        passwordEncoder;
    static final String                  HEADER                     = "Authorization";
    static final String                  HEADER_PREFIX              = "Bearer ";
    static final String                  SAPL_TOKEN_PREFIX          = "sapl_";
    private static final String          RSOCKET_METADATA_MIME_TPYE = "messaging/Bearer";
    private final CacheManager           cacheManager;

    /**
     * Lookup authentication token in cache.
     *
     * @param apiKey api key
     */
    private Mono<Authentication> checkApiKey(final String apiKey) {
        final var cache = cacheManager.getCache("ApiKeyCache");
        // get authentication from cache of possible
        if (cache != null) {
            final var cacheEntry = cache.get(apiKey);
            if (cacheEntry != null) {
                return Mono.just(new ApiKeyAuthenticationToken((String) cacheEntry.get()));
            }
        }

        // validate key and get authentication
        for (var encodedApiKey : pdpProperties.getAllowedApiKeys()) {
            log.debug("checking ApiKey against encoded ApiKey: " + encodedApiKey);
            if (passwordEncoder.matches(apiKey, encodedApiKey)) {
                if (cache != null) {
                    cache.put(apiKey, encodedApiKey);
                }
                return Mono.just(new ApiKeyAuthenticationToken(encodedApiKey));
            }
        }

        return Mono.error(() -> new ApiKeyAuthenticationException("ApiKey not authorized"));
    }

    public static Optional<String> getApiKeyToken(ServerWebExchange exchange) {
        final var authorization = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (authorization != null && authorization.startsWith(HEADER_PREFIX + SAPL_TOKEN_PREFIX)) {
            return Optional.of(authorization.substring(HEADER_PREFIX.length()));
        }
        return Optional.empty();
    }

    public ServerAuthenticationConverter getHttpApiKeyAuthenticationConverter() {
        return exchange -> {
            final var apiKeyToken = getApiKeyToken(exchange);
            if (apiKeyToken.isPresent()) {
                return checkApiKey(apiKeyToken.get());
            } else {
                return Mono.empty();
            }
        };
    }

    public PayloadExchangeAuthenticationConverter getRsocketApiKeyAuthenticationConverter() {
        return exchange -> {
            ByteBuf           metadata          = exchange.getPayload().metadata();
            CompositeMetadata compositeMetadata = new CompositeMetadata(metadata, false);
            for (CompositeMetadata.Entry entry : compositeMetadata) {
                if (RSOCKET_METADATA_MIME_TPYE.equalsIgnoreCase(entry.getMimeType())) {
                    String apikey = entry.getContent().toString(StandardCharsets.UTF_8);
                    return checkApiKey(apikey);
                }
            }
            return Mono.empty();
        };

    }

}
