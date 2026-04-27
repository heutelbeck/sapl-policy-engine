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

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Deploys the SAPL HTTP enforcement infrastructure. Servlet runtime gets the
 * blocking authorization manager, access-denied handler, and HTTP PEP filter
 * via {@link Servlet}; reactive runtime gets the non-blocking equivalents
 * via {@link Reactive}. Each inner configuration is gated at class level by
 * {@link ConditionalOnWebApplication} so that servlet types are not loaded
 * when only WebFlux is on the classpath, and vice versa. Both runtimes
 * share the same {@link EnforcementPlanner} bean defined by the
 * method-security configuration.
 * <p>
 * Users opt SAPL into {@code HttpSecurity} or {@code ServerHttpSecurity}
 * via the dedicated configurer that the corresponding inner class
 * documents. The configurers are not auto-applied; the user calls them
 * explicitly.
 */
@Slf4j
@AutoConfiguration
public class AuthorizationManagerConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "io.sapl.spring.pep.http.servlet.SaplAuthorizationManager")
    static class Servlet {

        @Bean
        @ConditionalOnMissingBean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        io.sapl.spring.pep.http.servlet.SaplAuthorizationManager saplAuthorizationManager(PolicyDecisionPoint pdp,
                EnforcementPlanner enforcementPlanner, ObjectMapper mapper) {
            log.debug("Servlet-based environment detected. Deploy SaplAuthorizationManager.");
            return new io.sapl.spring.pep.http.servlet.SaplAuthorizationManager(pdp, enforcementPlanner, mapper);
        }

        @Bean
        @ConditionalOnMissingBean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        io.sapl.spring.pep.http.servlet.SaplAccessDeniedHandler saplAccessDeniedHandler() {
            return new io.sapl.spring.pep.http.servlet.SaplAccessDeniedHandler();
        }

        @Bean
        @ConditionalOnMissingBean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        io.sapl.spring.pep.http.servlet.SaplHttpPepFilter saplHttpPepFilter() {
            return new io.sapl.spring.pep.http.servlet.SaplHttpPepFilter();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass(name = "io.sapl.spring.pep.http.reactive.ReactiveSaplAuthorizationManager")
    static class Reactive {

        @Bean
        @ConditionalOnMissingBean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        io.sapl.spring.pep.http.reactive.ReactiveSaplAuthorizationManager reactiveSaplAuthorizationManager(
                PolicyDecisionPoint pdp, EnforcementPlanner enforcementPlanner, ObjectMapper mapper) {
            log.debug("Webflux environment detected. Deploy ReactiveSaplAuthorizationManager.");
            return new io.sapl.spring.pep.http.reactive.ReactiveSaplAuthorizationManager(pdp, enforcementPlanner,
                    mapper);
        }

        @Bean
        @ConditionalOnMissingBean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        io.sapl.spring.pep.http.reactive.SaplServerAccessDeniedHandler saplServerAccessDeniedHandler() {
            return new io.sapl.spring.pep.http.reactive.SaplServerAccessDeniedHandler();
        }

        @Bean
        @ConditionalOnMissingBean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        io.sapl.spring.pep.http.reactive.SaplHttpPepWebFilter saplHttpPepWebFilter() {
            return new io.sapl.spring.pep.http.reactive.SaplHttpPepWebFilter();
        }
    }
}
