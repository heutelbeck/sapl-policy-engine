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
import io.sapl.api.pdp.traced.AttributeRecord;
import io.sapl.api.pdp.traced.ConditionHit;
import io.sapl.ast.*;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.ExpressionCompiler;
import io.sapl.compiler.expressions.LazyNaryBooleanCompiler;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static io.sapl.compiler.BooleanGuardCompiler.applyBooleanGuard;

@UtilityClass
public class PolicyBodyCompiler {

    public static final String ERROR_ATTEMPT_TO_REDEFINE_VARIABLE = "Policy attempted to redefine variable '%s'.";
    public static final String ERROR_CONDITION_NON_BOOLEAN        = "Condition in policy body must return a Boolean value, but got: %s.";

    public CompiledPolicyBody compilePolicyBody(PolicyBody policyBody, CompilationContext ctx) {
        return new CompiledPolicyBody(compilePolicyBodyForProduction(policyBody, ctx),
                compilePolicyBodyWithCoverage(policyBody, ctx));
    }

    /**
     * Compiles a policy body into an executable expression.
     * <p>
     * Processes VarDef statements to register local variables and compiles
     * Condition statements into a lazy AND expression that short-circuits
     * on the first FALSE or ERROR result.
     *
     * @param body the policy body containing statements to compile
     * @param ctx the compilation context for variable and function resolution
     * @return a compiled expression that evaluates to TRUE, FALSE, or ERROR
     */
    public CompiledExpression compilePolicyBodyForProduction(PolicyBody body, CompilationContext ctx) {
        val statements = body.statements();
        if (statements.isEmpty()) {
            return Value.TRUE;
        }
        ctx.resetForNextPolicy();
        val conditions = new ArrayList<CompiledExpression>(statements.size());
        for (Statement statement : statements) {
            switch (statement) {
            case VarDef(var name, var value, var ignored1, var ignored2) -> {
                if (!ctx.addLocalPolicyVariable(name, ExpressionCompiler.compile(value, ctx))) {
                    return Value.errorAt(statement.location(), ERROR_ATTEMPT_TO_REDEFINE_VARIABLE, name);
                }
            }
            case Condition(var expression, var location)                 ->
                conditions.add(applyBooleanGuard(ExpressionCompiler.compile(expression, ctx), location,
                        ERROR_CONDITION_NON_BOOLEAN));
            }
        }
        if (conditions.isEmpty()) {
            return Value.TRUE;
        }

        return LazyNaryBooleanCompiler.compile(conditions, body.location(), true);
    }

    record IndexedCompiledCondition(int index, SourceLocation location, CompiledExpression expression) {}

    record StratifiedConditions(
            List<IndexedCompiledCondition> values,
            List<IndexedCompiledCondition> pures,
            List<IndexedCompiledCondition> streams) {}

    /**
     * Compiles a policy body with coverage tracking for test and analysis purposes.
     * <p>
     * Unlike {@link #compilePolicyBodyForProduction}, this method returns a
     * reactive stream that
     * tracks which conditions were evaluated and their results. Evaluation follows
     * stratified ordering: Values first, then PureOperators, then StreamOperators.
     *
     * @param body the policy body containing statements to compile
     * @param ctx the compilation context for variable and function resolution
     * @return a Flux emitting coverage results for each evaluation cycle
     */
    public Flux<TracedPolicyBodyResultAndCoverage> compilePolicyBodyWithCoverage(PolicyBody body,
            CompilationContext ctx) {
        val statements         = body.statements();
        val numberOfConditions = statements.stream().filter(Condition.class::isInstance).count();
        val compiledConditions = new ArrayList<IndexedCompiledCondition>();
        ctx.resetForNextPolicy();

        val earlyError = compileStatementsForCoverage(statements, ctx, compiledConditions, numberOfConditions);
        if (earlyError != null) {
            return earlyError;
        }

        if (compiledConditions.isEmpty()) {
            return Flux
                    .just(new TracedPolicyBodyResultAndCoverage(Value.TRUE, List.of(), List.of(), numberOfConditions));
        }

        val stratified = stratifyConditions(compiledConditions);
        val valueHits  = new ArrayList<ConditionHit>();

        val valueShortCircuit = evaluateValuesForCoverage(stratified.values(), valueHits, numberOfConditions);
        if (valueShortCircuit != null) {
            return valueShortCircuit;
        }

        return evaluatePuresAndStreamsForCoverage(stratified, valueHits, numberOfConditions);
    }

