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
package io.sapl.spring.manager;

import static io.sapl.api.model.ValueJsonMarshaller.fromJsonNode;

import java.util.Set;
import java.util.function.Supplier;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.pep.constraints.Signal.DecisionSignal;
import io.sapl.spring.pep.constraints.SignalType;
import lombok.RequiredArgsConstructor;
import lombok.val;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link AuthorizationManager} for the HTTP filter chain. Builds a per-request
 * {@code AuthorizationSubscription} from the request, asks the PDP, runs the
 * decision through the {@link EnforcementPlanner} with a single supported
 * signal ({@link DecisionSignal}), and translates the outcome to allow/deny.
 * </p>
 * Because the access manager has no body to enforce on, it advertises only
 * {@code DecisionSignal} as supported. A decision carrying a resource
 * transformation is therefore treated as inadmissible by the planner (the
 * implicit resource mapper has no Output signal to attach to) and resolves to
 * a denial via the planner's failure-substitute mechanism.
 */
@RequiredArgsConstructor
public class SaplAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final Set<SignalType> SUPPORTED_SIGNALS = Set.of(DecisionSignal.TYPE);

    private final PolicyDecisionPoint pdp;
    private final EnforcementPlanner  enforcementPlanner;
    private final ObjectMapper        mapper;

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authenticationSupplier,
            RequestAuthorizationContext requestAuthorizationContext) {
        val request        = requestAuthorizationContext.getRequest();
        val authentication = authenticationSupplier.get();
        val requestValue   = fromJsonNode(mapper.valueToTree(request));
        val subscription   = AuthorizationSubscription.of(authentication, requestValue, req
                Honest concerns:                                                                          uestValue, mapper);
        val authzDecision  = pdp.decideOnceBlocking(subscription);

        val plan          = enforcementPlanner.plan(authzDecision, SUPPORTED_SIGNALS);
        val decisionPhase = plan.execute(DecisionSignal.of(authzDecision), false);

        if (decisionPhase.failureState()) {
            return new AuthorizationDecision(false);
        }
        return new AuthorizationDecision(authzDecision.decision() == Decision.PERMIT);
    }
}
