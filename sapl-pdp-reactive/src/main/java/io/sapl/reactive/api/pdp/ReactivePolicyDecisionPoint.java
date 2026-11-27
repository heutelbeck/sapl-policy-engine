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
import io.sapl.api.pdp.StreamingPolicyDecisionPoint;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The Policy Decision Point (PDP) is the central component for authorization
 * decisions. It evaluates authorization subscriptions against policies and
 * returns decisions.
 * <p>
 * Every method takes an explicit {@code pdpId} for tenant routing. The
 * no-argument overloads delegate to the parameterised form with
 * {@link StreamingPolicyDecisionPoint#DEFAULT_PDP_ID}; tenant resolution
 * (extracting the tenant
 * identifier from authentication, request headers, or any other transport
 * context) is the caller's responsibility and lives in application
 * infrastructure, not here.
 */
public interface ReactivePolicyDecisionPoint {

    /**
     * Evaluates an authorization subscription against the tenant's policies
     * and returns a continuous stream of decisions. New decisions are emitted
     * whenever the authorization context changes (attribute updates, policy
     * changes).
     *
     * @param authorizationSubscription the authorization subscription
     * @param pdpId the tenant's PDP identifier
     * @return a flux of authorization decisions
     */
    Flux<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription, String pdpId);

    /**
     * Evaluates an authorization subscription against the tenant's policies
     * and returns only the first decision. Never emits empty: an empty
     * upstream is mapped to {@link AuthorizationDecision#INDETERMINATE}.
     *
     * @param authorizationSubscription the authorization subscription
     * @param pdpId the tenant's PDP identifier
     * @return the first authorization decision
     */
    Mono<AuthorizationDecision> decideOnce(AuthorizationSubscription authorizationSubscription, String pdpId);

    /**
     * Evaluates multiple authorization subscriptions against the tenant's
     * policies and returns decisions as they become available.
     *
     * @param multiSubscription the multi-subscription
     * @param pdpId the tenant's PDP identifier
     * @return a flux of identifiable authorization decisions
     */
    Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiSubscription, String pdpId);

    /**
     * Evaluates multiple authorization subscriptions against the tenant's
     * policies and returns bundled decisions.
     *
     * @param multiSubscription the multi-subscription
     * @param pdpId the tenant's PDP identifier
     * @return a flux of multi-authorization decisions
     */
    Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiSubscription, String pdpId);

    /**
     * Convenience overload for single-tenant or context-free callers.
     * Delegates to {@link #decide(AuthorizationSubscription, String)} with
     * {@link StreamingPolicyDecisionPoint#DEFAULT_PDP_ID}.
     */
    default Flux<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription) {
        return decide(authorizationSubscription, StreamingPolicyDecisionPoint.DEFAULT_PDP_ID);
    }

    /**
     * Convenience overload for single-tenant or context-free callers.
     * Delegates to {@link #decideOnce(AuthorizationSubscription, String)}
     * with {@link StreamingPolicyDecisionPoint#DEFAULT_PDP_ID}.
     */
    default Mono<AuthorizationDecision> decideOnce(AuthorizationSubscription authorizationSubscription) {
        return decideOnce(authorizationSubscription, StreamingPolicyDecisionPoint.DEFAULT_PDP_ID);
    }

    /**
     * Convenience overload for single-tenant or context-free callers.
     * Delegates to {@link #decide(MultiAuthorizationSubscription, String)}
     * with {@link StreamingPolicyDecisionPoint#DEFAULT_PDP_ID}.
     */
    default Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiSubscription) {
        return decide(multiSubscription, StreamingPolicyDecisionPoint.DEFAULT_PDP_ID);
    }

    /**
     * Convenience overload for single-tenant or context-free callers.
     * Delegates to {@link #decideAll(MultiAuthorizationSubscription, String)}
     * with {@link StreamingPolicyDecisionPoint#DEFAULT_PDP_ID}.
     */
    default Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiSubscription) {
        return decideAll(multiSubscription, StreamingPolicyDecisionPoint.DEFAULT_PDP_ID);
    }
}
