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
package io.sapl.compiler.document;

import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.attributes.store.AttributeStore;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * Utilities operating on {@link Voter} values.
 */
@UtilityClass
public class Voters {

    /**
     * Bridges the callback-driven {@link AttributeStore} to a synchronous
     * single-vote pickup. Subscribes to {@code initialDependencies},
     * runs {@code evaluator} on each fulfilled snapshot, and returns the
     * first non-null vote produced. The store handle is released before
     * this method returns, regardless of outcome.
     *
     * @param store the attribute store to subscribe through
     * @param subscriptionId per-evaluation id for the store
     * @param initialDependencies the keys to subscribe to initially
     * @param evaluator a callback that evaluates against a snapshot and
     * returns a fresh {@link VoteResult}; the next dependency set fed
     * back to the store is taken from {@link VoteResult#dependencies()}
     * @return the first non-null vote produced by the evaluator
     * @throws InterruptedException if the calling thread is interrupted
     * while waiting for the first complete snapshot
     */
    public static Vote awaitFirstVote(AttributeStore store, String subscriptionId,
            Set<SubscriptionKey> initialDependencies,
            Function<Map<SubscriptionKey, AttributeSnapshot>, VoteResult> evaluator) throws InterruptedException {
        val future = new CompletableFuture<Vote>();
        try (val ignored = store.open(subscriptionId, initialDependencies, snapshot -> {
            val r = evaluator.apply(snapshot);
            if (r.vote() != null) {
                future.complete(r.vote());
            }
            return r.dependencies().keySet();
        })) {
            return future.get();
        } catch (ExecutionException ee) {
            val cause = ee.getCause();
            throw new IllegalStateException(cause == null ? ee.toString() : cause.toString(), ee);
        }
    }

    /**
     * Returns the snapshot entries for the keys in
     * {@code result.dependencies()}. Bounded by the dependency set.
     */
    public static Map<SubscriptionKey, AttributeSnapshot> readSnapshot(VoteResult result,
            Map<SubscriptionKey, AttributeSnapshot> snapshot) {
        val filtered = new HashMap<SubscriptionKey, AttributeSnapshot>(result.dependencies().size());
        for (val key : result.dependencies().keySet()) {
            val entry = snapshot.get(key);
            if (entry != null) {
                filtered.put(key, entry);
            }
        }
        return Map.copyOf(filtered);
    }
}
