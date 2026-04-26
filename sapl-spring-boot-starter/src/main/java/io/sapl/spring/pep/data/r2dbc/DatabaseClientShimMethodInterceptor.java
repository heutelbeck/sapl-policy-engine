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

import java.util.function.Supplier;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.PreparedOperation;
import org.springframework.r2dbc.core.binding.BindTarget;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.EnforcementPlanContext;
import io.sapl.spring.pep.constraints.Signal.SqlShimSignal;
import io.sapl.spring.util.Maybe.Present;
import lombok.val;

/**
 * AOP {@link MethodInterceptor} for the R2DBC {@code DatabaseClient} proxy.
 * Synchronously substitutes the SQL argument of {@code sql(String)},
 * {@code sql(Supplier<String>)}, and {@code sql(PreparedOperation<?>)} with a
 * lazy variant that applies the SAPL shim rewrite at the moment the
 * supplier is resolved. The supplier is resolved by Spring R2DBC's internal
 * execution at subscription time, on a thread where the active
 * {@link EnforcementPlan} is reachable via the {@link EnforcementPlanContext}
 * thread-local (kept current by Reactor's automatic context propagation, which
 * the R2DBC shim auto-configuration enables).
 * <p>
 * Catches every R2DBC query path because all paths bottom out at
 * {@code DatabaseClient.sql(...)}:
 * <ul>
 * <li>{@code R2dbcEntityTemplate.select/...} -> internally renders the
 * structured {@code Query} and calls {@code sql(...)};</li>
 * <li>derived repository queries built by {@code PartTreeR2dbcQuery} ->
 * {@code DatabaseClient.sql(PreparedOperation)} via the
 * {@code sql(Supplier<String>)} overload;</li>
 * <li>{@code @Query}-annotated repository methods ->
 * {@code DatabaseClient.sql(PreparedOperation)} via the same overload;</li>
 * <li>direct user calls to {@code databaseClient.sql(...)}.</li>
 * </ul>
 * <p>
 * No fluent-chain wrapping required: the user's {@code .bind(...)} /
 * {@code .fetch().all()} chain proceeds on the real
 * {@code GenericExecuteSpec}; only the SQL string the chain is built around
 * is the lazy-rewrite. JSqlParser AST manipulation only adds new conditions
 * and never reorders or removes existing parameter placeholders, so the
 * chain's bind positions remain valid against the rewritten SQL.
 */
public class DatabaseClientShimMethodInterceptor implements MethodInterceptor {

    private static final String ERROR_ACCESS_DENIED_OBLIGATION_FAILED = "Access Denied. A SQL query-manipulation obligation handler failed.";

    private static final String METHOD_SQL = "sql";

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        val method = invocation.getMethod();
        if (!METHOD_SQL.equals(method.getName()) || method.getParameterCount() != 1) {
            return invocation.proceed();
        }
        val args   = invocation.getArguments();
        val arg    = args[0];
        val target = (DatabaseClient) invocation.getThis();
        if (arg instanceof String original) {
            // Re-route to sql(Supplier<String>) on the target so the rewrite
            // is applied lazily at supplier resolution time, when the
            // EnforcementPlan is reachable via the propagated ThreadLocal.
            return target.sql((Supplier<String>) () -> rewriteIfPlanPresent(original));
        }
        if (arg instanceof PreparedOperation<?> prepared) {
            args[0] = new RewritingPreparedOperation(prepared);
            return invocation.proceed();
        }
        if (arg instanceof Supplier<?> supplier) {
            args[0] = (Supplier<String>) () -> rewriteIfPlanPresent((String) supplier.get());
            return invocation.proceed();
        }
        return invocation.proceed();
    }

    private static String rewriteIfPlanPresent(String originalSql) {
        return EnforcementPlanContext.currentBlocking().map(plan -> applyShim(plan, originalSql)).orElse(originalSql);
    }

    private static String applyShim(EnforcementPlan plan, String originalSql) {
        val result = plan.execute(SqlShimSignal.of(originalSql), false);
        if (result.failureState()) {
            throw new AccessDeniedException(ERROR_ACCESS_DENIED_OBLIGATION_FAILED);
        }
        if (result.value() instanceof Present<?>(var v) && v instanceof String rewritten) {
            return rewritten;
        }
        return originalSql;
    }

    /**
     * Wrapping {@link PreparedOperation} that returns the SAPL-rewritten SQL
     * from {@link #get()} while delegating {@link #bindTo(BindTarget)} to the
     * original. Bind positions/names stay valid because the rewrite only
     * adds new conditions; it never reorders or removes existing parameter
     * placeholders.
     */
    private record RewritingPreparedOperation(PreparedOperation<?> delegate) implements PreparedOperation<Object> {

        @Override
        public Object getSource() {
            return delegate.getSource();
        }

        @Override
        public String toQuery() {
            return rewriteIfPlanPresent(delegate.toQuery());
        }

        @Override
        public String get() {
            return rewriteIfPlanPresent(delegate.get());
        }

        @Override
        public void bindTo(BindTarget target) {
            delegate.bindTo(target);
        }
    }
}
