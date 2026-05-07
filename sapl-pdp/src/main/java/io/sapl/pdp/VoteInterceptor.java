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
package io.sapl.pdp;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.compiler.document.TracedVote;
import reactor.core.publisher.SignalType;

/**
 * Interceptor for processing votes during policy evaluation. Receives
 * a {@link TracedVote} carrying the vote, its emit timestamp, and the
 * per-evaluation attribute trace.
 * <p>
 * Interceptors are observability concerns: the PDP catches and logs
 * any exception thrown from interceptor methods rather than
 * propagating it to authorization callers. Interceptors must not be
 * used to influence authorization decisions.
 */
public interface VoteInterceptor extends Comparable<VoteInterceptor> {

    /**
     * @return the priority of this interceptor (lower executes first)
     */
    int priority();

    /**
     * Processes a vote.
     *
     * @param vote the traced vote (vote + timestamp + attribute trace)
     * @param subscriptionId the per-evaluation subscription identifier
     * @param authorizationSubscription the authorization subscription
     * that was evaluated
     */
    void intercept(TracedVote vote, String subscriptionId, AuthorizationSubscription authorizationSubscription);

    /**
     * Called when a new authorization subscription stream begins.
     *
     * @param subscriptionId the per-evaluation subscription identifier
     * @param authorizationSubscription the authorization subscription
     * being evaluated
     * @param pdpId the tenant identifier the request was routed to
     */
    default void onSubscribe(String subscriptionId, AuthorizationSubscription authorizationSubscription, String pdpId) {
        // no-op by default
    }

    /**
     * Called when an authorization subscription stream ends.
     *
     * @param subscriptionId the per-evaluation subscription identifier
     * @param signal the termination signal: {@link SignalType#ON_COMPLETE},
     * {@link SignalType#ON_ERROR}, or {@link SignalType#CANCEL}
     */
    default void onUnsubscribe(String subscriptionId, SignalType signal) {
        // no-op by default
    }

    @Override
    default int compareTo(VoteInterceptor otherInterceptor) {
        return Integer.compare(priority(), otherInterceptor.priority());
    }
}