    private Flux<TracedPolicyBodyResultAndCoverage> compileStatementsForCoverage(List<Statement> statements,
            CompilationContext ctx, List<IndexedCompiledCondition> compiledConditions, long numberOfConditions) {
        for (Statement statement : statements) {
            switch (statement) {
            case VarDef(var name, var value, var ignored1, var ignored2) -> {
                if (!ctx.addLocalPolicyVariable(name, ExpressionCompiler.compile(value, ctx))) {
                    return Flux.just(new TracedPolicyBodyResultAndCoverage(
                            Value.errorAt(statement.location(), ERROR_ATTEMPT_TO_REDEFINE_VARIABLE, name), List.of(),
                            List.of(), numberOfConditions));
                }
            }
            case Condition(var expression, var location)                 -> {
                val conditionExpr = applyBooleanGuard(ExpressionCompiler.compile(expression, ctx), location,
                        ERROR_CONDITION_NON_BOOLEAN);
                compiledConditions
                        .add(new IndexedCompiledCondition(compiledConditions.size(), location, conditionExpr));
            }
            }
        }
        return null;
    }

    private StratifiedConditions stratifyConditions(List<IndexedCompiledCondition> compiledConditions) {
        val values  = new ArrayList<IndexedCompiledCondition>();
        val pures   = new ArrayList<IndexedCompiledCondition>();
        val streams = new ArrayList<IndexedCompiledCondition>();

        for (var cc : compiledConditions) {
            switch (cc.expression()) {
            case Value v          -> values.add(cc);
            case PureOperator p   -> pures.add(cc);
            case StreamOperator s -> streams.add(cc);
            }
        }
        return new StratifiedConditions(values, pures, streams);
    }

    private Flux<TracedPolicyBodyResultAndCoverage> evaluateValuesForCoverage(List<IndexedCompiledCondition> values,
            List<ConditionHit> valueHits, long numberOfConditions) {
        for (var v : values) {
            val val = (Value) v.expression();
            valueHits.add(new ConditionHit(v.index(), val, v.location()));
            if (shouldShortCircuit(val)) {
                return Flux.just(new TracedPolicyBodyResultAndCoverage(val, List.of(), valueHits, numberOfConditions));
            }
        }
        return null;
    }

    private Flux<TracedPolicyBodyResultAndCoverage> evaluatePuresAndStreamsForCoverage(StratifiedConditions stratified,
            List<ConditionHit> valueHits, long numberOfConditions) {
        return Flux.deferContextual(ctxView -> {
            val evalCtx = ctxView.get(EvaluationContext.class);
            val hits    = new ArrayList<>(valueHits);
            val attrs   = new ArrayList<AttributeRecord>();

            for (var p : stratified.pures()) {
                val pure = (PureOperator) p.expression();
                val val  = pure.evaluate(evalCtx);
                hits.add(new ConditionHit(p.index(), val, p.location()));
                if (shouldShortCircuit(val)) {
                    return Flux.just(new TracedPolicyBodyResultAndCoverage(val, attrs, hits, numberOfConditions));
                }
            }

            if (stratified.streams().isEmpty()) {
                return Flux.just(new TracedPolicyBodyResultAndCoverage(Value.TRUE, attrs, hits, numberOfConditions));
            }

            return chainStreamsWithCoverage(stratified.streams(), 0, hits, attrs, numberOfConditions);
        });
    }

    private boolean shouldShortCircuit(Value val) {
        return val instanceof ErrorValue || (val instanceof BooleanValue(var b) && !b);
    }

    private Flux<TracedPolicyBodyResultAndCoverage> chainStreamsWithCoverage(List<IndexedCompiledCondition> streams,
            int index, List<ConditionHit> accumulatedHits, List<AttributeRecord> accumulatedAttrs,
            long numberOfConditions) {

        if (index >= streams.size()) {
            return Flux.just(new TracedPolicyBodyResultAndCoverage(Value.TRUE, accumulatedAttrs, accumulatedHits,
                    numberOfConditions));
        }

        val current = streams.get(index);
        val stream  = (StreamOperator) current.expression();

        return stream.stream().switchMap(tv -> {
            val newHits = new ArrayList<>(accumulatedHits);
            newHits.add(new ConditionHit(current.index(), tv.value(), current.location()));

            val newAttrs = new ArrayList<>(accumulatedAttrs);
            newAttrs.addAll(tv.contributingAttributes());

            val val = tv.value();
            if (shouldShortCircuit(val)) {
                return Flux.just(new TracedPolicyBodyResultAndCoverage(val, newAttrs, newHits, numberOfConditions));
            }
            return chainStreamsWithCoverage(streams, index + 1, newHits, newAttrs, numberOfConditions);
        });
    }

}
