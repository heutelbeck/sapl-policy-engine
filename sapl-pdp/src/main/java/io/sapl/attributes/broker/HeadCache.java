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
package io.sapl.attributes.broker;

import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import lombok.NonNull;
import lombok.val;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-subscription cache of "head" attribute values for the
 * evaluator-side head semantic. The {@link AttributeBroker} is
 * head-agnostic for value delivery; the policy semantic "freeze
 * this attribute at first observation" lives here.
 * <p>
 * Lifecycle: one instance per active broker subscription, threaded
 * through the consumer's onUpdate callback. Five steps per fire:
 * <ol>
 * <li>{@link #merge}: build the snapshot the policy reads, with
 * cached head values taking precedence over the broker's delivery
 * for the same head key.</li>
 * <li>(evaluate the policy)</li>
 * <li>{@link #captureFrom}: putIfAbsent for any head key the broker
 * just delivered.</li>
 * <li>{@link #retainOnly}: drop cache entries the policy no longer
 * references.</li>
 * <li>{@link #brokerDepsFor}: filter eval deps to broker deps,
 * dropping head keys the cache now serves.</li>
 * </ol>
 * <p>
 * Re-entry semantic (head dep removed from eval deps then later
 * re-added): the eviction in step 4 drops the cache entry, so on
 * re-entry the head key flows to the broker again and the next fire
 * captures the value the broker delivers at that moment. The "head"
 * value is therefore "value at first observation in the current
 * active-deps period," not "value at first observation in this
 * consumer's lifetime."
 * <p>
 * Single-threaded by construction: the broker's dispatch coalescer
 * serializes onUpdate per consumer, so the cache sees no concurrent
 * access. Empty-cache fast paths make every method a no-op for
 * head-free policies beyond one {@link HashMap} allocation at
 * subscription open.
 *
 * @since 4.2.0
 */
public final class HeadCache {

    private final Map<SubscriptionKey, AttributeSnapshot> cache = new HashMap<>();

    /**
     * Step 1: builds the snapshot the policy actually reads. For
     * every head key in the cache, the cached value overrides any
     * value the broker may have delivered for that key.
     *
     * @param brokerSnapshot the snapshot the broker fired with
     * @return a merged snapshot suitable for the policy evaluator;
     * returns the broker snapshot unchanged when the cache is empty
     */
    public @NonNull Map<SubscriptionKey, AttributeSnapshot> merge(
            @NonNull Map<SubscriptionKey, AttributeSnapshot> brokerSnapshot) {
        if (cache.isEmpty()) {
            return brokerSnapshot;
        }
        val full = new HashMap<>(brokerSnapshot);
        full.putAll(cache);
        return full;
    }

    /**
     * Step 3: captures newly-observed head values via
     * {@code putIfAbsent}; previously-cached entries are not
     * overwritten ({@code first-observation} semantic). Call after
     * the policy evaluation has returned so the cache stays pristine
     * if evaluation throws.
     *
     * @param brokerSnapshot the snapshot the broker fired with;
     * head-keyed entries that are not yet in the cache are captured
     */
    public void captureFrom(@NonNull Map<SubscriptionKey, AttributeSnapshot> brokerSnapshot) {
        for (val entry : brokerSnapshot.entrySet()) {
            if (entry.getKey().head()) {
                cache.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Step 4: drops cache entries whose keys are no longer in the
     * evaluator's logical dep set. A head dep that later re-enters
     * the dep set re-subscribes through the broker and re-captures
     * on its next fire.
     *
     * @param newEvalDeps the evaluator's logical dep set after the
     * round just completed
     */
    public void retainOnly(@NonNull Set<SubscriptionKey> newEvalDeps) {
        if (!cache.isEmpty()) {
            cache.keySet().retainAll(newEvalDeps);
        }
    }

    /**
     * Step 5: filters the evaluator's logical dep set down to the
     * broker's effective dep set. Head keys whose values are now
     * served from the cache are dropped, so the broker no longer
     * delivers them and the underlying subscription releases.
     * <p>
     * If filtering would leave the result empty (the policy depends
     * only on cached head values), the original set is returned
     * instead. The broker's contract requires a non-empty dep set,
     * and the eval ignores the broker's redundant fires via the
     * cache during merge.
     *
     * @param evalDeps the evaluator's logical dep set
     * @return the broker dep set; {@code evalDeps} unchanged when
     * the cache is empty
     */
    public @NonNull Set<SubscriptionKey> brokerDepsFor(@NonNull Set<SubscriptionKey> evalDeps) {
        if (cache.isEmpty()) {
            return evalDeps;
        }
        val result = new HashSet<>(evalDeps);
        result.removeIf(k -> k.head() && cache.containsKey(k));
        if (result.isEmpty()) {
            return evalDeps;
        }
        return result;
    }
}
