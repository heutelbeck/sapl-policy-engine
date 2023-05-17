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
import java.util.function.Supplier;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.BlockingConstraintHandlerBundle;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.method.metadata.PostEnforce;
import io.sapl.spring.method.metadata.SaplAttributeRegistry;
import io.sapl.spring.subscriptions.WebAuthorizationSubscriptionBuilderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;

/**
 * Method post-invocation handling based on a SAPL policy decision point.
 */
@Slf4j
@RequiredArgsConstructor
public class PostEnforcePolicyEnforcementPoint implements MethodInterceptor {

	private static final String ACCESS_DENIED_BY_PEP = "Access Denied by PEP.";

	private Supplier<Authentication> authentication = getAuthentication(
			SecurityContextHolder.getContextHolderStrategy());

	private final PolicyDecisionPoint                        pdp;
	private final SaplAttributeRegistry                      attributeRegistry;
	private final ConstraintEnforcementService               constraintEnforcementService;
	private final WebAuthorizationSubscriptionBuilderService subscriptionBuilder;

	@Override
	@SuppressWarnings("unchecked")
	public Object invoke(MethodInvocation methodInvocation) throws Throwable {
		log.info("mi: {}", methodInvocation.getClass().getCanonicalName());
		var returnedObject = methodInvocation.proceed();
		log.info("returns: {}", returnedObject);

		var attribute = attributeRegistry.getSaplAttributeForAnnotationType(methodInvocation, PostEnforce.class);
		if (attribute.isEmpty()) {
			log.info("No attribute found!");
			return returnedObject;
		}
		var postEnforceAttribute = attribute.get();

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
				returnType                         = postEnforceAttribute.genericsType();
			}
		}

		var authzSubscription = subscriptionBuilder.constructAuthorizationSubscriptionWithReturnObject(
				authentication.get(),
				methodInvocation, postEnforceAttribute, returnedObjectForAuthzSubscription);
		log.debug("AuthzSubscription: {}", authzSubscription);

		var authzDecisions = pdp.decide(authzSubscription);
		if (authzDecisions == null) {
			throw new AccessDeniedException(ACCESS_DENIED_BY_PEP);
		}

		var authzDecision = authzDecisions.blockFirst();
		log.debug("AuthzDecision    : {}", authzDecision);

		if (authzDecision == null) {
			throw new AccessDeniedException(ACCESS_DENIED_BY_PEP);
		}

		return enforceDecision(isOptional, returnedObjectForAuthzSubscription, returnType, authzDecision);
	}

	private <T> Object enforceDecision(boolean isOptional, Object returnedObjectForAuthzSubscription,
			Class<T> returnType, AuthorizationDecision authzDecision) {
		BlockingConstraintHandlerBundle<T> constraintHandlerBundle = null;
		try {
			constraintHandlerBundle = constraintEnforcementService.blockingPostEnforceBundleFor(authzDecision,
					returnType);
		} catch (Throwable e) {
			Exceptions.throwIfFatal(e);
			throw new AccessDeniedException(ACCESS_DENIED_BY_PEP);
		}

		if (constraintHandlerBundle == null) {
			throw new AccessDeniedException(ACCESS_DENIED_BY_PEP);
		}

		try {
			constraintHandlerBundle.handleOnDecisionConstraints();

			var isNotPermit = authzDecision.getDecision() != Decision.PERMIT;
			if (isNotPermit)
				throw new AccessDeniedException(ACCESS_DENIED_BY_PEP);

			var result = constraintHandlerBundle.handleAllOnNextConstraints(returnedObjectForAuthzSubscription);

			if (isOptional)
				return Optional.ofNullable(result);

			return result;
		} catch (Throwable e) {
			Throwable e1 = constraintHandlerBundle.handleAllOnErrorConstraints(e);
			Exceptions.throwIfFatal(e1);
			throw new AccessDeniedException(ACCESS_DENIED_BY_PEP);
		}
	}

	private static Supplier<Authentication> getAuthentication(SecurityContextHolderStrategy strategy) {
		return () -> {
			Authentication authentication = strategy.getContext().getAuthentication();
			if (authentication == null) {
				throw new AuthenticationCredentialsNotFoundException(
						"An Authentication object was not found in the SecurityContext");
			}
			return authentication;
		};
	}
}
