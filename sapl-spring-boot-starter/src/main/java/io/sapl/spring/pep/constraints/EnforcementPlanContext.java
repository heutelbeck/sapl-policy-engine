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
package io.sapl.spring.pep.constraints;

import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/**
 * Holder for the active {@link EnforcementPlan} during a PEP-wrapped
 * invocation. Shim wrappers placed deeper in the
 * call graph (e.g. around Spring Data templates) read the plan from here and
 * fire shim signals against it. When no plan
 * is in scope, lookups return {@link Optional#empty()} and shim wrappers pass
 * through unchanged.
 * </p>
 * Reactive flows propagate the plan via the Reactor {@link ContextView} keyed
 * by {@link #REACTOR_KEY}. Blocking flows
 * propagate via a {@link ThreadLocal}.
 */
@UtilityClass
public class EnforcementPlanContext {

    public static final String REACTOR_KEY = "io.sapl.enforcement.plan";

    private static final ThreadLocal<Object> BLOCKING = new ThreadLocal<>();

    public static Optional<EnforcementPlan> currentReactor(ContextView ctx) {
        return resolve(ctx.getOrDefault(REACTOR_KEY, null));
    }

    public static <T> Mono<T> withReactor(EnforcementPlan plan, Mono<T> body) {
        return body.contextWrite(ctx -> ctx.put(REACTOR_KEY, plan));
    }

    public static Optional<EnforcementPlan> currentBlocking() {
        return resolve(BLOCKING.get());
    }

    public static <T> T withBlocking(EnforcementPlan plan, Supplier<T> body) {
        val previous = BLOCKING.get();
        bindBlocking(plan);
        try {
            return body.get();
        } finally {
            bindContext(previous);
        }
    }

    /**
     * Sets or clears the thread-bound plan directly. Callers that cannot wrap their
     * body in a {@link Supplier} (e.g.
     * because the body throws {@link Throwable}) use this together with a manual
     * try/finally. Pass {@code null} to
     * clear the binding.
     */
    public static void bindBlocking(EnforcementPlan plan) {
        bindContext(plan);
    }

    /**
     * Returns the binding without resolving a streaming plan supplier.
     *
     * @return the plan or supplier held for context propagation, or null
     */
    public static @Nullable Object currentBinding() {
        return BLOCKING.get();
    }

    /**
     * Restores a context binding, preserving live streaming plan lookups.
     *
     * @param binding an enforcement plan, its supplier, or null to clear
     */
    public static void bindContext(@Nullable Object binding) {
        if (binding == null) {
            BLOCKING.remove();
        } else {
            BLOCKING.set(binding);
        }
    }

    private static Optional<EnforcementPlan> resolve(@Nullable Object binding) {
        if (binding instanceof Supplier<?> supplier) {
            return Optional.ofNullable((EnforcementPlan) supplier.get());
        }
        return Optional.ofNullable((EnforcementPlan) binding);
    }
}
