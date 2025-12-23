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
package io.sapl.pdp;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A high-performance clock that caches UTC ISO timestamps to avoid frequent
 * system time polling at high throughput.
 * <p>
 * Instead of calling {@link System#currentTimeMillis()} and formatting on every
 * request, this clock maintains a cached
 * timestamp string that is updated at a configurable interval (default: 10ms).
 * This trades timestamp precision for
 * throughput - suitable for scenarios where millions of timestamps per second
 * are needed and 10ms precision is
 * acceptable.
 * <p>
 * The {@link #now()} method is a simple atomic read with no allocation, making
 * it ideal for hot paths in policy
 * evaluation where timestamps are needed for tracing or logging.
 * <p>
 * Thread-safe for concurrent access. Implements {@link AutoCloseable} for
 * proper resource cleanup.
 *
 * <pre>{@code
 * // Create with default 10ms update interval
 * var clock = new LazyFastClock();
 *
 * // Use in hot path - very cheap operation
 * String timestamp = clock.now();
 *
 * // Clean up when done
 * clock.close();
 * }</pre>
 */
public final class LazyFastClock implements AutoCloseable {

    private static final long DEFAULT_UPDATE_INTERVAL_MS = 10;

    private final AtomicReference<String>  cachedTimestamp = new AtomicReference<>();
    private final ScheduledExecutorService scheduler;

    /**
     * Creates a clock with the default 10ms update interval.
     */
    public LazyFastClock() {
        this(DEFAULT_UPDATE_INTERVAL_MS);
    }

    /**
     * Creates a clock with a custom update interval.
     *
     * @param updateIntervalMilliseconds
     * the interval between timestamp updates in milliseconds; lower values increase
     * precision but add CPU
     * overhead
     */
    public LazyFastClock(long updateIntervalMilliseconds) {
        cachedTimestamp.set(Instant.ofEpochMilli(System.currentTimeMillis()).toString());
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "LazyFastClock-updater");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(this::updateTimestamp, updateIntervalMilliseconds, updateIntervalMilliseconds,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Returns the current cached UTC ISO timestamp.
     * <p>
     * The timestamp is in ISO-8601 format (e.g., "2025-12-03T14:30:00.123Z").
     * Precision is limited by the configured
     * update interval.
     * <p>
     * This is a very cheap operation - just an atomic reference read with no
     * allocation or system calls.
     *
     * @return the cached UTC timestamp string in ISO-8601 format
     */
    public String now() {
        return cachedTimestamp.get();
    }

    private void updateTimestamp() {
        cachedTimestamp.set(Instant.ofEpochMilli(System.currentTimeMillis()).toString());
    }

    /**
     * Shuts down the background update thread.
     * <p>
     * After calling this method, the cached timestamp will no longer be updated,
     * but {@link #now()} will continue to
     * return the last cached value.
     */
    @Override
    public void close() {
        scheduler.shutdown();
    }

}
