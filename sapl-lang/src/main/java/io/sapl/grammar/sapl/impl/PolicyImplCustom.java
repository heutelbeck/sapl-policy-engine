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

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
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
		return Flux.just(getEntitlement().getDecision()).concatMap(evaluateBody()).map(AuthorizationDecision::new)
				.concatMap(addObligation()).concatMap(addAdvice()).concatMap(addResource())
				.doOnNext(authzDecision -> log.debug("  |     |- {} '{}': {}", authzDecision.getDecision(), saplName,
						authzDecision));
	}

	private Function<? super Decision, Publisher<? extends Decision>> evaluateBody() {
		return entitlement -> getBody() == null ? Flux.just(entitlement) : getBody().evaluate(entitlement);

	}

	private Function<? super AuthorizationDecision, Publisher<? extends AuthorizationDecision>> addObligation() {
		return previousDecision -> evaluateObligations().map(obligation -> {
			if (obligation.isError()) {
				log.debug("  |     |- Error in obligation evaluation. INDETERMINATE: " + obligation.getMessage());
				return AuthorizationDecision.INDETERMINATE;
			}
			if (obligation.isUndefined()) {
				log.debug("  |     |- Undefined obligation. INDETERMINATE");
				return AuthorizationDecision.INDETERMINATE;
			}
			var obligationArray = Val.JSON.arrayNode();
			obligationArray.add(obligation.get());
			return previousDecision.withObligations(obligationArray);
		}).defaultIfEmpty(previousDecision);

	}

	private Function<? super AuthorizationDecision, Publisher<? extends AuthorizationDecision>> addAdvice() {
		return previousDecision -> evaluateAdvice().map(advice -> {
			if (advice.isError()) {
				log.debug("  |     |- Error in advice evaluation. INDETERMINATE: " + advice.getMessage());
				return AuthorizationDecision.INDETERMINATE;
			}
			if (advice.isUndefined()) {
				log.debug("  |     |- Undefined advice. INDETERMINATE");
				return AuthorizationDecision.INDETERMINATE;
			}
			var adviceValue = advice.get();
			log.debug("  |     |- Got advice: {}", adviceValue);
			var adviceArray = Val.JSON.arrayNode();
			adviceArray.add(adviceValue);
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

	private Flux<Val> evaluateObligations() {
		if (getObligation() == null) {
			return Flux.empty();
		}
		return getObligation().evaluate(Val.UNDEFINED);
	}

	private Flux<Val> evaluateAdvice() {
		if (getAdvice() == null) {
			return Flux.empty();
		}
		return getAdvice().evaluate(Val.UNDEFINED);
	}

	private Flux<Val> evaluateTransformation() {
		if (getTransformation() == null) {
			return Flux.empty();
		}
		return getTransformation().evaluate(Val.UNDEFINED);
	}

}
