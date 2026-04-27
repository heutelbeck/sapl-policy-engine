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

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.server.authorization.AuthorizationContext;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.pep.constraints.Signal.DecisionSignal;
import io.sapl.spring.pep.constraints.Signal.HttpRequestSignal;
import io.sapl.spring.pep.constraints.SignalType;
import lombok.RequiredArgsConstructor;
import lombok.val;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Reactive {@link ReactiveAuthorizationManager} for the WebFlux filter chain.
 * Mirrors {@link SaplAuthorizationManager} for the reactive runtime: builds a
 * per-request subscription, asks the PDP for the first decision, runs it
 * through the {@link EnforcementPlanner} with {@link DecisionSignal} and
 * {@link HttpRequestSignal} as supported signals, and translates the
 * outcome
 * to allow/deny.
 * </p>
 * Constraint handlers may attach to {@link HttpRequestSignal} to observe
 * the
 * request. The carried {@link org.springframework.http.HttpRequest} downcasts
 * to a reactive {@link ServerHttpRequest} for backend-specific access.
 */
@RequiredArgsConstructor
public class ReactiveSaplAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {

    private static final Authentication ANONYMOUS = new AnonymousAuthenticationToken("key", "anonymous",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    private static final Set<SignalType> SUPPORTED_SIGNALS = Set.of(DecisionSignal.TYPE, HttpRequestSignal.TYPE);

    private final PolicyDecisionPoint pdp;
    private final EnforcementPlanner  enforcementPlanner;
    private final ObjectMapper        mapper;

    @Override
    public Mono<AuthorizationResult> authorize(Mono<Authentication> authentication, AuthorizationContext context) {
        val request = context.getExchange().getRequest();
        return reactiveConstructAuthorizationSubscription(authentication, request)
                .flatMap(subscription -> enforce(subscription, request))
                .map(org.springframework.security.authorization.AuthorizationDecision::new);
    }

    private Mono<Boolean> enforce(AuthorizationSubscription subscription, ServerHttpRequest request) {
        return pdp.decide(subscription).next().defaultIfEmpty(AuthorizationDecision.DENY)
                .map(decision -> enforceDecision(decision, request));
    }

    private boolean enforceDecision(AuthorizationDecision authzDecision, ServerHttpRequest request) {
        val plan   = enforcementPlanner.plan(authzDecision, SUPPORTED_SIGNALS);
        var failed = plan.execute(DecisionSignal.of(authzDecision), false).failureState();
        failed = plan.execute(HttpRequestSignal.of(request), failed).failureState();
        if (failed) {
            return false;
        }
        return authzDecision.decision() == Decision.PERMIT;
    }

    private Mono<AuthorizationSubscription> reactiveConstructAuthorizationSubscription(
            Mono<Authentication> authentication, ServerHttpRequest request) {
        val requestValue = fromJsonNode(mapper.valueToTree(request));
        return authentication.defaultIfEmpty(ANONYMOUS)
                .map(authn -> AuthorizationSubscription.of(authn, requestValue, requestValue, mapper));
    }
}
