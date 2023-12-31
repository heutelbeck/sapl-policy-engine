/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.springdatamongoreactive.sapl;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import io.sapl.springdatamongoreactive.sapl.queries.enforcement.MongoAnnotationQueryManipulationEnforcementPoint;
import io.sapl.springdatamongoreactive.sapl.queries.enforcement.MongoMethodNameQueryManipulationEnforcementPoint;
import io.sapl.springdatamongoreactive.sapl.queries.enforcement.ProceededDataFilterEnforcementPoint;

@SpringBootTest
class QueryManipulationEnforcementPointFactoryTest {

    @Autowired
    QueryManipulationEnforcementPointFactory queryManipulationEnforcementPointFactory;

    @Mock
    BeanFactory beanFactoryMock;

    @Mock
    EmbeddedPolicyDecisionPoint pdpMock;

    private static final MethodInvocationForTesting mongoMethodInvocationTest = new MethodInvocationForTesting(
            "findAllByFirstname", new ArrayList<>(List.of(String.class)), null, null);

    @Test
    void createMongoAnnotationQueryManipulationEnforcementPoint() {

        try (@SuppressWarnings("rawtypes")
        MockedConstruction<MongoAnnotationQueryManipulationEnforcementPoint> mongoAnnotationQueryManipulationEnforcementPointMockedConstruction = Mockito
                .mockConstruction(MongoAnnotationQueryManipulationEnforcementPoint.class)) {

            // GIVEN
            var authSub         = AuthorizationSubscription.of("subject", "permitTest", "resource", "environment");
            var enforcementData = new QueryManipulationEnforcementData<TestUser>(mongoMethodInvocationTest,
                    beanFactoryMock, TestUser.class, pdpMock, authSub);

            // WHEN
            var result = queryManipulationEnforcementPointFactory
                    .createMongoAnnotationQueryManipulationEnforcementPoint(enforcementData);

            // THEN
            Assertions.assertNotNull(
                    mongoAnnotationQueryManipulationEnforcementPointMockedConstruction.constructed().get(0));
            Assertions.assertEquals(result.getClass(), MongoAnnotationQueryManipulationEnforcementPoint.class);
        }
    }

    @Test
    void createMongoMethodNameQueryManipulationEnforcementPoint() {

        try (@SuppressWarnings("rawtypes")
        MockedConstruction<MongoMethodNameQueryManipulationEnforcementPoint> mongoMethodNameQueryManipulationEnforcementPointMockedConstruction = Mockito
                .mockConstruction(MongoMethodNameQueryManipulationEnforcementPoint.class)) {

            // GIVEN
            var authSub         = AuthorizationSubscription.of("subject", "permitTest", "resource", "environment");
            var enforcementData = new QueryManipulationEnforcementData<TestUser>(mongoMethodInvocationTest,
                    beanFactoryMock, TestUser.class, pdpMock, authSub);

            // WHEN
            var result = queryManipulationEnforcementPointFactory
                    .createMongoMethodNameQueryManipulationEnforcementPoint(enforcementData);

            // THEN
            Assertions.assertNotNull(
                    mongoMethodNameQueryManipulationEnforcementPointMockedConstruction.constructed().get(0));
            Assertions.assertEquals(result.getClass(), MongoMethodNameQueryManipulationEnforcementPoint.class);
        }
    }

    @Test
    void createProceededDataFilterEnforcementPoint() {

        try (@SuppressWarnings("rawtypes")
        MockedConstruction<ProceededDataFilterEnforcementPoint> proceededDataFilterEnforcementPointMockedConstruction = Mockito
                .mockConstruction(ProceededDataFilterEnforcementPoint.class)) {

            // GIVEN
            var authSub         = AuthorizationSubscription.of("subject", "permitTest", "resource", "environment");
            var enforcementData = new QueryManipulationEnforcementData<TestUser>(mongoMethodInvocationTest,
                    beanFactoryMock, TestUser.class, pdpMock, authSub);

            // WHEN
            var result = queryManipulationEnforcementPointFactory
                    .createProceededDataFilterEnforcementPoint(enforcementData);

            // THEN
            Assertions.assertNotNull(proceededDataFilterEnforcementPointMockedConstruction.constructed().get(0));
            Assertions.assertEquals(result.getClass(), ProceededDataFilterEnforcementPoint.class);
        }
    }
}
