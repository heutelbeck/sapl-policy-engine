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
package io.sapl.springdatacommon.sapl.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.springdatacommon.handlers.QueryManipulationObligationProvider;

class QueryManipulationObligationProviderTests {

    final static ObjectMapper MAPPER           = new ObjectMapper();
    final static String       R2DBC_QUERY_TYPE = "r2dbcQueryManipulation";
    static ArrayNode          OBLIGATIONS;
    static JsonNode           R2DBC_QUERY_MANIPULATION;
    static ArrayNode          QUERY_MANIPULATION_WITHOUT_OBLIGATION;
    static JsonNode           WRONG_R2DBC_QUERY_MANIPULATION_KEY;
    static JsonNode           R2DBC_QUERY_MANIPULATION_CONDITION_IS_NO_ARRAY;
    static JsonNode           R2DBC_QUERY_MANIPULATION_HAS_NO_CONDITION_KEY;
    static JsonNode           R2DBC_QUERY_MANIPULATION_CONDITION_IS_EMPTY;
    static JsonNode           R2DBC_QUERY_MANIPULATION_TYPE_IS_NULL_1;
    static JsonNode           R2DBC_QUERY_MANIPULATION_TYPE_IS_NO_OBJECT;

    static ArrayNode EMPTY_ARRAY_NODE = MAPPER.createArrayNode();

    final JsonNode                            nullNode = JsonNodeFactory.instance.nullNode();
    final QueryManipulationObligationProvider provider = new QueryManipulationObligationProvider();

    @BeforeAll
    public static void initBeforeAll() throws JsonProcessingException {
        OBLIGATIONS                                    = MAPPER.readValue("""
                [
                  {
                    "type": "r2dbcQueryManipulation",
                    "conditions": [
                    		"role IN ('USER')"
                    	]
                  },
                  {
                    "type": "filterJsonContent",
                    "actions": [
                      {
                        "type": "blacken",
                        "path": "$.firstname",
                        "discloseLeft": 2
                      }
                    ]
                  },
                  {
                    "type": "jsonContentFilterPredicate",
                    "conditions": [
                      {
                        "type": "==",
                        "path": "$.id",
                        "value": "a1"
                      }
                    ]
                  }
                ]
                					""", ArrayNode.class);
        R2DBC_QUERY_MANIPULATION                       = MAPPER.readTree("""
                    		{
                  "type": "r2dbcQueryManipulation",
                  "conditions": [
                  			"role IN ('USER')"
                  		]
                }
                						""");
        QUERY_MANIPULATION_WITHOUT_OBLIGATION          = MAPPER.readValue("""
                						[
                  {
                    "type": "filterJsonContent",
                    "actions": [
                      {
                        "type": "blacken",
                        "path": "$.firstname",
                        "discloseLeft": 2
                      }
                    ]
                  },
                  {
                    "type": "jsonContentFilterPredicate",
                    "conditions": [
                      {
                        "type": "==",
                        "path": "$.id",
                        "value": "a1"
                      }
                    ]
                  }
                ]
                     		""", ArrayNode.class);
        WRONG_R2DBC_QUERY_MANIPULATION_KEY             = MAPPER.readTree("""
                    		{
                  "type": "r2dbcQueryManipulation",
                  "wrongName": [
                	    "role IN ('USER')"
                	  ]
                }
                    		""");
        R2DBC_QUERY_MANIPULATION_CONDITION_IS_NO_ARRAY = MAPPER.readTree("""
                 		{
                  "type": "r2dbcQueryManipulation",
                  "conditions":  "role IN ('USER')"
                }
                 		""");
        R2DBC_QUERY_MANIPULATION_CONDITION_IS_EMPTY    = MAPPER.readTree("""
                 		{
                  "type": "r2dbcQueryManipulation",
                  "conditions":  []
                }
                 		""");
        R2DBC_QUERY_MANIPULATION_HAS_NO_CONDITION_KEY  = MAPPER.readTree("""
                 		{
                  "type": "r2dbcQueryManipulation"
                }
                 		""");
        R2DBC_QUERY_MANIPULATION_TYPE_IS_NULL_1        = MAPPER.readTree("""
                 		{
                  "type": {
                  		"asd": 123
                  },
                  "conditions": 123
                }
                 		""");
        R2DBC_QUERY_MANIPULATION_TYPE_IS_NO_OBJECT     = MAPPER.readTree("""
                 		[
                 		  "Test"
                ]
                 		""");
    }

