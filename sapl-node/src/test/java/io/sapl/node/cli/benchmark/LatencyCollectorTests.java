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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

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
        void whenNoSamplesThenToLatencyReturnsNull() {
            val collector = new LatencyCollector(100);
            assertThat(collector.toLatency()).isNull();
        }

        @Test
        @DisplayName("count is zero when no samples recorded")
        void whenNoSamplesThenCountIsZero() {
            val collector = new LatencyCollector(100);
            assertThat(collector.count()).isZero();
        }
    }

    @Nested
    @DisplayName("single sample")
    class SingleSampleTests {

        @Test
        @DisplayName("all percentiles equal the single sample value")
        void whenOneSampleThenAllPercentilesEqual() {
            val collector = new LatencyCollector(100);
            collector.addSample(5000);
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
        void whenKnownDistributionThenPercentilesCorrect() {
            val collector = new LatencyCollector(1000);
            // Record 100 samples: 1000, 2000, 3000, ..., 100000
            for (int i = 1; i <= 100; i++) {
                collector.addSample(i * 1000L);
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
        void whenSkewedDistributionThenP99HigherThanP50() {
            val collector = new LatencyCollector(1000);
            for (int i = 0; i < 99; i++) {
                collector.addSample(100);
            }
            collector.addSample(10000);
            val latency = collector.toLatency();
            assertThat(latency.p99()).isGreaterThan(latency.p50());
        }
    }

    @Nested
    @DisplayName("ring buffer wrapping")
    class RingBufferTests {

        @Test
        @DisplayName("count caps at capacity when buffer overflows")
        void whenOverflowThenCountCapsAtCapacity() {
            val collector = new LatencyCollector(10);
            for (int i = 0; i < 25; i++) {
                collector.addSample(i * 100L);
            }
            assertThat(collector.count()).isEqualTo(10);
        }

        @Test
        @DisplayName("oldest samples are overwritten on overflow")
        void whenOverflowThenLatencyReflectsRecentSamples() {
            val collector = new LatencyCollector(5);
            // Record 1,2,3,4,5 then 100,200,300,400,500
            for (int i = 1; i <= 5; i++) {
                collector.addSample(i);
            }
            for (int i = 1; i <= 5; i++) {
                collector.addSample(i * 100L);
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
        void whenResetThenCountZeroAndReusable() {
            val collector = new LatencyCollector(100);
            collector.addSample(1000);
            collector.addSample(2000);
            assertThat(collector.count()).isEqualTo(2);

            collector.reset();
            assertThat(collector.count()).isZero();
            assertThat(collector.toLatency()).isNull();

            collector.addSample(5000);
            assertThat(collector.count()).isEqualTo(1);
            assertThat(collector.toLatency()).isNotNull();
        }
    }

    @Nested
    @DisplayName("thread safety")
    class ThreadSafetyTests {

        @Test
        @DisplayName("concurrent writers do not lose samples to slot collisions")
        void whenConcurrentWritersThenAllSamplesRecorded() throws InterruptedException {
            val threads   = 8;
            val perThread = 1000;
            val total     = threads * perThread;
            // Capacity equals the total sample count, so no wrapping occurs and
            // every written value must occupy its own distinct slot.
            val collector = new LatencyCollector(total);
            val latch     = new CountDownLatch(threads);

            // Each thread writes a unique, strictly positive range of values so
            // that a lost or clobbered write is observable: a colliding write
            // would leave a slot at its default 0, dropping the minimum to 0 and
            // perturbing the sum, while a duplicated value would change the sum.
            for (int t = 0; t < threads; t++) {
                val base = (long) (t + 1) * perThread;
                Thread.ofVirtual().start(() -> {
                    for (int i = 0; i < perThread; i++) {
                        collector.addSample(base + i);
                    }
                    latch.countDown();
                });
            }

            assertThat(latch.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

            // Expected aggregate over all distinct written values. Any dropped
            // sample (slot left at 0) or clobbered slot would break these.
            var expectedSum = 0L;
            var expectedMin = Long.MAX_VALUE;
            var expectedMax = Long.MIN_VALUE;
            for (int t = 0; t < threads; t++) {
                val base = (long) (t + 1) * perThread;
                for (int i = 0; i < perThread; i++) {
                    val value = base + i;
                    expectedSum += value;
                    expectedMin  = Math.min(expectedMin, value);
                    expectedMax  = Math.max(expectedMax, value);
                }
            }
            val expectedMean = (double) (expectedSum / total);
            val expectedLow  = (double) expectedMin;
            val expectedHigh = (double) expectedMax;

            val latency = collector.toLatency();
            assertThat(collector.count()).isEqualTo(total);
            assertThat(latency).satisfies(l -> {
                assertThat(l.min()).isCloseTo(expectedLow, within(0.1));
                assertThat(l.max()).isCloseTo(expectedHigh, within(0.1));
                assertThat(l.mean()).isCloseTo(expectedMean, within(0.5));
            });
        }
    }

    @Nested
    @DisplayName("invariants")
    class InvariantsTests {

        @Test
        @DisplayName("constructor rejects non-positive capacity")
        void whenCapacityNotPositiveThenThrows() {
            assertThatThrownBy(() -> new LatencyCollector(0)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("capacity");
            assertThatThrownBy(() -> new LatencyCollector(-1)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("capacity");
        }

        @Test
        @DisplayName("addSample does not throw when the internal counter has crossed Integer.MAX_VALUE")
        void whenIndexPastIntegerMaxThenAddSampleStillWorks() throws Exception {
            val collector  = new LatencyCollector(10);
            val indexField = LatencyCollector.class.getDeclaredField("index");
            indexField.setAccessible(true);
            ((AtomicLong) indexField.get(collector)).set((long) Integer.MAX_VALUE + 1);

            assertThatCode(() -> {
                for (int i = 0; i < 100; i++) {
                    collector.addSample(i);
                }
            }).doesNotThrowAnyException();
        }
    }
}
