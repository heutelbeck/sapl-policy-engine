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
package io.sapl.springdatamongoreactive.sapl.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.springdatamongoreactive.sapl.OperatorMongoDB;

class SaplConditionOperationTests {

    static final ObjectMapper MAPPER           = new ObjectMapper();
    static JsonNode           MONGO_QUERY_MANIPULATION;
    static JsonNode           MONGO_QUERY_MANIPULATION_OR_PART;
    static JsonNode           MONGO_QUERY_MANIPULATION_WITH_WRONG_CONDITIONS_ARRAY;
    static ArrayNode          CONDITIONS;
    static ArrayNode          CONDITIONS_WITH_OR_PART;
    static JsonNode           NOT_VALID_CONDITIONS;
    static final ArrayNode    EMPTY_ARRAY_NODE = MAPPER.createArrayNode();

    @BeforeAll
    public static void setUp() throws JsonProcessingException {
        MONGO_QUERY_MANIPULATION                             = MAPPER.readTree("""
                 		{
                  "type": "mongoQueryManipulation",
                  "conditions": [
                        "{'age': {'gt': 30 }}",
                        "{'firstname':  {'$in': ['Cathrin', 'Aaron']}}"
                    ]
                }
                 		""");
        MONGO_QUERY_MANIPULATION_OR_PART                     = MAPPER.readTree("""
                	{
                  "type": "mongoQueryManipulation",
                  "conditions": [
                    "{ 'age' : { '$lt' : 40}, '$or' : [{ 'firstname' : {'$eq': 'Aaron'}}]}"
                  ]
                }
                	""");
        CONDITIONS                                           = MAPPER.readValue("""
                   [
                 "{'age': {'gt': 30 }}",
                 "{'firstname':  {'$in': ['Cathrin', 'Aaron']}}"
                ]
                """, ArrayNode.class);
        CONDITIONS_WITH_OR_PART                              = MAPPER.readValue("""
                 [
                        "{ 'age' : { '$lt' : 40}, '$or' : [{ 'firstname' : {'$eq': 'Aaron'}}]}"
                     ]
                """, ArrayNode.class);
        NOT_VALID_CONDITIONS                                 = MAPPER.readValue("""
                	[
                  "{'fieldNotValid': {'gt': 30 }}"
                ]
                	""", ArrayNode.class);
        MONGO_QUERY_MANIPULATION_WITH_WRONG_CONDITIONS_ARRAY = MAPPER.readTree(
                """
                        	[
                                   "{ 'age' : { '$lt' : 40}, '$or' : [{ 'firstname': 'Aaron'}], 'id': {'$in': [123, 456, 789]}}}",
                                   "{ 'firstname' : { '$in': ['Cecar', 'Dolf']}, '$or' : [{ 'age': 124}]}"
                                 ]
                        """);
    }

    @Test
    void when_saplConditionsCanBeGeneratedFromJsonNodeWithOrPart_then_jsonNodeToSaplConditions() {
        // GIVEN
        ArrayList<SaplCondition> expected = new ArrayList<>();
        expected.add(new SaplCondition("age", 40, OperatorMongoDB.LESS_THAN, "And"));
        expected.add(new SaplCondition("firstname", "Aaron", OperatorMongoDB.SIMPLE_PROPERTY, "or"));

        // WHEN
        var actualSaplConditions = SaplConditionOperation.jsonNodeToSaplConditions(CONDITIONS_WITH_OR_PART);

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
        var actualSaplConditions = SaplConditionOperation.jsonNodeToSaplConditions(CONDITIONS);

        // THEN
        assertTwoSaplConditions(actualSaplConditions.get(0), expected.get(0));
        assertTwoSaplConditions(actualSaplConditions.get(1), expected.get(1));
    }

    @Test
    void when_conditionsAreEmpty_then_returnEmptySaplConditionList() {
        // GIVEN
        ArrayList<SaplCondition> expected = new ArrayList<>();

        // WHEN
        var actualSaplConditions = SaplConditionOperation.jsonNodeToSaplConditions(EMPTY_ARRAY_NODE);

        // THEN
        assertEquals(expected, actualSaplConditions);
    }

