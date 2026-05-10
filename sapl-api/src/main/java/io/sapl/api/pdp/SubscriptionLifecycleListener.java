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

/**
 * Receives lifecycle callbacks for one authorization subscription stream.
 * Useful for activity counters, latency timers, audit pairing.
 * <p>
 * Each subscription stream produces exactly one
 * {@link #onSubscribe(String, AuthorizationSubscription, String)} call
 * followed by zero or more decision emissions and exactly one
 * {@link #onUnsubscribe(String)} call when the stream ends (normally,
 * via error, or via consumer cancellation).
 * <p>
 * Listeners are observability concerns: misbehaving listeners must not
 * affect authorization correctness. The PDP catches and logs exceptions
 * thrown from these methods.
 *
 * @since 4.1.0
 */
public interface SubscriptionLifecycleListener {

    /**
     * Called once when an authorization subscription stream begins.
     *
     * @param subscriptionId the per-evaluation subscription identifier
     * @param authorizationSubscription the subscription being evaluated
     * @param pdpId the tenant identifier the request was routed to
     */
    void onSubscribe(String subscriptionId, AuthorizationSubscription authorizationSubscription, String pdpId);

    /**
     * Called once when an authorization subscription stream ends,
     * regardless of how the stream terminated.
     *
     * @param subscriptionId the per-evaluation subscription identifier
     */
    void onUnsubscribe(String subscriptionId);
}
