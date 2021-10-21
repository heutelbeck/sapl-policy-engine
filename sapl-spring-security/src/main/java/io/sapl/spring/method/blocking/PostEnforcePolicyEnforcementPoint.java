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

import java.util.Optional;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.method.metadata.PostEnforceAttribute;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * Method post-invocation handling based on a SAPL policy decision point.
 */
@Slf4j
public class PostEnforcePolicyEnforcementPoint extends AbstractPolicyEnforcementPoint {

	public PostEnforcePolicyEnforcementPoint(ObjectFactory<PolicyDecisionPoint> pdpFactory,
			ObjectFactory<ConstraintEnforcementService> constraintHandlerFactory,
			ObjectFactory<AuthorizationSubscriptionBuilderService> subscriptionBuilderFactory) {
		super(pdpFactory, constraintHandlerFactory, subscriptionBuilderFactory);
	}

	@SuppressWarnings("unchecked") // is actually checked, warning is false positive
	public Object after(Authentication authentication, MethodInvocation methodInvocation,
			PostEnforceAttribute postEnforceAttribute, Object returnedObject) {
		log.debug("Attribute        : {}", postEnforceAttribute);

		lazyLoadDependencies();

		var returnOptional = false;
		var returnedObjectForAuthzSubscription = returnedObject;
		Flux<Object> resourceAccessPoint;
		Class<?> returnType;
		if (returnedObject instanceof Optional) {
			returnOptional = true;
			var optObject = (Optional<Object>) returnedObject;
			if (optObject.isPresent()) {
				returnedObjectForAuthzSubscription = ((Optional<Object>) returnedObject).get();
				returnType = ((Optional<Object>) returnedObject).get().getClass();
				resourceAccessPoint = Flux.just(returnedObjectForAuthzSubscription);
			} else {
				returnedObjectForAuthzSubscription = null;
				returnType = postEnforceAttribute.getGenericsType();
				resourceAccessPoint = Flux.empty();
			}
		} else {
			returnType = methodInvocation.getMethod().getReturnType();
			resourceAccessPoint = Flux.just(returnedObject);
		}

		var authzSubscription = subscriptionBuilder.constructAuthorizationSubscriptionWithReturnObject(authentication,
				methodInvocation, postEnforceAttribute, returnedObjectForAuthzSubscription);
		log.debug("AuthzSubscription: {}", authzSubscription);

		var authzDecision = pdp.decide(authzSubscription).blockFirst();
		log.debug("AuthzDecision    : {}", authzDecision);

		if (authzDecision == null)
			throw new AccessDeniedException("No decision by PDP");

		if (authzDecision.getDecision() != Decision.PERMIT)
			resourceAccessPoint = Flux.error(new AccessDeniedException("Access denied by PDP"));

		@SuppressWarnings("rawtypes")
		var result = constraintEnforcementService.enforceConstraintsOfDecisionOnResourceAccessPoint(authzDecision,
				(Flux) resourceAccessPoint, returnType).blockFirst();

		if (returnOptional)
			return Optional.ofNullable(result);

		return result;
	}

}
