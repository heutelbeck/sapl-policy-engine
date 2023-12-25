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
package io.sapl.springdatar2dbc.sapl;

import io.sapl.springdatar2dbc.database.MethodInvocationForTesting;
import io.sapl.springdatar2dbc.database.Person;
import io.sapl.springdatar2dbc.sapl.queryTypes.filterEnforcement.ProceededDataFilterEnforcementPoint;
import io.sapl.springdatar2dbc.sapl.queryTypes.annotationEnforcement.R2dbcAnnotationQueryManipulationEnforcementPoint;
import io.sapl.springdatar2dbc.sapl.queryTypes.methodNameEnforcement.R2dbcMethodNameQueryManipulationEnforcementPoint;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
class QueryManipulationEnforcementPointFactoryTest {

    @Autowired
    QueryManipulationEnforcementPointFactory queryManipulationEnforcementPointFactory;

    @Mock
    BeanFactory beanFactoryMock;

    @Mock
    EmbeddedPolicyDecisionPoint pdpMock;

    @Test
    void createR2dbcAnnotationQueryManipulationEnforcementPoint() {

        try (MockedConstruction<R2dbcAnnotationQueryManipulationEnforcementPoint> mongoAnnotationQueryManipulationEnforcementPointMockedConstruction = Mockito
                .mockConstruction(R2dbcAnnotationQueryManipulationEnforcementPoint.class)) {

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
            Assertions.assertNotNull(
                    mongoAnnotationQueryManipulationEnforcementPointMockedConstruction.constructed().get(0));
            Assertions.assertEquals(result.getClass(), R2dbcAnnotationQueryManipulationEnforcementPoint.class);
        }
    }

    @Test
    void createR2dbcMethodNameQueryManipulationEnforcementPoint() {

        try (MockedConstruction<R2dbcMethodNameQueryManipulationEnforcementPoint> mongoMethodNameQueryManipulationEnforcementPointMockedConstruction = Mockito
                .mockConstruction(R2dbcMethodNameQueryManipulationEnforcementPoint.class)) {

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
            Assertions.assertNotNull(
                    mongoMethodNameQueryManipulationEnforcementPointMockedConstruction.constructed().get(0));
            Assertions.assertEquals(result.getClass(), R2dbcMethodNameQueryManipulationEnforcementPoint.class);
        }
    }

    @Test
    void createProceededDataFilterEnforcementPoint() {

        try (MockedConstruction<ProceededDataFilterEnforcementPoint> proceededDataFilterEnforcementPointMockedConstruction = Mockito
                .mockConstruction(ProceededDataFilterEnforcementPoint.class)) {

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
            Assertions.assertNotNull(proceededDataFilterEnforcementPointMockedConstruction.constructed().get(0));
            Assertions.assertEquals(result.getClass(), ProceededDataFilterEnforcementPoint.class);
        }
    }
}
