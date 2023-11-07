/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.function.UnaryOperator;

/**
 * Classes implementing this interface can be wired into the PDP to intercept
 * any AuthorizationSubscription and potentially modify it before handing it
 * downstream.
 * <p>
 * The priority defines the evaluation sequence if multiple interceptor classes
 * are present. No assumptions can be made about the evaluation sequence of
 * interceptors with identical priority.
 */
@FunctionalInterface
public interface AuthorizationSubscriptionInterceptor
        extends UnaryOperator<AuthorizationSubscription>, Comparable<AuthorizationSubscriptionInterceptor> {
    /**
     * @return the interceptor priority
     */
    default Integer getPriority() {
        return 0;
    }

    @Override
    default int compareTo(AuthorizationSubscriptionInterceptor other) {
        return getPriority().compareTo(other.getPriority());
    }
}
