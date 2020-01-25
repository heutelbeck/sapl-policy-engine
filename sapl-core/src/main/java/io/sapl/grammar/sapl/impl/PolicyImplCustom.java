/**
 * Copyright © 2020 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.interpreter.EvaluationContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Slf4j
public class PolicyImplCustom extends PolicyImpl {

	private static final String OBLIGATIONS_ERROR = "Error occurred while evaluating obligations.";

	private static final String ADVICE_ERROR = "Error occurred while evaluating advice.";

	private static final String TRANSFORMATION_ERROR = "Error occurred while evaluating transformation.";

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static final String PERMIT = "permit";

	/**
	 * Evaluates the body of the policy within the given evaluation context and returns a
	 * {@link Flux} of {@link AuthorizationDecision} objects.
	 * @param ctx the evaluation context in which the policy's body is evaluated. It must
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
	public Flux<AuthorizationDecision> evaluate(EvaluationContext ctx) {
		final EvaluationContext policyCtx = ctx.copy();
		final Decision entitlement = PERMIT.equals(getEntitlement()) ? Decision.PERMIT : Decision.DENY;
		final Flux<Decision> decisionFlux = getBody() != null ? getBody().evaluate(entitlement, policyCtx)
				: Flux.just(entitlement);

		return decisionFlux.flatMap(decision -> {
			if (decision == Decision.PERMIT || decision == Decision.DENY) {
				return evaluateObligationsAndAdvice(policyCtx).map(obligationsAndAdvice -> {
					final Optional<ArrayNode> obligations = obligationsAndAdvice.getT1();
					final Optional<ArrayNode> advice = obligationsAndAdvice.getT2();
					return new AuthorizationDecision(decision, Optional.empty(), obligations, advice);
				});
			}
			else {
				return Flux.just(new AuthorizationDecision(decision));
			}
		}).flatMap(authzDecision -> {
			final Decision decision = authzDecision.getDecision();
			if (decision == Decision.PERMIT) {
				return evaluateTransformation(policyCtx).map(resource -> new AuthorizationDecision(decision, resource,
						authzDecision.getObligations(), authzDecision.getAdvices()));
			}
			else {
				return Flux.just(authzDecision);
			}
		}).onErrorReturn(INDETERMINATE);
	}

	private Flux<Tuple2<Optional<ArrayNode>, Optional<ArrayNode>>> evaluateObligationsAndAdvice(
			EvaluationContext evaluationCtx) {
		Flux<Optional<ArrayNode>> obligationsFlux;
		if (getObligation() != null) {
			final ArrayNode obligationArr = JSON.arrayNode();
			obligationsFlux = getObligation().evaluate(evaluationCtx, true, Optional.empty())
					.doOnError(error -> LOGGER.error(OBLIGATIONS_ERROR, error)).map(obligation -> {
						obligation.ifPresent(obligationArr::add);
						return obligationArr.size() > 0 ? Optional.of(obligationArr) : Optional.empty();
					});
		}
		else {
			obligationsFlux = Flux.just(Optional.empty());
		}

		Flux<Optional<ArrayNode>> adviceFlux;
		if (getAdvice() != null) {
			final ArrayNode adviceArr = JSON.arrayNode();
			adviceFlux = getAdvice().evaluate(evaluationCtx, true, Optional.empty())
					.doOnError(error -> LOGGER.error(ADVICE_ERROR, error)).map(advice -> {
						advice.ifPresent(adviceArr::add);
						return adviceArr.size() > 0 ? Optional.of(adviceArr) : Optional.empty();
					});
		}
		else {
			adviceFlux = Flux.just(Optional.empty());
		}

		return Flux.combineLatest(obligationsFlux, adviceFlux, Tuples::of);
	}

	private Flux<Optional<JsonNode>> evaluateTransformation(EvaluationContext evaluationCtx) {
		if (getTransformation() != null) {
			return getTransformation().evaluate(evaluationCtx, true, Optional.empty())
					.doOnError(error -> LOGGER.error(TRANSFORMATION_ERROR, error));
		}
		else {
			return Flux.just(Optional.empty());
		}
	}

}
