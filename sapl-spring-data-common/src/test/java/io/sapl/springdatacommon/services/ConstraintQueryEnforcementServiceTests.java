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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.constraints.providers.ConstraintResponsibility;
import io.sapl.springdatacommon.utils.TestUtils;

class ConstraintQueryEnforcementServiceTests {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private final AuthorizationDecision decision = Mockito.mock(AuthorizationDecision.class);

	private static Optional<ArrayNode> OBLIGATIONS_MONGO_QUERY;
	private static JsonNode OBLIGATION_MONGO_QUERY;

	private static Optional<ArrayNode> OBLIGATIONS_R2DBC_QUERY;
	private static JsonNode OBLIGATION_R2DBC_QUERY;

	private static MockedStatic<ConstraintResponsibility> CONSTRAINT_RESPONSIBILITY_MOCK;
	private static MockedStatic<JsonNodeStructure> JSON_NODE_STRUCTURE_MOCK;

	private static ConstraintQueryEnforcementService CONSTRAINT_SERVICE = new ConstraintQueryEnforcementService();

	@Test
	void when_queryManipulationBundelFor_then_createBundleWithMongoQueryManipulation()
			throws JsonMappingException, JsonProcessingException {
		// GIVEN
		when(decision.getObligations()).thenReturn(OBLIGATIONS_MONGO_QUERY);
		when(ConstraintResponsibility.isResponsible(OBLIGATION_MONGO_QUERY, "mongoQueryManipulation")).thenReturn(true);
		when(JsonNodeStructure.compare(OBLIGATION_MONGO_QUERY,
				EnumConstraintHandlerType.MONGO_QUERY_MANIPULATION.getTemplate())).thenReturn(true);

		var queryManipulationRecords = List.of(
				new RecordConstraintData(EnumConstraintHandlerType.MONGO_QUERY_MANIPULATION, OBLIGATION_MONGO_QUERY));
		var result = new QueryManipulationConstraintHandlerBundle(queryManipulationRecords);

		// WHEN
		var bundle = CONSTRAINT_SERVICE.queryManipulationBundelFor(decision, false);

		// THEN
		assertEquals(bundle.getQueryManipulationRecords(), result.getQueryManipulationRecords());
	}

	@Test
	void when_queryManipulationBundelFor_then_createBundleWithR2dbcQueryManipulation()
			throws JsonMappingException, JsonProcessingException {
		// GIVEN
		when(decision.getObligations()).thenReturn(OBLIGATIONS_R2DBC_QUERY);
		when(ConstraintResponsibility.isResponsible(OBLIGATION_R2DBC_QUERY, "r2dbcQueryManipulation")).thenReturn(true);
		when(JsonNodeStructure.compare(OBLIGATION_R2DBC_QUERY,
				EnumConstraintHandlerType.R2DBC_QUERY_MANIPULATION.getTemplate())).thenReturn(true);

		var queryManipulationRecords = List.of(
				new RecordConstraintData(EnumConstraintHandlerType.R2DBC_QUERY_MANIPULATION, OBLIGATION_R2DBC_QUERY));
		var result = new QueryManipulationConstraintHandlerBundle(queryManipulationRecords);

		// WHEN
		var bundle = CONSTRAINT_SERVICE.queryManipulationBundelFor(decision, true);

		// THEN
		assertEquals(bundle.getQueryManipulationRecords(), result.getQueryManipulationRecords());
	}

