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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Service;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.ReactiveTransactionManager;

import io.r2dbc.h2.H2ConnectionConfiguration;
import io.r2dbc.h2.H2ConnectionFactory;
import io.r2dbc.h2.H2ConnectionOption;
import io.r2dbc.spi.ConnectionFactory;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.config.EnableReactiveSaplMethodSecurity;
import io.sapl.spring.method.metadata.PreEnforce;
import io.sapl.spring.pep.data.integration.RelationalShimChainIT.Tome;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

/**
 * Documents whether the
 * {@link io.sapl.spring.pep.constraints.EnforcementPlanContext}
 * propagates across Reactor scheduler hops. Reactor's {@code Context} is
 * supposed to follow the subscription regardless of which scheduler an operator
 * runs on, so the shim proxy should still see the active plan when a service's
 * downstream pipeline is published or subscribed on a different scheduler.
 * <p>
 * Scenario continues from {@link RelationalShimChainIT}: same Palanthas
 * library, same Tome data, same {@code relational:queryManipulation}
 * obligation. The service method introduces a deliberate scheduler hop between
 * the {@code @PreEnforce}-annotated boundary and the repository call.
 */
@SpringBootTest(classes = ReactorContextPropagationTests.PalanthasLibraryTestApp.class)
@WithMockUser(username = "raistlin", roles = "BLACK_ROBE")
class ReactorContextPropagationTests {

    private static final String NUITARI = "Nuitari";

    private static final Duration STEP_TIMEOUT = Duration.ofSeconds(5);

    @Autowired
    LibraryService library;

    @Autowired
    DatabaseClient databaseClient;

    @MockitoBean
    PolicyDecisionPoint pdp;

    @BeforeEach
    void resetSchema() {
        databaseClient.sql(
                "CREATE TABLE IF NOT EXISTS tome(id INT PRIMARY KEY, title VARCHAR(255), moon VARCHAR(32), forbidden_tier INT)")
                .fetch().rowsUpdated().block(STEP_TIMEOUT);
        databaseClient.sql("DELETE FROM tome").fetch().rowsUpdated().block(STEP_TIMEOUT);
        seed(1, "The Disks of Mishakal", "Solinari", 0);
        seed(2, "The Bestiary of Krynn", "Solinari", 1);
        seed(3, "Songs of the Bards", "Lunitari", 0);
        seed(4, "The Lost Chronicles", "Lunitari", 2);
        seed(5, "The Black Wing Rite", NUITARI, 3);
        seed(6, "Necromancers' Compendium", NUITARI, 4);
    }

    private void seed(int id, String title, String moon, int forbiddenTier) {
        databaseClient.sql("INSERT INTO tome(id, title, moon, forbidden_tier) VALUES (:id, :title, :moon, :tier)")
                .bind("id", id).bind("title", title).bind("moon", moon).bind("tier", forbiddenTier).fetch()
                .rowsUpdated().block(STEP_TIMEOUT);
    }

    @Nested
    @DisplayName("Plan reaches the shim proxy after a publishOn(parallel) hop")
    class PublishOnParallel {

        @Test
        @DisplayName("Obligation moon=Nuitari is still applied when the downstream is published on parallel scheduler")
        void whenPublishOnParallelInsideEnforceScopeThenObligationStillApplied() {
            decide(decisionWithRelationalCriteria(eqColumn("moon", NUITARI)));

            StepVerifier.create(library.tomesViaPublishOnParallel().map(Tome::title).collectList())
                    .assertNext(titles -> assertThat(titles).containsExactlyInAnyOrder("The Black Wing Rite",
                            "Necromancers' Compendium"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Plan reaches the shim proxy after a subscribeOn(boundedElastic) hop")
    class SubscribeOnBoundedElastic {

        @Test
        @DisplayName("Obligation moon=Nuitari is still applied when the upstream is subscribed on boundedElastic scheduler")
        void whenSubscribeOnBoundedElasticInsideEnforceScopeThenObligationStillApplied() {
            decide(decisionWithRelationalCriteria(eqColumn("moon", NUITARI)));

            StepVerifier.create(library.tomesViaSubscribeOnBoundedElastic().map(Tome::title).collectList())
                    .assertNext(titles -> assertThat(titles).containsExactlyInAnyOrder("The Black Wing Rite",
                            "Necromancers' Compendium"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Plan reaches the shim proxy after a deeply nested operator chain that includes a scheduler hop")
    class NestedFlatMapWithSchedulerHop {

        @Test
        @DisplayName("Obligation is still applied across nested flatMaps that switch schedulers between them")
        void whenNestedFlatMapWithSchedulerHopThenObligationStillApplied() {
            decide(decisionWithRelationalCriteria(eqColumn("moon", NUITARI)));

            StepVerifier.create(library.tomesViaNestedHop().map(Tome::title).collectList())
                    .assertNext(titles -> assertThat(titles).containsExactlyInAnyOrder("The Black Wing Rite",
                            "Necromancers' Compendium"))
                    .verifyComplete();
        }
    }

    private void decide(AuthorizationDecision decision) {
        when(pdp.decideOnce(any())).thenReturn(Mono.just(decision));
    }

    private static AuthorizationDecision decisionWithRelationalCriteria(ObjectValue criterion) {
        val obligation = Value.ofObject(
                Map.of("type", Value.of("relational:queryManipulation"), "criteria", Value.ofArray(criterion)));
        return new AuthorizationDecision(Decision.PERMIT, Value.ofArray(obligation), Value.EMPTY_ARRAY,
                Value.UNDEFINED);
    }

    private static ObjectValue eqColumn(String column, String value) {
        return Value.ofObject(Map.of("column", Value.of(column), "op", Value.of("="), "value", Value.of(value)));
    }

    @Service
    static class LibraryService {

        private final TomeRepository repository;

        LibraryService(TomeRepository repository) {
            this.repository = repository;
        }

        @PreEnforce(action = "'tomesViaPublishOnParallel'")
        public Flux<Tome> tomesViaPublishOnParallel() {
            return repository.findAll().publishOn(Schedulers.parallel());
        }

        @PreEnforce(action = "'tomesViaSubscribeOnBoundedElastic'")
        public Flux<Tome> tomesViaSubscribeOnBoundedElastic() {
            return repository.findAll().subscribeOn(Schedulers.boundedElastic());
        }

        @PreEnforce(action = "'tomesViaNestedHop'")
        public Flux<Tome> tomesViaNestedHop() {
            return Mono.just("trigger").publishOn(Schedulers.parallel())
                    .flatMapMany(ignored -> Mono.fromCallable(() -> "next").subscribeOn(Schedulers.boundedElastic())
                            .flatMapMany(ignored2 -> repository.findAll()));
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableReactiveSaplMethodSecurity
    @org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories(basePackageClasses = TomeRepository.class)
    static class PalanthasLibraryTestApp {

        @Bean
        ConnectionFactory connectionFactory() {
            return new H2ConnectionFactory(H2ConnectionConfiguration.builder().inMemory("sapl-context-propagation")
                    .property(H2ConnectionOption.DB_CLOSE_DELAY, "-1").build());
        }

        @Bean
        @Primary
        ReactiveTransactionManager reactiveTransactionManager(ConnectionFactory connectionFactory) {
            return new R2dbcTransactionManager(connectionFactory);
        }

        @Bean
        DatabaseClient databaseClient(ConnectionFactory connectionFactory) {
            return DatabaseClient.create(connectionFactory);
        }

        @Bean
        LibraryService libraryService(TomeRepository repository) {
            return new LibraryService(repository);
        }
    }
}
