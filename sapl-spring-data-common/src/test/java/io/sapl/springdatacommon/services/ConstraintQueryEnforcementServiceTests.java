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
import static org.mockito.ArgumentMatchers.eq;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.constraints.providers.ConstraintResponsibility;
import io.sapl.springdatacommon.utils.TestUtils;

class ConstraintQueryEnforcementServiceTests {

    private static final ObjectMapper              MAPPER   = new ObjectMapper();
    private final AuthorizationDecision            decision = Mockito.mock(AuthorizationDecision.class);
    private MockedStatic<ConstraintResponsibility> constraintResponsibilityMock;

    private static Optional<ArrayNode> OBLIGATIONS_MONGO_QUERY;
    private static JsonNode            OBLIGATION_MONGO_QUERY;

    private static Optional<ArrayNode> OBLIGATIONS_R2DBC_QUERY;
    private static JsonNode            OBLIGATION_R2DBC_QUERY;
    private static Optional<ArrayNode> OBLIGATIONS_ANOTHER_ONE;

    private static Optional<ArrayNode> OBLIGATION_WITH_WRONG_FORMAT;
    private static Optional<ArrayNode> OBLIGATION_WITH_WRONG_FORMAT_TWO;

    private static ConstraintQueryEnforcementService CONSTRAINT_SERVICE = new ConstraintQueryEnforcementService();

    @Test
	void when_queryManipulationBundelFor_then_createBundleWithMongoQueryManipulation() {
		// GIVEN
		when(decision.getObligations()).thenReturn(OBLIGATIONS_MONGO_QUERY);
		when(ConstraintResponsibility.isResponsible(OBLIGATION_MONGO_QUERY, "mongoQueryManipulation")).thenReturn(true);

		var queryManipulationRecords = List
				.of(new RecordConstraintData(ConstraintHandlerType.MONGO_QUERY_MANIPULATION, OBLIGATION_MONGO_QUERY));
		var result = new QueryManipulationConstraintHandlerService(queryManipulationRecords);

		// WHEN
		var handler = CONSTRAINT_SERVICE.queryManipulationForMongoReactive(decision);

		// THEN
		assertEquals(handler.getQueryManipulationRecords(), result.getQueryManipulationRecords());
	}

    @Test
	void when_queryManipulationBundelFor_then_createBundleWithR2dbcQueryManipulation() {
		// GIVEN
		when(decision.getObligations()).thenReturn(OBLIGATIONS_R2DBC_QUERY);
		when(ConstraintResponsibility.isResponsible(OBLIGATION_R2DBC_QUERY, "r2dbcQueryManipulation")).thenReturn(true);

		var queryManipulationRecords = List
				.of(new RecordConstraintData(ConstraintHandlerType.R2DBC_QUERY_MANIPULATION, OBLIGATION_R2DBC_QUERY));
		var result = new QueryManipulationConstraintHandlerService(queryManipulationRecords);

		// WHEN
		var handler = CONSTRAINT_SERVICE.queryManipulationForR2dbc(decision);

		// THEN
		assertEquals(handler.getQueryManipulationRecords(), result.getQueryManipulationRecords());
	}

