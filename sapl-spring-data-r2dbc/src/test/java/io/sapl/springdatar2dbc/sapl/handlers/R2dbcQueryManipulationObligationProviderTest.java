/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.springdatar2dbc.sapl.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

class R2dbcQueryManipulationObligationProviderTest {

    static final ObjectMapper MAPPER = new ObjectMapper();
    static JsonNode           OBLIGATIONS;
    static JsonNode           MONGO_QUERY_MANIPULATION;

    final JsonNode nullNode = JsonNodeFactory.instance.nullNode();

    final R2dbcQueryManipulationObligationProvider provider = new R2dbcQueryManipulationObligationProvider();

    @BeforeAll
    public static void beforeAll() throws JsonProcessingException {
        OBLIGATIONS              = MAPPER.readTree(
                "[{\"type\":\"r2dbcQueryManipulation\",\"condition\":\"role IN ('USER')\"},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
        MONGO_QUERY_MANIPULATION = MAPPER
                .readTree("{\"type\":\"r2dbcQueryManipulation\",\"condition\":\"role IN ('USER')\"}");
    }

    @Test
    void when_obligationContainsConditions_then_getConditions() {
        // GIVEN
        var expectedCondition = "role IN ('USER')";

        // WHEN
        var condition = provider.getCondition(MONGO_QUERY_MANIPULATION);

        // THEN
        assertEquals(condition.asText(), expectedCondition);
    }

    @Test
    void when_obligationContainsNotCorrectStructuredConditions_then_returnNullNode() throws JsonProcessingException {
        // GIVEN
        var wrongMongoQueryManipulation = MAPPER
                .readTree("{\"type\":\"r2dbcQueryManipulation\",\"wrongName\":\"role IN ('USER')\"}");

        // WHEN
        var conditionsResult = provider.getCondition(wrongMongoQueryManipulation);

        // THEN
        assertEquals(conditionsResult, nullNode);
    }

    @Test
    void when_obligationsContainMongoQueryManipulationObligation_then_getObligation() {
        // GIVEN

        // WHEN
        var mongoQueryManipulationObligationResult = provider.getObligation(OBLIGATIONS);

        // THEN
        assertEquals(mongoQueryManipulationObligationResult, MONGO_QUERY_MANIPULATION);
    }

    @Test
    void when_obligationsContainNoMongoQueryManipulationObligation_then_returnNullNode()
            throws JsonProcessingException {
        // GIVEN
        var obligationsWithoutMongoQueryManipulationObligation = MAPPER.readTree(
                "[{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");

        // WHEN
        var mongoQueryManipulationObligationResult = provider
                .getObligation(obligationsWithoutMongoQueryManipulationObligation);

        // THEN
        assertEquals(mongoQueryManipulationObligationResult, nullNode);
    }

    @Test
    void when_obligationsContainMongoQueryManipulationObligation_then_isResponsible() {
        // GIVEN

        // WHEN
        var mongoQueryManipulationObligationResult = provider.isResponsible(OBLIGATIONS);

        // THEN
        assertTrue(mongoQueryManipulationObligationResult);
    }

    @Test
    void when_obligationsContainMongoQueryManipulationObligation_then_isNotResponsible()
            throws JsonProcessingException {
        // GIVEN
        var obligationsWithoutMongoQueryManipulationObligation = MAPPER.readTree(
                "[{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");

        // WHEN
        var mongoQueryManipulationObligationResult = provider
                .isResponsible(obligationsWithoutMongoQueryManipulationObligation);

        // THEN
        assertFalse(mongoQueryManipulationObligationResult);
    }
}
