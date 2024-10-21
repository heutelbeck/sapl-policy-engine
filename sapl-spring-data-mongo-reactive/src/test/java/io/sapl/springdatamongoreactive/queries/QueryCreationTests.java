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
package io.sapl.springdatamongoreactive.queries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.repository.query.parser.PartTree;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.springdatacommon.queries.QueryAnnotationParameterResolver;
import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;

class QueryCreationTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ArrayNode conditions;
    private static ArrayNode selections;

    MethodInvocation                               methodInvocationMock = mock(MethodInvocation.class);
    MockedStatic<ConvertToMQL>                     convertToMQLMock;
    MockedStatic<SaplConditionOperation>           saplConditionOperationMock;
    MockedStatic<SaplPartTreeCriteriaCreator>      saplPartTreeCriteriaCreatorMock;
    MockedStatic<QueryAnnotationParameterResolver> queryAnnotationParameterResolverMock;

    @BeforeEach
    public void beforeEach() {
        convertToMQLMock                     = mockStatic(ConvertToMQL.class);
        saplConditionOperationMock           = mockStatic(SaplConditionOperation.class);
        saplPartTreeCriteriaCreatorMock      = mockStatic(SaplPartTreeCriteriaCreator.class);
        queryAnnotationParameterResolverMock = mockStatic(QueryAnnotationParameterResolver.class);

        convertToMQLMock.when(() -> ConvertToMQL.createPageable(any(MethodInvocation.class), any(BasicQuery.class)))
                .thenReturn(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "age")));

    }

    @AfterEach
    public void cleanUp() {
        convertToMQLMock.close();
        saplConditionOperationMock.close();
        saplPartTreeCriteriaCreatorMock.close();
        queryAnnotationParameterResolverMock.close();
    }

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

        conditions = MAPPER.readValue("""
                [
                	"{'firstname': {'$eq': 'Juni' }}",
                	"{'role': {'$eq': 'USER'}}"
                ]
                """, ArrayNode.class);

    }

    @Test
    void when_manipulateQuery_then_returnQuery() {
        // GIVEN
        final var queryString = "{ \"age\": { \"$gte\": 18 } }";
        final var basicQuery  = new BasicQuery(queryString);
        final var expected    = "Query: { \"age\" : { \"$gte\" : 18}, \"firstname\" : { \"$eq\" : \"Juni\"}, \"role\" : { \"$eq\" : \"USER\"}}, Fields: { \"firstname\" : 0, \"age\" : 0}, Sort: { \"age\" : -1}";

        // WHEN
        final var result = QueryCreation.manipulateQuery(conditions, selections, basicQuery, methodInvocationMock);

        // THEN
        assertEquals(result.toString(), expected);
    }

    @Test
    void when_createBaselineQuery_then_returnBasicQuery1() {
        // GIVEN
        final var methodInvocationMock2 = new MethodInvocationForTesting("findUserTest",
                new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Aaron")), null);
        final var baseQueryString       = "{ 'firstname': { '$eq': 'Juni' } }XXXXX{}XXXXX";
        final var expected              = "Query: { \"firstname\" : { \"$eq\" : \"Juni\"}}, Fields: {}, Sort: { \"age\" : -1}";

        // WHEN
        queryAnnotationParameterResolverMock
                .when(() -> QueryAnnotationParameterResolver.resolveForMongoDB(any(Method.class), any(Object[].class)))
                .thenReturn(baseQueryString);

        final var result = QueryCreation.createBaselineQuery(methodInvocationMock2);

        // THEN
        assertEquals(result.toString(), expected);
    }

    @Test
    void when_createBaselineQuery_then_returnBasicQuery2() {
        // GIVEN
        final var methodInvocationMock2 = new MethodInvocationForTesting("findUserTest",
                new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Aaron")), null);
        final var baseQueryString       = "{ 'firstname': { '$eq': 'Juni' } }XXXXX{}XXXXX{ 'firstname' : -1}";
        final var expected              = "Query: { \"firstname\" : { \"$eq\" : \"Juni\"}}, Fields: {}, Sort: { \"firstname\" : -1, \"age\" : -1}";

        // WHEN
        queryAnnotationParameterResolverMock
                .when(() -> QueryAnnotationParameterResolver.resolveForMongoDB(any(Method.class), any(Object[].class)))
                .thenReturn(baseQueryString);

        final var result = QueryCreation.createBaselineQuery(methodInvocationMock2);

        // THEN
        assertEquals(result.toString(), expected);
    }

    @Test
    @SuppressWarnings("unchecked") // List.class
    void when_createManipulatedQuery_then_returnQuery1() {
        // GIVEN
        final var expected           = "Query: { \"$and\" : [{ \"age\" : { \"$gt\" : 14}}]}, Fields: { \"firstname\" : 0, \"age\" : 0}, Sort: {}";
        final var saplConditions     = List.of(new SaplCondition("age", 22, OperatorMongoDB.SIMPLE_PROPERTY, null),
                new SaplCondition("firstname", "Juni", OperatorMongoDB.SIMPLE_PROPERTY, null));
        final var modifiedMethodName = "findAllByAgeAndFirstname";
        final var criteria           = new Criteria().andOperator(Criteria.where("age").gt(14));

        // WHEN
        saplConditionOperationMock.when(() -> SaplConditionOperation.jsonNodeToSaplConditions(any(ArrayNode.class)))
                .thenReturn(saplConditions);
        saplConditionOperationMock.when(() -> SaplConditionOperation.toModifiedMethodName(anyString(), any(List.class)))
                .thenReturn(modifiedMethodName);
        saplPartTreeCriteriaCreatorMock
                .when(() -> SaplPartTreeCriteriaCreator.create(any(List.class), any(PartTree.class)))
                .thenReturn(criteria);

        final var result = QueryCreation.createManipulatedQuery(conditions, selections, "findAllByFirstname",
                TestUser.class, new Object[] { "Juni", PageRequest.of(0, 2) });

        // THEN
        assertEquals(result.toString(), expected);
    }

    @Test
    @SuppressWarnings("unchecked") // List.class
    void when_createManipulatedQuery_then_returnQuery2() {
        // GIVEN
        final var expected           = "Query: { \"$and\" : [{ \"age\" : { \"$gt\" : 14}}]}, Fields: { \"firstname\" : 0, \"age\" : 0}, Sort: {}";
        final var saplConditions     = List.of(new SaplCondition("age", 22, OperatorMongoDB.SIMPLE_PROPERTY, null),
                new SaplCondition("firstname", "Juni", OperatorMongoDB.SIMPLE_PROPERTY, null));
        final var modifiedMethodName = "findAllByAgeAndFirstname";
        final var criteria           = new Criteria().andOperator(Criteria.where("age").gt(14));

        // WHEN
        saplConditionOperationMock.when(() -> SaplConditionOperation.jsonNodeToSaplConditions(any(ArrayNode.class)))
                .thenReturn(saplConditions);
        saplConditionOperationMock.when(() -> SaplConditionOperation.toModifiedMethodName(anyString(), any(List.class)))
                .thenReturn(modifiedMethodName);
        saplPartTreeCriteriaCreatorMock
                .when(() -> SaplPartTreeCriteriaCreator.create(any(List.class), any(PartTree.class)))
                .thenReturn(criteria);

        final var result = QueryCreation.createManipulatedQuery(conditions, selections, "findAllByFirstname",
                TestUser.class, new Object[] { "Juni", Sort.by(Sort.Direction.ASC, "age") });

        // THEN
        assertEquals(result.toString(), expected);
    }

}
