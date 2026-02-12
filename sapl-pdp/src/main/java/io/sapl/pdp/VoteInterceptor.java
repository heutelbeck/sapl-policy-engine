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
import io.sapl.compiler.document.TimestampedVote;

/**
 * Interceptor for processing votes during policy evaluation.
 */
public interface VoteInterceptor extends Comparable<VoteInterceptor> {

    /**
     * @return the priority of this interceptor (lower executes first)
     */
    int priority();

    /**
     * Processes a vote.
     *
     * @param vote the vote to intercept
     * @param subscriptionId the subscription identifier
     * @param authorizationSubscription the authorization subscription that was
     * evaluated
     */
    void intercept(TimestampedVote vote, String subscriptionId, AuthorizationSubscription authorizationSubscription);

    /**
     * Called when a new authorization subscription stream begins.
     *
     * @param subscriptionId the subscription identifier
     * @param authorizationSubscription the authorization subscription being
     * evaluated
     */
    default void onSubscribe(String subscriptionId, AuthorizationSubscription authorizationSubscription) {
        // no-op by default
    }

    /**
     * Called when an authorization subscription stream ends.
     *
     * @param subscriptionId the subscription identifier
     */
    default void onUnsubscribe(String subscriptionId) {
        // no-op by default
    }

    @Override
    default int compareTo(VoteInterceptor otherInterceptor) {
        return Integer.compare(priority(), otherInterceptor.priority());
    }
}
