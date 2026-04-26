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

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.method.metadata.PreEnforce;
import io.sapl.spring.method.metadata.SaplAttribute;
import io.sapl.spring.method.metadata.SaplAttributeRegistry;
import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.EnforcementPlanContext;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.pep.constraints.Signal.AfterTerminationSignal;
import io.sapl.spring.pep.constraints.Signal.CancelSignal;
import io.sapl.spring.pep.constraints.Signal.CompleteSignal;
import io.sapl.spring.pep.constraints.Signal.DecisionSignal;
import io.sapl.spring.pep.constraints.Signal.ErrorSignal;
import io.sapl.spring.pep.constraints.Signal.InputSignal;
import io.sapl.spring.pep.constraints.Signal.OutputSignal;
import io.sapl.spring.pep.constraints.Signal.SubscriptionSignal;
import io.sapl.spring.pep.constraints.Signal.TerminationSignal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.pep.data.ShimSignalContributor;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ResolvableType;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reactive variant of the @{@link PreEnforce} PEP. Detects the protected
 * method's return type and currently supports {@link Mono} returns. Wires the
 * Pre-PRAP sequence (decision, input, permit gate, proceed, output) plus the
 * reactive lifecycle signals (subscription, cancel, complete, terminate,
 * after-terminate) onto the returned Mono. Subject lookup is delegated to
 * {@link AuthorizationSubscriptionBuilderService}, which reads from
 * {@link org.springframework.security.core.context.ReactiveSecurityContextHolder}
 * with a fallback to the thread-bound holder.
 *
 * @since 4.1.0
 */
@RequiredArgsConstructor
public final class PreEnforcePolicyEnforcementPoint implements MethodInterceptor {

    private static final String ERROR_ACCESS_DENIED_DECISION_NOT_PERMIT = "Access Denied by @PreEnforce PEP. The PDP decision was %s, not PERMIT.";
    private static final String ERROR_UNSUPPORTED_RETURN_TYPE           = "@PreEnforce reactive PEP supports Mono and Flux only. Found return type %s.";

    private static final Object EMPTY_RAP_MARKER = new Object();

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

