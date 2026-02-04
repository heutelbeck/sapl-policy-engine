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
package io.sapl.spring.data.mongo.queries;

import io.sapl.spring.data.mongo.sapl.database.MethodInvocationForTesting;
import io.sapl.spring.data.mongo.sapl.database.TestUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.commons.util.ReflectionUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SaplConditionOperationTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode mongoQueryManipulation;
    private static JsonNode mongoQueryManipulationOrPart;
    private static JsonNode conditions;
    private static JsonNode conditionsWithOrPart;

    @BeforeAll
    static void setUp() throws JacksonException {
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
        assertThat(actualSaplConditions).isEqualTo(expected);
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
        assertThat(actualMethodName).isEqualTo(methodNameManipulated);
    }

    private static Stream<Arguments> modifyMethodNameArguments() {
        return Stream.of(Arguments.of("and", "findAllByIdAndAgeIsGreaterThanAndFirstnameIsIn", "findAllById"),
                Arguments.of(null, "findAllByIdAndAgeIsGreaterThanAndFirstnameIsInOrderByAge", "findAllByIdOrderByAge"),
                Arguments.of(null, "findAllByAgeIsGreaterThanAndFirstnameIsIn", "findAll"));
    }

    private void assertTwoSaplConditions(SaplCondition first, SaplCondition second) {
        assertThat(first.field()).isEqualTo(second.field());
        assertThat(first.value()).isEqualTo(second.value());
        assertThat(first.operator()).isEqualTo(second.operator());
        assertThat(first.conjunction()).isEqualTo(second.conjunction());
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
        assertThat(actualResult).isEqualTo(expectedResult);
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
        assertThatThrownBy(() -> SaplConditionOperation.methodToSaplConditions(args, method, TestUser.class))
                .isInstanceOf(ArrayIndexOutOfBoundsException.class);
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
        final var constructor = SaplConditionOperation.class.getDeclaredConstructors()[0];
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
        ReflectionUtils.makeAccessible(constructor);
        assertThatThrownBy(() -> constructor.newInstance()).isInstanceOf(InvocationTargetException.class);
    }

}
