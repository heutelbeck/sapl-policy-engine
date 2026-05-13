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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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

    static final String API_KEY_CACHE     = "ApiKeyCache";
    static final String HEADER            = "Authorization";
    static final String HEADER_PREFIX     = "Bearer ";
    static final String SAPL_TOKEN_PREFIX = "sapl_";

    private static final String ERROR_API_KEY_NOT_AUTHORIZED = "ApiKey not authorized.";

    private final UserLookupService userLookupService;
    private final CacheManager      cacheManager;

    private Authentication checkApiKey(String apiKey) {
        val cache    = cacheManager.getCache(API_KEY_CACHE);
        val cacheKey = cache != null ? sha256(apiKey) : null;
        if (cache != null) {
            val cachedUser = getCachedUser(cache, cacheKey);
            if (cachedUser != null) {
                return new SaplAuthenticationToken(cachedUser);
            }
        }
        val userEntryOpt = userLookupService.findByApiKey(apiKey);
        if (userEntryOpt.isPresent()) {
            val saplUser = userLookupService.toSaplUser(userEntryOpt.get());
            if (cache != null) {
                cache.put(cacheKey, saplUser);
            }
            return new SaplAuthenticationToken(saplUser);
        }
        log.info("API key authentication failed: no matching user");
        throw new ApiKeyAuthenticationException(ERROR_API_KEY_NOT_AUTHORIZED);
    }

    private SaplUser getCachedUser(Cache cache, String cacheKey) {
        val cacheEntry = cache.get(cacheKey);
        if (cacheEntry != null && cacheEntry.get() instanceof SaplUser saplUser) {
            return saplUser;
        }
        return null;
    }

    // SHA-256 of the raw key as cache key so heap dumps don't expose
    // the plaintext credential. Mirrors CachingHttpAuthHandler.
    private static String sha256(String apiKey) {
        try {
            val digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(apiKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
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
