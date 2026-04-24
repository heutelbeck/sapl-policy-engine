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

import java.util.function.Function;

import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.EnforcementPlanContext;
import io.sapl.spring.pep.constraints.Signal.MongoDbQueryShimSignal;
import io.sapl.spring.util.Maybe.Present;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import lombok.val;
import reactor.core.publisher.Flux;

/**
 * Wraps a {@link ReactiveMongoTemplate} delegate, intercepting the
 * query-bearing {@code find} methods to fire {@link MongoDbQueryShimSignal}
 * against the active {@link EnforcementPlan} (looked up via
 * {@link EnforcementPlanContext}). All other {@link ReactiveMongoOperations}
 * methods forward to the delegate via Lombok's {@link Delegate}. Pass-through
 * when no plan is in scope, so repository operations called outside any
 * PEP-wrapped flow proceed unmodified.
 *
 * Implements {@link ReactiveMongoOperations} (the interface). Injection sites
 * typed as the concrete {@code ReactiveMongoTemplate} class will not be
 * satisfied by this wrapper. Use the interface type for injection.
 */
@RequiredArgsConstructor
public class ShimReactiveMongoTemplate implements ReactiveMongoOperations {

    private static final String ERROR_ACCESS_DENIED_OBLIGATION_FAILED = "Access Denied. A MongoDB query-manipulation obligation handler failed.";

    @Delegate(types = ReactiveMongoOperations.class, excludes = ShimmedFindMethods.class)
    private final ReactiveMongoTemplate delegate;

    @Override
    public <T> Flux<T> find(Query query, Class<T> entityClass) {
        return Flux.deferContextual(ctx -> EnforcementPlanContext.currentReactor(ctx)
                .map(plan -> applyShim(plan, query, q -> delegate.find(q, entityClass)))
                .orElseGet(() -> delegate.find(query, entityClass)));
    }

    @Override
    public <T> Flux<T> find(Query query, Class<T> entityClass, String collectionName) {
        return Flux.deferContextual(ctx -> EnforcementPlanContext.currentReactor(ctx)
                .map(plan -> applyShim(plan, query, q -> delegate.find(q, entityClass, collectionName)))
                .orElseGet(() -> delegate.find(query, entityClass, collectionName)));
    }

    private static <T> Flux<T> applyShim(EnforcementPlan plan, Query query, Function<Query, Flux<T>> downstream) {
        val result = plan.execute(MongoDbQueryShimSignal.of(query), false);
        if (result.failureState()) {
            return Flux.error(new AccessDeniedException(ERROR_ACCESS_DENIED_OBLIGATION_FAILED));
        }
        if (result.value() instanceof Present<?>(var v) && v instanceof Query mapped) {
            return downstream.apply(mapped);
        }
        return downstream.apply(query);
    }

    /** Method signatures excluded from {@link Delegate} forwarding. */
    private interface ShimmedFindMethods {
        <T> Flux<T> find(Query query, Class<T> entityClass);

        <T> Flux<T> find(Query query, Class<T> entityClass, String collectionName);
    }
}
