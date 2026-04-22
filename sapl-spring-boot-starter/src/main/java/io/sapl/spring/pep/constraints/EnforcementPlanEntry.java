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
package io.sapl.spring.pep.constraints;

import java.util.Comparator;

import org.jspecify.annotations.NonNull;

import io.sapl.api.model.Value;

/**
 * One entry in an {@link EnforcementPlan}: a typed handler with its priority,
 * obligation/advice tag, and the
 * originating constraint value used to drive consistent failure reporting.
 */
public record EnforcementPlanEntry<T>(
        ConstraintHandler<T> handler,
        int priority,
        ConstraintType constraintType,
        Value constraint) implements Comparable<EnforcementPlanEntry<?>> {

    private static final Comparator<EnforcementPlanEntry<?>> COMPARATOR = Comparator
            .<EnforcementPlanEntry<?>>comparingInt(EnforcementPlanEntry::priority)
            .thenComparingInt(entry -> handlerOrdinal(entry.handler()));

    /**
     * Orders by {@link #priority} ascending, then by handler kind
     * {@link ConstraintHandler.Runner} &lt; {@link ConstraintHandler.Mapper} &lt;
     * {@link ConstraintHandler.Consumer}.
     * {@link #constraintType} and {@link #constraint} are not part of the ordering.
     */
    @Override
    public int compareTo(@NonNull EnforcementPlanEntry<?> other) {
        return COMPARATOR.compare(this, other);
    }

    private static int handlerOrdinal(ConstraintHandler<?> handler) {
        return switch (handler) {
        case ConstraintHandler.Runner ignored      -> 0;
        case ConstraintHandler.Mapper<?> ignored   -> 1;
        case ConstraintHandler.Consumer<?> ignored -> 2;
        };
    }
}
