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

import io.sapl.compiler.expressions.BooleanGuard.BinaryResult;

import io.sapl.api.model.*;
import io.sapl.ast.ArrayExpression;
import io.sapl.ast.BinaryOperator;
import io.sapl.ast.BinaryOperatorType;
import io.sapl.ast.Expression;
import io.sapl.compiler.index.SemanticHashing;
import io.sapl.compiler.operators.ArithmeticOperators;
import io.sapl.compiler.operators.BooleanOperators;
import io.sapl.compiler.operators.ComparisonOperators;
import io.sapl.compiler.operators.HasOperators;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static io.sapl.ast.BinaryOperatorType.*;

@UtilityClass
public class BinaryOperationCompiler {

    private static final String ERROR_BOOLEAN_ORDERING_OPERAND      = "Numeric comparisons require numbers, but this operand is %s.";
    private static final String ERROR_UNIMPLEMENTED_BINARY_OPERATOR = "Unimplemented binary operator: %s";

    static final Map<BinaryOperatorType, BinaryOperation> BINARY_OPERATIONS = Map.ofEntries(
            // Arithmetic
            Map.entry(ADD, ArithmeticOperators::add), Map.entry(SUB, ArithmeticOperators::subtract),
            Map.entry(MUL, ArithmeticOperators::multiply), Map.entry(DIV, ArithmeticOperators::divide),
            Map.entry(MOD, ArithmeticOperators::modulo),
            // Numeric comparison
            predicate(LT, ArithmeticOperators::lessThan), predicate(LE, ArithmeticOperators::lessThanOrEqual),
            predicate(GT, ArithmeticOperators::greaterThan), predicate(GE, ArithmeticOperators::greaterThanOrEqual),
            // Equality
            predicate(EQ, (a, b, location) -> ComparisonOperators.equals(a, b)),
            predicate(NE, (a, b, location) -> ComparisonOperators.notEquals(a, b)),
            // Membership
            predicate(IN, ComparisonOperators::isContainedIn), predicate(ANY_IN, ComparisonOperators::anyIn),
            predicate(ALL_IN, ComparisonOperators::allIn),
            // Key membership (has operator)
            predicate(HAS_ONE, HasOperators::hasOne), predicate(HAS_ANY, HasOperators::hasAny),
            predicate(HAS_ALL, HasOperators::hasAll),
            // XOR (the only non-short-circuit boolean operator)
            predicate(XOR, BooleanOperators::xor));

    private static Map.Entry<BinaryOperatorType, BinaryOperation> predicate(BinaryOperatorType type,
            BooleanGuard.Predicate operation) {
        return Map.entry(type, operation);
    }

    /**
     * The commutative operators whose operands are compiled in canonical order.
     * The operator {@code +} is not among them, as it concatenates texts.
     */
    private static final Set<BinaryOperatorType> CANONICALLY_ORDERED = EnumSet.of(EQ, NE, MUL, XOR);

    private static final Set<BinaryOperatorType> NUMERIC_COMPARISONS = EnumSet.of(LT, LE, GT, GE);

    /**
     * Compiles a binary expression and folds its constant operands.
     *
     * @param binaryOperation the operation as written in the policy
     * @param ctx the compilation context
     * @return the compiled expression
     * @throws SaplCompilerException if the operation cannot be compiled, including a
     * boolean constant on either side of a numeric comparison
     */
    public CompiledExpression compile(BinaryOperator binaryOperation, CompilationContext ctx) {
        val special = tryCompileSpecialCase(binaryOperation, ctx);
        if (special != null) {
            return special;
        }

        val operatorType = binaryOperation.op();
        val op           = BINARY_OPERATIONS.get(operatorType);
        if (op == null) {
            throw new SaplCompilerException(ERROR_UNIMPLEMENTED_BINARY_OPERATOR.formatted(operatorType),
                    binaryOperation);
        }
        val operands = CompilationChildren.pair(() -> compileOperand(binaryOperation.left(), operatorType, ctx),
                () -> compileOperand(binaryOperation.right(), operatorType, ctx));
        val left     = operands.first();
        val right    = operands.second();
        val location = binaryOperation.location();
        if (CANONICALLY_ORDERED.contains(operatorType)) {
            return compileCanonicallyOrdered(operatorType, op, left, right, location);
        }
        if (left instanceof ErrorValue) {
            return left;
        }
        if (right instanceof ErrorValue) {
            return right;
        }
        if (operatorType == IN && ctx.unrollInOperator() && binaryOperation.right() instanceof ArrayExpression
                && left instanceof PureOperator needle && right instanceof ArrayValue haystack
                && InArrayUnrollingCompiler.reserveExpansion(needle, haystack, ctx)) {
            return compileUnrolledMembership(needle, haystack, location);
        }
        return dispatchToShape(operatorType, op, left, right, location);
    }

    private static CompiledExpression compileOperand(Expression operand, BinaryOperatorType operatorType,
            CompilationContext ctx) {
        val compiled = ExpressionCompiler.compile(operand, ctx);
        if (compiled instanceof BooleanValue(boolean value) && NUMERIC_COMPARISONS.contains(operatorType)) {
            throw new SaplCompilerException(ERROR_BOOLEAN_ORDERING_OPERAND.formatted(value), operand);
        }
        return compiled;
    }

