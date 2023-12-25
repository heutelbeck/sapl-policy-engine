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

import io.sapl.springdatar2dbc.database.Person;
import io.sapl.springdatar2dbc.sapl.queryTypes.methodNameEnforcement.PartTreeToSqlQueryStringConverter;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.commons.util.ReflectionUtils;
import org.mockito.Answers;
import org.mockito.Mock;
import org.springframework.beans.factory.BeanFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PartTreeToSqlQueryStringConverterTest {

    EmbeddedPolicyDecisionPoint pdpMock;
    BeanFactory                 beanFactoryMock;
    MethodInvocation            methodInvocationMock;

    final AuthorizationSubscription authSubPermit = AuthorizationSubscription.of("subject", "permitTest", "resource",
            "environment");

    @BeforeEach
    void beforeEach() {
        pdpMock              = mock(EmbeddedPolicyDecisionPoint.class);
        beanFactoryMock      = mock(BeanFactory.class);
        methodInvocationMock = mock(MethodInvocation.class, Answers.RETURNS_DEEP_STUBS);
    }

    @ParameterizedTest
    @MethodSource("methodNameToSqlQuery")
    void when_sqlQueryCanBeDerivedFromMethodName_then_createSqlBaseQuery(String methodName, Object[] arguments,
            String sqlQueryResult) {
        // GIVEN
        var enforcementData = new QueryManipulationEnforcementData<>(methodInvocationMock, beanFactoryMock,
                Person.class, pdpMock, authSubPermit);

        // WHEN
        when(methodInvocationMock.getMethod().getName()).thenReturn(methodName);
        when(methodInvocationMock.getArguments()).thenReturn(arguments);

        var result = PartTreeToSqlQueryStringConverter.createSqlBaseQuery(enforcementData);

        // THEN
        Assertions.assertEquals(sqlQueryResult, result);
    }

    @Test
    void when_partTreeHasNoOrPartAtAll_then_throwIllegalStateException() {
        // GIVEN
        var enforcementData = new QueryManipulationEnforcementData<>(methodInvocationMock, beanFactoryMock,
                Person.class, pdpMock, authSubPermit);

        // WHEN
        when(methodInvocationMock.getMethod().getName()).thenReturn("findAllByAge");
        when(methodInvocationMock.getArguments()).thenReturn(new Object[] {});

        // THEN
        assertThrows(NoSuchElementException.class,
                () -> PartTreeToSqlQueryStringConverter.createSqlBaseQuery(enforcementData));
    }

    @Test
    void when_classIsStaticUtilityClass_then_instantiateThisTestForCoverageReasonsOfConstructor() {
        assertThrows(InvocationTargetException.class, () -> {
            var constructor = PartTreeToSqlQueryStringConverter.class.getDeclaredConstructor();
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
            ReflectionUtils.makeAccessible(constructor);
            constructor.newInstance();
        });
    }

    private static Stream<Arguments> methodNameToSqlQuery() {

        return Stream.of(arguments("readByAgeIs", new Object[] { 30 }, "age = 30"),
                arguments("getByAgeAfter", new Object[] { 30 }, "age > 30"),
                arguments("readByAgeIsLessThanEqual", new Object[] { 30 }, "age <= 30"),
                arguments("queryByAgeIsGreaterThanEqual", new Object[] { 30 }, "age >= 30"),
                arguments("findByFirstnameIsNot", new Object[] { "Aaron" }, "firstname <> 'Aaron'"),
                arguments("findByFirstnameExists", new Object[] { "Aaron" }, "firstname EXISTS 'Aaron'"),
                arguments("streamAllByFirstnameLike", new Object[] { "Aaron" }, "firstname LIKE 'Aaron'"),
                arguments("streamAllByAgeIn", new Object[] { List.of(20, 30, 40) }, "age IN (20, 30, 40)"),
                arguments("searchAllByFirstnameIsNotLike", new Object[] { "Aaron" }, "firstname NOT LIKE 'Aaron'"),
                arguments("findAllByFirstnameAndAgeBefore", new Object[] { '2', 30 }, "firstname = '2' AND age < 30"),
                arguments("findAllByAgeOrderByAgeAscFirstnameDesc", new Object[] { 30 },
                        "age = 30 ORDER BY age ASC, firstname DESC"),
                arguments("queryByAgeIsGreaterThanEqualOrFirstnameIs", new Object[] { 30, "Aaron" },
                        "age >= 30 OR firstname = 'Aaron'"),
                arguments("streamAllByAgeBetweenAndFirstname", new Object[] { List.of(1, 30), "Aaron" },
                        "age BETWEEN (1, 30) AND firstname = 'Aaron'"),
                arguments("streamAllByFirstnameIsNotIn", new Object[] { List.of("Aaron", "Brian", "Cathrin") },
                        "firstname NIN ('Aaron', 'Brian', 'Cathrin')"));
    }
}
