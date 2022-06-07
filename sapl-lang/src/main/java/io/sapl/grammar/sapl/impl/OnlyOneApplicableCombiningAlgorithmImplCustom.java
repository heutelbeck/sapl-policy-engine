/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.api.pdp.Decision.NOT_APPLICABLE;

import java.util.List;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.Policy;
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
	public Flux<AuthorizationDecision> combinePolicies(List<Policy> policies) {
		return doCombinePolicies(policies);
	}

}
