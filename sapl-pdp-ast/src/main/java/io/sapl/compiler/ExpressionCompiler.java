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

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.TracedValue;
import io.sapl.api.model.Value;
import io.sapl.ast.*;
import io.sapl.compiler.operators.IdentifierOperator;
import io.sapl.compiler.operators.RelativeLocationOperator;
import io.sapl.compiler.operators.RelativeValueOperator;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.List;

@UtilityClass
public class ExpressionCompiler {

    public CompiledExpression compile(Expression expression, CompilationContext ctx) {
        return switch (expression) {
        case Literal l           -> l.value();
        case Identifier id       -> compileIdentifier(id, ctx);
        case Parenthesized p     -> compile(p.expression(), ctx);
        case RelativeReference r -> compileRelativeReference(r, ctx);

        // Operations
        case BinaryOperator b -> compileBinaryOperation(b, ctx);
        case UnaryOperator u  -> compileUnaryOperation(u, ctx);

        // N-ary operations
        case ArrayExpression a       -> compileArrayExpression(a, ctx);
        case ObjectExpression o      -> compileObjectExpression(o, ctx);
        case Conjunction c           -> compileConjunction(c, ctx);
        case Disjunction d           -> compileDisjunction(d, ctx);
        case EagerConjunction ec     -> compileEagerConjunction(ec, ctx);
        case EagerDisjunction ed     -> compileEagerDisjunction(ed, ctx);
        case ExclusiveDisjunction xd -> compileExclusiveDisjunction(xd, ctx);
        case Sum s                   -> compileSum(s, ctx);
        case Product p               -> compileProduct(p, ctx);

        // Steps
        case KeyStep ks                -> compileKeyStep(ks, ctx);
        case IndexStep is              -> compileIndexStep(is, ctx);
        case WildcardStep ws           -> compileWildcardStep(ws, ctx);
        case SliceStep ss              -> compileSliceStep(ss, ctx);
        case ExpressionStep es         -> compileExpressionStep(es, ctx);
        case ConditionStep cs          -> compileConditionStep(cs, ctx);
        case RecursiveKeyStep rks      -> compileRecursiveKeyStep(rks, ctx);
        case RecursiveIndexStep ris    -> compileRecursiveIndexStep(ris, ctx);
        case RecursiveWildcardStep rws -> compileRecursiveWildcardStep(rws, ctx);
        case IndexUnionStep ius        -> compileIndexUnionStep(ius, ctx);
        case AttributeUnionStep aus    -> compileAttributeUnionStep(aus, ctx);

        // Functions and Filters
        case FunctionCall fc    -> compileFunctionCall(fc, ctx);
        case FilterOperation fo -> compileFilterOperation(fo, ctx);

        // Attributes (STREAM)
        case EnvironmentAttribute ea -> compileEnvironmentAttribute(ea, ctx);
        case AttributeStep as        -> compileAttributeStep(as, ctx);
        };
    }

    private static CompiledExpression compileIdentifier(Identifier id, CompilationContext ctx) {
        val name          = id.name();
        val localVariable = ctx.getVariable(name);
        if (localVariable != null) {
            return localVariable;
        }
        return new IdentifierOperator(name, id.location(), true);
    }

    private CompiledExpression compileRelativeReference(RelativeReference r, CompilationContext ctx) {
        return switch (r.type()) {
        case VALUE    -> new RelativeValueOperator(r.location());
        case LOCATION -> new RelativeLocationOperator(r.location());
        };
    }

    private static final BinaryOperationCompiler BINARY_COMPILER = new BinaryOperationCompiler();
    private static final UnaryOperatorCompiler   UNARY_COMPILER  = new UnaryOperatorCompiler();

    private CompiledExpression compileBinaryOperation(BinaryOperator b, CompilationContext ctx) {
        return BINARY_COMPILER.compile(b, ctx);
    }

    private CompiledExpression compileUnaryOperation(UnaryOperator u, CompilationContext ctx) {
        return UNARY_COMPILER.compile(u, ctx);
    }

