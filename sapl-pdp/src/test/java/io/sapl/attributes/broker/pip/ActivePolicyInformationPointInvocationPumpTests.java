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
package io.sapl.attributes.broker.pip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.Poll;
import io.sapl.api.model.Value;
import io.sapl.api.stream.Stream;
import lombok.val;

/**
 * Domain requirements for the PIP pump across a hot-swap rebind. A hot-swap
 * replaces the source stream; the contract is that exactly one pump drives a
 * stream, the superseded pump terminates instead of migrating onto the new
 * stream, and no pump thread accumulates over repeated rebinds.
 */
@DisplayName("PIP pump is bound to its stream across hot-swap rebind")
class ActivePolicyInformationPointInvocationPumpTests {

    private static final AttributeAccessContext EMPTY_CONTEXT = new AttributeAccessContext(Value.EMPTY_OBJECT,
            Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);

    private static final Duration HOLD  = Duration.ofMillis(400);
    private static final Duration LIMIT = Duration.ofSeconds(3);

    private static AttributeFinderInvocation invocation() {
        return new AttributeFinderInvocation("default", "test.attr", List.of(), Duration.ofSeconds(1),
                Duration.ofMillis(100), Duration.ofMillis(100), 0L, false, EMPTY_CONTEXT);
    }

    private static ActivePolicyInformationPointInvocation pump(Stream<Value> source, Consumer<Value> onValue) {
        return new ActivePolicyInformationPointInvocation(invocation(), source, null, InstantSource.system(), onValue);
    }

    /**
     * Test stream whose {@code awaitNext} blocks until a value is emitted or the
     * stream is closed. Records the distinct threads that consume it and the peak
     * concurrent consumers, so a migrated second pump is observable.
     */
    private static final class ControllableStream implements Stream<Value> {

        private static final Object POISON = new Object();

        private final LinkedBlockingQueue<Object> queue             = new LinkedBlockingQueue<>();
        private final Set<Long>                   consumerThreadIds = ConcurrentHashMap.newKeySet();
        private final AtomicInteger               concurrent        = new AtomicInteger();
        private final AtomicInteger               maxConcurrent     = new AtomicInteger();
        private final AtomicInteger               awaitCalls        = new AtomicInteger();
        private final AtomicInteger               closeCalls        = new AtomicInteger();

        @Override
        public Value awaitNext() throws InterruptedException {
            awaitCalls.incrementAndGet();
            consumerThreadIds.add(Thread.currentThread().threadId());
            val active = concurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(active, Math::max);
            try {
                val taken = queue.take();
                return taken == POISON ? null : (Value) taken;
            } finally {
                concurrent.decrementAndGet();
            }
        }

        @Override
        public Poll<Value> tryNext() {
            val taken = queue.poll();
            if (taken == null) {
                return Poll.empty();
            }
            return taken == POISON ? Poll.done() : Poll.value((Value) taken);
        }

        @Override
        public void close() {
            closeCalls.incrementAndGet();
            queue.offer(POISON);
        }

        void emit(Value value) {
            queue.offer(value);
        }

        int distinctConsumers() {
            return consumerThreadIds.size();
        }
    }

    @Test
    @DisplayName("a pump caught between values during a rebind exits instead of migrating onto the new stream")
    void whenRebindRacesPumpThenOldPumpDoesNotMigrateToNewStream() {
        val published = new CopyOnWriteArrayList<Value>();
        val parked    = new CountDownLatch(1);
        val firstSeen = new CountDownLatch(1);
        // Park the pump in the onValue handler after it pulls its first value:
        // this places it exactly between awaitNext returning and its next loop.
        Consumer<Value> onValue    = value -> {
                                       published.add(value);
                                       if (firstSeen.getCount() > 0) {
                                           firstSeen.countDown();
                                           try {
                                               parked.await();
                                           } catch (InterruptedException e) {
                                               Thread.currentThread().interrupt();
                                           }
                                       }
                                   };
        val             oldStream  = new ControllableStream();
        val             newStream  = new ControllableStream();
        val             invocation = pump(oldStream, onValue);
        invocation.start();

        oldStream.emit(Value.TRUE);
        await().atMost(LIMIT).until(() -> firstSeen.getCount() == 0);

        invocation.rebind(newStream, null);
        parked.countDown();

        await().atMost(LIMIT).until(() -> newStream.awaitCalls.get() >= 1);
        await().during(HOLD).atMost(LIMIT).untilAsserted(() -> assertThat(newStream.distinctConsumers()).isEqualTo(1));
        assertThat(newStream.maxConcurrent.get()).isEqualTo(1);

        invocation.close();
    }

