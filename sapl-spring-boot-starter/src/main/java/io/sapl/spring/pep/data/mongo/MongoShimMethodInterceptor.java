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

import static io.sapl.spring.pep.data.mongo.MongoShimSupport.DIRECT_DENY_BUCKET;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.ERROR_UNEXPECTED_TERMINATING_RETURN_TYPE;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.FLUENT_FIND_DENY;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.METHOD_APPLY;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.METHOD_AS;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.METHOD_IN_COLLECTION;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.METHOD_MATCHING;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.METHOD_REMOVE;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.METHOD_UPDATE;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.applyShim;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.deniedUnsupported;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.invokeReflectively;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.isFindAllEntry;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.isFluentAggregateDenyEntry;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.isFluentClassEntry;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.isFluentFindEntry;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.isFluentStage;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.isQueryFirst;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.toQuery;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.unwrap;

import java.lang.reflect.Method;
import java.util.function.Function;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.mongodb.core.ReactiveFindOperation.FindWithProjection;
import org.springframework.data.mongodb.core.ReactiveFindOperation.FindWithQuery;
import org.springframework.data.mongodb.core.ReactiveFindOperation.TerminatingFind;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.ReactiveRemoveOperation.RemoveWithQuery;
import org.springframework.data.mongodb.core.ReactiveUpdateOperation.UpdateWithQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import io.sapl.spring.pep.constraints.EnforcementPlanContext;
import io.sapl.spring.pep.data.mongo.MongoShimSupport.Denied;
import io.sapl.spring.pep.data.mongo.MongoShimSupport.RewriteOutcome;
import io.sapl.spring.pep.data.mongo.MongoShimSupport.Rewritten;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/**
 * AOP {@link MethodInterceptor} for the reactive Mongo template proxy
 * ({@code ReactiveMongoTemplate}). It narrows every data-reaching operation
 * with
 * the active {@code mongo:queryRewriting} obligation so that no terminal call
 * can reach the database without the obligation being applied, and any call
 * that cannot be narrowed fails closed while a narrowing obligation is in
 * scope.
 * The method classification and obligation application are shared with the
 * blocking shim through {@link MongoShimSupport}; this class supplies only the
 * reactive execution: the obligation is read at the terminal reactive boundary
 * ({@code deferContextual}) where the active plan is reachable, and the rewrite
 * is stateless (it never mutates the shared {@link MethodInvocation}; each
 * subscription builds its own argument array and invokes the real target).
 *
 * @see MongoShimSupport
 * @see MongoBlockingShimMethodInterceptor
 */
