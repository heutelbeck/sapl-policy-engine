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

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import lombok.extern.slf4j.Slf4j;

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
public class PermitOverridesCombinator extends AbstractEagerCombinator {

	@Override
	protected AuthorizationDecision combineDecisions(Object[] decisions, boolean errorsInTarget) {
		if ((decisions == null || decisions.length == 0) && !errorsInTarget) {
			log.debug("| |-- No matches/errors. Default to: {}", AuthorizationDecision.NOT_APPLICABLE);
			return AuthorizationDecision.NOT_APPLICABLE;
		}
		var entitlement = errorsInTarget ? Decision.INDETERMINATE : Decision.NOT_APPLICABLE;
		var collector = new ObligationAdviceCollector();
		Optional<JsonNode> resource = Optional.empty();
		for (var oDecision : decisions) {
			var decision = (AuthorizationDecision) oDecision;
			if (decision.getDecision() == Decision.PERMIT) {
				entitlement = Decision.PERMIT;
			}
			if (decision.getDecision() == Decision.INDETERMINATE) {
				if (entitlement != Decision.PERMIT) {
					entitlement = Decision.INDETERMINATE;
				}
			}
			if (decision.getDecision() == Decision.DENY) {
				if (entitlement == Decision.NOT_APPLICABLE) {
					entitlement = Decision.DENY;
				}
			}
			collector.add(decision);
			if (decision.getResource().isPresent()) {
				if (resource.isPresent()) {
					// this is a transformation uncertainty.
					// another policy already defined a transformation
					// this the overall result is basically INDETERMINATE.
					// However, existing DENY overrides with this algorithm.
					entitlement = Decision.INDETERMINATE;
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

}
