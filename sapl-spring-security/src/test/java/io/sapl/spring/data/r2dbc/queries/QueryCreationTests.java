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
package io.sapl.spring.data.r2dbc.queries;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.sapl.spring.data.queries.QueryAnnotationParameterResolver;
import io.sapl.spring.data.r2dbc.database.MethodInvocationForTesting;
import io.sapl.spring.data.r2dbc.database.Person;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.domain.Sort;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class QueryCreationTests {

    private static final ObjectMapper MAPPER          = new ObjectMapper();
    private static final ArrayNode    EMPTY_ARRAYNODE = MAPPER.createArrayNode();

    private static ArrayNode conditionsOne;
    private static ArrayNode conditionsTwo;
    private static ArrayNode conditionsWithOr;
    private static ArrayNode conditionsWithAnd;
    private static ArrayNode selections;
    private static ArrayNode transdormations;

    MockedStatic<ConvertToSQL>                     convertToSQLMock;
    MockedStatic<QuerySelectionUtils>              querySelectionUtilsMock;
    MockedStatic<QueryAnnotationParameterResolver> queryAnnotationParameterResolverMock;

    @BeforeAll
    static void initJsonNodes() throws JsonProcessingException {

        selections = MAPPER.readValue("""
                [
                	{
                		"type": "blacklist",
                		"columns": ["firstname"]
                	},
                	{
                		"type": "blacklist",
                		"columns": ["age"]
                	}
                ]
                """, ArrayNode.class);

        conditionsOne = MAPPER.readValue("""
                [
                	"firstname = 'Juni'",
                	"active = true"
                ]
                """, ArrayNode.class);

        conditionsTwo = MAPPER.readValue("""
                [
                	"AND firstname = 'Juni'",
                	"OR active = true"
                ]
                """, ArrayNode.class);

        conditionsWithOr = MAPPER.readValue("""
                [
                	"OR firstname = 'Juni'"
                ]
                """, ArrayNode.class);

        conditionsWithAnd = MAPPER.readValue("""
                [
                	"AND firstname = 'Juni'"
                ]
                """, ArrayNode.class);
        transdormations   = MAPPER.readValue("""
                [
                	{
                		"firstname": "UPPER"
                	},
                	{
                		"birthday": "YEAR"
                	}
                ]
                """, ArrayNode.class);
    }

    @BeforeEach
    void beforeEach() {
        convertToSQLMock                     = mockStatic(ConvertToSQL.class);
        querySelectionUtilsMock              = mockStatic(QuerySelectionUtils.class);
        queryAnnotationParameterResolverMock = mockStatic(QueryAnnotationParameterResolver.class);
    }

    @AfterEach
    void cleanUp() {
        convertToSQLMock.close();
        querySelectionUtilsMock.close();
        queryAnnotationParameterResolverMock.close();
    }

    @Test
    void when_manipulateQuery_then_returnManipulatedQueryContainsWhere1() {
        // GIVEN
        final var baseQuery = "SELECT * FROM PERSON";
        final var expected  = "SELECT id, active FROM PERSON WHERE age > 22";

        // WHEN
        querySelectionUtilsMock.when(() -> QuerySelectionUtils.createSelectionPartForAnnotation(anyString(),
                any(ArrayNode.class), any(ArrayNode.class), anyString(), eq(Person.class))).thenReturn(expected);

        final var actual = QueryCreation.manipulateQuery(baseQuery, conditionsOne, selections, transdormations, "",
                Person.class);

        // THEN
        assertThat(actual).isEqualTo(expected);

        querySelectionUtilsMock.verify(() -> QuerySelectionUtils.createSelectionPartForAnnotation(anyString(),
                any(ArrayNode.class), any(ArrayNode.class), anyString(), eq(Person.class)), times(1));
    }

    @Test
    void when_manipulateQuery_then_returnManipulatedQueryContainsWhere2() {
        // GIVEN
        final var baseQuery = "SELECT * FROM PERSON WHERE age > 22";
        final var expected  = "SELECT id, active FROM PERSON WHERE age > 22";

        // WHEN
        querySelectionUtilsMock.when(() -> QuerySelectionUtils.createSelectionPartForAnnotation(anyString(),
                any(ArrayNode.class), any(ArrayNode.class), anyString(), eq(Person.class))).thenReturn(expected);

        final var actual = QueryCreation.manipulateQuery(baseQuery, conditionsOne, selections, transdormations, "",
                Person.class);

        // THEN
        assertThat(actual).isEqualTo(expected);

        querySelectionUtilsMock.verify(() -> QuerySelectionUtils.createSelectionPartForAnnotation(anyString(),
                any(ArrayNode.class), any(ArrayNode.class), anyString(), eq(Person.class)), times(1));
    }

    @Test
    void when_manipulateQuery_then_returnManipulatedQueryContainsWhere3() {
        // GIVEN
        final var baseQuery = "SELECT * FROM PERSON WHERE age > 22";
        final var expected  = "SELECT id, active FROM PERSON WHERE firstname = 'Juni' AND age > 22";

        // WHEN
        querySelectionUtilsMock.when(() -> QuerySelectionUtils.createSelectionPartForAnnotation(anyString(),
                any(ArrayNode.class), any(ArrayNode.class), anyString(), eq(Person.class))).thenReturn(expected);

        final var actual = QueryCreation.manipulateQuery(baseQuery, conditionsWithAnd, selections, transdormations, "",
                Person.class);

        // THEN
        assertThat(actual).isEqualTo(expected);

        querySelectionUtilsMock.verify(() -> QuerySelectionUtils.createSelectionPartForAnnotation(anyString(),
                any(ArrayNode.class), any(ArrayNode.class), anyString(), eq(Person.class)), times(1));
    }

    @Test
    void when_manipulateQuery_then_returnManipulatedQueryContainsNoWhere() {
        // GIVEN
        final var baseQuery = "SELECT * FROM PERSON where age > 22";
        final var expected  = "SELECT id, active FROM PERSON WHERE firstname = 'Juni'";

        // WHEN
        querySelectionUtilsMock.when(() -> QuerySelectionUtils.createSelectionPartForAnnotation(anyString(),
                any(ArrayNode.class), any(ArrayNode.class), anyString(), eq(Person.class))).thenReturn(expected);

        final var actual = QueryCreation.manipulateQuery(baseQuery, conditionsWithOr, selections, transdormations, "",
                Person.class);

        // THEN
        assertThat(actual).isEqualTo(expected);

        querySelectionUtilsMock.verify(() -> QuerySelectionUtils.createSelectionPartForAnnotation(anyString(),
                any(ArrayNode.class), any(ArrayNode.class), anyString(), eq(Person.class)), times(1));
    }

    @Test
    void when_createBaselineQuery_then_returnBaselineQuery() {
        // GIVEN
        final var sortingPart          = " ORDER BY age";
        final var baseQuery            = "SELECT id, active FROM PERSON WHERE firstname = 'Juni'";
        final var methodInvocationMock = new MethodInvocationForTesting("findUserTest",
                new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Aaron")), null);

        // WHEN
        queryAnnotationParameterResolverMock.when(() -> QueryAnnotationParameterResolver
                .resolveForRelationalDatabase(any(Method.class), any(Object[].class))).thenReturn(baseQuery);
        convertToSQLMock.when(() -> ConvertToSQL.prepareAndMergeSortObjects(any(Sort.class), any(Object[].class)))
                .thenReturn(sortingPart);

        final var actual = QueryCreation.createBaselineQuery(methodInvocationMock);

        // THEN
        assertThat(actual).isEqualTo(baseQuery + sortingPart);

        queryAnnotationParameterResolverMock.verify(() -> QueryAnnotationParameterResolver
                .resolveForRelationalDatabase(any(Method.class), any(Object[].class)), times(1));
        convertToSQLMock.verify(() -> ConvertToSQL.prepareAndMergeSortObjects(any(Sort.class), any(Object[].class)),
                times(1));
    }

    @Test
    void when_createSqlQuery_then_returnSqlQuery1() {
        // GIVEN
        final var selectionPart = "SELECT firstname, age FROM PERSON WHERE ";
        final var baseQuery     = "age > 22";
        final var expected      = "SELECT firstname, age FROM PERSON WHERE firstname = 'Juni' AND active = true AND age > 22";

        // WHEN
        querySelectionUtilsMock.when(() -> QuerySelectionUtils
                .createSelectionPartForMethodNameQuery(any(ArrayNode.class), any(ArrayNode.class), eq(Person.class)))
                .thenReturn(selectionPart);

        final var actual = QueryCreation.createSqlQuery(conditionsOne, selections, transdormations, Person.class,
                baseQuery);

        // THEN
        assertThat(actual).isEqualTo(expected);

        querySelectionUtilsMock.verify(() -> QuerySelectionUtils
                .createSelectionPartForMethodNameQuery(any(ArrayNode.class), any(ArrayNode.class), eq(Person.class)),
                times(1));
    }

    @Test
    void when_createSqlQuery_then_returnSqlQuery2() {
        // GIVEN
        final var selectionPart = "SELECT firstname, age FROM PERSON WHERE ";
        final var baseQuery     = "";
        final var expected      = "SELECT firstname, age FROM PERSON WHERE firstname = 'Juni' AND active = true";

        // WHEN
        querySelectionUtilsMock.when(() -> QuerySelectionUtils
                .createSelectionPartForMethodNameQuery(any(ArrayNode.class), any(ArrayNode.class), eq(Person.class)))
                .thenReturn(selectionPart);

        final var actual = QueryCreation.createSqlQuery(conditionsOne, selections, transdormations, Person.class,
                baseQuery);

        // THEN
        assertThat(actual).isEqualTo(expected);

        querySelectionUtilsMock.verify(() -> QuerySelectionUtils
                .createSelectionPartForMethodNameQuery(any(ArrayNode.class), any(ArrayNode.class), eq(Person.class)),
                times(1));
    }

    @Test
    void when_createSqlQuery_then_returnSqlQuery3() {
        // GIVEN
        final var selectionPart = "SELECT firstname, age FROM PERSON WHERE ";
        final var baseQuery     = "age > 22";
        final var expected      = "SELECT firstname, age FROM PERSON WHERE age > 22";

        // WHEN
        querySelectionUtilsMock.when(() -> QuerySelectionUtils
                .createSelectionPartForMethodNameQuery(any(ArrayNode.class), any(ArrayNode.class), eq(Person.class)))
                .thenReturn(selectionPart);

        final var actual = QueryCreation.createSqlQuery(EMPTY_ARRAYNODE, selections, transdormations, Person.class,
                baseQuery);

        // THEN
        assertThat(actual).isEqualTo(expected);

        querySelectionUtilsMock.verify(() -> QuerySelectionUtils
                .createSelectionPartForMethodNameQuery(any(ArrayNode.class), any(ArrayNode.class), eq(Person.class)),
                times(1));
    }

    @Test
    void when_createSqlQuery_then_returnSqlQuery4() {
        // GIVEN
        final var selectionPart = "SELECT firstname, age FROM PERSON WHERE ";
        final var baseQuery     = "age > 22";
        final var expected      = "SELECT firstname, age FROM PERSON WHERE  firstname = 'Juni' AND active = true OR  age > 22";

        // WHEN
        querySelectionUtilsMock.when(() -> QuerySelectionUtils
                .createSelectionPartForMethodNameQuery(any(ArrayNode.class), any(ArrayNode.class), eq(Person.class)))
                .thenReturn(selectionPart);

        final var actual = QueryCreation.createSqlQuery(conditionsTwo, selections, transdormations, Person.class,
                baseQuery);

        // THEN
        assertThat(actual).isEqualTo(expected);

        querySelectionUtilsMock.verify(() -> QuerySelectionUtils
                .createSelectionPartForMethodNameQuery(any(ArrayNode.class), any(ArrayNode.class), eq(Person.class)),
                times(1));
    }

}
