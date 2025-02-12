/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.springdatacommon.config;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;

import io.sapl.springdatacommon.services.ConstraintQueryEnforcementService;
import io.sapl.springdatacommon.services.MethodSecurityExpressionEvaluator;
import io.sapl.springdatacommon.services.QueryEnforceAuthorizationSubscriptionService;
import io.sapl.springdatacommon.services.RepositoryInformationCollectorService;
import io.sapl.springdatacommon.services.SecurityExpressionService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AutoConfiguration
public class SaplSpringDataCommonAutoConfiguration {

    public SaplSpringDataCommonAutoConfiguration() {
        log.debug("# Setting up SAPL Spring Data Commons..");
    }

    @Bean
    ConstraintQueryEnforcementService constraintQueryEnforcementService() {
        return new ConstraintQueryEnforcementService();
    }

    @Bean
    QueryEnforceAuthorizationSubscriptionService queryEnforceAnnotationService(BeanFactory beanFactory,
            SecurityExpressionService securityExpressionService) {
        return new QueryEnforceAuthorizationSubscriptionService(beanFactory, securityExpressionService);
    }

    @Bean
    SecurityExpressionService securityExpressionService(MethodSecurityExpressionEvaluator securityExpressionEvaluator) {
        return new SecurityExpressionService(securityExpressionEvaluator);
    }

    @Bean
    MethodSecurityExpressionEvaluator securityExpressionEvaluator(
            ObjectProvider<MethodSecurityExpressionHandler> securityExpressionHandler) {
        return new MethodSecurityExpressionEvaluator(securityExpressionHandler);
    }

    @Bean
    RepositoryInformationCollectorService repositoryInformationCollectorService() {
        return new RepositoryInformationCollectorService();
    }

}
