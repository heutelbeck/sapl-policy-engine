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

import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.interpreter.EvaluationContext;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Slf4j
public class PolicyImplCustom extends PolicyImpl {

	private static final String PERMIT = "permit";

	/**
	 * Evaluates the body of the policy within the given evaluation context and
	 * returns a {@link Flux} of {@link AuthorizationDecision} objects.
	 * 
	 * @param ctx the evaluation context in which the policy's body is evaluated. It
	 *            must contain
	 *            <ul>
	 *            <li>the attribute context</li>
	 *            <li>the function context</li>
	 *            <li>the variable context holding the four authorization
	 *            subscription variables 'subject', 'action', 'resource' and
	 *            'environment' combined with system variables from the PDP
	 *            configuration and other variables e.g. obtained from the
	 *            containing policy set</li>
	 *            <li>the import mapping for functions and attribute finders</li>
	 *            </ul>
	 * @return A {@link Flux} of {@link AuthorizationDecision} objects.
	 */
	@Override
	public Flux<AuthorizationDecision> evaluate(@NonNull EvaluationContext ctx) {
		final EvaluationContext policyCtx = ctx.copy();
		final Decision entitlement = PERMIT.equals(getEntitlement()) ? Decision.PERMIT : Decision.DENY;
		final Flux<Decision> decisionFlux = getBody() != null ? getBody().evaluate(entitlement, policyCtx)
				: Flux.just(entitlement);

		return decisionFlux.switchMap(decision -> {
			if (decision == Decision.PERMIT || decision == Decision.DENY) {
				return evaluateObligationsAndAdvice(policyCtx).map(obligationAndAdvice -> {
					var obligation = obligationAndAdvice.getT1();
					var advice = obligationAndAdvice.getT2();
					if (obligation.isError()) {
						log.debug("| |- Error in obligation evaluation. INDETERMINATE: " + obligation.getMessage());
						return AuthorizationDecision.INDETERMINATE;
					}
					if (advice.isError()) {
						log.debug("| |- Error in advice evaluation. INDETERMINATE: " + advice.getMessage());
						return AuthorizationDecision.INDETERMINATE;
					}
					return new AuthorizationDecision(decision, Optional.empty(), wrapInArrayIfExists(obligation),
							wrapInArrayIfExists(advice));
				});
			} else {
				return Flux.just(new AuthorizationDecision(decision));
			}
		}).switchMap(authzDecision -> {
			var decision = authzDecision.getDecision();
			if (decision == Decision.PERMIT) {
				return evaluateTransformation(policyCtx).map(resource -> {
					if (resource.isEmpty()) {
						return authzDecision;
					}
					if (resource.get().isError()) {
						log.debug("| |- Error in resource evaluation. INDETERMINATE: " + resource.get());
						return AuthorizationDecision.INDETERMINATE;
					}
					if (resource.get().isUndefined()) {
						log.debug("| |- Error: Resource evaluated to 'undefined'. INDETERMINATE: " + resource.get());
						return AuthorizationDecision.INDETERMINATE;
					}
					return new AuthorizationDecision(decision, Optional.of(resource.get().get()),
							authzDecision.getObligations(), authzDecision.getAdvices());
				});
			} else {
				return Flux.just(authzDecision);
			}
		});
	}

	private Optional<ArrayNode> wrapInArrayIfExists(Val value) {
		if (value.isDefined()) {
			var array = Val.JSON.arrayNode();
			array.add(value.get());
			return Optional.of(array);
		}
		return Optional.empty();
	}

	private Flux<Tuple2<Val, Val>> evaluateObligationsAndAdvice(EvaluationContext evaluationCtx) {
		Flux<Val> obligationsFlux;
		if (getObligation() != null) {
			obligationsFlux = getObligation().evaluate(evaluationCtx, Val.UNDEFINED);
		} else {
			obligationsFlux = Val.undefinedFlux();
		}
		Flux<Val> adviceFlux;
		if (getAdvice() != null) {
			adviceFlux = getAdvice().evaluate(evaluationCtx, Val.UNDEFINED);
		} else {
			adviceFlux = Val.undefinedFlux();
		}
		return Flux.combineLatest(obligationsFlux, adviceFlux, Tuples::of);
	}

	private Flux<Optional<Val>> evaluateTransformation(EvaluationContext evaluationCtx) {
		if (getTransformation() == null) {
			return Flux.just(Optional.empty());
		}
		return getTransformation().evaluate(evaluationCtx, Val.UNDEFINED).map(Optional::of);
	}
}
