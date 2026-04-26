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
import java.util.Collection;
import java.util.List;
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
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Service;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.ReactiveTransactionManager;

import io.r2dbc.h2.H2ConnectionConfiguration;
import io.r2dbc.h2.H2ConnectionFactory;
import io.r2dbc.h2.H2ConnectionOption;
import io.r2dbc.spi.ConnectionFactory;
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
 * Engine-side end-to-end test of the R2DBC shim chain: a {@code @PreEnforce}
 * service method calls a reactive Spring Data repository whose underlying
 * {@code R2dbcEntityTemplate} bean has been wrapped by the
 * {@code R2dbcShimBeanPostProcessor}. The PDP returns a decision carrying a
 * {@code relational:queryManipulation} obligation; the
 * {@code RelationalQueryManipulationProvider} produces a Mapper that rewrites
 * the {@code Query} at the {@code RelationalQueryShimSignal}; the rewritten
 * query reaches H2 and the result set is asserted on real rows.
 * <p>
 * Scenario: the Great Library of Palanthas. Tomes are catalogued by moon
 * alignment (Solinari, Lunitari, Nuitari) and a forbidden tier. Robe-color
 * users see only tomes their moon permits.
 */
@SpringBootTest(classes = RelationalShimChainIT.PalanthasLibraryTestApp.class)
@WithMockUser(username = "raistlin", roles = "BLACK_ROBE")
class RelationalShimChainIT {

