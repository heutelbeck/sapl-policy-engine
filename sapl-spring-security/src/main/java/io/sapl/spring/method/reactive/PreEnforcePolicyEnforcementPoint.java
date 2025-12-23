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
package io.sapl.spring.method.reactive;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import lombok.RequiredArgsConstructor;
import org.aopalliance.intercept.MethodInvocation;
import org.reactivestreams.Publisher;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;

import java.util.function.Function;

@RequiredArgsConstructor
public class PreEnforcePolicyEnforcementPoint {

    private final ConstraintEnforcementService constraintEnforcementService;

    public <T> Flux<T> enforce(Flux<AuthorizationDecision> authorizationDecisions, MethodInvocation invocation,
            Class<T> clazz) {
        return authorizationDecisions.next().flatMapMany(enforceDecision(invocation, clazz));
    }

    @SuppressWarnings("unchecked")
    private <T> Function<AuthorizationDecision, Flux<T>> enforceDecision(MethodInvocation invocation, Class<T> clazz) {
        return decision -> {
            final var constraintHandlerBundle = constraintEnforcementService.reactiveTypeBundleFor(decision, clazz);

            Flux<T> resourceAccessPoint;

            final var decisionIsPermit = Decision.PERMIT != decision.decision();
            if (decisionIsPermit) {
                resourceAccessPoint = Flux.error(new AccessDeniedException("Access Denied by PDP"));
            } else {
                constraintHandlerBundle.handleMethodInvocationHandlers(invocation);
                try {
                    resourceAccessPoint = Flux.from((Publisher<T>) invocation.proceed());
                } catch (Throwable t) {
                    resourceAccessPoint = Flux.error(t);
                }
            }

            resourceAccessPoint = constraintEnforcementService.replaceIfResourcePresent(resourceAccessPoint,
                    decision.resource(), clazz);

            // onErrorStop is required to counter an onErrorContinue attack on the PEP/RAP.
            return constraintHandlerBundle.wrap(resourceAccessPoint).onErrorStop();
        };
    }

}
