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
package io.sapl.attributes.libraries.vnext.util;

import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * {@link TimeScheduler} backed by a {@link ScheduledExecutorService}
 * with a virtual-thread task factory. Reads the current time from an
 * injectable {@link Clock} so tests can pin time without changing the
 * production wiring path.
 */
@Slf4j
public final class RealTimeScheduler implements TimeScheduler, AutoCloseable {

    private static final String ERROR_TASK_RAISED_UNHANDLED_EXCEPTION = "Scheduled task raised an unhandled exception";

    private final Clock                    clock;
    private final ScheduledExecutorService executor;

    /**
     * Creates a scheduler that runs tasks on virtual threads using
     * the system UTC clock.
     */
    public RealTimeScheduler() {
        this(Clock.systemUTC());
    }

    /**
     * Creates a scheduler that runs tasks on virtual threads using
     * the supplied clock.
     */
    public RealTimeScheduler(Clock clock) {
        this.clock    = clock;
        this.executor = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
    }

    @Override
    public Cancellable scheduleAt(Instant when, Runnable task) {
        val                delay  = Math.max(0L, Duration.between(clock.instant(), when).toNanos());
        ScheduledFuture<?> future = executor.schedule(() -> {
                                      try {
                                          task.run();
                                      } catch (RuntimeException e) {
                                          // Tasks scheduled here are expected to handle their own
                                          // exceptions (Streams helpers do, via stream error
                                          // emission). Reaching here means a bug in a task's own
                                          // exception handling. Log loudly with stack trace.
                                          log.error(ERROR_TASK_RAISED_UNHANDLED_EXCEPTION, e);
                                      }
                                  }, delay, TimeUnit.NANOSECONDS);
        return () -> future.cancel(false);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
