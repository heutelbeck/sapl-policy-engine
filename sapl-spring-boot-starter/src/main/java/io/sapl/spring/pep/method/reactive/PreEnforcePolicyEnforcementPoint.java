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
import io.sapl.spring.pep.constraints.EnforcementResult;
import io.sapl.spring.pep.constraints.Signal;
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
import io.sapl.spring.util.Maybe.Present;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ResolvableType;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.Exceptions;
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
 * Every {@code plan.execute(...)} is gated through {@link #fireAndEnforce}: a
 * failure of any obligation handler at any signal raises
 * {@link AccessDeniedException} immediately.
 *
 * @since 4.1.0
 */
@RequiredArgsConstructor
public final class PreEnforcePolicyEnforcementPoint implements MethodInterceptor {

    private static final String ERROR_ACCESS_DENIED_DECISION_NOT_PERMIT               = "Access Denied by @PreEnforce PEP. The PDP decision was %s, not PERMIT.";
    private static final String ERROR_ACCESS_DENIED_POST_INVOCATION_OBLIGATION_FAILED = "Access Denied by @PreEnforce PEP. A post-invocation obligation handler failed after the protected method had already executed. Side effects of the invocation may have occurred.";
    private static final String ERROR_ACCESS_DENIED_PRE_INVOCATION_OBLIGATION_FAILED  = "Access Denied by @PreEnforce PEP. A pre-invocation obligation handler failed. The protected method was not invoked.";
    private static final String ERROR_UNSUPPORTED_RETURN_TYPE                         = "@PreEnforce reactive PEP currently supports Mono only. Found return type %s.";

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

        val returnType = methodInvocation.getMethod().getReturnType();
        if (!Mono.class.isAssignableFrom(returnType)) {
            throw new IllegalStateException(ERROR_UNSUPPORTED_RETURN_TYPE.formatted(returnType.getName()));
        }

        val saplAttribute = attribute.get();
        return requestDecisionAndEnforce(methodInvocation, saplAttribute);
    }

    private Mono<Object> requestDecisionAndEnforce(MethodInvocation methodInvocation, SaplAttribute saplAttribute) {
        val authzSubscription = subscriptionBuilderProvider.getObject()
                .reactiveConstructAuthorizationSubscription(methodInvocation, saplAttribute);
        val pdp               = policyDecisionPointProvider.getObject();
        val authzDecision     = authzSubscription.flatMap(pdp::decideOnce);
        return authzDecision.flatMap(decision -> enforceDecision(methodInvocation, decision));
    }

    private Mono<Object> enforceDecision(MethodInvocation methodInvocation, AuthorizationDecision authzDecision) {
        val outputType       = ResolvableType.forMethodReturnType(methodInvocation.getMethod());
        val supportedSignals = collectSupportedSignals(outputType);
        val plan             = enforcementPlannerProvider.getObject().plan(authzDecision, supportedSignals);

        return Mono.defer(() -> {
            fireAndEnforce(plan, DecisionSignal.of(authzDecision),
                    ERROR_ACCESS_DENIED_PRE_INVOCATION_OBLIGATION_FAILED);
            // Here we can ignore the result of the input enforcement, as it can only have
            // mutated the pre-existing MethodInvocation.
            fireAndEnforce(plan, InputSignal.of(methodInvocation),
                    ERROR_ACCESS_DENIED_PRE_INVOCATION_OBLIGATION_FAILED);
            return applyOutput(plan, outputType, rapStream(methodInvocation, authzDecision));
        }).contextWrite(ctx -> ctx.put(EnforcementPlanContext.REACTOR_KEY, plan)).onErrorResume(t -> errorPath(plan, t))
                .doOnRequest(demand -> fireAndEnforce(plan, SubscriptionSignal.of(demand),
                        ERROR_ACCESS_DENIED_PRE_INVOCATION_OBLIGATION_FAILED))
                .doOnCancel(() -> fireAndEnforce(plan, CancelSignal.INSTANCE,
                        ERROR_ACCESS_DENIED_POST_INVOCATION_OBLIGATION_FAILED))
                .doOnSuccess(v -> fireAndEnforce(plan, CompleteSignal.INSTANCE,
                        ERROR_ACCESS_DENIED_POST_INVOCATION_OBLIGATION_FAILED))
                .doOnTerminate(() -> fireAndEnforce(plan, TerminationSignal.INSTANCE,
                        ERROR_ACCESS_DENIED_POST_INVOCATION_OBLIGATION_FAILED))
                .doAfterTerminate(() -> fireAndEnforce(plan, AfterTerminationSignal.INSTANCE,
                        ERROR_ACCESS_DENIED_POST_INVOCATION_OBLIGATION_FAILED));
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

    /**
     * Fires the output signal once with the whole RAP {@link Mono} as the value
     * and returns the (possibly Mapper-transformed) Mono. Mappers attached to the
     * output signal operate on the Mono itself, exactly like content-filter
     * providers operating on a {@code List} or {@code Map} container in the
     * blocking case. If a Mapper returns {@code null} or a non-Mono value, the
     * chain falls back to {@link Mono#empty()} as a defensive default.
     */
    private static Mono<Object> applyOutput(EnforcementPlan plan, ResolvableType outputType, Mono<?> rap) {
        val outputResult = fireAndEnforce(plan, OutputSignal.ofUnchecked(outputType, rap),
                ERROR_ACCESS_DENIED_POST_INVOCATION_OBLIGATION_FAILED);
        if (outputResult.value() instanceof Present<?>(var v) && v instanceof Mono<?> mapped) {
            @SuppressWarnings("unchecked")
            val typed = (Mono<Object>) mapped;
            return typed;
        }
        return Mono.empty();
    }

    private static Mono<Object> errorPath(EnforcementPlan plan, Throwable t) {
        // Maps both RAP exceptions and the PEP's own AccessDeniedException throws
        // through the error signal, so error handlers may transform either. A failure
        // of an error-signal obligation itself escalates to a fresh AccessDenied.
        Exceptions.throwIfFatal(t);
        EnforcementResult<?> errorResult;
        try {
            errorResult = fireAndEnforce(plan, ErrorSignal.of(t),
                    ERROR_ACCESS_DENIED_POST_INVOCATION_OBLIGATION_FAILED);
        } catch (AccessDeniedException denied) {
            return Mono.error(denied);
        }
        if (errorResult.value() instanceof Present<?>(var v) && v instanceof Throwable mapped) {
            return Mono.error(mapped);
        }
        return Mono.error(t);
    }

    private static EnforcementResult<?> fireAndEnforce(EnforcementPlan plan, Signal signal, String denialMessage) {
        val result = plan.execute(signal, false);
        if (result.failureState()) {
            throw new AccessDeniedException(denialMessage);
        }
        return result;
    }
}
