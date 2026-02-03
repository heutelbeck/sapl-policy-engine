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
package io.sapl.spring.data.mongo.config;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.data.mongo.enforcement.MongoReactiveAnnotationQueryManipulationEnforcementPoint;
import io.sapl.spring.data.mongo.enforcement.MongoReactiveMethodNameQueryManipulationEnforcementPoint;
import io.sapl.spring.data.mongo.enforcement.MongoReactivePolicyEnforcementPoint;
import io.sapl.spring.data.mongo.proxy.MongoReactiveBeanPostProcessor;
import io.sapl.spring.data.mongo.proxy.MongoReactiveRepositoryFactoryCustomizer;
import io.sapl.spring.data.mongo.proxy.MongoReactiveRepositoryProxyPostProcessor;
import io.sapl.spring.data.mongo.sapl.database.TestUser;
import io.sapl.spring.data.services.ConstraintQueryEnforcementService;
import io.sapl.spring.data.services.RepositoryInformationCollectorService;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
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
