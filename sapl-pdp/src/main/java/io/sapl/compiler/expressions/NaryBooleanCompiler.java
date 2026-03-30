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
package io.sapl.compiler.expressions;

import io.sapl.api.model.AttributeRecord;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.TracedValue;
import io.sapl.api.model.Value;
import io.sapl.ast.Conjunction;
import io.sapl.ast.Disjunction;
import io.sapl.compiler.index.SemanticHashing;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiles N-ary boolean operators: Conjunction ({@code &&}, {@code &}) and
 * Disjunction ({@code ||}, {@code |}).
 * <p>
 * Both operators use the same compilation strategy, parameterized by:
 * <ul>
 * <li>shortCircuitValue: FALSE for AND, TRUE for OR</li>
 * <li>identityValue: TRUE for AND, FALSE for OR</li>
 * </ul>
 * <p>
 * Compilation strategy:
 * <ol>
 * <li>Compile all operands, fold Values at compile time</li>
 * <li>Split remaining operands into pure and stream buckets</li>
 * <li>If all Pures: return {@link NaryBooleanPure} (simple loop at
 * runtime)</li>
 * <li>If any Streams: wrap in deferContextual that evaluates pures first,
 * then subscribes to the pre-built stream composition (lazy switchMap chain
 * or eager combineLatest)</li>
 * </ol>
 * <p>
 * Key insight: AND/OR are commutative, so operands can be reordered by cost
 * stratum without changing semantics.
 */
@UtilityClass
public class NaryBooleanCompiler {

    private static final String ERROR_TYPE_MISMATCH = "Expected BOOLEAN but got: %s.";

    /**
     * Compiles a Conjunction ({@code a && b && c && ...} or
     * {@code a & b & c & ...}). Short-circuits on first {@code false}.
     */
    public static CompiledExpression compileConjunction(Conjunction c, CompilationContext ctx) {
        val compiled = c.operands().stream().map(o -> ExpressionCompiler.compile(o, ctx)).toList();
        return compile(compiled, c.location(), Value.FALSE, Value.TRUE, c.isEager());
    }

    /**
     * Compiles a Disjunction ({@code a || b || c || ...} or
     * {@code a | b | c | ...}). Short-circuits on first {@code true}.
     */
    public static CompiledExpression compileDisjunction(Disjunction d, CompilationContext ctx) {
        val compiled = d.operands().stream().map(o -> ExpressionCompiler.compile(o, ctx)).toList();
        return compile(compiled, d.location(), Value.TRUE, Value.FALSE, d.isEager());
    }

    /**
     * Compiles an n-ary boolean with lazy semantics. Used by
     * {@link io.sapl.compiler.policy.policybody.PolicyBodyCompiler} for implicit
     * AND of policy body conditions.
     */
    public static CompiledExpression compile(List<CompiledExpression> operands, SourceLocation location,
            Value shortCircuitValue, Value identityValue) {
        return compile(operands, location, shortCircuitValue, identityValue, false);
    }

    private static CompiledExpression compile(List<CompiledExpression> operands, SourceLocation location,
            Value shortCircuitValue, Value identityValue, boolean isEager) {

        // 1. Fold values at compile time, split remainder into pure and stream buckets
        val pures   = new ArrayList<PureOperator>();
        val streams = new ArrayList<StreamOperator>();
        for (var op : operands) {
            switch (op) {
            case ErrorValue e                                    -> {
                return e;
            }
            case BooleanValue b when shortCircuitValue.equals(b) -> {
                return shortCircuitValue;
            }
            case BooleanValue ignored                            -> { /* identity - fold away */ }
            case Value v                                         -> {
                return Value.errorAt(location, ERROR_TYPE_MISMATCH, v.getClass().getSimpleName());
            }
            case PureOperator pure                               -> pures.add(pure);
            case StreamOperator stream                           -> streams.add(stream);
            }
        }

        if (pures.isEmpty() && streams.isEmpty()) {
            return identityValue;
        }

        // 3. Pure-only: simple loop at runtime
        if (streams.isEmpty()) {
            val dependsOnSubscription = pures.stream().anyMatch(PureOperator::isDependingOnSubscription);
            return new NaryBooleanPure(pures, shortCircuitValue, identityValue, location, dependsOnSubscription);
        }

        // 4. Streams present: build deferContextual with pure gate + stream composition
        val streamFlux = isEager ? buildEagerStreamChain(streams, shortCircuitValue, identityValue, location)
                : buildLazyStreamChain(streams, shortCircuitValue, identityValue, location);

        if (pures.isEmpty()) {
            return new NaryBooleanStream(streamFlux);
        }

        return new NaryBooleanStream(Flux.deferContextual(ctxView -> {
            val ctx = ctxView.get(EvaluationContext.class);
            for (var pure : pures) {
                val v = pure.evaluate(ctx);
                if (v instanceof ErrorValue) {
                    return Flux.just(new TracedValue(v, List.of()));
                }
                if (!(v instanceof BooleanValue)) {
                    return Flux.just(new TracedValue(
                            Value.errorAt(location, ERROR_TYPE_MISMATCH, v.getClass().getSimpleName()), List.of()));
                }
                if (shortCircuitValue.equals(v)) {
                    return Flux.just(new TracedValue(shortCircuitValue, List.of()));
                }
            }
            return streamFlux;
        }));
    }

