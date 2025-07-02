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
package io.sapl.springdatamongoreactive.integration;

import java.io.IOException;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.annotation.DirtiesContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@AutoConfigureDataMongo
@SpringBootTest(properties = "de.flapdoodle.mongodb.embedded.version=8.0.5")
@EnableAutoConfiguration()
@DirtiesContext
@EntityScan(basePackages = "io.sapl.springdatamongoreactive.sapl.database")
@EnableReactiveMongoRepositories(basePackages = "io.sapl.springdatamongoreactive.integration")
class UserRepositoryIT {

    @Autowired
    private UserIntegrationTestsRepository repository;

    private static final ObjectMapper   MAPPER               = new ObjectMapper();
    private static final CollectionType LIST_TYPE            = MAPPER.getTypeFactory()
            .constructCollectionType(List.class, TestUser.class);
    private static final String         USERS_AS_JSON_STRING = """
            [
              {"id": "64de3bd9fbf82799677ed336", "firstname": "Rowat",        "age": 82, "admin": false},
              {"id": "64de3bd9fbf82799677ed338", "firstname": "Woodings",     "age": 96, "admin": true},
              {"id": "64de3bd9fbf82799677ed339", "firstname": "Bartolijn",    "age": 33, "admin": false},
              {"id": "64de3bd9fbf82799677ed33a", "firstname": "Hampton",      "age": 96, "admin": true},
              {"id": "64de3bd9fbf82799677ed33c", "firstname": "Streeton",     "age": 46, "admin": true},
              {"id": "64de3bd9fbf82799677ed33d", "firstname": "Tomaskov",     "age": 64, "admin": true},
              {"id": "64de3bd9fbf82799677ed342", "firstname": "Albinson",     "age": 54, "admin": false},
              {"id": "64de3bd9fbf82799677ed344", "firstname": "Morfell",      "age": 35, "admin": true},
              {"id": "64de3bd9fbf82799677ed345", "firstname": "Bickerstasse", "age": 66, "admin": true},
              {"id": "64de3bd9fbf82799677ed346", "firstname": "Angell",       "age": 94, "admin": false}
            ]
            """;

    @BeforeEach
    void setup() throws IOException {
        List<TestUser> testUsers = MAPPER.readValue(USERS_AS_JSON_STRING, LIST_TYPE);
        repository.deleteAll().thenMany(Flux.fromIterable(testUsers)).flatMap(repository::save).blockLast();
    }

    @Test
    void when_findAll_then_manipulateQuery() {
        // GIVEN
        final var testUser1    = new TestUser(new ObjectId("64de3bd9fbf82799677ed346"), null, 94, false);
        final var testUser2    = new TestUser(new ObjectId("64de3bd9fbf82799677ed339"), null, 33, false);
        final var testUser3    = new TestUser(new ObjectId("64de3bd9fbf82799677ed342"), null, 54, false);
        final var testUser4    = new TestUser(new ObjectId("64de3bd9fbf82799677ed336"), null, 82, false);
        final var testUserList = List.of(testUser1, testUser2, testUser3, testUser4);

        // WHEN
        final var testUserFlux = repository.findAll().collectList();

        StepVerifier.create(testUserFlux).expectNext(testUserList);
    }

    @Test
    void when_findAllByAgeAfter_then_manipulateQuery() {
        // GIVEN
        final var testUser1    = new TestUser(new ObjectId("64de3bd9fbf82799677ed336"), "Rowat", 0, false);
        final var testUser2    = new TestUser(new ObjectId("64de3bd9fbf82799677ed346"), "Angell", 0, false);
        final var testUserList = List.of(testUser1, testUser2);

        // WHEN
        final var testUserFlux = repository.findAllByAgeAfter(80, Pageable.ofSize(2)).collectList();

        // THEN
        StepVerifier.create(testUserFlux).expectNext(testUserList);
    }

    @Test
    void when_findAllByAgeAfter_then_fetchingByQueryMethod() {
        // GIVEN
        final var testUser1    = new TestUser(new ObjectId("64de3bd9fbf82799677ed346"), null, 94, false);
        final var testUser2    = new TestUser(new ObjectId("64de3bd9fbf82799677ed344"), null, 35, true);
        final var testUserList = List.of(testUser1, testUser2);

        // WHEN
        final var testUserFlux = repository.fetchingByQueryMethod("ll", Pageable.ofSize(2)).collectList();

        // THEN
        StepVerifier.create(testUserFlux).expectNext(testUserList);
    }

    @Test
    void when_findAllByAgeBefore_then_throwAccessDeniedException() {
        // GIVEN

        // WHEN
        final var testUserFlux = repository.findAllByAgeBefore(80);

        // THEN
        StepVerifier.create(testUserFlux).expectError(AccessDeniedException.class).verify();
    }
}
