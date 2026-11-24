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

import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * {@link TimeScheduler} backed by a {@link ScheduledExecutorService}
 * with a single dedicated platform thread. Reads the current time from an
 * injectable {@link Clock} so tests can pin time without changing the
 * production wiring path.
 * <p>
 * The timer runs on a platform thread rather than a virtual thread because
 * virtual threads are not preempted: while CPU-bound virtual threads occupy
 * every carrier thread, a virtual timer thread could not wake up and time
 * transitions such as token expiry would fire late. Tasks run inline on the
 * timer thread, so they must be short and must not block.
 */
@Slf4j
public final class RealTimeScheduler implements TimeScheduler, AutoCloseable {

    private static final String ERROR_TASK_RAISED_UNHANDLED_EXCEPTION = "Scheduled task raised an unhandled exception";
    private static final String TIMER_THREAD_NAME                     = "sapl-time-scheduler";

    private final Clock                    clock;
    private final ScheduledExecutorService executor;

    /**
     * Creates a scheduler that runs tasks on its timer thread using
     * the system UTC clock.
     */
    public RealTimeScheduler() {
        this(Clock.systemUTC());
    }

    /**
     * Creates a scheduler that runs tasks on its timer thread using
     * the supplied clock.
     *
     * @param clock the clock used to calculate absolute deadlines
     */
    public RealTimeScheduler(Clock clock) {
        this(clock,
                Executors.newScheduledThreadPool(1, Thread.ofPlatform().name(TIMER_THREAD_NAME).daemon().factory()));
    }

    RealTimeScheduler(Clock clock, ScheduledExecutorService executor) {
        this.clock    = clock;
        this.executor = executor;
    }

    @Override
    public Cancellable scheduleAt(Instant when, Runnable task) {
        val delay = nanosUntil(when);
        if (delay < Long.MAX_VALUE) {
            return schedule(delay, task);
        }
        val registration = new LongRegistration(when, task);
        registration.arm(delay);
        return registration;
    }

    private Cancellable schedule(long delay, Runnable task) {
        val future = executor.schedule(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                // Tasks scheduled here are expected to handle their own exceptions
                // (Streams helpers do, via stream error emission). Reaching here means
                // a bug in a task's own exception handling. Log loudly with stack trace.
                log.error(ERROR_TASK_RAISED_UNHANDLED_EXCEPTION, e);
            }
        }, delay, TimeUnit.NANOSECONDS);
        return () -> future.cancel(false);
    }

    private final class LongRegistration implements Runnable, Cancellable {
        private final Instant  when;
        private final Runnable task;
        private boolean        cancelled;
        private Cancellable    next = Cancellable.NOOP;

        private LongRegistration(Instant when, Runnable task) {
            this.when = when;
            this.task = task;
        }

        private synchronized void arm(long delay) {
            next = schedule(delay, this);
        }

        @Override
        public void run() {
            synchronized (this) {
                if (cancelled) {
                    return;
                }
                val remaining = nanosUntil(when);
                if (remaining > 0) {
                    arm(remaining);
                    return;
                }
            }
            task.run();
        }

        @Override
        public synchronized void cancel() {
            cancelled = true;
            next.cancel();
        }
    }

    /**
     * Delay in nanoseconds from now until {@code when}, saturated to
     * {@link Long#MAX_VALUE} when the distance overflows a long instead of
     * throwing. A saturated registration rechecks its original deadline when
     * this timer delay expires.
     */
    private long nanosUntil(Instant when) {
        val until = Duration.between(clock.instant(), when);
        if (until.isNegative()) {
            return 0L;
        }
        try {
            return until.toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
