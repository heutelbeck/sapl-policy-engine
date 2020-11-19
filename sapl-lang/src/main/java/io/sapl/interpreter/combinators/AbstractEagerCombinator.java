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
package io.sapl.interpreter.combinators;

import java.util.ArrayList;
import java.util.List;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuples;

@Slf4j
public abstract class AbstractEagerCombinator implements DocumentsCombinator, PolicyCombinator {

	@Override
	public Flux<AuthorizationDecision> combineMatchingDocuments(PolicyRetrievalResult policyRetrievalResult,
			EvaluationContext evaluationCtx) {
		log.debug("|-- Combining matching documents");
		var matchingSaplDocuments = policyRetrievalResult.getMatchingDocuments();
		final List<Flux<AuthorizationDecision>> authzDecisionFluxes = new ArrayList<>(matchingSaplDocuments.size());
		for (SAPL document : matchingSaplDocuments) {
			log.debug("| |-- Evaluate: {} ({})", document.getPolicyElement().getSaplName(),
					document.getPolicyElement().getClass().getName());
			authzDecisionFluxes.add(document.evaluate(evaluationCtx));
		}
		if (matchingSaplDocuments == null || matchingSaplDocuments.isEmpty()) {
			return Flux.just(combineDecisions(new AuthorizationDecision[0], policyRetrievalResult.isErrorsInTarget()));
		}
		return Flux.combineLatest(authzDecisionFluxes,
				decisions -> combineDecisions(decisions, policyRetrievalResult.isErrorsInTarget()));
	}

	@Override
	public Flux<AuthorizationDecision> combinePolicies(List<Policy> policies, EvaluationContext ctx) {
		return Flux.fromIterable(policies)
				.concatMap(policy -> policy.matches(ctx).map(match -> Tuples.of(match, policy)))
				.reduce(Tuples.of(Boolean.FALSE, new ArrayList<Policy>(policies.size())), (state, match) -> {
					var newState = new ArrayList<>(state.getT2());
					if (match.getT1().isBoolean() && match.getT1().getBoolean()) {
						newState.add(match.getT2());
					}
					return Tuples.of(state.getT1() || match.getT1().isError(), newState);
				}).flux().concatMap(matching -> doCombine(matching.getT2(), matching.getT1(), ctx));
	}

	private Flux<AuthorizationDecision> doCombine(List<Policy> matchingPolicies, boolean errorsInTarget,
			EvaluationContext ctx) {
		log.debug("| |-- Combining {} policies", matchingPolicies.size());
		final List<Flux<AuthorizationDecision>> authzDecisionFluxes = new ArrayList<>(matchingPolicies.size());
		for (Policy policy : matchingPolicies) {
			authzDecisionFluxes.add(policy.evaluate(ctx));
		}
		if (matchingPolicies == null || matchingPolicies.isEmpty()) {
			return Flux.just(combineDecisions(new AuthorizationDecision[0], errorsInTarget));
		}
		return Flux.combineLatest(authzDecisionFluxes, decisions -> combineDecisions(decisions, errorsInTarget));
	}

	protected abstract AuthorizationDecision combineDecisions(Object[] decisions, boolean errorsInTarget);
}
