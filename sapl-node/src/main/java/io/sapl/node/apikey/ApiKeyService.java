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

import java.util.Optional;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.stereotype.Service;

import io.sapl.node.auth.SaplAuthenticationToken;
import io.sapl.node.auth.SaplUser;
import io.sapl.node.auth.UserLookupService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Service for API key authentication on the servlet stack.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    static final String HEADER            = "Authorization";
    static final String HEADER_PREFIX     = "Bearer ";
    static final String SAPL_TOKEN_PREFIX = "sapl_";
    static final String API_KEY_CACHE     = "ApiKeyCache";

    private final UserLookupService userLookupService;
    private final CacheManager      cacheManager;

    private static final String ERROR_API_KEY_NOT_AUTHORIZED = "ApiKey not authorized.";

    private Authentication checkApiKey(String apiKey) {
        val cache = cacheManager.getCache(API_KEY_CACHE);
        if (cache != null) {
            val cachedUser = getCachedUser(cache, apiKey);
            if (cachedUser != null) {
                return new SaplAuthenticationToken(cachedUser);
            }
        }
        val userEntryOpt = userLookupService.findByApiKey(apiKey);
        if (userEntryOpt.isPresent()) {
            val saplUser = userLookupService.toSaplUser(userEntryOpt.get());
            if (cache != null) {
                cache.put(apiKey, saplUser);
            }
            return new SaplAuthenticationToken(saplUser);
        }
        throw new ApiKeyAuthenticationException(ERROR_API_KEY_NOT_AUTHORIZED);
    }

    private SaplUser getCachedUser(Cache cache, String apiKey) {
        val cacheEntry = cache.get(apiKey);
        if (cacheEntry != null && cacheEntry.get() instanceof SaplUser saplUser) {
            return saplUser;
        }
        return null;
    }

    /**
     * Extracts the API key token from the request if present.
     *
     * @param request the servlet request
     * @return the API key if present, empty otherwise
     */
    public static Optional<String> getApiKeyToken(HttpServletRequest request) {
        val authorization = request.getHeader(HEADER);
        if (authorization != null && authorization.startsWith(HEADER_PREFIX + SAPL_TOKEN_PREFIX)) {
            return Optional.of(authorization.substring(HEADER_PREFIX.length()));
        }
        return Optional.empty();
    }

    /**
     * Returns the authentication converter for API key authentication. The
     * converter inspects the request, returns an authenticated token on a
     * valid API key, or {@code null} when no SAPL API key is present.
     *
     * @return the servlet authentication converter
     */
    public AuthenticationConverter getHttpApiKeyAuthenticationConverter() {
        return request -> {
            val apiKeyToken = getApiKeyToken(request);
            if (apiKeyToken.isEmpty()) {
                return null;
            }
            return checkApiKey(apiKeyToken.get());
        };
    }
}
