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

import static io.sapl.attributes.broker.pip.AttributeSourceReplacementTests.LIMIT;
import static io.sapl.attributes.broker.pip.AttributeSourceReplacementTests.key;
import static io.sapl.attributes.broker.pip.AttributeSourceReplacementTests.mailboxMonitor;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.privateLookupIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.InstantSource;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.sapl.api.model.Poll;
import io.sapl.api.model.Value;
import io.sapl.api.stream.LatestSlotStream;
import io.sapl.api.stream.Stream;
import io.sapl.attributes.broker.pip.AttributeSourceReplacementTests.Palette;
import lombok.val;

@Timeout(15)
@DisplayName("Physical PIP retirement releases broker synchronization before calling the source")
class AttributeSourceRetirementTests {

    @Test
    @DisplayName("a failed open disconnects its previously attached source outside the routing lock")
    void whenASecondDependencyCannotOpenThenItsPredecessorDisconnectsOutsideTheBrokerLock() throws Exception {
        val           connected = new CompletableFuture<Void>();
        val           failure   = new DateTimeException("Timestamp unavailable for second dependency");
        InstantSource clock     = () -> {
                                    connected.orTimeout(LIMIT.toMillis(), TimeUnit.MILLISECONDS).join();
                                    throw failure;
                                };
        try (val broker = new PolicyInformationPointAttributeBroker(Duration.ZERO, null, clock)) {
            val routing         = routingLock(broker);
            val heldDuringClose = new AtomicReference<Boolean>();
            val source          = new ConnectedSource(connected,
                    () -> heldDuringClose.set(routing.isHeldByCurrentThread()));
            broker.load(new Palette(() -> source));
            val dependencies = new LinkedHashSet<>(List.of(key(), key("catalog.missing")));
            assertThatThrownBy(() -> broker.open("reader", dependencies, ignored -> dependencies)).isSameAs(failure);
            assertThat(heldDuringClose).as("physical disconnect invoked outside the routing lock").hasValue(false);
        }
    }

    @Test
    @DisplayName("a promotion discarded by broker shutdown releases its prepared source outside the routing lock")
    void whenPromotionFinishesAfterShutdownThenItsUnusedSourceCanDisconnect() throws Exception {
        try (val broker = new PolicyInformationPointAttributeBroker()) {
            val routing         = routingLock(broker);
            val heldDuringClose = new AtomicReference<Boolean>();
            val source          = new LatestSlotStream<Value>();
            source.onClose(() -> heldDuringClose.set(routing.isHeldByCurrentThread()));
            val entered = new CompletableFuture<Void>();
            val release = new CompletableFuture<Void>();
            try (val reader = broker.open("reader", Set.of(key()), ignored -> Set.of(key()))) {
                val promotion = CompletableFuture.runAsync(() -> broker.load(new Palette(() -> {
                    entered.complete(null);
                    release.orTimeout(LIMIT.toMillis(), TimeUnit.MILLISECONDS).join();
                    return source;
                })), Thread::startVirtualThread);
                try {
                    entered.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS);
                    broker.close();
                } finally {
                    release.complete(null);
                }
                promotion.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS);
            }
            assertThat(heldDuringClose).as("abandoned source disconnected outside the routing lock").hasValue(false);
        }
    }

    @Test
    @DisplayName("a replacement rejected by a retired mailbox disconnects outside its mailbox monitor")
    void whenAReplacementArrivesAfterRetirementThenItsSourceCanDisconnect() throws Exception {
        val retired         = active(new LatestSlotStream<>());
        val monitor         = mailboxMonitor(retired);
        val heldDuringClose = new AtomicReference<Boolean>();
        val rejected        = new LatestSlotStream<Value>();
        rejected.onClose(() -> heldDuringClose.set(Thread.holdsLock(monitor)));
        retired.close();
        retired.rebind(rejected, null);
        assertThat(heldDuringClose).as("rejected source disconnected outside the mailbox monitor").hasValue(false);
    }

    private static ReentrantLock routingLock(PolicyInformationPointAttributeBroker broker)
            throws ReflectiveOperationException {
        val type = PolicyInformationPointAttributeBroker.class;
        return (ReentrantLock) privateLookupIn(type, lookup()).findVarHandle(type, "lock", ReentrantLock.class)
                .get(broker);
    }

    private static ActivePolicyInformationPointInvocation active(Stream<Value> source) {
        return new ActivePolicyInformationPointInvocation(key().invocation(), source, null, InstantSource.system(),
                ignored -> {});
    }

    private record ConnectedSource(
            CompletableFuture<Void> connected,
            CompletableFuture<Void> disconnected,
            Runnable onDisconnect) implements Stream<Value> {
        ConnectedSource(CompletableFuture<Void> connected, Runnable onDisconnect) {
            this(connected, new CompletableFuture<>(), onDisconnect);
        }

        @Override
        public Value awaitNext() throws InterruptedException {
            connected.complete(null);
            try {
                disconnected.get();
            } catch (ExecutionException failure) {
                throw new IllegalStateException(failure.getCause());
            } catch (InterruptedException interrupted) {
                // Give the physical cancellation ownership before its interrupted reader also attempts cleanup.
                disconnected.completeOnTimeout(null, LIMIT.toMillis(), TimeUnit.MILLISECONDS).join();
                throw interrupted;
            }
            return null;
        }

        @Override
        public Poll<Value> tryNext() {
            return disconnected.isDone() ? Poll.done() : Poll.empty();
        }

        @Override
        public void close() {
            if (disconnected.complete(null)) {
                onDisconnect.run();
            }
        }
    }
}