    private CompiledExpression compileArrayExpression(ArrayExpression a, CompilationContext ctx) {
        return ArrayCompiler.compile(a, ctx);
    }

    private CompiledExpression compileObjectExpression(ObjectExpression o, CompilationContext ctx) {
        return ObjectCompiler.compile(o, ctx);
    }

    private CompiledExpression compileFunctionCall(FunctionCall fc, CompilationContext ctx) {
        return unimplemented("FunctionCall");
    }

    private CompiledExpression compileFilterOperation(FilterOperation fo, CompilationContext ctx) {
        return unimplemented("FilterOperation");
    }

    private CompiledExpression compileConjunction(Conjunction c, CompilationContext ctx) {
        return unimplemented("Conjunction");
    }

    private CompiledExpression compileDisjunction(Disjunction d, CompilationContext ctx) {
        return unimplemented("Disjunction");
    }

    private CompiledExpression compileEagerConjunction(EagerConjunction ec, CompilationContext ctx) {
        return unimplemented("EagerConjunction");
    }

    private CompiledExpression compileEagerDisjunction(EagerDisjunction ed, CompilationContext ctx) {
        return unimplemented("EagerDisjunction");
    }

    private CompiledExpression compileExclusiveDisjunction(ExclusiveDisjunction xd, CompilationContext ctx) {
        return unimplemented("ExclusiveDisjunction");
    }

    private CompiledExpression compileSum(Sum s, CompilationContext ctx) {
        return unimplemented("Sum");
    }

    private CompiledExpression compileProduct(Product p, CompilationContext ctx) {
        return unimplemented("Product");
    }

    private CompiledExpression compileKeyStep(KeyStep ks, CompilationContext ctx) {
        return unimplemented("KeyStep");
    }

    private CompiledExpression compileIndexStep(IndexStep is, CompilationContext ctx) {
        return unimplemented("IndexStep");
    }

    private CompiledExpression compileWildcardStep(WildcardStep ws, CompilationContext ctx) {
        return unimplemented("WildcardStep");
    }

    private CompiledExpression compileSliceStep(SliceStep ss, CompilationContext ctx) {
        return unimplemented("SliceStep");
    }

    private CompiledExpression compileExpressionStep(ExpressionStep es, CompilationContext ctx) {
        return unimplemented("ExpressionStep");
    }

    private CompiledExpression compileConditionStep(ConditionStep cs, CompilationContext ctx) {
        return unimplemented("ConditionStep");
    }

    private CompiledExpression compileRecursiveKeyStep(RecursiveKeyStep rks, CompilationContext ctx) {
        return unimplemented("RecursiveKeyStep");
    }

    private CompiledExpression compileRecursiveIndexStep(RecursiveIndexStep ris, CompilationContext ctx) {
        return unimplemented("RecursiveIndexStep");
    }

    private CompiledExpression compileRecursiveWildcardStep(RecursiveWildcardStep rws, CompilationContext ctx) {
        return unimplemented("RecursiveWildcardStep");
    }

    private CompiledExpression compileIndexUnionStep(IndexUnionStep ius, CompilationContext ctx) {
        return unimplemented("IndexUnionStep");
    }

    private CompiledExpression compileAttributeUnionStep(AttributeUnionStep aus, CompilationContext ctx) {
        return unimplemented("AttributeUnionStep");
    }

    private StreamOperator compileEnvironmentAttribute(EnvironmentAttribute ea, CompilationContext ctx) {
        return AttributeCompiler.compileEnvironmentAttribute(ea, ctx);
    }

    private StreamOperator compileAttributeStep(AttributeStep as, CompilationContext ctx) {
        return AttributeCompiler.compileAttributeStep(as, ctx);
    }

    private Value unimplemented(String type) {
        return Value.error("UNIMPLEMENTED: %s", type);
    }

    private StreamOperator unimplementedStream(String type) {
        return new StreamOperator() {
            @Override
            public Flux<TracedValue> stream() {
                return Flux.just(new TracedValue(Value.error("UNIMPLEMENTED: %s", type), List.of()));
            }
        };
    }

}
