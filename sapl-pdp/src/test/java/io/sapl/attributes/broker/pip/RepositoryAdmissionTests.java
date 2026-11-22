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
import static org.assertj.core.api.Assertions.assertThat;

import io.sapl.api.model.Value;
import io.sapl.api.stream.LatestSlotStream;
import io.sapl.attributes.broker.pip.AttributeSourceReplacementTests.Palette;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.AttributeSubscriptionObserver;
import io.sapl.attributes.broker.repository.AttributeAssignment;
import io.sapl.attributes.broker.repository.RepositoryKey;
import io.sapl.attributes.broker.repository.RepositoryState;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Timeout(20)
@DisplayName("Repository view admission and the meaning of explicit bindings")
class RepositoryAdmissionTests {
    private static final RepositoryKey KEY = RepositoryKey.fromInvocation(key().invocation());

    @Test
    @DisplayName("shutdown retires a repository enrollment still preparing a PIP replacement")
    void whenTheBrokerClosesBeforeRepositoryHandoffThenTheReturnedRegistrationIsReleased() throws Exception {
        val entered = new CompletableFuture<Void>();
        val release = new CompletableFuture<Void>();
        val source  = new ViewSource() {
                        @Override
                        public StateRegistration observeState(String tenant, String configuration,
                                Consumer<RepositoryState> observer) {
                            entered.complete(null);
                            release.orTimeout(LIMIT.toMillis(), TimeUnit.MILLISECONDS).join();
                            return super.observeState(tenant, configuration, observer);
                        }
                    };
        val key     = key();
        try (val broker = new PolicyInformationPointAttributeBroker(Duration.ZERO, source)) {
            val handle = broker.load(new Palette(LatestSlotStream::new));
            try (val reader = broker.open("reader", Set.of(key), inputs -> Set.of(key))) {
                val replacement = CompletableFuture.runAsync(handle::unload, Thread::startVirtualThread);
                try {
                    entered.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS);
                    broker.close();
                } finally {
                    release.complete(null);
                }
                replacement.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS);
                assertThat(source.closed).hasValue(1);
            }
        }
    }

    @Test
    @DisplayName("an explicitly published undefined with generation minus one is a new observation")
    void whenTheFirstBindingHasGenerationMinusOneThenBothObserversDistinguishItFromAbsence() {
        val source       = new ViewSource();
        val brokerValues = new CopyOnWriteArrayList<Value>();
        val scalarValues = new CopyOnWriteArrayList<Value>();
        val key          = key();
        try (val broker = new PolicyInformationPointAttributeBroker(Duration.ZERO, source);
                val managed = broker.open("managed", Set.of(key), inputs -> {
                    brokerValues.add(inputs.get(key).value());
                    return Set.of(key);
                });
                val scalar = source.observe(key.invocation(), scalarValues::add)) {
            val update = new RepositoryState(2L, Map.of(KEY, new RepositoryState.Binding(-1L, Value.UNDEFINED)), null);
            source.observers.forEach(observer -> observer.accept(update));
            source.observers.forEach(observer -> observer.accept(update));
            assertThat(List.of(brokerValues, scalarValues))
                    .allSatisfy(values -> assertThat(values).containsExactly(Value.UNDEFINED, Value.UNDEFINED));
        }
    }

    @Test
    @DisplayName("a new key gets its time of first observation while a previously observed binding retains its time")
    void whenASecondKeyJoinsAnEstablishedRouteThenItGetsItsOwnObservationTime() {
        val source = new ViewSource();
        val clock  = new AtomicReference<>(Instant.EPOCH);
        val stamps = new CopyOnWriteArrayList<Instant>();
        val a      = key();
        val b      = key("ward.department");
        try (val broker = new PolicyInformationPointAttributeBroker(Duration.ZERO, source, clock::get);
                val first = broker.open("first", Set.of(a), inputs -> {
                    stamps.add(inputs.get(a).timestamp());
                    return Set.of(a);
                })) {
            clock.set(Instant.EPOCH.plusSeconds(10));
            try (val second = broker.open("second", Set.of(a, b), inputs -> {
                stamps.add(inputs.get(a).timestamp());
                stamps.add(inputs.get(b).timestamp());
                return Set.of(a, b);
            })) {
                assertThat(stamps).containsExactly(Instant.EPOCH, Instant.EPOCH, clock.get());
                assertThat(source.observers).hasSize(1);
            }
        }
    }

    @ValueSource(booleans = { true, false })
    @ParameterizedTest(name = "newer revision already admitted before timestamp failure = {0}")
    @DisplayName("a timestamp failure can retire only a registration and revision that still need admission")
    void whenTimestampingAnIncomingViewFailsThenOnlyTheCurrentRevisionCanTerminateTheConsumer(boolean overtaken)
            throws Exception {
        val           source   = new ViewSource();
        val           enter    = new CompletableFuture<Void>();
        val           resume   = new CompletableFuture<Void>();
        val           armed    = new AtomicBoolean();
        val           failed   = new DateTimeException("Observation clock stopped.");
        InstantSource clock    = () -> {
                                   if (armed.compareAndSet(true, false)) {
                                       enter.complete(null);
                                       resume.orTimeout(LIMIT.toMillis(), TimeUnit.MILLISECONDS).join();
                                       throw failed;
                                   }
                                   return Instant.EPOCH;
                               };
        val           key      = key();
        val           failures = new CopyOnWriteArrayList<RuntimeException>();
        val           values   = new CopyOnWriteArrayList<Value>();
        try (val broker = new PolicyInformationPointAttributeBroker(Duration.ZERO, source, clock);
                val reader = broker.open("reader", Set.of(key), new AttributeSubscriptionObserver(inputs -> {
                    values.add(inputs.get(key).value());
                    return Set.of(key);
                }, failures::add))) {
            val old = source.observers.getFirst();
            old.accept(view(3, Value.TRUE));
            armed.set(true);
            val publish = CompletableFuture.runAsync(() -> old.accept(view(5, Value.FALSE)),
                    Thread::startVirtualThread);
            try {
                enter.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS);
                if (overtaken) {
                    old.accept(view(6, Value.FALSE));
                }
            } finally {
                resume.complete(null);
            }
            publish.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS);
            old.accept(view(4, Value.TRUE));
            if (overtaken) {
                assertThat(failures).isEmpty();
                assertThat(values).containsExactly(Value.UNDEFINED, Value.TRUE, Value.FALSE);
            } else {
                old.accept(view(9, Value.TRUE));
                assertThat(failures).containsExactly(failed);
                assertThat(values).containsExactly(Value.UNDEFINED, Value.TRUE);
                assertThat(source.closed).hasValue(1);
                val recovered = new CopyOnWriteArrayList<Value>();
                try (val second = broker.open("next", Set.of(key), inputs -> {
                    recovered.add(inputs.get(key).value());
                    return Set.of(key);
                })) {
                    source.observers.getLast().accept(view(2, Value.FALSE));
                    assertThat(recovered).containsExactly(Value.UNDEFINED, Value.FALSE);
                }
            }
        }
    }

    @Test
    @DisplayName("a failed repository handoff terminates the managed reader before it can receive a retired grant")
    void whenARepositoryRegistrationFailsDuringPipUnloadThenItsPreparedGrantCannotReachTheReader() throws Exception {
        val           source       = new ViewSource();
        val           entered      = new CompletableFuture<Void>();
        val           resume       = new CompletableFuture<Void>();
        val           unloading    = new AtomicReference<Thread>();
        val           failNext     = new AtomicBoolean();
        val           clockFailure = new DateTimeException("Observation clock stopped.");
        InstantSource clock        = () -> {
                                       if (unloading.compareAndSet(Thread.currentThread(), null)) {
                                           entered.complete(null);
                                           resume.orTimeout(LIMIT.toMillis(), TimeUnit.MILLISECONDS).join();
                                       }
                                       if (failNext.getAndSet(false)) {
                                           throw clockFailure;
                                       }
                                       return Instant.EPOCH;
                                   };
        val           target       = key();
        val           other        = key("ward.department");
        val           failures     = new CopyOnWriteArrayList<RuntimeException>();
        val           values       = new CopyOnWriteArrayList<Value>();
        try (val broker = new PolicyInformationPointAttributeBroker(Duration.ZERO, source, clock);
                val resident = broker.open("resident", Set.of(other), inputs -> Set.of(other))) {
            val pip = broker.load(new Palette(LatestSlotStream::new));
            try (val reader = broker.open("reader", Set.of(target), new AttributeSubscriptionObserver(inputs -> {
                values.add(inputs.get(target).value());
                return Set.of(target);
            }, failures::add))) {
                val removal = CompletableFuture.runAsync(() -> {
                    unloading.set(Thread.currentThread());
                    pip.unload();
                }, Thread::startVirtualThread);
                try {
                    entered.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS);
                    val registration = source.observers.getFirst();
                    registration.accept(view(2, Value.TRUE));
                    failNext.set(true);
                    registration.accept(view(3, Value.FALSE));
                } finally {
                    resume.complete(null);
                }
                removal.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS);
                assertThat(values).doesNotContain(Value.TRUE);
                assertThat(failures).containsExactly(clockFailure);
            }
        }
    }

    private static RepositoryState view(long revision, Value value) {
        return new RepositoryState(revision, Map.of(KEY, new RepositoryState.Binding(revision, value)), null);
    }

    private static class ViewSource implements AttributeRepository {
        final List<Consumer<RepositoryState>> observers = new CopyOnWriteArrayList<>();
        final AtomicInteger                   closed    = new AtomicInteger();

        @Override
        public StateRegistration observeState(String tenant, String configuration, Consumer<RepositoryState> observer) {
            observers.add(observer);
            return new StateRegistration() {
                @Override
                public RepositoryState initialState() {
                    return new RepositoryState(1, Map.of(), null);
                }

                @Override
                public void close() {
                    closed.incrementAndGet();
                }
            };
        }

        @Override
        public void publish(List<AttributeAssignment> assignments) {
            throw new UnsupportedOperationException("This source delivers prepared repository views.");
        }

        @Override
        public void close() {
            observers.clear();
        }
    }
}
