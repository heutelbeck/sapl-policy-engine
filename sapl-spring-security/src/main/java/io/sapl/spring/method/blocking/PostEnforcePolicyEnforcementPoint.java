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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ReactiveConstraintEnforcementService;
import io.sapl.spring.method.metadata.PostEnforceAttribute;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import lombok.extern.slf4j.Slf4j;

/**
 * Method post-invocation handling based on a SAPL policy decision point.
 */
@Slf4j
public class PostEnforcePolicyEnforcementPoint extends AbstractPolicyEnforcementPoint {

	public PostEnforcePolicyEnforcementPoint(ObjectFactory<PolicyDecisionPoint> pdpFactory,
			ObjectFactory<ReactiveConstraintEnforcementService> constraintHandlerFactory,
			ObjectFactory<ObjectMapper> objectMapperFactory,
			ObjectFactory<AuthorizationSubscriptionBuilderService> subscriptionBuilderFactory) {
		super(pdpFactory, constraintHandlerFactory, objectMapperFactory, subscriptionBuilderFactory);
	}

	@SuppressWarnings("unchecked") // is actually checked, warning is false positive
	public Object after(Authentication authentication, MethodInvocation methodInvocation,
			PostEnforceAttribute postEnforceAttribute, Object returnedObject) {
		lazyLoadDependencies();

		var returnOptional = false;
		var returnedObjectForAuthzSubscription = returnedObject;
		Class<?> returnType;
		if (returnedObject instanceof Optional) {
			returnedObjectForAuthzSubscription = ((Optional<Object>) returnedObject).get();
			returnType = ((Optional<Object>) returnedObject).get().getClass();
			returnOptional = true;
		} else
			returnType = methodInvocation.getMethod().getReturnType();

		var authzSubscription = subscriptionBuilder.constructAuthorizationSubscriptionWithReturnObject(authentication,
				methodInvocation, postEnforceAttribute, returnedObjectForAuthzSubscription);
		log.debug("ATTRIBUTE: {} - {}", postEnforceAttribute, postEnforceAttribute.getClass());
		log.debug("SUBSCRIPTION  :\n - ACTION={}\n - RESOURCE={}\n - SUBJ={}\n - ENV={}", authzSubscription.getAction(),
				authzSubscription.getResource(), authzSubscription.getSubject(), authzSubscription.getEnvironment());

		var authzDecision = pdp.decide(authzSubscription).blockFirst();
		log.debug("AUTH_DECISION : {} - {}", authzDecision == null ? "null" : authzDecision.getDecision(),
				authzDecision);

		if (authzDecision == null)
			throw new AccessDeniedException("No decision by PDP");

		if (authzDecision.getDecision() != Decision.PERMIT) {
			constraintEnforcementService.handleForBlockingMethodInvocationOrAccessDenied(authzDecision);
			throw new AccessDeniedException("Access denied by PDP");
		}

		if (authzDecision.getResource().isPresent()) {
			try {
				var returnValue = mapper.treeToValue(authzDecision.getResource().get(), returnType);
				if (returnOptional)
					returnedObject = Optional.of(returnValue);
				else
					returnedObject = returnValue;
			} catch (JsonProcessingException e) {
				log.trace("Transformed result cannot be mapped to expected return type. {}",
						authzDecision.getResource().get());
				throw new AccessDeniedException(
						"Returned resource of authzDecision cannot be mapped back to return value. Access not permitted by policy enforcement point.",
						e);
			}
		}

		return constraintEnforcementService.handleAfterBlockingMethodInvocation(authzDecision, returnedObject,
				returnType);
	}

}
