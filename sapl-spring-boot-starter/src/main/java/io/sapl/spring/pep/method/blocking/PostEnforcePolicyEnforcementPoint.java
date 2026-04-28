/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.pep.method.blocking;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.method.metadata.PostEnforce;
import io.sapl.spring.method.metadata.SaplAttribute;
import io.sapl.spring.method.metadata.SaplAttributeRegistry;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.pep.constraints.Signal.DecisionSignal;
import io.sapl.spring.pep.constraints.Signal.ErrorSignal;
import io.sapl.spring.pep.constraints.Signal.OutputSignal;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import io.sapl.spring.util.Maybe.Present;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.util.Set;

/**
 * An {@link org.springframework.security.authorization.AuthorizationManager}
 * which can determine if an {@link Authentication} was permitted to invoke the
 * {@link MethodInvocation} by calling a Policy Decision Point with the method's
 * return value, based on the expressions from the {@link PostEnforce}
 * annotation.
 * </p>
 *
 * @since 4.1.0
 */
@RequiredArgsConstructor
public final class PostEnforcePolicyEnforcementPoint implements MethodInterceptor {

    private static final String ERROR_ACCESS_DENIED_DECISION_NOT_PERMIT = "Access Denied by @PostEnforce PEP. The PDP decision was %s, not PERMIT.";
    private static final String ERROR_ACCESS_DENIED_OBLIGATION_FAILED   = "Access Denied by @PostEnforce PEP. A post-invocation obligation handler failed after the protected method had already executed. Side effects of the invocation may have occurred.";

    private final ObjectProvider<PolicyDecisionPoint>                     policyDecisionPointProvider;
    private final ObjectProvider<SaplAttributeRegistry>                   attributeRegistryProvider;
    private final ObjectProvider<EnforcementPlanner>                      enforcementPlannerProvider;
    private final ObjectProvider<AuthorizationSubscriptionBuilderService> subscriptionBuilderProvider;

    @Override
    public Object invoke(@NonNull MethodInvocation methodInvocation) throws Throwable {

        val attribute = attributeRegistryProvider.getObject().getSaplAttributeForAnnotationType(methodInvocation,
                PostEnforce.class);
        if (attribute.isEmpty()) {
            return methodInvocation.proceed();
        }

        // The RAP runs first in Post-PRAP. Its output is part of the authorization
        // request, so the decision and the plan can only be obtained after the
        // method has returned. RAP exceptions therefore propagate unmapped.
        val returnedObject = methodInvocation.proceed();

        val saplAttribute = attribute.get();
        val authzDecision = getAuthorizationFromPolicyDecisionPoint(methodInvocation, saplAttribute, returnedObject);

        val supportedSignals = Set.of(DecisionSignal.SIGNAL_TYPE, ErrorSignal.SIGNAL_TYPE,
                OutputSignal.typeForReturnOf(methodInvocation));

        val enforcementPlan = enforcementPlannerProvider.getObject().plan(authzDecision, supportedSignals);

        try {
            var enforcementFailed = enforcementPlan.enforceDecisionConstraints(authzDecision);

            if (authzDecision.decision() != Decision.PERMIT) {
                throw new AccessDeniedException(
                        ERROR_ACCESS_DENIED_DECISION_NOT_PERMIT.formatted(authzDecision.decision()));
            }

            return enforcementPlan.enforceOutputConstraints(returnedObject, enforcementFailed);
        } catch (Throwable t) {
            // Catches the PEP's own AccessDeniedException throws above so error-signal
            // handlers may transform them. RAP exceptions are not seen here because
            // proceed runs before the plan exists.
            throw enforcementPlan.enforceErrorConstraintsAsThrowable(t);
        }
    }

    private AuthorizationDecision getAuthorizationFromPolicyDecisionPoint(MethodInvocation methodInvocation,
            SaplAttribute attribute, Object returnedObject) {
        val authzSubscription = subscriptionBuilderProvider.getObject()
                .constructAuthorizationSubscriptionWithReturnObject(BlockingAuthentication.current(), methodInvocation,
                        attribute, returnedObject);
        return policyDecisionPointProvider.getObject().decideOnceBlocking(authzSubscription);
    }

}
