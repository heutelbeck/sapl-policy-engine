package io.sapl.interpreter.combinators;

import java.util.ArrayList;
import java.util.List;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.AuthDecision;
import io.sapl.grammar.sapl.Policy;
import io.sapl.interpreter.EvaluationContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class FirstApplicableCombinator implements PolicyCombinator {

	@Override
	public Flux<AuthDecision> combinePolicies(List<Policy> policies, EvaluationContext ctx) {
		final List<Policy> matchingPolicies = new ArrayList<>();
		for (Policy policy : policies) {
			try {
				if (policy.matches(ctx)) {
					matchingPolicies.add(policy);
					LOGGER.trace("Matching policy: {}", policy);
				}
			}
			catch (PolicyEvaluationException e) {
				return Flux.just(AuthDecision.INDETERMINATE);
			}
		}

		if (matchingPolicies.isEmpty()) {
			return Flux.just(AuthDecision.NOT_APPLICABLE);
		}

		final List<Flux<AuthDecision>> authDecisionFluxes = new ArrayList<>(matchingPolicies.size());
		for (Policy policy : matchingPolicies) {
			authDecisionFluxes.add(policy.evaluate(ctx));
		}
		return Flux.combineLatest(authDecisionFluxes, authDecisions -> {
			for (Object authDecision : authDecisions) {
				if (((AuthDecision) authDecision).getDecision() != Decision.NOT_APPLICABLE) {
					return (AuthDecision) authDecision;
				}
			}
			return AuthDecision.NOT_APPLICABLE;
		}).distinctUntilChanged();
	}

}
