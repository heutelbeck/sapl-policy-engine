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

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.ExpressionResult;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.StreamExpressionResult;
import io.sapl.api.model.TracedValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.ast.*;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

import static io.sapl.compiler.ArithmeticOperators.unaryMinus;
import static io.sapl.compiler.ArithmeticOperators.unaryPlus;
import static io.sapl.compiler.BooleanOperators.not;

import java.util.List;

@UtilityClass
public class ExpressionEvaluator {

    public ExpressionResult evaluate(Expression expression, EvaluationContext ctx) {
        return switch (expression) {
        case Literal l           -> l.value();
        case Identifier id       -> ctx.get(id.name());
        case Parenthesized p     -> evaluate(p.expression(), ctx);
        case RelativeReference r -> evaluateRelativeReference(r, ctx);

        // Operations
        case BinaryOperation b -> evaluateBinaryOperation(b, ctx);
        case UnaryOperation u  -> evaluateUnaryOperation(u, ctx);

        // N-ary operations
        case ArrayExpression a       -> evaluateArrayExpression(a, ctx);
        case ObjectExpression o      -> evaluateObjectExpression(o, ctx);
        case Conjunction c           -> evaluateConjunction(c, ctx);
        case Disjunction d           -> evaluateDisjunction(d, ctx);
        case EagerConjunction ec     -> evaluateEagerConjunction(ec, ctx);
        case EagerDisjunction ed     -> evaluateEagerDisjunction(ed, ctx);
        case ExclusiveDisjunction xd -> evaluateExclusiveDisjunction(xd, ctx);
        case Sum s                   -> evaluateSum(s, ctx);
        case Product p               -> evaluateProduct(p, ctx);

        // Steps
        case KeyStep ks                -> evaluateKeyStep(ks, ctx);
        case IndexStep is              -> evaluateIndexStep(is, ctx);
        case WildcardStep ws           -> evaluateWildcardStep(ws, ctx);
        case SliceStep ss              -> evaluateSliceStep(ss, ctx);
        case ExpressionStep es         -> evaluateExpressionStep(es, ctx);
        case ConditionStep cs          -> evaluateConditionStep(cs, ctx);
        case RecursiveKeyStep rks      -> evaluateRecursiveKeyStep(rks, ctx);
        case RecursiveIndexStep ris    -> evaluateRecursiveIndexStep(ris, ctx);
        case RecursiveWildcardStep rws -> evaluateRecursiveWildcardStep(rws, ctx);
        case IndexUnionStep ius        -> evaluateIndexUnionStep(ius, ctx);
        case AttributeUnionStep aus    -> evaluateAttributeUnionStep(aus, ctx);

        // Functions and Filters
        case FunctionCall fc    -> evaluateFunctionCall(fc, ctx);
        case FilterOperation fo -> evaluateFilterOperation(fo, ctx);

        // Attributes (STREAM)
        case EnvironmentAttribute ea -> evaluateEnvironmentAttribute(ea, ctx);
        case AttributeStep as        -> evaluateAttributeStep(as, ctx);
        };
    }

    private Value evaluateRelativeReference(RelativeReference r, EvaluationContext ctx) {
        return switch (r.type()) {
        case VALUE    -> ctx.relativeValue();
        case LOCATION -> ctx.relativeLocation();
        };
    }

    private ExpressionResult evaluateBinaryOperation(BinaryOperation b, EvaluationContext ctx) {
        return unimplemented("BinaryOperation");
    }

    private ExpressionResult evaluateUnaryOperation(UnaryOperation u, EvaluationContext ctx) {
        var operandResult = evaluate(u.operand(), ctx);
        if (operandResult instanceof StreamExpressionResult)
            return unimplemented("UnaryOperation with stream operand");
        var operand = (Value) operandResult;
        return switch (u.op()) {
        case NOT    -> not(u, operand);
        case NEGATE -> unaryMinus(u, operand);
        case PLUS   -> unaryPlus(u, operand);
        };
    }

    private ExpressionResult evaluateArrayExpression(ArrayExpression a, EvaluationContext ctx) {
        var builder = ArrayValue.builder();
        for (var element : a.elements()) {
            var result = evaluate(element, ctx);
            if (result instanceof StreamExpressionResult)
                return unimplemented("ArrayExpression with stream element");
            if (result instanceof ErrorValue)
                return result;
            if (!(result instanceof UndefinedValue))
                builder.add((Value) result);
        }
        return builder.build();
    }

