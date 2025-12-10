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
package io.sapl.spring.data.r2dbc.config;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.data.services.ConstraintQueryEnforcementService;
import io.sapl.spring.data.services.QueryEnforceAuthorizationSubscriptionService;
import io.sapl.spring.data.services.RepositoryInformationCollectorService;
import io.sapl.springdatar2dbc.enforcement.R2dbcAnnotationQueryManipulationEnforcementPoint;
import io.sapl.springdatar2dbc.enforcement.R2dbcMethodNameQueryManipulationEnforcementPoint;
import io.sapl.springdatar2dbc.enforcement.R2dbcPolicyEnforcementPoint;
import io.sapl.springdatar2dbc.proxy.R2dbcBeanPostProcessor;
import io.sapl.springdatar2dbc.proxy.R2dbcRepositoryFactoryCustomizer;
import io.sapl.springdatar2dbc.proxy.R2dbcRepositoryProxyPostProcessor;
import io.sapl.spring.data.r2dbc.queries.QueryManipulationExecutor;
import io.sapl.spring.data.r2dbc.queries.SqlQueryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.data.r2dbc.repository.R2dbcRepository;

/**
 * Autoconfiguration for SAPL R2DBC policy enforcement.
 * This configuration is only activated when R2DBC support is on the classpath.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(R2dbcRepository.class)
public class SaplR2dbcAutoConfiguration<T> {

    public SaplR2dbcAutoConfiguration() {
        log.debug("# Setting up SAPL R2DBC policy enforcement points...");
    }

    @Bean
    R2dbcPolicyEnforcementPoint<T> r2dbcPolicyEnforcementPoint(
            ObjectProvider<QueryEnforceAuthorizationSubscriptionService> queryEnforceAnnotationService,
            ObjectProvider<R2dbcAnnotationQueryManipulationEnforcementPoint<T>> r2dbcAnnotationQueryManipulationEnforcementPointProvider,
            ObjectProvider<R2dbcMethodNameQueryManipulationEnforcementPoint<T>> r2dbcMethodNameQueryManipulationEnforcementPointProvider,
            RepositoryInformationCollectorService repositoryInformationCollectorService) {
        log.debug("# Instantiate R2dbcPolicyEnforcementPoint...");
        return new R2dbcPolicyEnforcementPoint<>(r2dbcAnnotationQueryManipulationEnforcementPointProvider,
                r2dbcMethodNameQueryManipulationEnforcementPointProvider, queryEnforceAnnotationService,
                repositoryInformationCollectorService);
    }

    @Bean
    R2dbcRepositoryProxyPostProcessor<T> r2dbcRepositoryProxyPostProcessor(
            R2dbcPolicyEnforcementPoint<T> r2dbcPolicyEnforcementPoint,
            RepositoryInformationCollectorService repositoryInformationCollectorService) {
        log.debug("# Instantiate R2dbcRepositoryProxyPostProcessor...");
        return new R2dbcRepositoryProxyPostProcessor<>(r2dbcPolicyEnforcementPoint,
                repositoryInformationCollectorService);
    }

    @Bean
    R2dbcRepositoryFactoryCustomizer saplR2dbcRepositoryFactoryCustomizer(
            R2dbcRepositoryProxyPostProcessor<?> r2dbcRepositoryProxyPostProcessor) {
        log.debug("# Instantiate SaplR2dbcRepositoryFactoryCustomizer...");
        return new R2dbcRepositoryFactoryCustomizer(r2dbcRepositoryProxyPostProcessor);
    }

    @Bean
    R2dbcBeanPostProcessor r2dbcBeanPostProcessor(
            ObjectProvider<R2dbcRepositoryFactoryCustomizer> r2dbcRepositoryFactoryCustomizerProvider) {
        log.debug("# Instantiate R2dbcBeanPostProcessor...");
        return new R2dbcBeanPostProcessor(r2dbcRepositoryFactoryCustomizerProvider);
    }

    @Bean
    R2dbcAnnotationQueryManipulationEnforcementPoint<T> r2dbcAnnotationQueryManipulationEnforcementPoint(
            ObjectProvider<PolicyDecisionPoint> pdpProvider,
            ObjectProvider<QueryManipulationExecutor> queryManipulationExecutorProvider,
            ObjectProvider<ConstraintQueryEnforcementService> constraintQueryEnforcementServiceProvider,
            ConstraintEnforcementService constraintEnforcementService) {
        return new R2dbcAnnotationQueryManipulationEnforcementPoint<>(pdpProvider, queryManipulationExecutorProvider,
                constraintQueryEnforcementServiceProvider, constraintEnforcementService);
    }

    @Bean
    R2dbcMethodNameQueryManipulationEnforcementPoint<T> r2dbcMethodNameQueryManipulationEnforcementPointProvider(
            ObjectProvider<PolicyDecisionPoint> pdpProvider,
            ObjectProvider<QueryManipulationExecutor> queryManipulationExecutorProvider,
            ObjectProvider<ConstraintQueryEnforcementService> constraintQueryEnforcementServiceProvider,
            ConstraintEnforcementService constraintEnforcementService) {
        return new R2dbcMethodNameQueryManipulationEnforcementPoint<>(pdpProvider, queryManipulationExecutorProvider,
                constraintQueryEnforcementServiceProvider, constraintEnforcementService);
    }

    @Bean
    QueryManipulationExecutor queryManipulationExecutor(ObjectProvider<SqlQueryExecutor> sqlQueryExecutorProvider) {
        return new QueryManipulationExecutor(sqlQueryExecutorProvider);
    }

    @Bean
    SqlQueryExecutor sqlQueryExecutor(ObjectProvider<BeanFactory> beanFactoryProvider) {
        return new SqlQueryExecutor(beanFactoryProvider);
    }
}
