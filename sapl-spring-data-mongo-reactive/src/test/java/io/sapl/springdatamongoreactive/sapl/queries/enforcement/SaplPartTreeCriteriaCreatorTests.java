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
package io.sapl.springdatamongoreactive.sapl.queries.enforcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoWriter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.query.ConvertingParameterAccessor;
import org.springframework.data.mongodb.repository.query.MongoParameterAccessor;
import org.springframework.data.repository.query.parser.Part;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.springdatamongoreactive.sapl.OperatorMongoDB;
import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import io.sapl.springdatamongoreactive.sapl.utils.SaplCondition;
import io.sapl.springdatamongoreactive.sapl.utils.SaplConditionOperation;

class SaplPartTreeCriteriaCreatorTests {

    ReactiveMongoTemplate reactiveMongoTemplateMock = mock(ReactiveMongoTemplate.class);

    static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked") // generic types in arguments of methods that are mocked
    void when_policyDecisionContainsQueryManipulationConditions_then_createManipulatedQuery() {
        var saplConditionOperationMockedStatic = mockStatic(SaplConditionOperation.class);

        try (MockedConstruction<MongoQueryCreatorFactory> mockedConstruction = mockConstruction(
                MongoQueryCreatorFactory.class)) {

            // GIVEN
            var methodInvocation = new MethodInvocationForTesting("findAllByAgeBefore",
                    new ArrayList<>(List.of(int.class)), new ArrayList<>(List.of(40)), null);

            var criteria1         = Criteria.where("age").lt(40);
            var criteria1and2     = criteria1.and("id").is(10);
            var criteria1and2and3 = criteria1and2.and("firstname").is("Aaron");

            var mongoWriter = mock(MongoWriter.class);
            var delegate    = mock(MongoParameterAccessor.class);

            var conditions                     = MAPPER.createArrayNode();
            var expectedMethodSaplConditions   = new ArrayList<>(
                    List.of(new SaplCondition("age", 40, OperatorMongoDB.BEFORE, "And")));
            var expectedJsonNodeSaplConditions = new ArrayList<>(
                    List.of(new SaplCondition("id", 10, OperatorMongoDB.SIMPLE_PROPERTY, "And"),
                            new SaplCondition("firstname", "Aaron", OperatorMongoDB.SIMPLE_PROPERTY, "And")));
            var expectedResult                 = new Query()
                    .addCriteria(Criteria.where("age").lt(40).and("id").is(10).and("firstname").is("Aaron"))
                    .with(Sort.by(List.of()));

            var saplPartTreeCriteriaCreator = new SaplPartTreeCriteriaCreator<>(reactiveMongoTemplateMock,
                    methodInvocation, TestUser.class);

            var mongoQueryCreatorFactoryMock = mockedConstruction.constructed().get(0);

            // WHEN
            when(mongoQueryCreatorFactoryMock.create(any(Part.class), any(Iterator.class))).thenReturn(criteria1);
            when(mongoQueryCreatorFactoryMock.and(any(Part.class), eq(criteria1), any(Iterator.class)))
                    .thenReturn(criteria1and2);
            when(mongoQueryCreatorFactoryMock.and(any(Part.class), eq(criteria1and2), any(Iterator.class)))
                    .thenReturn(criteria1and2and3);
            when(mongoQueryCreatorFactoryMock.getConvertingParameterAccessor())
                    .thenReturn(new ConvertingParameterAccessor(mongoWriter, delegate));
            when(mongoQueryCreatorFactoryMock.getConvertingParameterAccessor().getSort())
                    .thenReturn(Sort.by(List.of()));
            saplConditionOperationMockedStatic.when(() -> SaplConditionOperation
                    .methodToSaplConditions(any(Object[].class), any(Method.class), any(Class.class)))
                    .thenReturn(expectedMethodSaplConditions);
            saplConditionOperationMockedStatic
                    .when(() -> SaplConditionOperation.toModifiedMethodName(anyString(), any(List.class)))
                    .thenReturn("findAllByAgeBeforeAndIdAndFirstname");
            saplConditionOperationMockedStatic
                    .when(() -> SaplConditionOperation.jsonNodeToSaplConditions(any(ArrayNode.class)))
                    .thenReturn(expectedJsonNodeSaplConditions);

            var actualResult = saplPartTreeCriteriaCreator.createManipulatedQuery(conditions);

            // THEN
            assertEquals(actualResult.toString(), expectedResult.toString());

            verify(mongoQueryCreatorFactoryMock, times(1)).create(any(Part.class), any(Iterator.class));
            verify(mongoQueryCreatorFactoryMock, times(2)).and(any(Part.class), any(Criteria.class),
                    any(Iterator.class));
            saplConditionOperationMockedStatic.verify(() -> SaplConditionOperation
                    .methodToSaplConditions(any(Object[].class), any(Method.class), any(Class.class)), times(1));
            saplConditionOperationMockedStatic
                    .verify(() -> SaplConditionOperation.toModifiedMethodName(anyString(), any(List.class)), times(1));
            saplConditionOperationMockedStatic
                    .verify(() -> SaplConditionOperation.jsonNodeToSaplConditions(any(ArrayNode.class)), times(1));
        }
        saplConditionOperationMockedStatic.close();
    }

