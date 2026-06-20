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
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.isFluentTerminal;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.isQueryFirst;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.planNarrows;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.toQuery;
import static io.sapl.spring.pep.data.mongo.MongoShimSupport.unwrap;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Function;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.mongodb.core.ExecutableFindOperation.FindWithProjection;
import org.springframework.data.mongodb.core.ExecutableFindOperation.FindWithQuery;
import org.springframework.data.mongodb.core.ExecutableRemoveOperation.RemoveWithQuery;
import org.springframework.data.mongodb.core.ExecutableUpdateOperation.UpdateWithQuery;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import io.sapl.spring.pep.constraints.EnforcementPlanContext;
import io.sapl.spring.pep.data.mongo.MongoShimSupport.Denied;
import io.sapl.spring.pep.data.mongo.MongoShimSupport.Rewritten;
import lombok.val;

/**
 * Blocking counterpart of {@link MongoShimMethodInterceptor} for the blocking
 * Mongo template proxy ({@code MongoTemplate}). It applies the same capability
 * classification and {@code mongo:queryRewriting} narrowing as the reactive
 * shim (both share {@link MongoShimSupport}), but executes <em>inline</em>: the
 * active enforcement plan is read synchronously from the blocking thread-local
 * ({@link EnforcementPlanContext#currentBlocking()}), and the
 * narrowed call returns its value directly rather than a deferred publisher. A
 * fluent terminal is recognised by being declared on a Spring Data fluent
 * operation interface while returning a non-fluent type
 * ({@link MongoShimSupport#isFluentTerminal(Method)}).
 *
 * @see MongoShimSupport
 * @see MongoShimMethodInterceptor
 */
