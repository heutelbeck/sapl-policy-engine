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

import java.util.Optional;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.BlockingPostEnforceConstraintHandlerBundle;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.method.metadata.PostEnforceAttribute;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;

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
		lazyLoadDependencies();

		log.debug("Attribute        : {}", postEnforceAttribute);

		var isOptional                         = returnedObject instanceof Optional;
		var returnedObjectForAuthzSubscription = returnedObject;
		var returnType                         = methodInvocation.getMethod().getReturnType();

		if (isOptional) {
			var optObject = (Optional<Object>) returnedObject;
			if (optObject.isPresent()) {
				returnedObjectForAuthzSubscription = ((Optional<Object>) returnedObject).get();
				returnType                         = ((Optional<Object>) returnedObject).get().getClass();
			} else {
				returnedObjectForAuthzSubscription = null;
				returnType                         = postEnforceAttribute.getGenericsType();
			}
		}

		var authzSubscription = subscriptionBuilder.constructAuthorizationSubscriptionWithReturnObject(authentication,
				methodInvocation, postEnforceAttribute, returnedObjectForAuthzSubscription);
		log.debug("AuthzSubscription: {}", authzSubscription);

		var authzDecision = pdp.decide(authzSubscription).blockFirst();
		log.debug("AuthzDecision    : {}", authzDecision);

		if (authzDecision == null) {
			throw new AccessDeniedException(
					String.format("Access Denied by PEP. PDP did not return a decision. %s", postEnforceAttribute));
		}

		return enforceDecision(isOptional, returnedObjectForAuthzSubscription, returnType, authzDecision);
	}

	@SuppressWarnings("unchecked") // False positive. The type is checked beforehand
	private <T> Object enforceDecision(boolean isOptional, Object returnedObjectForAuthzSubscription,
			Class<T> returnType, AuthorizationDecision authzDecision) {
		BlockingPostEnforceConstraintHandlerBundle<T> constraintHandlerBundle = null;
		try {
			constraintHandlerBundle = constraintEnforcementService.blockingPostEnforceBundleFor(authzDecision,
					returnType);
		} catch (Throwable e) {
			Exceptions.throwIfFatal(e);
			throw new AccessDeniedException("Access Denied by PEP. Failed to construct bundle.", e);
		}
		
		if(constraintHandlerBundle == null) {
			throw new AccessDeniedException("Access Denied by PEP. No constraint handler bundle.");			
		}
		
		try {
			constraintHandlerBundle.handleOnDecisionConstraints();

			var isNotPermit = authzDecision.getDecision() != Decision.PERMIT;
			if (isNotPermit)
				throw new AccessDeniedException("Access denied by PDP");

			var result = constraintEnforcementService.replaceResultIfResourceDefinitionIsPresentInDecision(
					authzDecision, (T) returnedObjectForAuthzSubscription, returnType);
			result = constraintHandlerBundle.handleAllOnNextConstraints((T) result);

			if (isOptional)
				return Optional.ofNullable(result);

			return result;
		} catch (Throwable e) {
			e = constraintHandlerBundle.handleAllOnErrorConstraints(e);
			Exceptions.throwIfFatal(e);
			throw new AccessDeniedException("Access Denied by PEP. Failed to enforce decision", e);
		}
	}

}
