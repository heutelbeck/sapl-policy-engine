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

import java.util.ArrayList;
import java.util.Arrays;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.AuthorizationDecisionEvaluable;
import io.sapl.grammar.sapl.Policy;
import io.sapl.prp.PolicyRetrievalResult;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Slf4j
public class CombiningAlgorithmImplCustom extends CombiningAlgorithmImpl {

	@Override
	public Flux<AuthorizationDecision> combineMatchingDocuments(PolicyRetrievalResult policyRetrievalResult) {
		var matchingSaplDocuments = policyRetrievalResult.getMatchingDocuments();
		var authzDecisionFluxes   = new ArrayList<Flux<AuthorizationDecision>>(matchingSaplDocuments.size());
		for (AuthorizationDecisionEvaluable document : matchingSaplDocuments) {
			log.debug("  |- Evaluate: {} ", document);
			authzDecisionFluxes.add(document.evaluate());
		}
		if (matchingSaplDocuments.isEmpty()) {
			return Flux.just(combineDecisions(new AuthorizationDecision[0], policyRetrievalResult.isErrorsInTarget()));
		}
		return Flux
				.combineLatest(authzDecisionFluxes,
						decisions -> Arrays.copyOf(decisions, decisions.length, AuthorizationDecision[].class))
				.map(decisions -> combineDecisions(decisions, policyRetrievalResult.isErrorsInTarget()));
	}

	protected Flux<AuthorizationDecision> doCombinePolicies(Iterable<Policy> policies) {
		return Flux.fromIterable(policies)
				.concatMap(policy -> policy.matches().map(matches -> Tuples.of(matches, policy)))
				.reduce(new PolicyRetrievalResult(), (state, matchAndDocument) -> {
					PolicyRetrievalResult newState = state;
					if (isMatch(matchAndDocument))
						newState = state.withMatch(matchAndDocument.getT2());
					if (isError(matchAndDocument))
						newState = state.withError();
					return newState;
				}).flux().flatMap(policyRetrievalResult -> combineMatchingDocuments(policyRetrievalResult));
	}

	private boolean isMatch(Tuple2<Val, Policy> matchAndDocument) {
		return matchAndDocument.getT1().isBoolean() && matchAndDocument.getT1().getBoolean();
	}

	private boolean isError(Tuple2<Val, Policy> matchAndDocument) {
		return matchAndDocument.getT1().isError();
	}

	protected AuthorizationDecision combineDecisions(AuthorizationDecision[] decisions, boolean errorsInTarget) {
		// Implemented by sub-classes
		throw new UnsupportedOperationException();
	}

}
