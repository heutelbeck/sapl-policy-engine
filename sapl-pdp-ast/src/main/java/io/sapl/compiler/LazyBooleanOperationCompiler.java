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

import io.sapl.api.model.*;
import io.sapl.api.pdp.internal.AttributeRecord;
import io.sapl.ast.BinaryOperator;
import io.sapl.ast.BinaryOperatorType;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;

/**
 * Compiler for lazy (short-circuit) boolean operators: AND ({@code &&}) and OR
 * ({@code ||}).
 * <p>
 * Unlike eager operators, lazy operators may skip evaluation of the right
 * operand:
 * <ul>
 * <li>AND: if left is false, return false without evaluating right</li>
 * <li>OR: if left is true, return true without evaluating right</li>
 * </ul>
 * <p>
 * For streams, this means using switchMap instead of combineLatest to avoid
 * subscribing to the right stream when short-circuit occurs.
 */
public class LazyBooleanOperationCompiler {

    public static final java.lang.String ERROR_TYPE_MISMATCH = "Expected BOOLEAN but got: %s.";

    public CompiledExpression compile(BinaryOperator expr, CompilationContext ctx) {
        val op       = expr.op();
        val location = expr.location();
        val isAnd    = op == BinaryOperatorType.AND;

        val left = ExpressionCompiler.compile(expr.left(), ctx);
        if (left instanceof ErrorValue) {
            return left;
        }

        // Check if left (Value) short-circuits - if so, return early WITHOUT compiling
        // right
        // This ensures that lower-stratum short-circuit swallows higher-stratum errors
        // (LTR within stratum)
        if (left instanceof Value leftValue) {
            if (leftValue instanceof BooleanValue(var b)) {
                boolean shortCircuits = isAnd ? !b : b; // false for AND, true for OR
                if (shortCircuits) {
                    return b ? Value.TRUE : Value.FALSE;
                }
                // Not short-circuiting, fall through to compile right
            } else {
                // Non-boolean Value - type error
                return Value.errorAt(location, ERROR_TYPE_MISMATCH, leftValue.getClass().getSimpleName());
            }
        }

        val right = ExpressionCompiler.compile(expr.right(), ctx);
        if (right instanceof ErrorValue) {
            return right;
        }

        return switch (left) {
        case Value lv          -> switch (right) {
                           case Value rv              -> compileValueValue(lv, rv, isAnd, location);
                           case PureOperator rp       -> compileValuePure(lv, rp, isAnd, location);
                           case StreamOperator rs     -> compileValueStream(lv, rs, isAnd, location);
                           };
        case PureOperator lp   -> switch (right) {
                           case Value rv              -> compileValuePure(rv, lp, isAnd, location);
                           case PureOperator rp       -> compilePurePure(lp, rp, isAnd, location);
                           case StreamOperator rs     -> compilePureStream(lp, rs, isAnd, location);
                           };
        case StreamOperator ls -> switch (right) {
                           case Value rv              -> compileValueStream(rv, ls, isAnd, location);
                           case PureOperator rp       -> compilePureStream(rp, ls, isAnd, location);
                           case StreamOperator rs     -> compileStreamStream(ls, rs, isAnd, location);
                           };
        };
    }

    private CompiledExpression compileValueValue(Value v1, Value v2, boolean isAnd, SourceLocation location) {
        if (v1 instanceof BooleanValue(var b1) && v2 instanceof BooleanValue(var b2)) {
            return (isAnd ? (b1 && b2) : (b1 || b2)) ? Value.TRUE : Value.FALSE;
        }
        if (v1 instanceof ErrorValue) {
            return v1;
        }
        if (v2 instanceof ErrorValue) {
            return v2;
        }
        return Value.errorAt(location, ERROR_TYPE_MISMATCH,
                !(v1 instanceof BooleanValue) ? v1.getClass().getSimpleName() : v2.getClass().getSimpleName());
    }

    private CompiledExpression compileValuePure(Value v, PureOperator p, boolean isAnd, SourceLocation location) {
        if (v instanceof BooleanValue(var b)) {
            if ((isAnd && !b) || (!isAnd && b)) {
                return b ? Value.TRUE : Value.FALSE;
            }
            return new LazyValuePure(p, location, p.isDependingOnSubscription());
        }
        if (v instanceof ErrorValue) {
            return v;
        }
        return Value.errorAt(location, ERROR_TYPE_MISMATCH, v.getClass().getSimpleName());
    }

    private CompiledExpression compileValueStream(Value v, StreamOperator s, boolean isAnd, SourceLocation location) {
        if (v instanceof BooleanValue(var b)) {
            if ((isAnd && !b) || (!isAnd && b)) {
                return b ? Value.TRUE : Value.FALSE;
            }
            return new LazyValueStream(s, location);
        }
        if (v instanceof ErrorValue) {
            return v;
        }
        return Value.errorAt(location, ERROR_TYPE_MISMATCH, v.getClass().getSimpleName());
    }

    private CompiledExpression compilePurePure(PureOperator p1, PureOperator p2, boolean isAnd,
            SourceLocation location) {
        val depending = p1.isDependingOnSubscription() || p2.isDependingOnSubscription();
        return isAnd ? new LazyAndPurePure(p1, p2, location, depending)
                : new LazyOrPurePure(p1, p2, location, depending);
    }

