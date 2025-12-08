/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.method.metadata.PreEnforce;
import io.sapl.spring.method.metadata.SaplAttribute;
import io.sapl.spring.method.metadata.SaplAttributeRegistry;
import io.sapl.spring.subscriptions.WebAuthorizationSubscriptionBuilderService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import reactor.core.Exceptions;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * An {@link org.springframework.security.authorization.AuthorizationManager}
 * which can determine if an {@link Authentication} may invoke the
 * {@link MethodInvocation} by calling a Policy Decision Point based on the
 * expressions from the {@link PreEnforce} annotation.
 *
 * @since 3.0.0
 */
@RequiredArgsConstructor
public final class PreEnforcePolicyEnforcementPoint implements MethodInterceptor {

    private final Supplier<Authentication> authenticationSupplier = getAuthentication(
            SecurityContextHolder.getContextHolderStrategy());

    private final ObjectProvider<PolicyDecisionPoint>                        policyDecisionPointProvider;
    private final ObjectProvider<SaplAttributeRegistry>                      attributeRegistryProvider;
    private final ObjectProvider<ConstraintEnforcementService>               constraintEnforcementServiceProvider;
    private final ObjectProvider<WebAuthorizationSubscriptionBuilderService> subscriptionBuilderProvider;

    @Override
    public Object invoke(@NonNull MethodInvocation methodInvocation) throws Throwable {

        final var attribute = attributeRegistryProvider.getObject().getSaplAttributeForAnnotationType(methodInvocation,
                PreEnforce.class);
        if (attribute.isEmpty()) {
            return methodInvocation.proceed();
        }

        final var saplAttribute    = attribute.get();
        final var authzDecision    = getAuthorizationFromPolicyDecisionPoint(methodInvocation, saplAttribute);
        final var methodReturnType = methodInvocation.getMethod().getReturnType();
        var       bundleReturnType = methodReturnType;

        final var methodReturnsOptional = Optional.class.isAssignableFrom(methodReturnType);
        if (methodReturnsOptional) {
            bundleReturnType = saplAttribute.genericsType();
        }

        final var blockingPreEnforceBundle = constraintEnforcementServiceProvider.getObject()
                .blockingPreEnforceBundleFor(authzDecision, bundleReturnType);
        if (blockingPreEnforceBundle == null) {
            throw new AccessDeniedException(
                    "Access Denied by @PreEnforce PEP. Failed to construct constraint handlers for decision. The ConstraintEnforcementService unexpectedly returned null");
        }

        try {
            blockingPreEnforceBundle.handleOnDecisionConstraints();

            final var notGranted = authzDecision.decision() != Decision.PERMIT;
            if (notGranted)
                throw new AccessDeniedException("Access Denied. Action not permitted.");

            blockingPreEnforceBundle.handleMethodInvocationHandlers(methodInvocation);

            final var returnedObject = methodInvocation.proceed();

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

    private AuthorizationDecision getAuthorizationFromPolicyDecisionPoint(MethodInvocation methodInvocation,
            SaplAttribute attribute) {
        final var authzSubscription = subscriptionBuilderProvider.getObject()
                .constructAuthorizationSubscription(authenticationSupplier.get(), methodInvocation, attribute);

        final var authzDecisions = policyDecisionPointProvider.getObject().decide(authzSubscription);
        if (authzDecisions == null) {
            throw new AccessDeniedException(
                    String.format("Access Denied by @PreEnforce PEP. PDP returned null. %s", attribute));
        }

        final var authzDecision = authzDecisions.blockFirst();
        if (authzDecision == null) {
            throw new AccessDeniedException(
                    String.format("Access Denied by @PreEnforce PEP. PDP decision stream was empty. %s", attribute));
        }

        return authzDecision;
    }

    private static Supplier<Authentication> getAuthentication(SecurityContextHolderStrategy strategy) {
        return () -> {
            final var authentication = strategy.getContext().getAuthentication();
            if (authentication == null) {
                throw new AuthenticationCredentialsNotFoundException(
                        "An Authentication object was not found in the SecurityContext");
            }
            return authentication;
        };
    }

}
