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
package io.sapl.attributes.broker.pip;

import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.Poll;
import io.sapl.api.model.Value;
import io.sapl.api.stream.LatestSlotStream;
import io.sapl.api.stream.Stream;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Encapsulates the lifecycle of a single attribute invocation as a
 * {@link Stream}{@code <Value>}. Internally drives a perpetual
 * poll/retry/timeout loop, presenting a never-completing
 * {@code Stream<Value>} to its consumer until {@link #close()} is
 * called.
 * <p>
 * Each cycle: open a fresh inner stream from the supplier, drain its
 * values into the output mailbox, and on cycle end (clean completion
 * or retry-burst exhaustion) sleep {@code pollInterval} before the
 * next cycle. {@code initialTimeOut} bounds the wait for the first
 * value of each cycle. Failures (supplier throws, inner errors,
 * first-value timeout) trigger a retry burst: up to {@code retries}
 * additional attempts spaced by exponentially-growing backoff with
 * 50% jitter, starting at {@code backoff}. On retry exhaustion a
 * transient {@link Value#error} placeholder is published and the
 * outer loop continues. From the consumer's perspective there is no
 * terminal error state. Every error placeholder may be overwritten
 * by the next successful cycle's value.
 * <p>
 * Cancellation is best-effort. {@link Stream#awaitNext(Duration)}
 * uses cooperative interrupt to enforce {@code initialTimeOut}, and
 * the framework calls {@code inner.close()} on cycle teardown. An
 * inner stream that ignores both interrupt and {@code close} will
 * block its pump thread indefinitely. Recovery from a stuck pump
 * is the responsibility of the surrounding lifecycle: closing this
 * AttributeStream and opening a fresh one releases the consumer
 * regardless of whether the prior pump ever returns.
 */
@Slf4j
final class AttributeStream implements Stream<Value> {

    private static final String DEBUG_ATTEMPT_FAILED    = "Attribute '{}' attempt failed: {}";
    private static final String DEBUG_INNER_CLOSE_THREW = "Inner stream close threw: {}";
    private static final String ERROR_PUMP_FAILURE      = "Attribute '%s' pump encountered an unexpected failure: %s.";
    private static final String ERROR_PUMP_FAILURE_LOG  = "Attribute '{}' pump caught an unexpected failure; surfacing an error and retrying after backoff.";
    private static final String ERROR_RETRIES_EXHAUSTED = "Attribute '%s' transient failure: retries exhausted, last cause: %s.";

    private static final Duration PUMP_FAILURE_BACKOFF      = Duration.ofSeconds(1);
    private static final long     PUMP_FAILURE_LOG_INTERVAL = Duration.ofMinutes(1).toNanos();

    private final AttributeFinderInvocation      invocation;
    private final Supplier<Stream<Value>>        innerSupplier;
    private final AtomicReference<Stream<Value>> currentInner = new AtomicReference<>();

    private final LatestSlotStream<Value> output = new LatestSlotStream<>();
    private volatile boolean              closed = false;

    // Interrupted on close() so a pump parked in a sleep exits promptly.
    // Package-private for lifecycle tests.
    final Thread pumpThread;

    // Rate-limits the unexpected-failure log.
    private long    lastFailureLogNanos;
    private boolean failureLogged;

    AttributeStream(@NonNull AttributeFinderInvocation invocation, @NonNull Supplier<Stream<Value>> innerSupplier) {
        this.invocation    = invocation;
        this.innerSupplier = innerSupplier;
        this.pumpThread    = Thread.ofVirtual().name("AttributeStream-pump-" + invocation.attributeName())
                .start(this::runLoop);
    }

    @Override
    public Value awaitNext() throws InterruptedException {
        if (closed) {
            return null;
        }
        val next = output.awaitNext();
        if (closed) {
            return null;
        }
        return next;
    }

    @Override
    public Poll<Value> tryNext() {
        if (closed) {
            return Poll.done();
        }
        return output.tryNext();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        // Interrupt so a thread parked in a sleep notices the close immediately.
        pumpThread.interrupt();
        output.close();
        val inflightInner = currentInner.getAndSet(null);
        if (inflightInner != null) {
            safeClose(inflightInner);
        }
        // If the supplier holds a synchronously-opened first inner that the
        // pump never consumed, release it. Otherwise it leaks until GC.
        if (innerSupplier instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
    }

    private void runLoop() {
        while (!closed) {
            try {
                attemptWithRetries();
                sleepIfNotClosed(invocation.pollInterval());
            } catch (Throwable t) {
                // The pump must be immortal. attemptWithRetries already handles transient PIP
                // failures, so
                // reaching here is an unexpected fault. Surface an error, rate-limit the log,
                // and back off so
                // a deterministic fault degrades to a slow heartbeat.
                recoverFromPumpFailure(t);
            }
        }
    }

    private void recoverFromPumpFailure(Throwable failure) {
        logPumpFailure(failure);
        try {
            publish(Value.error(ERROR_PUMP_FAILURE.formatted(invocation.attributeName(), failure.toString())));
        } catch (Throwable ignored) {
            // Recovery must never throw out of the loop. Drop and keep the pump alive.
        }
        sleepIfNotClosed(PUMP_FAILURE_BACKOFF);
    }

    private void logPumpFailure(Throwable failure) {
        val now = System.nanoTime();
        if (!failureLogged || now - lastFailureLogNanos >= PUMP_FAILURE_LOG_INTERVAL) {
            log.error(ERROR_PUMP_FAILURE_LOG, invocation.attributeName(), failure);
            lastFailureLogNanos = now;
            failureLogged       = true;
        }
    }

    private void attemptWithRetries() {
        long             retriesLeft = invocation.retries();
        int              retryIndex  = 0;
        RuntimeException lastCause;
        while (!closed) {
            try {
                attempt();
                return;
            } catch (TimeoutException e) {
                publish(Value.UNDEFINED);
                lastCause = new RuntimeException("timeout after " + invocation.initialTimeOut());
            } catch (RuntimeException e) {
                log.debug(DEBUG_ATTEMPT_FAILED, invocation.attributeName(), e.getMessage());
                lastCause = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (retriesLeft <= 0) {
                publish(Value
                        .error(ERROR_RETRIES_EXHAUSTED.formatted(invocation.attributeName(), lastCause.getMessage())));
                return;
            }
            retriesLeft--;
            if (!sleepIfNotClosed(jitteredBackoff(invocation.backoff(), retryIndex))) {
                return;
            }
            retryIndex++;
        }
    }

    /**
     * Exponential backoff with 50% jitter. Delay for retry index
     * {@code n} is {@code base * 2^n}, then offset by a uniform
     * random in {@code [-50%, +50%]}. Capped at one hour to avoid
     * overflow on very large indices. Total by contract: it returns a
     * non-negative {@link Duration} for every {@code retryIndex >= 0}
     * and never throws, so the pump's retry path cannot fail here.
     */
    static Duration jitteredBackoff(Duration base, int retryIndex) {
        long baseMillis = base.toMillis();
        if (baseMillis <= 0) {
            return Duration.ZERO;
        }
        long capMillis = Duration.ofHours(1).toMillis();
        long shifted;
        if (retryIndex >= 62 || baseMillis > (capMillis >> retryIndex)) {
            // Shift would overflow or exceed the cap. Clamp to the cap.
            shifted = capMillis;
        } else {
            shifted = baseMillis << retryIndex;
        }
        long jitterRange = shifted / 2;
        if (jitterRange == 0) {
            return Duration.ofMillis(shifted);
        }
        long jittered = shifted + ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1);
        return Duration.ofMillis(Math.max(1, jittered));
    }

    /**
     * Opens an inner stream and drains it. Throws on any failure so
     * the caller's retry loop can decide what to do.
     */
    private void attempt() throws InterruptedException, TimeoutException {
        val inner = innerSupplier.get();
        currentInner.set(inner);
        try {
            // close() may have run before currentInner.set(inner) and
            // therefore missed closing this inner. Re-check now that
            // currentInner is published so the finally block can close
            // it without waiting out initialTimeOut on awaitNext.
            if (closed) {
                return;
            }
            val first = firstValue(inner);
            if (first == null) {
                publish(Value.UNDEFINED);
                return;
            }
            publish(first);
            drain(inner);
        } finally {
            if (currentInner.compareAndSet(inner, null)) {
                safeClose(inner);
            }
            // If compareAndSet failed, close() already took ownership of the
            // inner and closed it. Do not double-close.
        }
    }

    private Value firstValue(Stream<Value> inner) throws InterruptedException, TimeoutException {
        return inner.awaitNext(invocation.initialTimeOut());
    }

    private void drain(Stream<Value> inner) throws InterruptedException {
        while (!closed) {
            val next = inner.awaitNext();
            if (next == null) {
                return;
            }
            publish(next);
        }
    }

    private void publish(Value value) {
        if (!closed) {
            output.put(value);
        }
    }

    private boolean sleepIfNotClosed(Duration duration) {
        if (closed) {
            return false;
        }
        // Thread.sleep rejects a negative duration. Clamp a misconfigured negative to
        // zero.
        val sleep = duration.isNegative() ? Duration.ZERO : duration;
        try {
            Thread.sleep(sleep);
            return !closed;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void safeClose(Stream<Value> stream) {
        try {
            stream.close();
        } catch (RuntimeException e) {
            log.debug(DEBUG_INNER_CLOSE_THREW, e.getMessage());
        }
    }
}
