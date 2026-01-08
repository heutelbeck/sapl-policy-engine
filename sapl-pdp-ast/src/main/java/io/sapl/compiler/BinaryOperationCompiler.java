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
import io.sapl.ast.BinaryOperator;
import io.sapl.ast.BinaryOperatorType;
import io.sapl.compiler.model.BinaryOperation;
import io.sapl.compiler.operators.ArithmeticOperators;
import io.sapl.compiler.operators.BooleanOperators;
import io.sapl.compiler.operators.ComparisonOperators;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.sapl.ast.BinaryOperatorType.*;

public class BinaryOperationCompiler {

    private static final String ERROR_UNIMPLEMENTED_BINARY_OPERATOR = "Unimplemented binary operator: %s";

    private final RegexCompiler                regexCompiler       = new RegexCompiler();
    private final SubtemplateCompiler          subtemplateCompiler = new SubtemplateCompiler();
    private final LazyBooleanOperationCompiler lazyBooleanCompiler = new LazyBooleanOperationCompiler();

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
            Map.entry(IN, ComparisonOperators::isContainedIn),
            // XOR (the only non-short-circuit boolean operator)
            Map.entry(XOR, BooleanOperators::xor));

    public CompiledExpression compile(BinaryOperator binaryOperation, CompilationContext ctx) {
        // Special handling for REGEX with pre-compilation
        if (binaryOperation.op() == REGEX) {
            return regexCompiler.compile(binaryOperation, ctx);
        }

        // Special handling for SUBTEMPLATE (::) operator
        if (binaryOperation.op() == SUBTEMPLATE) {
            return subtemplateCompiler.compile(binaryOperation, ctx);
        }

        // Special handling for lazy boolean operators (short-circuit)
        if (binaryOperation.op().isLazy()) {
            return lazyBooleanCompiler.compile(binaryOperation, ctx);
        }

        val op = BINARY_OPERATIONS.get(binaryOperation.op());
        if (op == null) {
            throw new SaplCompilerException(ERROR_UNIMPLEMENTED_BINARY_OPERATOR.formatted(binaryOperation.op()),
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
        var loc = binaryOperation.location();
        return switch (left) {
        case Value lv          -> switch (right) {
                           case Value rv              -> op.apply(lv, rv, loc);
                           case PureOperator rp       ->
                               new BinaryValuePure(op, lv, rp, loc, rp.isDependingOnSubscription());
                           case StreamOperator rs     -> new BinaryValueStream(op, lv, rs, loc);
                           };
        case PureOperator lp   -> switch (right) {
                           case Value rv              ->
                               new BinaryPureValue(op, lp, rv, loc, lp.isDependingOnSubscription());
                           case PureOperator rp       -> new BinaryPurePure(op, lp, rp, loc,
                                   lp.isDependingOnSubscription() || rp.isDependingOnSubscription());
                           case StreamOperator rs     -> new BinaryPureStream(op, lp, rs, loc);
                           };
        case StreamOperator ls -> switch (right) {
                           case Value rv              -> new BinaryStreamValue(op, ls, rv, loc);
                           case PureOperator rp       -> new BinaryStreamPure(op, ls, rp, loc);
                           case StreamOperator rs     -> new BinaryStreamStream(op, ls, rs, loc);
                           };
        };
    }

    public record BinaryPurePure(
            BinaryOperation op,
            PureOperator lp,
            PureOperator rp,
            SourceLocation location,
            boolean isDependingOnSubscription) implements PureOperator {
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
    }

    public record BinaryValuePure(
            BinaryOperation op,
            Value lv,
            PureOperator rp,
            SourceLocation location,
            boolean isDependingOnSubscription) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            val rv = rp.evaluate(ctx);
            if (rv instanceof ErrorValue) {
                return rv;
            }
            return op.apply(lv, rv, location);
        }
    }

    public record BinaryPureValue(
            BinaryOperation op,
            PureOperator lp,
            Value rv,
            SourceLocation location,
            boolean isDependingOnSubscription) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            val lv = lp.evaluate(ctx);
            if (lv instanceof ErrorValue) {
                return lv;
            }
            return op.apply(lv, rv, location);
        }
    }

    public record BinaryValueStream(BinaryOperation op, Value lv, StreamOperator rs, SourceLocation location)
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
    }

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
    }

    public record BinaryPureStream(BinaryOperation op, PureOperator lp, StreamOperator rs, SourceLocation location)
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
    }

    public record BinaryStreamPure(BinaryOperation op, StreamOperator ls, PureOperator rp, SourceLocation location)
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
    }

    public record BinaryStreamStream(BinaryOperation op, StreamOperator ls, StreamOperator rs, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.combineLatest(ls.stream(), rs.stream(), (tlv, trv) -> {
                var combined = new ArrayList<>(trv.contributingAttributes());
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
    }

}
