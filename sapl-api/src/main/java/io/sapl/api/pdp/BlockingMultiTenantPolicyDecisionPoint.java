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
import io.sapl.reactive.api.pdp.MultiTenantPolicyDecisionPoint;

/**
 * {@link BlockingPolicyDecisionPoint} for multi-tenant deployments
 * that serve more than one tenant from a single PDP instance. Adds
 * variants of every method that take an explicit {@code pdpId} to
 * route the request to a specific tenant.
 * <p>
 * The methods inherited from {@link BlockingPolicyDecisionPoint} use
 * {@link MultiTenantPolicyDecisionPoint#DEFAULT_PDP_ID}. Callers
 * that need tenant routing must use the {@code pdpId}-bearing
 * variants.
 *
 * @since 4.2.0
 */
public interface BlockingMultiTenantPolicyDecisionPoint extends BlockingPolicyDecisionPoint {

    /**
     * Opens a stream of authorization decisions for the named tenant.
     *
     * @param authorizationSubscription the authorization subscription to evaluate
     * @param pdpId the tenant identifier
     * @return a closeable stream of authorization decisions
     */
    Stream<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription, String pdpId);

    /**
     * Returns a single authorization decision for the named tenant.
     * Never returns {@code null}.
     *
     * @param authorizationSubscription the authorization subscription to evaluate
     * @param pdpId the tenant identifier
     * @return the authorization decision
     */
    AuthorizationDecision decideOnce(AuthorizationSubscription authorizationSubscription, String pdpId);

    /**
     * Opens a stream of per-subscription decisions for the named
     * tenant.
     *
     * @param multiSubscription the batch of subscriptions
     * @param pdpId the tenant identifier
     * @return a closeable stream of identifiable authorization decisions
     */
    Stream<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiSubscription, String pdpId);

    /**
     * Opens a stream of combined batch decisions for the named tenant.
     *
     * @param multiSubscription the batch of subscriptions
     * @param pdpId the tenant identifier
     * @return a closeable stream of combined batch decisions
     */
    Stream<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiSubscription, String pdpId);

    @Override
    default Stream<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription) {
        return decide(authorizationSubscription, MultiTenantPolicyDecisionPoint.DEFAULT_PDP_ID);
    }

    @Override
    default AuthorizationDecision decideOnce(AuthorizationSubscription authorizationSubscription) {
        return decideOnce(authorizationSubscription, MultiTenantPolicyDecisionPoint.DEFAULT_PDP_ID);
    }

    @Override
    default Stream<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiSubscription) {
        return decide(multiSubscription, MultiTenantPolicyDecisionPoint.DEFAULT_PDP_ID);
    }

    @Override
    default Stream<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiSubscription) {
        return decideAll(multiSubscription, MultiTenantPolicyDecisionPoint.DEFAULT_PDP_ID);
    }
}