    /**
     * Builds a lazy (short-circuit) stream chain using switchMap. Iterates
     * backwards, building nested switchMap structure. Each stream conditionally
     * subscribes to the next based on its emitted value.
     */
    private static Flux<TracedValue> buildLazyStreamChain(List<StreamOperator> streams, Value shortCircuitValue,
            Value identityValue, SourceLocation location) {

        Flux<TracedValue> chain = Flux.just(new TracedValue(identityValue, List.of()));

        for (int i = streams.size() - 1; i >= 0; i--) {
            val stream = streams.get(i);
            val next   = chain;

            chain = stream.stream().switchMap(tv -> {
                val v     = tv.value();
                val attrs = tv.contributingAttributes();

                if (v instanceof ErrorValue) {
                    return Flux.just(tv);
                }
                if (!(v instanceof BooleanValue)) {
                    return Flux.just(new TracedValue(
                            Value.errorAt(location, ERROR_TYPE_MISMATCH, v.getClass().getSimpleName()), attrs));
                }
                if (shortCircuitValue.equals(v)) {
                    return Flux.just(tv);
                }
                return next.map(nextTv -> mergeAttributes(attrs, nextTv));
            });
        }

        return chain;
    }

    /**
     * Builds an eager stream chain using combineLatest. All streams are subscribed
     * simultaneously. On each emission from any stream, the combiner evaluates all
     * latest values together.
     */
    private static Flux<TracedValue> buildEagerStreamChain(List<StreamOperator> streams, Value shortCircuitValue,
            Value identityValue, SourceLocation location) {

        if (streams.size() == 1) {
            return streams.getFirst().stream().map(tv -> {
                val v = tv.value();
                if (v instanceof ErrorValue) {
                    return tv;
                }
                if (!(v instanceof BooleanValue)) {
                    return new TracedValue(Value.errorAt(location, ERROR_TYPE_MISMATCH, v.getClass().getSimpleName()),
                            tv.contributingAttributes());
                }
                return tv;
            });
        }

        val fluxes = streams.stream().map(StreamOperator::stream).toList();
        return Flux.combineLatest(fluxes, values -> {
            val allAttrs = new ArrayList<AttributeRecord>();
            for (Object obj : values) {
                val tv    = (TracedValue) obj;
                val v     = tv.value();
                val attrs = tv.contributingAttributes();
                allAttrs.addAll(attrs);

                if (v instanceof ErrorValue) {
                    return new TracedValue(v, allAttrs);
                }
                if (!(v instanceof BooleanValue)) {
                    return new TracedValue(Value.errorAt(location, ERROR_TYPE_MISMATCH, v.getClass().getSimpleName()),
                            allAttrs);
                }
                if (shortCircuitValue.equals(v)) {
                    return new TracedValue(shortCircuitValue, allAttrs);
                }
            }
            return new TracedValue(identityValue, allAttrs);
        });
    }

    public static TracedValue mergeAttributes(List<AttributeRecord> preceding, TracedValue subsequent) {
        val subsequentAttrs = subsequent.contributingAttributes();
        if (preceding.isEmpty()) {
            return subsequent;
        }
        if (subsequentAttrs.isEmpty()) {
            return new TracedValue(subsequent.value(), preceding);
        }
        val merged = new ArrayList<AttributeRecord>(preceding.size() + subsequentAttrs.size());
        merged.addAll(preceding);
        merged.addAll(subsequentAttrs);
        return new TracedValue(subsequent.value(), merged);
    }

    /**
     * N-ary boolean with only Pure operands. Simple loop at runtime,
     * short-circuits on shortCircuitValue.
     */
    record NaryBooleanPure(
            List<PureOperator> operands,
            Value shortCircuitValue,
            Value identityValue,
            SourceLocation location,
            boolean isDependingOnSubscription) implements PureOperator {

        @Override
        public Value evaluate(EvaluationContext ctx) {
            for (var op : operands) {
                val v = op.evaluate(ctx);
                if (v instanceof ErrorValue) {
                    return v;
                }
                if (!(v instanceof BooleanValue)) {
                    return Value.errorAt(location, ERROR_TYPE_MISMATCH, v.getClass().getSimpleName());
                }
                if (shortCircuitValue.equals(v)) {
                    return shortCircuitValue;
                }
            }
            return identityValue;
        }

        // Eager and lazy AND/OR produce the same hash because on the pure
        // stratum they evaluate identically. The isEager flag only affects
        // the streaming stratum.
        @Override
        public long semanticHash() {
            val childHashes = operands.stream().mapToLong(PureOperator::semanticHash).toArray();
            return SemanticHashing.commutative(shortCircuitValue.hashCode(), childHashes);
        }
    }

    /**
     * N-ary boolean with Stream operands. Flux chain is pre-built at compile
     * time - no runtime recursion.
     */
    record NaryBooleanStream(Flux<TracedValue> chain) implements StreamOperator {

        @Override
        public Flux<TracedValue> stream() {
            return chain;
        }
    }

}
