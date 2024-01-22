/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.springdatacommon.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatacommon.sapl.queries.enforcement.ProceededDataFilterEnforcementPoint;
import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import io.sapl.springdatamongoreactive.sapl.queries.enforcement.MongoAnnotationQueryManipulationEnforcementPoint;
import io.sapl.springdatamongoreactive.sapl.queries.enforcement.MongoMethodNameQueryManipulationEnforcementPoint;

@SuppressWarnings("rawtypes") // mocking of generic types
@SpringBootTest(classes = QueryManipulationEnforcementPointFactory.class)
class QueryManipulationEnforcementPointFactoryTests {

    @Autowired
    QueryManipulationEnforcementPointFactory queryManipulationEnforcementPointFactory;

    BeanFactory                 beanFactoryMock = mock(BeanFactory.class);
    EmbeddedPolicyDecisionPoint pdpMock         = mock(EmbeddedPolicyDecisionPoint.class);

    private static final MethodInvocationForTesting mongoMethodInvocationTest = new MethodInvocationForTesting(
            "findAllByFirstname", new ArrayList<>(List.of(String.class)), null, null);

    @Test
    void createMongoAnnotationQueryManipulationEnforcementPoint() {

        try (MockedConstruction<MongoAnnotationQueryManipulationEnforcementPoint> mongoAnnotationQueryManipulationEnforcementPointMockedConstruction = mockConstruction(
                MongoAnnotationQueryManipulationEnforcementPoint.class)) {

            // GIVEN
            var authSub         = AuthorizationSubscription.of("subject", "permitTest", "resource", "environment");
            var enforcementData = new QueryManipulationEnforcementData<TestUser>(mongoMethodInvocationTest,
                    beanFactoryMock, TestUser.class, pdpMock, authSub);

            // WHEN
            var result = queryManipulationEnforcementPointFactory
                    .createMongoAnnotationQueryManipulationEnforcementPoint(enforcementData);

            // THEN
            assertNotNull(mongoAnnotationQueryManipulationEnforcementPointMockedConstruction.constructed().get(0));
            assertEquals(result.getClass(), MongoAnnotationQueryManipulationEnforcementPoint.class);
        }
    }

    @Test
    void createMongoMethodNameQueryManipulationEnforcementPoint() {

        try (MockedConstruction<MongoMethodNameQueryManipulationEnforcementPoint> mongoMethodNameQueryManipulationEnforcementPointMockedConstruction = mockConstruction(
                MongoMethodNameQueryManipulationEnforcementPoint.class)) {

            // GIVEN
            var authSub         = AuthorizationSubscription.of("subject", "permitTest", "resource", "environment");
            var enforcementData = new QueryManipulationEnforcementData<TestUser>(mongoMethodInvocationTest,
                    beanFactoryMock, TestUser.class, pdpMock, authSub);

            // WHEN
            var result = queryManipulationEnforcementPointFactory
                    .createMongoMethodNameQueryManipulationEnforcementPoint(enforcementData);

            // THEN
            assertNotNull(mongoMethodNameQueryManipulationEnforcementPointMockedConstruction.constructed().get(0));
            assertEquals(result.getClass(), MongoMethodNameQueryManipulationEnforcementPoint.class);
        }
    }

    @Test
    void createProceededDataFilterEnforcementPoint() {

        try (MockedConstruction<ProceededDataFilterEnforcementPoint> proceededDataFilterEnforcementPointMockedConstruction = mockConstruction(
                ProceededDataFilterEnforcementPoint.class)) {

            // GIVEN
            var authSub         = AuthorizationSubscription.of("subject", "permitTest", "resource", "environment");
            var enforcementData = new QueryManipulationEnforcementData<TestUser>(mongoMethodInvocationTest,
                    beanFactoryMock, TestUser.class, pdpMock, authSub);

            // WHEN
            var result = queryManipulationEnforcementPointFactory
                    .createProceededDataFilterEnforcementPoint(enforcementData);

            // THEN
            assertNotNull(proceededDataFilterEnforcementPointMockedConstruction.constructed().get(0));
            assertEquals(result.getClass(), ProceededDataFilterEnforcementPoint.class);
        }
    }
}
