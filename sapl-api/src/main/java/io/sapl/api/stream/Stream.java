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
package io.sapl.api.stream;

import io.sapl.api.model.Poll;
import lombok.val;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A closeable handle for receiving values one at a time. Use
 * {@link #awaitNext()} to block until a value is ready. Use
 * {@link #tryNext()} for a non-blocking poll that distinguishes a
 * delivered value, an open-but-empty stream, and a completed
 * stream via the {@link Poll} sealed type.
 * <p>
 * A stream that has completed delivers no further values:
 * {@link #awaitNext()} returns {@code null}, {@link #tryNext()}
 * returns {@link Poll.Done}. A stream completes either when its
 * source has no more values to produce or when {@link #close()}
 * is called.
 * <p>
 * Always close the stream when finished. Use try-with-resources.
 *
 * @param <T> the value type carried by this stream
 *
 * @since 4.1.0
 */
public interface Stream<T> extends AutoCloseable {

    String ERROR_INVALID_TIMEOUT = "Timeout must be a positive Duration. For non-blocking use tryNext(). For indefinite use awaitNext().";

    /**
     * Returns the next value, blocking the calling thread until one
     * is available. Returns {@code null} if the stream has completed.
     *
     * @return the next value, or {@code null} on completion
     * @throws InterruptedException if the calling thread is interrupted
     * while waiting
     */
    T awaitNext() throws InterruptedException;

    /**
     * Returns the next value, blocking the calling thread until one is
     * available or the deadline expires.
     * <p>
     * The default implementation runs the zero-arg {@link #awaitNext()}
     * on the calling thread and a watchdog virtual thread that
     * interrupts the caller after {@code timeout}. An atomic
     * compare-and-set handshake between the two guarantees that, on a
     * successful return, the watchdog produces no observable side
     * effect on the caller (no spurious interrupt on the calling
     * thread, no propagated exception). Implementations may override
     * to use a native blocking-with-timeout primitive (for example
     * {@link java.util.concurrent.BlockingQueue#poll(long, java.util.concurrent.TimeUnit)})
     * but are not required to.
     * <p>
     * The default depends on the underlying {@link #awaitNext()} call
     * honouring {@link Thread#interrupt()}. A custom implementation
     * whose internal blocking call ignores interrupts will leave the
     * watchdog unable to unblock it cooperatively. The layered defense
     * for that case is the consumer's responsibility (see
     * {@code AttributeStream} in {@code sapl-pdp}).
     *
     * @param timeout the maximum time to wait. Must be greater than
     * {@link Duration#ZERO} and non-null. Use {@link #tryNext()} for a
     * non-blocking poll and the zero-arg {@link #awaitNext()} for an
     * indefinite wait.
     * @return the next value, or {@code null} on completion
     * @throws InterruptedException if the calling thread is interrupted
     * while waiting (other than by the internal watchdog) - the
     * interrupt flag is preserved per the JDK convention
     * @throws TimeoutException if no value arrived before the deadline
     * expired and the stream is still alive
     * @throws IllegalArgumentException if {@code timeout} is null,
     * zero, or negative
     */
    default T awaitNext(Duration timeout) throws InterruptedException, TimeoutException {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(ERROR_INVALID_TIMEOUT);
        }
        val state    = new AtomicReference<>(WatchdogState.PENDING);
        val caller   = Thread.currentThread();
        val watchdog = Thread.ofVirtual().name("Stream-awaitNext-watchdog").start(() -> {
                         try {
                             Thread.sleep(timeout);
                             if (state.compareAndSet(WatchdogState.PENDING, WatchdogState.FIRED)) {
                                 caller.interrupt();
                             }
                         } catch (InterruptedException ignored) {
                             // caller cancelled the watchdog because awaitNext returned in time
                         }
                     });
        try {
            val value = awaitNext();
            if (state.compareAndSet(WatchdogState.PENDING, WatchdogState.CANCELLED)) {
                // Caller won the race. Watchdog will see CANCELLED and will
                // not interrupt. Wake the watchdog's sleep so it exits early.
                watchdog.interrupt();
            } else {
                // Watchdog won the race. It has already done (or is about
                // to do) caller.interrupt(). Wait for the watchdog to finish
                // so the interrupt is reliably delivered, then clear the flag.
                joinUninterruptibly(watchdog);
                Thread.interrupted();
            }
            return value;
        } catch (InterruptedException e) {
            if (state.compareAndSet(WatchdogState.PENDING, WatchdogState.CANCELLED)) {
                // Caller won the race. External interrupt arrived before the
                // watchdog could fire. Wake the watchdog's sleep, then
                // re-throw the external interrupt.
                watchdog.interrupt();
                throw e;
            }
            // Watchdog won the race. The interrupt that broke us out of
            // awaitNext is the watchdog's. Wait for the watchdog to finish so
            // any in-flight interrupt is delivered, clear the flag, then
            // surface as TimeoutException.
            joinUninterruptibly(watchdog);
            Thread.interrupted();
            val timeoutFailure = new TimeoutException();
            timeoutFailure.initCause(e);
            throw timeoutFailure;
        }
    }

    /**
     * Polls for the next value without blocking. Returns one of:
     * <ul>
     * <li>{@link Poll.Value} - a value was available and is consumed</li>
     * <li>{@link Poll.Empty} - no value was available, the stream is
     * still open and may yield a value later</li>
     * <li>{@link Poll.Done} - the stream has completed, no further
     * values will be produced</li>
     * </ul>
     * <p>
     * Drain pattern:
     *
     * <pre>{@code
     * while (true) {
     *     switch (stream.tryNext()) {
     *     case Poll.Value(var v) -> handle(v);
     *     case Poll.Empty<T> e -> waitOrYield();
     *     case Poll.Done<T> d -> {
     *         return;
     *     }
     *     }
     * }
     * }</pre>
     *
     * @return the poll outcome
     */
    Poll<T> tryNext();

    /**
     * Closes the stream. After this returns, no further values
     * will be delivered. Idempotent and safe to call from any thread.
     */
    @Override
    void close();

    /** Internal state for the {@link #awaitNext(Duration)} default. */
    enum WatchdogState {
        PENDING,
        FIRED,
        CANCELLED
    }

    /**
     * Joins the given thread, swallowing any {@link InterruptedException}
     * encountered during the wait. Used by {@link #awaitNext(Duration)}
     * when the caller already knows the watchdog has done (or is about
     * to do) {@link Thread#interrupt()} on the caller; the caller
     * follows this with {@link Thread#interrupted()} to clear the flag.
     */
    private static void joinUninterruptibly(Thread thread) {
        while (true) {
            try {
                thread.join();
                return;
            } catch (InterruptedException ignored) {
                // keep waiting. Caller will deal with the interrupt flag
            }
        }
    }
}
