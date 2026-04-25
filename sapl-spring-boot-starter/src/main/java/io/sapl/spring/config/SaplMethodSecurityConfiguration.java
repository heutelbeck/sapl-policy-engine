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

import org.springframework.aop.Advisor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.method.metadata.SaplAttributeRegistry;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.pep.data.ShimSignalContributor;
import io.sapl.spring.pep.method.blocking.PolicyEnforcementPointAroundMethodInterceptor;
import io.sapl.spring.pep.method.blocking.PostEnforcePolicyEnforcementPoint;
import io.sapl.spring.pep.method.blocking.PreEnforcePolicyEnforcementPoint;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Wires the blocking @PreEnforce and @PostEnforce method-security advisors to
 * the new shim-signal-based PEP framework. The shared infrastructure beans
 * (planner, attribute registry, subscription builder) come from
 * {@link PepInfrastructureAutoConfiguration}.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
class SaplMethodSecurityConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    Advisor preEnforcePolicyEnforcementPoint(ObjectProvider<PolicyDecisionPoint> policyDecisionPointProvider,
            ObjectProvider<SaplAttributeRegistry> attributeRegistryProvider,
            ObjectProvider<EnforcementPlanner> enforcementPlannerProvider,
            ObjectProvider<AuthorizationSubscriptionBuilderService> subscriptionBuilderProvider,
            ObjectProvider<List<ShimSignalContributor>> shimSignalContributorsProvider) {
        log.debug("Deploy blocking @PreEnforce Policy Enforcement Point");
        val pep = new PreEnforcePolicyEnforcementPoint(policyDecisionPointProvider, attributeRegistryProvider,
                enforcementPlannerProvider, subscriptionBuilderProvider, shimSignalContributorsProvider);
        return PolicyEnforcementPointAroundMethodInterceptor.preEnforce(pep);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    Advisor postEnforcePolicyEnforcementPoint(ObjectProvider<PolicyDecisionPoint> policyDecisionPointProvider,
            ObjectProvider<SaplAttributeRegistry> attributeRegistryProvider,
            ObjectProvider<EnforcementPlanner> enforcementPlannerProvider,
            ObjectProvider<AuthorizationSubscriptionBuilderService> subscriptionBuilderProvider) {
        log.debug("Deploy blocking @PostEnforce Policy Enforcement Point");
        val pep = new PostEnforcePolicyEnforcementPoint(policyDecisionPointProvider, attributeRegistryProvider,
                enforcementPlannerProvider, subscriptionBuilderProvider);
        return PolicyEnforcementPointAroundMethodInterceptor.postEnforce(pep);
    }
}
