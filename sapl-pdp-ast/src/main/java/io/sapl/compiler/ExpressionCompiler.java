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
import io.sapl.ast.*;
import lombok.experimental.UtilityClass;
import lombok.val;

@UtilityClass
public class ExpressionCompiler {

    private static final BinaryOperationCompiler BINARY_COMPILER = new BinaryOperationCompiler();
    private static final UnaryOperatorCompiler   UNARY_COMPILER  = new UnaryOperatorCompiler();

    public CompiledExpression compile(Expression expression, CompilationContext ctx) {
        return switch (expression) {
        case Literal l       -> l.value();
        case Parenthesized p -> compile(p.expression(), ctx);

        case Identifier id -> {
            val localVariable = ctx.getVariable(id.name());
            yield localVariable != null ? localVariable : new IdentifierOp(id.name(), id.location());
        }

        case RelativeReference r -> switch (r.type()) {
                             case VALUE        -> new RelativeValueOp(r.location());
                             case LOCATION     -> new RelativeLocationOp(r.location());
                             };

        // Operations
        case BinaryOperator b -> BINARY_COMPILER.compile(b, ctx);
        case UnaryOperator u  -> UNARY_COMPILER.compile(u, ctx);

        // N-ary operations
        case ArrayExpression a       -> ArrayCompiler.compile(a, ctx);
        case ObjectExpression o      -> ObjectCompiler.compile(o, ctx);
        case Conjunction c           -> LazyNaryBooleanCompiler.compileConjunction(c, ctx);
        case Disjunction d           -> LazyNaryBooleanCompiler.compileDisjunction(d, ctx);
        case ExclusiveDisjunction xd -> NaryOperatorCompiler.compileXor(xd, ctx);
        case Sum s                   -> NaryOperatorCompiler.compileSum(s, ctx);
        case Product p               -> NaryOperatorCompiler.compileProduct(p, ctx);

        // Attributes and Steps
        case EnvironmentAttribute ea -> AttributeCompiler.compileEnvironmentAttribute(ea, ctx);
        case Step s                  -> StepCompiler.compile(s, ctx);

        // Functions and Filters
        case FunctionCall fc    -> FunctionCallCompiler.compile(fc, ctx);
        case FilterOperation fo -> Value.error("UNIMPLEMENTED: FilterOperation");
        };
    }

    record IdentifierOp(String name, SourceLocation location) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return ctx.get(name);
        }

        @Override
        public boolean isDependingOnSubscription() {
            return true;
        }
    }

    record RelativeValueOp(SourceLocation location) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return ctx.relativeValue();
        }

        @Override
        public boolean isDependingOnSubscription() {
            return false;
        }
    }

    record RelativeLocationOp(SourceLocation location) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return ctx.relativeLocation();
        }

        @Override
        public boolean isDependingOnSubscription() {
            return false;
        }
    }

}
