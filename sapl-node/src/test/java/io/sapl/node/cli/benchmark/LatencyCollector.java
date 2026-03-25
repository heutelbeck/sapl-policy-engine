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

/**
 * Lock-free latency sample collector using a pre-allocated ring buffer.
 * Samples are recorded in nanoseconds. When the buffer is full, oldest
 * samples are overwritten (ring). Percentile computation sorts a snapshot.
 *
 * Thread-safe for concurrent writers via atomic index.
 */
class LatencyCollector {

    private final long[]        samples;
    private final AtomicInteger index = new AtomicInteger(0);

    LatencyCollector(int capacity) {
        this.samples = new long[capacity];
    }

    void record(long nanos) {
        var i = index.getAndIncrement();
        samples[i % samples.length] = nanos;
    }

    int count() {
        return Math.min(index.get(), samples.length);
    }

    void printDistribution() {
        var n = count();
        if (n == 0) {
            System.out.println("  No samples.");
            return;
        }

        var sorted = new long[n];
        System.arraycopy(samples, 0, sorted, 0, n);
        Arrays.sort(sorted);

        var mean = 0L;
        for (int i = 0; i < n; i++) {
            mean += sorted[i];
        }
        mean /= n;

        System.out.printf("  Samples: %,d%n", n);
        System.out.printf("  Mean:    %,d us%n", mean / 1000);
        System.out.printf("  p50:     %,d us%n", pctl(sorted, 0.50) / 1000);
        System.out.printf("  p90:     %,d us%n", pctl(sorted, 0.90) / 1000);
        System.out.printf("  p99:     %,d us%n", pctl(sorted, 0.99) / 1000);
        System.out.printf("  p99.9:   %,d us%n", pctl(sorted, 0.999) / 1000);
        System.out.printf("  max:     %,d us%n", sorted[n - 1] / 1000);
    }

    private static long pctl(long[] sorted, double p) {
        var index = p * (sorted.length - 1);
        var lower = (int) Math.floor(index);
        var upper = Math.min(lower + 1, sorted.length - 1);
        var frac  = index - lower;
        return (long) (sorted[lower] * (1 - frac) + sorted[upper] * frac);
    }
}
