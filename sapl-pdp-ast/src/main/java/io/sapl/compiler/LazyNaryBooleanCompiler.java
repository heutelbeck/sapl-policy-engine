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
package io.sapl.compiler;

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.TracedValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.internal.AttributeRecord;
import io.sapl.ast.Conjunction;
import io.sapl.ast.Disjunction;
import io.sapl.ast.Expression;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiles N-ary lazy boolean operators: Conjunction ({@code &&}) and Disjunction ({@code ||}).
 * <p>
 * These operators short-circuit:
 * <ul>
 * <li>Conjunction: short-circuit on first {@code false}</li>
 * <li>Disjunction: short-circuit on first {@code true}</li>
 * </ul>
 * <p>
 * Cost-stratified evaluation (same pattern as {@link NaryOperatorCompiler}):
 * <ol>
 * <li>Compile all operands, categorize by strata (Value, Pure, Stream)</li>
 * <li>Fold all Values at compile time - if any short-circuits, return
 * immediately</li>
 * <li>If Values all pass (identity values), they're folded away</li>
 * <li>Pures evaluated before Streams at runtime, with short-circuit</li>
 * <li>Streams chained with switchMap for lazy subscription</li>
 * </ol>
 * <p>
 * Key insight: AND/OR are still associative, so we CAN reorder by strata.
 * {@code a && stream && false} is equivalent to {@code false && a && stream} =
 * {@code false}.
 */
@UtilityClass
public class LazyNaryBooleanCompiler {

    private static final String ERROR_TYPE_MISMATCH = "Expected BOOLEAN but got: %s.";

    /**
     * Compiles a Conjunction ({@code a && b && c && ...}).
     * Short-circuits on first {@code false}.
     */
    public CompiledExpression compileConjunction(Conjunction c, CompilationContext ctx) {
        return compile(c.operands(), ctx, c.location(), true);
    }

    /**
     * Compiles a Disjunction ({@code a || b || c || ...}).
     * Short-circuits on first {@code true}.
     */
    public CompiledExpression compileDisjunction(Disjunction d, CompilationContext ctx) {
        return compile(d.operands(), ctx, d.location(), false);
    }

    private CompiledExpression compile(List<Expression> operands, CompilationContext ctx, SourceLocation location,
            boolean isConjunction) {

        // The value that triggers short-circuit: false for AND, true for OR
        val shortCircuitValue = !isConjunction;

        // Compile all operands and categorize by strata
        var values  = new ArrayList<Value>();
        var pures   = new ArrayList<PureOperator>();
        var streams = new ArrayList<StreamOperator>();

        for (var operand : operands) {
            var compiled = ExpressionCompiler.compile(operand, ctx);
            switch (compiled) {
            case Value v          -> values.add(v);
            case PureOperator p   -> pures.add(p);
            case StreamOperator s -> streams.add(s);
            }
        }

        // Fold values at compile time (cheapest stratum)
        Value valueResult = foldValues(values, shortCircuitValue, location);
        if (valueResult instanceof ErrorValue) {
            return valueResult; // Compile-time error
        }
        if (valueResult instanceof BooleanValue(var b) && b == shortCircuitValue) {
            return valueResult; // Compile-time short-circuit!
        }

        // Determine return type based on remaining strata
        if (pures.isEmpty() && streams.isEmpty()) {
            // All values - return folded result (all were identity values)
            return isConjunction ? Value.TRUE : Value.FALSE;
        }

        if (streams.isEmpty()) {
            // Values + Pures only: return PureOperator
            boolean dependsOnSubscription = pures.stream().anyMatch(PureOperator::isDependingOnSubscription);
            return isConjunction ? new NaryConjunctionPure(pures, location, dependsOnSubscription)
                    : new NaryDisjunctionPure(pures, location, dependsOnSubscription);
        }

        // Has streams: return StreamOperator
        return isConjunction ? new NaryConjunctionStream(pures, streams, location)
                : new NaryDisjunctionStream(pures, streams, location);
    }

