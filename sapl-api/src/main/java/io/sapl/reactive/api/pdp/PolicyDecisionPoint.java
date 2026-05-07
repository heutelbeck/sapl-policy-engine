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
package io.sapl.reactive.api.pdp;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The Policy Decision Point (PDP) is the central component for authorization
 * decisions. It evaluates authorization subscriptions against policies and
 * returns decisions.
 * <p>
 * Implementations provide every method directly. The interface carries no
 * default implementations: composing one method out of another (for example
 * deriving {@code decideAll} from
 * {@code decide(MultiAuthorizationSubscription)}
 * via {@code Flux.combineLatest}) introduces composition glitches the
 * implementation must own.
 */
public interface PolicyDecisionPoint {

    /**
     * Evaluates an authorization subscription and returns a continuous stream of
     * decisions. New decisions are emitted whenever the authorization context
     * changes (e.g., due to attribute updates or policy changes).
     *
     * @param authorizationSubscription the authorization subscription to evaluate
     * @return a flux of authorization decisions
     */
    Flux<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription);

    /**
     * Evaluates an authorization subscription and returns only the first decision.
     * Never emits empty: an empty upstream is mapped to
     * {@link AuthorizationDecision#INDETERMINATE} so callers can rely on
     * receiving a decision.
     *
     * @param authorizationSubscription the authorization subscription to evaluate
     * @return the first authorization decision
     */
    Mono<AuthorizationDecision> decideOnce(AuthorizationSubscription authorizationSubscription);

    /**
     * Synchronous, blocking authorization decision for high-throughput scenarios.
     * Returns {@link AuthorizationDecision#INDETERMINATE} for an empty stream
     * (never {@code null}).
     *
     * @param authorizationSubscription the authorization subscription to evaluate
     * @return the authorization decision
     */
    AuthorizationDecision decideOnceBlocking(AuthorizationSubscription authorizationSubscription);

    /**
     * Evaluates multiple authorization subscriptions and returns decisions as
     * they become available. Each decision is tagged with the subscription ID
     * for correlation.
     *
     * @param multiSubscription the multi-subscription
     * @return a flux of identifiable authorization decisions
     */
    Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiSubscription);

    /**
     * Evaluates multiple authorization subscriptions and returns bundled
     * decisions. Waits until all subscriptions have at least one decision before
     * emitting; subsequent emissions occur when any decision changes.
     *
     * @param multiSubscription the multi-subscription
     * @return a flux of multi-authorization decisions
     */
    Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiSubscription);
}
