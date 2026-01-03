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

import lombok.NonNull;

/**
 * Pairs an authorization decision with the identifier of the corresponding
 * subscription, enabling correlation between
 * subscriptions and decisions in multi-subscription scenarios.
 *
 * @param subscriptionId
 * the unique identifier of the corresponding subscription
 * @param decision
 * the authorization decision
 */
public record IdentifiableAuthorizationDecision(
        @NonNull String subscriptionId,
        @NonNull AuthorizationDecision decision) {

    /**
     * An indeterminate decision without a subscription ID. Used when the PDP cannot
     * determine which subscription caused
     * the error.
     */
    public static final IdentifiableAuthorizationDecision INDETERMINATE = new IdentifiableAuthorizationDecision("",
            AuthorizationDecision.INDETERMINATE);
}
