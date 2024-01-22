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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.springdatacommon.handlers.QueryManipulationObligationProvider;

public class QueryManipulationObligationProviderTest {

    final static ObjectMapper MAPPER           = new ObjectMapper();
    final static String       R2DBC_QUERY_TYPE = "r2dbcQueryManipulation";
    static ArrayNode          OBLIGATIONS;
    static JsonNode           MONGO_QUERY_MANIPULATION;
    static ArrayNode          QUERY_MANIPULATION_WITHOUT_OBLIGATION;
    static JsonNode           WRONG_MONGO_QUERY_MANIPULATION;
    static JsonNode           MONGO_QUERY_MANIPULATION_CONDITIONS_ARE_NO_ARRAY_NODE;

    final JsonNode                            nullNode = JsonNodeFactory.instance.nullNode();
    final QueryManipulationObligationProvider provider = new QueryManipulationObligationProvider();

    @BeforeAll
    public static void initBeforeAll() throws JsonProcessingException {
        OBLIGATIONS                                           = MAPPER.readValue("""
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
        MONGO_QUERY_MANIPULATION                              = MAPPER.readTree("""
                    		{
                  "type": "r2dbcQueryManipulation",
                  "conditions": [
                  			"role IN ('USER')"
                  		]
                }
                						""");
        MONGO_QUERY_MANIPULATION_CONDITIONS_ARE_NO_ARRAY_NODE = MAPPER.readTree("""
                    		{
                  "type": "r2dbcQueryManipulation",
                  "conditions": "role IN ('USER')"
                }
                """);

        QUERY_MANIPULATION_WITHOUT_OBLIGATION = MAPPER.readValue("""
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

        WRONG_MONGO_QUERY_MANIPULATION = MAPPER.readTree("""
                    		{
                  "type": "r2dbcQueryManipulation",
                  "wrongName": [
                	    "role IN ('USER')"
                	  ]
                }
                    		""");
    }

    @Test
    void when_obligationContainsConditions_then_getConditions() {
        // GIVEN
        var expectedCondition = "role IN ('USER')";

        // WHEN
        var condition = provider.getConditions(MONGO_QUERY_MANIPULATION);

        // THEN
        assertEquals(condition.get(0).asText(), expectedCondition);
    }

    @Test
    void when_obligationContainsNotCorrectStructuredConditions_then_returnNullNode() throws JsonProcessingException {
        // GIVEN

        // WHEN
        var conditionsResult = provider.getConditions(WRONG_MONGO_QUERY_MANIPULATION);

        // THEN
        assertEquals(conditionsResult, MAPPER.createArrayNode());
    }

    @Test
    void when_obligationsContainMongoQueryManipulationObligation_then_getObligation() {
        // GIVEN

        // WHEN
        var mongoQueryManipulationObligationResult = provider.getObligation(OBLIGATIONS, R2DBC_QUERY_TYPE);

        // THEN
        assertEquals(mongoQueryManipulationObligationResult, MONGO_QUERY_MANIPULATION);
    }

    @Test
    void when_obligationsContainNoMongoQueryManipulationObligation_then_returnNullNode()
            throws JsonProcessingException {
        // GIVEN

        // WHEN
        var mongoQueryManipulationObligationResult = provider.getObligation(QUERY_MANIPULATION_WITHOUT_OBLIGATION,
                R2DBC_QUERY_TYPE);

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
    void when_obligationsContainMongoQueryManipulationObligation_then_isNotResponsible()
            throws JsonProcessingException {
        // GIVEN

        // WHEN
        var mongoQueryManipulationObligationResult = provider.isResponsible(QUERY_MANIPULATION_WITHOUT_OBLIGATION,
                R2DBC_QUERY_TYPE);

        // THEN
        assertFalse(mongoQueryManipulationObligationResult);
    }

}
