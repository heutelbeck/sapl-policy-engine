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
package io.sapl.api.model;

import java.util.Set;

import reactor.core.publisher.Flux;

public non-sealed interface StreamOperator extends CompiledExpression {

    /**
     * Reactor-based evaluation entry point. Returns a {@link Flux} of
     * {@link TracedValue}s. This is the legacy path that suffers from
     * {@code combineLatest} glitches when multiple attribute streams
     * compose; see
     * {@code notes/4.1.0-notes/sapl-glitch-free-streaming-architecture.md}.
     *
     * @return a flux of traced values for this expression's evaluation
     */
    Flux<TracedValue> stream();

    /**
     * Snapshot-driven evaluation entry point. Returns an
     * {@link ExpressionResult} carrying both the computed
     * {@link Value} (or {@code null} if attribute reads could not
     * complete) and the complete set of
     * {@link io.sapl.api.attributes.AttributeFinderInvocation}s
     * this evaluation pass needed or touched.
     * <p>
     * Each implementation should override this method. The default
     * throws {@link UnsupportedOperationException} naming the
     * concrete class so the migration work list is clear at runtime
     * for any code path exercised before its operator is migrated.
     * Once every StreamOperator implementation overrides this, the
     * default is removed and {@link #stream} can be deleted (cleanup
     * commit).
     * <p>
     * Implementation pattern: walk children, accumulate their
     * subscription sets, either compute a result (if all needed reads
     * resolved against the snapshot) or return {@code null} in
     * {@code result} (if at least one read could not complete; the
     * trigger loop subscribes the missing invocations and retries).
     * For dispatching on child type (Value, PureOperator, or another
     * StreamOperator) use {@link #evalChild}.
     *
     * @param ctx evaluation context bound to a snapshot version
     * @return value plus subscription set for this evaluation pass
     *
     * @since 4.2.0
     */
    default ExpressionResult evaluateWithSubscriptions(EvaluationContext ctx) {
        throw new UnsupportedOperationException(
                "evaluateWithSubscriptions not yet migrated for " + getClass().getSimpleName());
    }

    /**
     * Helper for evaluating a child expression that may be any
     * {@link CompiledExpression} variant. Pure children are evaluated
     * via the existing {@code evaluate} entry point and contribute no
     * subscriptions. Stream children are evaluated via
     * {@link #evaluateWithSubscriptions} and may contribute
     * subscriptions and / or a {@code null} result requiring a retry.
     *
     * @param child the child expression to evaluate
     * @param ctx evaluation context bound to the same snapshot as the
     * parent's evaluation pass
     * @return the child's evaluation result wrapped uniformly
     *
     * @since 4.2.0
     */
    static ExpressionResult evalChild(CompiledExpression child, EvaluationContext ctx) {
        return switch (child) {
        case Value v          -> new ExpressionResult(v, Set.of());
        case PureOperator p   -> new ExpressionResult(p.evaluate(ctx), Set.of());
        case StreamOperator s -> s.evaluateWithSubscriptions(ctx);
        };
    }
}
