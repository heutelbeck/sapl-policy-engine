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

import java.lang.reflect.Method;
import java.util.Set;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.data.relational.core.query.Query;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.EnforcementPlanContext;
import io.sapl.spring.pep.constraints.EnforcementResult;
import io.sapl.spring.pep.constraints.Signal.RelationalQueryShimSignal;
import io.sapl.spring.util.Maybe.Present;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/**
 * AOP {@link MethodInterceptor} for the R2DBC entity template proxy. Fires
 * {@link RelationalQueryShimSignal} against the active
 * {@link EnforcementPlan} (looked up via {@link EnforcementPlanContext}) for
 * the {@code Query}-bearing entry points ({@code select}, {@code selectOne},
 * {@code count}, {@code exists}, {@code update}, {@code delete}) before
 * delegating to the target template. Pass-through when no plan is in scope or
 * the invoked method is not a shimmed entry point.
 * </p>
 * Mutates the live arguments array on the {@link MethodInvocation} to insert
 * the rewritten {@link Query} before {@code proceed()}. Spring's
 * {@code ReflectiveMethodInvocation} treats {@code getArguments()} as the
 * live backing array; relying on that contract keeps the rewrite simple.
 */
public class R2dbcShimMethodInterceptor implements MethodInterceptor {

    private static final String ERROR_ACCESS_DENIED_OBLIGATION_FAILED = "Access Denied. A relational query-manipulation obligation handler failed.";

    private static final String      METHOD_SELECT     = "select";
    private static final Set<String> MONO_METHOD_NAMES = Set.of("selectOne", "count", "exists", "update", "delete");

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        val method = invocation.getMethod();
        val name   = method.getName();
        if (METHOD_SELECT.equals(name) && firstParamIsQuery(method)) {
            return interceptFlux(invocation);
        }
        if (MONO_METHOD_NAMES.contains(name) && firstParamIsQuery(method)) {
            return interceptMono(invocation);
        }
        return invocation.proceed();
    }

    private static boolean firstParamIsQuery(Method method) {
        val params = method.getParameterTypes();
        return params.length >= 1 && params[0] == Query.class;
    }

    private static Flux<?> interceptFlux(MethodInvocation invocation) {
        val originalQuery = (Query) invocation.getArguments()[0];
        return Flux.deferContextual(ctx -> {
            val rewriteOutcome = applyShim(ctx, invocation, originalQuery);
            return switch (rewriteOutcome) {
            case Denied denied   -> Flux.error(denied.cause());
            case Proceed ignored -> proceedAsFlux(invocation);
            };
        });
    }

    private static Mono<?> interceptMono(MethodInvocation invocation) {
        val originalQuery = (Query) invocation.getArguments()[0];
        return Mono.deferContextual(ctx -> {
            val rewriteOutcome = applyShim(ctx, invocation, originalQuery);
            return switch (rewriteOutcome) {
            case Denied denied   -> Mono.error(denied.cause());
            case Proceed ignored -> proceedAsMono(invocation);
            };
        });
    }

    private static RewriteOutcome applyShim(ContextView ctx, MethodInvocation invocation, Query originalQuery) {
        val planOpt = EnforcementPlanContext.currentReactor(ctx);
        if (planOpt.isEmpty()) {
            return Proceed.INSTANCE;
        }
        val                  plan   = planOpt.get();
        EnforcementResult<?> result = plan.execute(RelationalQueryShimSignal.of(originalQuery), false);
        if (result.failureState()) {
            return new Denied(new AccessDeniedException(ERROR_ACCESS_DENIED_OBLIGATION_FAILED));
        }
        if (result.value() instanceof Present<?>(var v) && v instanceof Query rewritten) {
            invocation.getArguments()[0] = rewritten;
        }
        return Proceed.INSTANCE;
    }

    private static Flux<?> proceedAsFlux(MethodInvocation invocation) {
        try {
            return (Flux<?>) invocation.proceed();
        } catch (Throwable throwable) {
            return Flux.error(throwable);
        }
    }

    private static Mono<?> proceedAsMono(MethodInvocation invocation) {
        try {
            return (Mono<?>) invocation.proceed();
        } catch (Throwable throwable) {
            return Mono.error(throwable);
        }
    }

    private sealed interface RewriteOutcome permits Proceed, Denied {
    }

    private record Proceed() implements RewriteOutcome {
        static final Proceed INSTANCE = new Proceed();
    }

    private record Denied(Throwable cause) implements RewriteOutcome {}
}
