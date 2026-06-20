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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public non-sealed interface StreamOperator extends CompiledExpression {

    /**
     * Snapshot-driven evaluation entry point. Returns an
     * {@link ExpressionResult} carrying both the computed
     * {@link Value} (or {@code null} if attribute reads could not
     * complete) and the complete dependency map this evaluation pass
     * needed or touched.
     * <p>
     * Each implementation should override this method. The default
     * throws {@link UnsupportedOperationException} naming the
     * concrete class so the migration work list is clear at runtime
     * for any code path exercised before its operator is migrated.
     * <p>
     * Implementation pattern: walk children, accumulate their
     * dependency maps, either compute a result (if all needed reads
     * resolved against the snapshot) or return {@code null} in
     * {@code result} (if at least one read could not complete; the
     * trigger loop subscribes the missing invocations and retries).
     * For dispatching on child type (Value, PureOperator, or another
     * StreamOperator) use {@link #evalChild}.
     *
     * @param ctx evaluation context bound to a snapshot version
     * @return value plus dependency map for this evaluation pass
     *
     * @since 4.1.0
     */
    ExpressionResult evaluate(EvaluationContext ctx);

    /**
     * Helper for evaluating a child expression that may be any
     * {@link CompiledExpression} variant, accumulating any attribute
     * dependencies contributed by stream children into the supplied
     * map. Pure children evaluate inline and add nothing. Stream
     * children evaluate via {@link #evaluate}; their full dependency
     * map is merged into {@code deps} and their value (possibly
     * {@code null} if their evaluation could not complete) is returned.
     * <p>
     * Returns the child's {@link Value} directly without boxing
     * through {@link ExpressionResult}, keeping the per-child cost
     * to the actual work for non-stream children.
     *
     * @param child the child expression to evaluate
     * @param ctx evaluation context bound to the same snapshot as the
     * parent's evaluation pass
     * @param deps accumulator for dependencies contributed by stream
     * children; pure and value children leave it untouched
     * @return the child's value, or {@code null} if a stream child
     * could not complete
     *
     * @since 4.1.0
     */
    static Value evalChild(CompiledExpression child, EvaluationContext ctx,
            Map<SubscriptionKey, List<Occurrence>> deps) {
        return switch (child) {
        case Value v          -> v;
        case PureOperator p   -> p.evaluate(ctx);
        case StreamOperator s -> {
            var r = s.evaluate(ctx);
            mergeDependencies(deps, r.dependencies());
            yield r.result();
        }
        };
    }

    /**
     * Merges {@code source} into {@code target}, appending each
     * source key's occurrence list to the target's. Used by callers
     * that resolve a child's {@link ExpressionResult} directly (rather
     * than through {@link #evalChild}) and need to fold its
     * dependencies into their own accumulator.
     *
     * @param target the accumulator to merge into
     * @param source the dependencies to merge from
     *
     * @since 4.1.0
     */
    static void mergeDependencies(Map<SubscriptionKey, List<Occurrence>> target,
            Map<SubscriptionKey, List<Occurrence>> source) {
        for (var entry : source.entrySet()) {
            target.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
        }
    }
}
