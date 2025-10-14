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
package io.sapl.springdatamongoreactive.config;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.springdatacommon.services.ConstraintQueryEnforcementService;
import io.sapl.springdatacommon.services.QueryEnforceAuthorizationSubscriptionService;
import io.sapl.springdatacommon.services.RepositoryInformationCollectorService;
import io.sapl.springdatamongoreactive.enforcement.MongoReactiveAnnotationQueryManipulationEnforcementPoint;
import io.sapl.springdatamongoreactive.enforcement.MongoReactiveMethodNameQueryManipulationEnforcementPoint;
import io.sapl.springdatamongoreactive.enforcement.MongoReactivePolicyEnforcementPoint;
import io.sapl.springdatamongoreactive.proxy.MongoReactiveBeanPostProcessor;
import io.sapl.springdatamongoreactive.proxy.MongoReactiveRepositoryFactoryCustomizer;
import io.sapl.springdatamongoreactive.proxy.MongoReactiveRepositoryProxyPostProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@Slf4j
@AutoConfiguration
public class SaplMongoReactiveAutoConfiguration<T> {

    public SaplMongoReactiveAutoConfiguration() {
        log.debug("# Setting up SAPL MongoReactive policy enforcement points...");
    }

    @Bean
    MongoReactivePolicyEnforcementPoint<T> mongoReactivePolicyEnforcementPoint(
            ObjectProvider<QueryEnforceAuthorizationSubscriptionService> queryEnforceAnnotationService,
            ObjectProvider<MongoReactiveAnnotationQueryManipulationEnforcementPoint<T>> mongoReactiveAnnotationQueryManipulationEnforcementPointProvider,
            ObjectProvider<MongoReactiveMethodNameQueryManipulationEnforcementPoint<T>> mongoReactiveMethodNameQueryManipulationEnforcementPointProvider,
            RepositoryInformationCollectorService repositoryInformationCollectorService) {
        log.debug("# Instantiate MongoReactivePolicyEnforcementPoint...");
        return new MongoReactivePolicyEnforcementPoint<>(
                mongoReactiveAnnotationQueryManipulationEnforcementPointProvider,
                mongoReactiveMethodNameQueryManipulationEnforcementPointProvider, queryEnforceAnnotationService,
                repositoryInformationCollectorService);
    }

    @Bean
    MongoReactiveRepositoryProxyPostProcessor<T> mongoReactiveRepositoryProxyPostProcessor(
            MongoReactivePolicyEnforcementPoint<T> mongoReactivePolicyEnforcementPoint,
            RepositoryInformationCollectorService repositoryInformationCollectorService) {
        log.debug("# Instantiate MongoReactiveRepositoryProxyPostProcessor...");
        return new MongoReactiveRepositoryProxyPostProcessor<>(mongoReactivePolicyEnforcementPoint,
                repositoryInformationCollectorService);
    }

    @Bean
    MongoReactiveRepositoryFactoryCustomizer saplMongoReactiveRepositoryFactoryCustomizer(
            MongoReactiveRepositoryProxyPostProcessor<?> mongoReactiveRepositoryProxyPostProcessor) {
        log.debug("# Instantiate SaplMongoReactiveRepositoryFactoryCustomizer...");
        return new MongoReactiveRepositoryFactoryCustomizer(mongoReactiveRepositoryProxyPostProcessor);
    }

    @Bean
    MongoReactiveBeanPostProcessor mongoReactiveBeanPostProcessor(
            ObjectProvider<MongoReactiveRepositoryFactoryCustomizer> mongoReactiveRepositoryFactoryCustomizerProvider) {
        log.debug("# Instantiate MongoReactiveBeanPostProcessor...");
        return new MongoReactiveBeanPostProcessor(mongoReactiveRepositoryFactoryCustomizerProvider);
    }

    @Bean
    MongoReactiveAnnotationQueryManipulationEnforcementPoint<T> mongoReactiveAnnotationQueryManipulationEnforcementPoint(
            ObjectProvider<PolicyDecisionPoint> pdpProvider, ObjectProvider<BeanFactory> beanFactoryProvider,
            ObjectProvider<ConstraintQueryEnforcementService> constraintQueryEnforcementServiceProvider,
            ConstraintEnforcementService constraintEnforcementService) {
        return new MongoReactiveAnnotationQueryManipulationEnforcementPoint<>(pdpProvider, beanFactoryProvider,
                constraintQueryEnforcementServiceProvider, constraintEnforcementService);
    }

    @Bean
    MongoReactiveMethodNameQueryManipulationEnforcementPoint<T> mongoReactiveMethodNameQueryManipulationEnforcementPointProvider(
            ObjectProvider<PolicyDecisionPoint> pdpProvider, ObjectProvider<BeanFactory> beanFactoryProvider,
            ObjectProvider<ConstraintQueryEnforcementService> constraintQueryEnforcementServiceProvider,
            ConstraintEnforcementService constraintEnforcementService) {
        return new MongoReactiveMethodNameQueryManipulationEnforcementPoint<>(pdpProvider, beanFactoryProvider,
                constraintQueryEnforcementServiceProvider, constraintEnforcementService);
    }

}
