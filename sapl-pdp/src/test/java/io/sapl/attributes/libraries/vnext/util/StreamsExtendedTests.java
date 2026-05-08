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

import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("Streams (extended helpers)")
class StreamsExtendedTests {

    private static final Instant T0 = Instant.parse("2026-05-08T12:00:00Z");

    @Nested
    @DisplayName("repeat")
    class Repeat {

        @Test
        @DisplayName("re-creates the source after each completion until closed")
        void whenSourceCompletesThenRepeatRecreates() {
            val factoryCalls = new AtomicInteger();
            val stream       = Streams.repeat(() -> {
                                 factoryCalls.incrementAndGet();
                                 return Streams.just(Value.of("v" + factoryCalls.get()));
                             });

            try (stream) {
                await().atMost(Duration.ofSeconds(1)).until(() -> factoryCalls.get() >= 3);
            }

            assertThat(factoryCalls.get()).isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("close stops the loop and the factory is no longer called")
        void whenCloseThenFactoryStopsBeingCalled() throws InterruptedException {
            val factoryCalls = new AtomicInteger();
            val stream       = Streams.repeat(() -> {
                                 factoryCalls.incrementAndGet();
                                 return Streams.just(Value.of("v"));
                             });

            await().atMost(Duration.ofSeconds(1)).until(() -> factoryCalls.get() >= 2);
            stream.close();
            val countAtClose = factoryCalls.get();
            Thread.sleep(80L);

            assertThat(factoryCalls.get()).isLessThanOrEqualTo(countAtClose + 1);
        }
    }

    @Nested
    @DisplayName("distinctUntilChanged")
    class DistinctUntilChanged {

        @Test
        @DisplayName("first value is always emitted")
        void whenFirstValueThenEmitted() {
            val source = Streams.just(Value.of("first"));

            val stream = Streams.distinctUntilChanged(source);

            StreamAssertions.assertThat(stream).awaitsNext(Value.of("first")).awaitsCompletion();
        }

        @Test
        @DisplayName("re-emitted distinct value passes through after a duplicate is suppressed")
        void whenSequenceAThenAThenBThenOnlyAandBAreObserved() {
            val scheduler = new TestTimeScheduler(T0);
            val a1        = Streams.just(Value.of("A"));
            val a2        = Streams.scheduledAt(Value.of("A"), T0.plusMillis(50), scheduler);
            val b         = Streams.scheduledAt(Value.of("B"), T0.plusMillis(100), scheduler);
            val source    = Streams.concat(a1, a2, b);

            val stream = Streams.distinctUntilChanged(source);

            StreamAssertions.assertThat(stream).awaitsNext(Value.of("A"));
            scheduler.advanceTo(T0.plusMillis(100));
            StreamAssertions.assertThat(stream).awaitsNext(Value.of("B")).awaitsCompletion();
        }
    }

    @Nested
    @DisplayName("scheduledPoll")
    class ScheduledPoll {

        @Test
        @DisplayName("emits source value immediately and then once per interval as scheduler advances")
        void whenSchedulerAdvancesThenEachIntervalEmits() {
            val scheduler = new TestTimeScheduler(T0);
            val clock     = new MutableClock(T0);
            val callCount = new AtomicInteger();
            val stream    = Streams.scheduledPoll(Duration.ofSeconds(5), () -> {
                              callCount.incrementAndGet();
                              return Value.of("tick" + callCount.get());
                          }, clock, scheduler);

            try (stream) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.of("tick1"));
                clock.setInstant(T0.plusSeconds(5));
                scheduler.advanceTo(T0.plusSeconds(5));
                StreamAssertions.assertThat(stream).awaitsNext(Value.of("tick2"));
                clock.setInstant(T0.plusSeconds(10));
                scheduler.advanceTo(T0.plusSeconds(10));
                StreamAssertions.assertThat(stream).awaitsNext(Value.of("tick3"));
            }
        }

        @Test
        @DisplayName("close cancels the pending tick")
        void whenCloseThenPendingTickIsCancelled() {
            val scheduler = new TestTimeScheduler(T0);
            val clock     = new MutableClock(T0);
            val callCount = new AtomicInteger();
            val stream    = Streams.scheduledPoll(Duration.ofSeconds(5), () -> {
                              callCount.incrementAndGet();
                              return Value.of("tick");
                          }, clock, scheduler);

            stream.close();
            scheduler.advanceTo(T0.plusSeconds(5));

            assertThat(callCount).hasValue(1);
        }
    }
}
