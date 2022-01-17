/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.HashSet;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class PolicySetImplCustom extends PolicySetImpl {

	/**
	 * Evaluates the body of the policy set within the given evaluation context and
	 * returns a {@link Flux} of {@link AuthorizationDecision} objects.
	 * 
	 * @return A {@link Flux} of {@link AuthorizationDecision} objects.
	 */
	@Override
	public Flux<AuthorizationDecision> evaluate() {
		if (!policyNamesAreUnique()) {
			log.debug("  |- INDETERMINATE (Set) '{}'. (Policy names not unique)", saplName);
			return Flux.just(AuthorizationDecision.INDETERMINATE);
		}
		return evaluatePolicySet(0)
				.doOnError(e -> log.debug(
						"| |- Error in the policy set evaluation. Policy set evaluated INDETERMINATE.: {}",
						e.getMessage()))
				.onErrorReturn(AuthorizationDecision.INDETERMINATE)
				.doOnNext(authzDecision -> log.debug("  |- {} (Set) '{}' {}", authzDecision.getDecision(), saplName,
						authzDecision));
	}

	private boolean policyNamesAreUnique() {
		var policyNames = new HashSet<String>(policies.size(), 1.0F);
		for (var policy : policies)
			if (!policyNames.add(policy.getSaplName()))
				return false;

		return true;
	}

	private Flux<AuthorizationDecision> evaluatePolicySet(
			int valueDefinitionId) {
		if (valueDefinitions == null || valueDefinitionId == valueDefinitions.size())
			return evaluateAndCombinePoliciesOfSet();

		var valueDefinition = valueDefinitions.get(valueDefinitionId);
		var evaluated       = valueDefinition.getEval().evaluate();
		return evaluated.switchMap(value -> evaluatePolicySet(valueDefinitionId + 1)
				.contextWrite(ctx -> AuthorizationContext.setVariable(ctx, valueDefinition.getName(), value)));
	}

	private Flux<AuthorizationDecision> evaluateAndCombinePoliciesOfSet() {
		return getAlgorithm().combinePolicies(policies);
	}

}
