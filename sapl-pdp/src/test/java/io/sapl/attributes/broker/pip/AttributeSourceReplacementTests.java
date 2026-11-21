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

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.privateLookupIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.stream.LatestSlotStream;
import io.sapl.api.stream.Stream;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import lombok.val;

@Timeout(15)
@DisplayName("Observations across PIP source replacement")
class AttributeSourceReplacementTests {

    static final Duration      LIMIT = Duration.ofSeconds(5);
    private static final Value BLUE  = Value.of("blue");
    private static final Value RED   = Value.of("red");
    private static final Value GRAY  = Value.of("gray");

    @Test
    @DisplayName("a retired source cannot publish a value it already read before replacement")
    void whenAReadCompletesBeforeReplacementThenItCannotChangeTheCurrentObservation() throws Exception {
        val           firstPublisher  = new AtomicReference<Thread>();
        val           nextObservation = new CompletableFuture<Void>();
        val           allowTimestamp  = new CompletableFuture<Void>();
        InstantSource clock           = () -> {
                                          if (!firstPublisher.compareAndSet(null, Thread.currentThread())
                                                  && firstPublisher.get() == Thread.currentThread()) {
                                              nextObservation.complete(null);
                                              allowTimestamp.orTimeout(LIMIT.toMillis(), TimeUnit.MILLISECONDS).join();
                                          }
                                          return Instant.EPOCH;
                                      };
        try (val broker = new PolicyInformationPointAttributeBroker(Duration.ZERO, null, clock)) {
            val old         = new LatestSlotStream<Value>();
            val replacement = new LatestSlotStream<Value>();
            val handle      = broker.load(new Palette(() -> old));
            val key         = key();
            val received    = new CopyOnWriteArrayList<Value>();
            try (val subscription = broker.open("reader", Set.of(key), values -> {
                received.add(values.get(key).value());
                return Set.of(key);
            })) {
                old.put(BLUE);
                await().atMost(LIMIT).until(() -> received.contains(BLUE));
                val publisher = firstPublisher.get();
                old.put(RED);
                try {
                    nextObservation.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS);
                    // The old pump has a real value, but cannot admit it until the timestamp is available.
                    broker.swap(handle, new Palette(() -> replacement));
                } finally {
                    allowTimestamp.complete(null);
                }
                assertThat(publisher.join(LIMIT)).isTrue();
                assertThat(received).containsExactly(BLUE);
                replacement.put(GRAY);
                await().atMost(LIMIT).untilAsserted(() -> assertThat(received).containsExactly(BLUE, GRAY));
            }
        }
    }

    @ValueSource(booleans = { true, false })
    @ParameterizedTest(name = "replacement supplied an explicit UNDEFINED: {0}")
    @DisplayName("source absence replaces a known value only when the replacement actually supplies it")
    void whenReplacementCompletesThenItsObservedValuesDetermineTheCurrentObservation(boolean explicitAbsence)
            throws Exception {
        try (val broker = new PolicyInformationPointAttributeBroker()) {
            val original = broker.load(publishing(BLUE));
            val key      = key();
            val arrivals = new CopyOnWriteArrayList<Value>();
            try (val current = broker.open("current", Set.of(key), values -> {
                arrivals.add(values.get(key).value());
                return Set.of(key);
            })) {
                await().atMost(LIMIT).until(() -> arrivals.contains(BLUE));
                val drained     = new CompletableFuture<Void>();
                val replacement = new LatestSlotStream<Value>();
                replacement.onClose(() -> drained.complete(null));
                if (explicitAbsence) {
                    replacement.put(Value.UNDEFINED);
                }
                replacement.complete();
                val next = broker.swap(original, new Palette(() -> replacement));
                drained.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS);
                val expected = explicitAbsence ? List.of(BLUE, Value.UNDEFINED) : List.of(BLUE);
                if (explicitAbsence) {
                    await().atMost(LIMIT).untilAsserted(() -> assertThat(arrivals).isEqualTo(expected));
                } else {
                    await().during(Duration.ofMillis(250)).atMost(LIMIT)
                            .untilAsserted(() -> assertThat(arrivals).isEqualTo(expected));
                }
                val late = new CopyOnWriteArrayList<Value>();
                try (val later = broker.open("later", Set.of(key), values -> {
                    late.add(values.get(key).value());
                    return Set.of(key);
                })) {
                    assertThat(late).containsExactly(expected.getLast());
                    broker.swap(next, publishing(GRAY));
                    await().atMost(LIMIT)
                            .untilAsserted(() -> assertThat(late).containsExactly(expected.getLast(), GRAY));
                }
            }
        }
    }

    @Test
    @DisplayName("a public decision and a new one-shot decision both apply an explicit replacement UNDEFINED")
    void whenReplacementDeclaresAbsenceThenOngoingAndNewPublicDecisionsUseIt() throws Exception {
        val policy  = """
                policy "Display blue labels"
                permit
                  <palette.color> == "blue";
                """;
        val request = AuthorizationSubscription.of("reader", "display", "label");
        try (val components = PolicyDecisionPointBuilder.withoutDefaults().withPolicy(policy).build()) {
            val broker = (PolicyInformationPointAttributeBroker) components.attributeBroker();
            val handle = broker.load(publishing(BLUE));
            try (val ongoing = components.pdp().decide(request)) {
                assertThat(ongoing.awaitNext(LIMIT).decision()).isEqualTo(Decision.PERMIT);
                broker.swap(handle, publishing(Value.UNDEFINED));
                val independent = new AuthorizationSubscription(request.subject(), request.action(), request.resource(),
                        request.environment(), Value.ofObject(Map.of("reader", Value.of("independent"))));
                assertThat(components.pdp().decideOnce(independent).decision()).isEqualTo(Decision.DENY);
                await().atMost(LIMIT).untilAsserted(
                        () -> assertThat(components.pdp().decideOnce(request).decision()).isEqualTo(Decision.DENY));
                assertThat(ongoing.awaitNext(LIMIT).decision()).isEqualTo(Decision.DENY);
            }
        }
    }

    static SubscriptionKey key() {
        return key("palette.color");
    }

    static SubscriptionKey key(String attribute) {
        val context = new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        return new SubscriptionKey(new AttributeFinderInvocation("tenant", "configuration", attribute, List.of(),
                Duration.ofMinutes(1), Duration.ofHours(1), Duration.ofSeconds(1), 0L, false, context), false);
    }

    static Object mailboxMonitor(ActiveInvocation active) throws ReflectiveOperationException {
        val type = ActivePolicyInformationPointInvocation.class;
        return privateLookupIn(type, lookup()).findVarHandle(type, "lock", Object.class).get(active);
    }

    private static Palette publishing(Value value) {
        return new Palette(() -> {
            val stream = new LatestSlotStream<Value>();
            stream.put(value);
            return stream;
        });
    }

    @PolicyInformationPoint(name = "palette")
    static final class Palette {
        private final Supplier<Stream<Value>> source;

        Palette(Supplier<Stream<Value>> source) {
            this.source = source;
        }

        @EnvironmentAttribute
        public Stream<Value> color() {
            return source.get();
        }
    }
}
