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
import io.sapl.ast.BinaryOperator;
import io.sapl.ast.BinaryOperatorType;
import io.sapl.compiler.index.SemanticHashing;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;

import static io.sapl.api.model.StreamOperator.evalChild;

/**
 * Compiler for boolean operators AND ({@code &&}, {@code &}) and OR
 * ({@code ||}, {@code |}).
 * <p>
 * On the value and pure strata, lazy and eager variants behave identically.
 * On the streaming stratum they differ:
 * <ul>
 * <li>Lazy ({@code &&}, {@code ||}): short-circuits on the snapshot value of
 * the left operand and skips the right subtree entirely, opening no
 * subscriptions to skipped operands.</li>
 * <li>Eager ({@code &}, {@code |}): walks every operand against the snapshot
 * to accumulate all dependencies, even past a short-circuit value.</li>
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
        val isAnd     = op == BinaryOperatorType.LAZY_AND || op == BinaryOperatorType.EAGER_AND;
        val dominator = isAnd ? Value.FALSE : Value.TRUE;

        // Kleene strong 3-valued logic: only the dominator (FALSE for AND, TRUE for OR)
        // short-circuits. A constant left dominator folds the whole expression and
        // skips the right entirely, so no subscriptions are opened for it. A constant
        // left error or non-boolean does not short-circuit, because the right may still
        // yield the dominator.
        if (dominator.equals(left)) {
            return dominator;
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
        return combine(v1, v2, isAnd, location);
    }

    private CompiledExpression compileValuePure(Value v, PureOperator p, boolean isAnd, SourceLocation location) {
        val dominator = isAnd ? Value.FALSE : Value.TRUE;
        if (v instanceof BooleanValue) {
            if (dominator.equals(v)) {
                return dominator;
            }
            // v is the identity boolean: the result is the right operand as a boolean.
            return new LazyValuePure(p, location, p.isDependingOnSubscription(), p.isRelativeExpression());
        }
        // v is a constant error or non-boolean: it cannot fold, so combine it with the
        // pure operand at runtime. A dominating pure value still wins.
        val constant = new ConstantErrorPredicate(asError(v, location), location);
        return isAnd
                ? new LazyAndPurePure(constant, p, location, p.isDependingOnSubscription(), p.isRelativeExpression())
                : new LazyOrPurePure(constant, p, location, p.isDependingOnSubscription(), p.isRelativeExpression());
    }

    private CompiledExpression compileValueStream(Value v, StreamOperator s, boolean isAnd, SourceLocation location) {
        val dominator = isAnd ? Value.FALSE : Value.TRUE;
        if (v instanceof BooleanValue) {
            if (dominator.equals(v)) {
                return dominator;
            }
            return new LazyValueStream(s, location);
        }
        val constant = new ConstantErrorPredicate(asError(v, location), location);
        return isAnd ? new LazyAndPureStream(constant, s, location) : new LazyOrPureStream(constant, s, location);
    }

    private CompiledExpression compilePurePure(PureOperator p1, PureOperator p2, boolean isAnd,
            SourceLocation location) {
        val depending  = p1.isDependingOnSubscription() || p2.isDependingOnSubscription();
        val isRelative = p1.isRelativeExpression() || p2.isRelativeExpression();
        return isAnd ? new LazyAndPurePure(p1, p2, location, depending, isRelative)
                : new LazyOrPurePure(p1, p2, location, depending, isRelative);
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

    /**
     * Coerces a value used in a boolean position to boolean-or-error: a boolean
     * or an existing error passes through, any other (non-boolean) value becomes
     * a type-mismatch error. Shared by the operators, the policy index, and the
     * coverage evaluator so they all agree on the same rule and message.
     */
    public static Value asBoolean(Value v, SourceLocation location) {
        if (v instanceof BooleanValue) {
            return v;
        }
        if (v instanceof ErrorValue) {
            return v;
        }
        return Value.errorAt(location, ERROR_TYPE_MISMATCH, v.getClass().getSimpleName());
    }

    /**
     * Kleene strong combination of two resolved values. The dominator (FALSE
     * for AND, TRUE for OR) wins regardless of position. Otherwise the first
     * error (an {@link ErrorValue}, or a non-boolean as a type mismatch) is
     * returned, and only when neither operand errors is the identity returned.
     */
    private static Value combine(Value a, Value b, boolean isAnd, SourceLocation location) {
        val dominator = isAnd ? Value.FALSE : Value.TRUE;
        if (dominator.equals(a) || dominator.equals(b)) {
            return dominator;
        }
        val errorA = kleeneError(a, location);
        if (errorA != null) {
            return errorA;
        }
        val errorB = kleeneError(b, location);
        if (errorB != null) {
            return errorB;
        }
        return isAnd ? Value.TRUE : Value.FALSE;
    }

    private static @Nullable Value kleeneError(Value v, SourceLocation location) {
        if (v instanceof ErrorValue) {
            return v;
        }
        if (!(v instanceof BooleanValue)) {
            return Value.errorAt(location, ERROR_TYPE_MISMATCH, v.getClass().getSimpleName());
        }
        return null;
    }

    private static Value asError(Value v, SourceLocation location) {
        if (v instanceof ErrorValue) {
            return v;
        }
        return Value.errorAt(location, ERROR_TYPE_MISMATCH, v.getClass().getSimpleName());
    }

    record LazyValuePure(
            PureOperator p,
            SourceLocation location,
            boolean isDependingOnSubscription,
            boolean isRelativeExpression) implements PureOperator {

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
            boolean isDependingOnSubscription,
            boolean isRelativeExpression) implements PureOperator {

        private static final long KIND = SemanticHashing.kindHash(LazyAndPurePure.class);

        @Override
        public Value evaluate(EvaluationContext ctx) {
            val v1 = p1.evaluate(ctx);
            if (Value.FALSE.equals(v1)) {
                return Value.FALSE;
            }
            return combine(v1, p2.evaluate(ctx), true, location);
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

    record LazyOrPurePure(
            PureOperator p1,
            PureOperator p2,
            SourceLocation location,
            boolean isDependingOnSubscription,
            boolean isRelativeExpression) implements PureOperator {

        private static final long KIND = SemanticHashing.kindHash(LazyOrPurePure.class);

        @Override
        public Value evaluate(EvaluationContext ctx) {
            val v1 = p1.evaluate(ctx);
            if (Value.TRUE.equals(v1)) {
                return Value.TRUE;
            }
            return combine(v1, p2.evaluate(ctx), false, location);
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

    record LazyValueStream(StreamOperator s, SourceLocation location) implements StreamOperator {
        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            val deps = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(1);
            val v    = evalChild(s, ctx, deps);
            if (v == null || v instanceof ErrorValue) {
                return new ExpressionResult(v, deps);
            }
            return new ExpressionResult(asBoolean(v, location), deps);
        }
    }

    record LazyAndPureStream(PureOperator p, StreamOperator s, SourceLocation location) implements StreamOperator {
        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            val deps = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(1);
            val pv   = p.evaluate(ctx);
            if (Value.FALSE.equals(pv)) {
                return new ExpressionResult(Value.FALSE, deps);
            }
            val sv = evalChild(s, ctx, deps);
            if (sv == null) {
                return new ExpressionResult(null, deps);
            }
            return new ExpressionResult(combine(pv, sv, true, location), deps);
        }
    }

    record LazyOrPureStream(PureOperator p, StreamOperator s, SourceLocation location) implements StreamOperator {
        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            val deps = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(1);
            val pv   = p.evaluate(ctx);
            if (Value.TRUE.equals(pv)) {
                return new ExpressionResult(Value.TRUE, deps);
            }
            val sv = evalChild(s, ctx, deps);
            if (sv == null) {
                return new ExpressionResult(null, deps);
            }
            return new ExpressionResult(combine(pv, sv, false, location), deps);
        }
    }

    record LazyAndStreamStream(StreamOperator s1, StreamOperator s2, SourceLocation location)
            implements StreamOperator {
        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            val deps = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(2);
            val lv   = evalChild(s1, ctx, deps);
            if (lv == null) {
                return new ExpressionResult(null, deps);
            }
            if (Value.FALSE.equals(lv)) {
                return new ExpressionResult(Value.FALSE, deps);
            }
            val rv = evalChild(s2, ctx, deps);
            if (rv == null) {
                return new ExpressionResult(null, deps);
            }
            return new ExpressionResult(combine(lv, rv, true, location), deps);
        }
    }

    record LazyOrStreamStream(StreamOperator s1, StreamOperator s2, SourceLocation location) implements StreamOperator {
        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            val deps = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(2);
            val lv   = evalChild(s1, ctx, deps);
            if (lv == null) {
                return new ExpressionResult(null, deps);
            }
            if (Value.TRUE.equals(lv)) {
                return new ExpressionResult(Value.TRUE, deps);
            }
            val rv = evalChild(s2, ctx, deps);
            if (rv == null) {
                return new ExpressionResult(null, deps);
            }
            return new ExpressionResult(combine(lv, rv, false, location), deps);
        }
    }

    record EagerAndStreamStream(StreamOperator s1, StreamOperator s2, SourceLocation location)
            implements StreamOperator {
        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            val deps = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(2);
            val lv   = evalChild(s1, ctx, deps);
            val rv   = evalChild(s2, ctx, deps);
            if (Value.FALSE.equals(lv) || Value.FALSE.equals(rv)) {
                return new ExpressionResult(Value.FALSE, deps);
            }
            if (lv == null || rv == null) {
                return new ExpressionResult(null, deps);
            }
            return new ExpressionResult(combine(lv, rv, true, location), deps);
        }
    }

    record EagerOrStreamStream(StreamOperator s1, StreamOperator s2, SourceLocation location)
            implements StreamOperator {
        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            val deps = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(2);
            val lv   = evalChild(s1, ctx, deps);
            val rv   = evalChild(s2, ctx, deps);
            if (Value.TRUE.equals(lv) || Value.TRUE.equals(rv)) {
                return new ExpressionResult(Value.TRUE, deps);
            }
            if (lv == null || rv == null) {
                return new ExpressionResult(null, deps);
            }
            return new ExpressionResult(combine(lv, rv, false, location), deps);
        }
    }

}
