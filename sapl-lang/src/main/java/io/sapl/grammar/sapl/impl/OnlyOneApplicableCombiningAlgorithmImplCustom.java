package io.sapl.grammar.sapl.impl;

import static io.sapl.api.pdp.Decision.NOT_APPLICABLE;

import java.util.List;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.Policy;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;

/**
 * This algorithm is used if policy sets and policies are constructed in a way
 * that multiple policy documents with a matching target are considered an
 * error. A PERMIT or DENY decision will only be returned if there is exactly
 * one policy set or policy with matching target expression and if this policy
 * document evaluates to PERMIT or DENY.
 * 
 * It works as follows:
 * 
 * 1. If any target evaluation results in an error (INDETERMINATE) or if more
 * than one policy documents have a matching target, the decision is
 * INDETERMINATE.
 * 
 * 2. Otherwise:
 * 
 * a) If there is no matching policy document, the decision is NOT_APPLICABLE.
 * 
 * b) Otherwise, i.e., there is exactly one matching policy document, the
 * decision is the result of evaluating this policy document.
 *
 */
public class OnlyOneApplicableCombiningAlgorithmImplCustom extends OnlyOneApplicableCombiningAlgorithmImpl {
	@Override
	protected AuthorizationDecision combineDecisions(AuthorizationDecision[] decisions, boolean errorsInTarget) {
		if (errorsInTarget || decisions.length > 1)
			return AuthorizationDecision.INDETERMINATE;

		if (decisions.length == 0 || decisions[0].getDecision() == NOT_APPLICABLE)
			return AuthorizationDecision.NOT_APPLICABLE;

		return decisions[0];
	}

	@Override
	public Flux<AuthorizationDecision> combinePolicies(List<Policy> policies, EvaluationContext ctx) {
		return doCombinePolicies(policies, ctx);
	}
}
