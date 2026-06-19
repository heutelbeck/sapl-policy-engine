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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

import org.springframework.data.mongodb.core.query.CriteriaDefinition;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.Signal.MongoDbQueryShimSignal;
import io.sapl.spring.util.Maybe.Present;
import lombok.val;

/**
 * Execution-agnostic helpers shared by the reactive
 * ({@link MongoShimMethodInterceptor}) and blocking
 * ({@link MongoBlockingShimMethodInterceptor}) Mongo query-rewriting shims.
 * Holds the method classification (which capability bucket a template method
 * falls into), the application of the {@code mongo:queryRewriting} obligation
 * to a Spring {@link Query}, and the reflective invocation helpers. The two
 * interceptors differ only in how they execute (deferred on the reactor context
 * versus inline on the blocking thread-local), so everything that decides
 * <em>what</em> to do lives here.
 */
final class MongoShimSupport {

    static final String ERROR_ACCESS_DENIED_OBLIGATION_FAILED     = "Access Denied. A MongoDB query-rewriting obligation handler failed.";
    static final String ERROR_ACCESS_DENIED_UNSUPPORTED_OPERATION = "Access Denied. A MongoDB query-rewriting obligation is in force but the requested operation cannot be narrowed by a row-level query.";
    static final String ERROR_UNEXPECTED_TERMINATING_RETURN_TYPE  = "Cannot enforce the query-rewriting obligation for an unexpected terminating return type: ";

    static final String METHOD_QUERY         = "query";
    static final String METHOD_FIND_ALL      = "findAll";
    static final String METHOD_AS            = "as";
    static final String METHOD_IN_COLLECTION = "inCollection";
    static final String METHOD_MATCHING      = "matching";
    static final String METHOD_UPDATE        = "update";
    static final String METHOD_REMOVE        = "remove";
    static final String METHOD_APPLY         = "apply";

    static final Set<String> FLUENT_AGGREGATE_DENY = Set.of("aggregateAndReturn", "mapReduce");
    static final Set<String> DIRECT_DENY_BUCKET    = Set.of("aggregate", "mapReduce", "geoNear", "estimatedCount");
    static final Set<String> FLUENT_FIND_DENY      = Set.of("distinct", "near");

    private static final String FLUENT_PACKAGE_PREFIX  = "org.springframework.data.mongodb.core.";
    private static final String REACTIVE_FLUENT_PREFIX = FLUENT_PACKAGE_PREFIX + "Reactive";
    private static final String BLOCKING_FLUENT_PREFIX = FLUENT_PACKAGE_PREFIX + "Executable";

    private MongoShimSupport() {
    }

    static boolean isFluentFindEntry(Method method) {
        return METHOD_QUERY.equals(method.getName()) && method.getParameterCount() == 1
                && method.getParameterTypes()[0] == Class.class;
    }

    static boolean isFluentClassEntry(Method method, String name) {
        return name.equals(method.getName()) && method.getParameterCount() == 1
                && method.getParameterTypes()[0] == Class.class && returnsFluentOperation(method);
    }

    static boolean isFluentAggregateDenyEntry(Method method) {
        return FLUENT_AGGREGATE_DENY.contains(method.getName()) && method.getParameterCount() == 1
                && method.getParameterTypes()[0] == Class.class && returnsFluentOperation(method);
    }

    static boolean isQueryFirst(Method method) {
        val params = method.getParameterTypes();
        return params.length >= 1 && params[0] == Query.class;
    }

    static boolean isFindAllEntry(Method method) {
        val params = method.getParameterTypes();
        return METHOD_FIND_ALL.equals(method.getName()) && params.length >= 1 && params[0] == Class.class;
    }

    private static boolean returnsFluentOperation(Method method) {
        return isMongoFluentTypeName(method.getReturnType().getName());
    }

    /**
     * Whether a method on a fluent builder is a data-reaching terminal: it is
     * declared on one of the Spring Data Mongo fluent operation interfaces and
     * does not return another fluent operation stage. This is how the blocking
     * shim recognises terminals, whose return types ({@code List}, {@code long},
     * {@code boolean}, an entity, ...) are not reactive publishers.
     */
    static boolean isFluentTerminal(Method method) {
        return method.getDeclaringClass().getName().startsWith(FLUENT_PACKAGE_PREFIX)
                && !returnsFluentOperation(method);
    }

    static boolean isFluentStage(Object candidate) {
        if (isMongoFluentTypeName(candidate.getClass().getName())) {
            return true;
        }
        for (val iface : candidate.getClass().getInterfaces()) {
            if (isMongoFluentTypeName(iface.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMongoFluentTypeName(String name) {
        return name.startsWith(REACTIVE_FLUENT_PREFIX) || name.startsWith(BLOCKING_FLUENT_PREFIX);
    }

    static Query toQuery(Object matchingArgument) {
        if (matchingArgument instanceof Query query) {
            return query;
        }
        if (matchingArgument instanceof CriteriaDefinition criteria) {
            return Query.query(criteria);
        }
        return new Query();
    }

    static AccessDeniedException deniedUnsupported() {
        return new AccessDeniedException(ERROR_ACCESS_DENIED_UNSUPPORTED_OPERATION);
    }

    /**
     * Whether an obligation that cannot be applied as a row-level query
     * narrowing is in force, in which case an unsupported operation must fail
     * closed. True when the plan denies, or when it rewrites an empty query into
     * a non-empty one (a real narrowing is active that this operation cannot
     * honour).
     */
    static boolean planNarrows(EnforcementPlan plan) {
        return switch (applyShim(plan, new Query())) {
        case Denied ignored         -> true;
        case Rewritten(Query query) -> !query.getQueryObject().isEmpty();
        };
    }

    static RewriteOutcome applyShim(EnforcementPlan plan, Query originalQuery) {
        val result = plan.execute(MongoDbQueryShimSignal.of(originalQuery), false);
        if (result.failureState()) {
            return new Denied(new AccessDeniedException(ERROR_ACCESS_DENIED_OBLIGATION_FAILED));
        }
        if (result.value() instanceof Present<?>(var v) && v instanceof Query rewritten) {
            return new Rewritten(rewritten);
        }
        return new Rewritten(originalQuery);
    }

    static Object invokeReflectively(Object target, Method method, Object[] args) throws ReflectiveOperationException {
        // A fluent builder is proxied by subclassing its concrete (non-public)
        // implementation
        // class, so the resolved method is declared on that inaccessible class even
        // though it
        // is public. Make it accessible before invoking it on the real target.
        if (!method.canAccess(target)) {
            method.setAccessible(true);
        }
        return method.invoke(target, args);
    }

    static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof InvocationTargetException ite && ite.getCause() != null) {
            return ite.getCause();
        }
        return throwable;
    }

    sealed interface RewriteOutcome permits Rewritten, Denied {
    }

    record Rewritten(Query query) implements RewriteOutcome {}

    record Denied(Throwable cause) implements RewriteOutcome {}
}
