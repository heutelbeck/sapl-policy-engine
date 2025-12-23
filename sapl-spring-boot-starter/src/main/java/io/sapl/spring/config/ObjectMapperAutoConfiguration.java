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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.spring.serialization.HttpServletRequestSerializer;
import io.sapl.spring.serialization.MethodInvocationSerializer;
import io.sapl.spring.serialization.ServerHttpRequestSerializer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * This configuration provides a Jackson ObjectMapper bean, if missing.
 * <p>
 * In addition, the SaplJacksonModule is registered for SAPL type serialization,
 * the JDK8 Module for handling Optional, and serializers for HttpServletRequest
 * and MethodInvocation are added.
 * <p>
 * These serializers are used in building authorization subscriptions, if no
 * explicit values for the fields of the subscription (e.g., action, resource)
 * are provided.
 */
@Slf4j
@AutoConfiguration
public class ObjectMapperAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * Register serializers for creating authorization subscriptions.
     */
    @Configuration
    public static class BasicModuleRegistrationConfiguration {

        @Autowired
        void configureObjectMapper(final ObjectMapper mapper) {
            log.debug("Add basic SAPL serializer modules to ObjectMapper");
            final var module = new SimpleModule();
            module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
            mapper.registerModule(module);
            mapper.registerModule(new SaplJacksonModule());
            mapper.registerModule(new Jdk8Module());
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        }

    }

    /**
     * Register serializers for Servlet-based authorization subscriptions.
     */
    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public static class ServletModuleRegistrationConfiguration {

        @Autowired
        void configureObjectMapper(final ObjectMapper mapper) {
            log.debug("Servlet-based environment detected. Add HttpServletRequestSerializer to ObjectMapper.");
            final var module = new SimpleModule();
            module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
            mapper.registerModule(module);
        }

    }

    /**
     * Register serializers for Webflux-based authorization subscriptions.
     */
    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public static class WebfluxModuleRegistrationConfiguration {

        @Autowired
        void configureObjectMapper(final ObjectMapper mapper) {
            log.debug("Webflux environment detected. Add Deploy ServerHttpRequestSerializer to ObjectMapper.");
            final var module = new SimpleModule();
            module.addSerializer(ServerHttpRequest.class, new ServerHttpRequestSerializer());
            mapper.registerModule(module);
        }

    }

}