public class MongoBlockingShimMethodInterceptor implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        val method = invocation.getMethod();
        if (isFluentFindEntry(method)) {
            return wrapFind((FindWithProjection<?>) invocation.proceed());
        }
        if (isFluentClassEntry(method, METHOD_UPDATE)) {
            return wrapUpdate(invocation.proceed(), null, null);
        }
        if (isFluentClassEntry(method, METHOD_REMOVE)) {
            return wrapRemove(invocation.proceed(), null);
        }
        if (isFluentAggregateDenyEntry(method)) {
            return wrapDenyOnTerminal(invocation.proceed());
        }
        if (isQueryFirst(method)) {
            return narrowLegacy(invocation);
        }
        if (isFindAllEntry(method)) {
            return rerouteFindAll(invocation);
        }
        if (DIRECT_DENY_BUCKET.contains(method.getName())) {
            return denyDirect(invocation);
        }
        return invocation.proceed();
    }

    private static Object narrowLegacy(MethodInvocation invocation) throws Throwable {
        val planOpt = EnforcementPlanContext.currentBlocking();
        if (planOpt.isEmpty()) {
            return invocation.proceed();
        }
        val args = invocation.getArguments();
        return switch (applyShim(planOpt.get(), (Query) args[0])) {
        case Denied(Throwable cause)    -> throw cause;
        case Rewritten(Query rewritten) ->
            invokeBlocking(invocation.getThis(), invocation.getMethod(), withQuery(args, rewritten));
        };
    }

    private static Object rerouteFindAll(MethodInvocation invocation) throws Throwable {
        val planOpt = EnforcementPlanContext.currentBlocking();
        if (planOpt.isEmpty()) {
            return invocation.proceed();
        }
        Query query = new Query();
        switch (applyShim(planOpt.get(), query)) {
        case Denied(Throwable cause)    -> throw cause;
        case Rewritten(Query rewritten) -> query = rewritten;
        }
        val target = (MongoOperations) Objects.requireNonNull(invocation.getThis(), "AOP target must not be null");
        val args   = invocation.getArguments();
        val type   = (Class<?>) args[0];
        if (args.length >= 2 && args[1] instanceof String collection) {
            return target.find(query, type, collection);
        }
        return target.find(query, type);
    }

    private static Object denyDirect(MethodInvocation invocation) throws Throwable {
        denyIfPlanNarrows();
        return invocation.proceed();
    }

    private static Object wrapFind(FindWithProjection<?> delegate) {
        val factory = new ProxyFactory(delegate);
        factory.addAdvice((MethodInterceptor) inv -> {
            val method = inv.getMethod();
            val name   = method.getName();
            if (METHOD_MATCHING.equals(name) && inv.getArguments().length == 1) {
                return wrapTerminatingFind(delegate, toQuery(inv.getArguments()[0]));
            }
            if (METHOD_AS.equals(name) || METHOD_IN_COLLECTION.equals(name)) {
                return wrapFind((FindWithProjection<?>) inv.proceed());
            }
            if (FLUENT_FIND_DENY.contains(name)) {
                return wrapDenyOnTerminal(inv.proceed());
            }
            if (isFluentTerminal(method)) {
                return narrowAndInvokeFind((FindWithQuery<?>) delegate, new Query(), method, inv.getArguments());
            }
            return inv.proceed();
        });
        return factory.getProxy();
    }

    private static Object wrapTerminatingFind(FindWithQuery<?> delegate, Query capturedQuery) {
        val factory = new ProxyFactory(delegate.matching(capturedQuery));
        factory.addAdvice((MethodInterceptor) inv -> {
            if (isFluentTerminal(inv.getMethod())) {
                return narrowAndInvokeFind(delegate, capturedQuery, inv.getMethod(), inv.getArguments());
            }
            return inv.proceed();
        });
        return factory.getProxy();
    }

    private static Object narrowAndInvokeFind(FindWithQuery<?> delegate, Query capturedQuery, Method method,
            Object[] args) throws Throwable {
        return invokeBlocking(delegate.matching(narrow(capturedQuery)), method, args);
    }

    private static Object wrapUpdate(Object base, Query capturedQuery, UpdateDefinition capturedUpdate) {
        val factory = new ProxyFactory(base);
        factory.setProxyTargetClass(true);
        factory.addAdvice((MethodInterceptor) inv -> {
            val method = inv.getMethod();
            val name   = method.getName();
            if (METHOD_MATCHING.equals(name) && inv.getArguments().length == 1) {
                return wrapUpdate(base, toQuery(inv.getArguments()[0]), capturedUpdate);
            }
            if (METHOD_APPLY.equals(name) && inv.getArguments().length == 1
                    && inv.getArguments()[0] instanceof UpdateDefinition update) {
                return wrapUpdate(base, capturedQuery, update);
            }
            if (METHOD_IN_COLLECTION.equals(name)) {
                return wrapUpdate(inv.proceed(), capturedQuery, capturedUpdate);
            }
            if (isFluentTerminal(method) && capturedUpdate != null) {
                val update = capturedUpdate;
                return narrowAndInvokeWrite(query -> ((UpdateWithQuery<?>) base).matching(query).apply(update),
                        capturedQuery, method, inv.getArguments());
            }
            if (isFluentTerminal(method)) {
                // A terminal reached with no captured update (for example the
                // replaceWith / findAndReplace path) cannot be narrowed here.
                throw deniedUnsupported();
            }
            val result = inv.proceed();
            if (result != null && isFluentStage(result)) {
                return wrapDenyOnTerminal(result);
            }
            return result;
        });
        return factory.getProxy();
    }

    private static Object wrapRemove(Object base, Query capturedQuery) {
        val factory = new ProxyFactory(base);
        factory.setProxyTargetClass(true);
        factory.addAdvice((MethodInterceptor) inv -> {
            val method = inv.getMethod();
            val name   = method.getName();
            if (METHOD_MATCHING.equals(name) && inv.getArguments().length == 1) {
                return wrapRemove(base, toQuery(inv.getArguments()[0]));
            }
            if (METHOD_IN_COLLECTION.equals(name)) {
                return wrapRemove(inv.proceed(), capturedQuery);
            }
            if (isFluentTerminal(method)) {
                return narrowAndInvokeWrite(((RemoveWithQuery<?>) base)::matching, capturedQuery, method,
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

    private static Object narrowAndInvokeWrite(Function<Query, Object> rescope, Query capturedQuery, Method method,
            Object[] args) throws Throwable {
        val query = narrow(capturedQuery != null ? capturedQuery : new Query());
        return invokeBlocking(rescope.apply(query), method, args);
    }

    private static Object wrapDenyOnTerminal(Object delegate) {
        val factory = new ProxyFactory(delegate);
        factory.setProxyTargetClass(true);
        factory.addAdvice((MethodInterceptor) inv -> {
            val method = inv.getMethod();
            if (isFluentTerminal(method)) {
                denyIfPlanNarrows();
                return invokeBlocking(delegate, method, inv.getArguments());
            }
            val result = inv.proceed();
            if (result != null && isFluentStage(result)) {
                return wrapDenyOnTerminal(result);
            }
            return result;
        });
        return factory.getProxy();
    }

    private static Query narrow(Query capturedQuery) throws Throwable {
        val planOpt = EnforcementPlanContext.currentBlocking();
        if (planOpt.isEmpty()) {
            return capturedQuery;
        }
        return switch (applyShim(planOpt.get(), capturedQuery)) {
        case Denied(Throwable cause)    -> throw cause;
        case Rewritten(Query rewritten) -> rewritten;
        };
    }

    private static void denyIfPlanNarrows() {
        val planOpt = EnforcementPlanContext.currentBlocking();
        if (planOpt.isPresent() && planNarrows(planOpt.get())) {
            throw deniedUnsupported();
        }
    }

    private static Object[] withQuery(Object[] originalArgs, Query rewritten) {
        val copy = originalArgs.clone();
        copy[0] = rewritten;
        return copy;
    }

    private static Object invokeBlocking(Object target, Method method, Object[] args) throws Throwable {
        try {
            return invokeReflectively(target, method, args);
        } catch (ReflectiveOperationException reflectiveFailure) {
            throw unwrap(reflectiveFailure);
        }
    }
}