    @Test
    @DisplayName("the old pump publishes its already-pulled value once on rebind, then exits without rereading the old stream")
    void whenRebindAfterPullThenOneTrailingEmitThenOldPumpExits() {
        val published = new CopyOnWriteArrayList<Value>();
        val parked    = new CountDownLatch(1);
        val firstSeen = new CountDownLatch(1);
        // Park the pump in the handler right after it pulls its first value, placing
        // it between awaitNext returning and its next loop iteration.
        Consumer<Value> onValue    = value -> {
                                       published.add(value);
                                       if (firstSeen.getCount() > 0) {
                                           firstSeen.countDown();
                                           try {
                                               parked.await();
                                           } catch (InterruptedException e) {
                                               Thread.currentThread().interrupt();
                                           }
                                       }
                                   };
        val             oldStream  = new ControllableStream();
        val             newStream  = new ControllableStream();
        val             invocation = pump(oldStream, onValue);
        invocation.start();

        oldStream.emit(Value.TRUE);
        await().atMost(LIMIT).until(() -> firstSeen.getCount() == 0);
        val oldAwaitCallsAtRebind = oldStream.awaitCalls.get();

        invocation.rebind(newStream, null);
        parked.countDown();

        // The value pulled before the rebind is published exactly once as the trailing
        // emit; the superseded pump then exits on the next loop guard and never awaits
        // the old stream again.
        await().atMost(LIMIT).until(() -> published.contains(Value.TRUE));
        await().during(HOLD).atMost(LIMIT)
                .untilAsserted(() -> assertThat(oldStream.awaitCalls.get()).isEqualTo(oldAwaitCallsAtRebind));
        assertThat(published).containsExactly(Value.TRUE);

        invocation.close();
    }

    @Test
    @DisplayName("after a hot-swap only the new pump consumes the new stream and its values are published")
    void whenRebindThenNewStreamHasOneConsumerAndPublishes() {
        val published  = new CopyOnWriteArrayList<Value>();
        val oldStream  = new ControllableStream();
        val newStream  = new ControllableStream();
        val invocation = pump(oldStream, published::add);
        invocation.start();
        await().atMost(LIMIT).until(() -> oldStream.awaitCalls.get() >= 1);

        invocation.rebind(newStream, null);
        await().atMost(LIMIT).until(() -> newStream.awaitCalls.get() >= 1);
        newStream.emit(Value.of("after-swap"));
        await().atMost(LIMIT).until(() -> published.contains(Value.of("after-swap")));

        assertThat(oldStream.closeCalls.get()).isGreaterThanOrEqualTo(1);
        assertThat(newStream.distinctConsumers()).isEqualTo(1);
        assertThat(newStream.maxConcurrent.get()).isEqualTo(1);

        invocation.close();
    }

    @Test
    @DisplayName("repeated hot-swaps do not accumulate pumps - the final stream has exactly one consumer")
    void whenManyRebindsThenNoPumpAccumulation() {
        val invocation = pump(new ControllableStream(), value -> {});
        invocation.start();
        ControllableStream current = new ControllableStream();
        for (var i = 0; i < 20; i++) {
            current = new ControllableStream();
            invocation.rebind(current, null);
        }
        val finalStream = current;
        await().atMost(LIMIT).until(() -> finalStream.awaitCalls.get() >= 1);
        await().during(HOLD).atMost(LIMIT)
                .untilAsserted(() -> assertThat(finalStream.distinctConsumers()).isEqualTo(1));
        assertThat(finalStream.maxConcurrent.get()).isEqualTo(1);

        invocation.close();
    }

    @Test
    @DisplayName("close terminates the pump - it consumes nothing further")
    void whenClosedThenPumpExits() {
        val stream     = new ControllableStream();
        val invocation = pump(stream, value -> {});
        invocation.start();
        await().atMost(LIMIT).until(() -> stream.awaitCalls.get() >= 1);

        invocation.close();

        assertThat(invocation.isClosed()).isTrue();
        assertThat(stream.closeCalls.get()).isGreaterThanOrEqualTo(1);
        val callsAtClose = stream.awaitCalls.get();
        await().during(HOLD).atMost(LIMIT)
                .untilAsserted(() -> assertThat(stream.awaitCalls.get()).isEqualTo(callsAtClose));
    }

    @Test
    @DisplayName("values from a single stream are published in order by the one pump")
    void whenSingleStreamThenValuesPublishedInOrder() {
        val published  = new CopyOnWriteArrayList<Value>();
        val stream     = new ControllableStream();
        val invocation = pump(stream, published::add);
        invocation.start();

        stream.emit(Value.of("a"));
        stream.emit(Value.of("b"));
        stream.emit(Value.of("c"));
        await().atMost(LIMIT).until(() -> published.size() >= 3);

        assertThat(published).containsExactly(Value.of("a"), Value.of("b"), Value.of("c"));
        assertThat(stream.maxConcurrent.get()).isEqualTo(1);

        invocation.close();
    }
}
