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
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Two helpers that drive the standard eval loop against an
 * {@link AttributeBroker} with eval-side head caching threaded
 * in via {@link HeadCache}.
 * <p>
 * The standard loop, on every broker fire: merge cache into the snapshot,
 * evaluate, emit, capture head values, evict
 * cache entries the eval dropped, filter head keys out of the next broker dep
 * set. The same pattern across many call
 * sites. These helpers exist so it's written once.
 * <p>
 * Two flavors:
 * <ul>
 * <li>{@link #openWithHead}: streaming. Keeps the subscription open; emits via
 * a caller-supplied consumer on every
 * fire.</li>
 * <li>{@link #awaitFirstResult}: blocking. Returns the first non-null value the
 * builder produces, then closes the
 * subscription.</li>
 * </ul>
 *
 * @since 4.1.0
 */
@Slf4j
@UtilityClass
public class BrokerEvalLoops {

    private static final String ERROR_EVAL_CALLBACK_THREW = "Eval-loop callback for subscription {} threw (engine invariant: it must never throw); surfacing a fail-closed decision to the subscriber: {}";
    private static final String ERROR_NO_DECISION         = "Evaluation produced no decision and no remaining dependencies.";

    /**
     * Streaming flavor: opens a broker subscription that runs the eval loop on
     * every fire until the caller closes the
     * returned handle.
     * <p>
     * Each fire: merge HeadCache into the broker snapshot, run {@code evaluator},
     * hand the result to {@code onResult},
     * extract next-round deps via {@code nextDeps}, update the HeadCache, return
     * the filtered broker deps for the next
     * round.
     *
     * @param <R>
     * per-round result type
     * @param broker
     * broker to subscribe through
     * @param subscriptionId
     * broker-unique subscription id
     * @param initialDeps
     * eval's initial dep set
     * @param evaluator
     * runs against the merged snapshot, returns the round result
     * @param onResult
     * side-effecting consumer of the result and the merged snapshot. The typical
     * body is "if the result is
     * terminal, emit it on a sink"
     * @param nextDeps
     * extracts the eval's logical dep set from the result for the next round
     * @param onTerminate
     * invoked once when the loop will not continue, with the cause: a non-null
     * {@link RuntimeException} when
     * a round's {@code evaluator}, {@code onResult}, or {@code nextDeps} threw (the
     * broker holds no decision
     * sink, so the caller must emit a fail-closed decision and complete the
     * stream), or {@code null} when
     * the policy collapsed to a constant (no attribute deps remain; the final
     * decision was already emitted
     * via {@code onResult}, so the caller need only complete the stream). After it
     * fires, the loop stops
     * re-evaluating and the subscription idles until the consumer closes it
     *
     * @return the subscription handle. The caller closes it
     */
    public static <R> AttributeBroker.Subscription openWithHead(AttributeBroker broker, String subscriptionId,
            Set<SubscriptionKey> initialDeps, Function<Map<SubscriptionKey, AttributeSnapshot>, R> evaluator,
            BiConsumer<R, Map<SubscriptionKey, AttributeSnapshot>> onResult, Function<R, Set<SubscriptionKey>> nextDeps,
            Consumer<@Nullable RuntimeException> onTerminate) {
        val headCache         = new HeadCache();
        val initialBrokerDeps = headCache.brokerDepsFor(initialDeps);
        val terminated        = new AtomicBoolean(false);
        return broker.open(subscriptionId, initialBrokerDeps, brokerSnap -> {
            if (terminated.get()) {
                // A prior round already terminated the loop. Idle on the last dep set
                // (broker requires non-empty) until the consumer closes the stream.
                return initialBrokerDeps;
            }
            try {
                val full = headCache.merge(brokerSnap);
                val r    = evaluator.apply(full);
                onResult.accept(r, full);
                val newDeps = nextDeps.apply(r);
                headCache.captureFrom(brokerSnap);
                headCache.retainOnly(newDeps);
                val brokerDeps = headCache.brokerDepsFor(newDeps);
                if (brokerDeps.isEmpty()) {
                    // The policy collapsed to a constant: no attribute deps remain. The
                    // final decision was already emitted via onResult. Complete the stream
                    // (clean teardown) rather than hand the broker an illegal empty set.
                    terminated.set(true);
                    onTerminate.accept(null);
                    return initialBrokerDeps;
                }
                return brokerDeps;
            } catch (RuntimeException e) {
                terminated.set(true);
                log.error(ERROR_EVAL_CALLBACK_THREW, subscriptionId, e.getMessage(), e);
                onTerminate.accept(e);
                return initialBrokerDeps;
            }
        });
    }

    /**
     * Blocking flavor: drives the eval loop until {@code builder} produces a
     * non-null value, returns that value,
     * releases the subscription.
     * <p>
     * Built on {@link #openWithHead}. The builder is consulted on every fire and
     * returns {@code null} when the round is
     * not terminal (e.g., a dep is still missing) or the completion value when the
     * round resolves.
     *
     * @param <R>
     * per-round result type
     * @param <V>
     * completion value type
     * @param broker
     * broker to subscribe through
     * @param subscriptionId
     * broker-unique subscription id
     * @param initialDeps
     * eval's initial dep set
     * @param evaluator
     * runs against the merged snapshot, returns the round result
     * @param builder
     * maps a result + merged snapshot to a completion value, or {@code null} if the
     * round is not terminal
     * @param nextDeps
     * extracts the eval's logical dep set from the result for the next round
     *
     * @return the first non-null value the builder produced
     *
     * @throws InterruptedException
     * if the caller is interrupted while waiting
     * @throws EvaluationException
     * if {@code evaluator} or {@code builder} threw during dispatch
     */
    public static <R, V> V awaitFirstResult(AttributeBroker broker, String subscriptionId,
            Set<SubscriptionKey> initialDeps, Function<Map<SubscriptionKey, AttributeSnapshot>, R> evaluator,
            BiFunction<R, Map<SubscriptionKey, AttributeSnapshot>, V> builder,
            Function<R, Set<SubscriptionKey>> nextDeps) throws InterruptedException, EvaluationException {
        val future = new CompletableFuture<V>();
        // The broker dispatches the callback off the caller's thread and
        // swallows any throw from it. Each callback stage is wrapped so a
        // failure completes the future exceptionally instead of leaving
        // future.get() blocked forever.
        final Function<Map<SubscriptionKey, AttributeSnapshot>, R> guardedEvaluator = snap -> failFuture(future,
                () -> evaluator.apply(snap));
        final Function<R, Set<SubscriptionKey>>                    guardedNextDeps  = r -> failFuture(future,
                () -> nextDeps.apply(r));
        try {
            try (val ignored = openWithHead(broker, subscriptionId, initialDeps, guardedEvaluator, (r, snap) -> {
                try {
                    val v = builder.apply(r, snap);
                    if (v != null) {
                        future.complete(v);
                    }
                } catch (RuntimeException e) {
                    future.completeExceptionally(e);
                }
            }, guardedNextDeps, cause -> future
                    .completeExceptionally(cause != null ? cause : new IllegalStateException(ERROR_NO_DECISION)))) {
                return future.get();
            }
        } catch (ExecutionException ee) {
            val cause = ee.getCause();
            throw new EvaluationException(cause == null ? ee.toString() : cause.toString(), cause == null ? ee : cause);
        } catch (RuntimeException re) {
            throw new EvaluationException(re.getMessage(), re);
        }
    }

    private static <T> T failFuture(CompletableFuture<?> future, Supplier<T> stage) {
        try {
            return stage.get();
        } catch (RuntimeException e) {
            future.completeExceptionally(e);
            throw e;
        }
    }
}
