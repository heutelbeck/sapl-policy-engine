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

import io.netty.buffer.ByteBuf;
import io.rsocket.metadata.CompositeMetadata;
import io.sapl.server.lt.SAPLServerLTProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.rsocket.authentication.PayloadExchangeAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {
    private final SAPLServerLTProperties pdpProperties;
    private final PasswordEncoder        passwordEncoder;
    private final String                 rsocketApiKeyMimeTypeValue = String
            .valueOf(MimeType.valueOf("messaging/Bearer"));
    private final CacheManager           cacheManager;

    /**
     * Lookup authentication token in cache.
     *
     * @param apiKey api key
     */
    private Mono<Authentication> checkApiKey(final String apiKey) {
        var cache = cacheManager.getCache("ApiKeyCache");
        if (cache != null) {
            var cacheEntry = cache.get(apiKey);
            if (cacheEntry != null) {
                return Mono.just(new ApiKeyAuthenticationToken((String) cacheEntry.get()));
            } else {
                for (var encodedApiKey : pdpProperties.getAllowedApiKeys()) {
                    log.debug("checking ApiKey against encoded ApiKey: " + encodedApiKey);
                    if (passwordEncoder.matches(apiKey, encodedApiKey)) {
                        cache.put(apiKey, encodedApiKey);
                        return Mono.just(new ApiKeyAuthenticationToken(encodedApiKey));
                    }
                }
            }
        }
        return Mono.error(() -> new ApiKeyAuthenticationException("ApiKey not authorized"));
    }

    public ServerAuthenticationConverter getHttpApiKeyAuthenticationConverter() {
        return exchange -> {
            var authorization = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (authorization != null && authorization.startsWith("Bearer sapl_")) {
                var apiKeyToken = authorization.replaceFirst("^Bearer ", "");
                return checkApiKey(apiKeyToken);
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
                if (rsocketApiKeyMimeTypeValue.equals(entry.getMimeType())) {
                    String apikey = entry.getContent().toString(StandardCharsets.UTF_8);
                    return checkApiKey(apikey);
                }
            }
            return Mono.empty();
        };

    }

}
