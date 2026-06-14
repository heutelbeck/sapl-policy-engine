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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("CoarseClock")
class CoarseClockTests {

    @Test
    void whenCreatedThenReturnsValidIsoTimestamp() {
        try (var clock = new CoarseClock()) {
            var timestamp = clock.now();

            assertThat(timestamp).isNotNull().isNotEmpty().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*Z");
        }
    }

    @Test
    void whenCalledMultipleTimesThenReturnsConsistentFormat() {
        try (var clock = new CoarseClock()) {
            for (int i = 0; i < 1000; i++) {
                var timestamp = clock.now();
                assertThat(Instant.parse(timestamp)).isNotNull();
            }
        }
    }

    @Test
    void whenTimestampUpdatedThenInstantAndStringAdvance() {
        try (var clock = new CoarseClock()) {
            var initialInstant = clock.instant();
            var initialString  = clock.now();

            // Drive the update directly (rather than racing the background scheduler,
            // which can be starved under CPU/JVM-warmup load) and poll until the wall
            // clock has advanced past the millisecond resolution. Polling lets real
            // time pass without a fixed Thread.sleep, keeping the assertion deterministic.
            await().atMost(Duration.ofSeconds(1)).pollInterval(Duration.ofMillis(1)).untilAsserted(() -> {
                clock.updateTimestamp();
                assertThat(clock.instant()).isAfter(initialInstant);
                assertThat(clock.now()).isNotEqualTo(initialString);
            });
        }
    }

    @Test
    void whenClosedThenStillReturnsLastCachedValue() {
        var clock     = new CoarseClock();
        var timestamp = clock.now();

        clock.close();

        assertThat(clock.now()).isEqualTo(timestamp);
    }

    @Test
    void whenAccessedFromMultipleThreadsThenNoExceptions() throws InterruptedException {
        try (var clock = new CoarseClock(1)) {
            var threads = new Thread[10];
            var errors  = new boolean[1];

            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(() -> {
                    try {
                        for (int j = 0; j < 10000; j++) {
                            var timestamp = clock.now();
                            if (timestamp == null || timestamp.isEmpty()) {
                                errors[0] = true;
                            }
                        }
                    } catch (Exception exception) {
                        errors[0] = true;
                    }
                });
            }

            for (var thread : threads) {
                thread.start();
            }
            for (var thread : threads) {
                thread.join(TimeUnit.SECONDS.toMillis(5));
            }

            assertThat(errors[0]).isFalse();
        }
    }

    @Test
    void whenCustomIntervalProvidedThenUsesCustomInterval() {
        try (var clock = new CoarseClock(50)) {
            var initial = clock.now();

            await().atMost(Duration.ofMillis(200)).pollInterval(Duration.ofMillis(10))
                    .untilAsserted(() -> assertThat(clock.now()).isNotEqualTo(initial));
        }
    }

}
