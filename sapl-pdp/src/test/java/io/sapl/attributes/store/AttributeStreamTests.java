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
package io.sapl.attributes.store;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Poll;
import io.sapl.api.model.Value;
import io.sapl.api.stream.LatestSlotStream;
import io.sapl.api.stream.Stream;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Tests {@link AttributeStream}'s perpetual-poll loop, retry burst,
 * timeout watchdog, abandonment fallback, and close behaviour, all
 * driven against a {@link ControlledSource} test double instead of a
 * real PIP.
 */
@DisplayName("AttributeStream")
class AttributeStreamTests {

    private static final Duration POLL_INTERVAL   = Duration.ofMillis(20);
    private static final Duration BACKOFF         = Duration.ofMillis(10);
    private static final Duration INITIAL_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration AWAIT_BUDGET    = Duration.ofSeconds(2);

    private static final AttributeAccessContext EMPTY_CONTEXT = new AttributeAccessContext(Value.EMPTY_OBJECT,
            Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);

    private static AttributeFinderInvocation invocation(String name) {
        return invocation(name, INITIAL_TIMEOUT, POLL_INTERVAL, BACKOFF, 0L);
    }

    private static AttributeFinderInvocation invocation(String name, Duration initialTimeOut, Duration pollInterval,
            Duration backoff, long retries) {
        return new AttributeFinderInvocation("default", "test." + name, List.of(), initialTimeOut, pollInterval,
                backoff, retries, false, EMPTY_CONTEXT);
    }

    private static Value valueOrNull(Poll<Value> poll) {
        return poll instanceof Poll.Value(Value v) ? v : null;
    }

    @Nested
    @DisplayName("argument validation")
    class ArgumentValidation {

        static List<Arguments> invalidInvocations() {
            return List.of(arguments("zero initialTimeOut", invocation("a", Duration.ZERO, POLL_INTERVAL, BACKOFF, 0L)),
                    arguments("negative initialTimeOut",
                            invocation("a", Duration.ofMillis(-1), POLL_INTERVAL, BACKOFF, 0L)),
                    arguments("zero pollInterval", invocation("a", INITIAL_TIMEOUT, Duration.ZERO, BACKOFF, 0L)),
                    arguments("negative pollInterval",
                            invocation("a", INITIAL_TIMEOUT, Duration.ofMillis(-1), BACKOFF, 0L)),
                    arguments("zero backoff", invocation("a", INITIAL_TIMEOUT, POLL_INTERVAL, Duration.ZERO, 0L)),
                    arguments("negative backoff",
                            invocation("a", INITIAL_TIMEOUT, POLL_INTERVAL, Duration.ofMillis(-1), 0L)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidInvocations")
        @DisplayName("non-positive timing parameter is rejected")
        void whenInvalidTimingThenIllegalArgument(String description, AttributeFinderInvocation badInvocation) {
            val source = new ControlledSource();
            assertThatThrownBy(() -> new AttributeStream(badInvocation, source))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("source emits one value then completes; pump waits pollInterval and re-invokes the supplier")
        void whenCleanCycleThenPollIntervalThenNextCycle() throws Exception {
            val source = new ControlledSource(() -> oneShot(Value.of("v1")), () -> oneShot(Value.of("v2")));
            try (val stream = new AttributeStream(invocation("a"), source)) {

                assertThat(stream.awaitNext()).isEqualTo(Value.of("v1"));
                assertThat(stream.awaitNext()).isEqualTo(Value.of("v2"));
                Awaitility.await().atMost(AWAIT_BUDGET).until(() -> source.invocations() >= 2);
            }
        }

        @Test
        @DisplayName("source emits multiple values within a cycle; all reach the consumer (latest-wins)")
        void whenMultipleEmissionsThenAllReachConsumer() throws Exception {
            val inner = new ScriptedStream();
            inner.emit(Value.of("a"));
            inner.emit(Value.of("b"));
            inner.complete();
            val source = new ControlledSource(() -> inner);

            try (val stream = new AttributeStream(invocation("multi"), source)) {

                // Latest-wins semantics in the slot, so we may see "b" directly
                // or "a" then "b" depending on consumer cadence.
                Awaitility.await().atMost(AWAIT_BUDGET)
                        .until(() -> Value.of("b").equals(valueOrNull(stream.tryNext())));
            }
        }
    }

    @Nested
    @DisplayName("timeout")
    class Timeout {

        @Test
        @DisplayName("first-emit slower than initialTimeOut publishes ErrorValue and enters retry burst")
        void whenFirstEmitSlowerThanTimeoutThenErrorValueThenRetry() throws Exception {
            val firstInner  = new ScriptedStream();   // never emits, never completes
            val secondInner = new ScriptedStream();
            secondInner.emit(Value.of("recovered"));
            secondInner.complete();
            val source = new ControlledSource(() -> firstInner, () -> secondInner);

            try (val stream = new AttributeStream(invocation("slow", Duration.ofMillis(50), POLL_INTERVAL, BACKOFF, 1L),
                    source)) {

                val firstObserved = stream.awaitNext();
                assertThat(firstObserved).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) firstObserved).message()).contains("produced no first value");

                val recovered = stream.awaitNext();
                assertThat(recovered).isEqualTo(Value.of("recovered"));
            }
        }

    }

    @Nested
    @DisplayName("retry burst")
    class RetryBurst {

        @Test
        @DisplayName("inner errors then succeeds: retry yields the new value")
        void whenInnerErrorsThenSucceedsThenRetryRecovers() throws Exception {
            val erroring = new ScriptedStream();
            erroring.error(new RuntimeException("boom"));
            val good = new ScriptedStream();
            good.emit(Value.of("ok"));
            good.complete();
            val source = new ControlledSource(() -> erroring, () -> good);

            try (val stream = new AttributeStream(invocation("retryOk", INITIAL_TIMEOUT, POLL_INTERVAL, BACKOFF, 1L),
                    source)) {

                assertThat(stream.awaitNext()).isEqualTo(Value.of("ok"));
            }
        }