    @Test
	void when_queryManipulationBundelFor_then_throwAccessDenyErrorBecauseUnhandledObligationDetected1() {
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
							}
				""";

		// WHEN

		// THEN
		var accessDeniedException = assertThrows(AccessDeniedException.class, () -> {
			CONSTRAINT_SERVICE.queryManipulationForMongoReactive(decision);
		});
		assertEquals(TestUtils.removeWhitespace(errorMessage),
				TestUtils.removeWhitespace(accessDeniedException.getMessage()));
	}

    @Test
	void when_queryManipulationBundelFor_then_throwAccessDenyErrorBecauseNoObligationDetected() {
		// GIVEN
		when(decision.getObligations()).thenReturn(OBLIGATIONS_ANOTHER_ONE);
		when(ConstraintResponsibility.isResponsible(OBLIGATIONS_ANOTHER_ONE.get(), "r2dbcQueryManipulation"))
				.thenReturn(true);

		var errorMessage = """
						UnhandableObligation:{
							"type":"testObligation",
							"test":["{'role':{'$eq':'USER'}}"]}
				""";

		// WHEN

		// THEN
		var accessDeniedException = assertThrows(AccessDeniedException.class, () -> {
			CONSTRAINT_SERVICE.queryManipulationForR2dbc(decision);
		});
		assertEquals(TestUtils.removeWhitespace(errorMessage),
				TestUtils.removeWhitespace(accessDeniedException.getMessage()));
	}

    @Test
	void when_queryManipulationBundelFor_then_throwAccessDenyErrorWrongFormatOfObligationDetected() {
		// GIVEN
		when(decision.getObligations()).thenReturn(OBLIGATION_WITH_WRONG_FORMAT_TWO);
		when(ConstraintResponsibility.isResponsible(any(JsonNode.class), eq("r2dbcQueryManipulation")))
				.thenReturn(true);

		var errorMessage = """
					UnhandableObligation:{
						"type":"r2dbcQueryManipulation",
						"conditionss":["active=true"],
						"selectionss":{
							"types":["blacklist"],
							"columnss":["firstname"]}}
				""";

		// WHEN

		// THEN
		var accessDeniedException = assertThrows(AccessDeniedException.class, () -> {
			CONSTRAINT_SERVICE.queryManipulationForR2dbc(decision);
		});
		assertEquals(TestUtils.removeWhitespace(errorMessage),
				TestUtils.removeWhitespace(accessDeniedException.getMessage()));
	}

    @Test
	void when_queryManipulationBundelFor_then_throwAccessDenyErrorBecauseUnhandledObligationDetected2() {
		// GIVEN
		when(decision.getObligations()).thenReturn(OBLIGATION_WITH_WRONG_FORMAT);
		when(ConstraintResponsibility.isResponsible(OBLIGATION_MONGO_QUERY, "mongoQueryManipulation")).thenReturn(true);

		var errorMessage = """
							Unhandable Obligation: {
							  "type" : "r2dbcQueryManipulation",
							  "conditionss" : [ "active=true" ],
							  "selection" : {
							    "types" : "blacklist",
							    "columns" : [ "firstname" ]
							  }
							}
				""";

		// WHEN

		// THEN
		var accessDeniedException = assertThrows(AccessDeniedException.class, () -> {
			CONSTRAINT_SERVICE.queryManipulationForMongoReactive(decision);
		});
		assertEquals(TestUtils.removeWhitespace(errorMessage),
				TestUtils.removeWhitespace(accessDeniedException.getMessage()));
	}

    @BeforeEach
    void initStaticClasses() {
        constraintResponsibilityMock = mockStatic(ConstraintResponsibility.class);
    }

    @AfterEach
    void closeStaticClasses() {
        constraintResponsibilityMock.close();
    }

    @BeforeAll
    static void initTestData() throws JsonProcessingException {
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

        OBLIGATIONS_ANOTHER_ONE = Optional.of(MAPPER.readValue("""
                		[
                			{
                			  "type": "testObligation",
                			  "test": [
                			    "{'role': {'$eq': 'USER'}}"
                			  ]
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

        OBLIGATION_WITH_WRONG_FORMAT = Optional.of(MAPPER.readValue("""
                			[
                			  {
                			    "type": "r2dbcQueryManipulation",
                			    "conditionss": [
                			      "active = true"
                			    ],
                			    "selection": {
                			      "types": "blacklist",
                			      "columns": [
                			        "firstname"
                			      ]
                			    }
                			  }
                			]
                """, ArrayNode.class));

        OBLIGATION_WITH_WRONG_FORMAT_TWO = Optional.of(MAPPER.readValue("""
                			[
                			  {
                			    "type": "r2dbcQueryManipulation",
                			    "conditionss": [
                			      "active = true"
                			    ],
                			    "selectionss": {
                			      "types": ["blacklist"],
                			      "columnss": [
                			        "firstname"
                			      ]
                			    }
                			  }
                			]
                """, ArrayNode.class));
    };

}
