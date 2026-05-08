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

import lombok.val;

import java.time.Instant;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic {@link TimeScheduler} for tests. Tasks are queued
 * and never run automatically; tests call {@link #advanceTo(Instant)}
 * to fire all tasks whose scheduled instant is at or before the
 * supplied time. Insertion order is preserved among tasks scheduled
 * for the same instant.
 */
public final class TestTimeScheduler implements TimeScheduler {

    private final PriorityQueue<Entry> queue    = new PriorityQueue<>();
    private final AtomicLong           sequence = new AtomicLong();
    private Instant                    currentTime;

    public TestTimeScheduler(Instant initialTime) {
        this.currentTime = initialTime;
    }

    @Override
    public synchronized Cancellable scheduleAt(Instant when, Runnable task) {
        val entry = new Entry(when, sequence.getAndIncrement(), task);
        queue.add(entry);
        return () -> {
            synchronized (TestTimeScheduler.this) {
                queue.remove(entry);
            }
        };
    }

    /**
     * Advances the scheduler's current time to {@code target} and
     * fires every queued task whose scheduled instant is at or
     * before {@code target}, in scheduled-instant order (ties broken
     * by insertion order).
     */
    public synchronized void advanceTo(Instant target) {
        if (target.isBefore(currentTime)) {
            return;
        }
        currentTime = target;
        while (!queue.isEmpty() && !queue.peek().when.isAfter(target)) {
            val entry = queue.poll();
            entry.task.run();
        }
    }

    /**
     * Number of tasks still pending. Useful for assertions about
     * cancellation.
     */
    public synchronized int pendingCount() {
        return queue.size();
    }

    private record Entry(Instant when, long sequence, Runnable task) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry other) {
            int byTime = when.compareTo(other.when);
            return byTime != 0 ? byTime : Long.compare(sequence, other.sequence);
        }
    }
}
