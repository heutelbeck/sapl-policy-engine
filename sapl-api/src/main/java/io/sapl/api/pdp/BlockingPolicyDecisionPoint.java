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

import io.sapl.api.model.Stream;

/**
 * Synchronous counterpart to {@link PolicyDecisionPoint} for
 * callers that do not use Reactor.
 * <p>
 * {@link #decide(AuthorizationSubscription)} returns a
 * {@link Stream} that delivers the current authorization decision
 * and any later changes; the caller pulls one decision at a time.
 * {@link #decideOnce(AuthorizationSubscription)} returns a single
 * decision and does not keep watching.
 * <p>
 * For batch authorization the multi-subscription variants accept a
 * {@link MultiAuthorizationSubscription} and either deliver
 * per-subscription decisions tagged with their id
 * ({@link #decide(MultiAuthorizationSubscription)}) or combine them
 * into a single bundled decision
 * ({@link #decideAll(MultiAuthorizationSubscription)}).
 *
 * @since 4.2.0
 */
public interface BlockingPolicyDecisionPoint {

    /**
     * Opens a stream that delivers the current authorization
     * decision for the subscription and any later changes.
     *
     * @param authorizationSubscription the authorization subscription to evaluate
     * @return a closeable stream of authorization decisions
     */
    Stream<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription);

    /**
     * Returns the current authorization decision for the subscription
     * and does not keep watching for changes. Never returns
     * {@code null}: when no decision can be produced,
     * {@link AuthorizationDecision#INDETERMINATE} is returned.
     *
     * @param authorizationSubscription the authorization subscription to evaluate
     * @return the authorization decision
     */
    AuthorizationDecision decideOnce(AuthorizationSubscription authorizationSubscription);

    /**
     * Opens a stream that delivers per-subscription decisions for
     * each subscription in the batch, tagged with the originating
     * subscription id.
     *
     * @param multiSubscription the batch of subscriptions
     * @return a closeable stream of identifiable authorization decisions
     */
    Stream<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiSubscription);

    /**
     * Opens a stream that delivers a combined
     * {@link MultiAuthorizationDecision} containing every
     * subscription's current decision.
     *
     * @param multiSubscription the batch of subscriptions
     * @return a closeable stream of combined batch decisions
     */
    Stream<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiSubscription);
}