    private CompiledExpression compilePureStream(PureOperator p, StreamOperator s, boolean isAnd,
            SourceLocation location) {
        return isAnd ? new LazyAndPureStream(p, s, location) : new LazyOrPureStream(p, s, location);
    }

    private CompiledExpression compileStreamStream(StreamOperator s1, StreamOperator s2, boolean isAnd,
            SourceLocation location) {
        return isAnd ? new LazyAndStreamStream(s1, s2, location) : new LazyOrStreamStream(s1, s2, location);
    }

    private static Value asBoolean(Value v, SourceLocation location) {
        if (v instanceof BooleanValue) {
            return v;
        }
        if (v instanceof ErrorValue) {
            return v;
        }
        return Value.errorAt(location, ERROR_TYPE_MISMATCH, v.getClass().getSimpleName());
    }

    public record LazyValuePure(PureOperator p, SourceLocation location, boolean isDependingOnSubscription)
            implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return asBoolean(p.evaluate(ctx), location);
        }
    }

    public record LazyAndPurePure(
            PureOperator p1,
            PureOperator p2,
            SourceLocation location,
            boolean isDependingOnSubscription) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            if (p1.evaluate(ctx) instanceof BooleanValue(var b1)) {
                if (!b1) {
                    return Value.FALSE;
                }
                return asBoolean(p2.evaluate(ctx), location);
            }
            return Value.errorAt(location, ERROR_TYPE_MISMATCH, p1.getClass().getSimpleName());
        }
    }

    public record LazyOrPurePure(
            PureOperator p1,
            PureOperator p2,
            SourceLocation location,
            boolean isDependingOnSubscription) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            if (p1.evaluate(ctx) instanceof BooleanValue(var b1)) {
                if (b1) {
                    return Value.TRUE;
                }
                return asBoolean(p2.evaluate(ctx), location);
            }
            return Value.errorAt(location, ERROR_TYPE_MISMATCH, p1.getClass().getSimpleName());
        }
    }

    public record LazyValueStream(StreamOperator s, SourceLocation location) implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return s.stream().map(tv -> new TracedValue(asBoolean(tv.value(), location), tv.contributingAttributes()));
        }
    }

    public record LazyAndPureStream(PureOperator p, StreamOperator s, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.deferContextual(ctx -> {
                val pv = p.evaluate(ctx.get(EvaluationContext.class));
                if (pv instanceof BooleanValue(var b)) {
                    if (!b) {
                        return Flux.just(new TracedValue(Value.FALSE, java.util.List.of()));
                    }
                    return s.stream()
                            .map(tv -> new TracedValue(asBoolean(tv.value(), location), tv.contributingAttributes()));
                }
                return Flux.just(
                        new TracedValue(Value.errorAt(location, ERROR_TYPE_MISMATCH, pv.getClass().getSimpleName()),
                                java.util.List.of()));
            });
        }
    }

    public record LazyOrPureStream(PureOperator p, StreamOperator s, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.deferContextual(ctx -> {
                val pv = p.evaluate(ctx.get(EvaluationContext.class));
                if (pv instanceof BooleanValue(var b)) {
                    if (b) {
                        return Flux.just(new TracedValue(Value.TRUE, java.util.List.of()));
                    }
                    return s.stream()
                            .map(tv -> new TracedValue(asBoolean(tv.value(), location), tv.contributingAttributes()));
                }
                return Flux.just(
                        new TracedValue(Value.errorAt(location, ERROR_TYPE_MISMATCH, pv.getClass().getSimpleName()),
                                java.util.List.of()));
            });
        }
    }

    public record LazyAndStreamStream(StreamOperator s1, StreamOperator s2, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return s1.stream().switchMap(tv1 -> {
                if (tv1.value() instanceof BooleanValue(var b1)) {
                    if (!b1) {
                        return Flux.just(new TracedValue(Value.FALSE, tv1.contributingAttributes()));
                    }
                    return s2.stream().map(tv2 -> {
                        val combined = new ArrayList<AttributeRecord>(tv1.contributingAttributes());
                        combined.addAll(tv2.contributingAttributes());
                        return new TracedValue(asBoolean(tv2.value(), location), combined);
                    });
                }
                return Flux.just(new TracedValue(
                        Value.errorAt(location, ERROR_TYPE_MISMATCH, tv1.value().getClass().getSimpleName()),
                        tv1.contributingAttributes()));
            });
        }
    }

    public record LazyOrStreamStream(StreamOperator s1, StreamOperator s2, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return s1.stream().switchMap(tv1 -> {
                if (tv1.value() instanceof BooleanValue(var b1)) {
                    if (b1) {
                        return Flux.just(new TracedValue(Value.TRUE, tv1.contributingAttributes()));
                    }
                    return s2.stream().map(tv2 -> {
                        val combined = new ArrayList<AttributeRecord>(tv1.contributingAttributes());
                        combined.addAll(tv2.contributingAttributes());
                        return new TracedValue(asBoolean(tv2.value(), location), combined);
                    });
                }
                return Flux.just(new TracedValue(
                        Value.errorAt(location, ERROR_TYPE_MISMATCH, tv1.value().getClass().getSimpleName()),
                        tv1.contributingAttributes()));
            });
        }
    }

}
