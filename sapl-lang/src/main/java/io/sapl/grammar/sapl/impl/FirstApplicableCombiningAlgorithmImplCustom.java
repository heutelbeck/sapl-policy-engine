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

import static io.sapl.api.pdp.Decision.NOT_APPLICABLE;

import java.util.List;
import java.util.function.Function;

import org.reactivestreams.Publisher;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.Policy;
import io.sapl.interpreter.EvaluationContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * This algorithm is used if the policy administrator manages the policy’s priority by
 * their order in a policy set. As soon as the first policy returns PERMIT, DENY or
 * INDETERMINATE, its result is the final decision. Thus a "default" can be specified by
 * creating a last policy without any conditions. If a decision is found, errors which
 * might occur in later policies are ignored.
 *
 * Since there is no order in the policy documents known to the PDP, the PDP cannot be
 * configured with this algorithm. first-applicable might only be used for policy
 * combination inside a policy set.
 *
 * It works as follows:
 *
 * Each policy is evaluated in the order specified in the policy set.
 *
 * If it evaluates to INDETERMINATE, the decision is INDETERMINATE.
 *
 * If it evaluates to PERMIT or DENY, the decision is PERMIT or DENY
 *
 * If it evaluates to NOT_APPLICABLE, the next policy is evaluated.
 *
 * If no policy with a decision different from NOT_APPLICABLE has been found, the decision
 * of the policy set is NOT_APPLICABLE.
 *
 */
@Slf4j
public class FirstApplicableCombiningAlgorithmImplCustom extends FirstApplicableCombiningAlgorithmImpl {

	@Override
	public Flux<AuthorizationDecision> combinePolicies(List<Policy> policies, EvaluationContext ctx) {
		return Flux.just(AuthorizationDecision.NOT_APPLICABLE).flatMap(combine(0, policies, ctx));
	}

	private Function<AuthorizationDecision, Publisher<? extends AuthorizationDecision>> combine(int policyId,
			List<Policy> policies, EvaluationContext ctx) {
		if (policyId == policies.size())
			return Flux::just;

		return decision -> evaluatePolicy(policies.get(policyId), ctx).switchMap(newDecision -> {
			if (newDecision.getDecision() != NOT_APPLICABLE)
				return Flux.just(newDecision);

			return Flux.just(newDecision).switchMap(combine(policyId + 1, policies, ctx));
		});
	}

	private Flux<AuthorizationDecision> evaluatePolicy(Policy policy, EvaluationContext ctx) {
		return policy.matches(ctx).flux().flatMap(match -> {

			if (!match.isBoolean()) {
				log.debug("  |- INDETERMINATE - '{}' (target not boolen)", policy.getSaplName());
				return Flux.just(AuthorizationDecision.INDETERMINATE);
			}

			if (!match.getBoolean()) {
				log.debug("  |- NOT_APPLICABLE - '{}' (target FALSE)", policy.getSaplName());
				return Flux.just(AuthorizationDecision.NOT_APPLICABLE);
			}

			return policy.evaluate(ctx).doOnNext(
					decision -> log.debug("  |- {} '{}' {}", decision.getDecision(), policy.getSaplName(), decision));
		});
	}

}
