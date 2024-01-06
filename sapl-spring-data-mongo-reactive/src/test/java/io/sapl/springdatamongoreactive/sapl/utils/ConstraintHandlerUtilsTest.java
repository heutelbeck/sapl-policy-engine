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
package io.sapl.springdatamongoreactive.sapl.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.util.ReflectionUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;

class ConstraintHandlerUtilsTest {

    static final ObjectMapper objectMapper = new ObjectMapper();
    static JsonNode           obligations;
    static JsonNode           mongoQueryManipulation;
    static JsonNode           wrongTypesObligations;
    static JsonNode           advice;

    final JsonNode nullNode = JsonNodeFactory.instance.nullNode();

    @BeforeAll
    public static void initBeforeAll() throws JsonProcessingException {
        obligations            = objectMapper.readTree(
                "[{\"type\":\"mongoQueryManipulation\",\"conditions\":[\"{'role':  {'$in': ['USER']}}\"]},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
        mongoQueryManipulation = objectMapper
                .readTree("{\"type\":\"mongoQueryManipulation\",\"conditions\":[\"{'role':  {'$in': ['USER']}}\"]}");
        wrongTypesObligations  = objectMapper
                .readTree("[{\"type\":\"mongoQuery\",\"conditions\":[\"{'role':  {'$in': ['USER']}}\"]}]");
        advice                 = objectMapper
                .readTree("[{\"id\": \"log\",\"message\": \"You are using SAPL for protection of database.\"}]");
    }

    @Test
    void when_obligationContainsSpecificType_then_getConstraintHandlerByTypeIfResponsible() {
        // GIVEN

        // WHEN
        var actual = ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible(obligations,
                "mongoQueryManipulation");

        // THEN
        assertEquals(mongoQueryManipulation, actual);
    }

    @Test
    void when_obligationContainsNotSpecificType_then_returnNullNode() {
        // GIVEN

        // WHEN
        var actual = ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible(wrongTypesObligations,
                "mongoQueryManipulation");

        // THEN
        assertEquals(nullNode, actual);
    }

    @Test
    void when_authorizationDecisionHasObligations_then_getObligations() {
        // GIVEN
        var obligationsAsArrayNode = (ArrayNode) obligations;
        var optionalObligations    = Optional.of(obligationsAsArrayNode);
        var authDec                = new AuthorizationDecision(Decision.PERMIT, null, optionalObligations, null);

        // WHEN
        var actual = ConstraintHandlerUtils.getObligations(authDec);

        // THEN
        assertEquals(obligations, actual);
    }

    @Test
    void when_authorizationDecisionHasAdvices_then_getAdvices() {
        // GIVEN
        var adviceAsArrayNode = (ArrayNode) advice;

        var optionalAdvice = Optional.of(adviceAsArrayNode);
        var authDec        = new AuthorizationDecision(Decision.PERMIT, null, null, optionalAdvice);

        // WHEN
        var actual = ConstraintHandlerUtils.getAdvices(authDec);

        // THEN
        assertEquals(advice, actual);
    }

    @Test
    void when_classIsStaticUtilityClass_then_instantiateThisTestForCoverageReasonsOfConstructor() {
        assertThrows(InvocationTargetException.class, () -> {
            var constructor = ConstraintHandlerUtils.class.getDeclaredConstructor();
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
            ReflectionUtils.makeAccessible(constructor);
            constructor.newInstance();
        });
    }

}
