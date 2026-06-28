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
package io.sapl.node;

import io.sapl.api.pdp.MultiAuthorizationSubscription;
import lombok.experimental.UtilityClass;

/**
 * Shared SAPL Node limits for public multi-subscription transports.
 */
@UtilityClass
public class MultiSubscriptionLimits {

    public static final int DEFAULT_MAX_MULTI_SUBSCRIPTION_COUNT = 256;

    private static final String ERROR_MAX_MULTI_SUBSCRIPTION_COUNT      = "Maximum multi-subscription count must be positive.";
    private static final String ERROR_MULTI_SUBSCRIPTION_COUNT_EXCEEDED = "Multi-subscription contains %d entries, exceeding the configured maximum of %d.";

    /**
     * Validates the configured maximum.
     *
     * @param maxMultiSubscriptionCount the configured maximum
     * @return the validated maximum
     * @throws IllegalArgumentException if the maximum is not positive
     */
    public static int requirePositiveMax(int maxMultiSubscriptionCount) {
        if (maxMultiSubscriptionCount <= 0) {
            throw new IllegalArgumentException(ERROR_MAX_MULTI_SUBSCRIPTION_COUNT);
        }
        return maxMultiSubscriptionCount;
    }

    /**
     * Tests whether a multi-subscription exceeds a configured maximum.
     *
     * @param subscription the multi-subscription
     * @param maxMultiSubscriptionCount the configured maximum
     * @return true when the subscription count exceeds the maximum
     * @throws IllegalArgumentException if the maximum is not positive
     */
    public static boolean exceedsMaxCount(MultiAuthorizationSubscription subscription, int maxMultiSubscriptionCount) {
        return subscription.size() > requirePositiveMax(maxMultiSubscriptionCount);
    }

    /**
     * Builds the user-facing rejection message for an oversized
     * multi-subscription.
     *
     * @param subscription the multi-subscription
     * @param maxMultiSubscriptionCount the configured maximum
     * @return the rejection message
     * @throws IllegalArgumentException if the maximum is not positive
     */
    public static String exceededMessage(MultiAuthorizationSubscription subscription, int maxMultiSubscriptionCount) {
        return ERROR_MULTI_SUBSCRIPTION_COUNT_EXCEEDED.formatted(subscription.size(),
                requirePositiveMax(maxMultiSubscriptionCount));
    }
}
