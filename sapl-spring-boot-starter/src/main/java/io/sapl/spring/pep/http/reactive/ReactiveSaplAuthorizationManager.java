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
package io.sapl.spring.pep.http.reactive;

import static io.sapl.api.model.ValueJsonMarshaller.fromJsonNode;

import java.util.Set;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.web.server.ServerWebExchange;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
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
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Reactive {@link ReactiveAuthorizationManager} for the WebFlux filter
 * chain. Mirrors
 * {@link io.sapl.spring.pep.http.servlet.SaplAuthorizationManager} for the
 * reactive runtime: builds a per-request subscription, asks the PDP for the
 * first decision, runs it through the {@link EnforcementPlanner} with the
 * full HTTP signal set, publishes the resulting plan on the exchange
 * attribute {@link HttpEnforcementContext#PLAN_ATTRIBUTE} so the downstream
 * {@link SaplHttpPepWebFilter} and {@link SaplServerAccessDeniedHandler}
 * fire additional signals against the same plan, and translates the
 * outcome to allow/deny.
 */
@RequiredArgsConstructor
public class ReactiveSaplAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {

    private static final Authentication ANONYMOUS = new AnonymousAuthenticationToken("key", "anonymous",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    private static final Set<SignalType> SUPPORTED_SIGNALS = Set.of(DecisionSignal.TYPE, HttpRequestSignal.TYPE,
            HttpRequestMutationSignal.TYPE, HttpResponseSignal.TYPE, HttpDenialSignal.TYPE);

    private final PolicyDecisionPoint pdp;
    private final EnforcementPlanner  enforcementPlanner;
    private final ObjectMapper        mapper;

    @Override
    public Mono<AuthorizationResult> authorize(Mono<Authentication> authentication, AuthorizationContext context) {
        val exchange = context.getExchange();
        return reactiveConstructAuthorizationSubscription(authentication, exchange)
                .flatMap(subscription -> enforce(subscription, exchange))
                .map(org.springframework.security.authorization.AuthorizationDecision::new);
    }

    private Mono<Boolean> enforce(AuthorizationSubscription subscription, ServerWebExchange exchange) {
        return pdp.decide(subscription).next().defaultIfEmpty(AuthorizationDecision.DENY)
                .map(decision -> enforceDecision(decision, exchange));
    }

    private boolean enforceDecision(AuthorizationDecision authzDecision, ServerWebExchange exchange) {
        val plan = enforcementPlanner.plan(authzDecision, SUPPORTED_SIGNALS);
        exchange.getAttributes().put(HttpEnforcementContext.PLAN_ATTRIBUTE, plan);

        var failed = plan.execute(DecisionSignal.of(authzDecision), false).failureState();
        failed = plan.execute(HttpRequestSignal.of(exchange.getRequest()), failed).failureState();
        if (failed) {
            return false;
        }
        return authzDecision.decision() == Decision.PERMIT;
    }

    private Mono<AuthorizationSubscription> reactiveConstructAuthorizationSubscription(
            Mono<Authentication> authentication, ServerWebExchange exchange) {
        val requestValue = fromJsonNode(mapper.valueToTree(exchange.getRequest()));
        return authentication.defaultIfEmpty(ANONYMOUS)
                .map(authn -> AuthorizationSubscription.of(authn, requestValue, requestValue, mapper));
    }
}
