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
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.method.metadata.SaplAttribute;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class PostEnforcePolicyEnforcementPoint {

    private static final String ERROR_ACCESS_DENIED_BY_PDP = "Access Denied by PDP";

    private final PolicyDecisionPoint pdp;

    private final ConstraintEnforcementService constraintHandlerService;

    private final AuthorizationSubscriptionBuilderService subscriptionBuilder;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Mono<Object> postEnforceOneDecisionOnResourceAccessPoint(Mono<?> resourceAccessPoint,
            MethodInvocation invocation, SaplAttribute postEnforceAttribute) {
        return resourceAccessPoint.flatMap(result -> {
            Mono<AuthorizationDecision> dec = postEnforceDecision(invocation, postEnforceAttribute, result);
            return dec.flatMap(decision -> {
                var finalResourceAccessPoint = Flux.just(result);
                if (Decision.PERMIT != decision.decision())
                    finalResourceAccessPoint = Flux.error(new AccessDeniedException(ERROR_ACCESS_DENIED_BY_PDP));

                return constraintHandlerService.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                        (Flux) finalResourceAccessPoint, postEnforceAttribute.genericsType()).onErrorStop().next();
            });
        });
    }

    private Mono<AuthorizationDecision> postEnforceDecision(MethodInvocation invocation,
            SaplAttribute postEnforceAttribute, Object returnedObject) {
        return subscriptionBuilder
                .reactiveConstructAuthorizationSubscription(invocation, postEnforceAttribute, returnedObject)
                .flatMapMany(pdp::decide).next();
    }

}
