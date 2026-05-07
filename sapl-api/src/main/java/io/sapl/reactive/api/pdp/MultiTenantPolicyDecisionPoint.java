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
 * Extension of {@link PolicyDecisionPoint} for multi-tenant deployments where
 * multiple isolated policy sets are served by a single PDP instance.
 * <p>
 * Provides parameterized methods that accept an explicit {@code pdpId} for
 * direct tenant routing. Callers that have the tenant identity available (e.g.,
 * RSocket acceptors with per-connection authentication) should use these
 * methods directly. The no-argument forms inherited from
 * {@link PolicyDecisionPoint} are expected to read the PDP ID from the Reactor
 * Context using {@link #REACTOR_CONTEXT_PDP_ID_KEY}.
 * <p>
 * The interface carries no default implementations. Implementations provide
 * every method directly and own the trade-offs (per-subscription wiring,
 * glitch-free combination, context routing).
 */
public interface MultiTenantPolicyDecisionPoint extends PolicyDecisionPoint {

    /**
     * Default PDP identifier for single-tenant deployments.
     */
    String DEFAULT_PDP_ID = "default";

    /**
     * Reactor Context key for the PDP identifier. Callers that route requests
     * through Reactor Context (e.g., HTTP WebFilters) write the authenticated
     * tenant's PDP ID under this key.
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
     * Evaluates an authorization subscription against the tenant's policies and
     * returns only the first decision. Never emits empty: an empty upstream is
     * mapped to {@link AuthorizationDecision#INDETERMINATE}.
     *
     * @param authorizationSubscription the authorization subscription
     * @param pdpId the tenant's PDP identifier
     * @return the first authorization decision
     */
    Mono<AuthorizationDecision> decideOnce(AuthorizationSubscription authorizationSubscription, String pdpId);

    /**
     * Synchronous, blocking authorization decision for a specific tenant.
     * Returns {@link AuthorizationDecision#INDETERMINATE} for an empty stream
     * (never {@code null}).
     *
     * @param authorizationSubscription the authorization subscription
     * @param pdpId the tenant's PDP identifier
     * @return the authorization decision
     */
    AuthorizationDecision decideOnceBlocking(AuthorizationSubscription authorizationSubscription, String pdpId);

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
}
