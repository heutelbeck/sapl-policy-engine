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
package io.sapl.springdatamongoreactive.sapl.handlers;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

class MongoQueryManipulationObligationProviderTest {

    static final ObjectMapper objectMapper = new ObjectMapper();
    static JsonNode           obligations;
    static JsonNode           mongoQueryManipulation;

    final JsonNode nullNode = JsonNodeFactory.instance.nullNode();

    final MongoQueryManipulationObligationProvider provider = new MongoQueryManipulationObligationProvider();

    @BeforeAll
    public static void initBeforeAll() throws JsonProcessingException {
        obligations            = objectMapper.readTree(
                "[{\"type\":\"mongoQueryManipulation\",\"conditions\":[\"{'role':  {'$in': ['USER']}}\"]},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
        mongoQueryManipulation = objectMapper
                .readTree("{\"type\":\"mongoQueryManipulation\",\"conditions\":[\"{'role':  {'$in': ['USER']}}\"]}");
    }

    @Test
    void when_obligationContainsConditions_then_getConditions() throws JsonProcessingException {
        // GIVEN
        var expectedConditions = objectMapper.readTree("[\"{'role':  {'$in': ['USER']}}\"]");

        // WHEN
        var conditions = provider.getConditions(mongoQueryManipulation);

        // THEN
        Assertions.assertEquals(conditions, expectedConditions);
    }

    @Test
    void when_obligationContainsNotCorrectStructuredConditions_then_returnNullNode() throws JsonProcessingException {
        // GIVEN
        var wrongMongoQueryManipulation = objectMapper
                .readTree("{\"type\":\"mongoQueryManipulation\",\"wrongName\":[\"{'role':  {'$in': ['USER']}}\"]}");

        // WHEN
        var conditionsResult = provider.getConditions(wrongMongoQueryManipulation);

        // THEN
        Assertions.assertEquals(conditionsResult, nullNode);
    }

    @Test
    void when_obligationsContainMongoQueryManipulationObligation_then_getObligation() {
        // GIVEN

        // WHEN
        var mongoQueryManipulationObligationResult = provider.getObligation(obligations);

        // THEN
        Assertions.assertEquals(mongoQueryManipulationObligationResult, mongoQueryManipulation);
    }

    @Test
    void when_obligationsContainNoMongoQueryManipulationObligation_then_returnNullNode()
            throws JsonProcessingException {
        // GIVEN
        var obligationsWithoutMongoQueryManipulationObligation = objectMapper.readTree(
                "[{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");

        // WHEN
        var mongoQueryManipulationObligationResult = provider
                .getObligation(obligationsWithoutMongoQueryManipulationObligation);

        // THEN
        Assertions.assertEquals(mongoQueryManipulationObligationResult, nullNode);
    }

    @Test
    void when_obligationsContainMongoQueryManipulationObligation_then_isResponsible() {
        // GIVEN

        // WHEN
        var mongoQueryManipulationObligationResult = provider.isResponsible(obligations);

        // THEN
        Assertions.assertTrue(mongoQueryManipulationObligationResult);
    }

    @Test
    void when_obligationsContainMongoQueryManipulationObligation_then_isNotResponsible()
            throws JsonProcessingException {
        // GIVEN
        var obligationsWithoutMongoQueryManipulationObligation = objectMapper.readTree(
                "[{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");

        // WHEN
        var mongoQueryManipulationObligationResult = provider
                .isResponsible(obligationsWithoutMongoQueryManipulationObligation);

        // THEN
        Assertions.assertFalse(mongoQueryManipulationObligationResult);
    }
}
