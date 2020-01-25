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

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.EvaluationContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;

@Slf4j
public class PolicyElementImplCustom extends PolicyElementImpl {

	protected static final String CANNOT_ASSIGN_UNDEFINED_TO_A_VAL = "Cannot assign undefined to a val.";

	protected static final AuthorizationDecision INDETERMINATE = AuthorizationDecision.INDETERMINATE;

	private static final String CONDITION_NOT_BOOLEAN = "Evaluation error: Target condition must evaluate to a boolean value, but was: '%s'.";

	/**
	 * Checks whether the policy element (policy set or policy) matches an authorization
	 * subscription by evaluating the element's target expression. An import mapping and
	 * custom variables can be provided.
	 * @param ctx the evaluation context in which the policy element's target expression
	 * is be evaluated. It must contain
	 * <ul>
	 * <li>the function context, as functions can be used in the target expression</li>
	 * <li>the variable context holding the four authorization subscription variables
	 * 'subject', 'action', 'resource' and 'environment' combined with system variables
	 * from the PDP configuration and other variables e.g. obtained from the containing
	 * policy set</li>
	 * <li>the import mapping for functions</li>
	 * </ul>
	 * @return {@code true} if the target expression evaluates to {@code true},
	 * {@code false} otherwise.
	 * @throws PolicyEvaluationException in case there is an error while evaluating the
	 * target expression
	 */
	@Override
	public boolean matches(EvaluationContext ctx) throws PolicyEvaluationException {
		LOGGER.trace("| | |-- PolicyElement test match '{}'", getSaplName());
		final Expression targetExpression = getTargetExpression();
		if (targetExpression == null) {
			LOGGER.trace("| | | |-- MATCH (no target expression, matches all)");
			LOGGER.trace("| | |");
			return true;
		}
		else {
			try {
				final Optional<JsonNode> expressionResult = targetExpression.evaluate(ctx, false, Optional.empty())
						.blockFirst();
				if (expressionResult.isPresent() && expressionResult.get().isBoolean()) {
					LOGGER.trace("| | | |-- {}", expressionResult.get().asBoolean() ? "MATCH" : "NO MATCH");
					LOGGER.trace("| | |");
					return expressionResult.get().asBoolean();
				}
				else {
					LOGGER.trace("| | | |-- ERROR in target expression did not evaluate to boolean. Was: {}",
							expressionResult);
					LOGGER.trace("| | |");
					throw new PolicyEvaluationException(String.format(CONDITION_NOT_BOOLEAN, expressionResult));
				}
			}
			catch (RuntimeException fluxError) {
				LOGGER.trace("| | | |-- ERROR during target expression evaluation: {} ", fluxError.getMessage());
				LOGGER.trace("| | | |-- trace: ", fluxError);
				LOGGER.trace("| | |");

				final Throwable originalError = Exceptions.unwrap(fluxError);
				if (originalError instanceof PolicyEvaluationException) {
					throw (PolicyEvaluationException) originalError;
				}
				throw fluxError;
			}
		}
	}

}
