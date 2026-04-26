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
package io.sapl.spring.pep.method.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Service;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionalOperator;

import io.r2dbc.h2.H2ConnectionConfiguration;
import io.r2dbc.h2.H2ConnectionFactory;
import io.r2dbc.h2.H2ConnectionOption;
import io.r2dbc.spi.ConnectionFactory;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.config.EnableReactiveSaplMethodSecurity;
import io.sapl.spring.method.metadata.PostEnforce;
import io.sapl.spring.method.metadata.PreEnforce;
import io.sapl.spring.pep.constraints.ConstraintHandler;
import io.sapl.spring.pep.constraints.ConstraintHandler.Mapper;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.Signal.OutputSignal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.pep.constraints.SignalType.ValueSignalType;
import lombok.val;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reactive twin of {@link BlockingTransactionalEnforcementTests}: confirms
 * that reactive PEPs nest correctly inside the R2DBC transaction proxy so
 * that an access-denied decision after a transactional insert rolls back the
 * transaction. Uses the same Miskatonic loan-ledger scenario as the blocking
 * twin, only on a reactive stack with R2DBC + H2.
 */
@SpringBootTest(classes = ReactiveTransactionalEnforcementTests.LedgerTestApp.class)
@WithMockUser(username = "armitage", roles = "FACULTY")
class ReactiveTransactionalEnforcementTests {

    private static final String LOAN_ENTRY_BODY = "loan: De Vermis Mysteriis to Wilmarth";

    private static final Duration STEP_TIMEOUT = Duration.ofSeconds(5);

    private static final AtomicInteger NEXT_LOAN_ID = new AtomicInteger();

    @Autowired
    LedgerService ledger;

    @Autowired
    DatabaseClient databaseClient;

    @MockitoBean
    PolicyDecisionPoint pdp;

    @BeforeEach
    void resetSchema() {
        databaseClient.sql("CREATE TABLE IF NOT EXISTS ledger_entry(id INT PRIMARY KEY, body VARCHAR(255))").fetch()
                .rowsUpdated().block(STEP_TIMEOUT);
        databaseClient.sql("DELETE FROM ledger_entry").fetch().rowsUpdated().block(STEP_TIMEOUT);
        NEXT_LOAN_ID.set(1);
    }

    @Nested
    @DisplayName("PreEnforce + @Transactional on Mono<Void>")
    class PreEnforceTransactional {