    private ExpressionResult evaluateObjectExpression(ObjectExpression o, EvaluationContext ctx) {
        var builder = ObjectValue.builder();
        for (var entry : o.entries()) {
            var result = evaluate(entry.value(), ctx);
            if (result instanceof StreamExpressionResult)
                return unimplemented("ObjectExpression with stream value");
            if (result instanceof ErrorValue)
                return result;
            if (!(result instanceof UndefinedValue))
                builder.put(entry.key(), (Value) result);
        }
        return builder.build();
    }

    private ExpressionResult evaluateFunctionCall(FunctionCall fc, EvaluationContext ctx) {
        return unimplemented("FunctionCall");
    }

    private ExpressionResult evaluateFilterOperation(FilterOperation fo, EvaluationContext ctx) {
        return unimplemented("FilterOperation");
    }

    private ExpressionResult evaluateConjunction(Conjunction c, EvaluationContext ctx) {
        return unimplemented("Conjunction");
    }

    private ExpressionResult evaluateDisjunction(Disjunction d, EvaluationContext ctx) {
        return unimplemented("Disjunction");
    }

    private ExpressionResult evaluateEagerConjunction(EagerConjunction ec, EvaluationContext ctx) {
        return unimplemented("EagerConjunction");
    }

    private ExpressionResult evaluateEagerDisjunction(EagerDisjunction ed, EvaluationContext ctx) {
        return unimplemented("EagerDisjunction");
    }

    private ExpressionResult evaluateExclusiveDisjunction(ExclusiveDisjunction xd, EvaluationContext ctx) {
        return unimplemented("ExclusiveDisjunction");
    }

    private ExpressionResult evaluateSum(Sum s, EvaluationContext ctx) {
        return unimplemented("Sum");
    }

    private ExpressionResult evaluateProduct(Product p, EvaluationContext ctx) {
        return unimplemented("Product");
    }

    private ExpressionResult evaluateKeyStep(KeyStep ks, EvaluationContext ctx) {
        return unimplemented("KeyStep");
    }

    private ExpressionResult evaluateIndexStep(IndexStep is, EvaluationContext ctx) {
        return unimplemented("IndexStep");
    }

    private ExpressionResult evaluateWildcardStep(WildcardStep ws, EvaluationContext ctx) {
        return unimplemented("WildcardStep");
    }

    private ExpressionResult evaluateSliceStep(SliceStep ss, EvaluationContext ctx) {
        return unimplemented("SliceStep");
    }

    private ExpressionResult evaluateExpressionStep(ExpressionStep es, EvaluationContext ctx) {
        return unimplemented("ExpressionStep");
    }

    private ExpressionResult evaluateConditionStep(ConditionStep cs, EvaluationContext ctx) {
        return unimplemented("ConditionStep");
    }

    private ExpressionResult evaluateRecursiveKeyStep(RecursiveKeyStep rks, EvaluationContext ctx) {
        return unimplemented("RecursiveKeyStep");
    }

    private ExpressionResult evaluateRecursiveIndexStep(RecursiveIndexStep ris, EvaluationContext ctx) {
        return unimplemented("RecursiveIndexStep");
    }

    private ExpressionResult evaluateRecursiveWildcardStep(RecursiveWildcardStep rws, EvaluationContext ctx) {
        return unimplemented("RecursiveWildcardStep");
    }

    private ExpressionResult evaluateIndexUnionStep(IndexUnionStep ius, EvaluationContext ctx) {
        return unimplemented("IndexUnionStep");
    }

    private ExpressionResult evaluateAttributeUnionStep(AttributeUnionStep aus, EvaluationContext ctx) {
        return unimplemented("AttributeUnionStep");
    }

    private StreamExpressionResult evaluateEnvironmentAttribute(EnvironmentAttribute ea, EvaluationContext ctx) {
        return unimplementedStream("EnvironmentAttribute");
    }

    private StreamExpressionResult evaluateAttributeStep(AttributeStep as, EvaluationContext ctx) {
        return unimplementedStream("AttributeStep");
    }

    private Value unimplemented(String type) {
        return Value.error("UNIMPLEMENTED: %s", type);
    }

    private StreamExpressionResult unimplementedStream(String type) {
        return new StreamExpressionResult(
                Flux.just(new TracedValue(Value.error("UNIMPLEMENTED: %s", type), List.of())));
    }

}
