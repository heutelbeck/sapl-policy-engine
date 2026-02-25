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
package io.sapl.spring.method.blocking;

import lombok.Getter;

/**
 * Ordering constants for SAPL method-level Policy Enforcement Point
 * interceptors.
 * <p>
 * SAPL PEPs are placed at the innermost positions of the AOP interceptor chain
 * (highest order values). This ensures that when combined with
 * {@code @Transactional}, constraint handler failures after method execution
 * propagate through the {@code TransactionInterceptor} and trigger a rollback.
 * <p>
 * From inner to outer:
 * <ol>
 * <li>{@link #POST_ENFORCE} ({@code Integer.MAX_VALUE}) - innermost</li>
 * <li>{@link #PRE_ENFORCE} ({@code Integer.MAX_VALUE - 1})</li>
 * <li>{@link #STREAMING} ({@code Integer.MAX_VALUE - 2})</li>
 * <li>{@code TransactionInterceptor} - should be configured with order
 * {@link #TRANSACTION_ORDER} ({@code Integer.MAX_VALUE - 3})</li>
 * </ol>
 * <p>
 * All three enforcement types (pre, post, streaming) are mutually exclusive on
 * any given method.
 * <p>
 * To configure the transaction interceptor order, use
 * {@code @EnableTransactionManagement(order = SaplAuthorizationInterceptorsOrder.TRANSACTION_ORDER)}.
 *
 * @since 3.0.0
 */
@Getter
public enum SaplAuthorizationInterceptorsOrder {

    POST_ENFORCE(Integer.MAX_VALUE),
    PRE_ENFORCE(Integer.MAX_VALUE - 1),
    STREAMING(Integer.MAX_VALUE - 2);

    /**
     * Recommended order for {@code TransactionInterceptor} when used with SAPL
     * enforcement. This places the transaction boundary just outside the SAPL
     * PEPs so that constraint handler failures trigger transaction rollback.
     */
    public static final int TRANSACTION_ORDER = Integer.MAX_VALUE - 3;

    private final int order;

    SaplAuthorizationInterceptorsOrder(int order) {
        this.order = order;
    }
}
