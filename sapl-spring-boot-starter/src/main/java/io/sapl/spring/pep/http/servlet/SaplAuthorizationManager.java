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
package io.sapl.spring.pep.http.servlet;

import java.util.Set;
import java.util.function.Supplier;

import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.pep.constraints.Signal.DecisionSignal;
import io.sapl.spring.pep.constraints.Signal.HttpDenialSignal;
import io.sapl.spring.pep.constraints.Signal.HttpRequestMutationSignal;
import io.sapl.spring.pep.constraints.Signal.HttpRequestSignal;
import io.sapl.spring.pep.constraints.Signal.HttpResponseSignal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.pep.http.HttpEnforcementContext;
import lombok.RequiredArgsConstructor;
import lombok.val;

/**
 * {@link AuthorizationManager} for the HTTP filter chain. Delegates
 * subscription construction to an {@link AuthorizationSubscriptionFactory},
 * asks the PDP for the decision, runs it through the
 * {@link EnforcementPlanner} with the full HTTP signal set, publishes the
 * resulting plan on the request attribute
 * {@link HttpEnforcementContext#PLAN_ATTRIBUTE}, and translates the outcome
 * to allow/deny.
 * <p>
 * Because the access manager has no body to enforce on, it does not advertise
 * an output signal. A decision carrying a resource transformation is therefore
 * treated as inadmissible by the planner (the implicit resource mapper has no
 * Output signal to attach to) and resolves to a denial via the planner's
 * failure-substitute mechanism.
 * <p>
 * Constraint handlers may attach to {@link HttpRequestSignal} to observe
 * the request without changing it. Typical uses are audit logging, metrics,
 * and rate limiting. The signal carries an
 * {@link org.springframework.http.HttpRequest} view of the inbound request,
 * which downcasts to {@link ServletServerHttpRequest} for servlet-specific
 * access (cookies, session, attributes).
 * <p>
 * When the security context has no {@link Authentication}, the manager
 * defaults to an anonymous token so policies can express "permit anyone" or
 * "deny anonymous" rules without the authorization machinery raising an NPE.
 */
@RequiredArgsConstructor
public class SaplAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final Authentication ANONYMOUS = new AnonymousAuthenticationToken("key", "anonymous",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    private static final Set<SignalType> SUPPORTED_SIGNALS = Set.of(DecisionSignal.SIGNAL_TYPE,
            HttpRequestSignal.SIGNAL_TYPE, HttpRequestMutationSignal.SIGNAL_TYPE, HttpResponseSignal.SIGNAL_TYPE,
            HttpDenialSignal.SIGNAL_TYPE);

    private final PolicyDecisionPoint              pdp;
    private final EnforcementPlanner               enforcementPlanner;
    private final AuthorizationSubscriptionFactory subscriptionFactory;

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authenticationSupplier,
            RequestAuthorizationContext requestAuthorizationContext) {
        val servletRequest = requestAuthorizationContext.getRequest();
        val rawAuth        = authenticationSupplier.get();
        val authentication = rawAuth != null ? rawAuth : ANONYMOUS;
        val subscription   = subscriptionFactory.build(authentication, servletRequest);
        val authzDecision  = pdp.decideOnceBlocking(subscription);

        val plan = enforcementPlanner.plan(authzDecision, SUPPORTED_SIGNALS);
        servletRequest.setAttribute(HttpEnforcementContext.PLAN_ATTRIBUTE, plan);

        var failed      = plan.execute(DecisionSignal.of(authzDecision), false).failureState();
        val httpRequest = new ServletServerHttpRequest(servletRequest);
        failed = plan.execute(HttpRequestSignal.of(httpRequest), failed).failureState();

        if (failed) {
            return new AuthorizationDecision(false);
        }
        return new AuthorizationDecision(authzDecision.decision() == Decision.PERMIT);
    }
}
