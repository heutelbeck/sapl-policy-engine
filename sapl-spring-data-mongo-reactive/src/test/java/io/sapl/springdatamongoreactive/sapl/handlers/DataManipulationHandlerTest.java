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
package io.sapl.springdatamongoreactive.sapl.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import io.sapl.springdatamongoreactive.sapl.utils.ConstraintHandlerUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class DataManipulationHandlerTest {

    DataManipulationHandler<TestUser>    dataManipulationHandler;
    MockedStatic<ConstraintHandlerUtils> constraintHandlerUtilsMock;

    final TestUser aaron   = new TestUser(new ObjectId(), "Aaron", 20);
    final TestUser brian   = new TestUser(new ObjectId(), "Brian", 21);
    final TestUser cathrin = new TestUser(new ObjectId(), "Cathrin", 23);

    final Flux<TestUser> data = Flux.just(aaron, brian, cathrin);

    static final ObjectMapper objectMapper = new ObjectMapper();
    static JsonNode           obligations;
    static JsonNode           jsonContentFilterPredicate;
    static JsonNode           filterJsonContent;

    @BeforeAll
    public static void initBeforeAll() throws JsonProcessingException {
        obligations                = objectMapper.readTree(
                "[{\"type\":\"mongoQueryManipulation\",\"conditions\":[\"{'role':  {'$in': ['USER']}}\"]},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
        jsonContentFilterPredicate = objectMapper.readTree(
                "{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.firstname\",\"value\":\"Aaron\"}]}");
        filterJsonContent          = objectMapper.readTree(
                "{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2},{\"type\":\"delete\",\"path\":\"$.age\"}]}");
    }

    @BeforeEach
    public void initBeforeEach() {
        constraintHandlerUtilsMock = mockStatic(ConstraintHandlerUtils.class);
        dataManipulationHandler    = new DataManipulationHandler<>(TestUser.class);
    }

    @AfterEach
    public void cleanUp() {
        constraintHandlerUtilsMock.close();
    }

    @Test
    void when_NoFilteringOrTransformationIsDesired_then_manipulate() {
        // GIVEN

        // WHEN
        constraintHandlerUtilsMock.when(
                () -> ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), anyString()))
                .thenReturn(JsonNodeFactory.instance.nullNode());

        var result = dataManipulationHandler.manipulate(obligations).apply(data);

        // THEN
        StepVerifier.create(result).expectNextMatches(testUser -> assertTwoTestUsers(testUser, aaron))
                .expectNextMatches(testUser -> assertTwoTestUsers(testUser, brian))
                .expectNextMatches(testUser -> assertTwoTestUsers(testUser, cathrin)).expectComplete().verify();

        constraintHandlerUtilsMock.verify(
                () -> ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), anyString()),
                times(2));
    }

    @Test
    void when_FilterPredicateIsDesired_then_manipulate() {
        // GIVEN

        // WHEN
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils
                .getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("mongoQueryManipulation")))
                .thenReturn(JsonNodeFactory.instance.nullNode());
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils
                .getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("filterJsonContent")))
                .thenReturn(JsonNodeFactory.instance.nullNode());
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils
                .getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("jsonContentFilterPredicate")))
                .thenReturn(jsonContentFilterPredicate);

        var result = dataManipulationHandler.manipulate(obligations).apply(data);

        // THEN
        StepVerifier.create(result).expectNextMatches(testUser -> assertTwoTestUsers(testUser, aaron)).expectComplete()
                .verify();

        constraintHandlerUtilsMock.verify(
                () -> ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), anyString()),
                times(2));
    }

    @Test
    void when_FilterJsonContentIsDesired_then_manipulate() {
        // GIVEN

        // WHEN
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils
                .getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("mongoQueryManipulation")))
                .thenReturn(JsonNodeFactory.instance.nullNode());
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils
                .getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("filterJsonContent")))
                .thenReturn(filterJsonContent);
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils
                .getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("jsonContentFilterPredicate")))
                .thenReturn(JsonNodeFactory.instance.nullNode());

        var result = dataManipulationHandler.manipulate(obligations).apply(data);

        // THEN
        StepVerifier.create(result).expectNextMatches(testUser -> {
            assertEquals(aaron.getId(), testUser.getId());
            assertEquals(0, testUser.getAge());
            assertEquals("Aa███", testUser.getFirstname());
            return true;
        }).expectNextMatches(testUser -> {
            assertEquals(brian.getId(), testUser.getId());
            assertEquals(0, testUser.getAge());
            assertEquals("Br███", testUser.getFirstname());
            return true;
        }).expectNextMatches(testUser -> {
            assertEquals(cathrin.getId(), testUser.getId());
            assertEquals(0, testUser.getAge());
            assertEquals("Ca█████", testUser.getFirstname());
            return true;
        }).expectComplete().verify();

        constraintHandlerUtilsMock.verify(
                () -> ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), anyString()),
                times(2));
    }

    @Test
    void when_FilterJsonContentAndFilterPredicateIsDesired_then_manipulate() {
        // GIVEN

        // WHEN
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils
                .getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("mongoQueryManipulation")))
                .thenReturn(JsonNodeFactory.instance.nullNode());
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils
                .getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("filterJsonContent")))
                .thenReturn(filterJsonContent);
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils
                .getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("jsonContentFilterPredicate")))
                .thenReturn(jsonContentFilterPredicate);

        var result = dataManipulationHandler.manipulate(obligations).apply(data);

        // THEN
        StepVerifier.create(result).expectNextMatches(testUser -> {
            assertEquals(aaron.getId(), testUser.getId());
            assertEquals(0, testUser.getAge());
            assertEquals("Aa███", testUser.getFirstname());
            return true;
        }).expectComplete().verify();

        constraintHandlerUtilsMock.verify(
                () -> ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), anyString()),
                times(2));
    }

    /**
     * Necessary, because the ObjectMapper changes the object reference. The
     * ObjectMapper is needed if a DomainObject has an ObjectId as type.
     */
    private boolean assertTwoTestUsers(TestUser first, TestUser second) {
        assertEquals(second.getId(), first.getId());
        assertEquals(second.getFirstname(), first.getFirstname());
        assertEquals(second.getAge(), first.getAge());
        return true;
    }
}
