/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationSubscriptionInterceptorTests {

    @Test
    void defaultPriorityIsZero() {
        AuthorizationSubscriptionInterceptor interceptor = t -> t;
        assertThat(interceptor.getPriority()).isZero();
    }

    @Test
    void compareTo() {
        AuthorizationSubscriptionInterceptor interceptor1 = t -> t;
        AuthorizationSubscriptionInterceptor interceptor2 = t -> t;
        assertThat(interceptor1).isEqualByComparingTo(interceptor2);
    }
}
