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
package io.sapl.springdatacommon.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.springdatacommon.utils.TestUtils;

class QueryManipulationConstraintHandlerServiceTests {

    private static JsonNode     R2DBC_OBLIGATION;
    private static JsonNode     R2DBC_UNHANDABLE_OBLIGATION;
    private static JsonNode     MONGO_OBLIGATION;
    private static JsonNode     SELECTIONS;
    private static JsonNode     CONDITIONS;
    private static JsonNode     OBLIGATION_WITHOUT_SELECTION;
    private static JsonNode     R2DBC_OBLIGATION_WRONG_ALIAS;
    private static JsonNode     R2DBC_OBLIGATION_WRONG_TRANSFORMATIONS;
    private static ArrayNode    TRANSFORMATIONS;
    private static ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void initJsonNodes() throws JsonProcessingException {

        R2DBC_OBLIGATION = MAPPER.readTree("""
                	{
                          "type": "r2dbcQueryManipulation",
                          "conditions": [ "role = 'USER'" ],
                	      "selection": {
                				"type": "blacklist",
                				"columns": ["firstname"]
                			},
                			"transformations": {
                				"firstname": "UPPER"
                				},
                			"alias": "p"
                     }
                """);

        R2DBC_OBLIGATION_WRONG_ALIAS = MAPPER.readTree("""
                	{
                          "type": "r2dbcQueryManipulation",
                          "conditions": [ "role = 'USER'" ],
                	      "selection": {
                				"type": "blacklist",
                				"columns": ["firstname"]
                			},
                			"transformations": {
                				"firstname": "UPPER"
                				},
                			"alias": 0
                     }
                """);

        R2DBC_OBLIGATION_WRONG_TRANSFORMATIONS = MAPPER.readTree("""
                	{
                          "type": "r2dbcQueryManipulation",
                          "conditions": [ "role = 'USER'" ],
                	      "selection": {
                				"type": "blacklist",
                				"columns": ["firstname"]
                			},
                			"transformations": 23,
                			"alias": 0
                     }
                """);

        R2DBC_UNHANDABLE_OBLIGATION = MAPPER.readTree("""
                	{
                          "types": "r2dbcQueryManipulation",
                          "conditions": [ "role = 'USER'" ],
                	       "selection": {
                				"types": "blacklist",
                				"columns": ["firstname"]
                			}
                     }
                """);

        MONGO_OBLIGATION = MAPPER.readTree("""
                {
                  "type": "mongoQueryManipulation",
                  "conditions": [ "{'role': {'$eq': 'USER'}}" ],
                  "selection": {
                		"type": "blacklist",
                		"columns": ["firstname"]
                }
                         }
                """);

        SELECTIONS = MAPPER.readValue("""
                [
                	{
                		"type": "blacklist",
                		"columns": ["firstname"]
                	},
                	{
                		"type": "blacklist",
                		"columns": ["firstname"]
                	}
                ]
                """, ArrayNode.class);

        CONDITIONS = MAPPER.readValue("""
                [
                	"role = 'USER'",
                	"{'role': {'$eq': 'USER'}}"
                ]
                """, ArrayNode.class);

        OBLIGATION_WITHOUT_SELECTION = MAPPER.readTree("""
                {
                  "type": "mongoQueryManipulation",
                  "conditions": [ "{'role': {'$eq': 'USER'}}" ]
                }
                """);

        TRANSFORMATIONS = MAPPER.readValue("""
                [{"firstname":"UPPER"}]
                """, ArrayNode.class);
    }

    @Test
    void when_getSelections_then_returnSelectionsOfObligations() {
        // GIVEN
        var constraintData = List.of(
                new RecordConstraintData(ConstraintHandlerType.R2DBC_QUERY_MANIPULATION, R2DBC_OBLIGATION),
                new RecordConstraintData(ConstraintHandlerType.MONGO_QUERY_MANIPULATION, MONGO_OBLIGATION));

        var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN
        var result = handlerBundle.getSelections();

        // THEN
        assertEquals(result, SELECTIONS);
    }

    @Test
    void when_getSelections_then_throwAccessDeniedException() {
        // GIVEN
        var errorMessage = """
                		UnhandableObligation:
                		{
                				"types":"r2dbcQueryManipulation",
                				"conditions":["role='USER'"],
                				"selection":{
                					"types":"blacklist",
                					"columns":["firstname"]
                				}
                		}
                """;

        var constraintData = List.of(
                new RecordConstraintData(ConstraintHandlerType.R2DBC_QUERY_MANIPULATION, R2DBC_UNHANDABLE_OBLIGATION),
                new RecordConstraintData(ConstraintHandlerType.MONGO_QUERY_MANIPULATION, MONGO_OBLIGATION));

        var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN

        var accessDeniedException = assertThrows(AccessDeniedException.class, () -> {
            handlerBundle.getSelections();
        });

        // THEN
        assertEquals(TestUtils.removeWhitespace(errorMessage),
                TestUtils.removeWhitespace(accessDeniedException.getMessage()));
    }

