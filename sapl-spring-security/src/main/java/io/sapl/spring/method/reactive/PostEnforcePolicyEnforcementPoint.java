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
package io.sapl.spring.method.reactive;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.method.metadata.PostEnforceAttribute;
import io.sapl.spring.subscriptions.WebfluxAuthorizationSubscriptionBuilderService;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class PostEnforcePolicyEnforcementPoint {

	private final PolicyDecisionPoint pdp;

	private final ConstraintEnforcementService constraintHandlerService;

	private final WebfluxAuthorizationSubscriptionBuilderService subscriptionBuilder;

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Mono<?> postEnforceOneDecisionOnResourceAccessPoint(Mono<?> resourceAccessPoint, MethodInvocation invocation,
			PostEnforceAttribute postEnforceAttribute) {
		return resourceAccessPoint.flatMap(result -> {
			Mono<AuthorizationDecision> dec = postEnforceDecision(invocation, postEnforceAttribute, result);
			return dec.flatMap(decision -> {
				var finalResourceAccessPoint = Flux.just(result);
				if (Decision.PERMIT != decision.getDecision())
					finalResourceAccessPoint = Flux.error(new AccessDeniedException("Access Denied by PDP"));

				return constraintHandlerService.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
						(Flux) finalResourceAccessPoint, postEnforceAttribute.getGenericsType()).onErrorStop().next();
			});
		});
	}

	private Mono<AuthorizationDecision> postEnforceDecision(MethodInvocation invocation,
			PostEnforceAttribute postEnforceAttribute, Object returnedObject) {
		return subscriptionBuilder
				.reactiveConstructAuthorizationSubscription(invocation, postEnforceAttribute, returnedObject)
				.flatMapMany(pdp::decide).next();
	}

}
