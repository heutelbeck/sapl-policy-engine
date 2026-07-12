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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Poll;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("Streams")
class StreamsTests {

    private static final Instant REFERENCE = Instant.parse("2025-01-01T00:00:00Z");
    private static final Clock   CLOCK     = Clock.fixed(REFERENCE, ZoneOffset.UTC);

    @Nested
    @DisplayName("just")
    class JustStream {

        @Test
        @DisplayName("emits the value and completes")
        void whenJustThenEmitsValueAndCompletes() throws InterruptedException {
            val stream = Streams.just(Value.of("hello"));

            assertThat(stream.awaitNext()).isEqualTo(Value.of("hello"));
            assertThat(stream.awaitNext()).isNull();
        }
    }

    @Nested
    @DisplayName("error")
    class ErrorStream {

        @Test
        @DisplayName("emits a single error value carrying the message")
        void whenErrorThenEmitsErrorValueAndCompletes() throws InterruptedException {
            val stream = Streams.error("boom");

            val emitted = stream.awaitNext();
            assertThat(emitted).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) emitted).message()).isEqualTo("boom");
            assertThat(stream.awaitNext()).isNull();
        }
    }

    @Nested
    @DisplayName("empty")
    class EmptyStream {

        @Test
        @DisplayName("completes without emitting any value")
        void whenEmptyThenCompletesImmediately() throws InterruptedException {
            val stream = Streams.empty();

            assertThat(stream.awaitNext()).isNull();
        }
    }

    @Nested
    @DisplayName("defer")
    class Defer {

        @Test
        @DisplayName("does not invoke the factory until the stream is first read")
        void whenNotReadThenFactoryNotInvoked() throws InterruptedException {
            val invoked = new AtomicBoolean(false);
            val stream  = Streams.defer(() -> {
                            invoked.set(true);
                            return Streams.just(Value.of("v"));
                        });

            assertThat(invoked).isFalse();

            assertThat(stream.awaitNext()).isEqualTo(Value.of("v"));
            assertThat(invoked).isTrue();
        }

        @Test
        @DisplayName("runs the factory off the calling thread, forwards its value, then completes")
        void whenReadThenFactoryRunsOffThreadAndValueForwarded() throws InterruptedException {
            val callingThread = Thread.currentThread();
            val factoryThread = new AtomicReference<Thread>();
            val stream        = Streams.defer(() -> {
                                  factoryThread.set(Thread.currentThread());
                                  return Streams.just(Value.of("a"));
                              });

            assertThat(stream.awaitNext()).isEqualTo(Value.of("a"));
            assertThat(stream.awaitNext()).isNull();
            assertThat(factoryThread.get()).isNotNull().isNotSameAs(callingThread);
        }

        @Test
        @DisplayName("invokes the factory exactly once, even after the stream completes (one-shot, unlike repeat)")
        void whenStreamCompletesThenFactoryNotReinvoked() throws InterruptedException {
            val count  = new AtomicInteger(0);
            val stream = Streams.defer(() -> {
                           count.incrementAndGet();
                           return Streams.just(Value.of("once"));
                       });

            assertThat(stream.awaitNext()).isEqualTo(Value.of("once"));
            assertThat(stream.awaitNext()).isNull();
            assertThat(stream.awaitNext()).isNull();
            assertThat(count.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("scheduledAt")
    class ScheduledAt {

        private RealTimeScheduler scheduler;

        @BeforeEach
        void setUp() {
            scheduler = new RealTimeScheduler(CLOCK);
        }

        @AfterEach
        void tearDown() {
            scheduler.close();
        }

        @Test
        @DisplayName("emits the value at the scheduled instant and completes")
        void whenScheduledAtFutureThenEmitsAndCompletes() throws InterruptedException {
            val when   = REFERENCE.plusMillis(40);
            val stream = Streams.scheduledAt(Value.of("now"), when, scheduler);

            assertThat(stream.awaitNext()).isEqualTo(Value.of("now"));
            assertThat(stream.awaitNext()).isNull();
        }

        @Test
        @DisplayName("close before fire cancels the scheduled emission")
        void whenCloseBeforeFireThenNoEmission() {
            val when   = REFERENCE.plusMillis(200);
            val stream = Streams.scheduledAt(Value.of("never"), when, scheduler);

            stream.close();

            await().pollDelay(Duration.ofMillis(300)).atMost(Duration.ofMillis(400))
                    .untilAsserted(() -> assertThat(stream.tryNext()).isEqualTo(Poll.done()));
        }
    }

    @Nested
    @DisplayName("concat")
    class Concat {

        private RealTimeScheduler scheduler;

        @BeforeEach
        void setUp() {
            scheduler = new RealTimeScheduler(CLOCK);
        }

        @AfterEach
        void tearDown() {
            scheduler.close();
        }

        @Test
        @DisplayName("delivers each phase across a real time gap")
        void whenConcatOverScheduledTransitionThenBothPhasesDelivered() throws InterruptedException {
            val phase1 = Streams.just(Value.of("phase1"));
            val phase2 = Streams.scheduledAt(Value.of("phase2"), REFERENCE.plusMillis(80), scheduler);

            val stream = Streams.concat(phase1, phase2);

            assertThat(stream.awaitNext()).isEqualTo(Value.of("phase1"));
            assertThat(stream.awaitNext()).isEqualTo(Value.of("phase2"));
            assertThat(stream.awaitNext()).isNull();
        }

        @Test
        @DisplayName("completes after the only source completes")
        void whenSingleSourceThenStreamCompletesAfterDelivery() throws InterruptedException {
            val stream = Streams.concat(Streams.just(Value.of("only")));

            assertThat(stream.awaitNext()).isEqualTo(Value.of("only"));
            assertThat(stream.awaitNext()).isNull();
        }
    }

    @Nested
    @DisplayName("poll")
    class PollHelper {

        @Test
        @DisplayName("emits the supplier's value immediately and again at each interval")
        void whenPollThenEmitsRepeatedly() {
            val callCount = new AtomicInteger();
            val stream    = Streams.poll(Duration.ofMillis(30), () -> {
                              callCount.incrementAndGet();
                              return Value.of("tick");
                          });

            try (stream) {
                await().atMost(Duration.ofSeconds(1)).until(() -> callCount.get() >= 3);
            }
        }

        @Test
        @DisplayName("emits an error value when the supplier throws and continues polling")
        void whenSupplierThrowsThenErrorEmittedAndPollContinues() {
            val callCount = new AtomicInteger();
            val stream    = Streams.poll(Duration.ofMillis(20), () -> {
                              val n = callCount.incrementAndGet();
                              if (n == 1) {
                                  throw new RuntimeException("first call fails");
                              }
                              return Value.of("ok");
                          });

            try (stream) {
                await().atMost(Duration.ofSeconds(1)).until(() -> callCount.get() >= 3);
            }
        }

        @Test
        @DisplayName("close stops the polling loop")
        void whenCloseThenLoopStops() throws InterruptedException {
            val callCount = new AtomicInteger();
            val stream    = Streams.poll(Duration.ofMillis(20), () -> {
                              callCount.incrementAndGet();
                              return Value.of("tick");
                          });

            await().atMost(Duration.ofSeconds(1)).until(() -> callCount.get() >= 1);
            stream.close();
            val countAtClose = callCount.get();

            await().pollDelay(Duration.ofMillis(120)).atMost(Duration.ofMillis(220))
                    .untilAsserted(() -> assertThat(callCount.get()).isLessThanOrEqualTo(countAtClose + 1));
        }
    }

    @Nested
    @DisplayName("scheduledPoll")
    class ScheduledPoll {

        @Test
        @DisplayName("a close racing the reschedule still cancels the freshly scheduled tick")
        void whenCloseRacesRescheduleThenScheduledTickIsCancelled() {
            val lastCancelled = new AtomicBoolean(false);
            val streamRef     = new AtomicReference<Stream<Value>>();
            val pendingTick   = new AtomicReference<Runnable>();
            val scheduleCount = new AtomicInteger();
            // On the reschedule from the manually fired tick, close runs after the tick has
            // passed its stopped re-check but before it stores the returned handle. Without
            // re-checking stopped afterwards, that freshly scheduled handle is leaked.
            val racingScheduler = new TimeScheduler() {
                @Override
                public Cancellable scheduleAt(Instant when, Runnable task) {
                    if (scheduleCount.incrementAndGet() == 1) {
                        pendingTick.set(task);
                        return Cancellable.NOOP;
                    }
                    streamRef.get().close();
                    return () -> lastCancelled.set(true);
                }
            };

            streamRef.set(Streams.scheduledPoll(Duration.ofMillis(10), () -> Value.of("tick"), CLOCK, racingScheduler));
            pendingTick.get().run();

            assertThat(lastCancelled).as("handle scheduled while a close was racing must be cancelled").isTrue();
        }
    }

    @Nested
    @DisplayName("fromBlockingSource")
    class FromBlockingSource {

        @Test
        @DisplayName("emits each value returned by the source until null terminates the loop")
        void whenSourceReturnsThenNullThenStreamCompletes() throws InterruptedException {
            val seq    = new AtomicInteger();
            val stream = Streams.fromBlockingSource(() -> {
                           val n = seq.incrementAndGet();
                           if (n > 2) {
                               return null;
                           }
                           return Value.of("v" + n);
                       });

            while (stream.awaitNext() != null) {
                // drain
            }
        }

        @Test
        @DisplayName("emits an error value when the source throws and completes")
        void whenSourceThrowsThenErrorEmittedAndStreamCompletes() throws InterruptedException {
            val stream = Streams.fromBlockingSource(() -> {
                throw new RuntimeException("nope");
            });

            val v = stream.awaitNext();
            assertThat(v).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) v).message()).isEqualTo("nope");
            assertThat(stream.awaitNext()).isNull();
        }
    }

    @Nested
    @DisplayName("fromCallback")
    class FromCallback {

        @Test
        @DisplayName("delivers values via the emit callback and completes via the complete callback")
        void whenCallbackEmitsAndCompletesThenStreamReflectsBoth() throws InterruptedException {
            val stream = Streams.fromCallback((emit, complete) -> {
                emit.accept(Value.of("only"));
                complete.run();
                return () -> {};
            });

            assertThat(stream.awaitNext()).isEqualTo(Value.of("only"));
            assertThat(stream.awaitNext()).isNull();
        }

        @Test
        @DisplayName("close runs the cleanup runnable returned by the producer")
        void whenCloseThenCleanupRuns() {
            val cleaned = new AtomicBoolean(false);
            val stream  = Streams.fromCallback((emit, complete) -> () -> cleaned.set(true));

            stream.close();

            assertThat(cleaned).isTrue();
        }
    }
}
