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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.springdatacommon.utils.TestUtils;


class QueryManipulationConstraintHandlerBundleTests {

	private static JsonNode R2DBC_OBLIGATION;
	private static JsonNode MONGO_OBLIGATION;
	private static JsonNode SELECTIONS;
	private static JsonNode CONDITIONS;
	private static JsonNode OBLIGATION_WITHOUT_SELECTION;
	private static MockedStatic<JsonNodeStructure> JSON_NODE_STRUCTURE_MOCK;
	private static ObjectMapper MAPPER = new ObjectMapper();

	@BeforeAll
	static void initJsonNodes() throws JsonMappingException, JsonProcessingException {

		R2DBC_OBLIGATION = MAPPER.readTree("""
					{
				          "type": "r2dbcQueryManipulation",
				          "conditions": [ "role = 'USER'" ],
					       "selection": {
								"type": "blacklist",
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
	}

	@Test
	void when_getSelections_then_returnSelectionsOfObligations() {
		// GIVEN
		var constraintData = List.of(
				new RecordConstraintData(EnumConstraintHandlerType.R2DBC_QUERY_MANIPULATION, R2DBC_OBLIGATION),
				new RecordConstraintData(EnumConstraintHandlerType.MONGO_QUERY_MANIPULATION, MONGO_OBLIGATION));

		var handlerBundle = new QueryManipulationConstraintHandlerBundle(constraintData);

		// WHEN
		when(JsonNodeStructure.compare(any(JsonNode.class), any(JsonNode.class))).thenReturn(true);

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
								"type":"r2dbcQueryManipulation",
								"conditions":["role='USER'"],
								"selection":{
									"type":"blacklist",
									"columns":["firstname"]
								}
						}
				""";

		var constraintData = List.of(
				new RecordConstraintData(EnumConstraintHandlerType.R2DBC_QUERY_MANIPULATION, R2DBC_OBLIGATION),
				new RecordConstraintData(EnumConstraintHandlerType.MONGO_QUERY_MANIPULATION, MONGO_OBLIGATION));

		var handlerBundle = new QueryManipulationConstraintHandlerBundle(constraintData);

		// WHEN
		when(JsonNodeStructure.compare(any(JsonNode.class), any(JsonNode.class))).thenReturn(false);

		var accessDeniedException = assertThrows(AccessDeniedException.class, () -> {
			handlerBundle.getSelections();
		});

		// THEN
		assertEquals(TestUtils.removeWhitespace(errorMessage), TestUtils.removeWhitespace(accessDeniedException.getMessage()));
	}

	@Test
	void when_getConditions_then_returnEmptyArrayNode() {
		// GIVEN
		var constraintData = new ArrayList<RecordConstraintData>();

		var handlerBundle = new QueryManipulationConstraintHandlerBundle(constraintData);

		// WHEN
		var result = handlerBundle.getConditions();

		// THEN
		assertEquals(result, MAPPER.createArrayNode());
	}

	@Test
	void when_getSelections_then_returnEmptyArrayNode1() {
		// GIVEN
		var constraintData = new ArrayList<RecordConstraintData>();

		var handlerBundle = new QueryManipulationConstraintHandlerBundle(constraintData);

		// WHEN
		var result = handlerBundle.getSelections();

		// THEN
		assertEquals(result, MAPPER.createArrayNode());
	}

	@Test
	void when_getSelections_then_returnEmptyArrayNode2() {
		// GIVEN
		var constraintData = List.of(new RecordConstraintData(EnumConstraintHandlerType.R2DBC_QUERY_MANIPULATION,
				OBLIGATION_WITHOUT_SELECTION));

		var handlerBundle = new QueryManipulationConstraintHandlerBundle(constraintData);

		// WHEN
		var result = handlerBundle.getSelections();

		// THEN
		assertEquals(result, MAPPER.createArrayNode());
	}

	@Test
	void when_getConditions_then_returnConditionsOfObligations() {
		// GIVEN
		var constraintData = List.of(
				new RecordConstraintData(EnumConstraintHandlerType.R2DBC_QUERY_MANIPULATION, R2DBC_OBLIGATION),
				new RecordConstraintData(EnumConstraintHandlerType.MONGO_QUERY_MANIPULATION, MONGO_OBLIGATION));

		var handlerBundle = new QueryManipulationConstraintHandlerBundle(constraintData);

		// WHEN

		var result = handlerBundle.getConditions();

		// THEN
		assertEquals(result, CONDITIONS);
	}

	@BeforeEach
	void initStaticClasses() {
		JSON_NODE_STRUCTURE_MOCK = mockStatic(JsonNodeStructure.class);
	}

	@AfterEach
	void closeStaticClasses() {
		JSON_NODE_STRUCTURE_MOCK.close();
	}

}
