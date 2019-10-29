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
