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

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.EList;
import org.reactivestreams.Publisher;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.grammar.sapl.Expression;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class PolicyImplCustom extends PolicyImpl {

	/**
	 * Evaluates the body of the policy within the given evaluation context and
	 * returns a {@link Flux} of {@link AuthorizationDecision} objects.
	 * 
	 * @return A {@link Flux} of {@link AuthorizationDecision} objects.
	 */
	@Override
	public Flux<AuthorizationDecision> evaluate() {
		log.debug("  |  |- Evaluate '{}'", saplName);
		// @formatter:off
		return Flux.just(getEntitlement().getDecision())
				.concatMap(evaluateBody())
				.map(AuthorizationDecision::new)
				.switchMap(addConstraintsIfPolicyIsApplicable())
				.doOnNext(authzDecision -> log.debug("  |     |- {} '{}': {}", authzDecision.getDecision(), saplName, authzDecision));
		// @formatter:on
	}

	private Function<? super AuthorizationDecision, Publisher<? extends AuthorizationDecision>> addConstraintsIfPolicyIsApplicable() {
		return authzDecision -> {
			var justTheDecision = Flux.just(authzDecision);
			if (decisionMayNotCarryConstraints(authzDecision))
				return justTheDecision;

			return justTheDecision.concatMap(addObligation()).concatMap(addAdvice()).concatMap(addResource());
		};
	}

	private boolean decisionMayNotCarryConstraints(AuthorizationDecision authzDecision) {
		return authzDecision.getDecision() == Decision.INDETERMINATE
				|| authzDecision.getDecision() == Decision.NOT_APPLICABLE;
	}

	private Function<? super Decision, Publisher<? extends Decision>> evaluateBody() {
		return entitlement -> getBody() == null ? Flux.just(entitlement) : getBody().evaluate(entitlement);
	}

	private Function<? super AuthorizationDecision, Publisher<? extends AuthorizationDecision>> addObligation() {
		return previousDecision -> evaluateObligations().map(obligations -> {
			var obligationArray = Val.JSON.arrayNode();
			for (var obligation : obligations) {
				if (obligation.isError()) {
					log.debug("  |     |- Error in obligation evaluation. INDETERMINATE: " + obligation.getMessage());
					return AuthorizationDecision.INDETERMINATE;
				}
				if (obligation.isUndefined()) {
					log.debug("  |     |- Undefined obligation. INDETERMINATE");
					return AuthorizationDecision.INDETERMINATE;
				}
				log.debug("  |     |- Got obligation: {}", obligation);
				obligationArray.add(obligation.get());
			}
			return previousDecision.withObligations(obligationArray);
		}).defaultIfEmpty(previousDecision);

	}

	private Function<? super AuthorizationDecision, Publisher<? extends AuthorizationDecision>> addAdvice() {
		return previousDecision -> evaluateAdvice().map(advice -> {
			var adviceArray = Val.JSON.arrayNode();
			for (var anAdvice : advice) {

				if (anAdvice.isError()) {
					log.debug("  |     |- Error in advice evaluation. INDETERMINATE: " + anAdvice.getMessage());
					return AuthorizationDecision.INDETERMINATE;
				}
				if (anAdvice.isUndefined()) {
					log.debug("  |     |- Undefined advice. INDETERMINATE");
					return AuthorizationDecision.INDETERMINATE;
				}
				log.debug("  |     |- Got advice: {}", anAdvice);
				adviceArray.add(anAdvice.get());
			}
			return previousDecision.withAdvice(adviceArray);
		}).defaultIfEmpty(previousDecision);
	}

	private Function<? super AuthorizationDecision, Publisher<? extends AuthorizationDecision>> addResource() {
		return previousDecision -> evaluateTransformation().map(transformation -> {
			if (transformation.isError()) {
				log.debug(
						"  |     |- Error in transformation evaluation. INDETERMINATE: " + transformation.getMessage());
				return AuthorizationDecision.INDETERMINATE;
			}
			if (transformation.isUndefined()) {
				log.debug("  |     |- Undefined transformation. INDETERMINATE");
				return AuthorizationDecision.INDETERMINATE;
			}
			return previousDecision.withResource(transformation.get());
		}).defaultIfEmpty(previousDecision);
	}

	private Flux<Val[]> evaluateObligations() {
		return evaluateConstraints(getObligations());
	}

	private Flux<Val[]> evaluateAdvice() {
		return evaluateConstraints(getAdvice());
	}

	private Flux<Val[]> evaluateConstraints(EList<Expression> constraints) {
		if (constraints == null)
			return Flux.empty();
		var evaluatedConstraints = constraints.stream().map(Expression::evaluate).collect(Collectors.toList());
		return Flux.combineLatest(evaluatedConstraints, v -> Arrays.copyOf(v, v.length, Val[].class));
	}

	private Flux<Val> evaluateTransformation() {
		if (getTransformation() == null) {
			return Flux.empty();
		}
		return getTransformation().evaluate();
	}

}
