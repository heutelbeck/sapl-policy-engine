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
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Compiles N-ary lazy boolean operators: Conjunction ({@code &&}) and
 * Disjunction ({@code ||}).
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
 * <li>Sort remaining operands by stratum: Pures first, then Streams</li>
 * <li>If all Pures: return {@link NaryLazyBooleanPure} (simple loop at
 * runtime)</li>
 * <li>If any Streams: pre-build Flux chain at compile time, return
 * {@link NaryLazyBooleanStream}</li>
 * </ol>
 * <p>
 * Key insight: AND/OR are commutative, so operands can be reordered by cost
 * stratum without changing semantics.
 */
@UtilityClass
public class LazyNaryBooleanCompiler {

    private static final String ERROR_TYPE_MISMATCH             = "Expected BOOLEAN but got: %s.";
    public static final String  ERROR_UNEXPECTED_OPERAND_TYPE_S = "Unexpected operand type: %s";

    /**
     * Compiles a Conjunction ({@code a && b && c && ...}). Short-circuits on first
     * {@code false}.
     */
    public CompiledExpression compileConjunction(Conjunction c, CompilationContext ctx) {
        val compiled = c.operands().stream().map(o -> ExpressionCompiler.compile(o, ctx)).toList();
        return compile(compiled, c.location(), Value.FALSE, Value.TRUE);
    }

    /**
     * Compiles a Disjunction ({@code a || b || c || ...}). Short-circuits on first
     * {@code true}.
     */
    public CompiledExpression compileDisjunction(Disjunction d, CompilationContext ctx) {
        val compiled = d.operands().stream().map(o -> ExpressionCompiler.compile(o, ctx)).toList();
        return compile(compiled, d.location(), Value.TRUE, Value.FALSE);
    }

    /**
     * Unified compilation for both conjunction and disjunction.
     *
     * @param operands compiled operands
     * @param location source location for error messages
     * @param shortCircuitValue value that triggers short-circuit (FALSE for AND,
     * TRUE for OR)
     * @param identityValue value returned when all operands pass (TRUE for AND,
     * FALSE for OR)
     * @return compiled expression of appropriate stratum
     */
    public CompiledExpression compile(List<CompiledExpression> operands, SourceLocation location,
            Value shortCircuitValue, Value identityValue) {

        // 1. Fold values at compile time
        val remaining = new ArrayList<CompiledExpression>();
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
            default                                              -> remaining.add(op);
            }
        }

        if (remaining.isEmpty()) {
            return identityValue;
        }

        // 2. Sort by stratum: pures first, then streams
        remaining.sort(Comparator.comparing(op -> op instanceof StreamOperator ? 1 : 0));

        // 3. Return appropriate type
        if (remaining.getLast() instanceof StreamOperator) {
            return new NaryLazyBooleanStream(buildChain(remaining, shortCircuitValue, identityValue, location));
        }

        val dependsOnSubscription = remaining.stream().anyMatch(op -> ((PureOperator) op).isDependingOnSubscription());
        return new NaryLazyBooleanPure(remaining, shortCircuitValue, identityValue, location, dependsOnSubscription);
    }

    /**
     * Pre-builds the Flux chain at compile time. Iterates backwards, building
     * nested switchMap/deferContextual structure. No runtime recursion.
     */
    private Flux<TracedValue> buildChain(List<CompiledExpression> operands, Value shortCircuitValue,
            Value identityValue, SourceLocation location) {

        Flux<TracedValue> chain = Flux.just(new TracedValue(identityValue, List.of()));

        for (int i = operands.size() - 1; i >= 0; i--) {
            val operand = operands.get(i);
            val next    = chain;

            chain = switch (operand) {
            case PureOperator pure     -> buildPureLink(pure, next, shortCircuitValue, location);
            case StreamOperator stream -> buildStreamLink(stream, next, shortCircuitValue, location);
            default                    -> throw new SaplCompilerException(
                    ERROR_UNEXPECTED_OPERAND_TYPE_S.formatted(operand.getClass().getSimpleName()), location);
            };
        }

        return chain;
    }

    private Flux<TracedValue> buildPureLink(PureOperator pure, Flux<TracedValue> next, Value shortCircuitValue,
            SourceLocation location) {
        return Flux.deferContextual(ctxView -> {
            val ctx = ctxView.get(EvaluationContext.class);
            val v   = pure.evaluate(ctx);

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
            return next;
        });
    }

    private Flux<TracedValue> buildStreamLink(StreamOperator stream, Flux<TracedValue> next, Value shortCircuitValue,
            SourceLocation location) {
        return stream.stream().switchMap(tv -> {
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

    public TracedValue mergeAttributes(List<AttributeRecord> preceding, TracedValue subsequent) {
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
     * N-ary lazy boolean with only Pure operands. Simple loop at runtime,
     * short-circuits on shortCircuitValue.
     */
    record NaryLazyBooleanPure(
            List<CompiledExpression> operands,
            Value shortCircuitValue,
            Value identityValue,
            SourceLocation location,
            boolean isDependingOnSubscription) implements PureOperator {

        @Override
        public Value evaluate(EvaluationContext ctx) {
            for (var op : operands) {
                val v = ((PureOperator) op).evaluate(ctx);
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
    }

    /**
     * N-ary lazy boolean with Stream operands. Flux chain is pre-built at compile
     * time - no runtime recursion.
     */
    record NaryLazyBooleanStream(Flux<TracedValue> chain) implements StreamOperator {

        @Override
        public Flux<TracedValue> stream() {
            return chain;
        }
    }

}
