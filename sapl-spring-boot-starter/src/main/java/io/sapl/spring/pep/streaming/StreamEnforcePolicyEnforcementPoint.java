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
package io.sapl.spring.pep.streaming;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ResolvableType;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.method.metadata.SaplAttribute;
import io.sapl.spring.method.metadata.SaplAttributeRegistry;
import io.sapl.spring.method.metadata.StreamEnforce;
import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.pep.constraints.Signal.AfterTerminationSignal;
import io.sapl.spring.pep.constraints.Signal.CancelSignal;
import io.sapl.spring.pep.constraints.Signal.CompleteSignal;
import io.sapl.spring.pep.constraints.Signal.DecisionSignal;
import io.sapl.spring.pep.constraints.Signal.ErrorSignal;
import io.sapl.spring.pep.constraints.Signal.OutputSignal;
import io.sapl.spring.pep.constraints.Signal.SubscriptionSignal;
import io.sapl.spring.pep.constraints.Signal.TerminationSignal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.pep.data.ShimSignalContributor;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.publisher.Flux;

/**
 * Reactive PEP for {@link StreamEnforce}. Builds a
 * {@link StreamingPipeline} per invocation and returns its output
 * {@link Flux} to the caller.
 * <p>
 * Currently supports {@link Flux} return types only.
 *
 * @since 4.1.0
 */
@Slf4j
@RequiredArgsConstructor
public final class StreamEnforcePolicyEnforcementPoint implements MethodInterceptor {

    private static final String ERROR_UNSUPPORTED_RETURN_TYPE = "@StreamEnforce reactive PEP supports Flux only at this time. Found return type %s.";

    private final ObjectProvider<PolicyDecisionPoint>                     policyDecisionPointProvider;
    private final ObjectProvider<SaplAttributeRegistry>                   attributeRegistryProvider;
    private final ObjectProvider<EnforcementPlanner>                      enforcementPlannerProvider;
    private final ObjectProvider<AuthorizationSubscriptionBuilderService> subscriptionBuilderProvider;
    private final ObjectProvider<List<ShimSignalContributor>>             shimSignalContributorsProvider;

    @Override
    public Object invoke(@NonNull MethodInvocation methodInvocation) throws Throwable {
        val attribute = attributeRegistryProvider.getObject().getSaplAttributeForAnnotationType(methodInvocation,
                StreamEnforce.class);
        if (attribute.isEmpty()) {
            return methodInvocation.proceed();
        }

        val returnType = methodInvocation.getMethod().getReturnType();
        if (!Flux.class.isAssignableFrom(returnType)) {
            throw new IllegalStateException(ERROR_UNSUPPORTED_RETURN_TYPE.formatted(returnType.getName()));
        }
        return buildPipeline(methodInvocation, attribute.get());
    }

    private Flux<Object> buildPipeline(MethodInvocation methodInvocation, SaplAttribute attribute) {
        val publisherType    = ResolvableType.forMethodReturnType(methodInvocation.getMethod());
        val supportedSignals = collectSupportedSignals(publisherType);
        val planner          = enforcementPlannerProvider.getObject();
        val pdp              = policyDecisionPointProvider.getObject();
        val authzSub         = subscriptionBuilderProvider.getObject()
                .reactiveConstructAuthorizationSubscription(methodInvocation, attribute);

        val decisions = authzSub.flatMapMany(pdp::decide);

        Function<AuthorizationDecision, EnforcementPlan> planFor     = decision -> planner.plan(decision,
                supportedSignals);
        Supplier<Flux<?>>                                rapSupplier = () -> invokeProtected(methodInvocation);

        return StreamingPipeline.create(attribute.terminateOnItemEnforcementFailure(),
                attribute.pauseRapDuringSuspend(), decisions, planFor, rapSupplier, attribute.signalTransitions());
    }

    private Set<SignalType> collectSupportedSignals(ResolvableType outputType) {
        val signals = new HashSet<SignalType>();
        signals.add(DecisionSignal.SIGNAL_TYPE);
        signals.add(ErrorSignal.SIGNAL_TYPE);
        signals.add(OutputSignal.typeFor(outputType.getGeneric(0)));
        signals.add(SubscriptionSignal.SIGNAL_TYPE);
        signals.add(CancelSignal.SIGNAL_TYPE);
        signals.add(CompleteSignal.SIGNAL_TYPE);
        signals.add(TerminationSignal.SIGNAL_TYPE);
        signals.add(AfterTerminationSignal.SIGNAL_TYPE);
        val contributors = shimSignalContributorsProvider.getIfAvailable(List::of);
        for (val contributor : contributors) {
            signals.addAll(contributor.supportedSignals());
        }
        return Set.copyOf(signals);
    }

    private static Flux<?> invokeProtected(MethodInvocation methodInvocation) {
        try {
            return (Flux<?>) methodInvocation.proceed();
        } catch (Throwable throwable) {
            return Flux.error(throwable);
        }
    }
}
