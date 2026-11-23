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
package io.sapl.pdp;

import static io.sapl.api.model.Value.of;
import static io.sapl.api.pdp.AuthorizationSubscription.of;
import static io.sapl.api.stream.Streams.just;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static io.sapl.util.SaplTesting.functionBroker;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.stream.Stream;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.api.pdp.SubscriptionLifecycleListener;
import io.sapl.api.pdp.TracedDecision;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.attributes.broker.api.TestAttributeBroker;

import lombok.val;

@Timeout(15)
@DisplayName("One decision from the active tenant configuration")
class DecideOnceConfigurationTests {

    private static final String                    TENANT        = "archive";
    private static final String                    ATTRIBUTE     = "access.approved";
    private static final AuthorizationSubscription REQUEST       = of("alice", "read", "ledger");
    private static final String                    PENDING       = """
            policy "pending approval" permit <access.approved>;
            """;
    private static final String                    PERMIT_LEDGER = """
            policy "ledger access" permit subject == "alice"; action == "read"; resource == "ledger"; transform resource
            """;
    private static final String                    DENY          = """
            policy "closed" deny
            """;

    @CsvSource({ "true, false", "false, false", "true, true", "false, true" })
    @ParameterizedTest(name = "Previous approval {0}, interceptor installed {1}")
    @DisplayName("A replacement decides the original request before the old finder delivers")
    void whenFinderIsPendingThenReplacementDecides(boolean previousApproval, boolean observed) throws Exception {
        val reports = new CopyOnWriteArrayList<TracedDecision>();
        try (val broker = new TestAttributeBroker(); val executor = newVirtualThreadPerTaskExecutor()) {
            broker.register(ATTRIBUTE);
            val builder = PolicyDecisionPointBuilder.withoutDefaults().withAttributeBroker(broker);
            if (observed) {
                builder.withDecisionInterceptor((trace, timestamp, id, sub) -> reports.add(trace));
            }
            try (val components = builder.build()) {
                val source = components.pdpVoterSource();
                source.loadConfiguration(configuration(TENANT, PENDING));
                val decision = executor.submit(() -> components.pdp().decideOnce(REQUEST, TENANT));
                try {
                    awaitFinder(broker);
                    source.loadConfiguration(configuration(TENANT, previousApproval ? DENY : PERMIT_LEDGER));
                    val expected = previousApproval ? AuthorizationDecision.DENY
                            : new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                                    of("ledger"));
                    assertThat(decision.get(3, SECONDS)).isEqualTo(expected);
                    if (observed) {
                        assertThat(reports).extracting(TracedDecision::authorizationDecision).containsExactly(expected);
                    }
                    assertThat(broker.openSubscriptions()).isEmpty();
                } finally {
                    broker.publishByName(ATTRIBUTE, of(previousApproval));
                    decision.cancel(true);
                }
            }
        }
    }

    @Test
    @DisplayName("a failed evaluator is attributed to the active configuration on both public coverage paths")
    void whenEvaluationFailsThenStreamingAndOneShotReturnTheSameErrorVote() throws Exception {
        val functions = functionBroker("records.check", arguments -> {
            throw new IllegalStateException("records unavailable");
        });
        try (val components = PolicyDecisionPointBuilder.withoutDefaults().withFunctionBroker(functions).build()) {
            components.pdpVoterSource()
                    .loadConfiguration(configuration(TENANT, "policy \"access\" permit records.check(subject);"));
            val once = components.pdp().decideOnceWithCoverage(REQUEST, TENANT).vote();
            try (val rounds = components.pdp().decideWithCoverage(REQUEST, TENANT)) {
                val stream = rounds.awaitNext(Duration.ofSeconds(3)).vote();
                assertThat(stream).isEqualTo(once);
                assertThat(stream.authorizationDecision()).isEqualTo(components.pdp().decideOnce(REQUEST, TENANT));
                assertThat(stream.voter().configurationId()).isEqualTo("deployment");
            }
        }
    }

    @Test
    @DisplayName("Coverage of a pending request follows a replacement configuration")
    void whenAConfigurationReplacesAPendingFinderThenItsVoteAndCoverageApply() throws Exception {
        try (val broker = new TestAttributeBroker();
                val executor = newVirtualThreadPerTaskExecutor();
                val components = PolicyDecisionPointBuilder.withoutDefaults().withAttributeBroker(broker).build()) {
            broker.register(ATTRIBUTE);
            val source = components.pdpVoterSource();
            source.loadConfiguration(configuration(TENANT, PENDING));
            val result = executor.submit(() -> components.pdp().decideOnceWithCoverage(REQUEST, TENANT));
            try {
                awaitFinder(broker);
                source.loadConfiguration(configuration(TENANT, DENY));
                assertThat(result.get(3, SECONDS).vote().authorizationDecision()).isEqualTo(AuthorizationDecision.DENY);
                assertThat(broker.openSubscriptions()).isEmpty();
            } finally {
                broker.publishByName(ATTRIBUTE, of(true));
                result.cancel(true);
            }
        }
    }

    @ValueSource(booleans = { false, true })
    @ParameterizedTest(name = "Retired timestamp source fails: {0}")
    @DisplayName("A replacement activated while the old vote is timestamped wins admission")
    void whenConfigurationChangesBeforeAdmissionThenOnlyTheReplacementIsObserved(boolean failed) {
        val beforeAdmission = new AtomicReference<Runnable>();
        val reports         = new CopyOnWriteArrayList<AuthorizationDecision>();
        try (val components = PolicyDecisionPointBuilder.withoutDefaults()
                .withTimestampSource(admissionClock(beforeAdmission))
                .withDecisionInterceptor((trace, time, id, sub) -> reports.add(trace.authorizationDecision()))
                .build()) {
            val source = components.pdpVoterSource();
            source.loadConfiguration(configuration(TENANT, DENY));
            beforeAdmission.set(() -> {
                source.loadConfiguration(configuration(TENANT, PERMIT_LEDGER));
                if (failed) {
                    throw new IllegalStateException("Retired clock failed");
                }
            });

            val decision = components.pdp().decideOnce(REQUEST, TENANT);
            assertThat(decision.decision()).isEqualTo(Decision.PERMIT);
            assertThat(reports).containsExactly(decision);
        }
    }

    @Test
    @DisplayName("Activation during synchronous finder setup preserves one subscription lifetime")
    void whenFinderReturnsAfterActivationThenItsLateVoteIsDiscarded() throws Exception {
        val lifecycle = new Lifetime();
        val reports   = new CopyOnWriteArrayList<String>();
        try (val broker = new OpeningBroker();
                val components = PolicyDecisionPointBuilder.withoutDefaults().withAttributeBroker(broker)
                        .withSubscriptionLifecycleListener(lifecycle)
                        .withDecisionInterceptor((trace, time, id, sub) -> reports.add(id)).build();
                val executor = newVirtualThreadPerTaskExecutor()) {
            val source = components.pdpVoterSource();
            source.loadConfiguration(configuration(TENANT, PENDING));
            val decision = executor.submit(() -> components.pdp().decideOnce(REQUEST, TENANT));
            try {
                broker.opening.get(3, SECONDS);
                source.loadConfiguration(configuration(TENANT, """
                        policy "replacement" deny <access.closed>;
                        """));
                broker.delegate.publishByName(ATTRIBUTE, Value.TRUE);
                broker.release.complete(null);
                assertThat(decision.get(3, SECONDS)).isEqualTo(AuthorizationDecision.DENY);
                assertThat(lifecycle.opened).containsExactly(REQUEST);
                assertThat(lifecycle.tenants).containsExactly(TENANT);
                assertThat(lifecycle.ids).hasSize(1).isEqualTo(reports).isEqualTo(lifecycle.closed);
            } finally {
                broker.release.complete(null);
                decision.cancel(true);
            }
            assertThat(broker.delegate.openSubscriptions()).isEmpty();
        }
    }

    @Test
    @DisplayName("An interrupt racing a replacement cannot report the withdrawn vote to an interceptor")
    void whenInterruptionRetiresTheVoterBeforeAdmissionThenTheCallerOnlySeesCancellation() throws Exception {
        val beforeAdmission = new AtomicReference<Runnable>();
        val observed        = new CopyOnWriteArrayList<AuthorizationDecision>();
        try (val broker = new TestAttributeBroker();
                val components = PolicyDecisionPointBuilder.withoutDefaults().withAttributeBroker(broker)
                        .withTimestampSource(admissionClock(beforeAdmission))
                        .withDecisionInterceptor((trace, time, id, sub) -> observed.add(trace.authorizationDecision()))
                        .build()) {
            broker.register(ATTRIBUTE);
            val source = components.pdpVoterSource();
            source.loadConfiguration(configuration(TENANT, PENDING));
            val result = new FutureTask<>(() -> components.pdp().decideOnce(REQUEST, TENANT));
            val caller = Thread.startVirtualThread(result);
            try {
                awaitFinder(broker);
                beforeAdmission.set(() -> source.loadConfiguration(configuration(TENANT, DENY)));
                caller.interrupt();
                assertThat(result.get(3, SECONDS)).isEqualTo(AuthorizationDecision.INDETERMINATE);
                assertThat(observed).isEmpty();
            } finally {
                caller.interrupt();
            }
        }
    }

    @Test
    @DisplayName("Interrupting a pending request releases the broker and preserves the interrupt flag")
    void whenCallerIsInterruptedThenDecisionIsIndeterminateAndBrokerIsReleased() throws Exception {
        try (val broker = new TestAttributeBroker();
                val components = PolicyDecisionPointBuilder.withoutDefaults().withAttributeBroker(broker).build()) {
            broker.register(ATTRIBUTE);
            components.pdpVoterSource().loadConfiguration(configuration(TENANT, PENDING));
            val result = new FutureTask<>(() -> {
                           val decision = components.pdp().decideOnce(REQUEST, TENANT);
                           assertThat(Thread.currentThread().isInterrupted()).isTrue();
                           return decision;
                       });
            val caller = Thread.startVirtualThread(result);
            try {
                awaitFinder(broker);
                caller.interrupt();
                assertThat(result.get(3, SECONDS)).isEqualTo(AuthorizationDecision.INDETERMINATE);
                assertThat(broker.openSubscriptions()).isEmpty();
            } finally {
                caller.interrupt();
            }
        }
    }

    @Test
    @DisplayName("A nested decision from a finder detects a dependency on its own enrollment")
    void whenFinderRequestsItsOwnPendingAttributeThenNestedDecisionFailsClosed() throws Exception {
        val finder = new NestedFinder();
        try (val components = PolicyDecisionPointBuilder.withoutDefaults().withPolicyInformationPoint(finder).build();
                val executor = newVirtualThreadPerTaskExecutor()) {
            val source = components.pdpVoterSource();
            source.loadConfiguration(configuration(TENANT, PENDING));
            finder.decide.set(() -> components.pdp().decideOnce(REQUEST, TENANT));
            val outer = executor.submit(() -> components.pdp().decideOnce(REQUEST, TENANT));
            try {
                assertThat(outer.get(3, SECONDS)).isEqualTo(AuthorizationDecision.PERMIT);
                assertThat(finder.nested.get()).isEqualTo(AuthorizationDecision.INDETERMINATE);
            } finally {
                source.removeConfigurationForPdp(TENANT);
                outer.cancel(true);
            }
        }
    }

    @PolicyInformationPoint(name = "access")
    public static class NestedFinder {
        private final AtomicReference<Supplier<AuthorizationDecision>> decide = new AtomicReference<>();
        private final AtomicReference<AuthorizationDecision>           nested = new AtomicReference<>();

        @EnvironmentAttribute(name = "approved")
        public Stream<Value> approved() {
            nested.set(decide.get().get());
            return just(Value.TRUE);
        }
    }

    @Test
    @DisplayName("A nested request can reenter the lock held by its outer finder")
    void whenNestedFindersShareALockThenBothCanPermit() throws Exception {
        val finder = new LockedFinder();
        try (val components = PolicyDecisionPointBuilder.withoutDefaults().withPolicyInformationPoint(finder).build();
                val executor = newVirtualThreadPerTaskExecutor()) {
            val source = components.pdpVoterSource();
            source.loadConfiguration(configuration(TENANT, """
                    policy "outer access" permit <locks.outer>;
                    """));
            source.loadConfiguration(configuration("inner", """
                    policy "inner access" permit <locks.inner>;
                    """));
            finder.decide.set(() -> components.pdp().decideOnce(REQUEST, "inner"));
            val decision = executor.submit(() -> components.pdp().decideOnce(REQUEST, TENANT));
            try {
                assertThat(decision.get(3, SECONDS)).isEqualTo(AuthorizationDecision.PERMIT);
            } finally {
                source.removeConfigurationForPdp("inner");
                decision.cancel(true);
            }
        }
    }

    @PolicyInformationPoint(name = "locks")
    public static class LockedFinder {
        private final ReentrantLock                                    lock   = new ReentrantLock();
        private final AtomicReference<Supplier<AuthorizationDecision>> decide = new AtomicReference<>();

        @EnvironmentAttribute(name = "outer")
        public Stream<Value> outer() {
            lock.lock();
            try {
                return just(of(decide.get().get().decision() == Decision.PERMIT));
            } finally {
                lock.unlock();
            }
        }

        @EnvironmentAttribute(name = "inner")
        public Stream<Value> inner() {
            lock.lock();
            try {
                return just(Value.TRUE);
            } finally {
                lock.unlock();
            }
        }
    }

    private static final class Lifetime implements SubscriptionLifecycleListener {
        private final List<AuthorizationSubscription> opened  = new CopyOnWriteArrayList<>();
        private final List<String>                    tenants = new CopyOnWriteArrayList<>();
        private final List<String>                    ids     = new CopyOnWriteArrayList<>();
        private final List<String>                    closed  = new CopyOnWriteArrayList<>();

        @Override
        public void onSubscribe(String id, AuthorizationSubscription subscription, String pdpId) {
            opened.add(subscription);
            tenants.add(pdpId);
            ids.add(id);
        }

        @Override
        public void onUnsubscribe(String id) {
            closed.add(id);
        }
    }

    private static final class OpeningBroker implements AttributeBroker {
        private final TestAttributeBroker     delegate = new TestAttributeBroker();
        private final CompletableFuture<Void> opening  = new CompletableFuture<>();
        private final CompletableFuture<Void> release  = new CompletableFuture<>();

        private OpeningBroker() {
            delegate.register(ATTRIBUTE);
            delegate.register("access.closed", Value.TRUE);
        }

        @Override
        public Subscription open(String id, Set<SubscriptionKey> dependencies,
                Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate) {
            val handle = delegate.open(id, dependencies, onUpdate);
            if (dependencies.stream().anyMatch(key -> ATTRIBUTE.equals(key.invocation().attributeName()))) {
                opening.complete(null);
                release.orTimeout(8, SECONDS).join();
            }
            return handle;
        }

        @Override
        public void close() {
            release.complete(null);
            delegate.close();
        }
    }

    private enum Transition {
        REMOVE,
        CLOSE,
        OTHER_TENANT,
        REJECT
    }

    @EnumSource(Transition.class)
    @ParameterizedTest(name = "{0}")
    @DisplayName("Only withdrawal or replacement of the served tenant ends the wait for its finder")
    void whenConfigurationChangesThenOnlyTheServedTenantMatters(Transition transition) throws Exception {
        try (val broker = new TestAttributeBroker();
                val components = PolicyDecisionPointBuilder.withoutDefaults().withAttributeBroker(broker).build();
                val executor = newVirtualThreadPerTaskExecutor()) {
            broker.register(ATTRIBUTE);
            val source = components.pdpVoterSource();
            source.loadConfiguration(configuration(TENANT, PENDING));
            val decision = executor.submit(() -> components.pdp().decideOnce(REQUEST, TENANT));
            try {
                awaitFinder(broker);
                switch (transition) {
                case REMOVE       -> source.removeConfigurationForPdp(TENANT);
                case CLOSE        -> source.close();
                case OTHER_TENANT -> source.loadConfiguration(configuration("another-archive", DENY));
                case REJECT       -> source.loadConfiguration(configuration(TENANT, "invalid policy"));
                }
                if (transition == Transition.OTHER_TENANT || transition == Transition.REJECT) {
                    broker.publishByName(ATTRIBUTE, Value.TRUE);
                    assertThat(decision.get(3, SECONDS)).isEqualTo(AuthorizationDecision.PERMIT);
                } else {
                    assertThat(decision.get(3, SECONDS)).isEqualTo(AuthorizationDecision.INDETERMINATE);
                }
            } finally {
                decision.cancel(true);
            }
        }
    }

    private static InstantSource admissionClock(AtomicReference<Runnable> beforeAdmission) {
        return () -> {
            val replace = beforeAdmission.getAndSet(null);
            if (replace != null) {
                replace.run();
            }
            return Instant.EPOCH;
        };
    }

    private static void awaitFinder(TestAttributeBroker broker) {
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(broker.subscribedKeys())
                .extracting(key -> key.invocation().attributeName()).containsExactly(ATTRIBUTE));
    }

    private static PDPConfiguration configuration(String tenant, String policy) {
        // Deployments can reuse the same external configuration ID.
        return new PDPConfiguration(tenant, "deployment", CombiningAlgorithm.DEFAULT, List.of(policy),
                new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
    }
}