	@Test
	void when_queryManipulationBundelFor_then_throwAccessDenyErrorBecauseUnhandledObligationDetected1()
			throws JsonMappingException, JsonProcessingException {
		// GIVEN
		when(decision.getObligations()).thenReturn(OBLIGATIONS_MONGO_QUERY);
		when(ConstraintResponsibility.isResponsible(OBLIGATION_MONGO_QUERY, "mongoQueryManipulation"))
				.thenReturn(false);

		var errorMessage = """
							Unhandable Obligation: {
							  "type" : "mongoQueryManipulation",
							  "conditions" : [ "{'role': {'$eq': 'USER'}}" ],
							  "selection" : {
							    "type" : "blacklist",
							    "columns" : [ "firstname" ]
							  }
							}:
				""";

		// WHEN

		// THEN
		var accessDeniedException = assertThrows(AccessDeniedException.class, () -> {
			CONSTRAINT_SERVICE.queryManipulationBundelFor(decision, false);
		});
		assertEquals(TestUtils.removeWhitespace(errorMessage), TestUtils.removeWhitespace(accessDeniedException.getMessage()));
	}

	@Test
	void when_queryManipulationBundelFor_then_throwAccessDenyErrorBecauseUnhandledObligationDetected2()
			throws JsonMappingException, JsonProcessingException {
		// GIVEN
		when(decision.getObligations()).thenReturn(OBLIGATIONS_MONGO_QUERY);
		when(ConstraintResponsibility.isResponsible(OBLIGATION_MONGO_QUERY, "mongoQueryManipulation")).thenReturn(true);
		when(JsonNodeStructure.compare(OBLIGATION_MONGO_QUERY,
				EnumConstraintHandlerType.MONGO_QUERY_MANIPULATION.getTemplate())).thenReturn(false);

		var errorMessage = """
							Unhandable Obligation: {
							  "type" : "mongoQueryManipulation",
							  "conditions" : [ "{'role': {'$eq': 'USER'}}" ],
							  "selection" : {
							    "type" : "blacklist",
							    "columns" : [ "firstname" ]
							  }
							}:
				""";

		// WHEN

		// THEN
		var accessDeniedException = assertThrows(AccessDeniedException.class, () -> {
			CONSTRAINT_SERVICE.queryManipulationBundelFor(decision, false);
		});
		assertEquals(TestUtils.removeWhitespace(errorMessage), TestUtils.removeWhitespace(accessDeniedException.getMessage()));
	}

	@BeforeEach
	void initStaticClasses() {
		CONSTRAINT_RESPONSIBILITY_MOCK = mockStatic(ConstraintResponsibility.class);
		JSON_NODE_STRUCTURE_MOCK = mockStatic(JsonNodeStructure.class);
	}

	@AfterEach
	void closeStaticClasses() {
		CONSTRAINT_RESPONSIBILITY_MOCK.close();
		JSON_NODE_STRUCTURE_MOCK.close();
	}

	@BeforeAll
	static void initTestData() throws JsonMappingException, JsonProcessingException {
		OBLIGATIONS_MONGO_QUERY = Optional.of(MAPPER.readValue("""
						[
							{
							  "type": "mongoQueryManipulation",
							  "conditions": [
							    "{'role': {'$eq': 'USER'}}"
							  ],
							  "selection": {
							    "type": "blacklist",
							    "columns": [
							      "firstname"
							    ]
							  }
							}
							]
				""", ArrayNode.class));

		OBLIGATION_MONGO_QUERY = MAPPER.readTree("""
							{
							  "type": "mongoQueryManipulation",
							  "conditions": [
							    "{'role': {'$eq': 'USER'}}"
							  ],
							  "selection": {
							    "type": "blacklist",
							    "columns": [
							      "firstname"
							    ]
							  }
							}
				""");

		OBLIGATIONS_R2DBC_QUERY = Optional.of(MAPPER.readValue("""
							[
							  {
							    "type": "r2dbcQueryManipulation",
							    "conditions": [
							      "active = true"
							    ],
							    "selection": {
							      "type": "blacklist",
							      "columns": [
							        "firstname"
							      ]
							    }
							  }
							]
				""", ArrayNode.class));

		OBLIGATION_R2DBC_QUERY = MAPPER.readTree("""
							  {
							    "type": "r2dbcQueryManipulation",
							    "conditions": [
							      "active = true"
							    ],
							    "selection": {
							      "type": "blacklist",
							      "columns": [
							        "firstname"
							      ]
							    }
							  }
				""");
	};

}
