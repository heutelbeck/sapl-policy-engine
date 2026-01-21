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
import io.sapl.compiler.operators.ArithmeticOperators;
import io.sapl.compiler.operators.BooleanOperators;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.Map;

import static io.sapl.ast.UnaryOperatorType.*;

@UtilityClass
public class UnaryOperatorCompiler {

    private static final String ERROR_UNIMPLEMENTED_UNARY_OPERATOR = "Unimplemented unary operator: %s";

    static final Map<UnaryOperatorType, UnaryOperation> UNARY_OPERATIONS = Map.of(NOT, BooleanOperators::not, NEGATE,
            ArithmeticOperators::unaryMinus, PLUS, ArithmeticOperators::unaryPlus);

    public CompiledExpression compile(UnaryOperator unaryOp, CompilationContext ctx) {
        val op = UNARY_OPERATIONS.get(unaryOp.op());
        if (op == null) {
            throw new SaplCompilerException(ERROR_UNIMPLEMENTED_UNARY_OPERATOR.formatted(unaryOp.op()), unaryOp);
        }
        val operand = ExpressionCompiler.compile(unaryOp.operand(), ctx);
        if (operand instanceof ErrorValue) {
            return operand;
        }
        val location = unaryOp.location();

        return switch (operand) {
        case Value v          -> op.apply(v, location);
        case PureOperator p   -> new UnaryPure(op, p, location, p.isDependingOnSubscription());
        case StreamOperator s -> new UnaryStream(op, s, location);
        };
    }

    public record UnaryPure(
            UnaryOperation op,
            PureOperator operand,
            SourceLocation location,
            boolean isDependingOnSubscription) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            val v = operand.evaluate(ctx);
            if (v instanceof ErrorValue) {
                return v;
            }
            return op.apply(v, location);
        }
    }

    public record UnaryStream(UnaryOperation op, StreamOperator operand, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return operand.stream().map(tv -> {
                val v = tv.value();
                if (v instanceof ErrorValue) {
                    return tv;
                }
                return new TracedValue(op.apply(v, location), tv.contributingAttributes());
            });
        }
    }

}
