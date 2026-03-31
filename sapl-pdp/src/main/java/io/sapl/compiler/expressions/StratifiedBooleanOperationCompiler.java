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

import io.sapl.api.model.BooleanExpression;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.TracedValue;
import io.sapl.api.model.Value;
import io.sapl.ast.BinaryOperator;
import io.sapl.ast.BinaryOperatorType;
import io.sapl.compiler.index.SemanticHashing;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiler for boolean operators AND ({@code &&}, {@code &}) and OR
 * ({@code ||}, {@code |}).
 * <p>
 * On the value and pure strata, lazy and eager variants behave identically.
 * On the streaming stratum they differ:
 * <ul>
 * <li>Lazy ({@code &&}, {@code ||}): uses switchMap for short-circuit
 * evaluation, avoiding unnecessary subscriptions.</li>
 * <li>Eager ({@code &}, {@code |}): uses combineLatest, keeping both
 * subscriptions active for lower latency.</li>
 * </ul>
 */
@UtilityClass
public class StratifiedBooleanOperationCompiler {

    public static final String ERROR_NOT_A_BOOLEAN_OPERATOR = "Not a boolean operator: %s.";
    public static final String ERROR_TYPE_MISMATCH          = "Expected BOOLEAN but got: %s.";

    public static CompiledExpression compile(BinaryOperator expr, CompilationContext ctx) {
        val op       = expr.op();
        val location = expr.location();

        val left  = ExpressionCompiler.compile(expr.left(), ctx);
        val right = ExpressionCompiler.compile(expr.right(), ctx);
        return compile(left, right, op, location);
    }