        @Test
        @DisplayName("retries exhausted publishes transient ErrorValue; outer poll loop continues")
        void whenRetriesExhaustedThenErrorValueThenNextCycleRecovers() throws Exception {
            val first = new ScriptedStream();
            first.error(new RuntimeException("first-fail"));
            val second = new ScriptedStream();
            second.error(new RuntimeException("second-fail"));
            val third = new ScriptedStream();
            third.emit(Value.of("after-burst"));
            third.complete();
            val source = new ControlledSource(() -> first, () -> second, () -> third);

            try (val stream = new AttributeStream(invocation("burst", INITIAL_TIMEOUT, POLL_INTERVAL, BACKOFF, 1L),
                    source)) {

                val transientErr = stream.awaitNext();
                assertThat(transientErr).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) transientErr).message()).contains("retries exhausted");

                val recovered = stream.awaitNext();
                assertThat(recovered).isEqualTo(Value.of("after-burst"));
            }
        }

        @Test
        @DisplayName("supplier itself throws repeatedly: ErrorValue published, outer poll loop continues")
        void whenSupplierThrowsRepeatedlyThenErrorValueAndContinues() throws Exception {
            val                     attempts = new AtomicInteger();
            Supplier<Stream<Value>> source   = () -> {
                                                 val n = attempts.incrementAndGet();
                                                 if (n < 3) {
                                                     throw new RuntimeException("supplier-fail-" + n);
                                                 }
                                                 val good = new ScriptedStream();
                                                 good.emit(Value.of("supplier-ok"));
                                                 good.complete();
                                                 return good;
                                             };

            try (val stream = new AttributeStream(
                    invocation("supplierThrows", INITIAL_TIMEOUT, POLL_INTERVAL, BACKOFF, 1L), source)) {

                val transientErr = stream.awaitNext();
                assertThat(transientErr).isInstanceOf(ErrorValue.class);

                val recovered = stream.awaitNext();
                assertThat(recovered).isEqualTo(Value.of("supplier-ok"));
            }
        }
    }

    @Nested
    @DisplayName("close")
    class Close {

        @Test
        @DisplayName("close mid-cycle terminates the pump and the consumer's awaitNext returns null")
        void whenClosedMidCycleThenAwaitNextReturnsNull() throws Exception {
            val inner  = new ScriptedStream();   // never emits
            val source = new ControlledSource(() -> inner);

            val stream = new AttributeStream(invocation("closeMid"), source);

            Thread.startVirtualThread(() -> {
                try {
                    Thread.sleep(Duration.ofMillis(50));
                    stream.close();
                } catch (InterruptedException ignored) {
                    // Test cleanup helper: the close-after-delay closure is fire-and-forget; if
                    // interrupted we simply skip the close.
                }
            });

            assertThat(stream.awaitNext()).isNull();
        }

        @Test
        @DisplayName("close is idempotent")
        void whenClosedTwiceThenNoError() throws Exception {
            val source = new ControlledSource(() -> {
                           val s = new ScriptedStream();
                           s.emit(Value.of("x"));
                           s.complete();
                           return s;
                       });
            val stream = new AttributeStream(invocation("twice"), source);
            stream.close();
            stream.close();
        }

        @Test
        @DisplayName("after close, tryNext returns Done")
        void whenClosedThenTryNextDone() {
            val source = new ControlledSource();
            val stream = new AttributeStream(invocation("done"), source);
            stream.close();

            assertThat(stream.tryNext()).isEqualTo(Poll.done());
        }
    }

    private static Stream<Value> oneShot(Value v) {
        val s = new ScriptedStream();
        s.emit(v);
        s.complete();
        return s;
    }

    /**
     * Supplier whose successive calls return preconfigured streams.
     * Counts invocations for assertions.
     */
    private static final class ControlledSource implements Supplier<Stream<Value>> {

        private final List<Supplier<Stream<Value>>> scripts;
        private final AtomicInteger                 cursor      = new AtomicInteger();
        private final AtomicInteger                 invocations = new AtomicInteger();

        @SafeVarargs
        ControlledSource(Supplier<Stream<Value>>... scripts) {
            this.scripts = List.of(scripts);
        }

        @Override
        public Stream<Value> get() {
            invocations.incrementAndGet();
            val idx = cursor.getAndIncrement();
            if (idx >= scripts.size()) {
                // After scripted runs are exhausted, return an empty quickly-completing stream.
                val s = new ScriptedStream();
                s.complete();
                return s;
            }
            return scripts.get(idx).get();
        }

        int invocations() {
            return invocations.get();
        }
    }

    /**
     * Scriptable {@link Stream} test double backed by a
     * {@link LatestSlotStream}; tests can drive emit/error/complete
     * from any thread.
     */
    private static class ScriptedStream implements Stream<Value> {

        protected final LatestSlotStream<Value> backing = new LatestSlotStream<>();

        void emit(Value v) {
            backing.put(v);
        }

        void error(RuntimeException e) {
            backing.put(Value.error(e.getMessage()));
            backing.close();
        }

        void complete() {
            backing.close();
        }

        @Override
        public Value awaitNext() throws InterruptedException {
            val next = backing.awaitNext();
            if (next instanceof ErrorValue) {
                throw new RuntimeException(((ErrorValue) next).message());
            }
            return next;
        }

        @Override
        public Poll<Value> tryNext() {
            return backing.tryNext();
        }

        @Override
        public void close() {
            backing.close();
        }
    }

}
