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
package io.sapl.spring.data.services;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import io.sapl.spring.data.utils.TestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueryManipulationConstraintHandlerServiceTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode  r2dbcObligation;
    private static JsonNode  r2dbcUnhandableObligation;
    private static JsonNode  mongoObligation;
    private static JsonNode  selections;
    private static JsonNode  conditions;
    private static JsonNode  obligationWithoutSelection;
    private static JsonNode  r2dbcObligationWrongAlias;
    private static JsonNode  r2dbcObligationWrongTransformations;
    private static ArrayNode transformations;

    @BeforeAll
    static void initJsonNodes() throws JacksonException {

        r2dbcObligation = MAPPER.readTree("""
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

        r2dbcObligationWrongAlias = MAPPER.readTree("""
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

        r2dbcObligationWrongTransformations = MAPPER.readTree("""
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

        r2dbcUnhandableObligation = MAPPER.readTree("""
                	{
                          "types": "r2dbcQueryManipulation",
                          "conditions": [ "role = 'USER'" ],
                	       "selection": {
                				"types": "blacklist",
                				"columns": ["firstname"]
                			}
                     }
                """);

        mongoObligation = MAPPER.readTree("""
                {
                  "type": "mongoQueryManipulation",
                  "conditions": [ "{'role': {'$eq': 'USER'}}" ],
                  "selection": {
                		"type": "blacklist",
                		"columns": ["firstname"]
                }
                         }
                """);

        selections = MAPPER.readValue("""
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

        conditions = MAPPER.readValue("""
                [
                	"role = 'USER'",
                	"{'role': {'$eq': 'USER'}}"
                ]
                """, ArrayNode.class);

        obligationWithoutSelection = MAPPER.readTree("""
                {
                  "type": "mongoQueryManipulation",
                  "conditions": [ "{'role': {'$eq': 'USER'}}" ]
                }
                """);

        transformations = MAPPER.readValue("""
                [{"firstname":"UPPER"}]
                """, ArrayNode.class);
    }

    @Test
    void when_getSelections_then_returnSelectionsOfObligations() {
        // GIVEN
        final var constraintData = List.of(
                new RecordConstraintData(ConstraintHandlerType.R2DBC_QUERY_MANIPULATION, r2dbcObligation),
                new RecordConstraintData(ConstraintHandlerType.MONGO_QUERY_MANIPULATION, mongoObligation));

        final var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN
        final var result = handlerBundle.getSelections();

        // THEN
        assertEquals(result, selections);
    }

    @Test
    void when_getSelections_then_throwAccessDeniedException() {
        // GIVEN
        final var errorMessage = """
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

        final var constraintData = List.of(
                new RecordConstraintData(ConstraintHandlerType.R2DBC_QUERY_MANIPULATION, r2dbcUnhandableObligation),
                new RecordConstraintData(ConstraintHandlerType.MONGO_QUERY_MANIPULATION, mongoObligation));

        final var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN

        final var accessDeniedException = assertThrows(AccessDeniedException.class, handlerBundle::getSelections);

        // THEN
        assertEquals(TestUtils.removeWhitespace(errorMessage),
                TestUtils.removeWhitespace(accessDeniedException.getMessage()));
    }

    @Test
    void when_getConditions_then_returnEmptyArrayNode() {
        // GIVEN
        final var constraintData = new ArrayList<RecordConstraintData>();

        final var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN
        final var result = handlerBundle.getConditions();

        // THEN
        assertEquals(result, MAPPER.createArrayNode());
    }

    @Test
    void when_getSelections_then_returnEmptyArrayNode1() {
        // GIVEN
        final var constraintData = new ArrayList<RecordConstraintData>();

        final var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN
        final var result = handlerBundle.getSelections();

        // THEN
        assertEquals(result, MAPPER.createArrayNode());
    }

    @Test
    void when_getSelections_then_returnEmptyArrayNode2() {
        // GIVEN
        final var constraintData = List.of(
                new RecordConstraintData(ConstraintHandlerType.R2DBC_QUERY_MANIPULATION, obligationWithoutSelection));

        final var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN
        final var result = handlerBundle.getSelections();

        // THEN
        assertEquals(result, MAPPER.createArrayNode());
    }

    @Test
    void when_getConditions_then_returnConditionsOfObligations() {
        // GIVEN
        final var constraintData = List.of(
                new RecordConstraintData(ConstraintHandlerType.R2DBC_QUERY_MANIPULATION, r2dbcObligation),
                new RecordConstraintData(ConstraintHandlerType.MONGO_QUERY_MANIPULATION, mongoObligation));

        final var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN

        final var result = handlerBundle.getConditions();

        // THEN
        assertEquals(result, conditions);
    }

    @Test
    void when_getQueryManipulationObligations_then_returnObligations() {
        // GIVEN
        final var constraintData = List.of(
                new RecordConstraintData(ConstraintHandlerType.R2DBC_QUERY_MANIPULATION, r2dbcObligation),
                new RecordConstraintData(ConstraintHandlerType.MONGO_QUERY_MANIPULATION, mongoObligation));
        final var obligations    = new JsonNode[] { r2dbcObligation, mongoObligation };

        final var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN

        final var result = handlerBundle.getQueryManipulationObligations();

        // THEN
        for (int i = 0; i < result.length; i++) {
            assertEquals(result[i], obligations[i]);
        }
    }

    @Test
    void when_getQueryManipulationObligations_then_returnEmptyArray() {
        // GIVEN
        final var constraintData = List.of(new RecordConstraintData(null, r2dbcObligation));

        final var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN

        final var result = handlerBundle.getQueryManipulationObligations();

        // THEN
        assertEquals(0, result.length);
    }

    @Test
    void when_getQueryManipulationObligations_then_returnEmptyArray2() {
        // GIVEN
        List<RecordConstraintData> constraintData = List.of();

        final var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN

        final var result = handlerBundle.getQueryManipulationObligations();

        // THEN
        assertEquals(0, result.length);
    }

    @Test
    void when_getTransformations_then_returnTransformations() {
        // GIVEN
        final var constraintData = List.of(
                new RecordConstraintData(ConstraintHandlerType.R2DBC_QUERY_MANIPULATION, r2dbcObligation),
                new RecordConstraintData(ConstraintHandlerType.MONGO_QUERY_MANIPULATION, mongoObligation));

        final var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN

        final var result = handlerBundle.getTransformations();

        // THEN
        assertEquals(result, transformations);
    }

    @Test
    void when_getTransformations_then_returnEmptyArrayNode2() {
        // GIVEN
        final var constraintData = List.of(new RecordConstraintData(ConstraintHandlerType.R2DBC_QUERY_MANIPULATION,
                r2dbcObligationWrongTransformations));

        final var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN

        final var result = handlerBundle.getTransformations();

        // THEN
        assertEquals(result, MAPPER.createArrayNode());
    }

    @Test
    void when_getTransformations_then_returnEmptyArrayNode3() {
        // GIVEN
        List<RecordConstraintData> constraintData = List.of();

        final var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN

        final var result = handlerBundle.getTransformations();

        // THEN
        assertEquals(result, MAPPER.createArrayNode());
    }

    @Test
    void when_getTransformations_then_returnAlias() {
        // GIVEN
        final var constraintData = List
                .of(new RecordConstraintData(ConstraintHandlerType.R2DBC_QUERY_MANIPULATION, r2dbcObligation));

        final var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN

        final var result = handlerBundle.getAlias();

        // THEN
        assertEquals("p", result);
    }

    @Test
    void when_getTransformations_then_returnEmptyString() {
        // GIVEN
        final var constraintData = List
                .of(new RecordConstraintData(ConstraintHandlerType.MONGO_QUERY_MANIPULATION, mongoObligation));

        final var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN

        final var result = handlerBundle.getAlias();

        // THEN
        assertEquals("", result);
    }

    @Test
    void when_getTransformations_then_returnEmptyArrayNode() {
        // GIVEN
        final var constraintData = List.of(
                new RecordConstraintData(ConstraintHandlerType.R2DBC_QUERY_MANIPULATION, r2dbcObligationWrongAlias));

        final var handlerBundle = new QueryManipulationConstraintHandlerService(constraintData);

        // WHEN

        final var result = handlerBundle.getAlias();

        // THEN
        assertEquals("", result);
    }
}
