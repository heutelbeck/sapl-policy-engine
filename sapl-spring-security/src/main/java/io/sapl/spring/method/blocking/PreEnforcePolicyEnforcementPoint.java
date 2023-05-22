/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.method.metadata.PreEnforce;
import io.sapl.spring.method.metadata.SaplAttribute;
import io.sapl.spring.method.metadata.SaplAttributeRegistry;
import io.sapl.spring.subscriptions.WebAuthorizationSubscriptionBuilderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;

/**
 * An {@link AuthorizationManager} which can determine if an
 * {@link Authentication} may invoke the {@link MethodInvocation} by calling a
 * Policy Decision Point based on the expressions from the {@link PreEnforce}
 * annotation.
 *
 * @author Dominic Heutelbeck
 * @since 3.0.0
 */
@Slf4j
@RequiredArgsConstructor
public final class PreEnforcePolicyEnforcementPoint implements MethodInterceptor {

	private static final String ACCESS_DENIED_BY_PEP = "Access Denied by PEP.";

	private Supplier<Authentication> authentication = getAuthentication(
			SecurityContextHolder.getContextHolderStrategy());

	private final PolicyDecisionPoint                        policyDecisionPoint;
	private final SaplAttributeRegistry                      attributeRegistry;
	private final ConstraintEnforcementService               constraintEnforcementService;
	private final WebAuthorizationSubscriptionBuilderService subscriptionBuilder;

	@Override
	public Object invoke(MethodInvocation methodInvocation) throws Throwable {

		var attribute = attributeRegistry.getSaplAttributeForAnnotationType(methodInvocation, PreEnforce.class);
		if (attribute.isEmpty()) {
			return methodInvocation.proceed();
		}
		var saplAttribute = attribute.get();

		var authzDecision = getAuthorizationFromPolicyDecisionPoint(methodInvocation, saplAttribute);

		var methodReturnType = methodInvocation.getMethod().getReturnType();
		var bundleReturnType = methodReturnType;

		var methodReturnsOptional = Optional.class.isAssignableFrom(methodReturnType);
		if (methodReturnsOptional) {
			bundleReturnType = saplAttribute.genericsType();
		}

		var blockingPreEnforceBundle = constraintEnforcementService.blockingPreEnforceBundleFor(authzDecision,
				bundleReturnType);
		if (blockingPreEnforceBundle == null) {
			throw new AccessDeniedException(ACCESS_DENIED_BY_PEP);
		}

		try {
			blockingPreEnforceBundle.handleOnDecisionConstraints();

			var notGranted = authzDecision.getDecision() != Decision.PERMIT;
			if (notGranted) {
				throw new AccessDeniedException(ACCESS_DENIED_BY_PEP);
			}

			blockingPreEnforceBundle.handleMethodInvocationHandlers(methodInvocation);

			var returnedObject = methodInvocation.proceed();

			var unpackedObject = returnedObject;
			if (returnedObject instanceof Optional<?> optional) {
				unpackedObject = optional.orElse(null);
			}

			unpackedObject = blockingPreEnforceBundle.handleAllOnNextConstraints(unpackedObject);

			if (methodReturnsOptional) {
				return Optional.ofNullable(unpackedObject);
			}

			return unpackedObject;
		} catch (Throwable t) {
			Exceptions.throwIfFatal(t);
			throw blockingPreEnforceBundle.handleAllOnErrorConstraints(t);
		}
	}

	private AuthorizationDecision getAuthorizationFromPolicyDecisionPoint(
			MethodInvocation methodInvocation, SaplAttribute attribute) {
		var authzSubscription = subscriptionBuilder.constructAuthorizationSubscription(authentication.get(),
				methodInvocation, attribute);

		log.debug("AuthzSubscription: {}", authzSubscription);

		var authzDecisions = policyDecisionPoint.decide(authzSubscription);
		if (authzDecisions == null) {
			log.warn("Access Denied by PEP. PDP returned null. {}", attribute);
			throw new AccessDeniedException(ACCESS_DENIED_BY_PEP);
		}

		var authzDecision = authzDecisions.blockFirst();
		if (authzDecision == null) {
			log.warn("Access Denied by PEP. PDP did not return a decision. {}", attribute);
			throw new AccessDeniedException(ACCESS_DENIED_BY_PEP);
		}

		log.debug("AuthzDecision    : {}", authzDecision);
		return authzDecision;
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
