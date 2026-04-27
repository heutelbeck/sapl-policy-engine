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
import io.sapl.spring.pep.http.reactive.DefaultReactiveAuthorizationSubscriptionFactory;
import io.sapl.spring.pep.http.reactive.ReactiveAuthorizationSubscriptionFactory;
import io.sapl.spring.pep.http.reactive.ReactiveSaplAuthorizationManager;
import io.sapl.spring.pep.http.reactive.SaplHttpPepWebFilter;
import io.sapl.spring.pep.http.reactive.SaplServerAccessDeniedHandler;
import io.sapl.spring.pep.http.servlet.AuthorizationSubscriptionFactory;
import io.sapl.spring.pep.http.servlet.DefaultAuthorizationSubscriptionFactory;
import io.sapl.spring.pep.http.servlet.SaplAccessDeniedHandler;
import io.sapl.spring.pep.http.servlet.SaplAuthorizationManager;
import io.sapl.spring.pep.http.servlet.SaplHttpPepFilter;
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
    @ConditionalOnClass(name = "io.sapl.spring.pep.http.servlet.SaplAuthorizationManager")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class Servlet {

        @Bean
        @ConditionalOnMissingBean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        AuthorizationSubscriptionFactory saplAuthorizationSubscriptionFactory(ObjectMapper mapper) {
            return new DefaultAuthorizationSubscriptionFactory(mapper);
        }

        @Bean
        @ConditionalOnMissingBean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        SaplAuthorizationManager saplAuthorizationManager(PolicyDecisionPoint pdp,
                EnforcementPlanner enforcementPlanner, AuthorizationSubscriptionFactory subscriptionFactory) {
            log.debug("Servlet-based environment detected. Deploy SaplAuthorizationManager.");
            return new SaplAuthorizationManager(pdp, enforcementPlanner, subscriptionFactory);
        }

        @Bean
        @ConditionalOnMissingBean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        SaplAccessDeniedHandler saplAccessDeniedHandler() {
            return new SaplAccessDeniedHandler();
        }

        @Bean
        @ConditionalOnMissingBean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        SaplHttpPepFilter saplHttpPepFilter() {
            return new SaplHttpPepFilter();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.sapl.spring.pep.http.reactive.ReactiveSaplAuthorizationManager")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    static class Reactive {

        @Bean
        @ConditionalOnMissingBean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        ReactiveAuthorizationSubscriptionFactory reactiveSaplAuthorizationSubscriptionFactory(ObjectMapper mapper) {
            return new DefaultReactiveAuthorizationSubscriptionFactory(mapper);
        }

        @Bean
        @ConditionalOnMissingBean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        ReactiveSaplAuthorizationManager reactiveSaplAuthorizationManager(PolicyDecisionPoint pdp,
                EnforcementPlanner enforcementPlanner, ReactiveAuthorizationSubscriptionFactory subscriptionFactory) {
            log.debug("Webflux environment detected. Deploy ReactiveSaplAuthorizationManager.");
            return new ReactiveSaplAuthorizationManager(pdp, enforcementPlanner, subscriptionFactory);
        }

        @Bean
        @ConditionalOnMissingBean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        SaplServerAccessDeniedHandler saplServerAccessDeniedHandler() {
            return new SaplServerAccessDeniedHandler();
        }

        @Bean
        @ConditionalOnMissingBean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        SaplHttpPepWebFilter saplHttpPepWebFilter() {
            return new SaplHttpPepWebFilter();
        }
    }
}
