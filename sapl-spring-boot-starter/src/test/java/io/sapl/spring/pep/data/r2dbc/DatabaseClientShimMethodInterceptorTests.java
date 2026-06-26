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
package io.sapl.spring.pep.data.r2dbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.model.Value;
import io.sapl.spring.pep.constraints.ConstraintHandler;
import io.sapl.spring.pep.constraints.ConstraintType;
import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.EnforcementPlanContext;
import io.sapl.spring.pep.constraints.EnforcementPlanEntry;
import io.sapl.spring.pep.constraints.Signal.SqlShimSignal;
import lombok.val;

@ExtendWith(MockitoExtension.class)
@DisplayName("DatabaseClient SQL shim: obligation-driven rewriting must fail closed")
class DatabaseClientShimMethodInterceptorTests {

    private static final String ORIGINAL_SQL = "SELECT * FROM tome";

    private static final Method SQL_METHOD = sqlMethod();

    private final DatabaseClientShimMethodInterceptor interceptor = new DatabaseClientShimMethodInterceptor();

    @Mock
    private DatabaseClient databaseClient;

    @Mock
    private MethodInvocation invocation;

    @AfterEach
    void clearPlan() {
        EnforcementPlanContext.bindBlocking(null);
    }

    @Test
    @DisplayName("an SQL-rewrite obligation that yields no usable rewritten SQL is denied, not run un-narrowed")
    void whenObligationMapperYieldsNoStringThenAccessDenied() throws Throwable {
        val supplier = lazyRewriteSupplier();
        EnforcementPlanContext.bindBlocking(planWithSqlEntry(ConstraintType.OBLIGATION, sql -> null));

        assertThatThrownBy(supplier::get).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("an SQL-rewrite obligation that produces rewritten SQL passes the rewrite through")
    void whenObligationMapperRewritesThenRewrittenSqlReturned() throws Throwable {
        val supplier = lazyRewriteSupplier();
        EnforcementPlanContext
                .bindBlocking(planWithSqlEntry(ConstraintType.OBLIGATION, sql -> sql + " WHERE moon = 'Nuitari'"));

        assertThat(supplier.get()).isEqualTo(ORIGINAL_SQL + " WHERE moon = 'Nuitari'");
    }

    @Test
    @DisplayName("advice-only rewriting degrades to the original SQL when it yields no usable rewrite")
    void whenAdviceMapperYieldsNoStringThenOriginalReturned() throws Throwable {
        val supplier = lazyRewriteSupplier();
        EnforcementPlanContext.bindBlocking(planWithSqlEntry(ConstraintType.ADVICE, sql -> null));

        assertThat(supplier.get()).isEqualTo(ORIGINAL_SQL);
    }

    @Test
    @DisplayName("no plan in scope means no obligation, so the original SQL passes through unchanged")
    void whenNoPlanThenOriginalReturned() throws Throwable {
        val supplier = lazyRewriteSupplier();

        assertThat(supplier.get()).isEqualTo(ORIGINAL_SQL);
    }

    @SuppressWarnings("unchecked")
    private Supplier<String> lazyRewriteSupplier() throws Throwable {
        when(invocation.getMethod()).thenReturn(SQL_METHOD);
        when(invocation.getArguments()).thenReturn(new Object[] { ORIGINAL_SQL });
        when(invocation.getThis()).thenReturn(databaseClient);

        interceptor.invoke(invocation);

        val captor = ArgumentCaptor.forClass(Supplier.class);
        verify(databaseClient).sql(captor.capture());
        return captor.getValue();
    }

    private static EnforcementPlan planWithSqlEntry(ConstraintType type, ConstraintHandler.Mapper<String> mapper) {
        val entry = new EnforcementPlanEntry<>(mapper, 0, type, Value.of("sql-rewrite"));
        return new EnforcementPlan(Map.of(SqlShimSignal.SIGNAL_TYPE, List.<EnforcementPlanEntry<?>>of(entry)));
    }

    private static Method sqlMethod() {
        try {
            return DatabaseClient.class.getMethod("sql", String.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }
}
