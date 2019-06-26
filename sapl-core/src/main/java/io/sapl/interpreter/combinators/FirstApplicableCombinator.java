package io.sapl.interpreter.combinators;

import java.util.ArrayList;
import java.util.List;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Response;
import io.sapl.grammar.sapl.Policy;
import io.sapl.interpreter.EvaluationContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class FirstApplicableCombinator implements PolicyCombinator {

	@Override
	public Flux<Response> combinePolicies(List<Policy> policies, EvaluationContext ctx) {
		final List<Policy> matchingPolicies = new ArrayList<>();
		for (Policy policy : policies) {
			try {
				if (policy.matches(ctx)) {
					matchingPolicies.add(policy);
					LOGGER.trace("Matching policy: {}", policy);
				}
			}
			catch (PolicyEvaluationException e) {
				return Flux.just(Response.indeterminate());
			}
		}

		if (matchingPolicies.isEmpty()) {
			return Flux.just(Response.notApplicable());
		}

		final List<Flux<Response>> responseFluxes = new ArrayList<>(
				matchingPolicies.size());
		for (Policy policy : matchingPolicies) {
			responseFluxes.add(policy.evaluate(ctx));
		}
		return Flux.combineLatest(responseFluxes, responses -> {
			for (Object response : responses) {
				final Response resp = (Response) response;
				if (resp.getDecision() != Decision.NOT_APPLICABLE) {
					return resp;
				}
			}
			return Response.notApplicable();
		}).distinctUntilChanged();
	}

}
