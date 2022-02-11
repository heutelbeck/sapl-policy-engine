/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.function.Function;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.grammar.sapl.Condition;
import io.sapl.grammar.sapl.ValueDefinition;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

@Slf4j
public class PolicyBodyImplCustom extends PolicyBodyImpl {

	private static final String STATEMENT_NOT_BOOLEAN = "Evaluation error: Statement must evaluate to a boolean value, but was: '%s'.";

	/**
	 * Evaluates all statements of this policy body within the given evaluation
	 * context and returns a {@link Flux} of {@link Decision} objects.
	 * 
	 * @param entitlement the entitlement of the enclosing policy.
	 * @return A {@link Flux} of {@link AuthorizationDecision} objects.
	 */
	@Override
	public Flux<Decision> evaluate(Decision entitlement) {
		return evaluateStatements(Val.TRUE, 0).map(toDecision(entitlement))
				.onErrorReturn(Decision.INDETERMINATE);
	}

	private Function<? super Val, Decision> toDecision(Decision entitlement) {
		return val -> {
			if (val.isError()) {
				log.trace("Error evaluating statements: {}", val.getMessage());
				return Decision.INDETERMINATE;
			}

			if (val.getBoolean())
				return entitlement;

			return Decision.NOT_APPLICABLE;
		};
	}

	protected Flux<Val> evaluateStatements(
			Val previousResult,
			int statementId) {
		if (previousResult.isError() || !previousResult.getBoolean() || statementId == statements.size())
			return Flux.just(previousResult);

		var statement = statements.get(statementId);

		if (statement instanceof ValueDefinition)
			return evaluateValueStatement(previousResult, statementId, (ValueDefinition) statement);

		return evaluateCondition(previousResult, (Condition) statement)
				.switchMap(newResult -> evaluateStatements(newResult, statementId + 1));
	}

	private Flux<Val> evaluateValueStatement(Val previousResult, int statementId, ValueDefinition valueDefinition) {
		var valueStream = valueDefinition.getEval().evaluate();
		return valueStream.switchMap(value -> evaluateStatements(previousResult, statementId + 1)
				.contextWrite(setVariable(valueDefinition.getName(), value)));
	}

	private Function<Context, Context> setVariable(String name, Val value) {
		return ctx -> AuthorizationContext.setVariable(ctx, name, value);
	}

	// protected to provide hook for test coverage calculations
	protected Flux<Val> evaluateCondition(
			Val previousResult,
			Condition condition) {
		return condition.getExpression().evaluate().map(this::assertConditionResultIsBooleanOrError);
	}

	private Val assertConditionResultIsBooleanOrError(Val conditionResult) {
		if (conditionResult.isBoolean() || conditionResult.isError())
			return conditionResult;

		return Val.error(STATEMENT_NOT_BOOLEAN, conditionResult);
	}

}