    private static CompiledExpression compileUnrolledMembership(PureOperator needle, ArrayValue haystack,
            SourceLocation location) {
        val                equals = BINARY_OPERATIONS.get(EQ);
        CompiledExpression chain  = compileCanonicallyOrdered(EQ, equals, needle, haystack.getFirst(), location);
        for (int index = 1; index < haystack.size(); index++) {
            val next = compileCanonicallyOrdered(EQ, equals, needle, haystack.get(index), location);
            chain = StratifiedBooleanOperationCompiler.compile(chain, next, LAZY_OR, location);
        }
        val notEqual = BINARY_OPERATIONS.get(NE);
        val defined  = new BinaryPureValue(NE, notEqual, needle, Value.UNDEFINED, location,
                needle.isDependingOnSubscription(), needle.isRelativeExpression());
        return StratifiedBooleanOperationCompiler.compile(defined, chain, LAZY_AND, location);
    }

    private static CompiledExpression compileCanonicallyOrdered(BinaryOperatorType operatorType, BinaryOperation op,
            CompiledExpression written, CompiledExpression other, SourceLocation location) {
        final CompiledExpression left;
        final CompiledExpression right;
        if (CanonicalOperandOrder.precedes(other, written)) {
            left  = other;
            right = written;
        } else {
            left  = written;
            right = other;
        }
        if (left instanceof ErrorValue) {
            return left;
        }
        if (right instanceof ErrorValue) {
            return right;
        }
        return dispatchToShape(operatorType, op, left, right, location);
    }

    private CompiledExpression tryCompileSpecialCase(BinaryOperator binaryOperation, CompilationContext ctx) {
        val operatorType = binaryOperation.op();
        if (operatorType == REGEX) {
            return RegexCompiler.compile(binaryOperation, ctx);
        }
        if (operatorType == SUBTEMPLATE) {
            return SubtemplateCompiler.compile(binaryOperation, ctx);
        }
        if ((operatorType == ANY_IN || operatorType == ALL_IN) && binaryOperation.left() instanceof ArrayExpression arr
                && arr.elements().size() == 1) {
            return compile(new BinaryOperator(IN, arr.elements().getFirst(), binaryOperation.right(),
                    binaryOperation.location()), ctx);
        }
        if (operatorType.isBooleanAndOr()) {
            return StratifiedBooleanOperationCompiler.compile(binaryOperation, ctx);
        }
        return null;
    }

    private static CompiledExpression dispatchToShape(BinaryOperatorType operatorType, BinaryOperation op,
            CompiledExpression left, CompiledExpression right, SourceLocation loc) {
        if (left instanceof Value lv && right instanceof Value rv) {
            return op.apply(lv, rv, loc);
        }
        if (left instanceof StreamOperator || right instanceof StreamOperator) {
            return new BinaryStream(op, left, right, loc);
        }
        return buildPureBinary(operatorType, op, left, right, loc);
    }

    private static PureOperator buildPureBinary(BinaryOperatorType operatorType, BinaryOperation op,
            CompiledExpression left, CompiledExpression right, SourceLocation loc) {
        if (left instanceof Value lv) {
            val rp = (PureOperator) right;
            return new BinaryValuePure(operatorType, op, lv, rp, loc, rp.isDependingOnSubscription(),
                    rp.isRelativeExpression());
        }
        val lp = (PureOperator) left;
        if (right instanceof Value rv) {
            return new BinaryPureValue(operatorType, op, lp, rv, loc, lp.isDependingOnSubscription(),
                    lp.isRelativeExpression());
        }
        val rp = (PureOperator) right;
        return new BinaryPurePure(operatorType, op, lp, rp, loc,
                lp.isDependingOnSubscription() || rp.isDependingOnSubscription(),
                lp.isRelativeExpression() || rp.isRelativeExpression());
    }

    public record BinaryPurePure(
            BinaryOperatorType opType,
            BinaryOperation op,
            PureOperator lp,
            PureOperator rp,
            SourceLocation location,
            boolean isDependingOnSubscription,
            boolean isRelativeExpression) implements PureOperator, BinaryResult {
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
            boolean isRelativeExpression) implements PureOperator, BinaryResult {
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
            return SemanticHashing.binaryOp(opType, SemanticHashing.valueHash(lv), rp.semanticHash());
        }
    }

    public record BinaryPureValue(
            BinaryOperatorType opType,
            BinaryOperation op,
            PureOperator lp,
            Value rv,
            SourceLocation location,
            boolean isDependingOnSubscription,
            boolean isRelativeExpression) implements PureOperator, BinaryResult {
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
            return SemanticHashing.binaryOp(opType, lp.semanticHash(), SemanticHashing.valueHash(rv));
        }
    }

    /**
     * Stream-stratum binary operation. At least one of {@code left} or
     * {@code right} is a {@link StreamOperator}; {@link #evaluate}
     * delegates to {@link BinaryOperation#evalEager} which walks both
     * children to accumulate the maximum subscription set, holds the
     * first error from either side, and returns it after the full walk.
     */
    public record BinaryStream(
            BinaryOperation op,
            CompiledExpression left,
            CompiledExpression right,
            SourceLocation location) implements StreamOperator, BinaryResult {
        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            return op.evalEager(left, right, location, ctx);
        }
    }

}
