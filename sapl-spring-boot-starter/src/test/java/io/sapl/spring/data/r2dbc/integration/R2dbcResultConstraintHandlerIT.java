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
package io.sapl.spring.data.r2dbc.integration;

import io.sapl.api.model.Value;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import io.sapl.spring.constraints.providers.ConstraintResponsibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import reactor.test.StepVerifier;

import java.util.function.UnaryOperator;

@Import({ TestConfig.class, R2dbcResultConstraintHandlerIT.ConstraintHandlerConfig.class })
@SpringBootTest(classes = { TestApplication.class }, properties = { "io.sapl.pdp.embedded.enabled=true",
        "io.sapl.pdp.embedded.pdp-config-type=RESOURCES", "io.sapl.pdp.embedded.policies-path=policies-r2dbc",
        "spring.autoconfigure.exclude=de.flapdoodle.embed.mongo.spring.autoconfigure.EmbeddedMongoAutoConfiguration,"
                + "org.springframework.boot.data.mongodb.autoconfigure.MongoReactiveDataAutoConfiguration,"
                + "org.springframework.boot.mongodb.autoconfigure.MongoReactiveAutoConfiguration" })
class R2dbcResultConstraintHandlerIT extends TestContainerBase {

    @Autowired
    PersonIntegrationTestsRepository repository;

    @Test
    @DisplayName("failing mapping obligation handler propagates error")
    void when_queryWithFailingMappingObligation_thenErrorPropagated() {
        StepVerifier.create(repository.findPeopleByAgeWithFailingMapping(20)).expectError(AccessDeniedException.class)
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
