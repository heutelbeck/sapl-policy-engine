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
package io.sapl.spring.method.blocking;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.method.metadata.PreEnforceAttribute;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import lombok.extern.slf4j.Slf4j;

/**
 * Method pre-invocation handling based on a SAPL policy decision point for
 * blocking methods.
 */
@Slf4j
public class PreEnforcePolicyEnforcementPoint extends AbstractPolicyEnforcementPoint {

	public PreEnforcePolicyEnforcementPoint(ObjectFactory<PolicyDecisionPoint> pdpFactory,
			ObjectFactory<ConstraintEnforcementService> constraintHandlerFactory,
			ObjectFactory<AuthorizationSubscriptionBuilderService> subscriptionBuilderFactory) {
		super(pdpFactory, constraintHandlerFactory, subscriptionBuilderFactory);
	}

	public boolean before(
			Authentication authentication,
			MethodInvocation methodInvocation,
			PreEnforceAttribute attribute) {
		lazyLoadDependencies();

		log.debug("Attribute        : {}", attribute);

		var authzSubscription = subscriptionBuilder.constructAuthorizationSubscription(authentication, methodInvocation,
				attribute);
		log.debug("AuthzSubscription: {}", authzSubscription);

		var authzDecisions = pdp.decide(authzSubscription);
		if (authzDecisions == null) {
			log.warn("Access Denied by PEP. PDP returned null. {}", attribute);
			return false;
		}

		var authzDecision = authzDecisions.blockFirst();
		log.debug("AuthzDecision    : {}", authzDecision);

		if (authzDecision == null) {
			log.warn("Access Denied by PEP. PDP did not return a decision. {}", attribute);
			return false;
		}

		var hasResourceReplacement = authzDecision.getResource().isPresent();
		if (hasResourceReplacement) {
			log.warn("Access Denied by PEP. @PreEnforce cannot replace method return value. {}", attribute);
			return false;
		}

		var blockingPreEnforceBundle = constraintEnforcementService.blockingPreEnforceBundleFor(authzDecision);

		if (blockingPreEnforceBundle == null) {
			log.warn("Access Denied by PEP. No constraint handler bundle.");
			return false;
		}
		
		try {
			blockingPreEnforceBundle.handleOnDecisionConstraints();
			blockingPreEnforceBundle.handleMethodInvocationHandlers(methodInvocation);
		} catch (AccessDeniedException e) {
			return false;
		}

		return authzDecision.getDecision() == Decision.PERMIT;
	}

}
