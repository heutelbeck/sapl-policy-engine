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
package io.sapl.compiler.policy.policybody;

import io.sapl.api.model.*;
import io.sapl.ast.*;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.ExpressionCompiler;
import io.sapl.compiler.expressions.LazyNaryBooleanCompiler;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.policy.SchemaValidatorCompiler;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.jspecify.annotations.NonNull;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static io.sapl.compiler.policy.OperatorLiftUtil.liftToStream;
import static io.sapl.compiler.policy.policybody.BooleanGuardCompiler.applyBooleanGuard;

@UtilityClass
public class PolicyBodyCompiler {

    public static final String ERROR_ATTEMPT_TO_REDEFINE_VARIABLE_S = "Policy attempted to redefine variable '%s'.";
    public static final String ERROR_CONDITION_NON_BOOLEAN          = "Condition in policy body must return a Boolean value, but got: %s.";

    public CompiledPolicyBody compilePolicyBody(PolicyBody policyBody, CompilationContext ctx) {
        return new CompiledPolicyBody(compilePolicyBodyExpression(policyBody, ctx),
                compilePolicyBodyWithCoverage(policyBody, ctx));
    }

    public CompiledExpression compilePolicyBodyExpression(PolicyBody body, CompilationContext ctx) {
        val conditions = compileConditions(body.statements(), ctx).stream().map(IndexedCompiledCondition::expression)
                .toList();
        return LazyNaryBooleanCompiler.compile(conditions, body.location(), Value.FALSE, Value.TRUE);
    }

    record IndexedCompiledCondition(CompiledExpression expression, SourceLocation location, long statementId) {}

    public Flux<TracedPolicyBodyResultAndCoverage> compilePolicyBodyWithCoverage(PolicyBody body,
            CompilationContext ctx) {
        val       statements = body.statements();
        final var conditions = compileConditions(statements, ctx);
        if (conditions.isEmpty()) {
            val bodyResult = new TracedPolicyBodyResultAndCoverage(TracedValue.of(Value.TRUE),
                    new Coverage.BodyCoverage(List.of(), 0));
            return Flux.just(bodyResult);
        }

        // Fold values at compile time
        val numberOfConditions  = conditions.size();
        val remainingConditions = new ArrayList<IndexedCompiledCondition>();
        val hits                = new ArrayList<Coverage.ConditionHit>(conditions.size());
        for (var condition : conditions) {
            switch (condition.expression()) {
            case ErrorValue error           -> {
                hits.add(new Coverage.ConditionHit(error, condition.location(), condition.statementId()));
                val bodyResult = new TracedPolicyBodyResultAndCoverage(TracedValue.of(error),
                        new Coverage.BodyCoverage(hits, numberOfConditions));
                return Flux.just(bodyResult);
            }
            case BooleanValue(var b) when b -> /* identity - record hit only */
                hits.add(new Coverage.ConditionHit(Value.TRUE, condition.location(), condition.statementId()));
            case BooleanValue ignored       -> {
                hits.add(new Coverage.ConditionHit(Value.FALSE, condition.location(), condition.statementId()));
                val bodyResult = new TracedPolicyBodyResultAndCoverage(TracedValue.of(Value.FALSE),
                        new Coverage.BodyCoverage(hits, numberOfConditions));
                return Flux.just(bodyResult);
            }
            default                         ->
                remainingConditions.add(new IndexedCompiledCondition(liftToStream(condition.expression()),
                        condition.location(), condition.statementId()));
            }
        }

        if (remainingConditions.isEmpty()) {
            val bodyResult = new TracedPolicyBodyResultAndCoverage(TracedValue.of(Value.TRUE),
                    new Coverage.BodyCoverage(hits, numberOfConditions));
            return Flux.just(bodyResult);
        }
        return buildChain(remainingConditions, hits, numberOfConditions);
    }

    private Flux<TracedPolicyBodyResultAndCoverage> buildChain(List<IndexedCompiledCondition> conditions,
            List<Coverage.ConditionHit> hits, long numberOfConditions) {

        val                                     fallbackResult = new TracedPolicyBodyResultAndCoverage(
                TracedValue.of(Value.TRUE), new Coverage.BodyCoverage(hits, numberOfConditions));
        Flux<TracedPolicyBodyResultAndCoverage> chain          = Flux.just(fallbackResult);

        for (int i = conditions.size() - 1; i >= 0; i--) {
            val condition = conditions.get(i);
            val location  = condition.location();
            val next      = chain;
            chain = buildStreamLink(condition, next, location, new Coverage.BodyCoverage(hits, numberOfConditions));
        }
        return chain;
    }

    private Flux<TracedPolicyBodyResultAndCoverage> buildStreamLink(IndexedCompiledCondition condition,
            Flux<TracedPolicyBodyResultAndCoverage> next, SourceLocation location, Coverage.BodyCoverage coverage) {
        val stream = ((StreamOperator) condition.expression()).stream();
        return stream.switchMap(currentTracedValue -> {
            var currentValue                  = currentTracedValue.value();
            val currentContributingAttributes = currentTracedValue.contributingAttributes();
            val hit                           = new Coverage.ConditionHit(currentValue, location,
                    condition.statementId());
            if (currentValue instanceof ErrorValue) {
                val result = new TracedPolicyBodyResultAndCoverage(currentTracedValue, coverage.with(hit));
                return Flux.just(result);
            }
            if (currentValue instanceof BooleanValue(var b) && !b) {
                val result = new TracedPolicyBodyResultAndCoverage(
                        new TracedValue(Value.FALSE, currentContributingAttributes), coverage.with(hit));
                return Flux.just(result);
            }
            return next.map(nextTv -> {
                val value = LazyNaryBooleanCompiler.mergeAttributes(currentContributingAttributes, nextTv.value());
                val hits  = nextTv.bodyCoverage().with(hit);
                return new TracedPolicyBodyResultAndCoverage(value, hits);
            });
        });
    }

    private static @NonNull ArrayList<IndexedCompiledCondition> compileConditions(List<Statement> statements,
            CompilationContext ctx) {
        val conditions  = new ArrayList<IndexedCompiledCondition>(statements.size());
        var statementId = 0;
        if (statements.getFirst() instanceof SchemaCondition) {
            statementId = -1;
        }
        for (Statement statement : statements) {
            switch (statement) {
            case VarDef(var name, var value, var ignored, var location) -> {
                if (!ctx.addLocalPolicyVariable(name, ExpressionCompiler.compile(value, ctx))) {
                    throw new SaplCompilerException(ERROR_ATTEMPT_TO_REDEFINE_VARIABLE_S.formatted(name), location);
                }
            }
            case Condition(var expression, var location)                -> {
                val conditionExpression = applyBooleanGuard(ExpressionCompiler.compile(expression, ctx), location,
                        ERROR_CONDITION_NON_BOOLEAN);
                conditions.add(new IndexedCompiledCondition(conditionExpression, statement.location(), statementId));
            }
            case SchemaCondition(var schemas, var ignored)              -> {
                val conditionExpression = SchemaValidatorCompiler.compileValidator(schemas, ctx);
                conditions.add(new IndexedCompiledCondition(conditionExpression, statement.location(), statementId));
            }
            }
            statementId++;
        }
        return conditions;
    }

}
