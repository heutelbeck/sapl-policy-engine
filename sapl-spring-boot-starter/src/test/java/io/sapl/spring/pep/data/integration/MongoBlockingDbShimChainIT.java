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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.StreamingPolicyDecisionPoint;
import io.sapl.spring.config.EnableSaplMethodSecurity;
import io.sapl.spring.method.metadata.PreEnforce;
import io.sapl.spring.testsupport.SaplPepTestApp;
import lombok.val;

/**
 * Blocking sibling of {@link MongoDbShimChainIT}. Exercises the blocking Mongo
 * shim against a real MongoDB through the blocking {@code MongoTemplate} and a
 * blocking {@code MongoRepository}, covering the same hooks: repository derived
 * queries, {@code findById}, {@code findAll}, {@code count}, the fluent
 * {@code query(Class)} chain (bare terminals, {@code matching(Criteria)},
 * projection), and the fluent update / remove builders.
 */
@SpringBootTest(classes = MongoBlockingDbShimChainIT.BlockingChroniclesTestApp.class)
@Testcontainers
@WithMockUser(username = "raistlin", roles = "BLACK_ROBE")
class MongoBlockingDbShimChainIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongoContainer = new MongoDBContainer("mongo:8.0");

    private static final String SOLINARI = "Solinari";
    private static final String LUNITARI = "Lunitari";
    private static final String NUITARI  = "Nuitari";

    @Autowired
    BlockingChronicleService chronicles;

    @Autowired
    MongoTemplate mongoTemplate;

    @MockitoBean
    StreamingPolicyDecisionPoint pdp;

    @BeforeEach
    void resetCollection() {
        mongoTemplate.dropCollection(Chronicle.class);
        mongoTemplate.insertAll(List.of(new Chronicle("1", "The Disks of Mishakal", SOLINARI, 0),
                new Chronicle("2", "The Bestiary of Krynn", SOLINARI, 1),
                new Chronicle("3", "Songs of the Bards", LUNITARI, 0),
                new Chronicle("4", "The Lost Chronicles", LUNITARI, 2),
                new Chronicle("5", "The Black Wing Rite", NUITARI, 3),
                new Chronicle("6", "Necromancers' Compendium", NUITARI, 4)));
    }

    @Nested
    @DisplayName("Repository surface (legacy template path) is narrowed")
    class RepositorySurface {

        @Test
        @DisplayName("findAll is narrowed by the obligation")
        void whenFindAllThenNarrowed() {
            decide(decisionWithMongoCriteria(eqColumn("moon", NUITARI)));

            assertThat(chronicles.allChronicles()).extracting(Chronicle::title)
                    .containsExactlyInAnyOrder("The Black Wing Rite", "Necromancers' Compendium");
        }

        @Test
        @DisplayName("A derived query is intersected with the obligation")
        void whenDerivedQueryThenIntersected() {
            decide(decisionWithMongoCriteria(eqColumn("moon", SOLINARI)));

            assertThat(chronicles.notMoreForbiddenThan(1)).extracting(Chronicle::title)
                    .containsExactlyInAnyOrder("The Disks of Mishakal", "The Bestiary of Krynn");
        }

        @Test
        @DisplayName("findById of a disallowed document yields empty")
        void whenFindByIdDisallowedThenEmpty() {
            decide(decisionWithMongoCriteria(eqColumn("moon", LUNITARI)));

            assertThat(chronicles.chronicleById("5")).isEmpty();
        }

        @Test
        @DisplayName("count is narrowed by the obligation")
        void whenCountThenNarrowed() {
            decide(decisionWithMongoCriteria(eqColumn("moon", LUNITARI)));

            assertThat(chronicles.countByMoon(LUNITARI)).isEqualTo(2L);
            assertThat(chronicles.countByMoon(NUITARI)).isZero();
        }
    }

    @Nested
    @DisplayName("Fluent chain is narrowed, not bypassed")
    class FluentChain {

        @Test
        @DisplayName("query(X).all() with no matching step is narrowed")
        void whenFluentAllThenNarrowed() {
            decide(decisionWithMongoCriteria(eqColumn("moon", NUITARI)));

            assertThat(chronicles.fluentAll()).extracting(Chronicle::title)
                    .containsExactlyInAnyOrder("The Black Wing Rite", "Necromancers' Compendium");
        }

        @Test
        @DisplayName("query(X).count() with no matching step counts only narrowed documents")
        void whenFluentCountThenNarrowed() {
            decide(decisionWithMongoCriteria(eqColumn("moon", LUNITARI)));

            assertThat(chronicles.fluentCount()).isEqualTo(2L);
        }

        @Test
        @DisplayName("query(X).exists() reflects the narrowed set")
        void whenFluentExistsAgainstEmptyNarrowedSetThenFalse() {
            decide(decisionWithMongoCriteria(eqColumn("moon", "Reorx")));

            assertThat(chronicles.fluentExists()).isFalse();
        }

        @Test
        @DisplayName("query(X).matching(Criteria).all() honours both criteria and obligation")
        void whenFluentMatchingCriteriaThenIntersected() {
            decide(decisionWithMongoCriteria(eqColumn("moon", SOLINARI)));

            assertThat(chronicles.fluentMatchingCriteria(1)).extracting(Chronicle::title)
                    .containsExactlyInAnyOrder("The Disks of Mishakal", "The Bestiary of Krynn");
        }

        @Test
        @DisplayName("query(X).as(View).all() is narrowed before projection")
        void whenFluentProjectionThenNarrowed() {
            decide(decisionWithMongoCriteria(eqColumn("moon", LUNITARI)));

            assertThat(chronicles.fluentProjection()).containsExactlyInAnyOrder("Songs of the Bards",
                    "The Lost Chronicles");
        }
    }

    @Nested
    @DisplayName("Fluent update / remove builders narrow their selection")
    class FluentWrite {

        @Test
        @DisplayName("update builder: an intersecting obligation updates in-scope rows")
        void whenObligationIntersectsFluentUpdateThenInScopeRowsUpdated() {
            decide(decisionWithMongoCriteria(eqColumn("moon", NUITARI)));

            assertThat(chronicles.fluentUpdate()).isEqualTo(2L);
        }

        @Test
        @DisplayName("update builder: a disjoint obligation updates nothing")
        void whenObligationDisjointFromFluentUpdateThenNothingUpdated() {
            decide(decisionWithMongoCriteria(eqColumn("moon", LUNITARI)));

            assertThat(chronicles.fluentUpdate()).isZero();
        }

        @Test
        @DisplayName("remove builder: a disjoint obligation deletes nothing")
        void whenObligationDisjointFromFluentRemoveThenNothingDeleted() {
            decide(decisionWithMongoCriteria(eqColumn("moon", LUNITARI)));

            assertThat(chronicles.fluentRemove()).isZero();
        }

        @Test
        @DisplayName("remove builder: an intersecting obligation deletes in-scope rows")
        void whenObligationIntersectsFluentRemoveThenInScopeRowsDeleted() {
            decide(decisionWithMongoCriteria(eqColumn("moon", NUITARI)));

            assertThat(chronicles.fluentRemove()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("Decisions and pass-through")
    class DecisionsAndPassThrough {

        @Test
        @DisplayName("DENY raises AccessDeniedException")
        void whenDenyThenAccessDenied() {
            decide(AuthorizationDecision.DENY);

            assertThatThrownBy(() -> chronicles.allChronicles()).isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("PERMIT without obligations returns all six chronicles")
        void whenPermitWithoutObligationThenAllReturned() {
            decide(AuthorizationDecision.PERMIT);

            assertThat(chronicles.allChronicles()).hasSize(6);
        }

        @Test
        @DisplayName("A repository save under a query-rewriting obligation is not over-denied")
        void whenObligationAndRepositorySaveThenNotDenied() {
            decide(decisionWithMongoCriteria(eqColumn("moon", NUITARI)));

            val saved = chronicles.saveChronicle(new Chronicle("7", "The Star of Reorx", NUITARI, 2));
            assertThat(saved.id()).isEqualTo("7");
        }
    }

    private void decide(AuthorizationDecision decision) {
        when(pdp.decideOnce(any(), anyString())).thenReturn(decision);
    }

    private static AuthorizationDecision decisionWithMongoCriteria(ObjectValue... criteria) {
        val obligation = Value
                .ofObject(Map.of("type", Value.of("mongo:queryRewriting"), "criteria", arrayOf(criteria)));
        return new AuthorizationDecision(Decision.PERMIT, Value.ofArray(obligation), Value.EMPTY_ARRAY,
                Value.UNDEFINED);
    }

    private static ObjectValue eqColumn(String column, String value) {
        return Value.ofObject(Map.of("column", Value.of(column), "op", Value.of("="), "value", Value.of(value)));
    }

    private static ArrayValue arrayOf(ObjectValue... values) {
        return Value.ofArray((Value[]) values);
    }

    @Document("blocking-chronicles")
    public record Chronicle(@Id String id, String title, String moon, Integer forbiddenTier) {}

    record TitleOnly(String title) {}

    static class BlockingChronicleService {

        private final BlockingChronicleRepository repository;
        private final MongoTemplate               template;

        BlockingChronicleService(BlockingChronicleRepository repository, MongoTemplate template) {
            this.repository = repository;
            this.template   = template;
        }

        @PreEnforce(action = "'allChronicles'")
        public List<Chronicle> allChronicles() {
            return repository.findAll();
        }

        @PreEnforce(action = "'notMoreForbiddenThan'")
        public List<Chronicle> notMoreForbiddenThan(int maxTier) {
            return repository.findByForbiddenTierLessThanEqual(maxTier);
        }

        @PreEnforce(action = "'chronicleById'")
        public Optional<Chronicle> chronicleById(String id) {
            return repository.findById(id);
        }

        @PreEnforce(action = "'countByMoon'")
        public long countByMoon(String moon) {
            return repository.countByMoon(moon);
        }

        @PreEnforce(action = "'fluentAll'")
        public List<Chronicle> fluentAll() {
            return template.query(Chronicle.class).all();
        }

        @PreEnforce(action = "'fluentCount'")
        public long fluentCount() {
            return template.query(Chronicle.class).count();
        }

        @PreEnforce(action = "'fluentExists'")
        public boolean fluentExists() {
            return template.query(Chronicle.class).exists();
        }

        @PreEnforce(action = "'fluentMatchingCriteria'")
        public List<Chronicle> fluentMatchingCriteria(int maxTier) {
            return template.query(Chronicle.class).matching(Criteria.where("forbiddenTier").lte(maxTier)).all();
        }

        @PreEnforce(action = "'fluentProjection'")
        public List<String> fluentProjection() {
            return template.query(Chronicle.class).as(TitleOnly.class).all().stream().map(TitleOnly::title).toList();
        }

        @PreEnforce(action = "'fluentUpdate'")
        public long fluentUpdate() {
            return template.update(Chronicle.class).matching(Criteria.where("moon").is(NUITARI))
                    .apply(new Update().set("forbiddenTier", 9)).all().getModifiedCount();
        }

        @PreEnforce(action = "'fluentRemove'")
        public long fluentRemove() {
            return template.remove(Chronicle.class).matching(Criteria.where("moon").is(NUITARI)).all()
                    .getDeletedCount();
        }

        @PreEnforce(action = "'saveChronicle'")
        public Chronicle saveChronicle(Chronicle chronicle) {
            return repository.save(chronicle);
        }
    }

    @SaplPepTestApp
    @EnableSaplMethodSecurity
    @EnableMongoRepositories(basePackageClasses = BlockingChronicleRepository.class)
    static class BlockingChroniclesTestApp {

        @Bean
        BlockingChronicleService blockingChronicleService(BlockingChronicleRepository repository,
                MongoTemplate template) {
            return new BlockingChronicleService(repository, template);
        }
    }
}
