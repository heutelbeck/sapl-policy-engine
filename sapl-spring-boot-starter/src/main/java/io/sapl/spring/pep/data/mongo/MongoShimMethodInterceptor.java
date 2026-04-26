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
import java.util.Set;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.mongodb.core.ReactiveFindOperation;
import org.springframework.data.mongodb.core.ReactiveFindOperation.FindWithProjection;
import org.springframework.data.mongodb.core.ReactiveFindOperation.FindWithQuery;
import org.springframework.data.mongodb.core.ReactiveFindOperation.TerminatingFind;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.EnforcementPlanContext;
import io.sapl.spring.pep.constraints.Signal.MongoDbQueryShimSignal;
import io.sapl.spring.util.Maybe.Present;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * AOP {@link MethodInterceptor} for the reactive Mongo template proxy. Catches
 * both the legacy {@code find(Query, Class)} family of entry points
 * ({@code find}, {@code findOne}, {@code exists}, {@code count},
 * {@code remove}) and the fluent {@link ReactiveFindOperation} chain
 * ({@code template.query(Class).matching(Query).all()}) used by Spring Data
 * Mongo's derived-query and {@code @Query}-annotated repository methods. The
 * legacy entry points cover {@code SimpleReactiveMongoRepository}'s CRUD
 * surface ({@code findById}, {@code existsById}, {@code count()},
 * {@code findAll}, {@code deleteById}, {@code findOne(Example)}, etc.) since
 * each of them ultimately bottoms out at one of these methods on
 * {@code ReactiveMongoTemplate}.
 * <p>
 * For the fluent chain, the interceptor returns a Spring AOP proxy
 * implementing {@link FindWithProjection} on top of the template's real chain
 * and re-wraps the result of {@code as(Class)} so that {@code matching(Query)}
 * is intercepted regardless of whether projection retyping was applied first
 * (which {@code AbstractReactiveMongoQuery.doExecute} does for derived
 * queries). The shim defers to the terminating method (where the returned
 * {@link Mono} or {@link Flux} can {@code deferContextual} to read the active
 * plan) and re-invokes the real chain with the rewritten query.
 */
public class MongoShimMethodInterceptor implements MethodInterceptor {

    private static final String ERROR_ACCESS_DENIED_OBLIGATION_FAILED = "Access Denied. A MongoDB query-manipulation obligation handler failed.";

