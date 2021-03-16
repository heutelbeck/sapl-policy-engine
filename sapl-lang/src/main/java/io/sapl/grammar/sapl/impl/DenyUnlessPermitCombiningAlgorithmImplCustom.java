package io.sapl.grammar.sapl.impl;

import static io.sapl.api.pdp.Decision.DENY;
import static io.sapl.api.pdp.Decision.PERMIT;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.Policy;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.combinators.ObligationAdviceCollector;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * This strict algorithm is used if the decision should be DENY except for there
 * is a PERMIT. It ensures that any decision is either DENY or PERMIT.
 * 
 * It works as follows:
 * 
 * - If any policy document evaluates to PERMIT and there is no transformation
 * uncertainty (multiple policies evaluate to PERMIT and at least one of them
 * has a transformation statement), the decision is PERMIT.
 * 
 * - Otherwise the decision is DENY.
 */
@Slf4j
public class DenyUnlessPermitCombiningAlgorithmImplCustom extends DenyUnlessPermitCombiningAlgorithmImpl {

	@Override
	protected AuthorizationDecision combineDecisions(AuthorizationDecision[] decisions, boolean errorsInTarget) {
		if (decisions == null || decisions.length == 0) {
			return AuthorizationDecision.DENY;
		}
		var entitlement = DENY;
		var collector = new ObligationAdviceCollector();
		Optional<JsonNode> resource = Optional.empty();
		for (var decision : decisions) {
			if (decision.getDecision() == PERMIT) {
				entitlement = PERMIT;
			}
			collector.add(decision);
			if (decision.getResource().isPresent()) {
				if (resource.isPresent()) {
					// this is a transformation uncertainty.
					// another policy already defined a transformation
					// this the overall result is basically INDETERMINATE.
					// However, DENY overrides with this algorithm.
					entitlement = DENY;
				} else {
					resource = decision.getResource();
				}
			}
		}
		var finalDecision = new AuthorizationDecision(entitlement, resource, collector.getObligations(entitlement),
				collector.getAdvices(entitlement));
		log.debug("| |-- {} Combined AuthorizationDecision: {}", finalDecision.getDecision(), finalDecision);
		return finalDecision;
	}

	@Override
	public Flux<AuthorizationDecision> combinePolicies(List<Policy> policies, EvaluationContext ctx) {
		return doCombinePolicies(policies, ctx);
	}
}