    @Test
    @SuppressWarnings("unchecked") // generic types in arguments of methods that are mocked
    void when_policyDecisionContainsQueryManipulationWithOrCondition_then_createManipulatedQuery() {
        var saplConditionOperationMockedStatic = mockStatic(SaplConditionOperation.class);

        try (MockedConstruction<MongoQueryCreatorFactory> mockedConstruction = mockConstruction(
                MongoQueryCreatorFactory.class)) {

            // GIVEN
            var methodInvocation = new MethodInvocationForTesting("findAllByAgeBefore",
                    new ArrayList<>(List.of(int.class)), new ArrayList<>(List.of(40)), null);

            var criteria1      = Criteria.where("age").lt(40);
            var criteriaOrPart = new Criteria("firstname").is("Aaron");
            var critWithOr     = criteria1.orOperator(criteriaOrPart);

            var mongoWriter = mock(MongoWriter.class);
            var delegate    = mock(MongoParameterAccessor.class);

            var conditions                     = MAPPER.createArrayNode();
            var expectedMethodSaplConditions   = new ArrayList<>(
                    List.of(new SaplCondition("age", 40, OperatorMongoDB.BEFORE, "And")));
            var expectedJsonNodeSaplConditions = new ArrayList<>(
                    List.of(new SaplCondition("firstname", "Aaron", OperatorMongoDB.SIMPLE_PROPERTY, "Or")));
            var expectedResult                 = new Query()
                    .addCriteria(Criteria.where("age").lt(40).orOperator(criteriaOrPart)).with(Sort.by(List.of()));

            var saplPartTreeCriteriaCreator = new SaplPartTreeCriteriaCreator<>(reactiveMongoTemplateMock,
                    methodInvocation, TestUser.class);

            var mongoQueryCreatorFactoryMock = mockedConstruction.constructed().get(0);

            // WHEN
            when(mongoQueryCreatorFactoryMock.create(any(Part.class), any(Iterator.class))).thenReturn(criteria1);
            when(mongoQueryCreatorFactoryMock.or(eq(criteria1), any(Criteria.class))).thenReturn(critWithOr);
            when(mongoQueryCreatorFactoryMock.getConvertingParameterAccessor())
                    .thenReturn(new ConvertingParameterAccessor(mongoWriter, delegate));
            when(mongoQueryCreatorFactoryMock.getConvertingParameterAccessor().getSort())
                    .thenReturn(Sort.by(List.of()));
            saplConditionOperationMockedStatic.when(() -> SaplConditionOperation
                    .methodToSaplConditions(any(Object[].class), any(Method.class), any(Class.class)))
                    .thenReturn(expectedMethodSaplConditions);
            saplConditionOperationMockedStatic
                    .when(() -> SaplConditionOperation.toModifiedMethodName(anyString(), any(List.class)))
                    .thenReturn("findAllByAgeBeforeOrFirstname");
            saplConditionOperationMockedStatic
                    .when(() -> SaplConditionOperation.jsonNodeToSaplConditions(any(ArrayNode.class)))
                    .thenReturn(expectedJsonNodeSaplConditions);

            var actualResult = saplPartTreeCriteriaCreator.createManipulatedQuery(conditions);

            // THEN
            assertEquals(actualResult.toString(), expectedResult.toString());

            verify(mongoQueryCreatorFactoryMock, times(2)).create(any(Part.class), any(Iterator.class));
            verify(mongoQueryCreatorFactoryMock, times(1)).or(any(Criteria.class), any(Criteria.class));
            saplConditionOperationMockedStatic.verify(() -> SaplConditionOperation
                    .methodToSaplConditions(any(Object[].class), any(Method.class), any(Class.class)), times(1));
            saplConditionOperationMockedStatic
                    .verify(() -> SaplConditionOperation.toModifiedMethodName(anyString(), any(List.class)), times(1));
            saplConditionOperationMockedStatic
                    .verify(() -> SaplConditionOperation.jsonNodeToSaplConditions(any(ArrayNode.class)), times(1));
        }
        saplConditionOperationMockedStatic.close();
    }

}
