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
package io.sapl.attributes.broker;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Failure-path tests for the blocking eval-loop helper. The operator contract
 * is that a failing evaluator surfaces as
 * the checked {@link EvaluationException} the Javadoc promises, never as an
 * indefinite block on the calling thread.
 */
@DisplayName("BrokerEvalLoops")
class BrokerEvalLoopsTests {

    private static final AttributeAccessContext CTX = new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT,
            Value.EMPTY_OBJECT);

    private static SubscriptionKey liveKey() {
        val invocation = new AttributeFinderInvocation("test-pdp", "default", "test.attribute", List.of(),
                Duration.ofSeconds(1), Duration.ofMillis(100), Duration.ofMillis(100), 0L, false, CTX);
        return new SubscriptionKey(invocation, false);
    }

    /**
     * Broker that fires the consumer callback once, asynchronously, on a separate
     * thread. Any throw from the callback
     * escapes onto that thread, mirroring how the real broker dispatches off the
     * caller's thread; the caller is left
     * blocked on the future unless the helper routes the failure to it.
     */
    private static AttributeBroker asyncFiringBroker(SubscriptionKey key) {
        return new AttributeBroker() {
            @Override
            public Subscription open(String subscriptionId, Set<SubscriptionKey> initialDependencies,
                    Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate) {
                val snapshot = new AttributeSnapshot(Value.of("ready"), Instant.parse("2026-05-17T12:00:00Z"));
                val thread   = new Thread(() -> {
                                 try {
                                     onUpdate.apply(Map.of(key, snapshot));
                                 } catch (RuntimeException ignored) {
                                     // The real broker swallows callback throws; the helper must
                                     // not depend on the broker re-surfacing them to the caller.
                                 }
                             });
                thread.setDaemon(true);
                thread.start();
                return () -> {};
            }

            @Override
            public void close() {
                // no-op
            }
        };
    }

    /**
     * Broker that fires the consumer callback {@code fires} times, synchronously,
     * on the calling thread, swallowing any
     * throw (mirroring the real broker's dispatch). Lets a test drive multiple
     * fires deterministically.
     */
    private static AttributeBroker syncFiringBroker(SubscriptionKey key, int fires) {
        return new AttributeBroker() {
            @Override
            public Subscription open(String subscriptionId, Set<SubscriptionKey> initialDependencies,
                    Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate) {
                val snapshot = new AttributeSnapshot(Value.of("ready"), Instant.parse("2026-05-17T12:00:00Z"));
                for (int i = 0; i < fires; i++) {
                    try {
                        onUpdate.apply(Map.of(key, snapshot));
                    } catch (RuntimeException ignored) {
                        // The real broker swallows callback throws on its dispatch thread.
                    }
                }
                return () -> {};
            }

            @Override
            public void close() {
                // no-op
            }
        };
    }

    @Nested
    @DisplayName("awaitFirstResult failure propagation")
    class AwaitFirstResultFailures {

        @Test
        @DisplayName("when the evaluator throws on an async broker fire then EvaluationException is thrown instead of blocking forever")
        void whenEvaluatorThrowsOnAsyncFireThenEvaluationExceptionInsteadOfHang() {
            val                                                                       key       = liveKey();
            val                                                                       broker    = asyncFiringBroker(
                    key);
            final Function<Map<SubscriptionKey, AttributeSnapshot>, String>           evaluator = snapshot -> {
                                                                                                    throw new IllegalStateException(
                                                                                                            "evaluator boom");
                                                                                                };
            final BiFunction<String, Map<SubscriptionKey, AttributeSnapshot>, String> builder   = (r, snap) -> r;
            final Function<String, Set<SubscriptionKey>>                              nextDeps  = r -> Set.of(key);

            assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> assertThatExceptionOfType(EvaluationException.class)
                            .isThrownBy(() -> BrokerEvalLoops.awaitFirstResult(broker, "sub", Set.of(key), evaluator,
                                    builder, nextDeps))
                            .withRootCauseInstanceOf(IllegalStateException.class)
                            .withMessageContaining("evaluator boom"));
        }

        @Test
        @DisplayName("when nextDeps throws on an async broker fire then EvaluationException is thrown instead of blocking forever")
        void whenNextDepsThrowsOnAsyncFireThenEvaluationExceptionInsteadOfHang() {
            val                                                                       key       = liveKey();
            val                                                                       broker    = asyncFiringBroker(
                    key);
            final Function<Map<SubscriptionKey, AttributeSnapshot>, String>           evaluator = snapshot -> "value";
            final BiFunction<String, Map<SubscriptionKey, AttributeSnapshot>, String> builder   = (r, snap) -> null;
            final Function<String, Set<SubscriptionKey>>                              nextDeps  = r -> {
                                                                                                    throw new IllegalStateException(
                                                                                                            "nextDeps boom");
                                                                                                };

            assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> assertThatExceptionOfType(EvaluationException.class)
                            .isThrownBy(() -> BrokerEvalLoops.awaitFirstResult(broker, "sub", Set.of(key), evaluator,
                                    builder, nextDeps))
                            .withRootCauseInstanceOf(IllegalStateException.class)
                            .withMessageContaining("nextDeps boom"));
        }

        @Test
        @DisplayName("when the builder produces a value then awaitFirstResult returns it")
        void whenBuilderProducesValueThenReturned() {
            val                                                                       key       = liveKey();
            val                                                                       broker    = asyncFiringBroker(
                    key);
            final Function<Map<SubscriptionKey, AttributeSnapshot>, String>           evaluator = snapshot -> "value";
            final BiFunction<String, Map<SubscriptionKey, AttributeSnapshot>, String> builder   = (r, snap) -> "done:"
                    + r;
            final Function<String, Set<SubscriptionKey>>                              nextDeps  = r -> Set.of(key);

            assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> assertThat(
                            BrokerEvalLoops.awaitFirstResult(broker, "sub", Set.of(key), evaluator, builder, nextDeps))
                            .isEqualTo("done:value"));
        }
    }

    /**
     * Streaming counterpart to {@link AwaitFirstResultFailures}. The real broker
     * swallows a callback throw on its
     * dispatch thread and holds no decision sink, so the eval loop itself must
     * surface the failure to the subscriber.
     * These tests prove the throw reaches {@code onInternalError} despite the
     * swallow.
     */
    @Nested
    @DisplayName("openWithHead failure propagation")
    class OpenWithHeadFailures {

        private void assertOnInternalErrorFires(Function<Map<SubscriptionKey, AttributeSnapshot>, String> evaluator,
                BiConsumer<String, Map<SubscriptionKey, AttributeSnapshot>> onResult,
                Function<String, Set<SubscriptionKey>> nextDeps, String expectedMessage) {
            val                              key             = liveKey();
            val                              broker          = asyncFiringBroker(key);
            val                              captured        = new CompletableFuture<RuntimeException>();
            final Consumer<RuntimeException> onInternalError = captured::complete;

            BrokerEvalLoops.openWithHead(broker, "sub", Set.of(key), evaluator, onResult, nextDeps, onInternalError);

            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThat(captured.get())
                    .isInstanceOf(IllegalStateException.class).hasMessage(expectedMessage));
        }

        @Test
        @DisplayName("when the evaluator throws then onInternalError fires despite the broker swallowing the throw")
        void whenEvaluatorThrowsThenOnInternalErrorFires() {
            final Function<Map<SubscriptionKey, AttributeSnapshot>, String> evaluator = snapshot -> {
                throw new IllegalStateException("evaluator boom");
            };
            assertOnInternalErrorFires(evaluator, (r, snap) -> {}, r -> Set.of(liveKey()), "evaluator boom");
        }

        @Test
        @DisplayName("when onResult throws then onInternalError fires despite the broker swallowing the throw")
        void whenOnResultThrowsThenOnInternalErrorFires() {
            final BiConsumer<String, Map<SubscriptionKey, AttributeSnapshot>> onResult = (r, snap) -> {
                throw new IllegalStateException("onResult boom");
            };
            assertOnInternalErrorFires(snapshot -> "value", onResult, r -> Set.of(liveKey()), "onResult boom");
        }

        @Test
        @DisplayName("when nextDeps throws then onInternalError fires despite the broker swallowing the throw")
        void whenNextDepsThrowsThenOnInternalErrorFires() {
            final Function<String, Set<SubscriptionKey>> nextDeps = r -> {
                throw new IllegalStateException("nextDeps boom");
            };
            assertOnInternalErrorFires(snapshot -> "value", (r, snap) -> {}, nextDeps, "nextDeps boom");
        }

        @Test
        @DisplayName("after a callback throw the loop terminates: later broker fires do not re-evaluate")
        void whenCallbackThrowsThenLoopStopsReevaluating() {
            val                                                             key          = liveKey();
            val                                                             broker       = syncFiringBroker(key, 2);
            val                                                             evalCount    = new AtomicInteger();
            val                                                             terminations = new AtomicInteger();
            final Function<Map<SubscriptionKey, AttributeSnapshot>, String> evaluator    = snapshot -> {
                                                                                             evalCount
                                                                                                     .incrementAndGet();
                                                                                             throw new IllegalStateException(
                                                                                                     "evaluator boom");
                                                                                         };

            BrokerEvalLoops.openWithHead(broker, "sub", Set.of(key), evaluator, (r, snap) -> {}, r -> Set.of(liveKey()),
                    cause -> terminations.incrementAndGet());

            assertThat(evalCount).hasValue(1);
            assertThat(terminations).hasValue(1);
        }

        @Test
        @DisplayName("when the policy collapses to no dependencies the loop terminates once with a null cause")
        void whenNextDepsEmptyThenOnTerminateCalledWithNullCause() {
            val                                                             key           = liveKey();
            val                                                             broker        = syncFiringBroker(key, 2);
            val                                                             evalCount     = new AtomicInteger();
            val                                                             terminations  = new AtomicInteger();
            val                                                             capturedCause = new AtomicReference<RuntimeException>(
                    new IllegalStateException("sentinel"));
            final Function<Map<SubscriptionKey, AttributeSnapshot>, String> evaluator     = snapshot -> {
                                                                                              evalCount
                                                                                                      .incrementAndGet();
                                                                                              return "value";
                                                                                          };

            BrokerEvalLoops.openWithHead(broker, "sub", Set.of(key), evaluator, (r, snap) -> {},
                    r -> Set.<SubscriptionKey>of(), cause -> {
                        terminations.incrementAndGet();
                        capturedCause.set(cause);
                    });

            assertThat(evalCount).hasValue(1);
            assertThat(terminations).hasValue(1);
            assertThat(capturedCause.get()).isNull();
        }
    }
}
