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
import io.sapl.ast.UnaryOperator;
import io.sapl.ast.UnaryOperatorType;
import io.sapl.compiler.expressions.BooleanGuard.BooleanResult;
import io.sapl.compiler.expressions.BooleanGuard.CompiledUnary;
import io.sapl.compiler.index.SemanticHashing;
import io.sapl.compiler.operators.ArithmeticOperators;
import io.sapl.compiler.operators.BooleanOperators;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.Map;

import static io.sapl.ast.UnaryOperatorType.*;
import static io.sapl.compiler.expressions.BooleanGuard.BOOLEAN_GUARD;
import static io.sapl.compiler.expressions.BooleanGuard.withoutBooleanGuard;

@UtilityClass
public class UnaryOperatorCompiler {

    private static final String ERROR_UNIMPLEMENTED_UNARY_OPERATOR = "Unimplemented unary operator: %s";

    private static final UnaryOperation LOGICAL_NOT = BooleanOperators::not;

    static final Map<UnaryOperatorType, UnaryOperation> UNARY_OPERATIONS = Map.of(NOT, LOGICAL_NOT, CHECK_BOOLEAN,
            BOOLEAN_GUARD, NEGATE, ArithmeticOperators::unaryMinus, PLUS, ArithmeticOperators::unaryPlus);

    public CompiledExpression compile(UnaryOperator unaryOp, CompilationContext ctx) {
        val op = UNARY_OPERATIONS.get(unaryOp.op());
        if (op == null) {
            throw new SaplCompilerException(ERROR_UNIMPLEMENTED_UNARY_OPERATOR.formatted(unaryOp.op()), unaryOp);
        }
        var operand = ExpressionCompiler.compile(unaryOp.operand(), ctx);
        if (operand instanceof ErrorValue) {
            return operand;
        }
        var location = unaryOp.location();
        // Variable bindings can expose a boolean check that was not visible in the AST.
        if (op == BOOLEAN_GUARD && (operand instanceof BooleanResult result && result.hasBooleanResult()
                || operand instanceof CompiledUnary inner
                        && (inner.op() == LOGICAL_NOT || inner.op() == BOOLEAN_GUARD))) {
            return operand;
        }
        if (op == LOGICAL_NOT) {
            if (operand instanceof CompiledUnary inner && inner.op() == BOOLEAN_GUARD) {
                location = inner.location();
            }
            operand = withoutBooleanGuard(operand);
        }

        return switch (operand) {
        case Value v          -> op.apply(v, location);
        case PureOperator p   ->
            new UnaryPure(unaryOp.op(), op, p, location, p.isDependingOnSubscription(), p.isRelativeExpression());
        case StreamOperator s -> new UnaryStream(op, s, location);
        };
    }

    public record UnaryPure(
            UnaryOperatorType opType,
            UnaryOperation op,
            PureOperator operand,
            SourceLocation location,
            boolean isDependingOnSubscription,
            boolean isRelativeExpression) implements PureOperator, CompiledUnary {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            val v = operand.evaluate(ctx);
            if (v instanceof ErrorValue) {
                return v;
            }
            return op.apply(v, location);
        }

        @Override
        public long semanticHash() {
            return SemanticHashing.ordered(SemanticHashing.kindHash(opType), operand.semanticHash());
        }

        @Override
        public BooleanExpression booleanExpression() {
            if (opType == UnaryOperatorType.NOT) {
                return new BooleanExpression.Not(operand.booleanExpression());
            }
            return PureOperator.super.booleanExpression();
        }
    }

    public record UnaryStream(UnaryOperation op, StreamOperator operand, SourceLocation location)
            implements StreamOperator, CompiledUnary {
        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            val r = operand.evaluate(ctx);
            val v = r.result();
            if (v == null || v instanceof ErrorValue) {
                return r;
            }
            return new ExpressionResult(op.apply(v, location), r.dependencies());
        }
    }

}
