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
package io.sapl.server.ce.security;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
public class AuthenticationCacheConfig {

    @Value("${io.sapl.server.apiKeyCaching.enabled:#{False}}")
    private boolean apiKeyCachingEnabled;

    @Value("${io.sapl.server.apiKeyCaching.expire:#{300}}")
    private Integer apiKeyCachingExpireSeconds;

    @Value("${io.sapl.server.apiKeyCaching.maxSize:#{300}}")
    private Integer apiKeyCachingMaxSize;

    @Bean
    Caffeine<Object, Object> caffeineConfig() {
        return Caffeine.newBuilder().expireAfterAccess(apiKeyCachingExpireSeconds, TimeUnit.SECONDS).initialCapacity(10)
                .maximumSize(apiKeyCachingMaxSize);
    }

    @Bean
    CacheManager apiKeyCacheManager(Caffeine<Object, Object> caffeine) {
        if (apiKeyCachingEnabled) {
            CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager("ApiKeyCache");
            caffeineCacheManager.setCaffeine(caffeine);
            return caffeineCacheManager;
        } else {
            return new NoOpCacheManager();
        }
    }

}