public class MongoShimMethodInterceptor implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        val method = invocation.getMethod();
        if (isFluentFindEntry(method)) {
            return wrapFindWithProjection((FindWithProjection<?>) invocation.proceed());
        }
        if (isFluentClassEntry(method, METHOD_UPDATE)) {
            return wrapUpdateBuilder(invocation.proceed(), null, null);
        }
        if (isFluentClassEntry(method, METHOD_REMOVE)) {
            return wrapRemoveBuilder(invocation.proceed(), null);
        }
        if (isFluentAggregateDenyEntry(method)) {
            return wrapDenyOnTerminal(invocation.proceed());
        }
        if (isQueryFirst(method) && isReactiveReturn(method)) {
            return narrowLegacyQuery(invocation);
        }
        if (isFindAllEntry(method)) {
            return rerouteFindAll(invocation);
        }
        if (DIRECT_DENY_BUCKET.contains(method.getName()) && isReactiveReturn(method)) {
            return denyDirect(invocation);
        }
        return invocation.proceed();
    }

    private static boolean isReactiveReturn(Method method) {
        val returnType = method.getReturnType();
        return returnType == Flux.class || returnType == Mono.class;
    }

    private static Object narrowLegacyQuery(MethodInvocation invocation) {
        val target       = invocation.getThis();
        val method       = invocation.getMethod();
        val originalArgs = invocation.getArguments().clone();
        if (method.getReturnType() == Flux.class) {
            return Flux.deferContextual(ctx -> narrowAndInvokeFlux(ctx, target, method, originalArgs));
        }
        return Mono.deferContextual(ctx -> narrowAndInvokeMono(ctx, target, method, originalArgs));
    }

    private static Flux<Object> narrowAndInvokeFlux(ContextView ctx, Object target, Method method, Object[] args) {
        val planOpt = EnforcementPlanContext.currentReactor(ctx);
        if (planOpt.isEmpty()) {
            return invokeAsFlux(target, method, args);
        }
        return switch (applyShim(planOpt.get(), (Query) args[0])) {
        case Denied(Throwable cause)    -> Flux.error(cause);
        case Rewritten(Query rewritten) -> invokeAsFlux(target, method, withQuery(args, rewritten));
        };
    }

    private static Mono<Object> narrowAndInvokeMono(ContextView ctx, Object target, Method method, Object[] args) {
        val planOpt = EnforcementPlanContext.currentReactor(ctx);
        if (planOpt.isEmpty()) {
            return invokeAsMono(target, method, args);
        }
        return switch (applyShim(planOpt.get(), (Query) args[0])) {
        case Denied(Throwable cause)    -> Mono.error(cause);
        case Rewritten(Query rewritten) -> invokeAsMono(target, method, withQuery(args, rewritten));
        };
    }

    private static Object[] withQuery(Object[] originalArgs, Query rewritten) {
        val copy = originalArgs.clone();
        copy[0] = rewritten;
        return copy;
    }

    private static Flux<Object> rerouteFindAll(MethodInvocation invocation) {
        val target = (ReactiveMongoOperations) invocation.getThis();
        val args   = invocation.getArguments().clone();
        return Flux.deferContextual(ctx -> {
            val query = narrowEmptyQuery(ctx);
            if (query instanceof Denied(Throwable cause)) {
                return Flux.error(cause);
            }
            val effective = ((Rewritten) query).query();
            val type      = (Class<?>) args[0];
            if (args.length >= 2 && args[1] instanceof String collection) {
                return castFlux(target.find(effective, type, collection));
            }
            return castFlux(target.find(effective, type));
        });
    }

    private static Object denyDirect(MethodInvocation invocation) {
        val target       = invocation.getThis();
        val method       = invocation.getMethod();
        val originalArgs = invocation.getArguments();
        if (method.getReturnType() == Flux.class) {
            return Flux.deferContextual(ctx -> mustDenyUnsupported(ctx) ? Flux.error(deniedUnsupported())
                    : invokeAsFlux(target, method, originalArgs));
        }
        return Mono.deferContextual(ctx -> mustDenyUnsupported(ctx) ? Mono.error(deniedUnsupported())
                : invokeAsMono(target, method, originalArgs));
    }

    private static <T> FindWithProjection<T> wrapFindWithProjection(FindWithProjection<T> delegate) {
        val factory = new ProxyFactory(delegate);
        factory.addAdvice((MethodInterceptor) inv -> {
            val method = inv.getMethod();
            val name   = method.getName();
            if (METHOD_MATCHING.equals(name) && inv.getArguments().length == 1) {
                return wrapTerminatingFind((FindWithQuery<?>) delegate, toQuery(inv.getArguments()[0]));
            }
            if (METHOD_AS.equals(name) || METHOD_IN_COLLECTION.equals(name)) {
                return wrapFindWithProjection((FindWithProjection<?>) inv.proceed());
            }
            if (FLUENT_FIND_DENY.contains(name)) {
                return wrapDenyOnTerminal(inv.proceed());
            }
            if (isReactiveReturn(method)) {
                return dispatchTerminatingFind((FindWithQuery<?>) delegate, new Query(), method, inv.getArguments());
            }
            return inv.proceed();
        });
        @SuppressWarnings("unchecked")
        val proxy = (FindWithProjection<T>) factory.getProxy();
        return proxy;
    }

    private static <T> TerminatingFind<T> wrapTerminatingFind(FindWithQuery<T> delegate, Query capturedQuery) {
        val intermediateForType = delegate.matching(capturedQuery);
        val factory             = new ProxyFactory(intermediateForType);
        factory.addAdvice((MethodInterceptor) inv -> {
            if (isReactiveReturn(inv.getMethod())) {
                return dispatchTerminatingFind(delegate, capturedQuery, inv.getMethod(), inv.getArguments());
            }
            return inv.proceed();
        });
        @SuppressWarnings("unchecked")
        val proxy = (TerminatingFind<T>) factory.getProxy();
        return proxy;
    }

    private static Object dispatchTerminatingFind(FindWithQuery<?> delegate, Query originalQuery, Method method,
            Object[] args) {
        val returnType = method.getReturnType();
        if (returnType == Flux.class) {
            return Flux.deferContextual(ctx -> applyShimAndInvokeTerminating(ctx, delegate, originalQuery, method, args)
                    .flatMapMany(it -> (Flux<?>) it));
        }
        if (returnType == Mono.class) {
            return Mono.deferContextual(ctx -> applyShimAndInvokeTerminating(ctx, delegate, originalQuery, method, args)
                    .flatMap(it -> (Mono<?>) it));
        }
        throw new UnsupportedOperationException(ERROR_UNEXPECTED_TERMINATING_RETURN_TYPE + returnType.getName());
    }

    private static Mono<Object> applyShimAndInvokeTerminating(ContextView ctx, FindWithQuery<?> delegate,
            Query originalQuery, Method method, Object[] args) {
        val   planOpt    = EnforcementPlanContext.currentReactor(ctx);
        Query queryToUse = originalQuery;
        if (planOpt.isPresent()) {
            switch (applyShim(planOpt.get(), originalQuery)) {
            case Denied(Throwable cause)    -> {
                return Mono.error(cause);
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

    private static Object wrapUpdateBuilder(Object base, Query capturedQuery, UpdateDefinition capturedUpdate) {
        val factory = new ProxyFactory(base);
        factory.setProxyTargetClass(true);
        factory.addAdvice((MethodInterceptor) inv -> {
            val method = inv.getMethod();
            val name   = method.getName();
            if (METHOD_MATCHING.equals(name) && inv.getArguments().length == 1) {
                return wrapUpdateBuilder(base, toQuery(inv.getArguments()[0]), capturedUpdate);
            }
            if (METHOD_APPLY.equals(name) && inv.getArguments().length == 1
                    && inv.getArguments()[0] instanceof UpdateDefinition update) {
                return wrapUpdateBuilder(base, capturedQuery, update);
            }
            if (METHOD_IN_COLLECTION.equals(name)) {
                return wrapUpdateBuilder(inv.proceed(), capturedQuery, capturedUpdate);
            }
            if (isReactiveReturn(method) && capturedUpdate != null) {
                val update = capturedUpdate;
                return dispatchWriteTerminal(query -> ((UpdateWithQuery<?>) base).matching(query).apply(update),
                        capturedQuery, method, inv.getArguments());
            }
            if (isReactiveReturn(method)) {
                // A terminal reached with no captured update (for example the
                // replaceWith / findAndReplace path) cannot be narrowed here.
                return denyTerminal(method);
            }
            val result = inv.proceed();
            if (result != null && isFluentStage(result)) {
                return wrapDenyOnTerminal(result);
            }
            return result;
        });
        return factory.getProxy();
    }

    private static Object wrapRemoveBuilder(Object base, Query capturedQuery) {
        val factory = new ProxyFactory(base);
        factory.setProxyTargetClass(true);
        factory.addAdvice((MethodInterceptor) inv -> {
            val method = inv.getMethod();
            val name   = method.getName();
            if (METHOD_MATCHING.equals(name) && inv.getArguments().length == 1) {
                return wrapRemoveBuilder(base, toQuery(inv.getArguments()[0]));
            }
            if (METHOD_IN_COLLECTION.equals(name)) {
                return wrapRemoveBuilder(inv.proceed(), capturedQuery);
            }
            if (isReactiveReturn(method)) {
                return dispatchWriteTerminal(((RemoveWithQuery<?>) base)::matching, capturedQuery, method,
                        inv.getArguments());
            }
            val result = inv.proceed();
            if (result != null && isFluentStage(result)) {
                return wrapDenyOnTerminal(result);
            }
            return result;
        });
        return factory.getProxy();
    }

    private static Object dispatchWriteTerminal(Function<Query, Object> rescope, Query capturedQuery, Method method,
            Object[] args) {
        val returnType = method.getReturnType();
        if (returnType == Flux.class) {
            return Flux.deferContextual(ctx -> applyShimAndInvokeWrite(ctx, rescope, capturedQuery, method, args)
                    .flatMapMany(it -> (Flux<?>) it));
        }
        if (returnType == Mono.class) {
            return Mono.deferContextual(ctx -> applyShimAndInvokeWrite(ctx, rescope, capturedQuery, method, args)
                    .flatMap(it -> (Mono<?>) it));
        }
        throw new UnsupportedOperationException(ERROR_UNEXPECTED_TERMINATING_RETURN_TYPE + returnType.getName());
    }

    private static Mono<Object> applyShimAndInvokeWrite(ContextView ctx, Function<Query, Object> rescope,
            Query capturedQuery, Method method, Object[] args) {
        Query queryToUse = capturedQuery != null ? capturedQuery : new Query();
        val   planOpt    = EnforcementPlanContext.currentReactor(ctx);
        if (planOpt.isPresent()) {
            switch (applyShim(planOpt.get(), queryToUse)) {
            case Denied(Throwable cause)    -> {
                return Mono.error(cause);
            }
            case Rewritten(Query rewritten) -> queryToUse = rewritten;
            }
        }
        try {
            return Mono.just(invokeReflectively(rescope.apply(queryToUse), method, args));
        } catch (Exception t) {
            return Mono.error(unwrap(t));
        }
    }

    private static Object denyTerminal(Method method) {
        if (method.getReturnType() == Flux.class) {
            return Flux.error(deniedUnsupported());
        }
        return Mono.error(deniedUnsupported());
    }

    private static Object wrapDenyOnTerminal(Object delegate) {
        val factory = new ProxyFactory(delegate);
        // The fluent aggregate builder objects implement several fluent interfaces that
        // declare covariantly-overridden methods with the same erasure, which a JDK
        // proxy
        // over all of those interfaces cannot represent, so subclass the concrete
        // builder.
        factory.setProxyTargetClass(true);
        factory.addAdvice((MethodInterceptor) inv -> {
            val method = inv.getMethod();
            if (method.getReturnType() == Flux.class) {
                val args = inv.getArguments();
                return Flux.deferContextual(ctx -> mustDenyUnsupported(ctx) ? Flux.error(deniedUnsupported())
                        : invokeAsFlux(delegate, method, args));
            }
            if (method.getReturnType() == Mono.class) {
                val args = inv.getArguments();
                return Mono.deferContextual(ctx -> mustDenyUnsupported(ctx) ? Mono.error(deniedUnsupported())
                        : invokeAsMono(delegate, method, args));
            }
            val result = inv.proceed();
            if (result != null && isFluentStage(result)) {
                return wrapDenyOnTerminal(result);
            }
            return result;
        });
        return factory.getProxy();
    }

    private static boolean mustDenyUnsupported(ContextView ctx) {
        return EnforcementPlanContext.currentReactor(ctx).map(MongoShimSupport::planNarrows).orElse(false);
    }

    private static RewriteOutcome narrowEmptyQuery(ContextView ctx) {
        val planOpt = EnforcementPlanContext.currentReactor(ctx);
        if (planOpt.isEmpty()) {
            return new Rewritten(new Query());
        }
        return applyShim(planOpt.get(), new Query());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Flux<Object> castFlux(Object publisher) {
        return (Flux) publisher;
    }

    private static Flux<Object> invokeAsFlux(Object target, Method method, Object[] args) {
        try {
            return castFlux(invokeReflectively(target, method, args));
        } catch (Exception throwable) {
            return Flux.error(unwrap(throwable));
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Mono<Object> invokeAsMono(Object target, Method method, Object[] args) {
        try {
            return (Mono) invokeReflectively(target, method, args);
        } catch (Exception throwable) {
            return Mono.error(unwrap(throwable));
        }
    }
}