        @Test
        @DisplayName("PERMIT lets the loan be recorded; the row commits")
        void whenPermitThenRowIsCommitted() {
            org.mockito.Mockito.when(pdp.decideOnce(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(Mono.just(AuthorizationDecision.PERMIT));

            StepVerifier.create(ledger.recordLoanWithPreEnforce(NEXT_LOAN_ID.getAndIncrement(), LOAN_ENTRY_BODY))
                    .verifyComplete();

            assertThat(rowCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("DENY blocks the protected method entirely; no row is ever written")
        void whenDenyThenMethodNeverInvokedAndNoRowWritten() {
            org.mockito.Mockito.when(pdp.decideOnce(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(Mono.just(AuthorizationDecision.DENY));

            StepVerifier.create(ledger.recordLoanWithPreEnforce(NEXT_LOAN_ID.getAndIncrement(), LOAN_ENTRY_BODY))
                    .expectError(AccessDeniedException.class).verify(STEP_TIMEOUT);

            assertThat(rowCount()).isZero();
        }
    }

    @Nested
    @DisplayName("PostEnforce + @Transactional + obligation handler throws after the insert")
    class ObligationHandlerFailureRollsBack {

        private static final String GATE_REFUSES = "miskatonic:gateRefusesToOpenMapper";

        @Test
        @DisplayName("OutputSignal Mapper failure rolls the insert back (post-invocation handler-throws path)")
        void whenOutputMapperObligationFailsAfterInsertThenRollback() {
            org.mockito.Mockito.when(pdp.decideOnce(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(Mono.just(decisionWithObligation(GATE_REFUSES)));

            StepVerifier.create(ledger.recordLoanWithPostEnforce(NEXT_LOAN_ID.getAndIncrement(), LOAN_ENTRY_BODY))
                    .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(AccessDeniedException.class)
                            .hasMessageContaining("post-invocation"))
                    .verify(STEP_TIMEOUT);

            assertThat(rowCount())
                    .as("row count after handler failure (must be 0 - rollback applies to handler errors too)")
                    .isZero();
        }

        private static AuthorizationDecision decisionWithObligation(String type) {
            val constraint  = Value.ofObject(Map.of("type", Value.of(type)));
            val obligations = Value.ofArray(constraint);
            return new AuthorizationDecision(Decision.PERMIT, obligations, Value.EMPTY_ARRAY, Value.UNDEFINED);
        }
    }

    @Nested
    @DisplayName("PreEnforce + @Transactional on Mono<Void>")
    class PreEnforceMonoVoidTransactional {

        @Test
        @DisplayName("PERMIT lets the side-effecting Mono<Void> rite commit")
        void whenPermitThenSideEffectCommits() {
            org.mockito.Mockito.when(pdp.decideOnce(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(Mono.just(AuthorizationDecision.PERMIT));

            StepVerifier
                    .create(ledger.performWardingRiteWithPreEnforce(NEXT_LOAN_ID.getAndIncrement(), LOAN_ENTRY_BODY))
                    .verifyComplete();

            assertThat(rowCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("DENY blocks the rite entirely; no row is ever written")
        void whenDenyThenMethodNeverInvoked() {
            org.mockito.Mockito.when(pdp.decideOnce(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(Mono.just(AuthorizationDecision.DENY));

            StepVerifier
                    .create(ledger.performWardingRiteWithPreEnforce(NEXT_LOAN_ID.getAndIncrement(), LOAN_ENTRY_BODY))
                    .expectError(AccessDeniedException.class).verify(STEP_TIMEOUT);

            assertThat(rowCount()).isZero();
        }
    }

    @Nested
    @DisplayName("PostEnforce + @Transactional on Mono<String>: this is where the rollback contract is exercised")
    class PostEnforceTransactional {

        @Test
        @DisplayName("PERMIT after the insert lets the row commit")
        void whenPermitThenRowIsCommitted() {
            org.mockito.Mockito.when(pdp.decideOnce(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(Mono.just(AuthorizationDecision.PERMIT));

            StepVerifier.create(ledger.recordLoanWithPostEnforce(NEXT_LOAN_ID.getAndIncrement(), LOAN_ENTRY_BODY))
                    .expectNext(LOAN_ENTRY_BODY).verifyComplete();

            assertThat(rowCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("DENY after the insert rolls the transaction back; the row is expunged")
        void whenDenyAfterInsertThenTransactionRollsBack() {
            org.mockito.Mockito.when(pdp.decideOnce(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(Mono.just(AuthorizationDecision.DENY));

            StepVerifier.create(ledger.recordLoanWithPostEnforce(NEXT_LOAN_ID.getAndIncrement(), LOAN_ENTRY_BODY))
                    .expectError(AccessDeniedException.class).verify(STEP_TIMEOUT);

            assertThat(rowCount()).as("row count after DENY (must be 0 if reactive rollback ordering is correct)")
                    .isZero();
        }
    }

    private long rowCount() {
        Long count = databaseClient.sql("SELECT COUNT(*) FROM ledger_entry").map(row -> row.get(0, Long.class)).one()
                .block(STEP_TIMEOUT);
        return count == null ? 0L : count;
    }

    @Service
    static class LedgerService {

        private final DatabaseClient        client;
        private final TransactionalOperator txOperator;

        LedgerService(DatabaseClient client, TransactionalOperator txOperator) {
            this.client     = client;
            this.txOperator = txOperator;
        }

        @PreEnforce
        @Transactional
        public Mono<Void> recordLoanWithPreEnforce(int id, String body) {
            return insert(id, body).as(txOperator::transactional).then();
        }

        @PostEnforce
        @Transactional
        public Mono<String> recordLoanWithPostEnforce(int id, String body) {
            return insert(id, body).as(txOperator::transactional).thenReturn(body);
        }

        @PreEnforce
        @Transactional
        public Mono<Void> performWardingRiteWithPreEnforce(int id, String body) {
            return insert(id, body).as(txOperator::transactional).then();
        }

        private Mono<Long> insert(int id, String body) {
            return client.sql("INSERT INTO ledger_entry(id, body) VALUES ($1, $2)").bind("$1", id).bind("$2", body)
                    .fetch().rowsUpdated();
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableReactiveSaplMethodSecurity
    @EnableTransactionManagement
    static class LedgerTestApp {

        @Bean
        ConnectionFactory connectionFactory() {
            return new H2ConnectionFactory(H2ConnectionConfiguration.builder().inMemory("sapl-reactive-tx")
                    .property(H2ConnectionOption.DB_CLOSE_DELAY, "-1").build());
        }

        @Bean
        @Primary
        ReactiveTransactionManager reactiveTransactionManager(ConnectionFactory connectionFactory) {
            return new R2dbcTransactionManager(connectionFactory);
        }

        @Bean
        TransactionalOperator transactionalOperator(ReactiveTransactionManager txManager) {
            return TransactionalOperator.create(txManager);
        }

        @Bean
        DatabaseClient databaseClient(ConnectionFactory connectionFactory) {
            val client = DatabaseClient.create(connectionFactory);
            client.sql("CREATE TABLE IF NOT EXISTS ledger_entry(id INT PRIMARY KEY, body VARCHAR(255))").fetch()
                    .rowsUpdated().block();
            return client;
        }

        @Bean
        LedgerService ledgerService(DatabaseClient client, TransactionalOperator txOperator) {
            return new LedgerService(client, txOperator);
        }

        @Bean
        ConstraintHandlerProvider failingMapperProvider() {
            return new FailingOutputMapperProvider();
        }
    }

    static class FailingOutputMapperProvider implements ConstraintHandlerProvider {

        private static final String OBLIGATION_TYPE = "miskatonic:gateRefusesToOpenMapper";

        @Override
        public Optional<ScopedConstraintHandler> getConstraintHandler(Value constraint,
                Set<SignalType> supportedSignals) {
            if (!(constraint instanceof ObjectValue obj)) {
                return Optional.empty();
            }
            if (!(obj.get("type") instanceof TextValue(String type)) || !OBLIGATION_TYPE.equals(type)) {
                return Optional.empty();
            }
            for (val s : supportedSignals) {
                if (s instanceof ValueSignalType<?> v && OutputSignal.class.equals(v.type())) {
                    Mapper<Object> failing = ignored -> {
                        throw new IllegalStateException("the gate refuses to open");
                    };
                    return Optional.of(new ScopedConstraintHandler((ConstraintHandler<?>) failing, s, 30));
                }
            }
            return Optional.empty();
        }
    }
}
