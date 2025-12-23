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
package io.sapl.server.ce.security;

import com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider;
import lombok.Setter;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;
import java.util.List;
import java.util.Properties;

@Setter
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ Caching.class, JCacheCacheManager.class })
@AutoConfigureBefore(AuthenticationCacheConfig.class)
public class RateLimiterCacheManagerConfig implements BeanClassLoaderAware {
    public static final String CACHE_PROVIDER_FQN = CaffeineCachingProvider.class.getName();

    private ClassLoader beanClassLoader;

    @Bean
    @Primary
    JCacheCacheManager cacheManager(CacheManager jCacheCacheManager) {
        return new JCacheCacheManager(jCacheCacheManager);
    }

    @Bean
    CacheManager jCacheCacheManager(
            ObjectProvider<javax.cache.configuration.Configuration<?, ?>> defaultCacheConfiguration) {
        CacheManager jCacheCacheManager = createCacheManager();
        List<String> cacheNames         = List.of("buckets");

        for (String cacheName : cacheNames) {
            jCacheCacheManager.createCache(cacheName,
                    defaultCacheConfiguration.getIfAvailable(MutableConfiguration::new));
        }

        return jCacheCacheManager;
    }

    private CacheManager createCacheManager() {
        CachingProvider cachingProvider = getCachingProvider();
        return cachingProvider.getCacheManager(null, this.beanClassLoader, new Properties());
    }

    private CachingProvider getCachingProvider() {
        return Caching.getCachingProvider(RateLimiterCacheManagerConfig.CACHE_PROVIDER_FQN);
    }

}
