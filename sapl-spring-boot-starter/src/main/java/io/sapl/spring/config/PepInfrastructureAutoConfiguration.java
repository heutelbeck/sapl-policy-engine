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

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.core.GrantedAuthorityDefaults;

import io.sapl.spring.method.metadata.SaplAttributeRegistry;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.pep.constraints.providers.ContentFilterPredicateProvider;
import io.sapl.spring.pep.constraints.providers.ContentFilteringProvider;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import io.sapl.spring.subscriptions.SubscriptionSecretsInjector;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Provides the shared infrastructure beans the SAPL PEP framework needs
 * regardless of which Enable* annotation is active: the
 * {@link EnforcementPlanner}, the {@link SaplAttributeRegistry}, the
 * {@link AuthorizationSubscriptionBuilderService}, and the framework's
 * default {@link ConstraintHandlerProvider} beans (content filtering and
 * content-filter predicate).
 * </p>
 * Method-security advisors live in
 * {@link SaplMethodSecurityConfiguration} (blocking) and
 * {@link ReactiveSaplMethodSecurityConfiguration} (reactive) so that the
 * advice surface scales with the activated {@code @Enable*MethodSecurity}.
 */
@Slf4j
@AutoConfiguration
public class PepInfrastructureAutoConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    EnforcementPlanner enforcementPlanner(ObjectProvider<List<ConstraintHandlerProvider>> providersProvider,
            ObjectMapper objectMapper) {
        return new EnforcementPlanner(providersProvider.getIfAvailable(List::of), objectMapper);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    SaplAttributeRegistry saplAttributeRegistry(
            ObjectProvider<MethodSecurityExpressionHandler> expressionHandlerProvider,
            ObjectProvider<GrantedAuthorityDefaults> defaultsProvider, ApplicationContext context) {
        return new SaplAttributeRegistry(
                MethodSecurityExpressionHandlers.resolve(expressionHandlerProvider, defaultsProvider, context));
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    AuthorizationSubscriptionBuilderService authorizationSubscriptionBuilderService(
            ObjectProvider<MethodSecurityExpressionHandler> expressionHandlerProvider,
            ObjectProvider<ObjectMapper> mapperProvider, ObjectProvider<GrantedAuthorityDefaults> defaultsProvider,
            ApplicationContext context, ObjectProvider<SubscriptionSecretsInjector> secretsInjectorProvider) {
        return new AuthorizationSubscriptionBuilderService(expressionHandlerProvider, mapperProvider, defaultsProvider,
                context, secretsInjectorProvider.getIfAvailable());
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    ContentFilteringProvider contentFilteringProvider(ObjectMapper objectMapper) {
        return new ContentFilteringProvider(objectMapper);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    ContentFilterPredicateProvider contentFilterPredicateProvider(ObjectMapper objectMapper) {
        return new ContentFilterPredicateProvider(objectMapper);
    }
}