    @Test
    void when_obligationContainsConditions_then_getConditions() {
        // GIVEN
        var expectedCondition = "role IN ('USER')";

        // WHEN
        var condition = provider.getConditions(R2DBC_QUERY_MANIPULATION);

        // THEN
        assertEquals(condition.get(0).asText(), expectedCondition);
    }

    @Test
    void when_obligationContainsConditionsButIsNoArray_then_returnEmptyArray() {
        // GIVEN

        // WHEN
        var condition = provider.getConditions(R2DBC_QUERY_MANIPULATION_CONDITION_IS_NO_ARRAY);

        // THEN
        assertEquals(condition, EMPTY_ARRAY_NODE);
    }

    @Test
    void when_obligationContainsConditionsIsEmpty_then_returnEmptyArray() {
        // GIVEN

        // WHEN
        var condition = provider.getConditions(R2DBC_QUERY_MANIPULATION_CONDITION_IS_EMPTY);

        // THEN
        assertEquals(condition, EMPTY_ARRAY_NODE);
    }

    @Test
    void when_obligationContainsNoConditionsAtAll_then_returnEmptyArray() {
        // GIVEN

        // WHEN
        var condition = provider.getConditions(R2DBC_QUERY_MANIPULATION_HAS_NO_CONDITION_KEY);

        // THEN
        assertEquals(condition, EMPTY_ARRAY_NODE);
    }

    @Test
    void when_obligationContainsNotCorrectStructuredConditions_then_returnEmptyArray() {
        // GIVEN

        // WHEN
        var conditionsResult = provider.getConditions(WRONG_R2DBC_QUERY_MANIPULATION_KEY);

        // THEN
        assertEquals(conditionsResult, MAPPER.createArrayNode());
    }

    @Test
    void when_obligationsContainMongoQueryManipulationObligation_then_getObligation() {
        // GIVEN

        // WHEN
        var mongoQueryManipulationObligationResult = provider.getObligation(OBLIGATIONS, R2DBC_QUERY_TYPE);

        // THEN
        assertEquals(mongoQueryManipulationObligationResult, R2DBC_QUERY_MANIPULATION);
    }

    @Test
    void when_obligationsContainsNullNode_then_returnNullNode() {
        // GIVEN
        var arrayNode = MAPPER.createArrayNode();
        arrayNode.add(nullNode);

        // WHEN
        var mongoQueryManipulationObligationResult = provider.getObligation(arrayNode, R2DBC_QUERY_TYPE);

        // THEN
        assertEquals(mongoQueryManipulationObligationResult, nullNode);
    }

    @Test
    void when_obligationsContainsNoObject_then_returnNullNode() {
        // GIVEN
        var arrayNode = MAPPER.createArrayNode();

        arrayNode.add("123123");

        // WHEN
        var mongoQueryManipulationObligationResult = provider.getObligation(arrayNode, R2DBC_QUERY_TYPE);

        // THEN
        assertEquals(mongoQueryManipulationObligationResult, nullNode);
    }

    @Test
    void when_obligationsTypeIsNull_then_returnNullNode() {
        // GIVEN

        // WHEN
        var mongoQueryManipulationObligationResult = provider.getObligation(R2DBC_QUERY_MANIPULATION_TYPE_IS_NULL_1,
                R2DBC_QUERY_TYPE);

        // THEN
        assertEquals(mongoQueryManipulationObligationResult, nullNode);
    }

    @Test
    void when_obligationsTypeIsNotTextual_then_returnNullNode() {
        // GIVEN
        var arrayNode = MAPPER.createArrayNode();
        var test      = MAPPER.createObjectNode();
        test.put("type", 123);
        arrayNode.add(test);

        // WHEN
        var mongoQueryManipulationObligationResult = provider.getObligation(arrayNode, R2DBC_QUERY_TYPE);

        // THEN
        assertEquals(mongoQueryManipulationObligationResult, nullNode);
    }

