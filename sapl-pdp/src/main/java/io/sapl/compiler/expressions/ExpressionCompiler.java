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
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.ReservedIdentifiers;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Value;
import io.sapl.ast.ArrayExpression;
import io.sapl.ast.BinaryOperator;
import io.sapl.ast.Conjunction;
import io.sapl.ast.Disjunction;
import io.sapl.ast.EnvironmentAttribute;
import io.sapl.ast.ExclusiveDisjunction;
import io.sapl.ast.Expression;
import io.sapl.ast.ExtendedFilter;
import io.sapl.ast.FunctionCall;
import io.sapl.ast.Identifier;
import io.sapl.ast.Literal;
import io.sapl.ast.ObjectExpression;
import io.sapl.ast.Parenthesized;
import io.sapl.ast.Product;
import io.sapl.ast.RelativeReference;
import io.sapl.ast.SimpleFilter;
import io.sapl.ast.Step;
import io.sapl.ast.Sum;
import io.sapl.ast.UnaryOperator;
import io.sapl.compiler.util.DummyEvaluationContextFactory;
import lombok.val;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ExpressionCompiler {

    public CompiledExpression compile(Expression expression, CompilationContext ctx) {
        return switch (expression) {
        case Literal(var value, var ignored)           -> value;
        case Parenthesized(var inner, var ignored)     -> compile(inner, ctx);
        case Identifier identifier                     -> compileIdentifier(identifier, ctx);
        case RelativeReference(var type, var location) -> switch (type) {
                                                   case VALUE        -> new RelativeValueOp(location);
                                                   case LOCATION     -> new RelativeLocationOp(location);
                                                   };

        // Operations
        case BinaryOperator b -> BinaryOperationCompiler.compile(b, ctx);
        case UnaryOperator u  -> UnaryOperatorCompiler.compile(u, ctx);

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
        case FunctionCall fc   -> FunctionCallCompiler.compile(fc, ctx);
        case SimpleFilter sf   -> FilterCompiler.compileSimple(sf, ctx);
        case ExtendedFilter ef -> ExtendedFilterCompiler.compile(ef, ctx);
        };
    }

    public CompiledExpression compileIdentifier(Identifier identifier, CompilationContext ctx) {
        val name     = identifier.name();
        val location = identifier.location();
        if (ReservedIdentifiers.RESERVED_IDENTIFIERS.contains(name)) {
            return new IdentifierOp(name, location);
        }
        return ctx.getVariable(name);
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

    public static CompiledExpression fold(CompiledExpression compiledExpression, CompilationContext ctx) {
        return switch (compiledExpression) {
        case Value value                                         -> value;
        case PureOperator po when po.isDependingOnSubscription() -> po;
        case PureOperator po                                     -> foldPure(po, ctx);
        case StreamOperator sto                                  -> sto;
        };
    }

    private static CompiledExpression foldPure(PureOperator po, CompilationContext ctx) {
        val foldingContext = DummyEvaluationContextFactory.dummyContext(ctx);
        return po.evaluate(foldingContext);
    }

    public record RelativeValueOp(SourceLocation location) implements PureOperator {
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
