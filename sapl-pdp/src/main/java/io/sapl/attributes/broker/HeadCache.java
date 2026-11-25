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
 * Per-subscription first-observation cache for {@code |head} attributes.
 * <p>
 * What: stores the first value observed for each {@code head=true}
 * SubscriptionKey, and serves that value on every subsequent read
 * until the caller removes it through {@link #retainOnly}.
 * <p>
 * The broker supplies current live inputs. A policy using {@code |head}
 * deliberately keeps the first delivered {@link AttributeSnapshot}, including
 * its value and timestamp, while the caller retains that dependency. The
 * standard loop retains the current logical dependency set after each round.
 * UNDEFINED and error values can also be first observations. This freezing is
 * evaluation semantics and does not change the repository binding or another
 * consumer's live observation. Ordinary live attributes are not cached here.
 * A missing input is still pending and cannot become a cached observation.
 * Partial broker rounds may update live inputs while a requested head is
 * absent. Retain head lifetimes by logical dependencies, not by presence in
 * that particular broker input map.
 * <p>
 * How: five-step pipeline per fire, one method per step.
 * <ol>
 * <li>{@link #merge}: build the snapshot the policy reads. Cached
 * head values override the broker's delivery for the same key.</li>
 * <li>(evaluate the policy)</li>
 * <li>{@link #captureFrom}: putIfAbsent for any head key the broker
 * just delivered. First observation wins. Nothing overwrites.</li>
 * <li>{@link #retainOnly}: drop cache entries outside the caller's retained
 * set. If a removed head dep reenters later, its next delivered observation
 * becomes the new first value, possibly from an existing warm invocation.</li>
 * <li>{@link #brokerDepsFor}: omit cached head keys when other broker
 * dependencies remain. If every remaining dependency is a cached head, keep
 * the original non-empty set to satisfy the broker contract. The standard loop releases
 * its subscription when no unresolved dependencies remain.</li>
 * </ol>
 * <p>
 * Callers must serialize access to this cache. The broker serializes callbacks
 * for one open subscription, so {@link BrokerEvalLoops} uses one cache without
 * additional locking. Successive callbacks may run on different threads;
 * serialization does not imply thread confinement.
 * <p>
 * Cost when no head deps are used: one empty {@link HashMap} per
 * subscription. Merge, retention and dependency filtering have empty-cache fast
 * paths; capture still examines the delivered keys for newly reached heads.
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
     * @return stable inputs that the evaluator must treat as read-only. Returns
     * the broker map unchanged when the cache is empty, or a private mutable map
     * containing the head overrides
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
     * @param brokerSnapshot the snapshot the broker fired with.
     * Head-keyed entries that are not yet in the cache are captured
     */
    public void captureFrom(@NonNull Map<SubscriptionKey, AttributeSnapshot> brokerSnapshot) {
        for (val entry : brokerSnapshot.entrySet()) {
            if (entry.getKey().head()) {
                cache.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Step 4: drops cache entries whose keys are outside the caller's retained
     * set. The standard evaluation loop supplies its current logical set.
     * A head dep that later reenters re-subscribes through the broker and recaptures
     * on its next fire.
     *
     * @param newEvalDeps dependencies whose cached heads the caller retains
     * after the round just completed
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
     * instead. Callers that keep a broker subscription open must return a
     * non-empty set. The standard evaluation loop releases the subscription
     * once every dependency is served by a frozen head.
     *
     * @param evalDeps the evaluator's logical dep set
     * @return the broker dep set; {@code evalDeps} unchanged when
     * the cache is empty
     */
    public @NonNull Set<SubscriptionKey> brokerDepsFor(@NonNull Set<SubscriptionKey> evalDeps) {
        val remaining = unresolvedDependencies(evalDeps);
        return remaining.isEmpty() ? evalDeps : remaining;
    }

    /** The logical keys that still require a broker observation. */
    Set<SubscriptionKey> unresolvedDependencies(Set<SubscriptionKey> evalDeps) {
        if (cache.isEmpty()) {
            return evalDeps;
        }
        val remaining = new HashSet<>(evalDeps);
        remaining.removeAll(cache.keySet());
        return remaining;
    }
}
