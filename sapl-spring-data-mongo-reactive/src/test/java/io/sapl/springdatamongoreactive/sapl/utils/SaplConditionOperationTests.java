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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.springdatamongoreactive.sapl.OperatorMongoDB;

class SaplConditionOperationTests {

    static final ObjectMapper MAPPER = new ObjectMapper();
    static JsonNode           mongoQueryManipulation;
    static JsonNode           mongoQueryManipulationOrPart;
    static JsonNode           conditions;
    static JsonNode           conditionsWithOrPart;
    static JsonNode           notValidConditions;

    @BeforeAll
    public static void setUp() throws JsonProcessingException {
        mongoQueryManipulation       = MAPPER.readTree(
                "{\"type\":\"mongoQueryManipulation\",\"conditions\":[\"{'age': {'gt': 30 }}\", \"{'firstname':  {'$in': ['Cathrin', 'Aaron']}}\"]}");
        mongoQueryManipulationOrPart = MAPPER.readTree(
                "{\"type\":\"mongoQueryManipulation\",\"conditions\":[\"{ 'age' : { '$lt' : 40}, '$or' : [{ 'firstname' : {'$eq': 'Aaron'}}]}\"]}");
        conditions                   = mongoQueryManipulation.get("conditions");
        conditionsWithOrPart         = mongoQueryManipulationOrPart.get("conditions");
        notValidConditions           = MAPPER.readTree("[\"{'fieldNotValid': {'gt': 30 }}\"]");
    }

    @Test
    void when_saplConditionsCanBeGeneratedFromJsonNodeWithOrPart_then_jsonNodeToSaplConditions() {
        // GIVEN
        ArrayList<SaplCondition> expected = new ArrayList<>();
        expected.add(new SaplCondition("age", 40, OperatorMongoDB.LESS_THAN, "And"));
        expected.add(new SaplCondition("firstname", "Aaron", OperatorMongoDB.SIMPLE_PROPERTY, "or"));

        // WHEN
        var actualSaplConditions = SaplConditionOperation.jsonNodeToSaplConditions(conditionsWithOrPart);

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
        var actualSaplConditions = SaplConditionOperation.jsonNodeToSaplConditions(conditions);

        // THEN
        assertTwoSaplConditions(actualSaplConditions.get(0), expected.get(0));
        assertTwoSaplConditions(actualSaplConditions.get(1), expected.get(1));
    }

    @Test
    void when_jsonNodeIsEmpty_then_returnEmptySaplConditionsList() {
        // GIVEN
        ArrayList<SaplCondition> expected = new ArrayList<>();

        // WHEN
        var actualSaplConditions = SaplConditionOperation.jsonNodeToSaplConditions(JsonNodeFactory.instance.nullNode());

        // THEN
        assertEquals(actualSaplConditions, expected);
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
