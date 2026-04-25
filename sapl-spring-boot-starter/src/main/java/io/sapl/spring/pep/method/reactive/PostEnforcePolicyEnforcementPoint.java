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
package io.sapl.spring.pep.method.reactive;

import java.util.Set;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ResolvableType;
import org.springframework.security.access.AccessDeniedException;

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
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import reactor.core.publisher.Mono;

/**
 * Reactive variant of the @{@link PostEnforce} PEP. Currently supports
 * {@link Mono} return types. Mirrors the blocking PostEnforce flow: the RAP
 * runs first (the value is part of the authorization request), then the PDP
 * is consulted, then the {@link DecisionSignal} and {@link OutputSignal} are
 * fired against the resulting plan. RAP errors propagate unmapped because the
 * plan does not yet exist when {@code proceed()} is invoked.
 * </p>
 * Empty-Mono RAP returns are passed through without consulting the PDP: there
 * is no value to authorize on.
 * </p>
 * No {@link io.sapl.spring.pep.data.ShimSignalContributor} wiring here. The
 * RAP runs before the plan exists, so a downstream shim wrapper would never
 * see a plan in context. Advertising shim signals would violate the
 * supportedSignals invariant.
 * </p>
 *
 * @since 4.1.0
 */
@RequiredArgsConstructor
public final class PostEnforcePolicyEnforcementPoint implements MethodInterceptor {

    private static final String ERROR_ACCESS_DENIED_DECISION_NOT_PERMIT = "Access Denied by @PostEnforce PEP. The PDP decision was %s, not PERMIT.";
    private static final String ERROR_NULL_RAP_RETURN                   = "@PostEnforce method returned null instead of a Mono.";
    private static final String ERROR_UNSUPPORTED_RETURN_TYPE           = "@PostEnforce reactive PEP currently supports Mono only. Found return type %s.";

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

        val returnType = methodInvocation.getMethod().getReturnType();
        if (!Mono.class.isAssignableFrom(returnType)) {
            throw new IllegalStateException(ERROR_UNSUPPORTED_RETURN_TYPE.formatted(returnType.getName()));
        }

        val saplAttribute = attribute.get();
        return enforceMono(methodInvocation, saplAttribute);
    }

    private Mono<Object> enforceMono(MethodInvocation methodInvocation, SaplAttribute saplAttribute) {
        Mono<?> rap;
        try {
            rap = (Mono<?>) methodInvocation.proceed();
        } catch (Throwable t) {
            return Mono.error(t);
        }
        if (rap == null) {
            return Mono.error(new IllegalStateException(ERROR_NULL_RAP_RETURN));
        }
        return rap.flatMap(returnedObject -> enforceForValue(methodInvocation, saplAttribute, returnedObject));
    }

    private Mono<Object> enforceForValue(MethodInvocation methodInvocation, SaplAttribute saplAttribute,
            Object returnedObject) {
        val authzSubscription = subscriptionBuilderProvider.getObject()
                .reactiveConstructAuthorizationSubscription(methodInvocation, saplAttribute, returnedObject);
        val pdp               = policyDecisionPointProvider.getObject();
        return authzSubscription.flatMap(pdp::decideOnce)
                .flatMap(decision -> applyDecision(methodInvocation, decision, returnedObject));
    }

    private Mono<Object> applyDecision(MethodInvocation methodInvocation, AuthorizationDecision authzDecision,
            Object returnedObject) {
        val itemType         = ResolvableType.forMethodReturnType(methodInvocation.getMethod()).getGeneric(0);
        val supportedSignals = Set.of(DecisionSignal.TYPE, ErrorSignal.TYPE, OutputSignal.typeFor(itemType));
        val plan             = enforcementPlannerProvider.getObject().plan(authzDecision, supportedSignals);
        try {
            var failed = plan.enforceDecisionConstraints(authzDecision);
            if (authzDecision.decision() != Decision.PERMIT) {
                throw new AccessDeniedException(
                        ERROR_ACCESS_DENIED_DECISION_NOT_PERMIT.formatted(authzDecision.decision()));
            }
            val v = plan.enforceOutputConstraints(returnedObject, failed);
            return v == null ? Mono.empty() : Mono.just(v);
        } catch (Throwable t) {
            return plan.enforceErrorConstraints(t);
        }
    }
}