    private static final String SOLINARI = "Solinari";
    private static final String LUNITARI = "Lunitari";
    private static final String NUITARI  = "Nuitari";

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
        seed(1, "The Disks of Mishakal", SOLINARI, 0);
        seed(2, "The Bestiary of Krynn", SOLINARI, 1);
        seed(3, "Songs of the Bards", LUNITARI, 0);
        seed(4, "The Lost Chronicles", LUNITARI, 2);
        seed(5, "The Black Wing Rite", NUITARI, 3);
        seed(6, "Necromancers' Compendium", NUITARI, 4);
    }

    private void seed(int id, String title, String moon, int forbiddenTier) {
        databaseClient.sql("INSERT INTO tome(id, title, moon, forbidden_tier) VALUES (:id, :title, :moon, :tier)")
                .bind("id", id).bind("title", title).bind("moon", moon).bind("tier", forbiddenTier).fetch()
                .rowsUpdated().block(STEP_TIMEOUT);
    }

    @Nested
    @DisplayName("Tenant isolation: a single-criterion obligation filters rows by moon")
    class MoonRestriction {

        @Test
        @DisplayName("Black Robe sees only Nuitari-aligned tomes")
        void whenObligationFiltersByMoonThenOnlyMatchingRowsReturned() {
            decide(decisionWithRelationalCriteria(eqColumn("moon", NUITARI)));

            StepVerifier
                    .create(library.allTomes().map(Tome::title).collectList()).assertNext(titles -> assertThat(titles)
                            .containsExactlyInAnyOrder("The Black Wing Rite", "Necromancers' Compendium"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Lunitari user sees neither Solinari nor Nuitari tomes")
        void whenObligationFiltersByDifferentMoonThenOnlyThoseRowsReturned() {
            decide(decisionWithRelationalCriteria(eqColumn("moon", LUNITARI)));

            StepVerifier.create(library.allTomes().map(Tome::title).collectList()).assertNext(
                    titles -> assertThat(titles).containsExactlyInAnyOrder("Songs of the Bards", "The Lost Chronicles"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Composition: obligation criteria AND-combined with original Spring Data criteria")
    class Composition {

        @Test
        @DisplayName("Repository derived query for forbiddenTier <= 1 is intersected with obligation moon=Solinari")
        void whenOriginalQueryAndObligationThenIntersectedRowsReturned() {
            decide(decisionWithRelationalCriteria(eqColumn("moon", SOLINARI)));

            StepVerifier.create(library.tomesNotMoreForbiddenThan(1).map(Tome::title).collectList())
                    .assertNext(titles -> assertThat(titles).containsExactlyInAnyOrder("The Disks of Mishakal",
                            "The Bestiary of Krynn"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Decisions other than PERMIT")
    class Denial {

        @Test
        @DisplayName("DENY raises AccessDeniedException with no DB query made")
        void whenDenyThenAccessDeniedAndNoQuery() {
            decide(AuthorizationDecision.DENY);

            StepVerifier.create(library.allTomes())
                    .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(AccessDeniedException.class))
                    .verify(STEP_TIMEOUT);
        }
    }

    @Nested
    @DisplayName("Pass-through: no obligation handler claims the constraint")
    class PassThrough {

        @Test
        @DisplayName("PERMIT without obligations returns all six tomes (proxy is in-path but does not transform)")
        void whenPermitWithoutObligationThenAllRowsReturned() {
            decide(AuthorizationDecision.PERMIT);

            StepVerifier.create(library.allTomes().count()).expectNext(6L).verifyComplete();
        }
    }

    @Nested
    @DisplayName("Complex obligations: OR-groups, IN-lists, LIKE patterns, nested OR-of-AND, multi-criteria")
    class ComplexObligations {

        @Test
        @DisplayName("OR-group obligation on findById: only tomes whose moon is in the Conclave's permitted set are returned")
        void whenOrGroupObligationOnFindByIdThenOnlyAllowedMoonReturned() {
            decide(decisionWithRelationalCriteria(orGroup(eqColumn("moon", SOLINARI), eqColumn("moon", LUNITARI))));

            StepVerifier.create(library.tomeById(1).map(Tome::title)).expectNext("The Disks of Mishakal")
                    .verifyComplete();
        }

        @Test
        @DisplayName("OR-group obligation on findById: lookup of disallowed moon yields empty Mono")
        void whenOrGroupObligationOnFindByIdAndDisallowedMoonThenEmpty() {
            decide(decisionWithRelationalCriteria(orGroup(eqColumn("moon", SOLINARI), eqColumn("moon", LUNITARI))));

            StepVerifier.create(library.tomeById(5)).verifyComplete();
        }

        @Test
        @DisplayName("IN-list obligation on countByMoon: count of allowed moon equals seeded rows for that moon")
        void whenInListObligationOnCountAllowedMoonThenSeededCount() {
            decide(decisionWithRelationalCriteria(inColumn("moon", SOLINARI, LUNITARI)));

            StepVerifier.create(library.countByMoon(LUNITARI)).expectNext(2L).verifyComplete();
        }

        @Test
        @DisplayName("IN-list obligation on countByMoon: count of moon outside the allowed set is zero")
        void whenInListObligationOnCountDisallowedMoonThenZero() {
            decide(decisionWithRelationalCriteria(inColumn("moon", SOLINARI, LUNITARI)));

            StepVerifier.create(library.countByMoon(NUITARI)).expectNext(0L).verifyComplete();
        }

        @Test
        @DisplayName("Nested OR-of-AND obligation imposes per-moon tier ceilings on a multi-condition derived query")
        void whenNestedOrOfAndObligationThenPerMoonTierCeilingApplied() {
            val perMoonCeilings = orGroup(andGroup(eqColumn("moon", SOLINARI), cmpColumn("forbidden_tier", "<=", 0)),
                    andGroup(eqColumn("moon", LUNITARI), cmpColumn("forbidden_tier", "<=", 2)),
                    andGroup(eqColumn("moon", NUITARI), cmpColumn("forbidden_tier", "<=", 3)));
            decide(decisionWithRelationalCriteria(perMoonCeilings));

            StepVerifier.create(library.tomesAtMoonNotMoreForbiddenThan(SOLINARI, 5).map(Tome::title).collectList())
                    .assertNext(titles -> assertThat(titles).containsExactly("The Disks of Mishakal")).verifyComplete();
        }

        @Test
        @DisplayName("LIKE obligation intersects with @Query LIKE: empty result when patterns disjoint")
        void whenLikeObligationDisjointFromUserPatternThenEmpty() {
            decide(decisionWithRelationalCriteria(likeColumn("title", "%Compendium%")));

            StepVerifier.create(library.tomesByTitle("%Chronicles%").collectList())
                    .assertNext(titles -> assertThat(titles).isEmpty()).verifyComplete();
        }

        @Test
        @DisplayName("LIKE obligation intersects with @Query LIKE: row whose title matches both patterns is returned")
        void whenLikeObligationIntersectsUserPatternThenMatchingRowReturned() {
            decide(decisionWithRelationalCriteria(likeColumn("title", "%Compendium%")));

            StepVerifier.create(library.tomesByTitle("%Necromancers%").map(Tome::title).collectList())
                    .assertNext(titles -> assertThat(titles).containsExactly("Necromancers' Compendium"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Multi-criteria obligation (IN + isNotNull) preserves derived-query ORDER BY")
        void whenMultiCriteriaObligationOnInOrderByThenIntersectedAndOrdered() {
            decide(decisionWithRelationalCriteria(inColumn("moon", SOLINARI, LUNITARI),
                    isNotNullColumn("forbidden_tier")));

            StepVerifier
                    .create(library.tomesByMoonsRanked(List.of(SOLINARI, LUNITARI, NUITARI)).map(Tome::title)
                            .collectList())
                    .assertNext(titles -> assertThat(titles).containsExactly("The Lost Chronicles",
                            "The Bestiary of Krynn", "The Disks of Mishakal", "Songs of the Bards"))
                    .verifyComplete();
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
            decide(decisionWithRelationalCriteria(brokenCriterion));

            StepVerifier.create(library.allTomes())
                    .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(AccessDeniedException.class))
                    .verify(STEP_TIMEOUT);
        }
    }

    private void decide(AuthorizationDecision decision) {
        when(pdp.decideOnce(any())).thenReturn(Mono.just(decision));
    }

    private static AuthorizationDecision decisionWithRelationalCriteria(ObjectValue... criteria) {
        val obligation = Value
                .ofObject(Map.of("type", Value.of("relational:queryManipulation"), "criteria", arrayOf(criteria)));
        return new AuthorizationDecision(Decision.PERMIT, Value.ofArray(obligation), Value.EMPTY_ARRAY,
                Value.UNDEFINED);
    }

    private static ObjectValue eqColumn(String column, String value) {
        return Value.ofObject(Map.of("column", Value.of(column), "op", Value.of("="), "value", Value.of(value)));
    }

    private static ObjectValue cmpColumn(String column, String op, int value) {
        return Value.ofObject(Map.of("column", Value.of(column), "op", Value.of(op), "value", Value.of(value)));
    }

    private static ObjectValue inColumn(String column, String... values) {
        val arr = new Value[values.length];
        for (int i = 0; i < values.length; i++) {
            arr[i] = Value.of(values[i]);
        }
        return Value.ofObject(Map.of("column", Value.of(column), "op", Value.of("in"), "value", Value.ofArray(arr)));
    }

    private static ObjectValue likeColumn(String column, String pattern) {
        return Value.ofObject(Map.of("column", Value.of(column), "op", Value.of("like"), "value", Value.of(pattern)));
    }

    private static ObjectValue isNotNullColumn(String column) {
        return Value.ofObject(Map.of("column", Value.of(column), "op", Value.of("isNotNull")));
    }

    private static ObjectValue orGroup(ObjectValue... children) {
        return Value.ofObject(Map.of("or", arrayOf(children)));
    }

    private static ObjectValue andGroup(ObjectValue... children) {
        return Value.ofObject(Map.of("and", arrayOf(children)));
    }

    private static ArrayValue arrayOf(ObjectValue... values) {
        return Value.ofArray((Value[]) values);
    }

    @Table("TOME")
    public record Tome(@Id Integer id, String title, String moon, Integer forbiddenTier) {}

    @Service
    static class LibraryService {

        private final TomeRepository repository;

        LibraryService(TomeRepository repository) {
            this.repository = repository;
        }

        @PreEnforce(action = "'allTomes'")
        public Flux<Tome> allTomes() {
            return repository.findAll();
        }

        @PreEnforce(action = "'tomesNotMoreForbiddenThan'")
        public Flux<Tome> tomesNotMoreForbiddenThan(int maxTier) {
            return repository.findByForbiddenTierLessThanEqual(maxTier);
        }

        @PreEnforce(action = "'tomeById'")
        public Mono<Tome> tomeById(int id) {
            return repository.findById(id);
        }

        @PreEnforce(action = "'countByMoon'")
        public Mono<Long> countByMoon(String moon) {
            return repository.countByMoon(moon);
        }

        @PreEnforce(action = "'tomesAtMoonNotMoreForbiddenThan'")
        public Flux<Tome> tomesAtMoonNotMoreForbiddenThan(String moon, int maxTier) {
            return repository.findByMoonAndForbiddenTierLessThanEqual(moon, maxTier);
        }

        @PreEnforce(action = "'tomesByMoonsRanked'")
        public Flux<Tome> tomesByMoonsRanked(Collection<String> moons) {
            return repository.findByMoonInOrderByForbiddenTierDescIdAsc(moons);
        }

        @PreEnforce(action = "'tomesByTitle'")
        public Flux<Tome> tomesByTitle(String pattern) {
            return repository.findRareTomes(pattern);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableReactiveSaplMethodSecurity
    @org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories(basePackageClasses = TomeRepository.class)
    static class PalanthasLibraryTestApp {

        @Bean
        ConnectionFactory connectionFactory() {
            return new H2ConnectionFactory(H2ConnectionConfiguration.builder().inMemory("sapl-relational-shim")
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
