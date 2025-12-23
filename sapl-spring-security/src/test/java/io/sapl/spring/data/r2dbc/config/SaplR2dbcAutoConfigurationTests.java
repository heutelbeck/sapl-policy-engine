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
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import io.sapl.spring.data.services.RepositoryInformationCollectorService;
import io.sapl.spring.data.r2dbc.database.Person;
import io.sapl.spring.data.r2dbc.enforcement.R2dbcAnnotationQueryManipulationEnforcementPoint;
import io.sapl.spring.data.r2dbc.enforcement.R2dbcMethodNameQueryManipulationEnforcementPoint;
import io.sapl.spring.data.r2dbc.enforcement.R2dbcPolicyEnforcementPoint;
import io.sapl.spring.data.r2dbc.proxy.R2dbcBeanPostProcessor;
import io.sapl.spring.data.r2dbc.proxy.R2dbcRepositoryFactoryCustomizer;
import io.sapl.spring.data.r2dbc.proxy.R2dbcRepositoryProxyPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = SaplR2dbcAutoConfiguration.class)
class SaplR2dbcAutoConfigurationTests {

    @MockitoBean
    ObjectProvider<BeanFactory> beanFactoryMock;

    @MockitoBean
    ObjectProvider<AuthorizationSubscriptionBuilderService> authorizationSubscriptionBuilderServiceMock;

    @MockitoBean
    ObjectProvider<PolicyDecisionPoint> pdpProviderMock;

    @MockitoBean
    ObjectProvider<ConstraintQueryEnforcementService> constraintQueryEnforcementServiceMock;

    @MockitoBean
    ConstraintEnforcementService constraintEnforcementServiceMock;

    @MockitoBean
    RepositoryInformationCollectorService repositoryInformationCollectorServiceMock;

    @Autowired
    R2dbcPolicyEnforcementPoint<Person> mongoReactivePolicyEnforcementPoint;

    @Autowired
    R2dbcRepositoryProxyPostProcessor<Person> mongoReactiveRepositoryProxyPostProcessor;

    @Autowired
    R2dbcRepositoryFactoryCustomizer mongoReactiveRepositoryFactoryCustomizer;

    @Autowired
    R2dbcBeanPostProcessor mongoReactiveBeanPostProcessor;

    @Autowired
    R2dbcAnnotationQueryManipulationEnforcementPoint<Person> mongoReactiveAnnotationQueryManipulationEnforcementPoint;

    @Autowired
    R2dbcMethodNameQueryManipulationEnforcementPoint<Person> mongoReactiveMethodNameQueryManipulationEnforcementPointProvider;

    @Test
    void when_constraintQueryEnforcementService_then_createBeans() {
        // GIVEN

        // WHEN

        // THEN
        assertNotNull(mongoReactivePolicyEnforcementPoint);
        assertNotNull(mongoReactiveRepositoryProxyPostProcessor);
        assertNotNull(mongoReactiveRepositoryFactoryCustomizer);
        assertNotNull(mongoReactiveBeanPostProcessor);
        assertNotNull(mongoReactiveAnnotationQueryManipulationEnforcementPoint);
        assertNotNull(mongoReactiveMethodNameQueryManipulationEnforcementPointProvider);
    }

}
