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
package io.sapl.api.stream;

import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(10)
@DisplayName("Absolute scheduler deadlines beyond the nanosecond horizon")
class RealTimeSchedulerDeadlineTests {

    private static final Instant  REFERENCE     = Instant.parse("2000-01-01T00:00:00Z");
    private static final Duration MAXIMUM_DELAY = Duration.ofNanos(Long.MAX_VALUE);

    @ParameterizedTest(name = "{0} years: callback after {1} timer wakeups")
    @CsvSource({ "40,1", "400,2", "800,3" })
    @DisplayName("intermediate timer wakeups keep the original absolute deadline")
    void whenDeadlineExceedsOneTimerDelayThenCallbackWaitsForTheDeadline(int years, int wakeups) {
        val clock     = new AdjustableClock();
        val executor  = new ScheduledThreadPoolExecutor(1);
        val target    = REFERENCE.plus(Duration.ofDays(years * 365L));
        val callbacks = new AtomicInteger();
        try (val scheduler = new RealTimeScheduler(clock, executor)) {
            scheduler.scheduleAt(target, callbacks::incrementAndGet);
            for (var wakeup = 1; wakeup < wakeups; wakeup++) {
                clock.now.set(REFERENCE.plus(MAXIMUM_DELAY.multipliedBy(wakeup)));
                fireNextTimer(executor);
                assertThat(callbacks).hasValue(0);
                assertThat(executor.getQueue()).hasSize(1);
            }
            clock.now.set(target);
            fireNextTimer(executor);
            assertThat(callbacks).hasValue(1);
            assertThat(executor.getQueue()).isEmpty();
        }
    }

    @ParameterizedTest(name = "{0} years, cancel after intermediate wakeup: {1}")
    @CsvSource({ "400,false", "400,true", "800,false", "800,true" })
    @DisplayName("cancellation prevents a far-future callback before and after rescheduling")
    void whenFarFutureTaskIsCancelledThenNoCallbackRuns(int years, boolean afterIntermediateWakeup) {
        val clock     = new AdjustableClock();
        val executor  = new ScheduledThreadPoolExecutor(1);
        val target    = REFERENCE.plus(Duration.ofDays(years * 365L));
        val callbacks = new AtomicInteger();
        try (val scheduler = new RealTimeScheduler(clock, executor)) {
            val cancellation = scheduler.scheduleAt(target, callbacks::incrementAndGet);
            if (afterIntermediateWakeup) {
                clock.now.set(REFERENCE.plus(MAXIMUM_DELAY));
                fireNextTimer(executor);
                assertThat(callbacks).hasValue(0);
            }
            cancellation.cancel();
            clock.now.set(target);
            fireNextTimer(executor);
            assertThat(callbacks).hasValue(0);
        }
    }

    private static void fireNextTimer(ScheduledThreadPoolExecutor executor) {
        // Advance only the injected clock; release the real timer registration explicitly.
        val timer = executor.getQueue().element();
        assertThat(executor.remove(timer)).isTrue();
        timer.run();
    }

    private static final class AdjustableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(REFERENCE);

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
