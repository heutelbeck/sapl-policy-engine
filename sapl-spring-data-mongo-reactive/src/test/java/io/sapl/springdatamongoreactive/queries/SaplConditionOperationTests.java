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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.commons.util.ReflectionUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;

class SaplConditionOperationTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode mongoQueryManipulation;
    private static JsonNode mongoQueryManipulationOrPart;
    private static JsonNode conditions;
    private static JsonNode conditionsWithOrPart;

    @BeforeAll
    public static void setUp() throws JsonProcessingException {
        mongoQueryManipulation       = MAPPER.readTree("""
                    		{
                  "type": "mongoQueryManipulation",
                  "conditions": [
                    "{'age': {'gt': 30 }}",
                    "{'firstname':  {'$in': ['Cathrin', 'Aaron']}}"
                  ]
                }
                    		""");
        mongoQueryManipulationOrPart = MAPPER.readTree("""
                    		{
                  "type": "mongoQueryManipulation",
                  "conditions": [
                    "{ 'age' : { '$lt' : 40}, '$or' : [{ 'firstname' : {'$eq': 'Aaron'}}]}"
                  ]
                }
                    		""");
        conditions                   = mongoQueryManipulation.get("conditions");
        conditionsWithOrPart         = mongoQueryManipulationOrPart.get("conditions");
    }

    @Test
    void when_saplConditionsCanBeGeneratedFromJsonNodeWithOrPart_then_jsonNodeToSaplConditions() {
        // GIVEN
        ArrayList<SaplCondition> expected = new ArrayList<>();
        expected.add(new SaplCondition("age", 40, OperatorMongoDB.LESS_THAN, "And"));
        expected.add(new SaplCondition("firstname", "Aaron", OperatorMongoDB.SIMPLE_PROPERTY, "or"));

        // WHEN
        final var actualSaplConditions = SaplConditionOperation.jsonNodeToSaplConditions(conditionsWithOrPart);

        // THEN
        assertTwoSaplConditions(actualSaplConditions.get(0), expected.get(0));
        assertTwoSaplConditions(actualSaplConditions.get(1), expected.get(1));
    }

    @Test
    void when_saplConditionsCanBeGeneratedFromJsonNodeInOperator_then_jsonNodeToSaplConditions() {
        // GIVEN
        ArrayList<SaplCondition> expected = new ArrayList<>();
        expected.add(new SaplCondition("age", 30, OperatorMongoDB.GREATER_THAN, "And"));
        expected.add(new SaplCondition("firstname", List.of("Cathrin", "Aaron"), OperatorMongoDB.IN, "and"));

        // WHEN
        final var actualSaplConditions = SaplConditionOperation.jsonNodeToSaplConditions(conditions);

        // THEN
        assertTwoSaplConditions(actualSaplConditions.get(0), expected.get(0));
        assertTwoSaplConditions(actualSaplConditions.get(1), expected.get(1));
    }

    @Test
    void when_jsonNodeIsEmpty_then_returnEmptySaplConditionsList() {
        // GIVEN
        ArrayList<SaplCondition> expected = new ArrayList<>();

        // WHEN
        final var actualSaplConditions = SaplConditionOperation
                .jsonNodeToSaplConditions(JsonNodeFactory.instance.nullNode());

        // THEN
        assertEquals(actualSaplConditions, expected);
    }

    @ParameterizedTest
    @MethodSource("modifyMethodNameArguments")
    void when_methodNameCanBeModified_then_toModifiedMethodName(String logicOperator, String methodNameManipulated,
            String originalMethodName) {
        // GIVEN
        ArrayList<SaplCondition> saplConditions = new ArrayList<>();
        saplConditions.add(new SaplCondition("age", 30, OperatorMongoDB.GREATER_THAN, "And"));
        saplConditions.add(new SaplCondition("firstname", new ArrayList<>(List.of("Cathrin", "Aaron")),
                OperatorMongoDB.IN, logicOperator));

        // WHEN
        final var actualMethodName = SaplConditionOperation.toModifiedMethodName(originalMethodName, saplConditions);

        // THEN
        assertEquals(methodNameManipulated, actualMethodName);
    }

    private static Stream<Arguments> modifyMethodNameArguments() {
        return Stream.of(Arguments.of("and", "findAllByIdAndAgeIsGreaterThanAndFirstnameIsIn", "findAllById"),
                Arguments.of(null, "findAllByIdAndAgeIsGreaterThanAndFirstnameIsInOrderByAge", "findAllByIdOrderByAge"),
                Arguments.of(null, "findAllByAgeIsGreaterThanAndFirstnameIsIn", "findAll"));
    }

    private void assertTwoSaplConditions(SaplCondition first, SaplCondition second) {
        assertEquals(first.field(), second.field());
        assertEquals(first.value(), second.value());
        assertEquals(first.operator(), second.operator());
        assertEquals(first.conjunction(), second.conjunction());
    }

    @Test
    void when_methodIsQueryMethodButNoPartsCanBeCreatedByMethodName_then_returnEmptySaplConditionList() {
        // GIVEN
        final var methodInvocation = new MethodInvocationForTesting("findAllBy", new ArrayList<>(List.of()),
                new ArrayList<>(List.of()), null);
        final var method           = methodInvocation.getMethod();
        final var args             = methodInvocation.getArguments();

        final var expectedResult = new ArrayList<>();

        // WHEN
        final var actualResult = SaplConditionOperation.methodToSaplConditions(args, method, TestUser.class);

        // THEN
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void when_methodIsQueryMethodButParametersDontFit_then_throwArrayIndexOutOfBoundsException() {
        // GIVEN
        final var methodInvocation = new MethodInvocationForTesting("findAllByFirstnameAndAgeBefore",
                new ArrayList<>(List.of(String.class, int.class)), new ArrayList<>(List.of("Aaron")), null);
        final var method           = methodInvocation.getMethod();
        final var args             = methodInvocation.getArguments();

        // WHEN

        // THEN
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> SaplConditionOperation.methodToSaplConditions(args, method, TestUser.class));
    }

    @Test
    void when_methodIsQueryMethod_then_convertToSaplConditions() {
        // GIVEN
        final var methodInvocation = new MethodInvocationForTesting("findAllByFirstnameAndAgeBefore",
                new ArrayList<>(List.of(String.class, int.class)), new ArrayList<>(List.of("Aaron", 22)), null);
        final var method           = methodInvocation.getMethod();
        final var args             = methodInvocation.getArguments();
        final var expectedResult   = List.of(
                new SaplCondition("firstname", "Aaron", OperatorMongoDB.SIMPLE_PROPERTY, "And"),
                new SaplCondition("age", 22, OperatorMongoDB.BEFORE, "And"));

        // WHEN
        final var actualResult = SaplConditionOperation.methodToSaplConditions(args, method, TestUser.class);

        // THEN
        for (int i = 0; i < actualResult.size(); i++) {
            assertTwoSaplConditions(expectedResult.get(i), actualResult.get(i));
        }
    }

    @Test
    void when_classIsStaticUtilityClass_then_instantiateThisTestForCoverageReasonsOfConstructor() {
        assertThrows(InvocationTargetException.class, () -> {
            final var constructor = SaplConditionOperation.class.getDeclaredConstructor();
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
            ReflectionUtils.makeAccessible(constructor);
            constructor.newInstance();
        });
    }

}