    public static CompiledExpression compile(CompiledExpression left, CompiledExpression right, BinaryOperatorType op,
            SourceLocation location) {
        val isAnd = op == BinaryOperatorType.LAZY_AND || op == BinaryOperatorType.EAGER_AND;
        if (left instanceof ErrorValue) {
            return left;
        }

        // Check if left (Value) short-circuits - if so, return early WITHOUT compiling
        // right
        // This ensures that lower-stratum short-circuit swallows higher-stratum errors
        // (LTR within stratum)
        if (left instanceof Value leftValue) {
            if (leftValue instanceof BooleanValue(var b)) {
                boolean shortCircuits = isAnd != b;
                if (shortCircuits) {
                    return b ? Value.TRUE : Value.FALSE;
                }
                // Not short-circuiting, fall through to compile right
            } else {
                // Non-boolean Value - type errors
                return Value.errorAt(location, ERROR_TYPE_MISMATCH, leftValue.getClass().getSimpleName());
            }
        }

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
                           case StreamOperator rs     -> compileStreamStream(ls, rs, op, location);
                           };
        };
    }

    private CompiledExpression compileValueValue(Value v1, Value v2, boolean isAnd, SourceLocation location) {
        if (v1 instanceof BooleanValue(var b1) && v2 instanceof BooleanValue(var b2)) {
            var result = isAnd ? (b1 && b2) : (b1 || b2);
            return result ? Value.TRUE : Value.FALSE;
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

    private CompiledExpression compileStreamStream(StreamOperator s1, StreamOperator s2, BinaryOperatorType op,
            SourceLocation location) {
        return switch (op) {
        case EAGER_AND -> new EagerAndStreamStream(s1, s2, location);
        case EAGER_OR  -> new EagerOrStreamStream(s1, s2, location);
        case LAZY_AND  -> new LazyAndStreamStream(s1, s2, location);
        case LAZY_OR   -> new LazyOrStreamStream(s1, s2, location);
        default        -> throw new SaplCompilerException(ERROR_NOT_A_BOOLEAN_OPERATOR.formatted(op));
        };
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

        private static final long KIND = SemanticHashing.kindHash(LazyValuePure.class);

        @Override
        public Value evaluate(EvaluationContext ctx) {
            return asBoolean(p.evaluate(ctx), location);
        }

        @Override
        public long semanticHash() {
            return SemanticHashing.ordered(KIND, p.semanticHash());
        }
    }

    public record LazyAndPurePure(
            PureOperator p1,
            PureOperator p2,
            SourceLocation location,
            boolean isDependingOnSubscription) implements PureOperator {

        private static final long KIND = SemanticHashing.kindHash(LazyAndPurePure.class);

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

        @Override
        public long semanticHash() {
            return SemanticHashing.commutative(KIND, p1.semanticHash(), p2.semanticHash());
        }

        @Override
        public BooleanExpression booleanExpression() {
            return new BooleanExpression.And(p1.booleanExpression(), p2.booleanExpression());
        }
    }

    public record LazyOrPurePure(
            PureOperator p1,
            PureOperator p2,
            SourceLocation location,
            boolean isDependingOnSubscription) implements PureOperator {

        private static final long KIND = SemanticHashing.kindHash(LazyOrPurePure.class);

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

        @Override
        public long semanticHash() {
            return SemanticHashing.commutative(KIND, p1.semanticHash(), p2.semanticHash());
        }

        @Override
        public BooleanExpression booleanExpression() {
            return new BooleanExpression.Or(p1.booleanExpression(), p2.booleanExpression());
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
                        return Flux.just(new TracedValue(Value.FALSE, List.of()));
                    }
                    return s.stream()
                            .map(tv -> new TracedValue(asBoolean(tv.value(), location), tv.contributingAttributes()));
                }
                return Flux.just(new TracedValue(
                        Value.errorAt(location, ERROR_TYPE_MISMATCH, pv.getClass().getSimpleName()), List.of()));
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
                        return Flux.just(new TracedValue(Value.TRUE, List.of()));
                    }
                    return s.stream()
                            .map(tv -> new TracedValue(asBoolean(tv.value(), location), tv.contributingAttributes()));
                }
                return Flux.just(new TracedValue(
                        Value.errorAt(location, ERROR_TYPE_MISMATCH, pv.getClass().getSimpleName()), List.of()));
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
                        val combined = new ArrayList<>(tv1.contributingAttributes());
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
                        val combined = new ArrayList<>(tv1.contributingAttributes());
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

    public record EagerAndStreamStream(StreamOperator s1, StreamOperator s2, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.combineLatest(s1.stream(), s2.stream(), (tv1, tv2) -> {
                val combined = new ArrayList<>(tv1.contributingAttributes());
                combined.addAll(tv2.contributingAttributes());
                if (tv1.value() instanceof BooleanValue(var b1) && tv2.value() instanceof BooleanValue(var b2)) {
                    return new TracedValue(b1 && b2 ? Value.TRUE : Value.FALSE, combined);
                }
                if (tv1.value() instanceof ErrorValue) {
                    return new TracedValue(tv1.value(), combined);
                }
                if (tv2.value() instanceof ErrorValue) {
                    return new TracedValue(tv2.value(), combined);
                }
                val bad = !(tv1.value() instanceof BooleanValue) ? tv1.value() : tv2.value();
                return new TracedValue(Value.errorAt(location, ERROR_TYPE_MISMATCH, bad.getClass().getSimpleName()),
                        combined);
            });
        }
    }

    public record EagerOrStreamStream(StreamOperator s1, StreamOperator s2, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.combineLatest(s1.stream(), s2.stream(), (tv1, tv2) -> {
                val combined = new ArrayList<>(tv1.contributingAttributes());
                combined.addAll(tv2.contributingAttributes());
                if (tv1.value() instanceof BooleanValue(var b1) && tv2.value() instanceof BooleanValue(var b2)) {
                    return new TracedValue(b1 || b2 ? Value.TRUE : Value.FALSE, combined);
                }
                if (tv1.value() instanceof ErrorValue) {
                    return new TracedValue(tv1.value(), combined);
                }
                if (tv2.value() instanceof ErrorValue) {
                    return new TracedValue(tv2.value(), combined);
                }
                val bad = !(tv1.value() instanceof BooleanValue) ? tv1.value() : tv2.value();
                return new TracedValue(Value.errorAt(location, ERROR_TYPE_MISMATCH, bad.getClass().getSimpleName()),
                        combined);
            });
        }
    }

}
