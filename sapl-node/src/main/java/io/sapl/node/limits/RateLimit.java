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
package io.sapl.node.limits;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import lombok.val;

/**
 * A lock-free requests-per-second limiter using the generic cell rate
 * algorithm (virtual scheduling). Allows bursts of up to one second's worth
 * of permits, then throttles to the configured steady rate. Rejected
 * attempts never write the shared state, so an over-rate flood does not
 * contend with admitted requests.
 */
public final class RateLimit {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final long         emissionIntervalNanos;
    private final long         burstToleranceNanos;
    private final LongSupplier nanoTime;
    private final AtomicLong   theoreticalArrivalTime;

    /**
     * Creates a limiter with the given steady rate.
     *
     * @param permitsPerSecond the sustained permit rate, also the burst capacity
     * @throws IllegalArgumentException if the rate is not positive
     */
    public RateLimit(int permitsPerSecond) {
        this(permitsPerSecond, System::nanoTime);
    }

    RateLimit(int permitsPerSecond, LongSupplier nanoTime) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond must be positive, got " + permitsPerSecond);
        }
        this.emissionIntervalNanos  = NANOS_PER_SECOND / permitsPerSecond;
        this.burstToleranceNanos    = emissionIntervalNanos * (permitsPerSecond - 1L);
        this.nanoTime               = nanoTime;
        this.theoreticalArrivalTime = new AtomicLong(nanoTime.getAsLong());
    }

    /**
     * Attempts to take one permit at the current instant.
     *
     * @return true if the request conforms to the configured rate, false if it
     * must be shed
     */
    public boolean tryAcquire() {
        val now = nanoTime.getAsLong();
        while (true) {
            val tat  = theoreticalArrivalTime.get();
            val base = Math.max(tat, now);
            if (base - now > burstToleranceNanos) {
                return false;
            }
            if (theoreticalArrivalTime.compareAndSet(tat, base + emissionIntervalNanos)) {
                return true;
            }
        }
    }
}
