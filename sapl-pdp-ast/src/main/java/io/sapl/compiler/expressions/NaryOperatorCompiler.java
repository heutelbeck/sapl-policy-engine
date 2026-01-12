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
import io.sapl.api.model.AttributeRecord;
import io.sapl.ast.ExclusiveDisjunction;
import io.sapl.ast.Expression;
import io.sapl.ast.Product;
import io.sapl.ast.Sum;
import io.sapl.compiler.operators.ArithmeticOperators;
import io.sapl.compiler.operators.BooleanOperators;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiles N-ary operators: ExclusiveDisjunction (XOR), Sum, Product.
 * <p>
 * These operators cannot short-circuit (all operands must be evaluated),
 * but we still apply cost-stratified optimization:
 * <ul>
 * <li>Fold all Value operands at compile time</li>
 * <li>Evaluate Pure operands before subscribing to Streams</li>
 * <li>Return errors early without unnecessary evaluation/subscription</li>
 * </ul>
 * <p>
 * Cost strata (cheapest first): Value, Pure, Stream
 * <p>
 * Since these operations are commutative and associative, operands can be
 * safely reordered by strata and folded in any order.
 */
@UtilityClass
public class NaryOperatorCompiler {

    private static final String ERROR_EMPTY_NARY_EXPRESSION = "Empty N-ary expression.";

    public CompiledExpression compileXor(ExclusiveDisjunction xd, CompilationContext ctx) {
        return compile(xd.operands(), ctx, xd.location(), BooleanOperators::xor);
    }

    public CompiledExpression compileSum(Sum s, CompilationContext ctx) {
        return compile(s.operands(), ctx, s.location(), ArithmeticOperators::add);
    }

    public CompiledExpression compileProduct(Product p, CompilationContext ctx) {
        return compile(p.operands(), ctx, p.location(), ArithmeticOperators::multiply);
    }

    private CompiledExpression compile(List<Expression> operands, CompilationContext ctx, SourceLocation location,
            BinaryOperation op) {

        // Compile all operands and categorize by strata
        var values  = new ArrayList<Value>();
        var pures   = new ArrayList<PureOperator>();
        var streams = new ArrayList<StreamOperator>();

        for (var operand : operands) {
            var compiled = ExpressionCompiler.compile(operand, ctx);
            switch (compiled) {
            case Value v          -> values.add(v);
            case PureOperator p   -> pures.add(p);
            case StreamOperator s -> streams.add(s);
            }
        }

        // Fold values at compile time (cheapest stratum)
        Value valueResult = foldValues(values, op, location);
        if (valueResult instanceof ErrorValue) {
            return valueResult; // Compile-time error - no need to evaluate anything else
        }

        // Determine return type based on remaining strata
        if (pures.isEmpty() && streams.isEmpty()) {
            // All values - return folded result
            return valueResult != null ? valueResult : Value.error(ERROR_EMPTY_NARY_EXPRESSION);
        }

        if (streams.isEmpty()) {
            // Values + Pures only: return PureOperator
            boolean dependsOnSubscription = pures.stream().anyMatch(PureOperator::isDependingOnSubscription);
            return new NaryPure(op, valueResult, pures, location, dependsOnSubscription);
        }

        // Has streams: return StreamOperator
        return new NaryStream(op, valueResult, pures, streams, location);
    }

    /**
     * Fold all Value operands at compile time.
     *
     * @return null if values is empty, the folded result otherwise.
     * Returns ErrorValue if any value is an error or type mismatch occurs.
     */
    private Value foldValues(List<Value> values, BinaryOperation op, SourceLocation location) {
        if (values.isEmpty()) {
            return null;
        }
        Value result = values.getFirst();
        if (result instanceof ErrorValue) {
            return result;
        }
        for (int i = 1; i < values.size(); i++) {
            result = op.apply(result, values.get(i), location);
            if (result instanceof ErrorValue) {
                return result;
            }
        }
        return result;
    }

    /**
     * Evaluates pure operators and folds them with an initial value using the given
     * operation.
     *
     * @return the folded result (may be null if initial is null and pures is
     * empty),
     * or ErrorValue if evaluation fails
     */
    private static Value evaluateAndFoldPures(Value initial, List<PureOperator> pures, BinaryOperation op,
            SourceLocation location, EvaluationContext ctx) {
        Value result = initial;
        for (var pure : pures) {
            var pv = pure.evaluate(ctx);
            if (pv instanceof ErrorValue) {
                return pv;
            }
            result = result == null ? pv : op.apply(result, pv, location);
            if (result instanceof ErrorValue) {
                return result;
            }
        }
        return result;
    }

    /**
     * Folds stream values with an initial value, collecting attributes along the
     * way.
     *
     * @return TracedValue with folded result and accumulated attributes
     */
    private static TracedValue foldStreamValues(Value initial, Object[] emittedValues, BinaryOperation op,
            SourceLocation location) {
        var   attributes = new ArrayList<AttributeRecord>();
        Value result     = initial;

        for (var obj : emittedValues) {
            var tv = (TracedValue) obj;
            attributes.addAll(tv.contributingAttributes());
            var v = tv.value();
            if (v instanceof ErrorValue) {
                return new TracedValue(v, attributes);
            }
            result = result == null ? v : op.apply(result, v, location);
            if (result instanceof ErrorValue) {
                return new TracedValue(result, attributes);
            }
        }
        return new TracedValue(result, attributes);
    }

    /**
     * N-ary operation with only Value and Pure operands (no streams).
     * <p>
     * At runtime: evaluates all pures, folding with the pre-computed valueResult.
     * Returns early on any error (type mismatch or propagated error).
     */
    record NaryPure(
            BinaryOperation op,
            Value valueResult,
            List<PureOperator> pures,
            SourceLocation location,
            boolean isDependingOnSubscription) implements PureOperator {

        @Override
        public Value evaluate(EvaluationContext ctx) {
            return evaluateAndFoldPures(valueResult, pures, op, location, ctx);
        }
    }

    /**
     * N-ary operation with at least one Stream operand.
     * <p>
     * At runtime:
     * 1. Evaluates all pures first (before subscribing to any streams)
     * 2. If pure evaluation fails, returns error without stream subscription
     * 3. Subscribes to all streams with combineLatest
     * 4. Folds stream values with the pre-combined result from values+pures
     */
    record NaryStream(
            BinaryOperation op,
            Value valueResult,
            List<PureOperator> pures,
            List<StreamOperator> streams,
            SourceLocation location) implements StreamOperator {

        @Override
        public Flux<TracedValue> stream() {
            return Flux.deferContextual(ctx -> {
                val evalCtx = ctx.get(EvaluationContext.class);

                // Evaluate pures first (before subscribing to streams)
                var preCombined = evaluateAndFoldPures(valueResult, pures, op, location, evalCtx);
                if (preCombined instanceof ErrorValue) {
                    return Flux.just(new TracedValue(preCombined, List.of()));
                }

                // Subscribe to all streams with combineLatest
                var streamFluxes     = streams.stream().map(StreamOperator::stream).toList();
                val finalPreCombined = preCombined;

                return Flux.combineLatest(streamFluxes,
                        emittedValues -> foldStreamValues(finalPreCombined, emittedValues, op, location));
            });
        }
    }
}
