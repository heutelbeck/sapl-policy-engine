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

import io.sapl.node.SaplNodeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.cache.CacheManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final String ERROR_API_KEY_NOT_AUTHORIZED = "ApiKey not authorized";

    private final SaplNodeProperties pdpProperties;
    private final PasswordEncoder    passwordEncoder;
    static final String              HEADER            = "Authorization";
    static final String              HEADER_PREFIX     = "Bearer ";
    static final String              SAPL_TOKEN_PREFIX = "sapl_";
    private final CacheManager       cacheManager;

    /**
     * Lookup authentication token in cache.
     *
     * @param apiKey api key
     */
    private Mono<Authentication> checkApiKey(final String apiKey) {
        val cache = cacheManager.getCache("ApiKeyCache");
        // get authentication from cache if possible
        if (cache != null) {
            val cacheEntry = cache.get(apiKey);
            if (cacheEntry != null) {
                return Mono.just(new ApiKeyAuthenticationToken((String) cacheEntry.get()));
            }
        }

        // validate key and get authentication
        for (var encodedApiKey : pdpProperties.getAllowedApiKeys()) {
            log.debug("checking ApiKey against encoded ApiKey: {}", encodedApiKey);
            if (passwordEncoder.matches(apiKey, encodedApiKey)) {
                if (cache != null) {
                    cache.put(apiKey, encodedApiKey);
                }
                return Mono.just(new ApiKeyAuthenticationToken(encodedApiKey));
            }
        }

        return Mono.error(() -> new ApiKeyAuthenticationException(ERROR_API_KEY_NOT_AUTHORIZED));
    }

    public static Optional<String> getApiKeyToken(ServerWebExchange exchange) {
        val authorization = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (authorization != null && authorization.startsWith(HEADER_PREFIX + SAPL_TOKEN_PREFIX)) {
            return Optional.of(authorization.substring(HEADER_PREFIX.length()));
        }
        return Optional.empty();
    }

    public ServerAuthenticationConverter getHttpApiKeyAuthenticationConverter() {
        return exchange -> {
            val apiKeyToken = getApiKeyToken(exchange);
            if (apiKeyToken.isPresent()) {
                return checkApiKey(apiKeyToken.get());
            } else {
                return Mono.empty();
            }
        };
    }

}
