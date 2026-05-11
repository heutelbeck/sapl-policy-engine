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

import io.sapl.api.stream.Stream;

/**
 * Reactor-free counterpart to {@code ReactivePolicyDecisionPoint} (in
 * {@code sapl-pdp-reactive}) for callers
 * that consume decisions through the SAPL {@link Stream} primitive.
 * Implementations block the calling thread on each
 * {@link Stream#awaitNext()} call.
 * <p>
 * Every method takes an explicit {@code pdpId} for tenant routing.
 * The no-argument overloads delegate to the parameterised form with
 * {@link #DEFAULT_PDP_ID}; tenant resolution lives in application
 * infrastructure, not in the PDP.
 * <p>
 * {@link #decide(AuthorizationSubscription, String)} returns a
 * {@link Stream} that delivers the current authorization decision
 * and any later changes; the caller pulls one decision at a time.
 * {@link #decideOnce(AuthorizationSubscription, String)} returns a
 * single decision and does not keep watching.
 * <p>
 * For batch authorization the multi-subscription variants accept a
 * {@link MultiAuthorizationSubscription} and either deliver
 * per-subscription decisions tagged with their id
 * ({@link #decide(MultiAuthorizationSubscription, String)}) or
 * combine them into a single bundled decision
 * ({@link #decideAll(MultiAuthorizationSubscription, String)}).
 *
 * @since 4.1.0
 */
public interface StreamingPolicyDecisionPoint {

    /**
     * Default PDP identifier for single-tenant deployments and for
     * callers that do not perform tenant routing.
     */
    String DEFAULT_PDP_ID = "default";

    /**
     * Opens a stream that delivers the current authorization decision
     * for the subscription and any later changes.
     *
     * @param authorizationSubscription the authorization subscription
     * @param pdpId the tenant identifier
     * @return a closeable stream of authorization decisions
     */
    Stream<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription, String pdpId);

    /**
     * Returns the current authorization decision for the subscription
     * and does not keep watching for changes. Never returns
     * {@code null}: when no decision can be produced,
     * {@link AuthorizationDecision#INDETERMINATE} is returned.
     *
     * @param authorizationSubscription the authorization subscription
     * @param pdpId the tenant identifier
     * @return the authorization decision
     */
    AuthorizationDecision decideOnce(AuthorizationSubscription authorizationSubscription, String pdpId);

    /**
     * Opens a stream that delivers per-subscription decisions for each
     * subscription in the batch, tagged with the originating
     * subscription id.
     *
     * @param multiSubscription the batch of subscriptions
     * @param pdpId the tenant identifier
     * @return a closeable stream of identifiable authorization decisions
     */
    Stream<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiSubscription, String pdpId);

    /**
     * Opens a stream that delivers a combined
     * {@link MultiAuthorizationDecision} containing every
     * subscription's current decision.
     *
     * @param multiSubscription the batch of subscriptions
     * @param pdpId the tenant identifier
     * @return a closeable stream of combined batch decisions
     */
    Stream<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiSubscription, String pdpId);

    /**
     * Convenience overload for single-tenant or context-free callers.
     * Delegates to {@link #decide(AuthorizationSubscription, String)}
     * with {@link #DEFAULT_PDP_ID}.
     */
    default Stream<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription) {
        return decide(authorizationSubscription, DEFAULT_PDP_ID);
    }

    /**
     * Convenience overload for single-tenant or context-free callers.
     * Delegates to
     * {@link #decideOnce(AuthorizationSubscription, String)} with
     * {@link #DEFAULT_PDP_ID}.
     */
    default AuthorizationDecision decideOnce(AuthorizationSubscription authorizationSubscription) {
        return decideOnce(authorizationSubscription, DEFAULT_PDP_ID);
    }

    /**
     * Convenience overload for single-tenant or context-free callers.
     * Delegates to
     * {@link #decide(MultiAuthorizationSubscription, String)} with
     * {@link #DEFAULT_PDP_ID}.
     */
    default Stream<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiSubscription) {
        return decide(multiSubscription, DEFAULT_PDP_ID);
    }

    /**
     * Convenience overload for single-tenant or context-free callers.
     * Delegates to
     * {@link #decideAll(MultiAuthorizationSubscription, String)} with
     * {@link #DEFAULT_PDP_ID}.
     */
    default Stream<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiSubscription) {
        return decideAll(multiSubscription, DEFAULT_PDP_ID);
    }
}
