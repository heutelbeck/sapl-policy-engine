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
package io.sapl.spring.method.blocking;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints2.ConstraintEnforcementService;
import io.sapl.spring.method.metadata.PreEnforceAttribute;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * Method pre-invocation handling based on a SAPL policy decision point.
 */
@Slf4j
public class PreEnforcePolicyEnforcementPoint extends AbstractPolicyEnforcementPoint {

	public PreEnforcePolicyEnforcementPoint(ObjectFactory<PolicyDecisionPoint> pdpFactory,
			ObjectFactory<ConstraintEnforcementService> constraintHandlerFactory,
			ObjectFactory<AuthorizationSubscriptionBuilderService> subscriptionBuilderFactory) {
		super(pdpFactory, constraintHandlerFactory, subscriptionBuilderFactory);
	}

	public boolean before(Authentication authentication, MethodInvocation methodInvocation,
			PreEnforceAttribute attribute) {
		log.debug("Attribute        : {}", attribute);

		lazyLoadDependencies();

		var authzSubscription = subscriptionBuilder.constructAuthorizationSubscription(authentication, methodInvocation,
				attribute);
		log.debug("AuthzSubscription: {}", authzSubscription);
		var authzDecision = pdp.decide(authzSubscription).blockFirst();
		log.debug("AuthzDecision    : {}", authzDecision);

		if (authzDecision == null)
			return false;

		if (authzDecision.getResource().isPresent()) {
			log.warn(
					"Cannot handle a authorization decision declaring a new resource in blocking @PreEnforce. Deny access!");
			return false;
		}

		try {
			constraintEnforcementService
					.enforceConstraintsOfDecisionOnResourceAccessPoint(authzDecision, Flux.empty(), Object.class)
					.blockFirst();
		} catch (AccessDeniedException e) {
			return false;
		}

		if (authzDecision.getDecision() != Decision.PERMIT)
			return false;

		return true;
	}

}
