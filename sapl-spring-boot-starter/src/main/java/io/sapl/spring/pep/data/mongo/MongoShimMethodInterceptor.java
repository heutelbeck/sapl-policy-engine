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
package io.sapl.spring.pep.data.mongo;

import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.EnforcementPlanContext;
import io.sapl.spring.pep.constraints.Signal.MongoDbQueryShimSignal;
import io.sapl.spring.util.Maybe.Present;
import lombok.val;
import reactor.core.publisher.Flux;

/**
 * AOP {@link MethodInterceptor} for the reactive Mongo template proxy. Fires
 * {@link MongoDbQueryShimSignal} against the active {@link EnforcementPlan}
 * (looked up via {@link EnforcementPlanContext}) for the {@code find(Query,
 * Class)} and {@code find(Query, Class, String)} entry points before
 * delegating to the target template. Pass-through when no plan is in scope or
 * the invoked method is not a shimmed entry point.
 * </p>
 * Mutates the live arguments array on the {@link MethodInvocation} to insert
 * the rewritten {@link Query} before {@code proceed()}. Spring's
 * {@code ReflectiveMethodInvocation} treats {@code getArguments()} as the
 * live backing array; relying on that contract keeps the rewrite simple.
 */
public class MongoShimMethodInterceptor implements MethodInterceptor {

    private static final String ERROR_ACCESS_DENIED_OBLIGATION_FAILED = "Access Denied. A MongoDB query-manipulation obligation handler failed.";
    private static final String METHOD_FIND                           = "find";

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        if (!isShimmedFind(invocation.getMethod())) {
            return invocation.proceed();
        }
        val originalQuery = (Query) invocation.getArguments()[0];
        return Flux.deferContextual(ctx -> {
            val planOpt = EnforcementPlanContext.currentReactor(ctx);
            if (planOpt.isEmpty()) {
                return proceedAsFlux(invocation);
            }
            val plan   = planOpt.get();
            val result = plan.execute(MongoDbQueryShimSignal.of(originalQuery), false);
            if (result.failureState()) {
                return Flux.error(new AccessDeniedException(ERROR_ACCESS_DENIED_OBLIGATION_FAILED));
            }
            if (result.value() instanceof Present<?>(var v) && v instanceof Query rewritten) {
                invocation.getArguments()[0] = rewritten;
            }
            return proceedAsFlux(invocation);
        });
    }

    private static boolean isShimmedFind(Method method) {
        if (!METHOD_FIND.equals(method.getName())) {
            return false;
        }
        val params = method.getParameterTypes();
        return (params.length == 2 || params.length == 3) && params[0] == Query.class && params[1] == Class.class;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Flux<Object> proceedAsFlux(MethodInvocation invocation) {
        try {
            return (Flux) invocation.proceed();
        } catch (Throwable throwable) {
            return Flux.error(throwable);
        }
    }
}
