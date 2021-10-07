package io.sapl.grammar.sapl.impl;

import static io.sapl.api.pdp.Decision.DENY;
import static io.sapl.api.pdp.Decision.INDETERMINATE;
import static io.sapl.api.pdp.Decision.NOT_APPLICABLE;
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
 * This algorithm is used if a PERMIT decision should prevail a DENY without
 * setting a default decision.
 * 
 * It works as follows:
 * 
 * 1. If any policy document evaluates to PERMIT and there is no transformation
 * uncertainty (multiple policies evaluate to PERMIT and at least one of them
 * has a transformation statement), the decision is PERMIT.
 * 
 * 2. Otherwise:
 * 
 * a) If there is any INDETERMINATE or there is a transformation uncertainty
 * (multiple policies evaluate to PERMIT and at least one of them has a
 * transformation statement), the decision is INDETERMINATE.
 * 
 * b) Otherwise:
 * 
 * i) If there is any DENY the decision is DENY.
 * 
 * ii) Otherwise the decision is NOT_APPLICABLE.
 */
@Slf4j
public class PermitOverridesCombiningAlgorithmImplCustom extends PermitOverridesCombiningAlgorithmImpl {
	@Override
	protected AuthorizationDecision combineDecisions(AuthorizationDecision[] decisions, boolean errorsInTarget) {
		if (decisions.length == 0 && !errorsInTarget) {
			log.debug("| |-- No matches/errors. Default to: {}", AuthorizationDecision.NOT_APPLICABLE);
			return AuthorizationDecision.NOT_APPLICABLE;
		}
		var entitlement = errorsInTarget ? INDETERMINATE : NOT_APPLICABLE;
		var collector = new ObligationAdviceCollector();
		Optional<JsonNode> resource = Optional.empty();
		for (var decision : decisions) {
			if (decision.getDecision() == PERMIT) {
				entitlement = PERMIT;
			}
			if (decision.getDecision() == INDETERMINATE) {
				if (entitlement != PERMIT) {
					entitlement = INDETERMINATE;
				}
			}
			if (decision.getDecision() == DENY) {
				if (entitlement == NOT_APPLICABLE) {
					entitlement = DENY;
				}
			}
			collector.add(decision);
			if (decision.getResource().isPresent()) {
				if (resource.isPresent()) {
					// this is a transformation uncertainty.
					// another policy already defined a transformation
					// this the overall result is basically INDETERMINATE.
					// However, existing DENY overrides with this algorithm.
					entitlement = INDETERMINATE;
				} else {
					resource = decision.getResource();
				}
			}
		}
		var finalDecision = new AuthorizationDecision(entitlement, resource, collector.getObligations(entitlement),
				collector.getAdvice(entitlement));
		log.debug("| |-- {} Combined AuthorizationDecision: {}", finalDecision.getDecision(), finalDecision);
		return finalDecision;
	}

	@Override
	public Flux<AuthorizationDecision> combinePolicies(List<Policy> policies, EvaluationContext ctx) {
		return doCombinePolicies(policies, ctx);
	}
}