    @Test
    void when_jsonNodeIsEmpty_then_returnEmptySaplConditionsList() {
        // GIVEN
        ArrayList<SaplCondition> expected = new ArrayList<>();

        // WHEN
        var actualSaplConditions = SaplConditionOperation.jsonNodeToSaplConditions(EMPTY_ARRAY_NODE);

        // THEN
        assertEquals(actualSaplConditions, expected);
    }

    @Test
    void when_jsonNodeIsHasErrorInConditions_then_returnEmptySaplConditionsList() {
        // GIVEN
        ArrayList<SaplCondition> expectedSaplConditions = new ArrayList<>();
        expectedSaplConditions.add(new SaplCondition("age", 40, OperatorMongoDB.LESS_THAN, "And"));
        expectedSaplConditions.add(new SaplCondition("firstname", "Aaron", OperatorMongoDB.SIMPLE_PROPERTY, "Or"));
        expectedSaplConditions
                .add(new SaplCondition("id", new ArrayList<>(List.of(123, 456, 789)), OperatorMongoDB.IN, "And"));
        expectedSaplConditions.add(
                new SaplCondition("firstname", new ArrayList<>(List.of("Cecar", "Dolf")), OperatorMongoDB.IN, "And"));
        expectedSaplConditions.add(new SaplCondition("age", 124, OperatorMongoDB.SIMPLE_PROPERTY, "Or"));

        // WHEN
        var actualSaplConditions = SaplConditionOperation
                .jsonNodeToSaplConditions(MONGO_QUERY_MANIPULATION_WITH_WRONG_CONDITIONS_ARRAY);

        // THEN
        assertTwoSaplConditions(actualSaplConditions.get(0), expectedSaplConditions.get(0));
        assertTwoSaplConditions(actualSaplConditions.get(1), expectedSaplConditions.get(1));
        assertTwoSaplConditions(actualSaplConditions.get(2), expectedSaplConditions.get(2));
        assertTwoSaplConditions(actualSaplConditions.get(3), expectedSaplConditions.get(3));
        assertTwoSaplConditions(actualSaplConditions.get(4), expectedSaplConditions.get(4));
    }

    @Test
    void when_methodNameCanBeModified_then_toModifiedMethodName() {
        // GIVEN
        ArrayList<SaplCondition> saplConditions = new ArrayList<>();
        saplConditions.add(new SaplCondition("age", 30, OperatorMongoDB.GREATER_THAN, "And"));
        saplConditions.add(new SaplCondition("firstname", new ArrayList<>(List.of("Cathrin", "Aaron")),
                OperatorMongoDB.IN, "and"));
        var expectedMethodName = "findAllByIdAndAgeIsGreaterThanAndFirstnameIsIn";

        // WHEN
        var actualMethodName = SaplConditionOperation.toModifiedMethodName("findAllById", saplConditions);

        // THEN
        assertEquals(expectedMethodName, actualMethodName);
    }

    @Test
    void when_methodNameCanBeModifiedAndContainsKeyword_then_toModifiedMethodName() {
        // GIVEN
        ArrayList<SaplCondition> saplConditions = new ArrayList<>();
        saplConditions.add(new SaplCondition("age", 30, OperatorMongoDB.GREATER_THAN, "And"));
        saplConditions.add(
                new SaplCondition("firstname", new ArrayList<>(List.of("Cathrin", "Aaron")), OperatorMongoDB.IN, null));
        var expectedMethodName = "findAllByIdAndAgeIsGreaterThanAndFirstnameIsInOrderByAge";

        // WHEN
        var actualMethodName = SaplConditionOperation.toModifiedMethodName("findAllByIdOrderByAge", saplConditions);

        // THEN
        assertEquals(expectedMethodName, actualMethodName);
    }

    private void assertTwoSaplConditions(SaplCondition first, SaplCondition second) {
        assertEquals(first.field(), second.field());
        assertEquals(first.value(), second.value());
        assertEquals(first.operator(), second.operator());
        assertEquals(first.conjunction(), second.conjunction());
    }

}
