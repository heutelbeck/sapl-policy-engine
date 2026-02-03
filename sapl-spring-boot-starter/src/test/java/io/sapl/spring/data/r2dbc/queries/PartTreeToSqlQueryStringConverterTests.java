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
package io.sapl.spring.data.r2dbc.queries;

import io.sapl.spring.data.r2dbc.database.Person;
import io.sapl.spring.data.utils.Utilities;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.commons.util.ReflectionUtils;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.springframework.data.domain.Sort;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class PartTreeToSqlQueryStringConverterTests {

    MethodInvocation methodInvocationMock = mock(MethodInvocation.class, Answers.RETURNS_DEEP_STUBS);

    MockedStatic<Utilities>    utilitiesMock;
    MockedStatic<ConvertToSQL> convertToSQLMock;

    @BeforeEach
    void beforeEach() {
        utilitiesMock    = mockStatic(Utilities.class);
        convertToSQLMock = mockStatic(ConvertToSQL.class);
    }

    @AfterEach
    void afterEach() {
        utilitiesMock.close();
        convertToSQLMock.close();
    }

    @ParameterizedTest
    @SuppressWarnings("unchecked")
    @MethodSource("methodNameToSqlQuery")
    void when_sqlQueryCanBeDerivedFromMethodName_then_createSqlBaseQuery(String methodName, Object[] arguments,
            String sqlQueryResult, List<Map.Entry<Class<?>, Boolean>> utilityIsStringMocks) {
        // GIVEN

        // WHEN
        utilitiesMock.when(() -> Utilities.isSpringDataDefaultMethod(anyString())).thenReturn(false);
        convertToSQLMock.when(() -> ConvertToSQL.prepareAndMergeSortObjects(any(Sort.class), any(Object[].class)))
                .thenReturn("");

        for (Map.Entry<Class<?>, Boolean> utilityIsStringMock : utilityIsStringMocks) {
            utilitiesMock.when(() -> Utilities.isString(eq(utilityIsStringMock.getKey())))
                    .thenReturn(utilityIsStringMock.getValue());
        }

        convertToSQLMock.when(() -> ConvertToSQL.conditions(any(List.class))).thenReturn(sqlQueryResult);
        when(methodInvocationMock.getMethod().getName()).thenReturn(methodName);
        when(methodInvocationMock.getArguments()).thenReturn(arguments);

        final var result = PartTreeToSqlQueryStringConverter.createSqlBaseQuery(methodInvocationMock, Person.class);

        // THEN
        Assertions.assertEquals(sqlQueryResult, result);

        convertToSQLMock.verify(() -> ConvertToSQL.conditions(any(List.class)), times(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void when_createSqlBaseQuery_then_returnEmptyStringBecauseMethodIsDefaultMethod() {
        // GIVEN

        // WHEN
        utilitiesMock.when(() -> Utilities.isSpringDataDefaultMethod(anyString())).thenReturn(true);
        convertToSQLMock.when(() -> ConvertToSQL.prepareAndMergeSortObjects(any(Sort.class), any(Object[].class)))
                .thenReturn("");
        convertToSQLMock.when(() -> ConvertToSQL.conditions(any(List.class))).thenReturn("");
        when(methodInvocationMock.getMethod().getName()).thenReturn("");
        when(methodInvocationMock.getArguments()).thenReturn(new Object[] {});

        final var result = PartTreeToSqlQueryStringConverter.createSqlBaseQuery(methodInvocationMock, Person.class);

        // THEN
        Assertions.assertEquals("", result);
        convertToSQLMock.verify(() -> ConvertToSQL.conditions(any(List.class)), times(0));
    }

    @Test
    void when_partTreeHasNoOrPartAtAll_then_throwNoSuchElementException() {
        // GIVEN

        // WHEN
        when(methodInvocationMock.getMethod().getName()).thenReturn("findAllByAge");
        when(methodInvocationMock.getArguments()).thenReturn(new Object[] {});

        // THEN
        assertThrows(NoSuchElementException.class,
                () -> PartTreeToSqlQueryStringConverter.createSqlBaseQuery(methodInvocationMock, Person.class));
    }

    @Test
    void when_partTreeHasNoOrPartAtAll_then_throwIllegalStateException() {
        // GIVEN

        // WHEN
        utilitiesMock.when(() -> Utilities.isSpringDataDefaultMethod(anyString())).thenReturn(false);
        convertToSQLMock.when(() -> ConvertToSQL.prepareAndMergeSortObjects(any(Sort.class), any(Object[].class)))
                .thenReturn("");
        utilitiesMock.when(() -> Utilities.isString(anyString())).thenReturn(true);
        when(methodInvocationMock.getMethod().getName()).thenReturn("findAllByFirstnameIn");
        when(methodInvocationMock.getArguments()).thenReturn(new Object[] { "Juni" });

        // THEN
        assertThrows(IllegalStateException.class,
                () -> PartTreeToSqlQueryStringConverter.createSqlBaseQuery(methodInvocationMock, Person.class));
    }

    @Test
    void when_partTreeHasNoOrPartAtAll_then_throwNullPointerException() {
        // GIVEN

        // WHEN
        when(methodInvocationMock.getMethod().getName()).thenReturn("findAllByAgeAndFirstname");
        when(methodInvocationMock.getArguments()).thenReturn(new Object[] { 22, null });

        // THEN
        assertThrows(NullPointerException.class,
                () -> PartTreeToSqlQueryStringConverter.createSqlBaseQuery(methodInvocationMock, Person.class));
    }

    @Test
    void when_classIsStaticUtilityClass_then_instantiateThisTestForCoverageReasonsOfConstructor() {
        assertThrows(InvocationTargetException.class, () -> {
            final var constructor = PartTreeToSqlQueryStringConverter.class.getDeclaredConstructor();
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
            ReflectionUtils.makeAccessible(constructor);
            constructor.newInstance();
        });
    }

    private static Stream<Arguments> methodNameToSqlQuery() {

        return Stream.of(
                arguments("readByAgeIs", new Object[] { 30 }, "age = 30", List.of(Map.entry(int.class, false))),
                arguments("getByAgeAfter", new Object[] { 30 }, "age > 30", List.of(Map.entry(int.class, false))),
                arguments("readByAgeIsLessThanEqual", new Object[] { 30 }, "age <= 30",
                        List.of(Map.entry(int.class, false))),
                arguments("queryByAgeIsGreaterThanEqual", new Object[] { 30 }, "age >= 30",
                        List.of(Map.entry(int.class, false))),
                arguments("findByFirstnameIsNot", new Object[] { "Aaron" }, "firstname <> 'Aaron'",
                        List.of(Map.entry(String.class, true))),
                arguments("findByFirstnameExists", new Object[] { "Aaron" }, "firstname EXISTS 'Aaron'",
                        List.of(Map.entry(String.class, true))),
                arguments("streamAllByFirstnameLike", new Object[] { "Aaron" }, "firstname LIKE 'Aaron'",
                        List.of(Map.entry(String.class, true))),
                arguments("streamAllByAgeIn", new Object[] { List.of(20, 30, 40) }, "age IN (20, 30, 40)",
                        List.of(Map.entry(int.class, false))),
                arguments("searchAllByFirstnameIsNotLike", new Object[] { "Aaron" }, "firstname NOT LIKE 'Aaron'",
                        List.of(Map.entry(String.class, true))),
                arguments("findAllByFirstnameAndAgeBefore", new Object[] { '2', 30 }, "firstname = '2' AND age < 30",
                        List.of(Map.entry(String.class, true), Map.entry(int.class, false))),
                arguments("findAllByAgeOrderByAgeAscFirstnameDesc", new Object[] { 30 },
                        "age = 30 ORDER BY age ASC, firstname DESC", List.of(Map.entry(int.class, false))),
                arguments("queryByAgeIsGreaterThanEqualOrFirstnameIs", new Object[] { 30, "Aaron" },
                        "age >= 30 OR firstname = 'Aaron'",
                        List.of(Map.entry(int.class, false), Map.entry(String.class, true))),
                arguments("streamAllByAgeBetweenAndFirstname", new Object[] { List.of(1, 30), "Aaron" },
                        "age BETWEEN (1, 30) AND firstname = 'Aaron'",
                        List.of(Map.entry(int.class, false), Map.entry(String.class, true))),
                arguments("streamAllByFirstnameIsNotIn", new Object[] { List.of("Aaron", "Brian", "Cathrin") },
                        "firstname NIN ('Aaron', 'Brian', 'Cathrin')", List.of(Map.entry(String.class, true))));
    }
}
