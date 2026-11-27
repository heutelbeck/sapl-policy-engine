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
 * Evaluation-side lifecycle for callbacks registered with an
 * {@link AttributeBroker}. The broker captures attribute inputs and manages
 * source subscriptions. These helpers invoke the supplied evaluator for each
 * round, report its result, reconcile logical dependencies and implement
 * {@code |head} through a per-subscription {@link HeadCache}.
 * <p>
 * Every round invokes the evaluator again against one stable input map. It
 * must reevaluate the compiled expression or voter using that map, rather than
 * resume an incomplete expression with live attribute results from an earlier
 * round. If a lazy A-and-B condition first reads A=true and discovers B, then
 * the broker installs an atomic A=false/B=true update before the next capture,
 * the next round must reread A=false. It can then finish false without reading
 * B. Carrying the old A=true into evaluation of the new B would create a false grant.
 * <p>
 * An update of a previously observed live input can start a round while a newly
 * requested attribute still has no value. The broker omits that pending key.
 * Reevaluation can drop it after a short circuit or remain incomplete, without
 * emitting a decision for the incomplete result. First deliveries of a new
 * dependency set are collected together unless a known input changes.
 * <p>
 * Each callback follows this order:
 * <ol>
 * <li>Merge the broker's current inputs with deliberately frozen head values.
 * The evaluator and result callback must treat this map as read-only.</li>
 * <li>Invoke the evaluator and pass the round result to the result callback.
 * A round can discover missing attributes and remain incomplete; reporting
 * that result is not permission to emit a completed decision.</li>
 * <li>Extract the complete logical dependency set discovered by this round,
 * including already read live attributes and missing attributes. This set is
 * not a delta, and discovering B does not make A cease to be a dependency.</li>
 * <li>Capture newly delivered head values and evict cached heads that are no
 * longer referenced. A head that later reenters can capture a new first value.</li>
 * <li>Return the effective broker dependencies. Adding a fulfilled key can
 * request another round; dropping a key alone does not. If the logical set is
 * empty, handle termination instead of violating the broker's non-empty-set
 * requirement.</li>
 * </ol>
 * {@code |head} intentionally retains a first observation across rounds; it is
 * not permission to retain arbitrary live intermediate results. Concurrent
 * source changes may be reflected by a later round while the current one
 * finishes with its captured inputs. Wake requests can coalesce, so callback,
 * source-commit and emitted-decision counts need not match.
 * <p>
 * Ordinary SAPL error values remain policy data and can resolve or recover in
 * later rounds. Unexpected Java runtime exceptions in evaluation callbacks are
 * a separate failure: the streaming helper stops evaluating and asks its caller
 * to emit a fail-closed result and complete. The caller owns the output sink
 * and subscription handle; the broker cannot perform those actions for it.
 * The one-shot helper reports setup, unhandled round or release failures as
 * {@link EvaluationException} and releases an opened handle on exit.
 * <p>
 * The two entry points are:
 * <ul>
 * <li>{@link #openWithHead}: keeps an observation open and lets the caller
 * interpret each result, including incomplete results.</li>
 * <li>{@link #awaitFirstResult}: waits for the first non-null completion value
 * supplied by the caller's builder, then releases the observation.</li>
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
     * Opens a streaming evaluation. Broker callbacks for this subscription are
     * serialized but may use different threads, including the opening thread.
     * The evaluator, result handler and termination handler must be ready before
     * this method returns its handle.
     * <p>
     * {@code onResult} receives every evaluated round, including incomplete
     * results. It owns the rule for emitting completed results and any decision
     * deduplication. {@code nextDeps} must return the full logical set actually
     * needed or touched in the same round, not just newly missing keys.
     * <p>
     * An unexpected runtime exception in {@code evaluator}, {@code onResult} or
     * {@code nextDeps} ends evaluation and reaches {@code onTerminate}. A normal
     * round with no logical dependencies calls {@code onTerminate} with null
     * after {@code onResult} has handled its final result. The termination
     * handler must return normally. Later broker callbacks do not reevaluate
     * the policy after termination.
     * <p>
     * An exception from opening the broker subscription itself is outside this
     * callback guard and propagates to the caller without calling the handler.
     * <p>
     * Termination does not close the handle. The helper retains the initial
     * effective broker dependencies as an idle, non-empty return value until
     * the caller closes it. This avoids closing a handle that may not yet have
     * returned from a synchronous open. Closing the handle alone does not call
     * {@code onTerminate}; nor does it join a callback already selected for
     * delivery. The owner must guard its sink if cancellation must suppress
     * late results.
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
     * reevaluates the compiled expression or voter against the current merged inputs
     * @param onResult
     * handles the round result and read-only inputs; emits a decision only when
     * the result supports one, rather than treating every callback as terminal
     * @param nextDeps
     * returns the complete non-null logical dependency set for that round;
     * an empty set requests normal evaluation termination
     * @param onTerminate
     * non-throwing handler called once on evaluation termination: a runtime
     * exception requires fail-closed output and completion by the owner; null
     * means normal termination after the final result was handled. The owner
     * remains responsible for closing the returned subscription
     *
     * @return the subscription handle. The caller closes it
     * @throws RuntimeException if the broker cannot open the subscription
     */
    public static <R> AttributeBroker.Subscription openWithHead(AttributeBroker broker, String subscriptionId,
            Set<SubscriptionKey> initialDeps, Function<Map<SubscriptionKey, AttributeSnapshot>, R> evaluator,
            BiConsumer<R, Map<SubscriptionKey, AttributeSnapshot>> onResult, Function<R, Set<SubscriptionKey>> nextDeps,
            Consumer<@Nullable RuntimeException> onTerminate) {
        val headCache         = new HeadCache();
        val initialBrokerDeps = headCache.brokerDepsFor(initialDeps);
        val terminated        = new AtomicBoolean(false);
        return broker.open(subscriptionId, initialBrokerDeps, new AttributeSubscriptionObserver(brokerSnap -> {
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
        }, failure -> {
            if (terminated.compareAndSet(false, true)) {
                onTerminate.accept(failure);
            }
        }));
    }

    /**
     * Drives rounds until {@code builder} accepts one as a non-null completion
     * value, returns it and releases the subscription. This is not necessarily
     * the first broker callback: earlier rounds can discover more dependencies
     * and remain incomplete.
     * <p>
     * The builder returns null to keep waiting. For round callbacks the first
     * completion wins: a later callback failure cannot replace a value already
     * accepted by the future. Opening or releasing the subscription can still
     * fail with {@link EvaluationException}, including a release failure after
     * a value was accepted. Interruption, exceptional completion and normal
     * return all release an opened handle; an in-flight callback is not joined
     * by that release.
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
     * reevaluates against the current merged inputs on each round
     * @param builder
     * maps a result + merged snapshot to a completion value, or {@code null} if the
     * round is not terminal
     * @param nextDeps
     * extracts the complete logical dependency set discovered in that round
     *
     * @return the first non-null value the builder produced
     *
     * @throws InterruptedException
     * if the caller is interrupted while waiting
     * @throws EvaluationException
     * if opening or releasing the subscription fails, a round callback fails
     * before a completion value is accepted, or evaluation terminates without one
     */
    public static <R, V> V awaitFirstResult(AttributeBroker broker, String subscriptionId,
            Set<SubscriptionKey> initialDeps, Function<Map<SubscriptionKey, AttributeSnapshot>, R> evaluator,
            BiFunction<R, Map<SubscriptionKey, AttributeSnapshot>, V> builder,
            Function<R, Set<SubscriptionKey>> nextDeps) throws InterruptedException, EvaluationException {
        val future = new CompletableFuture<V>();
        // The broker may dispatch inline or on a producer thread and catches
        // callback failures. Each stage is wrapped so a
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
