/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.api.pdp.Decision.DENY;
import static io.sapl.api.pdp.Decision.PERMIT;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.PolicyElement;
import io.sapl.grammar.sapl.impl.util.CombiningAlgorithmUtil;
import io.sapl.interpreter.CombinedDecision;
import io.sapl.interpreter.DocumentEvaluationResult;
import io.sapl.interpreter.combinators.ObligationAdviceCollector;
import reactor.core.publisher.Flux;

/**
 * This strict algorithm is used if the decision should be a DENY except for
 * there is a PERMIT. It ensures that any decision is either DENY or PERMIT.
 * <p>
 * It works as follows:
 * <p>
 * - If any policy document evaluates to PERMIT and there is no transformation
 * uncertainty (multiple policies evaluate to PERMIT and at least one of them
 * has a transformation statement), the decision is PERMIT.
 * <p>
 * - Otherwise the decision is a DENY.
 */
public class DenyUnlessPermitCombiningAlgorithmImplCustom extends DenyUnlessPermitCombiningAlgorithmImpl {

	@Override
	public Flux<CombinedDecision> combinePolicies(List<PolicyElement> policies) {
		return CombiningAlgorithmUtil.eagerlyCombinePolicyElements(policies, this::combinator, getName());
	}

	@Override
	public String getName() {
		return "DENY_UNLESS_PERMIT";
	}

	private CombinedDecision combinator(DocumentEvaluationResult[] policyDecisions) {
		if (policyDecisions.length == 0)
			return CombinedDecision.of(AuthorizationDecision.DENY, getName());

		var entitlement = DENY;
		var collector   = new ObligationAdviceCollector();
		var resource    = Optional.<JsonNode>empty();
		var decisions   = new LinkedList<DocumentEvaluationResult>();
		for (var policyDecision : policyDecisions) {
			decisions.add(policyDecision);
			var authzDecision = policyDecision.getAuthorizationDecision();
			if (authzDecision.getDecision() == PERMIT) {
				entitlement = PERMIT;
			}
			collector.add(authzDecision);
			if (authzDecision.getResource().isPresent()) {
				if (resource.isPresent()) {
					// this is a transformation uncertainty.
					// another policy already defined a transformation
					// this the overall result is basically INDETERMINATE.
					// However, DENY overrides with this algorithm.
					entitlement = DENY;
				} else {
					resource = authzDecision.getResource();
				}
			}
		}

		var finalDecision = new AuthorizationDecision(entitlement, resource, collector.getObligations(entitlement),
				collector.getAdvice(entitlement));
		return CombinedDecision.of(finalDecision, getName(), decisions);
	}

}
