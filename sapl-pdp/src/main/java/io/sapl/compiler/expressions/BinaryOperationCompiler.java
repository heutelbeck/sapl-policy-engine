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

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.ExpressionResult;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Subscription;
import io.sapl.api.model.TracedValue;
import io.sapl.api.model.Value;
import io.sapl.ast.ArrayExpression;
import io.sapl.ast.BinaryOperator;
import io.sapl.ast.BinaryOperatorType;
import io.sapl.compiler.index.SemanticHashing;
import io.sapl.compiler.operators.ArithmeticOperators;
import io.sapl.compiler.operators.BooleanOperators;
import io.sapl.compiler.operators.ComparisonOperators;
import io.sapl.compiler.operators.HasOperators;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static io.sapl.api.model.StreamOperator.evalChild;
import static io.sapl.ast.BinaryOperatorType.ADD;
import static io.sapl.ast.BinaryOperatorType.DIV;
import static io.sapl.ast.BinaryOperatorType.EQ;
import static io.sapl.ast.BinaryOperatorType.GE;
import static io.sapl.ast.BinaryOperatorType.GT;
import static io.sapl.ast.BinaryOperatorType.HAS_ALL;
import static io.sapl.ast.BinaryOperatorType.HAS_ANY;
import static io.sapl.ast.BinaryOperatorType.HAS_ONE;
import static io.sapl.ast.BinaryOperatorType.ALL_IN;
import static io.sapl.ast.BinaryOperatorType.ANY_IN;
import static io.sapl.ast.BinaryOperatorType.IN;
import static io.sapl.ast.BinaryOperatorType.LE;
import static io.sapl.ast.BinaryOperatorType.LT;
import static io.sapl.ast.BinaryOperatorType.MOD;
import static io.sapl.ast.BinaryOperatorType.MUL;
import static io.sapl.ast.BinaryOperatorType.NE;
import static io.sapl.ast.BinaryOperatorType.REGEX;
import static io.sapl.ast.BinaryOperatorType.SUB;
import static io.sapl.ast.BinaryOperatorType.SUBTEMPLATE;
import static io.sapl.ast.BinaryOperatorType.XOR;

@UtilityClass
public class BinaryOperationCompiler {

    private static final String ERROR_UNIMPLEMENTED_BINARY_OPERATOR = "Unimplemented binary operator: %s";

    static final Map<BinaryOperatorType, BinaryOperation> BINARY_OPERATIONS = Map.ofEntries(
            // Arithmetic
            Map.entry(ADD, ArithmeticOperators::add), Map.entry(SUB, ArithmeticOperators::subtract),
            Map.entry(MUL, ArithmeticOperators::multiply), Map.entry(DIV, ArithmeticOperators::divide),
            Map.entry(MOD, ArithmeticOperators::modulo),
            // Numeric comparison
            Map.entry(LT, ArithmeticOperators::lessThan), Map.entry(LE, ArithmeticOperators::lessThanOrEqual),
            Map.entry(GT, ArithmeticOperators::greaterThan), Map.entry(GE, ArithmeticOperators::greaterThanOrEqual),
            // Equality
            Map.entry(EQ, (a, b, location) -> ComparisonOperators.equals(a, b)),
            Map.entry(NE, (a, b, location) -> ComparisonOperators.notEquals(a, b)),
            // Membership
            Map.entry(IN, ComparisonOperators::isContainedIn), Map.entry(ANY_IN, ComparisonOperators::anyIn),
            Map.entry(ALL_IN, ComparisonOperators::allIn),
            // Key membership (has operator)
            Map.entry(HAS_ONE, HasOperators::hasOne), Map.entry(HAS_ANY, HasOperators::hasAny),
            Map.entry(HAS_ALL, HasOperators::hasAll),
            // XOR (the only non-short-circuit boolean operator)
            Map.entry(XOR, BooleanOperators::xor));

