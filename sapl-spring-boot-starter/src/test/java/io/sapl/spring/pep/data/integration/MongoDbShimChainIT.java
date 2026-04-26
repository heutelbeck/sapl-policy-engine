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
package io.sapl.spring.pep.data.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Service;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.config.EnableReactiveSaplMethodSecurity;
import io.sapl.spring.method.metadata.PreEnforce;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Engine-side end-to-end test of the Mongo shim chain. Mirrors
 * {@link RelationalShimChainIT} for MongoDB. Same scenario, different
 * collection: chronicles of Krynn (Astinus's annals) tagged by moon and
 * forbidden tier; the obligation filters access by moon alignment.
 * <p>
 * Uses a real MongoDB via Testcontainers (matching the
 * {@code queryrewriting-mongodb-reactive} demo's setup) so the proxy chain is
 * exercised against actual driver behaviour, not a mock template.
 */
@SpringBootTest(classes = MongoDbShimChainIT.AstinusChroniclesTestApp.class)
@Testcontainers
@WithMockUser(username = "raistlin", roles = "BLACK_ROBE")
class MongoDbShimChainIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongoContainer = new MongoDBContainer("mongo:8.0");

    private static final String SOLINARI = "Solinari";
    private static final String LUNITARI = "Lunitari";
    private static final String NUITARI  = "Nuitari";

    private static final Duration STEP_TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    ChronicleService chronicles;

    @Autowired
    ReactiveMongoTemplate mongoTemplate;

    @MockitoBean
    PolicyDecisionPoint pdp;

    @BeforeEach
    void resetCollection() {
        mongoTemplate.dropCollection(Chronicle.class).block(STEP_TIMEOUT);
        mongoTemplate.insertAll(java.util.List.of(new Chronicle("1", "The Disks of Mishakal", SOLINARI, 0),
                new Chronicle("2", "The Bestiary of Krynn", SOLINARI, 1),
                new Chronicle("3", "Songs of the Bards", LUNITARI, 0),
                new Chronicle("4", "The Lost Chronicles", LUNITARI, 2),
                new Chronicle("5", "The Black Wing Rite", NUITARI, 3),
                new Chronicle("6", "Necromancers' Compendium", NUITARI, 4))).blockLast(STEP_TIMEOUT);
    }

    @Nested
    @DisplayName("Tenant isolation: a single-criterion obligation filters documents by moon")
    class MoonRestriction {

        @Test
        @DisplayName("Black Robe sees only Nuitari-aligned chronicles")
        void whenObligationFiltersByMoonThenOnlyMatchingDocumentsReturned() {
            decide(decisionWithMongoCriteria(eqColumn("moon", NUITARI)));

            StepVerifier.create(chronicles.allChronicles().map(Chronicle::title).collectList())
                    .assertNext(titles -> assertThat(titles).containsExactlyInAnyOrder("The Black Wing Rite",
                            "Necromancers' Compendium"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Lunitari user sees neither Solinari nor Nuitari chronicles")
        void whenObligationFiltersByDifferentMoonThenOnlyThoseDocumentsReturned() {
            decide(decisionWithMongoCriteria(eqColumn("moon", LUNITARI)));

            StepVerifier.create(chronicles.allChronicles().map(Chronicle::title).collectList()).assertNext(
                    titles -> assertThat(titles).containsExactlyInAnyOrder("Songs of the Bards", "The Lost Chronicles"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Composition: obligation criteria AND-combined with original Spring Data criteria")
    class Composition {

        @Test
        @DisplayName("Repository derived query for forbiddenTier <= 1 is intersected with obligation moon=Solinari")
        void whenOriginalQueryAndObligationThenIntersectedDocumentsReturned() {
            decide(decisionWithMongoCriteria(eqColumn("moon", SOLINARI)));

            StepVerifier.create(chronicles.notMoreForbiddenThan(1).map(Chronicle::title).collectList())
                    .assertNext(titles -> assertThat(titles).containsExactlyInAnyOrder("The Disks of Mishakal",
                            "The Bestiary of Krynn"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Decisions other than PERMIT")
    class Denial {

        @Test
        @DisplayName("DENY raises AccessDeniedException")
        void whenDenyThenAccessDenied() {
            decide(AuthorizationDecision.DENY);

            StepVerifier.create(chronicles.allChronicles())
                    .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(AccessDeniedException.class))
                    .verify(STEP_TIMEOUT);
        }
    }

    @Nested
    @DisplayName("Pass-through: PERMIT without obligations returns all documents (proxy in path, no transform)")
    class PassThrough {

        @Test
        @DisplayName("PERMIT without obligations returns all six chronicles")
        void whenPermitWithoutObligationThenAllDocumentsReturned() {
            decide(AuthorizationDecision.PERMIT);

            StepVerifier.create(chronicles.allChronicles().count()).expectNext(6L).verifyComplete();
        }
    }

    @Nested
    @DisplayName("Fail-closed: malformed obligation does not silently bypass the proxy")
    class FailClosed {

        @Test
        @DisplayName("Obligation with unsupported operator is unresolved by the provider; planner injects failure")
        void whenObligationOperatorUnsupportedThenAccessDenied() {
            val brokenCriterion = Value.ofObject(Map.of("column", Value.of("moon"), "op",
                    Value.of("is_secretly_equal_to_for_legal_reasons"), "value", Value.of(NUITARI)));
            decide(decisionWithMongoCriteria(brokenCriterion));

            StepVerifier.create(chronicles.allChronicles())
                    .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(AccessDeniedException.class))
                    .verify(STEP_TIMEOUT);
        }
    }

    private void decide(AuthorizationDecision decision) {
        when(pdp.decideOnce(any())).thenReturn(Mono.just(decision));
    }

    private static AuthorizationDecision decisionWithMongoCriteria(ObjectValue... criteria) {
        val obligation = Value
                .ofObject(Map.of("type", Value.of("mongo:queryManipulation"), "criteria", arrayOf(criteria)));
        return new AuthorizationDecision(Decision.PERMIT, Value.ofArray(obligation), Value.EMPTY_ARRAY,
                Value.UNDEFINED);
    }

    private static ObjectValue eqColumn(String column, String value) {
        return Value.ofObject(Map.of("column", Value.of(column), "op", Value.of("="), "value", Value.of(value)));
    }

    private static ArrayValue arrayOf(ObjectValue... values) {
        return Value.ofArray((Value[]) values);
    }

    @Document("chronicles")
    public record Chronicle(@Id String id, String title, String moon, Integer forbiddenTier) {}

    @Service
    static class ChronicleService {

        private final MongoChronicleRepository repository;

        ChronicleService(MongoChronicleRepository repository) {
            this.repository = repository;
        }

        @PreEnforce(action = "'allChronicles'")
        public Flux<Chronicle> allChronicles() {
            return repository.findAll();
        }

        @PreEnforce(action = "'notMoreForbiddenThan'")
        public Flux<Chronicle> notMoreForbiddenThan(int maxTier) {
            return repository.findByForbiddenTierLessThanEqual(maxTier);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableReactiveSaplMethodSecurity
    @EnableReactiveMongoRepositories(basePackageClasses = MongoChronicleRepository.class)
    static class AstinusChroniclesTestApp {

        @Bean
        ChronicleService chronicleService(MongoChronicleRepository repository) {
            return new ChronicleService(repository);
        }
    }
}
