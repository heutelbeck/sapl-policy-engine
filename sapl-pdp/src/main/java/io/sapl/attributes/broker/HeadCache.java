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
 * Per-subscription cache of "head" attribute values.
 * <p>
 * What: stores the first value observed for each {@code head=true}
 * SubscriptionKey, and serves that value on every subsequent read
 * until the key leaves the eval's dep set.
 * <p>
 * Why: the {@link AttributeBroker} always delivers the latest value
 * for every key. Policies that wrote {@code |head} want
 * "value frozen at first observation" instead. That semantic lives
 * in the eval, not in the broker.
 * <p>
 * How: five-step pipeline per fire, one method per step.
 * <ol>
 * <li>{@link #merge}: build the snapshot the policy reads. Cached
 * head values override the broker's delivery for the same key.</li>
 * <li>(evaluate the policy)</li>
 * <li>{@link #captureFrom}: putIfAbsent for any head key the broker
 * just delivered. First observation wins; nothing overwrites.</li>
 * <li>{@link #retainOnly}: drop cache entries the policy no longer
 * references. If a head dep re-enters later, it captures a fresh
 * value at that point (not the original one).</li>
 * <li>{@link #brokerDepsFor}: drop head keys from broker deps if
 * the cache already serves them. The broker stops delivering them.</li>
 * </ol>
 * <p>
 * Thread safety: none needed. The broker's
 * {@link DispatchCoalescer} serializes onUpdate per consumer, so
 * the cache only ever sees one thread.
 * <p>
 * Cost when no head deps are used: one empty {@link HashMap} per
 * subscription. Every method short-circuits on the empty-cache
 * branch.
 *
 * @since 4.1.0
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