    @Test
    void when_obligationsContainNoMongoQueryManipulationObligation_then_returnNullNode() {
        // GIVEN

        // WHEN
        var mongoQueryManipulationObligationResult = provider.getObligation(QUERY_MANIPULATION_WITHOUT_OBLIGATION,
                R2DBC_QUERY_TYPE);

        // THEN
        assertEquals(mongoQueryManipulationObligationResult, nullNode);
    }

    @Test
    void when_getObligationButObligationsContainsNoObject_then_returnNullNode() {
        // GIVEN
        var arrayNode  = MAPPER.createArrayNode();
        var arrayNode2 = MAPPER.createArrayNode();
        var test       = MAPPER.createObjectNode();
        test.set("type", arrayNode2);
        arrayNode.add(test);

        // WHEN
        var mongoQueryManipulationObligationResult = provider.getObligation(arrayNode, R2DBC_QUERY_TYPE);

        // THEN
        assertEquals(mongoQueryManipulationObligationResult, nullNode);
    }

    @Test
    void when_getObligationButObligationsIsEmpty_then_returnNullNode() {
        // GIVEN
        var arrayNode = MAPPER.createArrayNode();

        // WHEN
        var mongoQueryManipulationObligationResult = provider.getObligation(arrayNode, R2DBC_QUERY_TYPE);

        // THEN
        assertEquals(mongoQueryManipulationObligationResult, nullNode);
    }

    @Test
    void when_obligationsContainMongoQueryManipulationObligation_then_isResponsible() {
        // GIVEN

        // WHEN
        var mongoQueryManipulationObligationResult = provider.isResponsible(OBLIGATIONS, R2DBC_QUERY_TYPE);

        // THEN
        assertTrue(mongoQueryManipulationObligationResult);
    }

    @Test
    void when_isResponsibleButObligationsIsEmpty_then_returnFalse() {
        // GIVEN
        var arrayNode = MAPPER.createArrayNode();

        // WHEN
        var mongoQueryManipulationObligationResult = provider.isResponsible(arrayNode, R2DBC_QUERY_TYPE);

        // THEN
        assertFalse(mongoQueryManipulationObligationResult);
    }

    @Test
    void when_obligationsContainMongoQueryManipulationObligation_then_isNotResponsible() {
        // GIVEN

        // WHEN
        var mongoQueryManipulationObligationResult = provider.isResponsible(QUERY_MANIPULATION_WITHOUT_OBLIGATION,
                R2DBC_QUERY_TYPE);

        // THEN
        assertFalse(mongoQueryManipulationObligationResult);
    }

    @Test
    void when_isResponsibleButObligationsContainsNullNode_then_returnNullNode() {
        // GIVEN
        ArrayNode arrayNode = MAPPER.createArrayNode();
        arrayNode.add(nullNode);

        // WHEN
        var mongoQueryManipulationObligationResult = provider.isResponsible(arrayNode, R2DBC_QUERY_TYPE);

        // THEN
        assertFalse(mongoQueryManipulationObligationResult);
    }

    @Test
    void when_isResponsibleButObligationsContainsNoObject_then_returnNullNode() {
        // GIVEN
        ArrayNode arrayNode = MAPPER.createArrayNode();

        arrayNode.add("123123");

        // WHEN
        var mongoQueryManipulationObligationResult = provider.isResponsible(arrayNode, R2DBC_QUERY_TYPE);

        // THEN
        assertFalse(mongoQueryManipulationObligationResult);
    }

    @Test
    void when_isResponsibleButObligationsTypeIsNotTextual_then_returnNullNode() {
        // GIVEN

        // WHEN
        var mongoQueryManipulationObligationResult = provider.isResponsible(R2DBC_QUERY_MANIPULATION_TYPE_IS_NULL_1,
                R2DBC_QUERY_TYPE);

        // THEN
        assertFalse(mongoQueryManipulationObligationResult);
    }

    @Test
    void when_isResponsibleAndObligationIsNull_then_returnFalse() {
        // GIVEN
        ArrayNode arrayNode = MAPPER.createArrayNode();
        arrayNode.add(nullNode);

        // WHEN
        var mongoQueryManipulationObligationResult = provider.isResponsible(arrayNode, R2DBC_QUERY_TYPE);

        // THEN
        assertFalse(mongoQueryManipulationObligationResult);
    }

}
