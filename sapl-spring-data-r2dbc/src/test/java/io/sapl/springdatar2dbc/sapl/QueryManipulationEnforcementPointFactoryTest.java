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
package io.sapl.springdatar2dbc.sapl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.springdatar2dbc.database.MethodInvocationForTesting;
import io.sapl.springdatar2dbc.database.Person;
import io.sapl.springdatar2dbc.sapl.queries.enforcement.ProceededDataFilterEnforcementPoint;
import io.sapl.springdatar2dbc.sapl.queries.enforcement.R2dbcAnnotationQueryManipulationEnforcementPoint;
import io.sapl.springdatar2dbc.sapl.queries.enforcement.R2dbcMethodNameQueryManipulationEnforcementPoint;

@SpringBootTest(classes = QueryManipulationEnforcementPointFactory.class)
class QueryManipulationEnforcementPointFactoryTest {

    @Mock
    BeanFactory beanFactoryMock;

    @Mock
    EmbeddedPolicyDecisionPoint pdpMock;

    @Test
    void createR2dbcAnnotationQueryManipulationEnforcementPoint() {

        try (@SuppressWarnings("rawtypes")
        MockedConstruction<R2dbcAnnotationQueryManipulationEnforcementPoint> mongoAnnotationQueryManipulationEnforcementPointMockedConstruction = Mockito
                .mockConstruction(R2dbcAnnotationQueryManipulationEnforcementPoint.class)) {

            QueryManipulationEnforcementPointFactory queryManipulationEnforcementPointFactory = new QueryManipulationEnforcementPointFactory();

            // GIVEN
            var methodInvocationMock = new MethodInvocationForTesting("findAllByFirstname",
                    new ArrayList<>(List.of(String.class)), null, null);
            var authSub              = AuthorizationSubscription.of("subject", "permitTest", "resource", "environment");
            var enforcementData      = new QueryManipulationEnforcementData<>(methodInvocationMock, beanFactoryMock,
                    Person.class, pdpMock, authSub);

            // WHEN
            var result = queryManipulationEnforcementPointFactory
                    .createR2dbcAnnotationQueryManipulationEnforcementPoint(enforcementData);

            // THEN
            assertNotNull(mongoAnnotationQueryManipulationEnforcementPointMockedConstruction.constructed().get(0));
            assertEquals(result.getClass(), R2dbcAnnotationQueryManipulationEnforcementPoint.class);
        }
    }

    @Test
    void createR2dbcMethodNameQueryManipulationEnforcementPoint() {

        try (@SuppressWarnings("rawtypes")
        MockedConstruction<R2dbcMethodNameQueryManipulationEnforcementPoint> mongoMethodNameQueryManipulationEnforcementPointMockedConstruction = Mockito
                .mockConstruction(R2dbcMethodNameQueryManipulationEnforcementPoint.class)) {
            QueryManipulationEnforcementPointFactory queryManipulationEnforcementPointFactory = new QueryManipulationEnforcementPointFactory();

            // GIVEN
            var methodInvocationMock = new MethodInvocationForTesting("findAllByAge",
                    new ArrayList<>(List.of(int.class)), null, null);
            var authSub              = AuthorizationSubscription.of("subject", "permitTest", "resource", "environment");
            var enforcementData      = new QueryManipulationEnforcementData<>(methodInvocationMock, beanFactoryMock,
                    Person.class, pdpMock, authSub);

            // WHEN
            var result = queryManipulationEnforcementPointFactory
                    .createR2dbcMethodNameQueryManipulationEnforcementPoint(enforcementData);

            // THEN
            assertNotNull(mongoMethodNameQueryManipulationEnforcementPointMockedConstruction.constructed().get(0));
            assertEquals(result.getClass(), R2dbcMethodNameQueryManipulationEnforcementPoint.class);
        }
    }

    @Test
    void createProceededDataFilterEnforcementPoint() {

        try (@SuppressWarnings("rawtypes")
        MockedConstruction<ProceededDataFilterEnforcementPoint> proceededDataFilterEnforcementPointMockedConstruction = Mockito
                .mockConstruction(ProceededDataFilterEnforcementPoint.class)) {
            QueryManipulationEnforcementPointFactory queryManipulationEnforcementPointFactory = new QueryManipulationEnforcementPointFactory();

            // GIVEN
            var methodInvocationMock = new MethodInvocationForTesting("methodTestWithAge",
                    new ArrayList<>(List.of(int.class)), null, null);
            var authSub              = AuthorizationSubscription.of("subject", "permitTest", "resource", "environment");
            var enforcementData      = new QueryManipulationEnforcementData<>(methodInvocationMock, beanFactoryMock,
                    Person.class, pdpMock, authSub);

            // WHEN
            var result = queryManipulationEnforcementPointFactory
                    .createProceededDataFilterEnforcementPoint(enforcementData);

            // THEN
            assertNotNull(proceededDataFilterEnforcementPointMockedConstruction.constructed().get(0));
            assertEquals(result.getClass(), ProceededDataFilterEnforcementPoint.class);
        }
    }
}
