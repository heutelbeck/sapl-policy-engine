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
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.method.blocking.PolicyEnforcementPointAroundMethodInterceptor;
import io.sapl.spring.method.blocking.PostEnforcePolicyEnforcementPoint;
import io.sapl.spring.method.blocking.PreEnforcePolicyEnforcementPoint;
import io.sapl.spring.method.metadata.SaplAttributeRegistry;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.Advisor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.core.GrantedAuthorityDefaults;

@Slf4j
@Configuration(proxyBeanMethods = false)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
class SaplMethodSecurityConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    AuthorizationSubscriptionBuilderService authorizationSubscriptionBuilderService(
            ObjectProvider<MethodSecurityExpressionHandler> expressionHandlerProvider,
            ObjectProvider<ObjectMapper> mapperProvider, ObjectProvider<GrantedAuthorityDefaults> defaultsProvider,
            ApplicationContext context) {
        return new AuthorizationSubscriptionBuilderService(expressionHandlerProvider, mapperProvider, defaultsProvider,
                context);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    Advisor preEnforcePolicyEnforcementPoint(ObjectProvider<PolicyDecisionPoint> policyDecisionPointProvider,
            ObjectProvider<SaplAttributeRegistry> attributeRegistryProvider,
            ObjectProvider<ConstraintEnforcementService> constraintEnforcementServiceProvider,
            ObjectProvider<AuthorizationSubscriptionBuilderService> subscriptionBuilderProvider) {

        log.debug("Deploy @PreEnforce Policy Enforcement Point");
        final var policyEnforcementPoint = new PreEnforcePolicyEnforcementPoint(policyDecisionPointProvider,
                attributeRegistryProvider, constraintEnforcementServiceProvider, subscriptionBuilderProvider);
        return PolicyEnforcementPointAroundMethodInterceptor.preEnforce(policyEnforcementPoint);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    Advisor postEnforcePolicyEnforcementPoint(ObjectProvider<PolicyDecisionPoint> policyDecisionPointProvider,
            ObjectProvider<SaplAttributeRegistry> attributeRegistryProvider,
            ObjectProvider<ConstraintEnforcementService> constraintEnforcementServiceProvider,
            ObjectProvider<AuthorizationSubscriptionBuilderService> subscriptionBuilderProvider) {

        log.debug("Deploy @PostEnforce Policy Enforcement Point");
        final var policyEnforcementPoint = new PostEnforcePolicyEnforcementPoint(policyDecisionPointProvider,
                attributeRegistryProvider, constraintEnforcementServiceProvider, subscriptionBuilderProvider);
        return PolicyEnforcementPointAroundMethodInterceptor.postEnforce(policyEnforcementPoint);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    SaplAttributeRegistry saplAttributeRegistry(ObjectProvider<GrantedAuthorityDefaults> defaultsProvider,
            ObjectProvider<MethodSecurityExpressionHandler> expressionHandlerProvider, ApplicationContext context) {
        final var expressionProvider = expressionHandlerProvider
                .getIfAvailable(() -> defaultExpressionHandler(defaultsProvider, context));
        return new SaplAttributeRegistry(expressionProvider);
    }

    private static MethodSecurityExpressionHandler defaultExpressionHandler(
            ObjectProvider<GrantedAuthorityDefaults> defaultsProvider, ApplicationContext context) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        defaultsProvider.ifAvailable(d -> handler.setDefaultRolePrefix(d.getRolePrefix()));
        handler.setApplicationContext(context);
        return handler;
    }

}
