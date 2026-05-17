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
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Eval-loop primitives that drive an {@link AttributeBroker}
 * subscription through one round per fire, with eval-side head
 * caching threaded in via {@link HeadCache}. Two flavors:
 * <ul>
 * <li>{@link #openWithHead}: streaming. Opens a subscription, runs
 * the evaluator on every fire, hands the result to a side-effecting
 * consumer (typically writing to a sink stream).</li>
 * <li>{@link #awaitFirstResult}: blocking. Subscribes, runs the
 * evaluator on every fire until the result builder produces a
 * non-null value, completes a future with that value, releases the
 * subscription.</li>
 * </ul>
 *
 * @since 4.2.0
 */
@UtilityClass
public class BrokerEvalLoops {

    /**
     * Streaming flavor: drive an eval loop until the caller closes
     * the returned {@link AttributeBroker.Subscription}. On every
     * broker fire, builds the merged snapshot (HeadCache-aware),
     * runs the evaluator, hands the result to {@code onResult}
     * (which decides whether and where to emit), then extracts the
     * next-round dep set and feeds it back to the broker after the
     * HeadCache wind-down.
     *
     * @param <R> the evaluator's per-round result type
     * @param broker the broker to subscribe through
     * @param subscriptionId per-subscription id (must be unique per
     * broker; typically a fresh UUID)
     * @param initialDeps the evaluator's initial dep set; head keys
     * propagate to the broker on first call and are absorbed into
     * the cache after the first fire
     * @param evaluator runs against the merged snapshot for one
     * round and returns the result {@code R}
     * @param onResult side-effecting consumer of the per-round
     * result and the merged snapshot; typically emits to a sink when
     * the result represents a complete round
     * @param nextDeps extracts the evaluator's logical dep set from
     * the result; the helper filters head-cached keys before
     * returning the broker-effective dep set
     * @return the broker subscription handle; the caller is
     * responsible for closing it (typically via the sink's onClose)
     */
    public static <R> AttributeBroker.Subscription openWithHead(AttributeBroker broker, String subscriptionId,
            Set<SubscriptionKey> initialDeps, Function<Map<SubscriptionKey, AttributeSnapshot>, R> evaluator,
            BiConsumer<R, Map<SubscriptionKey, AttributeSnapshot>> onResult,
            Function<R, Set<SubscriptionKey>> nextDeps) {
        val headCache = new HeadCache();
        return broker.open(subscriptionId, headCache.brokerDepsFor(initialDeps), brokerSnap -> {
            val full = headCache.merge(brokerSnap);
            val r    = evaluator.apply(full);
            onResult.accept(r, full);
            val newDeps = nextDeps.apply(r);
            headCache.captureFrom(brokerSnap);
            headCache.retainOnly(newDeps);
            return headCache.brokerDepsFor(newDeps);
        });
    }

    /**
     * Blocking flavor: drive an eval loop until {@code builder}
     * produces a non-null value, then return that value and release
     * the subscription. Built on {@link #openWithHead}; the builder
     * is consulted on every fire, returning {@code null} when the
     * round is incomplete (e.g., the policy body did not resolve)
     * and the completion value when terminal.
     *
     * @param <R> the evaluator's per-round result type
     * @param <V> the completion value type
     * @param broker the broker to subscribe through
     * @param subscriptionId per-subscription id
     * @param initialDeps the evaluator's initial dep set
     * @param evaluator runs against the merged snapshot for one
     * round
     * @param builder maps a per-round result and merged snapshot to
     * a completion value, or {@code null} when the round is not
     * terminal
     * @param nextDeps extracts the evaluator's logical dep set from
     * the result
     * @return the first non-null value the builder produced
     * @throws InterruptedException if the caller is interrupted
     * while waiting
     */
    public static <R, V> V awaitFirstResult(AttributeBroker broker, String subscriptionId,
            Set<SubscriptionKey> initialDeps, Function<Map<SubscriptionKey, AttributeSnapshot>, R> evaluator,
            BiFunction<R, Map<SubscriptionKey, AttributeSnapshot>, V> builder,
            Function<R, Set<SubscriptionKey>> nextDeps) throws InterruptedException {
        val future = new CompletableFuture<V>();
        try (val ignored = openWithHead(broker, subscriptionId, initialDeps, evaluator, (r, snap) -> {
            val v = builder.apply(r, snap);
            if (v != null) {
                future.complete(v);
            }
        }, nextDeps)) {
            return future.get();
        } catch (ExecutionException ee) {
            val cause = ee.getCause();
            throw new IllegalStateException(cause == null ? ee.toString() : cause.toString(), ee);
        }
    }
}
