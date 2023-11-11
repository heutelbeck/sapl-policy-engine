/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import reactor.core.Exceptions;

/**
 * Method post-invocation handling based on a SAPL policy decision point.
 */
@RequiredArgsConstructor
public class PostEnforcePolicyEnforcementPoint implements MethodInterceptor {

    private Supplier<Authentication> authentication = getAuthentication(
            SecurityContextHolder.getContextHolderStrategy());

    private final PolicyDecisionPoint                        pdp;
    private final SaplAttributeRegistry                      attributeRegistry;
    private final ConstraintEnforcementService               constraintEnforcementService;
    private final WebAuthorizationSubscriptionBuilderService subscriptionBuilder;

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
        var returnedObject = methodInvocation.proceed();
        var attribute      = attributeRegistry.getSaplAttributeForAnnotationType(methodInvocation, PostEnforce.class);
        if (attribute.isEmpty()) {
            return returnedObject;
        }

        var postEnforceAttribute               = attribute.get();
        var isOptional                         = returnedObject instanceof Optional;
        var returnedObjectForAuthzSubscription = returnedObject;
        var returnType                         = methodInvocation.getMethod().getReturnType();

        if (isOptional) {
            var optObject = (Optional<Object>) returnedObject;
            if (optObject.isPresent()) {
                returnedObjectForAuthzSubscription = optObject.get();
                returnType                         = returnedObjectForAuthzSubscription.getClass();
            } else {
                returnedObjectForAuthzSubscription = null;
                returnType                         = postEnforceAttribute.genericsType();
            }
        }

        var authzSubscription = subscriptionBuilder.constructAuthorizationSubscriptionWithReturnObject(
                authentication.get(), methodInvocation, postEnforceAttribute, returnedObjectForAuthzSubscription);

        var authzDecisions = pdp.decide(authzSubscription);
        if (authzDecisions == null) {
            throw new AccessDeniedException(
                    String.format("Access Denied by @PostEnforce PEP. PDP returned null. %s", attribute));
        }

        var authzDecision = authzDecisions.blockFirst();

        if (authzDecision == null) {
            throw new AccessDeniedException(
                    String.format("Access Denied by @PostEnforce PEP. PDP decision stream was empty. %s", attribute));
        }

        return enforceDecision(isOptional, returnedObjectForAuthzSubscription, returnType, authzDecision);
    }

    private <T> Object enforceDecision(boolean isOptional, Object returnedObjectForAuthzSubscription,
            Class<T> returnType, AuthorizationDecision authzDecision) throws Throwable {
        BlockingConstraintHandlerBundle<T> blockingPostEnforceBundle;
        try {
            blockingPostEnforceBundle = constraintEnforcementService.blockingPostEnforceBundleFor(authzDecision,
                    returnType);
        } catch (Throwable e) {
            Exceptions.throwIfFatal(e);
            throw new AccessDeniedException(
                    "Access Denied by @PostEnforce PEP. Failed to construct constraint handlers for decision.", e);
        }

        if (blockingPostEnforceBundle == null) {
            throw new AccessDeniedException(
                    "Access Denied by @PostEnforce PEP. Failed to construct constraint handlers for decision. The ConstraintEnforcementService unexpectedly returned null");
        }

        try {
            blockingPostEnforceBundle.handleOnDecisionConstraints();

            var isNotPermit = authzDecision.getDecision() != Decision.PERMIT;
            if (isNotPermit)
                throw new AccessDeniedException("Access Denied. Action not permitted.");

            var result = blockingPostEnforceBundle.handleAllOnNextConstraints(returnedObjectForAuthzSubscription);

            if (isOptional)
                return Optional.ofNullable(result);

            return result;
        } catch (Throwable e) {
            Exceptions.throwIfFatal(e);
            throw blockingPostEnforceBundle.handleAllOnErrorConstraints(e);
        }
    }

    private static Supplier<Authentication> getAuthentication(SecurityContextHolderStrategy strategy) {
        return () -> {
            var authentication = strategy.getContext().getAuthentication();
            if (authentication == null) {
                throw new AuthenticationCredentialsNotFoundException(
                        "An Authentication object was not found in the SecurityContext");
            }
            return authentication;
        };
    }
}
