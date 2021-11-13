/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import org.reactivestreams.Publisher;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.grammar.sapl.Condition;
import io.sapl.grammar.sapl.Statement;
import io.sapl.grammar.sapl.ValueDefinition;
import io.sapl.interpreter.EvaluationContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Slf4j
public class PolicyBodyImplCustom extends PolicyBodyImpl {

	private static final String STATEMENT_NOT_BOOLEAN = "Evaluation error: Statement must evaluate to a boolean value, but was: '%s'.";

	/**
	 * Evaluates all statements of this policy body within the given evaluation context
	 * and returns a {@link Flux} of {@link Decision} objects.
	 * @param entitlement the entitlement of the enclosing policy.
	 * @param ctx the evaluation context in which the statements are evaluated. It must
	 * contain
	 * <ul>
	 * <li>the attribute context</li>
	 * <li>the function context</li>
	 * <li>the variable context holding the four authorization subscription variables
	 * 'subject', 'action', 'resource' and 'environment' combined with system variables
	 * from the PDP configuration and other variables e.g. obtained from the containing
	 * policy set</li>
	 * <li>the import mapping for functions and attribute finders</li>
	 * </ul>
	 * @return A {@link Flux} of {@link AuthorizationDecision} objects.
	 */
	@Override
	public Flux<Decision> evaluate(Decision entitlement, EvaluationContext ctx) {
		return Flux.just(Tuples.of(Val.TRUE, ctx)).concatMap(evaluateStatements(0)).map(Tuple2::getT1).map(val -> {
			if (val.isError()) {
				log.debug("Error evaluation statements: {}", val.getMessage());
				return Decision.INDETERMINATE;
			}
			if (val.getBoolean()) {
				return entitlement;
			}
			return Decision.NOT_APPLICABLE;
		});
	}

	protected Function<? super Tuple2<Val, EvaluationContext>, Publisher<? extends Tuple2<Val, EvaluationContext>>> evaluateStatements(
			int statementId) {
		if (statementId == statements.size()) {
			return Flux::just;
		}
		return previousAndContext -> evalStatement(previousAndContext.getT1(), statements.get(statementId),
				previousAndContext.getT2()).switchMap(evaluateStatements(statementId + 1));
	}

	private Flux<Tuple2<Val, EvaluationContext>> evalStatement(Val previousResult, Statement statement,
			EvaluationContext ctx) {
		if (statement instanceof ValueDefinition) {
			return evaluateValueDefinition(previousResult, (ValueDefinition) statement, ctx);
		}
		else {
			return evaluateCondition(previousResult, (Condition) statement, ctx);
		}
	}

	private Flux<Tuple2<Val, EvaluationContext>> evaluateValueDefinition(Val previousResult,
			ValueDefinition valueDefinition, EvaluationContext ctx) {
		if (previousResult.isError() || !previousResult.getBoolean()) {
			return Flux.just(Tuples.of(previousResult, ctx));
		}
		return valueDefinition.getEval().evaluate(ctx, Val.UNDEFINED)
				.concatMap(derivePolicyBodyScopeEvaluationContext(valueDefinition, ctx));
	}

	private Function<? super Val, ? extends Publisher<? extends Tuple2<Val, EvaluationContext>>> derivePolicyBodyScopeEvaluationContext(
			ValueDefinition valueDefinition, EvaluationContext ctx) {
		return evaluatedValue -> {
			if (evaluatedValue.isError()) {
				return Flux.just(Tuples.of(evaluatedValue, ctx));
			}
			if (evaluatedValue.isDefined()) {
				try {
					var scopedCtx = ctx.withEnvironmentVariable(valueDefinition.getName(), evaluatedValue.get());
					return Flux.just(Tuples.of(Val.TRUE, scopedCtx));
				}
				catch (PolicyEvaluationException e) {
					return Flux.just(Tuples.of(Val.error(e), ctx));
				}
			}
			return Flux.just(Tuples.of(Val.TRUE, ctx));
		};
	}

	protected Flux<Tuple2<Val, EvaluationContext>> evaluateCondition(Val previousResult, Condition condition,
			EvaluationContext ctx) {
		if (previousResult.isError() || !previousResult.getBoolean()) {
			return Flux.just(Tuples.of(previousResult, ctx));
		}
		return condition.getExpression().evaluate(ctx, Val.UNDEFINED).concatMap(statementResult -> {
			if (statementResult.isBoolean()) {
				return Flux.just(Tuples.of(statementResult, ctx));
			}
			else {
				return Flux.just(Tuples.of(Val.error(STATEMENT_NOT_BOOLEAN, statementResult), ctx));
			}
		});
	}

}
