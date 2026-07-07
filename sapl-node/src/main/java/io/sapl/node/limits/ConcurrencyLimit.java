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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;

import lombok.val;

/**
 * A lock-free ceiling on concurrently held permits. Acquisition returns a
 * permit whose release is idempotent, so every termination path of the
 * guarded resource may release defensively without double-counting.
 */
public final class ConcurrencyLimit {

    private final int           maxConcurrent;
    private final AtomicInteger active = new AtomicInteger();

    /**
     * Creates a limit with the given ceiling.
     *
     * @param maxConcurrent the maximum number of concurrently held permits
     * @throws IllegalArgumentException if the ceiling is not positive
     */
    public ConcurrencyLimit(int maxConcurrent) {
        if (maxConcurrent <= 0) {
            throw new IllegalArgumentException("maxConcurrent must be positive, got " + maxConcurrent);
        }
        this.maxConcurrent = maxConcurrent;
    }

    /**
     * Attempts to acquire a permit.
     *
     * @return a permit, or null when the ceiling is reached
     */
    public @Nullable Permit tryAcquire() {
        while (true) {
            val current = active.get();
            if (current >= maxConcurrent) {
                return null;
            }
            if (active.compareAndSet(current, current + 1)) {
                return new Permit();
            }
        }
    }

    /**
     * The number of permits currently held.
     *
     * @return the active permit count
     */
    public int active() {
        return active.get();
    }

    /**
     * A held permit. Closing releases it; repeated closes are no-ops.
     */
    public final class Permit implements AutoCloseable {

        private final AtomicBoolean released = new AtomicBoolean();

        private Permit() {
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                active.decrementAndGet();
            }
        }
    }
}
