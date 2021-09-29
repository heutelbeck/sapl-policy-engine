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
import org.springframework.security.core.Authentication;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ReactiveConstraintEnforcementService;
import io.sapl.spring.method.attributes.PreEnforceAttribute;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import lombok.extern.slf4j.Slf4j;

/**
 * Method pre-invocation handling based on a SAPL policy decision point.
 */
@Slf4j
public class PolicyBasedPreInvocationEnforcementAdvice extends AbstractPolicyBasedInvocationEnforcementAdvice
		implements PreInvocationEnforcementAdvice {

	public PolicyBasedPreInvocationEnforcementAdvice(ObjectFactory<PolicyDecisionPoint> pdpFactory,
			ObjectFactory<ReactiveConstraintEnforcementService> constraintHandlerFactory,
			ObjectFactory<ObjectMapper> objectMapperFactory,
			ObjectFactory<AuthorizationSubscriptionBuilderService> subscriptionBuilderFactory) {
		super(pdpFactory, constraintHandlerFactory, objectMapperFactory, subscriptionBuilderFactory);
	}

	public boolean before(Authentication authentication, MethodInvocation methodInvocation,
			PreEnforceAttribute attribute) {
		// Lazy loading to decouple infrastructure initialization from domain
		// initialization. Else, beans may become non eligible for BeanPostProcessors
		lazyLoadDependencies();

		var authzSubscription = subscriptionBuilder.constructAuthorizationSubscription(authentication, methodInvocation,
				attribute);
		log.trace("AuthzSubscription: {}", authzSubscription);
		var authzDecision = pdp.decide(authzSubscription).blockFirst();
		log.trace("Decision         : {} - {}", authzDecision == null ? "null" : authzDecision.getDecision(),
				authzDecision);

		if (authzDecision == null)
			return false;

		if (authzDecision.getResource().isPresent()) {
			log.warn("Cannot handle a authorization decision declaring a new resource in @PreEnforce. Deny access!");
			return false;
		}

		if (authzDecision.getDecision() != Decision.PERMIT) {
			constraintEnforcementService.handleForBlockingMethodInvocationOrAccessDenied(authzDecision);
			return false;
		}

		return constraintEnforcementService.handleForBlockingMethodInvocationOrAccessDenied(authzDecision);
	}

}
