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
import io.sapl.spring.method.metadata.PreEnforce;
import io.sapl.spring.method.metadata.SaplAttribute;
import io.sapl.spring.method.metadata.SaplAttributeRegistry;
import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.EnforcementPlanContext;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.pep.constraints.Signal.DecisionSignal;
import io.sapl.spring.pep.constraints.Signal.ErrorSignal;
import io.sapl.spring.pep.constraints.Signal.InputSignal;
import io.sapl.spring.pep.constraints.Signal.OutputSignal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.pep.data.ShimSignalContributor;
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
import reactor.core.Exceptions;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    private static final String ERROR_ACCESS_DENIED_DECISION_NOT_PERMIT               = "Access Denied by @PreEnforce PEP. The PDP decision was %s, not PERMIT.";
    private static final String ERROR_ACCESS_DENIED_POST_INVOCATION_OBLIGATION_FAILED = "Access Denied by @PreEnforce PEP. A post-invocation obligation handler failed after the protected method had already executed. Side effects of the invocation may have occurred.";
    private static final String ERROR_ACCESS_DENIED_PRE_INVOCATION_OBLIGATION_FAILED  = "Access Denied by @PreEnforce PEP. A pre-invocation obligation handler failed. The protected method was not invoked.";

    private final ObjectProvider<PolicyDecisionPoint>                     policyDecisionPointProvider;
    private final ObjectProvider<SaplAttributeRegistry>                   attributeRegistryProvider;
    private final ObjectProvider<EnforcementPlanner>                      enforcementPlannerProvider;
    private final ObjectProvider<AuthorizationSubscriptionBuilderService> subscriptionBuilderProvider;
    private final ObjectProvider<List<ShimSignalContributor>>             shimSignalContributorsProvider;

    @Override
    public Object invoke(@NonNull MethodInvocation methodInvocation) throws Throwable {

        val attribute = attributeRegistryProvider.getObject().getSaplAttributeForAnnotationType(methodInvocation,
                PreEnforce.class);
        if (attribute.isEmpty()) {
            return methodInvocation.proceed();
        }

        val saplAttribute = attribute.get();
        val authzDecision = getAuthorizationFromPolicyDecisionPoint(methodInvocation, saplAttribute);

        val supportedSignals = collectSupportedSignals(methodInvocation);

        val enforcementPlan = enforcementPlannerProvider.getObject().plan(authzDecision, supportedSignals);

        var enforcementFailed = false;
        try {
            enforcementFailed = enforcementPlan.execute(DecisionSignal.of(authzDecision), enforcementFailed)
                    .failureState();
            // Here we can ignore the result of the input enforcement, as it can only have
            // mutated the pre-existing MethodInvocation.
            enforcementFailed = enforcementPlan.execute(InputSignal.of(methodInvocation), enforcementFailed)
                    .failureState();

            if (authzDecision.decision() != Decision.PERMIT) {
                throw new AccessDeniedException(
                        ERROR_ACCESS_DENIED_DECISION_NOT_PERMIT.formatted(authzDecision.decision()));
            }
            if (enforcementFailed) {
                throw new AccessDeniedException(ERROR_ACCESS_DENIED_PRE_INVOCATION_OBLIGATION_FAILED);
            }

            val returnedObject = invokeWithPlanInContext(methodInvocation, enforcementPlan);
            val outputSignal   = OutputSignal.forResultOf(methodInvocation, returnedObject);
            val outputResult   = enforcementPlan.execute(outputSignal, false);

            if (outputResult.failureState()) {
                throw new AccessDeniedException(ERROR_ACCESS_DENIED_POST_INVOCATION_OBLIGATION_FAILED);
            }

            return outputResult.value() instanceof Present<?>(var v) ? v : null;
        } catch (Throwable t) {
            // Catches both Throwables from the RAP and the PEP's own AccessDeniedException
            // throws above, so error-signal handlers may transform either.
            Exceptions.throwIfFatal(t);
            val errorResult = enforcementPlan.execute(ErrorSignal.of(t), enforcementFailed);
            if (errorResult.value() instanceof Present<?>(var v) && v instanceof Throwable mapped) {
                throw mapped;
            }
            throw t;
        }
    }

    /**
     * Invokes the protected method with the given {@link EnforcementPlan} bound
     * to the thread-local {@link EnforcementPlanContext}. Shim wrappers running
     * deeper in the call graph (e.g. around Spring Data templates) read the plan
     * from the context to fire shim signals.
     */
    private static Object invokeWithPlanInContext(MethodInvocation methodInvocation, EnforcementPlan plan)
            throws Throwable {
        val previous = EnforcementPlanContext.currentBlocking().orElse(null);
        EnforcementPlanContext.bindBlocking(plan);
        try {
            return methodInvocation.proceed();
        } finally {
            EnforcementPlanContext.bindBlocking(previous);
        }
    }

    private Set<SignalType> collectSupportedSignals(MethodInvocation methodInvocation) {
        val signals = new HashSet<SignalType>();
        signals.add(DecisionSignal.TYPE);
        signals.add(InputSignal.TYPE);
        signals.add(ErrorSignal.TYPE);
        signals.add(OutputSignal.typeForReturnOf(methodInvocation));
        val contributors = shimSignalContributorsProvider.getIfAvailable(List::of);
        for (val contributor : contributors) {
            signals.addAll(contributor.supportedSignals());
        }
        return Set.copyOf(signals);
    }

    private AuthorizationDecision getAuthorizationFromPolicyDecisionPoint(MethodInvocation methodInvocation,
            SaplAttribute attribute) {
        val authzSubscription = subscriptionBuilderProvider.getObject()
                .constructAuthorizationSubscription(BlockingAuthentication.current(), methodInvocation, attribute);
        return policyDecisionPointProvider.getObject().decideOnceBlocking(authzSubscription);
    }

}
