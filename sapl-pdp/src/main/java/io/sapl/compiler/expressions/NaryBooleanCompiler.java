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

import io.sapl.api.model.*;
import io.sapl.ast.Conjunction;
import io.sapl.ast.Disjunction;
import io.sapl.compiler.index.SemanticHashing;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.sapl.api.model.StreamOperator.evalChild;

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
 * <li>If any Streams: return one of two stream variants. The lazy variant
 * walks operands in order, short-circuiting on the first short-circuit value
 * and skipping remaining stream subtrees. The eager variant walks every
 * stream operand against the snapshot to accumulate all dependencies, then
 * scans the resolved values for the first non-identity result.</li>
 * </ol>
 * <p>
 * Key insight: AND/OR are commutative, so operands can be reordered by cost
 * stratum without changing semantics.
 */
@UtilityClass
public class NaryBooleanCompiler {

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
     * {@link io.sapl.compiler.policy.PolicyCompiler} for implicit AND of
     * policy body conditions.
     */
    public static CompiledExpression compile(List<CompiledExpression> operands, SourceLocation location,
            Value shortCircuitValue, Value identityValue) {
        return compile(operands, location, shortCircuitValue, identityValue, false);
    }

    private static CompiledExpression compile(List<CompiledExpression> operands, SourceLocation location,
            Value shortCircuitValue, Value identityValue, boolean isEager) {

        // Fold values at compile time, split remainder into pure and stream buckets.
        // Kleene strong 3-valued logic: only the dominator (shortCircuitValue) folds
        // the whole node. A constant error or non-boolean does not fold. It is kept
        // as a pending error so a later pure or stream dominator can still win.
        val   pures        = new ArrayList<PureOperator>();
        val   streams      = new ArrayList<StreamOperator>();
        Value pendingError = null;
        for (var op : operands) {
            switch (op) {
            case BooleanValue b when shortCircuitValue.equals(b) -> {
                return shortCircuitValue;
            }
            case ErrorValue e                                    -> {
                if (pendingError == null) {
                    pendingError = e;
                }
            }
            case BooleanValue ignored                            -> { /* identity - fold away */ }
            case Value v                                         -> {
                if (pendingError == null) {
                    pendingError = typeError(v, location);
                }
            }
            case PureOperator pure                               -> pures.add(pure);
            case StreamOperator stream                           -> streams.add(stream);
            }
        }

        if (pures.isEmpty() && streams.isEmpty()) {
            return pendingError != null ? pendingError : identityValue;
        }

        if (streams.isEmpty()) {
            val dependsOnSubscription = pures.stream().anyMatch(PureOperator::isDependingOnSubscription);
            val isRelative            = pures.stream().anyMatch(PureOperator::isRelativeExpression);
            return new NaryBooleanPure(pures, shortCircuitValue, identityValue, location, dependsOnSubscription,
                    isRelative, pendingError);
        }

        if (isEager) {
            return new NaryBooleanStreamEager(pures, streams, shortCircuitValue, identityValue, location, pendingError);
        }
        return new NaryBooleanStreamLazy(pures, streams, shortCircuitValue, identityValue, location, pendingError);
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
            boolean isDependingOnSubscription,
            boolean isRelativeExpression,
            @Nullable Value pendingError) implements PureOperator {

        @Override
        public Value evaluate(EvaluationContext ctx) {
            Value firstError = pendingError;
            for (var op : operands) {
                val v = op.evaluate(ctx);
                if (shortCircuitValue.equals(v)) {
                    return shortCircuitValue;
                }
                val error = errorOf(v, location);
                if (error != null && firstError == null) {
                    firstError = error;
                }
            }
            return firstError != null ? firstError : identityValue;
        }

        // Eager and lazy AND/OR produce the same hash because on the pure
        // stratum they evaluate identically. The isEager flag only affects
        // the streaming stratum.
        @Override
        public long semanticHash() {
            val childHashes = operands.stream().mapToLong(PureOperator::semanticHash).toArray();
            val base        = SemanticHashing.commutative(SemanticHashing.valueHash(shortCircuitValue), childHashes);
            if (pendingError == null) {
                return base;
            }
            return SemanticHashing.commutative(base, SemanticHashing.valueHash(pendingError));
        }

        @Override
        public BooleanExpression booleanExpression() {
            val children = new ArrayList<BooleanExpression>(
                    operands.stream().map(PureOperator::booleanExpression).toList());
            if (pendingError != null) {
                children.add(new ConstantErrorPredicate(pendingError, location).booleanExpression());
            }
            if (Value.FALSE.equals(shortCircuitValue)) {
                return new BooleanExpression.And(children);
            }
            return new BooleanExpression.Or(children);
        }
    }

