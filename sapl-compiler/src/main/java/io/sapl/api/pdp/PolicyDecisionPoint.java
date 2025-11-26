/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * The Policy Decision Point (PDP) is the central component for authorization
 * decisions.
 * It evaluates authorization subscriptions against policies and returns
 * decisions.
 *
 * <p>
 * The PDP supports both single subscriptions and multi-subscriptions for batch
 * authorization scenarios. Implementations only need to provide the core
 * {@link #decide(AuthorizationSubscription)} method; multi-subscription support
 * is provided via default methods.
 */
public interface PolicyDecisionPoint {

    /**
     * Evaluates an authorization subscription and returns a continuous stream of
     * decisions.
     * New decisions are emitted whenever the authorization context changes (e.g.,
     * due to
     * attribute updates or policy changes).
     *
     * @param authorizationSubscription the authorization subscription to evaluate
     * @return a flux of authorization decisions
     */
    Flux<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription);

    /**
     * Evaluates an authorization subscription and returns only the first decision.
     *
     * @param authorizationSubscription the authorization subscription to evaluate
     * @return the first authorization decision
     */
    default Mono<AuthorizationDecision> decideOnce(AuthorizationSubscription authorizationSubscription) {
        return decide(authorizationSubscription).next();
    }

    /**
     * Evaluates multiple authorization subscriptions and returns decisions as they
     * become available.
     * Each decision is tagged with the subscription ID for correlation.
     *
     * <p>
     * This default implementation iterates over all subscriptions, calls
     * {@link #decide(AuthorizationSubscription)}
     * for each, and merges the results into a single stream.
     *
     * @param multiSubscription the multi-subscription containing multiple
     * subscriptions
     * @return a flux of identifiable authorization decisions
     */
    default Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiSubscription) {
        if (!multiSubscription.hasSubscriptions()) {
            return Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE);
        }
        return Flux.merge(createIdentifiableDecisionFluxes(multiSubscription));
    }

    /**
     * Evaluates multiple authorization subscriptions and returns bundled decisions.
     * Waits until all subscriptions have at least one decision before emitting.
     * Subsequent emissions occur when any decision changes.
     *
     * <p>
     * This default implementation iterates over all subscriptions, calls
     * {@link #decide(AuthorizationSubscription)}
     * for each, and combines the latest decisions into a
     * {@link MultiAuthorizationDecision}.
     *
     * @param multiSubscription the multi-subscription containing multiple
     * subscriptions
     * @return a flux of multi-authorization decisions containing all subscription
     * decisions
     */
    default Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiSubscription) {
        if (!multiSubscription.hasSubscriptions()) {
            return Flux.just(MultiAuthorizationDecision.indeterminate());
        }
        return Flux.combineLatest(createIdentifiableDecisionFluxes(multiSubscription),
                PolicyDecisionPoint::collectDecisions);
    }

    private List<Flux<IdentifiableAuthorizationDecision>> createIdentifiableDecisionFluxes(
            MultiAuthorizationSubscription multiSubscription) {
        List<Flux<IdentifiableAuthorizationDecision>> fluxes = new ArrayList<>();
        for (var identifiableSubscription : multiSubscription) {
            var subscriptionId = identifiableSubscription.subscriptionId();
            var subscription   = identifiableSubscription.subscription();
            var decisionFlux   = decide(subscription)
                    .map(decision -> new IdentifiableAuthorizationDecision(subscriptionId, decision));
            fluxes.add(decisionFlux);
        }
        return fluxes;
    }

    private static MultiAuthorizationDecision collectDecisions(Object[] decisions) {
        var multiDecision = new MultiAuthorizationDecision();
        for (Object value : decisions) {
            var identifiable = (IdentifiableAuthorizationDecision) value;
            multiDecision.setDecision(identifiable.subscriptionId(), identifiable.decision());
        }
        return multiDecision;
    }
}