    /**
     * Fold all Value operands at compile time.
     * <p>
     * For conjunction: any false returns FALSE (short-circuit), any error returns
     * error.
     * For disjunction: any true returns TRUE (short-circuit), any error returns
     * error.
     * Otherwise: all values are identity elements, return null (fold away)
     *
     * @param values the Value operands
     * @param shortCircuitValue the value that triggers short-circuit (false for
     * AND, true for OR)
     * @param location source location for errors
     * @return short-circuit value, error, or null if all values are identity
     */
    private Value foldValues(List<Value> values, boolean shortCircuitValue, SourceLocation location) {
        for (var v : values) {
            if (v instanceof ErrorValue) {
                return v;
            }
            if (v instanceof BooleanValue(var b)) {
                if (b == shortCircuitValue) {
                    return b ? Value.TRUE : Value.FALSE; // Short-circuit
                }
                // Identity value - continue checking
            } else {
                return Value.errorAt(location, ERROR_TYPE_MISMATCH, v.getClass().getSimpleName());
            }
        }
        return null; // All values were identity elements (folded away)
    }

    /**
     * N-ary Conjunction with only Pure operands.
     * Evaluates left-to-right, short-circuiting on first false.
     */
    record NaryConjunctionPure(List<PureOperator> operands, SourceLocation location, boolean isDependingOnSubscription)
            implements PureOperator {

        @Override
        public Value evaluate(EvaluationContext ctx) {
            for (var operand : operands) {
                var v = operand.evaluate(ctx);
                if (v instanceof ErrorValue) {
                    return v;
                }
                if (v instanceof BooleanValue(var b)) {
                    if (!b) {
                        return Value.FALSE; // Short-circuit
                    }
                    // true - continue to next operand
                } else {
                    return Value.errorAt(location, ERROR_TYPE_MISMATCH, v.getClass().getSimpleName());
                }
            }
            return Value.TRUE; // All operands were true
        }
    }

    /**
     * N-ary Disjunction with only Pure operands.
     * Evaluates left-to-right, short-circuiting on first true.
     */
    record NaryDisjunctionPure(List<PureOperator> operands, SourceLocation location, boolean isDependingOnSubscription)
            implements PureOperator {

        @Override
        public Value evaluate(EvaluationContext ctx) {
            for (var operand : operands) {
                var v = operand.evaluate(ctx);
                if (v instanceof ErrorValue) {
                    return v;
                }
                if (v instanceof BooleanValue(var b)) {
                    if (b) {
                        return Value.TRUE; // Short-circuit
                    }
                    // false - continue to next operand
                } else {
                    return Value.errorAt(location, ERROR_TYPE_MISMATCH, v.getClass().getSimpleName());
                }
            }
            return Value.FALSE; // All operands were false
        }
    }

    /**
     * N-ary Conjunction with Stream operands.
     * <p>
     * Strategy:
     * <ol>
     * <li>Evaluate all Pures first (before any stream subscription)</li>
     * <li>If any Pure is false, return false without subscribing to streams</li>
     * <li>If any Pure is error/non-boolean, return error</li>
     * <li>Chain streams with switchMap for lazy subscription</li>
     * </ol>
     */
    record NaryConjunctionStream(List<PureOperator> pures, List<StreamOperator> streams, SourceLocation location)
            implements StreamOperator {

        @Override
        public Flux<TracedValue> stream() {
            return Flux.deferContextual(ctx -> {
                val evalCtx = ctx.get(EvaluationContext.class);

                // Evaluate all pures first
                for (var pure : pures) {
                    var v = pure.evaluate(evalCtx);
                    if (v instanceof ErrorValue) {
                        return Flux.just(new TracedValue(v, List.of()));
                    }
                    if (v instanceof BooleanValue(var b)) {
                        if (!b) {
                            return Flux.just(new TracedValue(Value.FALSE, List.of()));
                        }
                    } else {
                        return Flux.just(new TracedValue(
                                Value.errorAt(location, ERROR_TYPE_MISMATCH, v.getClass().getSimpleName()), List.of()));
                    }
                }

                // All pures were true, now chain streams with switchMap
                return chainConjunctionStreams(streams, 0, new ArrayList<>(), location);
            });
        }
    }

