/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.EList;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.grammar.sapl.Condition;
import io.sapl.grammar.sapl.Statement;
import io.sapl.grammar.sapl.ValueDefinition;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.FluxProvider;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class PolicyBodyImplCustom extends PolicyBodyImpl {

	private static final String STATEMENT_NOT_BOOLEAN = "Evaluation error: Statement must evaluate to a boolean value, but was: '%s'.";

	protected static final String CANNOT_ASSIGN_UNDEFINED_TO_A_VAL = "Cannot assign undefined to a val.";

	/**
	 * Evaluates all statements of this policy body within the given evaluation
	 * context and returns a {@link Flux} of {@link Decision} objects.
	 * 
	 * @param entitlement the entitlement of the enclosing policy.
	 * @param ctx         the evaluation context in which the statements are
	 *                    evaluated. It must contain
	 *                    <ul>
	 *                    <li>the attribute context</li>
	 *                    <li>the function context</li>
	 *                    <li>the variable context holding the four authorization
	 *                    subscription variables 'subject', 'action', 'resource' and
	 *                    'environment' combined with system variables from the PDP
	 *                    configuration and other variables e.g. obtained from the
	 *                    containing policy set</li>
	 *                    <li>the import mapping for functions and attribute
	 *                    finders</li>
	 *                    </ul>
	 * @return A {@link Flux} of {@link AuthorizationDecision} objects.
	 */
	@Override
	public Flux<Decision> evaluate(Decision entitlement, EvaluationContext ctx) {
		final EList<Statement> statements = getStatements();
		if (statements != null && !statements.isEmpty()) {
			final List<FluxProvider<Boolean>> fluxProviders = new ArrayList<>(statements.size());
			for (Statement statement : statements) {
				fluxProviders.add(currentResult -> evaluateStatement(statement, ctx));
			}
			// return sequentialSwitchMap(Boolean.TRUE, fluxProviders)
			return nestedSwitchMap(Boolean.TRUE, fluxProviders, 0)
					.map(result -> result ? entitlement : Decision.NOT_APPLICABLE).onErrorResume(error -> {
						log.debug("Error in policy body evaluation: {}", error.getMessage());
						return Flux.just(Decision.INDETERMINATE);
					});
		} else {
			return Flux.just(entitlement);
		}
	}

	private Flux<Boolean> nestedSwitchMap(Boolean currentResult, List<FluxProvider<Boolean>> fluxProviders, int idx) {
		if (idx < fluxProviders.size() && currentResult) {
			return fluxProviders.get(idx).getFlux(Boolean.TRUE)
					.switchMap(result -> nestedSwitchMap(result, fluxProviders, idx + 1));
		}
		return Flux.just(currentResult);
	}

	private Flux<Boolean> evaluateStatement(Statement statement, EvaluationContext evaluationCtx) {
		if (statement instanceof ValueDefinition) {
			return evaluateValueDefinition((ValueDefinition) statement, evaluationCtx);
		} else {
			return evaluateCondition((Condition) statement, evaluationCtx);
		}
	}

	private Flux<Boolean> evaluateValueDefinition(ValueDefinition valueDefinition, EvaluationContext evaluationCtx) {
		return valueDefinition.getEval().evaluate(evaluationCtx, Val.undefined()).concatMap(evaluatedValue -> {
			try {
				if (evaluatedValue.isDefined()) {
					evaluationCtx.getVariableCtx().put(valueDefinition.getName(), evaluatedValue.get());
					return Flux.just(Boolean.TRUE);
				} else {
					return Flux.error(new PolicyEvaluationException(CANNOT_ASSIGN_UNDEFINED_TO_A_VAL));
				}
			} catch (PolicyEvaluationException e) {
				log.debug("Error in value definition evaluation: {}", e.getMessage());
				return Flux.error(e);
			}
		});
	}

	private Flux<Boolean> evaluateCondition(Condition condition, EvaluationContext evaluationCtx) {
		return condition.getExpression().evaluate(evaluationCtx, Val.undefined()).concatMap(statementResult -> {
			if (statementResult.isDefined() && statementResult.get().isBoolean()) {
				return Flux.just(statementResult.get().asBoolean());
			} else {
				return Flux.error(new PolicyEvaluationException(STATEMENT_NOT_BOOLEAN, statementResult));
			}
		});
	}

}