    /**
     * N-ary boolean with Stream operands, lazy ({@code &&}, {@code ||})
     * variant. {@code evaluate(ctx)} applies stratified short-circuit: pures
     * first (free), then streams in order via {@link StreamOperator#evalChild}.
     * Subsequent streams are skipped (and their dependencies are not added)
     * once a short-circuit value is seen.
     */
    record NaryBooleanStreamLazy(
            List<PureOperator> pures,
            List<StreamOperator> streams,
            Value shortCircuitValue,
            Value identityValue,
            SourceLocation location,
            @Nullable Value pendingError) implements StreamOperator {

        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            val deps       = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(streams.size());
            val pureResult = evaluatePures(pures, shortCircuitValue, location, ctx, deps, pendingError);
            if (pureResult.shortCircuit() != null) {
                return pureResult.shortCircuit();
            }
            Value firstError = pureResult.firstError();
            for (val s : streams) {
                val v = evalChild(s, ctx, deps);
                if (v == null) {
                    return new ExpressionResult(null, deps);
                }
                if (shortCircuitValue.equals(v)) {
                    return new ExpressionResult(shortCircuitValue, deps);
                }
                val error = errorOf(v, location);
                if (error != null && firstError == null) {
                    firstError = error;
                }
            }
            return new ExpressionResult(firstError != null ? firstError : identityValue, deps);
        }
    }

    /**
     * N-ary boolean with Stream operands, eager ({@code &}, {@code |})
     * variant. {@code evaluate(ctx)} applies stratified short-circuit only on
     * the pure stratum (pures first, free); on the stream stratum every child
     * is walked via {@link StreamOperator#evalChild} so all dependencies are
     * accumulated, then the resolved values are scanned in operand order and
     * the first non-identity wins.
     */
    record NaryBooleanStreamEager(
            List<PureOperator> pures,
            List<StreamOperator> streams,
            Value shortCircuitValue,
            Value identityValue,
            SourceLocation location,
            @Nullable Value pendingError) implements StreamOperator {

        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            val deps       = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(streams.size());
            val pureResult = evaluatePures(pures, shortCircuitValue, location, ctx, deps, pendingError);
            if (pureResult.shortCircuit() != null) {
                return pureResult.shortCircuit();
            }
            Value   firstError = pureResult.firstError();
            val     resolved   = new Value[streams.size()];
            boolean seenNull   = false;
            for (int i = 0; i < streams.size(); i++) {
                resolved[i] = evalChild(streams.get(i), ctx, deps);
                if (resolved[i] == null) {
                    seenNull = true;
                }
            }
            for (val v : resolved) {
                if (v == null) {
                    continue;
                }
                if (shortCircuitValue.equals(v)) {
                    return new ExpressionResult(shortCircuitValue, deps);
                }
                val error = errorOf(v, location);
                if (error != null && firstError == null) {
                    firstError = error;
                }
            }
            if (seenNull) {
                return new ExpressionResult(null, deps);
            }
            return new ExpressionResult(firstError != null ? firstError : identityValue, deps);
        }
    }

    /**
     * Walks the pure stratum in order under Kleene semantics. Returns a
     * short-circuit terminal only when the dominating {@code shortCircuitValue}
     * is seen. Otherwise it returns no terminal and the first error encountered
     * (or the pending error carried from folding), so the caller proceeds to
     * the stream stratum. A pure error must not skip the streams, because a
     * stream may still yield the dominator.
     */
    private static PureStratumResult evaluatePures(List<PureOperator> pures, Value shortCircuitValue,
            SourceLocation location, EvaluationContext ctx, Map<SubscriptionKey, List<Occurrence>> deps,
            @Nullable Value pendingError) {
        Value firstError = pendingError;
        for (val p : pures) {
            val v = p.evaluate(ctx);
            if (shortCircuitValue.equals(v)) {
                return new PureStratumResult(new ExpressionResult(shortCircuitValue, deps), null);
            }
            val error = errorOf(v, location);
            if (error != null && firstError == null) {
                firstError = error;
            }
        }
        return new PureStratumResult(null, firstError);
    }

    private record PureStratumResult(@Nullable ExpressionResult shortCircuit, @Nullable Value firstError) {}

    /**
     * Classifies a single resolved value: returns the error it represents (an
     * {@link ErrorValue} as-is, a non-boolean as a type-mismatch error), or
     * {@code null} when it is a boolean that is not the dominator. The
     * dominator is handled by the caller before this is reached.
     */
    private static @Nullable Value errorOf(Value v, SourceLocation location) {
        if (v instanceof ErrorValue) {
            return v;
        }
        if (!(v instanceof BooleanValue)) {
            return typeError(v, location);
        }
        return null;
    }

    private static Value typeError(Value v, SourceLocation location) {
        return Value.errorAt(location, StratifiedBooleanOperationCompiler.ERROR_TYPE_MISMATCH,
                v.getClass().getSimpleName());
    }

}