    /**
     * N-ary Disjunction with Stream operands.
     * <p>
     * Strategy:
     * <ol>
     * <li>Evaluate all Pures first (before any stream subscription)</li>
     * <li>If any Pure is true, return true without subscribing to streams</li>
     * <li>If any Pure is error/non-boolean, return error</li>
     * <li>Chain streams with switchMap for lazy subscription</li>
     * </ol>
     */
    record NaryDisjunctionStream(List<PureOperator> pures, List<StreamOperator> streams, SourceLocation location)
            implements StreamOperator {

        @Override
        public Flux<TracedValue> stream() {
            return Flux.deferContextual(ctx -> {
                val evalCtx = ctx.get(EvaluationContext.class);

                // Evaluate all pures first
                for (var pure : pures) {
                    var v = pure.evaluate(evalCtx);
                    if (v instanceof ErrorValue) {
                        return Flux.just(new TracedValue(v, List.of()));
                    }
                    if (v instanceof BooleanValue(var b)) {
                        if (b) {
                            return Flux.just(new TracedValue(Value.TRUE, List.of()));
                        }
                    } else {
                        return Flux.just(new TracedValue(
                                Value.errorAt(location, ERROR_TYPE_MISMATCH, v.getClass().getSimpleName()), List.of()));
                    }
                }

                // All pures were false, now chain streams with switchMap
                return chainDisjunctionStreams(streams, 0, new ArrayList<>(), location);
            });
        }
    }

    /**
     * Recursively chains conjunction streams using switchMap.
     * Each stream is only subscribed to if all previous streams emitted true.
     */
    private static Flux<TracedValue> chainConjunctionStreams(List<StreamOperator> streams, int index,
            List<AttributeRecord> accumulatedAttributes, SourceLocation location) {

        if (index >= streams.size()) {
            // All streams emitted true
            return Flux.just(new TracedValue(Value.TRUE, accumulatedAttributes));
        }

        return streams.get(index).stream().switchMap(tv -> {
            var combinedAttributes = new ArrayList<>(accumulatedAttributes);
            combinedAttributes.addAll(tv.contributingAttributes());

            var v = tv.value();
            if (v instanceof ErrorValue) {
                return Flux.just(new TracedValue(v, combinedAttributes));
            }
            if (v instanceof BooleanValue(var b)) {
                if (!b) {
                    return Flux.just(new TracedValue(Value.FALSE, combinedAttributes));
                }
                // true - continue to next stream
                return chainConjunctionStreams(streams, index + 1, combinedAttributes, location);
            }
            return Flux.just(new TracedValue(Value.errorAt(location, ERROR_TYPE_MISMATCH, v.getClass().getSimpleName()),
                    combinedAttributes));
        });
    }

    /**
     * Recursively chains disjunction streams using switchMap.
     * Each stream is only subscribed to if all previous streams emitted false.
     */
    private static Flux<TracedValue> chainDisjunctionStreams(List<StreamOperator> streams, int index,
            List<AttributeRecord> accumulatedAttributes, SourceLocation location) {

        if (index >= streams.size()) {
            // All streams emitted false
            return Flux.just(new TracedValue(Value.FALSE, accumulatedAttributes));
        }

        return streams.get(index).stream().switchMap(tv -> {
            var combinedAttributes = new ArrayList<>(accumulatedAttributes);
            combinedAttributes.addAll(tv.contributingAttributes());

            var v = tv.value();
            if (v instanceof ErrorValue) {
                return Flux.just(new TracedValue(v, combinedAttributes));
            }
            if (v instanceof BooleanValue(var b)) {
                if (b) {
                    return Flux.just(new TracedValue(Value.TRUE, combinedAttributes));
                }
                // false - continue to next stream
                return chainDisjunctionStreams(streams, index + 1, combinedAttributes, location);
            }
            return Flux.just(new TracedValue(Value.errorAt(location, ERROR_TYPE_MISMATCH, v.getClass().getSimpleName()),
                    combinedAttributes));
        });
    }

}
