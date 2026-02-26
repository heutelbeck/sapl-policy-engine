/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.data.mongo.integration;

import io.sapl.api.model.Value;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import io.sapl.spring.constraints.providers.ConstraintResponsibility;
import io.sapl.spring.data.mongo.integration.config.TestConfig;
import io.sapl.spring.data.mongo.sapl.database.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.CollectionType;

import java.util.List;
import java.util.function.UnaryOperator;

@Testcontainers
@DirtiesContext
@Import({ TestConfig.class, MongoResultConstraintHandlerIT.ConstraintHandlerConfig.class })
@SpringBootTest(classes = TestApplication.class, properties = { "io.sapl.pdp.embedded.enabled=true",
        "io.sapl.pdp.embedded.pdp-config-type=RESOURCES", "io.sapl.pdp.embedded.policies-path=policies-mongo",
        "spring.autoconfigure.exclude=org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration" })
class MongoResultConstraintHandlerIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:8.0");

    @Autowired
    UserIntegrationTestsRepository repository;

    private static final JsonMapper     MAPPER               = JsonMapper.builder().build();
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
    void setup() {
        List<TestUser> testUsers = MAPPER.readValue(USERS_AS_JSON_STRING, LIST_TYPE);
        repository.deleteAll().thenMany(Flux.fromIterable(testUsers)).flatMap(repository::save).blockLast();
    }

    @Test
    @DisplayName("failing mapping obligation handler propagates error")
    void when_queryWithFailingMappingObligation_thenErrorPropagated() {
        StepVerifier.create(repository.findUsersByAgeWithFailingMapping(20)).expectError(AccessDeniedException.class)
                .verify();
    }

    @TestConfiguration
    static class ConstraintHandlerConfig {

        @Bean
        MappingConstraintHandlerProvider<Object> testFailingMappingProvider() {
            return new MappingConstraintHandlerProvider<>() {
                @Override
                public boolean isResponsible(Value constraint) {
                    return ConstraintResponsibility.isResponsible(constraint, "testFailingMapping");
                }

                @Override
                public Class<Object> getSupportedType() {
                    return Object.class;
                }

                @Override
                public UnaryOperator<Object> getHandler(Value constraint) {
                    return obj -> {
                        throw new RuntimeException("Obligation handler failure");
                    };
                }
            };
        }
    }
}
