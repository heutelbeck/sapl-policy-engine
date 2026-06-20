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
package io.sapl.api.test.stream;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Poll;
import io.sapl.api.model.Value;
import io.sapl.api.stream.Stream;
import io.sapl.api.stream.Streams;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Streams error propagation")
class StreamsErrorPropagationTests {

    private static final Instant T0 = Instant.parse("2026-05-08T12:00:00Z");

    @Nested
    @DisplayName("scheduledPoll")
    class ScheduledPoll {

        @Test
        @DisplayName("emits supplier error and continues polling on next tick")
        void whenSupplierThrowsThenEmitsErrorAndContinues() {
            val scheduler = new TestTimeScheduler(T0);
            val clock     = new MutableClock(T0);
            val callCount = new AtomicInteger();
            val stream    = Streams.scheduledPoll(Duration.ofSeconds(1), () -> {
                              val n = callCount.incrementAndGet();
                              if (n == 1) {
                                  throw new RuntimeException("supplier-blew-up");
                              }
                              return Value.of("ok");
                          }, clock, scheduler);

            try (stream) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> {
                    if (!(v instanceof ErrorValue err)) {
                        throw new AssertionError("Expected ErrorValue, got: " + v);
                    }
                    assertThat(err.message()).isEqualTo("supplier-blew-up");
                });
                clock.setInstant(T0.plusSeconds(1));
                scheduler.advanceTo(T0.plusSeconds(1));
                StreamAssertions.assertThat(stream).awaitsNext(Value.of("ok"));
            }
        }

        @Test
        @DisplayName("emits rescheduling error when the clock breaks between ticks (supplier healthy)")
        void whenReschedulingFailsThenEmitsError() {
            val scheduler = new TestTimeScheduler(T0);
            val flippable = new FlippableClock(T0);
            // Break the clock from the start so the very first tick succeeds in
            // calling the supplier (supplier ignores the clock) but the very
            // first rescheduling attempt fails.
            flippable.returnNull();

            val stream = Streams.scheduledPoll(Duration.ofSeconds(1), () -> Value.of("ok"), flippable, scheduler);

            try (stream) {
                val drained = StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(1)).drain();
                assertThat(drained).isNotEmpty();
                val last = drained.get(drained.size() - 1);
                assertThat(last).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) last).message()).contains("Cannot invoke", "Instant.plus");
            }
        }

        @Test
        @DisplayName("preserves supplier's diagnostic when both supplier and rescheduling fail")
        void whenBothFailThenSupplierErrorWinsInTheSlot() {
            val scheduler = new TestTimeScheduler(T0);
            val nullClock = new Clock() {
                              @Override
                              public ZoneId getZone() {
                                  return ZoneOffset.UTC;
                              }

                              @Override
                              public Clock withZone(ZoneId zone) {
                                  return this;
                              }

                              @Override
                              public Instant instant() {
                                  return null;
                              }
                          };
            val stream    = Streams.scheduledPoll(Duration.ofSeconds(1), () -> {
                              throw new IllegalStateException("supplier-says-clock-is-null");
                          }, nullClock, scheduler);

            try (stream) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> {
                    if (!(v instanceof ErrorValue err)) {
                        throw new AssertionError("Expected ErrorValue, got: " + v);
                    }
                    assertThat(err.message()).isEqualTo("supplier-says-clock-is-null");
                }).awaitsCompletion();
            }
        }
    }

    @Nested
    @DisplayName("concat")
    class Concat {

        @Test
        @DisplayName("emits error value when a source's awaitNext throws")
        void whenSourceAwaitNextThrowsThenErrorEmitted() {
            val poison = new Stream<Value>() {
                @Override
                public Value awaitNext() {
                    throw new RuntimeException("poisoned-source");
                }

                @Override
                public Poll<Value> tryNext() {
                    return Poll.empty();
                }

                @Override
                public void close() {
                    // No resources to release: this is a poison fixture for concat
                    // error-propagation testing.
                }
            };

            val stream = Streams.concat(Streams.just(Value.of("a")), poison);

            try (stream) {
                val drained = StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(1)).drain();
                assertThat(drained).isNotEmpty();
                val last = drained.get(drained.size() - 1);
                assertThat(last).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) last).message()).isEqualTo("poisoned-source");
            }
        }
    }

    @Nested
    @DisplayName("repeat")
    class Repeat {

        @Test
        @DisplayName("emits error value when the source factory throws")
        void whenFactoryThrowsThenErrorEmittedAndStreamCompletes() {
            val stream = Streams.repeat(() -> {
                throw new RuntimeException("factory-blew-up");
            });

            try (stream) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(1)).awaitsNext(v -> {
                    if (!(v instanceof ErrorValue err)) {
                        throw new AssertionError("Expected ErrorValue, got: " + v);
                    }
                    assertThat(err.message()).isEqualTo("factory-blew-up");
                }).awaitsCompletion();
            }
        }
    }

    @Nested
    @DisplayName("distinctUntilChanged")
    class DistinctUntilChanged {

        @Test
        @DisplayName("emits error value when the source's awaitNext throws")
        void whenSourceThrowsThenErrorEmitted() {
            val poison = new Stream<Value>() {
                @Override
                public Value awaitNext() {
                    throw new RuntimeException("poisoned-distinct-source");
                }

                @Override
                public Poll<Value> tryNext() {
                    return Poll.empty();
                }

                @Override
                public void close() {
                    // No resources to release: this is a poison fixture for distinctUntilChanged
                    // error-propagation testing.
                }
            };

            val stream = Streams.distinctUntilChanged(poison);

            try (stream) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(1)).awaitsNext(v -> {
                    if (!(v instanceof ErrorValue err)) {
                        throw new AssertionError("Expected ErrorValue, got: " + v);
                    }
                    assertThat(err.message()).isEqualTo("poisoned-distinct-source");
                }).awaitsCompletion();
            }
        }
    }

    /**
     * A {@link Clock} that can be flipped between returning a real instant and
     * returning {@code null}. Used to provoke
     * rescheduling failures in {@code scheduledPoll}.
     */
    private static final class FlippableClock extends Clock {
        private final Instant instant;
        private boolean       broken;

        FlippableClock(Instant instant) {
            this.instant = instant;
        }

        void returnNull() {
            this.broken = true;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return broken ? null : instant;
        }
    }
}
