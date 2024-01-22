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
package io.sapl.springdatacommon.sapl.handlers;

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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.springdatacommon.handlers.DataManipulationHandler;
import io.sapl.springdatacommon.database.Person;
import io.sapl.springdatacommon.database.Role;
import io.sapl.springdatacommon.database.User;
import io.sapl.springdatacommon.sapl.utils.ConstraintHandlerUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class DataManipulationHandlerTests {

    DataManipulationHandler<Person>      dataManipulationHandlerPerson;
    MockedStatic<ConstraintHandlerUtils> constraintHandlerUtilsMock;

    final Person malinda = new Person(1, "Malinda", "Perrot", 53, Role.ADMIN, true);
    final Person emerson = new Person(2, "Emerson", "Rowat", 82, Role.USER, false);
    final Person yul     = new Person(3, "Yul", "Barukh", 79, Role.USER, true);

    final ObjectId malindaUserId = new ObjectId("5399aba6e4b0ae375bfdca88");
    final ObjectId emersonUserId = new ObjectId("5399aba6e4b0ae375bfdca88");
    final ObjectId yulUserId     = new ObjectId("5399aba6e4b0ae375bfdca88");

    final User malindaUser = new User(malindaUserId, "Malinda", 53, Role.ADMIN);
    final User emersonUser = new User(emersonUserId, "Emerson", 82, Role.USER);
    final User yulUser     = new User(yulUserId, "Yul", 79, Role.USER);

    final Flux<Person> data     = Flux.just(malinda, emerson, yul);
    final Flux<User>   dataUser = Flux.just(malindaUser, emersonUser, yulUser);

    static final ObjectMapper MAPPER = new ObjectMapper();
    static ArrayNode          OBLIGATIONS;
    static JsonNode           JSON_CONTENT_FILTER_PREDICATE;
    static JsonNode           FILTER_JSON_CONTENT;

    @BeforeAll
    public static void beforeAll() throws JsonProcessingException {
        OBLIGATIONS                   = MAPPER.readValue("""
                    		[
                  {
                    "type": "r2dbcQueryManipulation",
                    "conditions": [
                      "{'role':  {'$in': ['USER']}}"
                    ]
                  },
                  {
                    "type": "filterJsonContent",
                    "actions": [
                      {
                        "type": "blacken",
                        "path": "$.firstname",
                        "discloseLeft": 2
                      }
                    ]
                  },
                  {
                    "type": "jsonContentFilterPredicate",
                    "conditions": [
                      {
                        "type": "==",
                        "path": "$.id",
                        "value": "a1"
                      }
                    ]
                  }
                ]
                    		""", ArrayNode.class);
        JSON_CONTENT_FILTER_PREDICATE = MAPPER.readTree("""
                    		{
                  "type": "jsonContentFilterPredicate",
                  "conditions": [
                    {
                      "type": "==",
                      "path": "$.firstname",
                      "value": "Malinda"
                    }
                  ]
                }
                    		""");
        FILTER_JSON_CONTENT           = MAPPER.readTree("""
                    		{
                  "type": "filterJsonContent",
                  "actions": [
                    {
                      "type": "blacken",
                      "path": "$.firstname",
                      "discloseLeft": 2
                    },
                    {
                      "type": "delete",
                      "path": "$.age"
                    }
                  ]
                }
                    		""");
    }

    @BeforeEach
    public void beforeEach() {
        constraintHandlerUtilsMock    = mockStatic(ConstraintHandlerUtils.class);
        dataManipulationHandlerPerson = new DataManipulationHandler<>(Person.class, true);
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

        var result = dataManipulationHandlerPerson.manipulate(OBLIGATIONS).apply(data);

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
                .thenReturn(JSON_CONTENT_FILTER_PREDICATE);

        var result = dataManipulationHandlerPerson.manipulate(OBLIGATIONS).apply(data);

        // THEN
        StepVerifier.create(result).expectNextMatches(user -> assertTwoPersons(user, malinda)).expectComplete()
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
                .thenReturn(FILTER_JSON_CONTENT);
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils
                .getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("jsonContentFilterPredicate")))
                .thenReturn(JsonNodeFactory.instance.nullNode());

        var result = dataManipulationHandlerPerson.manipulate(OBLIGATIONS).apply(data);

        // THEN
        StepVerifier.create(result).expectNextMatches(testUser -> {
            assertEquals(malinda.getId(), testUser.getId());
            assertEquals(0, testUser.getAge());
            assertEquals("Ma█████", testUser.getFirstname());
            assertEquals(malinda.getLastname(), testUser.getLastname());
            assertEquals(malinda.getRole(), testUser.getRole());
            return true;
        }).expectNextMatches(testUser -> {
            assertEquals(emerson.getId(), testUser.getId());
            assertEquals(0, testUser.getAge());
            assertEquals("Em█████", testUser.getFirstname());
            assertEquals(emerson.getLastname(), testUser.getLastname());
            assertEquals(emerson.getRole(), testUser.getRole());
            return true;
        }).expectNextMatches(testUser -> {
            assertEquals(yul.getId(), testUser.getId());
            assertEquals(0, testUser.getAge());
            assertEquals("Yu█", testUser.getFirstname());
            assertEquals(yul.getLastname(), testUser.getLastname());
            assertEquals(yul.getRole(), testUser.getRole());
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
                .thenReturn(FILTER_JSON_CONTENT);
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils
                .getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("jsonContentFilterPredicate")))
                .thenReturn(JSON_CONTENT_FILTER_PREDICATE);

        var result = dataManipulationHandlerPerson.manipulate(OBLIGATIONS).apply(data);

        // THEN
        StepVerifier.create(result).expectNextMatches(testUser -> {
            assertEquals(malinda.getId(), testUser.getId());
            assertEquals(0, testUser.getAge());
            assertEquals("Ma█████", testUser.getFirstname());
            assertEquals(malinda.getLastname(), testUser.getLastname());
            assertEquals(malinda.getRole(), testUser.getRole());
            return true;
        }).expectComplete().verify();

        constraintHandlerUtilsMock.verify(
                () -> ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), anyString()),
                times(2));
    }

    @Test
    void when_FilterJsonContentAndFilterPredicateIsDesired_then_manipulateForMongoReactive() {
        // GIVEN
        var dataManipulationHandlerUser = new DataManipulationHandler<>(User.class, false);

        // WHEN
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils
                .getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("mongoQueryManipulation")))
                .thenReturn(JsonNodeFactory.instance.nullNode());
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils
                .getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("filterJsonContent")))
                .thenReturn(FILTER_JSON_CONTENT);
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils
                .getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("jsonContentFilterPredicate")))
                .thenReturn(JSON_CONTENT_FILTER_PREDICATE);

        var result = dataManipulationHandlerUser.manipulate(OBLIGATIONS).apply(dataUser);

        // THEN
        StepVerifier.create(result).expectNextMatches(testUser -> {
            assertEquals(malindaUser.getId(), testUser.getId());
            assertEquals(0, testUser.getAge());
            assertEquals("Ma█████", testUser.getFirstname());
            assertEquals(malindaUser.getRole(), testUser.getRole());
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
        assertEquals(first.getId(), second.getId());
        assertEquals(first.getFirstname(), second.getFirstname());
        assertEquals(first.getLastname(), second.getLastname());
        assertEquals(first.getRole(), second.getRole());
        assertEquals(first.getAge(), second.getAge());

        return true;
    }

}
