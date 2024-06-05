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
package io.sapl.server.ce.security.apikey;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import io.sapl.server.ce.model.clients.AuthType;
import io.sapl.server.ce.model.clients.ClientCredentialsRepository;
import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Conditional(SetupFinishedCondition.class)
public class ApiKeyService {
    private final PasswordEncoder             passwordEncoder;
    private final ClientCredentialsRepository clientCredentialsRepository;
    private final CacheManager                apiKeyCacheManager;

    @Getter
    @Value("${io.sapl.server.apiKeyHeaderName:API_KEY}")
    private String apiKeyHeaderName;

    @Cacheable(cacheManager = "apiKeyCacheManager", value = "ApiKeyCache", unless = "#result == false")
    public boolean isValidApiKey(String apiKey) throws AuthenticationException {
        // extract client key from apiKey
        var apiKeyComponents = apiKey.split("\\.");
        if (apiKeyComponents.length == 2) {
            var key = apiKeyComponents[0];
            var c   = clientCredentialsRepository.findByKey(key)
                    .orElseThrow(() -> new UsernameNotFoundException("Provided apiKey client credentials not found"));
            return c.getAuthType().equals(AuthType.APIKEY) && passwordEncoder.matches(apiKey, c.getEncodedSecret());
        } else {
            throw new AuthenticationServiceException("Invalid apiKey");
        }
    }

    public void removeFromCache(String cacheKey) {
        CaffeineCache apiKeyCache = (CaffeineCache) apiKeyCacheManager.getCache("ApiKeyCache");
        if (apiKeyCache != null) {
            var nativeCache = apiKeyCache.getNativeCache();
            for (Map.Entry<Object, Object> entry : nativeCache.asMap().entrySet()) {
                var cacheEntry = entry.getKey();
                log.info("checking entry: " + cacheEntry);
                if (cacheEntry.toString().startsWith(cacheKey + ".")) {
                    log.info("removing entry: " + cacheEntry);
                    apiKeyCache.evict(cacheEntry);
                }
            }
        }
    }
}
