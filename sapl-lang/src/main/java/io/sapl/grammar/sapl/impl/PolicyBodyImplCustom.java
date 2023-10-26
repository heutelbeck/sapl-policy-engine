/*
 * Streaming Attribute Policy Language (SAPL) Engine
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.sapl.impl;

import java.util.Map;
import java.util.function.Function;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.grammar.sapl.Condition;
import io.sapl.grammar.sapl.PolicyBody;
import io.sapl.grammar.sapl.ValueDefinition;
import io.sapl.interpreter.context.AuthorizationContext;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

public class PolicyBodyImplCustom extends PolicyBodyImpl {

    private static final String STATEMENT_NOT_BOOLEAN_ERROR = "Evaluation error: Each condition in 'where' must evaluate to a boolean value. Got: '%s'.";

    /**
     * Evaluates all statements of this policy body within the given evaluation
     * context and returns a {@link Flux} of {@link Decision} objects.
     * 
     * @return A {@link Flux} of {@link AuthorizationDecision} objects.
     */
    @Override
    public Flux<Val> evaluate() {
        return evaluateStatements(Val.TRUE.withTrace(PolicyBody.class), 0);
    }

    protected Flux<Val> evaluateStatements(Val previousResult, int statementId) {
        if (previousResult.isError() || !previousResult.getBoolean() || statementId == statements.size())
            return Flux.just(previousResult.withTrace(PolicyBody.class,
                    Map.of(Trace.PREVIOUS_CONDITION_RESULT, previousResult)));

        var statement = statements.get(statementId);

        if (statement instanceof ValueDefinition valueDefinition)
            return evaluateValueStatement(previousResult, statementId, valueDefinition);

        return evaluateCondition(previousResult, (Condition) statement)
                .switchMap(newResult -> evaluateStatements(newResult, statementId + 1));
    }

    private Flux<Val> evaluateValueStatement(Val previousResult, int statementId, ValueDefinition valueDefinition) {
        var valueStream = valueDefinition.getEval().evaluate().map(
                val -> val.withTrace(PolicyBody.class, Map.of(Trace.VARIABLE_NAME, Val.of(valueDefinition.getName()))));
        return valueStream.switchMap(value -> evaluateStatements(previousResult, statementId + 1)
                .contextWrite(setVariable(valueDefinition.getName(), value)));
    }

    private Function<Context, Context> setVariable(String name, Val value) {
        return ctx -> AuthorizationContext.setVariable(ctx, name, value);
    }

    // protected to provide hook for test coverage calculations
    protected Flux<Val> evaluateCondition(Val previousResult, Condition condition) {
        return condition.getExpression().evaluate().map(this::assertConditionResultIsBooleanOrError);
    }

    private Val assertConditionResultIsBooleanOrError(Val conditionResult) {
        if (conditionResult.isBoolean() || conditionResult.isError())
            return conditionResult;

        return Val.error(STATEMENT_NOT_BOOLEAN_ERROR, conditionResult).withTrace(PolicyBody.class,
                Map.of(Trace.PREVIOUS_CONDITION_RESULT, conditionResult));
    }

}
