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
package io.sapl.spring.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.spring.serialization.SaplReactiveJacksonModule;
import io.sapl.spring.serialization.SaplServletJacksonModule;

/**
 * Auto-configuration that registers Jackson modules for SAPL type
 * serialization.
 * <p>
 * Spring Boot automatically discovers JacksonModule beans and registers them
 * with all ObjectMapper instances, including user-provided ones.
 */
@AutoConfiguration
public class ObjectMapperAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SaplJacksonModule saplJacksonModule() {
        return new SaplJacksonModule();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
    SaplServletJacksonModule saplServletJacksonModule() {
        return new SaplServletJacksonModule();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.http.server.reactive.ServerHttpRequest")
    SaplReactiveJacksonModule saplReactiveJacksonModule() {
        return new SaplReactiveJacksonModule();
    }

}
