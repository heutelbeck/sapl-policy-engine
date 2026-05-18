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

import io.sapl.api.model.Poll;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Tests the {@link Stream#awaitNext(Duration)} default implementation
 * via a minimal in-memory test double that exercises every branch of
 * the watchdog state machine.
 */
@DisplayName("Stream.awaitNext(Duration) default")
class StreamTests {

    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(50);
    private static final Duration LONG_TIMEOUT  = Duration.ofSeconds(2);

    @Nested
    @DisplayName("argument validation")
    class ArgumentValidation {

        static List<Arguments> invalidTimeouts() {
            return List.of(arguments("null", null), arguments("zero", Duration.ZERO),
                    arguments("negative", Duration.ofMillis(-1)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidTimeouts")
        @DisplayName("invalid timeout throws IllegalArgumentException")
        void whenInvalidTimeoutThenIllegalArgument(String description, Duration timeout) {
            try (val stream = new BlockingTestStream<String>()) {
                assertThatThrownBy(() -> stream.awaitNext(timeout)).isInstanceOf(IllegalArgumentException.class);
            }
        }
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("value already available returns immediately without timing out")
        void whenValueAvailableThenReturnsImmediately() throws Exception {
            try (val stream = new BlockingTestStream<String>()) {
                stream.publish("first");

                val result = stream.awaitNext(LONG_TIMEOUT);

                assertThat(result).isEqualTo("first");
            }
        }

        @Test
        @DisplayName("value arriving during the wait is returned")
        void whenValueArrivesDuringWaitThenReturned() throws Exception {
            try (val stream = new BlockingTestStream<String>()) {
                val testThread = Thread.currentThread();
                Thread.startVirtualThread(() -> {
                    awaitTestThreadBlocked(testThread);
                    stream.publish("late");
                });

                val result = stream.awaitNext(LONG_TIMEOUT);

                assertThat(result).isEqualTo("late");
            }
        }

        @Test
        @DisplayName("natural completion returns null without throwing TimeoutException")
        void whenStreamCompletesThenReturnsNull() throws Exception {
            try (val stream = new BlockingTestStream<String>()) {
                val testThread = Thread.currentThread();
                Thread.startVirtualThread(() -> {
                    awaitTestThreadBlocked(testThread);
                    stream.complete();
                });

                val result = stream.awaitNext(LONG_TIMEOUT);

                assertThat(result).isNull();
            }
        }

        @Test
        @DisplayName("successful return leaves caller's interrupt flag clean")
        void whenSuccessfulReturnThenCallerInterruptFlagClean() throws Exception {
            try (val stream = new BlockingTestStream<String>()) {
                stream.publish("ok");

                stream.awaitNext(LONG_TIMEOUT);

                assertThat(Thread.currentThread().isInterrupted()).isFalse();
            }
        }
    }

    @Nested
    @DisplayName("timeout")
    class Timeout {

        @Test
        @DisplayName("no value before deadline throws TimeoutException")
        void whenNoValueBeforeDeadlineThenTimeout() {
            try (val stream = new BlockingTestStream<String>()) {
                assertThatThrownBy(() -> stream.awaitNext(SHORT_TIMEOUT)).isInstanceOf(TimeoutException.class);
            }
        }

        @Test
        @DisplayName("after a TimeoutException the stream is still alive and a follow-up call can succeed")
        void whenTimeoutThenStreamStillAlive() throws Exception {
            try (val stream = new BlockingTestStream<String>()) {
                assertThatThrownBy(() -> stream.awaitNext(SHORT_TIMEOUT)).isInstanceOf(TimeoutException.class);

                stream.publish("after-timeout");

                assertThat(stream.awaitNext(LONG_TIMEOUT)).isEqualTo("after-timeout");
            }
        }

        @Test
        @DisplayName("after TimeoutException the caller's interrupt flag is clean")
        void whenTimeoutThenCallerInterruptFlagClean() {
            try (val stream = new BlockingTestStream<String>()) {
                assertThatThrownBy(() -> stream.awaitNext(SHORT_TIMEOUT)).isInstanceOf(TimeoutException.class);

                assertThat(Thread.currentThread().isInterrupted()).isFalse();
            }
        }
    }

    @Nested
    @DisplayName("external interrupt")
    class ExternalInterrupt {

        @Test
        @DisplayName("external interrupt of the calling thread propagates as InterruptedException, not TimeoutException")
        void whenExternalInterruptThenInterruptedException() {
            try (val stream = new BlockingTestStream<String>()) {
                val testThread = Thread.currentThread();
                Thread.startVirtualThread(() -> {
                    awaitTestThreadBlocked(testThread);
                    testThread.interrupt();
                });

                assertThatThrownBy(() -> stream.awaitNext(LONG_TIMEOUT)).isInstanceOf(InterruptedException.class);
            }
        }
    }

    @Nested
    @DisplayName("watchdog non-interference")
    class WatchdogNonInterference {

        @Test
        @DisplayName("watchdog firing concurrently with successful value does not affect caller")
        void whenWatchdogFiresAtSameInstantAsValueArrivesThenNoSpuriousInterrupt() {
            // Stress the race intentionally: the publish thread sleeps for the
            // exact same duration as the timeout, so the value lands at the
            // same instant the watchdog fires. Pattern A (state-based wait)
            // would defeat the test by guaranteeing publish-before-timeout.
            // Repeat to make the race likely on at least one iteration.
            val totalIterations    = 100;
            val spuriousInterrupts = new AtomicInteger();
            val timeoutCount       = new AtomicInteger();
            val successCount       = new AtomicInteger();

            val raceWindow = Duration.ofMillis(20);

            for (int i = 0; i < totalIterations; i++) {
                try (val stream = new BlockingTestStream<String>()) {
                    Thread.startVirtualThread(() -> {
                        try {
                            Thread.sleep(raceWindow);
                            stream.publish("racey");
                        } catch (InterruptedException ignored) {
                            // virtual thread exits, nothing else to do
                        }
                    });

                    try {
                        val result = stream.awaitNext(raceWindow);
                        if ("racey".equals(result)) {
                            successCount.incrementAndGet();
                        }
                        if (Thread.currentThread().isInterrupted()) {
                            val wasSet = Thread.interrupted();
                            if (wasSet) {
                                spuriousInterrupts.incrementAndGet();
                            }
                        }
                    } catch (TimeoutException timeoutException) {
                        timeoutCount.incrementAndGet();
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("unexpected InterruptedException leaked from awaitNext",
                                interruptedException);
                    }
                }
            }

            // The race must resolve into either a value or a TimeoutException,
            // never a spurious interrupt left on the caller's flag after a
            // successful return.
            assertThat(spuriousInterrupts.get()).isZero();
            assertThat(successCount.get() + timeoutCount.get()).isEqualTo(totalIterations);
        }

        @Test
        @DisplayName("after a successful return, a subsequent blocking operation is not interrupted spuriously")
        void whenSuccessfulReturnThenSubsequentBlockingOperationNotInterrupted() throws Exception {
            try (val stream = new BlockingTestStream<String>()) {
                stream.publish("first");
                stream.awaitNext(SHORT_TIMEOUT);

                // If the watchdog had set the caller's interrupt flag spuriously,
                // the next blocking call would throw InterruptedException
                // immediately. Sleep is the simplest such call.
                assertThat(Thread.currentThread().isInterrupted()).isFalse();
                Thread.sleep(5);
            }
        }
    }

    /**
     * Polls the given test thread until it has parked on a blocking
     * call (typically the {@code Object.wait()} inside our test
     * double's {@code awaitNext()}). Replaces fixed sleep delays in
     * publish-helper threads with deterministic state-based
     * synchronization. Polling cadence is tight so the publish lands
     * within milliseconds of the test thread reaching the blocked
     * state.
     */
    private static void awaitTestThreadBlocked(Thread testThread) {
        Awaitility.await().atMost(LONG_TIMEOUT).pollInterval(Duration.ofMillis(2)).until(() -> {
            val state = testThread.getState();
            return state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING;
        });
    }

    /**
     * Minimal {@link Stream} test double whose {@code awaitNext()}
     * blocks on a wait/notify primitive that honours
     * {@link Thread#interrupt()}. Synchronous publish/complete from any
     * thread.
     */
    private static final class BlockingTestStream<T> implements Stream<T> {

        private final Object lock = new Object();
        private T            pending;
        private boolean      hasValue;
        private boolean      completed;
        private boolean      closed;

        void publish(T value) {
            synchronized (lock) {
                pending  = value;
                hasValue = true;
                lock.notifyAll();
            }
        }

        void complete() {
            synchronized (lock) {
                completed = true;
                lock.notifyAll();
            }
        }

        @Override
        public T awaitNext() throws InterruptedException {
            synchronized (lock) {
                while (!hasValue && !completed && !closed) {
                    lock.wait();
                }
                if (hasValue) {
                    val v = pending;
                    pending  = null;
                    hasValue = false;
                    return v;
                }
                return null;
            }
        }

        @Override
        public Poll<T> tryNext() {
            synchronized (lock) {
                if (hasValue) {
                    val v = pending;
                    pending  = null;
                    hasValue = false;
                    return Poll.value(v);
                }
                if (completed || closed) {
                    return Poll.done();
                }
                return Poll.empty();
            }
        }

        @Override
        public void close() {
            synchronized (lock) {
                closed = true;
                lock.notifyAll();
            }
        }
    }

}
