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
package io.sapl.api.pdp.internal;

import java.util.function.UnaryOperator;

/**
 * Intercepts traced decisions for logging, auditing, or modification. Ordered
 * by priority (lower values execute first).
 */
@FunctionalInterface
public interface TracedDecisionInterceptor
        extends UnaryOperator<TracedDecision>, Comparable<TracedDecisionInterceptor> {

    /**
     * @return the interceptor priority (lower executes first, default 0)
     */
    default Integer getPriority() {
        return 0;
    }

    @Override
    default int compareTo(TracedDecisionInterceptor other) {
        return getPriority().compareTo(other.getPriority());
    }
}
