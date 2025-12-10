/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.constraints.providers.ConstraintResponsibility;
import io.sapl.spring.data.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

class ConstraintQueryEnforcementServiceTests {

    private static final ObjectMapper                      MAPPER             = new ObjectMapper();
    private static final ConstraintQueryEnforcementService CONSTRAINT_SERVICE = new ConstraintQueryEnforcementService();

    private static List<Value> obligationsMongoQuery;
    private static Value       obligationMongoQuery;

    private static List<Value> obligationsR2dbcQuery;
    private static Value       obligationR2dbcQuery;
    private static List<Value> obligationsAnotherOne;

    private static List<Value> firstObligationInWrongFormat;
    private static List<Value> secondObligationInWrongFormat;

    private MockedStatic<ConstraintResponsibility> constraintResponsibilityMock;

    private static AuthorizationDecision createDecision(List<Value> obligations) {
        return new AuthorizationDecision(Decision.PERMIT, obligations, List.of(), Value.UNDEFINED);
    }

    @Test
    void when_queryManipulationBundelFor_then_createBundleWithMongoQueryManipulation() {
        // GIVEN
        var decision = createDecision(obligationsMongoQuery);
        constraintResponsibilityMock
                .when(() -> ConstraintResponsibility.isResponsible(obligationMongoQuery, "mongoQueryManipulation"))
                .thenReturn(true);

        final var queryManipulationRecords = List.of(new RecordConstraintData(
                ConstraintHandlerType.MONGO_QUERY_MANIPULATION, ValueJsonMarshaller.toJsonNode(obligationMongoQuery)));
        final var result                   = new QueryManipulationConstraintHandlerService(queryManipulationRecords);

        // WHEN
        final var handler = CONSTRAINT_SERVICE.queryManipulationForMongoReactive(decision);

        // THEN
        assertEquals(handler.getQueryManipulationRecords(), result.getQueryManipulationRecords());
    }

    @Test
    void when_queryManipulationBundelFor_then_createBundleWithR2dbcQueryManipulation() {
        // GIVEN
        var decision = createDecision(obligationsR2dbcQuery);
        constraintResponsibilityMock
                .when(() -> ConstraintResponsibility.isResponsible(obligationR2dbcQuery, "r2dbcQueryManipulation"))
                .thenReturn(true);

        final var queryManipulationRecords = List.of(new RecordConstraintData(
                ConstraintHandlerType.R2DBC_QUERY_MANIPULATION, ValueJsonMarshaller.toJsonNode(obligationR2dbcQuery)));
        final var result                   = new QueryManipulationConstraintHandlerService(queryManipulationRecords);

        // WHEN
        final var handler = CONSTRAINT_SERVICE.queryManipulationForR2dbc(decision);

        // THEN
        assertEquals(handler.getQueryManipulationRecords(), result.getQueryManipulationRecords());
    }

    @Test
    void when_queryManipulationBundelFor_then_throwAccessDenyErrorBecauseUnhandledObligationDetected1() {
        // GIVEN
        var decision = createDecision(obligationsMongoQuery);
        constraintResponsibilityMock
                .when(() -> ConstraintResponsibility.isResponsible(obligationMongoQuery, "mongoQueryManipulation"))
                .thenReturn(false);

        final var errorMessage = """
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
        final var accessDeniedException = assertThrows(AccessDeniedException.class,
                () -> CONSTRAINT_SERVICE.queryManipulationForMongoReactive(decision));
        assertEquals(TestUtils.removeWhitespace(errorMessage),
                TestUtils.removeWhitespace(accessDeniedException.getMessage()));
    }

    @Test
    void when_queryManipulationBundelFor_then_throwAccessDenyErrorBecauseNoObligationDetected() {
        // GIVEN
        var decision = createDecision(obligationsAnotherOne);
        constraintResponsibilityMock.when(() -> ConstraintResponsibility.isResponsible(obligationsAnotherOne.getFirst(),
                "r2dbcQueryManipulation")).thenReturn(true);

        final var errorMessage = """
                		UnhandableObligation:{
                			"type":"testObligation",
                			"test":["{'role':{'$eq':'USER'}}"]}
                """;

        // WHEN

        // THEN
        final var accessDeniedException = assertThrows(AccessDeniedException.class,
                () -> CONSTRAINT_SERVICE.queryManipulationForR2dbc(decision));
        assertEquals(TestUtils.removeWhitespace(errorMessage),
                TestUtils.removeWhitespace(accessDeniedException.getMessage()));
    }

    @Test
    void when_queryManipulationBundelFor_then_throwAccessDenyErrorWrongFormatOfObligationDetected() {
        // GIVEN
        var decision = createDecision(secondObligationInWrongFormat);
        constraintResponsibilityMock
                .when(() -> ConstraintResponsibility.isResponsible(any(Value.class), eq("r2dbcQueryManipulation")))
                .thenReturn(true);

        final var errorMessage = """
                	UnhandableObligation:{
                		"type":"r2dbcQueryManipulation",
                		"conditionss":["active=true"],
                		"selectionss":{
                			"types":["blacklist"],
                			"columnss":["firstname"]}}
                """;

        // WHEN

        // THEN
        final var accessDeniedException = assertThrows(AccessDeniedException.class,
                () -> CONSTRAINT_SERVICE.queryManipulationForR2dbc(decision));
        assertEquals(TestUtils.removeWhitespace(errorMessage),
                TestUtils.removeWhitespace(accessDeniedException.getMessage()));
    }

    @Test
    void when_queryManipulationBundelFor_then_throwAccessDenyErrorBecauseUnhandledObligationDetected2() {
        // GIVEN
        var decision = createDecision(firstObligationInWrongFormat);
        constraintResponsibilityMock
                .when(() -> ConstraintResponsibility.isResponsible(obligationMongoQuery, "mongoQueryManipulation"))
                .thenReturn(true);

        final var errorMessage = """
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
        final var accessDeniedException = assertThrows(AccessDeniedException.class,
                () -> CONSTRAINT_SERVICE.queryManipulationForMongoReactive(decision));
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
        obligationMongoQuery = ValueJsonMarshaller.fromJsonNode(MAPPER.readTree("""
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
                """));

        obligationsMongoQuery = List.of(obligationMongoQuery);

        obligationsAnotherOne = List.of(ValueJsonMarshaller.fromJsonNode(MAPPER.readTree("""
                			{
                			  "type": "testObligation",
                			  "test": [
                			    "{'role': {'$eq': 'USER'}}"
                			  ]
                			}
                """)));

        obligationR2dbcQuery = ValueJsonMarshaller.fromJsonNode(MAPPER.readTree("""
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
                """));

        obligationsR2dbcQuery = List.of(obligationR2dbcQuery);

        firstObligationInWrongFormat = List.of(ValueJsonMarshaller.fromJsonNode(MAPPER.readTree("""
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
                """)));

        secondObligationInWrongFormat = List.of(ValueJsonMarshaller.fromJsonNode(MAPPER.readTree("""
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
                """)));
    }

}