        val returnType    = methodInvocation.getMethod().getReturnType();
        val saplAttribute = attribute.get();
        val authzDecision = decisionFor(methodInvocation, saplAttribute);
        if (Mono.class.isAssignableFrom(returnType)) {
            return authzDecision.flatMap(decision -> enforceDecision(methodInvocation, decision));
        }
        if (Flux.class.isAssignableFrom(returnType)) {
            return authzDecision.flatMapMany(decision -> enforceDecisionAsFlux(methodInvocation, decision));
        }
        throw new IllegalStateException(ERROR_UNSUPPORTED_RETURN_TYPE.formatted(returnType.getName()));
    }

    private Mono<AuthorizationDecision> decisionFor(MethodInvocation methodInvocation, SaplAttribute saplAttribute) {
        val authzSubscription = subscriptionBuilderProvider.getObject()
                .reactiveConstructAuthorizationSubscription(methodInvocation, saplAttribute);
        val pdp               = policyDecisionPointProvider.getObject();
        return authzSubscription.flatMap(pdp::decideOnce);
    }

    private Mono<Object> enforceDecision(MethodInvocation methodInvocation, AuthorizationDecision authzDecision) {
        val itemType = ResolvableType.forMethodReturnType(methodInvocation.getMethod()).getGeneric(0);
        val plan     = enforcementPlan(authzDecision, itemType);

        return Mono.defer(() -> {
            plan.enforcePreInvocationConstraints(authzDecision, methodInvocation);
            return applyOutput(plan, rapStream(methodInvocation, authzDecision));
        }).contextWrite(ctx -> ctx.put(EnforcementPlanContext.REACTOR_KEY, plan))
                .onErrorResume(plan::enforceErrorConstraints).doOnRequest(plan::enforceSubscription)
                .doOnCancel(plan::enforceCancel).doOnSuccess(v -> plan.enforceComplete())
                .doOnTerminate(plan::enforceTermination).doAfterTerminate(plan::enforceAfterTermination);
    }

    private Set<SignalType> collectSupportedSignals(ResolvableType outputType) {
        val signals = new HashSet<SignalType>();
        signals.add(DecisionSignal.TYPE);
        signals.add(InputSignal.TYPE);
        signals.add(ErrorSignal.TYPE);
        signals.add(OutputSignal.typeFor(outputType));
        signals.add(SubscriptionSignal.TYPE);
        signals.add(CancelSignal.TYPE);
        signals.add(CompleteSignal.TYPE);
        signals.add(TerminationSignal.TYPE);
        signals.add(AfterTerminationSignal.TYPE);
        val contributors = shimSignalContributorsProvider.getIfAvailable(List::of);
        for (val contributor : contributors) {
            signals.addAll(contributor.supportedSignals());
        }
        return Set.copyOf(signals);
    }

    private EnforcementPlan enforcementPlan(AuthorizationDecision authzDecision, ResolvableType outputType) {
        val supportedSignals = collectSupportedSignals(outputType);
        return enforcementPlannerProvider.getObject().plan(authzDecision, supportedSignals);
    }

    private Flux<Object> enforceDecisionAsFlux(MethodInvocation methodInvocation, AuthorizationDecision authzDecision) {
        val publisherType = ResolvableType.forMethodReturnType(methodInvocation.getMethod());
        val plan          = enforcementPlan(authzDecision, publisherType);

        return Flux.defer(() -> {
            plan.enforcePreInvocationConstraints(authzDecision, methodInvocation);
            return applyOutputFlux(plan, rapFluxStream(methodInvocation, authzDecision));
        }).contextWrite(ctx -> ctx.put(EnforcementPlanContext.REACTOR_KEY, plan))
                .onErrorResume(plan::enforceErrorConstraints).doOnRequest(plan::enforceSubscription)
                .doOnCancel(plan::enforceCancel).doOnComplete(plan::enforceComplete)
                .doOnTerminate(plan::enforceTermination).doAfterTerminate(plan::enforceAfterTermination);
    }

    private static Mono<?> rapStream(MethodInvocation methodInvocation, AuthorizationDecision authzDecision) {
        if (authzDecision.decision() != Decision.PERMIT) {
            return Mono.error(new AccessDeniedException(
                    ERROR_ACCESS_DENIED_DECISION_NOT_PERMIT.formatted(authzDecision.decision())));
        }
        try {
            return (Mono<?>) methodInvocation.proceed();
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    private static Flux<?> rapFluxStream(MethodInvocation methodInvocation, AuthorizationDecision authzDecision) {
        if (authzDecision.decision() != Decision.PERMIT) {
            return Flux.error(new AccessDeniedException(
                    ERROR_ACCESS_DENIED_DECISION_NOT_PERMIT.formatted(authzDecision.decision())));
        }
        try {
            return (Flux<?>) methodInvocation.proceed();
        } catch (Throwable t) {
            return Flux.error(t);
        }
    }

    /**
     * Fires the OutputSignal per emitted item, and once with a {@code null}
     * value when the RAP completes empty so policy still applies. Mappers
     * returning {@code null} drop the item (matches the "value may be null"
     * blocking semantic without violating Reactor's no-null-emission rule).
     */
    private static Mono<Object> applyOutput(EnforcementPlan plan, Mono<?> rap) {
        // Sentinel, not switchIfEmpty downstream of mapNotNull: enforcement may
        // legitimately produce an empty Mono (null Mapper return, void output type)
        // and a downstream switchIfEmpty would re-trigger enforcement.
        return rap.cast(Object.class).defaultIfEmpty(EMPTY_RAP_MARKER).flatMap(value -> {
            val enforced = plan.enforceOutputConstraints(value == EMPTY_RAP_MARKER ? null : value);
            return enforced == null ? Mono.empty() : Mono.just(enforced);
        });
    }

    /**
     * Flux variant of {@link #applyOutput}. Fires the output signal once with
     * the whole RAP {@link Flux} as the value and returns the (possibly
     * Mapper-transformed) Flux. Falls back to {@link Flux#empty()} if a Mapper
     * returns {@code null} or a non-Flux value.
     */
    private static Flux<Object> applyOutputFlux(EnforcementPlan plan, Flux<?> rap) {
        if (plan.enforceOutputConstraints(rap, false) instanceof Flux<?> mapped) {
            @SuppressWarnings("unchecked")
            val typed = (Flux<Object>) mapped;
            return typed;
        }
        return Flux.empty();
    }
}
