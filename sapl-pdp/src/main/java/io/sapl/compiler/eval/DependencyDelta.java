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
package io.sapl.compiler.eval;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.sapl.api.model.Occurrence;
import io.sapl.api.model.SubscriptionKey;
import lombok.val;

/**
 * Difference between two dependency maps produced by successive
 * {@code evaluate(ctx)} calls. Computed by the trigger loop between
 * rounds and used to drive the AttributeStore: subscribe everything
 * in {@link #added}, release everything in {@link #removed}, leave
 * {@link #kept} alone.
 * <p>
 * The occurrence lists in {@link #added} and {@link #kept} are taken
 * from the new (current) round and reflect every call site that now
 * depends on the invocation. The list in {@link #removed} is taken
 * from the old (previous) round so the trigger loop can report what
 * those subscriptions were used for, if needed for tracing or
 * coverage records.
 *
 * @param added invocations that appear in the current round but not
 * in the previous round; the trigger loop subscribes these
 * @param removed invocations that appeared in the previous round but
 * are absent in the current round; the trigger loop releases these
 * @param kept invocations present in both rounds; the trigger loop
 * leaves the AttributeStore alone but may forward updated occurrence
 * lists to observers
 *
 * @since 4.2.0
 */
public record DependencyDelta(
        Map<SubscriptionKey, List<Occurrence>> added,
        Map<SubscriptionKey, List<Occurrence>> removed,
        Map<SubscriptionKey, List<Occurrence>> kept) {

    /**
     * Computes the delta from {@code previous} to {@code current}. One
     * pass over each map; classification by hash-keyed lookup. The
     * returned maps reuse occurrence list references from the source
     * maps (no copying), so callers must treat them as read-only.
     *
     * @param previous dependencies returned by the prior evaluation
     * pass (the empty map for the very first round)
     * @param current dependencies returned by the current evaluation
     * pass
     * @return the categorisation of every invocation across both maps
     */
    public static DependencyDelta compute(Map<SubscriptionKey, List<Occurrence>> previous,
            Map<SubscriptionKey, List<Occurrence>> current) {
        val added   = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(current.size());
        val kept    = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(current.size());
        val removed = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(previous.size());

        for (val entry : current.entrySet()) {
            if (previous.containsKey(entry.getKey())) {
                kept.put(entry.getKey(), entry.getValue());
            } else {
                added.put(entry.getKey(), entry.getValue());
            }
        }
        for (val entry : previous.entrySet()) {
            if (!current.containsKey(entry.getKey())) {
                removed.put(entry.getKey(), entry.getValue());
            }
        }

        return new DependencyDelta(added, removed, kept);
    }
}