    private static final String      METHOD_QUERY                = "query";
    private static final Set<String> SHIMMED_LEGACY_QUERY_METHOD = Set.of("find", "findOne", "exists", "count",
            "remove");

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        val method = invocation.getMethod();
        if (METHOD_QUERY.equals(method.getName()) && method.getParameterCount() == 1
                && method.getParameterTypes()[0] == Class.class) {
            val realFindWithProjection = (FindWithProjection<?>) invocation.proceed();
            return wrapFindWithProjection(realFindWithProjection);
        }
        if (isShimmedLegacyQueryMethod(method)) {
            return interceptLegacyQueryMethod(invocation);
        }
        return invocation.proceed();
    }

    private static boolean isShimmedLegacyQueryMethod(Method method) {
        if (!SHIMMED_LEGACY_QUERY_METHOD.contains(method.getName())) {
            return false;
        }
        val params = method.getParameterTypes();
        return params.length >= 1 && params[0] == Query.class;
    }

    private static Object interceptLegacyQueryMethod(MethodInvocation invocation) {
        val returnType = invocation.getMethod().getReturnType();
        if (returnType == Flux.class) {
            return deferLegacyAsFlux(invocation);
        }
        if (returnType == Mono.class) {
            return deferLegacyAsMono(invocation);
        }
        return proceedSafely(invocation);
    }

    private static Flux<Object> deferLegacyAsFlux(MethodInvocation invocation) {
        val originalQuery = (Query) invocation.getArguments()[0];
        return Flux.deferContextual(ctx -> {
            val planOpt = EnforcementPlanContext.currentReactor(ctx);
            if (planOpt.isEmpty()) {
                return proceedAsFlux(invocation);
            }
            val rewriteOutcome = applyShim(planOpt.get(), originalQuery);
            return switch (rewriteOutcome) {
            case Denied denied              -> Flux.error(denied.cause());
            case Rewritten(Query rewritten) -> {
                invocation.getArguments()[0] = rewritten;
                yield proceedAsFlux(invocation);
            }
            };
        });
    }

    private static Mono<Object> deferLegacyAsMono(MethodInvocation invocation) {
        val originalQuery = (Query) invocation.getArguments()[0];
        return Mono.deferContextual(ctx -> {
            val planOpt = EnforcementPlanContext.currentReactor(ctx);
            if (planOpt.isEmpty()) {
                return proceedAsMono(invocation);
            }
            val rewriteOutcome = applyShim(planOpt.get(), originalQuery);
            return switch (rewriteOutcome) {
            case Denied denied              -> Mono.error(denied.cause());
            case Rewritten(Query rewritten) -> {
                invocation.getArguments()[0] = rewritten;
                yield proceedAsMono(invocation);
            }
            };
        });
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Flux<Object> proceedAsFlux(MethodInvocation invocation) {
        try {
            return (Flux) invocation.proceed();
        } catch (Throwable throwable) {
            return Flux.error(throwable);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Mono<Object> proceedAsMono(MethodInvocation invocation) {
        try {
            return (Mono) invocation.proceed();
        } catch (Throwable throwable) {
            return Mono.error(throwable);
        }
    }

    private static Object proceedSafely(MethodInvocation invocation) {
        try {
            return invocation.proceed();
        } catch (RuntimeException runtimeException) {
            throw runtimeException;
        } catch (Throwable throwable) {
            throw new IllegalStateException(throwable);
        }
    }

    private static <T> FindWithProjection<T> wrapFindWithProjection(FindWithProjection<T> delegate) {
        val factory = new ProxyFactory(delegate);
        factory.addAdvice((MethodInterceptor) inv -> {
            val method = inv.getMethod();
            val name   = method.getName();
            if ("matching".equals(name) && inv.getArguments().length == 1
                    && inv.getArguments()[0] instanceof Query query) {
                return wrapTerminatingFind((FindWithQuery<?>) delegate, query);
            }
            if ("as".equals(name) && inv.getArguments().length == 1) {
                val asResult = inv.proceed();
                return wrapFindWithProjection((FindWithProjection<?>) asResult);
            }
            return inv.proceed();
        });
        @SuppressWarnings("unchecked")
        val proxy = (FindWithProjection<T>) factory.getProxy();
        return proxy;
    }

    private static <T> TerminatingFind<T> wrapTerminatingFind(FindWithQuery<T> delegate, Query originalQuery) {
        val intermediateForType = delegate.matching(originalQuery);
        val factory             = new ProxyFactory(intermediateForType);
        factory.addAdvice((MethodInterceptor) inv -> dispatchTerminatingFind(delegate, originalQuery, inv.getMethod(),
                inv.getArguments()));
        @SuppressWarnings("unchecked")
        val proxy = (TerminatingFind<T>) factory.getProxy();
        return proxy;
    }

    private static Object dispatchTerminatingFind(FindWithQuery<?> delegate, Query originalQuery, Method method,
            Object[] args) throws Throwable {
        val returnType = method.getReturnType();
        if (returnType == Flux.class) {
            return Flux.deferContextual(ctx -> applyShimAndInvokeTerminating(ctx, delegate, originalQuery, method, args)
                    .flatMapMany(it -> (Flux<?>) it));
        }
        if (returnType == Mono.class) {
            return Mono.deferContextual(ctx -> applyShimAndInvokeTerminating(ctx, delegate, originalQuery, method, args)
                    .flatMap(it -> (Mono<?>) it));
        }
        return method.invoke(delegate.matching(originalQuery), args);
    }

    private static Mono<Object> applyShimAndInvokeTerminating(reactor.util.context.ContextView ctx,
            FindWithQuery<?> delegate, Query originalQuery, Method method, Object[] args) {
        val   planOpt    = EnforcementPlanContext.currentReactor(ctx);
        Query queryToUse = originalQuery;
        if (planOpt.isPresent()) {
            val outcome = applyShim(planOpt.get(), originalQuery);
            switch (outcome) {
            case Denied denied              -> {
                return Mono.error(denied.cause());
            }
            case Rewritten(Query rewritten) -> queryToUse = rewritten;
            }
        }
        try {
            return Mono.just(method.invoke(delegate.matching(queryToUse), args));
        } catch (Throwable t) {
            return Mono.error(unwrap(t));
        }
    }

    private static Throwable unwrap(Throwable t) {
        if (t instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null) {
            return ite.getCause();
        }
        return t;
    }

    private static RewriteOutcome applyShim(EnforcementPlan plan, Query originalQuery) {
        val result = plan.execute(MongoDbQueryShimSignal.of(originalQuery), false);
        if (result.failureState()) {
            return new Denied(new AccessDeniedException(ERROR_ACCESS_DENIED_OBLIGATION_FAILED));
        }
        if (result.value() instanceof Present<?>(var v) && v instanceof Query rewritten) {
            return new Rewritten(rewritten);
        }
        return new Rewritten(originalQuery);
    }

    private sealed interface RewriteOutcome permits Rewritten, Denied {
    }

    private record Rewritten(Query query) implements RewriteOutcome {}

    private record Denied(Throwable cause) implements RewriteOutcome {}
}
