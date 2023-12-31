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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.springdatar2dbc.database.Person;
import io.sapl.springdatar2dbc.database.Role;
import io.sapl.springdatar2dbc.sapl.utils.ConstraintHandlerUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class DataManipulationHandlerTest {

    DataManipulationHandler<Person>      dataManipulationHandler;
    MockedStatic<ConstraintHandlerUtils> constraintHandlerUtilsMock;

    final Person malinda = new Person(1, "Malinda", "Perrot", 53, Role.ADMIN, true);
    final Person emerson = new Person(2, "Emerson", "Rowat", 82, Role.USER, false);
    final Person yul     = new Person(3, "Yul", "Barukh", 79, Role.USER, true);

    final Flux<Person> data = Flux.just(malinda, emerson, yul);

    final static ObjectMapper objectMapper = new ObjectMapper();
    static JsonNode           obligations;
    static JsonNode           jsonContentFilterPredicate;
    static JsonNode           filterJsonContent;

    @BeforeAll
    public static void beforeAll() throws JsonProcessingException {
        obligations                = objectMapper.readTree(
                "[{\"type\":\"r2dbcQueryManipulation\",\"conditions\":[\"{'role':  {'$in': ['USER']}}\"]},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
        jsonContentFilterPredicate = objectMapper.readTree(
                "{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.firstname\",\"value\":\"Malinda\"}]}");
        filterJsonContent          = objectMapper.readTree(
                "{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2},{\"type\":\"delete\",\"path\":\"$.age\"}]}");
    }

    @BeforeEach
    public void beforeEach() {
        constraintHandlerUtilsMock = mockStatic(ConstraintHandlerUtils.class);
        dataManipulationHandler    = new DataManipulationHandler<>(Person.class);
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
        StepVerifier.create(result).expectNextMatches(testUser -> assertTwoPersons(testUser, malinda))
                .expectNextMatches(testUser -> assertTwoPersons(testUser, emerson))
                .expectNextMatches(testUser -> assertTwoPersons(testUser, yul)).expectComplete().verify();

        constraintHandlerUtilsMock.verify(
                () -> ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), anyString()),
                times(2));
    }

    @Test
    void when_FilterPredicateIsDesired_then_manipulate() {
        // GIVEN

        // WHEN
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils
                .getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("r2dbcQueryManipulation")))
                .thenReturn(JsonNodeFactory.instance.nullNode());
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils
                .getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("filterJsonContent")))
                .thenReturn(JsonNodeFactory.instance.nullNode());
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils
                .getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("jsonContentFilterPredicate")))
                .thenReturn(jsonContentFilterPredicate);

        var result = dataManipulationHandler.manipulate(obligations).apply(data);

        // THEN
        StepVerifier.create(result).expectNextMatches(person -> assertTwoPersons(person, malinda)).expectComplete()
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
                .getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("r2dbcQueryManipulation")))
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
            Assertions.assertEquals(malinda.getId(), testUser.getId());
            Assertions.assertEquals(0, testUser.getAge());
            Assertions.assertEquals("Ma█████", testUser.getFirstname());
            Assertions.assertEquals(malinda.getLastname(), testUser.getLastname());
            Assertions.assertEquals(malinda.getRole(), testUser.getRole());
            return true;
        }).expectNextMatches(testUser -> {
            Assertions.assertEquals(emerson.getId(), testUser.getId());
            Assertions.assertEquals(0, testUser.getAge());
            Assertions.assertEquals("Em█████", testUser.getFirstname());
            Assertions.assertEquals(emerson.getLastname(), testUser.getLastname());
            Assertions.assertEquals(emerson.getRole(), testUser.getRole());
            return true;
        }).expectNextMatches(testUser -> {
            Assertions.assertEquals(yul.getId(), testUser.getId());
            Assertions.assertEquals(0, testUser.getAge());
            Assertions.assertEquals("Yu█", testUser.getFirstname());
            Assertions.assertEquals(yul.getLastname(), testUser.getLastname());
            Assertions.assertEquals(yul.getRole(), testUser.getRole());
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
                .getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("r2dbcQueryManipulation")))
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
            Assertions.assertEquals(malinda.getId(), testUser.getId());
            Assertions.assertEquals(0, testUser.getAge());
            Assertions.assertEquals("Ma█████", testUser.getFirstname());
            Assertions.assertEquals(malinda.getLastname(), testUser.getLastname());
            Assertions.assertEquals(malinda.getRole(), testUser.getRole());
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
    private boolean assertTwoPersons(Person first, Person second) {
        Assertions.assertEquals(first.getId(), second.getId());
        Assertions.assertEquals(first.getFirstname(), second.getFirstname());
        Assertions.assertEquals(first.getLastname(), second.getLastname());
        Assertions.assertEquals(first.getRole(), second.getRole());
        Assertions.assertEquals(first.getAge(), second.getAge());

        return true;
    }
}