    public CompiledExpression compile(BinaryOperator binaryOperation, CompilationContext ctx) {
        val operatorType = binaryOperation.op();
        if (operatorType == REGEX) {
            return RegexCompiler.compile(binaryOperation, ctx);
        }

        if (operatorType == SUBTEMPLATE) {
            return SubtemplateCompiler.compile(binaryOperation, ctx);
        }

        if (ctx.unrollInOperator() && operatorType == IN) {
            val unrolled = InArrayUnrollingCompiler.tryCompile(binaryOperation, ctx);
            if (unrolled != null) {
                return unrolled;
            }
        }

        if ((operatorType == ANY_IN || operatorType == ALL_IN) && binaryOperation.left() instanceof ArrayExpression arr
                && arr.elements().size() == 1) {
            return compile(new BinaryOperator(IN, arr.elements().getFirst(), binaryOperation.right(),
                    binaryOperation.location()), ctx);
        }

        if (operatorType.isBooleanAndOr()) {
            return StratifiedBooleanOperationCompiler.compile(binaryOperation, ctx);
        }

        val op = BINARY_OPERATIONS.get(operatorType);
        if (op == null) {
            throw new SaplCompilerException(ERROR_UNIMPLEMENTED_BINARY_OPERATOR.formatted(operatorType),
                    binaryOperation);
        }
        val left = ExpressionCompiler.compile(binaryOperation.left(), ctx);
        if (left instanceof ErrorValue) {
            return left;
        }
        val right = ExpressionCompiler.compile(binaryOperation.right(), ctx);
        if (right instanceof ErrorValue) {
            return right;
        }
        val loc               = binaryOperation.location();
        val errorShortCircuit = ctx.errorShortCircuit();
        return switch (left) {
        case Value lv          -> switch (right) {
                           case Value rv              -> op.apply(lv, rv, loc);
                           case PureOperator rp       -> new BinaryValuePure(operatorType, op, lv, rp, loc,
                                   rp.isDependingOnSubscription(), rp.isRelativeExpression());
                           case StreamOperator rs     -> {
                               if (errorShortCircuit) {
                                   yield new BinaryValueStreamLazy(op, lv, rs, loc);
                               }
                               yield new BinaryValueStreamEager(op, lv, rs, loc);
                           }
                           };
        case PureOperator lp   -> switch (right) {
                           case Value rv              -> new BinaryPureValue(operatorType, op, lp, rv, loc,
                                   lp.isDependingOnSubscription(), lp.isRelativeExpression());
                           case PureOperator rp       -> new BinaryPurePure(operatorType, op, lp, rp, loc,
                                   lp.isDependingOnSubscription() || rp.isDependingOnSubscription(),
                                   lp.isRelativeExpression() || rp.isRelativeExpression());
                           case StreamOperator rs     -> {
                               if (errorShortCircuit) {
                                   yield new BinaryPureStreamLazy(op, lp, rs, loc);
                               }
                               yield new BinaryPureStreamEager(op, lp, rs, loc);
                           }
                           };
        case StreamOperator ls -> switch (right) {
                           case Value rv              -> new BinaryStreamValue(op, ls, rv, loc);
                           case PureOperator rp       -> new BinaryStreamPure(op, ls, rp, loc);
                           case StreamOperator rs     -> {
                               if (errorShortCircuit) {
                                   yield new BinaryStreamStreamLazy(op, ls, rs, loc);
                               }
                               yield new BinaryStreamStreamEager(op, ls, rs, loc);
                           }
                           };
        };
    }

