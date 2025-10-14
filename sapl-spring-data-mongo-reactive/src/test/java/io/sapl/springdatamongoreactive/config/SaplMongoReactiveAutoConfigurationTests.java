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
import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = SaplMongoReactiveAutoConfiguration.class)
class SaplMongoReactiveAutoConfigurationTests {

    @MockitoBean
    ObjectProvider<BeanFactory> beanFactoryMock;

    @MockitoBean
    ObjectProvider<QueryEnforceAuthorizationSubscriptionService> queryEnforceAuthorizationSubscriptionServiceMock;

    @MockitoBean
    ObjectProvider<PolicyDecisionPoint> pdpProviderMock;

    @MockitoBean
    ObjectProvider<ConstraintQueryEnforcementService> constraintQueryEnforcementServiceMock;

    @MockitoBean
    ConstraintEnforcementService constraintEnforcementServiceMock;

    @MockitoBean
    RepositoryInformationCollectorService repositoryInformationCollectorServiceMock;

    @Autowired
    MongoReactivePolicyEnforcementPoint<TestUser> mongoReactivePolicyEnforcementPoint;

    @Autowired
    MongoReactiveRepositoryProxyPostProcessor<TestUser> mongoReactiveRepositoryProxyPostProcessor;

    @Autowired
    MongoReactiveRepositoryFactoryCustomizer mongoReactiveRepositoryFactoryCustomizer;

    @Autowired
    MongoReactiveBeanPostProcessor mongoReactiveBeanPostProcessor;

    @Autowired
    MongoReactiveAnnotationQueryManipulationEnforcementPoint<TestUser> mongoReactiveAnnotationQueryManipulationEnforcementPoint;

    @Autowired
    MongoReactiveMethodNameQueryManipulationEnforcementPoint<TestUser> mongoReactiveMethodNameQueryManipulationEnforcementPointProvider;

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
