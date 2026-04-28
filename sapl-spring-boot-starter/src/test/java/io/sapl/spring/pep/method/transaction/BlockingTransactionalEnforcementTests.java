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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

import javax.sql.DataSource;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Service;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.config.EnableSaplMethodSecurity;
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

import java.util.Map;
import java.util.Set;

/**
 * Verifies that SAPL method-security PEPs nest correctly inside the Spring
 * transaction proxy, so that an access-denied decision after a transactional
 * write rolls back the transaction. The setup deliberately uses no SAPL
 * autoconfig overrides; only the default ordering applied by
 * {@code SaplTransactionManagementConfiguration} is in effect.
 * <p>
 * Scenario: the Miskatonic Library keeps a JDBC-backed loan ledger. Each loan
 * is recorded in a transactional method; the librarian (PEP) decides whether
 * the loan stands. A DENY after the row has been inserted must roll back the
 * insert.
 */
@SpringBootTest(classes = BlockingTransactionalEnforcementTests.LedgerTestApp.class)
@WithMockUser(username = "armitage", roles = "FACULTY")
class BlockingTransactionalEnforcementTests {

    private static final String LOAN_ENTRY_BODY = "loan: De Vermis Mysteriis to Wilmarth";

    private static final AtomicInteger NEXT_LOAN_ID = new AtomicInteger();

    @Autowired
    LedgerService ledger;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    PolicyDecisionPoint pdp;

    @BeforeEach
    void truncateLedger() {
        jdbc.update("DELETE FROM ledger_entry");
        NEXT_LOAN_ID.set(1);
    }

    @Nested
    @DisplayName("PreEnforce + @Transactional")
    class PreEnforceTransactional {

        @Test
        @DisplayName("PERMIT lets the loan be recorded; the row commits")
        void whenPermitThenRowIsCommitted() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.PERMIT);

            ledger.recordLoanWithPreEnforce(NEXT_LOAN_ID.getAndIncrement(), LOAN_ENTRY_BODY);

            assertThat(rowCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("DENY blocks the protected method entirely; no row is ever written")
        void whenDenyThenMethodNeverInvokedAndNoRowWritten() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.DENY);
            val loanId = NEXT_LOAN_ID.getAndIncrement();

            assertThatExceptionOfType(AccessDeniedException.class)
                    .isThrownBy(() -> ledger.recordLoanWithPreEnforce(loanId, LOAN_ENTRY_BODY));

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
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithObligation(GATE_REFUSES));
            val loanId = NEXT_LOAN_ID.getAndIncrement();

            assertThatExceptionOfType(AccessDeniedException.class)
                    .isThrownBy(() -> ledger.recordLoanWithPostEnforce(loanId, LOAN_ENTRY_BODY))
                    .withMessageContaining("post-invocation");

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
    @DisplayName("PostEnforce + @Transactional: this is where the rollback contract is exercised")
    class PostEnforceTransactional {

        @Test
        @DisplayName("PERMIT after the insert lets the row commit")
        void whenPermitThenRowIsCommitted() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.PERMIT);

            val result = ledger.recordLoanWithPostEnforce(NEXT_LOAN_ID.getAndIncrement(), LOAN_ENTRY_BODY);

            assertThat(result).isEqualTo(LOAN_ENTRY_BODY);
            assertThat(rowCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("DENY after the insert rolls the transaction back; the row is expunged")
        void whenDenyAfterInsertThenTransactionRollsBack() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.DENY);
            val loanId = NEXT_LOAN_ID.getAndIncrement();

            assertThatExceptionOfType(AccessDeniedException.class)
                    .isThrownBy(() -> ledger.recordLoanWithPostEnforce(loanId, LOAN_ENTRY_BODY));

            assertThat(rowCount()).as("row count after DENY (must be 0 if rollback ordering is correct)").isZero();
        }
    }

    private int rowCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entry", Integer.class);
        return count == null ? 0 : count;
    }

    @Service
    static class LedgerService {

        private final JdbcTemplate jdbc;

        LedgerService(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @PreEnforce
        @Transactional
        public void recordLoanWithPreEnforce(int id, String body) {
            jdbc.update("INSERT INTO ledger_entry(id, body) VALUES (?, ?)", id, body);
        }

        @PostEnforce
        @Transactional
        public String recordLoanWithPostEnforce(int id, String body) {
            jdbc.update("INSERT INTO ledger_entry(id, body) VALUES (?, ?)", id, body);
            return body;
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableSaplMethodSecurity
    @EnableTransactionManagement
    static class LedgerTestApp {

        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).setName("sapl-blocking-tx").build();
        }

        @Bean
        @Primary
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            val template = new JdbcTemplate(dataSource);
            template.execute("CREATE TABLE IF NOT EXISTS ledger_entry(id INT PRIMARY KEY, body VARCHAR(255))");
            return template;
        }

        @Bean
        LedgerService ledgerService(JdbcTemplate jdbc) {
            return new LedgerService(jdbc);
        }

        @Bean
        ConstraintHandlerProvider failingMapperProvider() {
            return new FailingOutputMapperProvider();
        }
    }

    static class FailingOutputMapperProvider implements ConstraintHandlerProvider {

        private static final String OBLIGATION_TYPE = "miskatonic:gateRefusesToOpenMapper";

        @Override
        public List<ScopedConstraintHandler> getConstraintHandlers(Value constraint, Set<SignalType> supportedSignals) {
            if (!(constraint instanceof ObjectValue obj)) {
                return List.of();
            }
            if (!(obj.get("type") instanceof TextValue(String type)) || !OBLIGATION_TYPE.equals(type)) {
                return List.of();
            }
            for (val s : supportedSignals) {
                if (s instanceof ValueSignalType<?> v && OutputSignal.class.equals(v.type())) {
                    Mapper<Object> failing = ignored -> {
                        throw new IllegalStateException("the gate refuses to open");
                    };
                    return List.of(new ScopedConstraintHandler((ConstraintHandler<?>) failing, s, 30));
                }
            }
            return List.of();
        }
    }
}
