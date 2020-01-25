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
package io.sapl.interpreter.combinators;

import java.util.ArrayList;
import java.util.List;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.Policy;
import io.sapl.interpreter.EvaluationContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class FirstApplicableCombinator implements PolicyCombinator {

	@Override
	public Flux<AuthorizationDecision> combinePolicies(List<Policy> policies, EvaluationContext ctx) {
		final List<Policy> matchingPolicies = new ArrayList<>();
		for (Policy policy : policies) {
			try {
				if (policy.matches(ctx)) {
					matchingPolicies.add(policy);
					LOGGER.trace("Matching policy: {}", policy);
				}
			}
			catch (PolicyEvaluationException e) {
				return Flux.just(AuthorizationDecision.INDETERMINATE);
			}
		}

		if (matchingPolicies.isEmpty()) {
			return Flux.just(AuthorizationDecision.NOT_APPLICABLE);
		}

		final List<Flux<AuthorizationDecision>> authzDecisionFluxes = new ArrayList<>(matchingPolicies.size());
		for (Policy policy : matchingPolicies) {
			authzDecisionFluxes.add(policy.evaluate(ctx));
		}
		return Flux.combineLatest(authzDecisionFluxes, authzDecisions -> {
			for (Object authzDecision : authzDecisions) {
				if (((AuthorizationDecision) authzDecision).getDecision() != Decision.NOT_APPLICABLE) {
					return (AuthorizationDecision) authzDecision;
				}
			}
			return AuthorizationDecision.NOT_APPLICABLE;
		}).distinctUntilChanged();
	}

}