    public record BinaryPurePure(
            BinaryOperatorType opType,
            BinaryOperation op,
            PureOperator lp,
            PureOperator rp,
            SourceLocation location,
            boolean isDependingOnSubscription,
            boolean isRelativeExpression) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            val lv = lp.evaluate(ctx);
            if (lv instanceof ErrorValue) {
                return lv;
            }
            val rv = rp.evaluate(ctx);
            if (rv instanceof ErrorValue) {
                return rv;
            }
            return op.apply(lv, rv, location);
        }

        @Override
        public long semanticHash() {
            return SemanticHashing.binaryOp(opType, lp.semanticHash(), rp.semanticHash());
        }
    }

    public record BinaryValuePure(
            BinaryOperatorType opType,
            BinaryOperation op,
            Value lv,
            PureOperator rp,
            SourceLocation location,
            boolean isDependingOnSubscription,
            boolean isRelativeExpression) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            val rv = rp.evaluate(ctx);
            if (rv instanceof ErrorValue) {
                return rv;
            }
            return op.apply(lv, rv, location);
        }

        @Override
        public long semanticHash() {
            return SemanticHashing.binaryOp(opType, lv.hashCode(), rp.semanticHash());
        }
    }

    public record BinaryPureValue(
            BinaryOperatorType opType,
            BinaryOperation op,
            PureOperator lp,
            Value rv,
            SourceLocation location,
            boolean isDependingOnSubscription,
            boolean isRelativeExpression) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            val lv = lp.evaluate(ctx);
            if (lv instanceof ErrorValue) {
                return lv;
            }
            return op.apply(lv, rv, location);
        }

        @Override
        public long semanticHash() {
            return SemanticHashing.binaryOp(opType, lp.semanticHash(), rv.hashCode());
        }
    }

    /**
     * Left-constant Value, right-Stream. Lazy variant: snapshot
     * {@code evaluate(ctx)} short-circuits on left {@link ErrorValue}
     * without subscribing the right stream. Selected at compile time when
     * {@code errorShortCircuit} is enabled.
     */
    record BinaryValueStreamLazy(BinaryOperation op, Value lv, StreamOperator rs, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return rs.stream().map(trv -> {
                val rv = trv.value();
                if (rv instanceof ErrorValue) {
                    return trv;
                }
                return new TracedValue(op.apply(lv, rv, location), trv.contributingAttributes());
            });
        }

        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            return op.evalLazy(lv, rs, location, ctx);
        }
    }

    /**
     * Left-constant Value, right-Stream. Eager variant: snapshot
     * {@code evaluate(ctx)} subscribes the right stream even when the
     * left Value is an {@link ErrorValue}, holds the first error and
     * returns it after the full walk. Selected at compile time when
     * {@code errorShortCircuit} is disabled (default).
     */
    record BinaryValueStreamEager(BinaryOperation op, Value lv, StreamOperator rs, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return rs.stream().map(trv -> {
                val rv = trv.value();
                if (rv instanceof ErrorValue) {
                    return trv;
                }
                return new TracedValue(op.apply(lv, rv, location), trv.contributingAttributes());
            });
        }

        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            return op.evalEager(lv, rs, location, ctx);
        }
    }

    /**
     * Left-Stream, right-constant Value. Lazy and eager produce identical
     * subscription sets and identical output here (right has no
     * subscriptions to "miss"), so a single record suffices. Snapshot
     * {@code evaluate(ctx)} uses the lazy helper as the canonical form.
     */
    public record BinaryStreamValue(BinaryOperation op, StreamOperator ls, Value rv, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return ls.stream().map(tlv -> {
                val lv = tlv.value();
                if (lv instanceof ErrorValue) {
                    return tlv;
                }
                return new TracedValue(op.apply(lv, rv, location), tlv.contributingAttributes());
            });
        }

        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            return op.evalLazy(ls, rv, location, ctx);
        }
    }

    /**
     * Left-Pure, right-Stream. Lazy variant: snapshot
     * {@code evaluate(ctx)} short-circuits on left pure
     * {@link ErrorValue} without subscribing the right stream. Selected
     * at compile time when {@code errorShortCircuit} is enabled.
     */
    record BinaryPureStreamLazy(BinaryOperation op, PureOperator lp, StreamOperator rs, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.deferContextual(ctx -> {
                val lv = lp.evaluate(ctx.get(EvaluationContext.class));
                if (lv instanceof ErrorValue) {
                    return Flux.just(new TracedValue(lv, List.of()));
                }
                return rs.stream().map(trv -> {
                    val rv = trv.value();
                    if (rv instanceof ErrorValue) {
                        return trv;
                    }
                    return new TracedValue(op.apply(lv, rv, location), trv.contributingAttributes());
                });
            });
        }

        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            return op.evalLazy(lp, rs, location, ctx);
        }
    }

    /**
     * Left-Pure, right-Stream. Eager variant: snapshot
     * {@code evaluate(ctx)} subscribes the right stream even when the
     * left pure produces an {@link ErrorValue}. Selected at compile time
     * when {@code errorShortCircuit} is disabled (default).
     */
    record BinaryPureStreamEager(BinaryOperation op, PureOperator lp, StreamOperator rs, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.deferContextual(ctx -> {
                val lv = lp.evaluate(ctx.get(EvaluationContext.class));
                if (lv instanceof ErrorValue) {
                    return Flux.just(new TracedValue(lv, List.of()));
                }
                return rs.stream().map(trv -> {
                    val rv = trv.value();
                    if (rv instanceof ErrorValue) {
                        return trv;
                    }
                    return new TracedValue(op.apply(lv, rv, location), trv.contributingAttributes());
                });
            });
        }

        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            return op.evalEager(lp, rs, location, ctx);
        }
    }

    /**
     * Left-Stream, right-Pure. Lazy and eager produce identical
     * subscription sets and identical output here (right has no
     * subscriptions to "miss"), so a single record suffices. Snapshot
     * {@code evaluate(ctx)} uses the lazy helper as the canonical form.
     */
    record BinaryStreamPure(BinaryOperation op, StreamOperator ls, PureOperator rp, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.deferContextual(ctx -> {
                val rv = rp.evaluate(ctx.get(EvaluationContext.class));
                if (rv instanceof ErrorValue) {
                    return Flux.just(new TracedValue(rv, List.of()));
                }
                return ls.stream().map(tlv -> {
                    val lv = tlv.value();
                    if (lv instanceof ErrorValue) {
                        return tlv;
                    }
                    return new TracedValue(op.apply(lv, rv, location), tlv.contributingAttributes());
                });
            });
        }

        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            return op.evalLazy(ls, rp, location, ctx);
        }
    }

    /**
     * Both children are streams. Lazy variant: snapshot
     * {@code evaluate(ctx)} short-circuits on left {@link ErrorValue}
     * without subscribing the right stream. Selected at compile time when
     * {@code errorShortCircuit} is enabled.
     */
    record BinaryStreamStreamLazy(BinaryOperation op, StreamOperator ls, StreamOperator rs, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.combineLatest(ls.stream(), rs.stream(), (tlv, trv) -> {
                val combined = new ArrayList<>(trv.contributingAttributes());
                combined.addAll(tlv.contributingAttributes());
                val lv = tlv.value();
                if (lv instanceof ErrorValue) {
                    return new TracedValue(lv, combined);
                }
                val rv = trv.value();
                if (rv instanceof ErrorValue) {
                    return new TracedValue(rv, combined);
                }
                return new TracedValue(op.apply(lv, rv, location), combined);
            });
        }

        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            return op.evalLazy(ls, rs, location, ctx);
        }
    }

    /**
     * Both children are streams. Eager variant: snapshot
     * {@code evaluate(ctx)} subscribes both streams regardless of which
     * errors first, holds the first error and returns it after the full
     * walk. Selected at compile time when {@code errorShortCircuit} is
     * disabled (default).
     */
    record BinaryStreamStreamEager(BinaryOperation op, StreamOperator ls, StreamOperator rs, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.combineLatest(ls.stream(), rs.stream(), (tlv, trv) -> {
                val combined = new ArrayList<>(trv.contributingAttributes());
                combined.addAll(tlv.contributingAttributes());
                val lv = tlv.value();
                if (lv instanceof ErrorValue) {
                    return new TracedValue(lv, combined);
                }
                val rv = trv.value();
                if (rv instanceof ErrorValue) {
                    return new TracedValue(rv, combined);
                }
                return new TracedValue(op.apply(lv, rv, location), combined);
            });
        }

        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            return op.evalEager(ls, rs, location, ctx);
        }
    }

    /**
     * Transitional adapter delegating to {@link BinaryOperation#evalLazy}
     * or {@link BinaryOperation#evalEager} depending on the runtime
     * {@code errorShortCircuit} parameter.
     * <p>
     * TODO: remove this helper once {@code RegexCompiler} has been split
     * into Lazy/Eager record variants matching the
     * {@code BinaryOperationCompiler} pattern. Once that lands, every
     * caller knows its variant at compile time and can call the
     * appropriate {@link BinaryOperation} default method directly.
     */
    static ExpressionResult evalBinary(BinaryOperation op, CompiledExpression left, CompiledExpression right,
            boolean errorShortCircuit, SourceLocation location, EvaluationContext ctx) {
        if (errorShortCircuit) {
            return op.evalLazy(left, right, location, ctx);
        }
        return op.evalEager(left, right, location, ctx);
    }

}
