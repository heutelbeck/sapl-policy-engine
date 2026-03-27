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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import lombok.val;

@DisplayName("LatencyCollector")
class LatencyCollectorTests {

    @Nested
    @DisplayName("empty collector")
    class EmptyCollectorTests {

        @Test
        @DisplayName("returns null latency when no samples recorded")
        void whenNoSamples_thenToLatencyReturnsNull() {
            val collector = new LatencyCollector(100);
            assertThat(collector.toLatency()).isNull();
        }

        @Test
        @DisplayName("count is zero when no samples recorded")
        void whenNoSamples_thenCountIsZero() {
            val collector = new LatencyCollector(100);
            assertThat(collector.count()).isZero();
        }
    }

    @Nested
    @DisplayName("single sample")
    class SingleSampleTests {

        @Test
        @DisplayName("all percentiles equal the single sample value")
        void whenOneSample_thenAllPercentilesEqual() {
            val collector = new LatencyCollector(100);
            collector.record(5000);
            val latency = collector.toLatency();
            assertThat(latency).satisfies(l -> {
                assertThat(l.mean()).isCloseTo(5000.0, within(0.1));
                assertThat(l.p50()).isCloseTo(5000.0, within(0.1));
                assertThat(l.p90()).isCloseTo(5000.0, within(0.1));
                assertThat(l.p99()).isCloseTo(5000.0, within(0.1));
                assertThat(l.min()).isCloseTo(5000.0, within(0.1));
                assertThat(l.max()).isCloseTo(5000.0, within(0.1));
            });
        }
    }

    @Nested
    @DisplayName("multiple samples")
    class MultipleSampleTests {

        @Test
        @DisplayName("computes correct mean and percentiles for known distribution")
        void whenKnownDistribution_thenPercentilesCorrect() {
            val collector = new LatencyCollector(1000);
            // Record 100 samples: 1000, 2000, 3000, ..., 100000
            for (int i = 1; i <= 100; i++) {
                collector.record(i * 1000L);
            }
            val latency = collector.toLatency();
            assertThat(latency).satisfies(l -> {
                assertThat(l.mean()).isCloseTo(50500.0, within(1.0));
                assertThat(l.p50()).isCloseTo(50500.0, within(1000.0));
                assertThat(l.min()).isCloseTo(1000.0, within(0.1));
                assertThat(l.max()).isCloseTo(100000.0, within(0.1));
            });
            assertThat(collector.count()).isEqualTo(100);
        }

        @Test
        @DisplayName("p99 is higher than p50 for skewed distribution")
        void whenSkewedDistribution_thenP99HigherThanP50() {
            val collector = new LatencyCollector(1000);
            for (int i = 0; i < 99; i++) {
                collector.record(100);
            }
            collector.record(10000);
            val latency = collector.toLatency();
            assertThat(latency.p99()).isGreaterThan(latency.p50());
        }
    }

    @Nested
    @DisplayName("ring buffer wrapping")
    class RingBufferTests {

        @Test
        @DisplayName("count caps at capacity when buffer overflows")
        void whenOverflow_thenCountCapsAtCapacity() {
            val collector = new LatencyCollector(10);
            for (int i = 0; i < 25; i++) {
                collector.record(i * 100L);
            }
            assertThat(collector.count()).isEqualTo(10);
        }

        @Test
        @DisplayName("oldest samples are overwritten on overflow")
        void whenOverflow_thenLatencyReflectsRecentSamples() {
            val collector = new LatencyCollector(5);
            // Record 1,2,3,4,5 then 100,200,300,400,500
            for (int i = 1; i <= 5; i++) {
                collector.record(i);
            }
            for (int i = 1; i <= 5; i++) {
                collector.record(i * 100L);
            }
            val latency = collector.toLatency();
            // After overwrite, buffer contains 100,200,300,400,500
            assertThat(latency.min()).isCloseTo(100.0, within(0.1));
        }
    }

    @Nested
    @DisplayName("reset")
    class ResetTests {

        @Test
        @DisplayName("reset clears count and allows reuse")
        void whenReset_thenCountZeroAndReusable() {
            val collector = new LatencyCollector(100);
            collector.record(1000);
            collector.record(2000);
            assertThat(collector.count()).isEqualTo(2);

            collector.reset();
            assertThat(collector.count()).isZero();
            assertThat(collector.toLatency()).isNull();

            collector.record(5000);
            assertThat(collector.count()).isEqualTo(1);
            assertThat(collector.toLatency()).isNotNull();
        }
    }

    @Nested
    @DisplayName("thread safety")
    class ThreadSafetyTests {

        @Test
        @DisplayName("concurrent writers do not lose samples")
        void whenConcurrentWriters_thenAllSamplesRecorded() throws InterruptedException {
            val collector = new LatencyCollector(100_000);
            val threads   = 8;
            val perThread = 1000;
            val latch     = new CountDownLatch(threads);

            for (int t = 0; t < threads; t++) {
                Thread.ofVirtual().start(() -> {
                    for (int i = 0; i < perThread; i++) {
                        collector.record(100);
                    }
                    latch.countDown();
                });
            }

            assertThat(latch.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(collector.count()).isEqualTo(threads * perThread);
        }
    }
}
