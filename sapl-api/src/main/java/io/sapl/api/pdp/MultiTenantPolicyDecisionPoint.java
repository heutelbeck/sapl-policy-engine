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
package io.sapl.api.pdp;

import java.util.ArrayList;
import java.util.List;

import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Extension of {@link PolicyDecisionPoint} for multi-tenant deployments where
 * multiple isolated policy sets are served by a single PDP instance.
 * <p>
 * Provides parameterized methods that accept an explicit {@code pdpId} for
 * direct tenant routing. Callers that have the tenant identity available (e.g.,
 * RSocket acceptors with per-connection authentication) should use these
 * methods directly.
 * <p>
 * The no-argument reactive methods inherited from {@link PolicyDecisionPoint}
 * read the PDP ID from the Reactor Context using
 * {@link #REACTOR_CONTEXT_PDP_ID_KEY}. This allows transparent multi-tenant
 * routing for HTTP endpoints where a WebFilter writes the authenticated
 * tenant's PDP ID into the Reactor Context upstream. If no PDP ID is found in
 * the context, {@link #DEFAULT_PDP_ID} is used.
 * <p>
 * The no-argument blocking method {@code decideOnceBlocking} delegates with
 * {@link #DEFAULT_PDP_ID} since Reactor Context is not available in blocking
 * calls. Callers requiring tenant routing in blocking contexts should use
 * {@link #decideOnceBlocking(AuthorizationSubscription, String)} directly.
 */
public interface MultiTenantPolicyDecisionPoint extends PolicyDecisionPoint {

    /**
     * Default PDP identifier for single-tenant deployments.
     */
    String DEFAULT_PDP_ID = "default";

    /**
     * Reactor Context key for the PDP identifier. Callers that route requests
     * through Reactor Context (e.g., HTTP WebFilters) write the authenticated
     * tenant's PDP ID under this key. The reactive default methods read it
     * automatically.
     */
    String REACTOR_CONTEXT_PDP_ID_KEY = "sapl.pdp.id";

    /**
     * Evaluates an authorization subscription against the tenant's policies
     * and returns a continuous stream of decisions.
     *
     * @param authorizationSubscription the authorization subscription
     * @param pdpId the tenant's PDP identifier
     * @return a flux of authorization decisions
     */
    Flux<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription, String pdpId);

    /**
     * Evaluates an authorization subscription against the tenant's policies
     * and returns only the first decision.
     *
     * @param authorizationSubscription the authorization subscription
     * @param pdpId the tenant's PDP identifier
     * @return the first authorization decision
     */
    default Mono<AuthorizationDecision> decideOnce(AuthorizationSubscription authorizationSubscription, String pdpId) {
        return decide(authorizationSubscription, pdpId).next();
    }

    /**
     * Synchronous, blocking authorization decision for a specific tenant.
     *
     * @param authorizationSubscription the authorization subscription
     * @param pdpId the tenant's PDP identifier
     * @return the authorization decision
     */
    default AuthorizationDecision decideOnceBlocking(AuthorizationSubscription authorizationSubscription,
            String pdpId) {
        return decideOnce(authorizationSubscription, pdpId).block();
    }

    /**
     * Evaluates multiple authorization subscriptions against the tenant's
     * policies and returns decisions as they become available.
     *
     * @param multiSubscription the multi-subscription
     * @param pdpId the tenant's PDP identifier
     * @return a flux of identifiable authorization decisions
     */
    default Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiSubscription,
            String pdpId) {
        if (!multiSubscription.hasSubscriptions()) {
            return Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE);
        }
        return Flux.merge(createIdentifiableDecisionFluxes(multiSubscription, pdpId));
    }

    /**
     * Evaluates multiple authorization subscriptions against the tenant's
     * policies and returns bundled decisions.
     *
     * @param multiSubscription the multi-subscription
     * @param pdpId the tenant's PDP identifier
     * @return a flux of multi-authorization decisions
     */
    default Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiSubscription, String pdpId) {
        if (!multiSubscription.hasSubscriptions()) {
            return Flux.just(MultiAuthorizationDecision.indeterminate());
        }
        return Flux.combineLatest(createIdentifiableDecisionFluxes(multiSubscription, pdpId),
                MultiTenantPolicyDecisionPoint::collectDecisions);
    }

    @Override
    default Flux<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription) {
        return Flux.deferContextual(
                ctx -> decide(authorizationSubscription, ctx.getOrDefault(REACTOR_CONTEXT_PDP_ID_KEY, DEFAULT_PDP_ID)));
    }

    @Override
    default Mono<AuthorizationDecision> decideOnce(AuthorizationSubscription authorizationSubscription) {
        return Mono.deferContextual(ctx -> decideOnce(authorizationSubscription,
                ctx.getOrDefault(REACTOR_CONTEXT_PDP_ID_KEY, DEFAULT_PDP_ID)));
    }

    @Override
    default AuthorizationDecision decideOnceBlocking(AuthorizationSubscription authorizationSubscription) {
        return decideOnceBlocking(authorizationSubscription, DEFAULT_PDP_ID);
    }

    @Override
    default Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiSubscription) {
        return Flux.deferContextual(
                ctx -> decide(multiSubscription, ctx.getOrDefault(REACTOR_CONTEXT_PDP_ID_KEY, DEFAULT_PDP_ID)));
    }

    @Override
    default Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiSubscription) {
        return Flux.deferContextual(
                ctx -> decideAll(multiSubscription, ctx.getOrDefault(REACTOR_CONTEXT_PDP_ID_KEY, DEFAULT_PDP_ID)));
    }

    private List<Flux<IdentifiableAuthorizationDecision>> createIdentifiableDecisionFluxes(
            MultiAuthorizationSubscription multiSubscription, String pdpId) {
        List<Flux<IdentifiableAuthorizationDecision>> fluxes = new ArrayList<>();
        for (val identifiableSubscription : multiSubscription) {
            val subscriptionId = identifiableSubscription.subscriptionId();
            val subscription   = identifiableSubscription.subscription();
            val decisionFlux   = decide(subscription, pdpId)
                    .map(decision -> new IdentifiableAuthorizationDecision(subscriptionId, decision));
            fluxes.add(decisionFlux);
        }
        return fluxes;
    }

    private static MultiAuthorizationDecision collectDecisions(Object[] decisions) {
        val multiDecision = new MultiAuthorizationDecision();
        for (Object value : decisions) {
            val identifiable = (IdentifiableAuthorizationDecision) value;
            multiDecision.setDecision(identifiable.subscriptionId(), identifiable.decision());
        }
        return multiDecision;
    }

}
