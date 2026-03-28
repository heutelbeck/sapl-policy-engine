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
package io.sapl.node.cli.benchmark;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;

import lombok.val;

/**
 * Lock-free latency sample collector using a pre-allocated ring buffer.
 * Samples are recorded in nanoseconds. When the buffer is full, oldest
 * samples are overwritten. Thread-safe for concurrent writers via atomic
 * index.
 */
class LatencyCollector {

    private final long[]        samples;
    private final AtomicInteger index = new AtomicInteger(0);

    /**
     * Creates a collector with the given capacity.
     *
     * @param capacity maximum number of samples before wrapping
     */
    LatencyCollector(int capacity) {
        this.samples = new long[capacity];
    }

    /**
     * Records a latency sample.
     *
     * @param nanos the latency in nanoseconds
     */
    void addSample(long nanos) {
        val i = index.getAndIncrement();
        samples[i % samples.length] = nanos;
    }

    /**
     * Returns the number of recorded samples (capped at capacity).
     *
     * @return sample count
     */
    int count() {
        return Math.min(index.get(), samples.length);
    }

    /**
     * Resets the collector for reuse between warmup and measurement phases.
     */
    void reset() {
        index.set(0);
    }

    /**
     * Computes latency statistics from the collected samples.
     *
     * @return a Latency record with percentiles, or null if no samples
     */
    BenchmarkResult.@Nullable Latency toLatency() {
        val n = count();
        if (n == 0) {
            return null;
        }

        val sorted = new long[n];
        System.arraycopy(samples, 0, sorted, 0, n);
        Arrays.sort(sorted);

        var mean = 0L;
        for (int i = 0; i < n; i++) {
            mean += sorted[i];
        }
        mean /= n;

        return new BenchmarkResult.Latency(mean, pctl(sorted, 0.50), pctl(sorted, 0.90), pctl(sorted, 0.99),
                pctl(sorted, 0.999), sorted[0], sorted[n - 1]);
    }

    private static long pctl(long[] sorted, double p) {
        val index = p * (sorted.length - 1);
        val lower = (int) Math.floor(index);
        val upper = Math.min(lower + 1, sorted.length - 1);
        val frac  = index - lower;
        return (long) (sorted[lower] * (1 - frac) + sorted[upper] * frac);
    }
}
