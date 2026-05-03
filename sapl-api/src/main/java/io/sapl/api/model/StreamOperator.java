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

import org.jspecify.annotations.Nullable;

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
    default ExpressionResult evaluate(EvaluationContext ctx) {
        throw new UnsupportedOperationException(
                "evaluateWithSubscriptions not yet migrated for " + getClass().getSimpleName());
    }

    /**
     * Helper for evaluating a child expression that may be any
     * {@link CompiledExpression} variant, accumulating any
     * {@link Subscription}s contributed by stream children into the
     * supplied set. Pure children evaluate inline and add nothing.
     * Stream children evaluate via {@link #evaluate}; their full
     * subscription set is merged into {@code subs} and their value
     * (possibly {@code null} if their evaluation could not complete)
     * is returned.
     * <p>
     * Returns the child's {@link Value} directly without boxing
     * through {@link ExpressionResult}, keeping the per-child cost
     * to the actual work for non-stream children.
     *
     * @param child the child expression to evaluate
     * @param ctx evaluation context bound to the same snapshot as the
     * parent's evaluation pass
     * @param subs accumulator for subscriptions contributed by stream
     * children; pure and value children leave it untouched
     * @return the child's value, or {@code null} if a stream child
     * could not complete
     *
     * @since 4.2.0
     */
    static @Nullable Value evalChild(CompiledExpression child, EvaluationContext ctx, Set<Subscription> subs) {
        return switch (child) {
        case Value v          -> v;
        case PureOperator p   -> p.evaluate(ctx);
        case StreamOperator s -> {
            var r = s.evaluate(ctx);
            subs.addAll(r.subscriptions());
            yield r.result();
        }
        };
    }
}