    @Test
    void when_getConditions_then_returnEmptyArrayNode() {
        // GIVEN
        var constraintData = new ArrayList<RecordConstraintData>();

        var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN
        var result = handlerBundle.getConditions();

        // THEN
        assertEquals(result, MAPPER.createArrayNode());
    }

    @Test
    void when_getSelections_then_returnEmptyArrayNode1() {
        // GIVEN
        var constraintData = new ArrayList<RecordConstraintData>();

        var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN
        var result = handlerBundle.getSelections();

        // THEN
        assertEquals(result, MAPPER.createArrayNode());
    }

    @Test
    void when_getSelections_then_returnEmptyArrayNode2() {
        // GIVEN
        var constraintData = List.of(
                new RecordConstraintData(ConstraintHandlerType.R2DBC_QUERY_MANIPULATION, OBLIGATION_WITHOUT_SELECTION));

        var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN
        var result = handlerBundle.getSelections();

        // THEN
        assertEquals(result, MAPPER.createArrayNode());
    }

    @Test
    void when_getConditions_then_returnConditionsOfObligations() {
        // GIVEN
        var constraintData = List.of(
                new RecordConstraintData(ConstraintHandlerType.R2DBC_QUERY_MANIPULATION, R2DBC_OBLIGATION),
                new RecordConstraintData(ConstraintHandlerType.MONGO_QUERY_MANIPULATION, MONGO_OBLIGATION));

        var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN

        var result = handlerBundle.getConditions();

        // THEN
        assertEquals(result, CONDITIONS);
    }

    @Test
    void when_getQueryManipulationObligations_then_returnObligations() {
        // GIVEN
        var constraintData = List.of(
                new RecordConstraintData(ConstraintHandlerType.R2DBC_QUERY_MANIPULATION, R2DBC_OBLIGATION),
                new RecordConstraintData(ConstraintHandlerType.MONGO_QUERY_MANIPULATION, MONGO_OBLIGATION));
        var obligations    = new JsonNode[] { R2DBC_OBLIGATION, MONGO_OBLIGATION };

        var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN

        var result = handlerBundle.getQueryManipulationObligations();

        // THEN
        for (int i = 0; i < result.length; i++) {
            assertEquals(result[i], obligations[i]);
        }
    }

    @Test
    void when_getQueryManipulationObligations_then_returnEmptyArray() {
        // GIVEN
        var constraintData = List.of(new RecordConstraintData(null, R2DBC_OBLIGATION));

        var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN

        var result = handlerBundle.getQueryManipulationObligations();

        // THEN
        assertEquals(0, result.length);
    }

    @Test
    void when_getQueryManipulationObligations_then_returnEmptyArray2() {
        // GIVEN
        List<RecordConstraintData> constraintData = List.of();

        var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN

        var result = handlerBundle.getQueryManipulationObligations();

        // THEN
        assertEquals(0, result.length);
    }

    @Test
    void when_getTransformations_then_returnTransformations() {
        // GIVEN
        var constraintData = List.of(
                new RecordConstraintData(ConstraintHandlerType.R2DBC_QUERY_MANIPULATION, R2DBC_OBLIGATION),
                new RecordConstraintData(ConstraintHandlerType.MONGO_QUERY_MANIPULATION, MONGO_OBLIGATION));

        var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN

        var result = handlerBundle.getTransformations();

        // THEN
        assertEquals(result, TRANSFORMATIONS);
    }

    @Test
    void when_getTransformations_then_returnEmptyArrayNode2() {
        // GIVEN
        var constraintData = List.of(new RecordConstraintData(ConstraintHandlerType.R2DBC_QUERY_MANIPULATION,
                R2DBC_OBLIGATION_WRONG_TRANSFORMATIONS));

        var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN

        var result = handlerBundle.getTransformations();

        // THEN
        assertEquals(result, MAPPER.createArrayNode());
    }

    @Test
    void when_getTransformations_then_returnEmptyArrayNode3() {
        // GIVEN
        List<RecordConstraintData> constraintData = List.of();

        var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN

        var result = handlerBundle.getTransformations();

        // THEN
        assertEquals(result, MAPPER.createArrayNode());
    }

    @Test
    void when_getTransformations_then_returnAlias() {
        // GIVEN
        var constraintData = List
                .of(new RecordConstraintData(ConstraintHandlerType.R2DBC_QUERY_MANIPULATION, R2DBC_OBLIGATION));

        var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN

        var result = handlerBundle.getAlias();

        // THEN
        assertEquals("p", result);
    }

    @Test
    void when_getTransformations_then_returnEmptyString() {
        // GIVEN
        var constraintData = List
                .of(new RecordConstraintData(ConstraintHandlerType.MONGO_QUERY_MANIPULATION, MONGO_OBLIGATION));

        var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN

        var result = handlerBundle.getAlias();

        // THEN
        assertEquals("", result);
    }

    @Test
    void when_getTransformations_then_returnEmptyArrayNode() {
        // GIVEN
        var constraintData = List.of(
                new RecordConstraintData(ConstraintHandlerType.R2DBC_QUERY_MANIPULATION, R2DBC_OBLIGATION_WRONG_ALIAS));

        var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN

        var result = handlerBundle.getAlias();

        // THEN
        assertEquals("", result);
    }
}
