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

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.stream.QueueStream;
import io.sapl.api.stream.Stream;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import lombok.val;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.privateLookupIn;
import static io.sapl.api.test.pdp.PdpTestHelper.configuration;
import static io.sapl.api.test.pdp.PdpTestHelper.subscription;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Timeout(20)
@DisplayName("Two references to a single PIP publication in one round")
class SharedPipObservationTests {
    private static final Duration LIMIT            = Duration.ofSeconds(5);
    private static final String   HEAD_AND_LIVE    = """
            policy "Compare observation labels"
            deny
              true;
            advice [|<sample.value>, <sample.value>, <sample.tick>]
            """;
    private static final String   FRESH_AND_REUSED = """
            policy "Compare observation labels"
            deny
              <sample.value[{fresh: true}]> >= 0;
            advice [<sample.value[{fresh: true}]>, <sample.value>, <sample.tick>]
            """;
    private static final Value    FIRST            = Value.of(5);
    private static final Value    SECOND           = Value.of(9);
    private static final Value    TICK             = Value.of("ready");

    private enum Schedule {
        BEFORE,
        BETWEEN,
        AFTER
    }

    @EnumSource(Schedule.class)
    @ParameterizedTest(name = "publication {0} the initial common capture")
    @DisplayName("a newly observed head and the live reference share their first observation")
    void whenOneSourceServesTheInitialHeadAndLiveReferenceThenBothReadOnePublication(Schedule schedule)
            throws Exception {
        exercise(HEAD_AND_LIVE, schedule, true);
    }

    @EnumSource(Schedule.class)
    @ParameterizedTest(name = "publication {0} the common capture of live references")
    @DisplayName("two live references served by one activated PIP share one observation")
    void whenTwoLiveReferencesReuseOneSourceThenBothReadOnePublication(Schedule schedule) throws Exception {
        exercise(FRESH_AND_REUSED, schedule, false);
    }

    private static void exercise(String policy, Schedule schedule, boolean usesHead) throws Exception {
        val pip       = new LabelPip();
        val audit     = new RecordingBroker(pip);
        val decisions = new ArrayList<AuthorizationDecision>();
        try (val components = PolicyDecisionPointBuilder.withoutDefaults().withAttributeBroker(audit).build()) {
            components.pdpVoterSource().loadConfiguration(configuration(policy));
            try (val stream = components.pdp().decide(subscription("marker", "read", "label"))) {
                val consumer = audit.opened.get(5, TimeUnit.SECONDS);
                try {
                    if (!usesHead) {
                        pip.value.put(FIRST);
                        pip.tickOpened.get(5, TimeUnit.SECONDS);
                    }
                    val mailbox = installCheckpoint(consumer, audit, pip);
                    if (usesHead) {
                        pip.value.put(FIRST);
                        awaitValue(mailbox, FIRST);
                    }
                    if (schedule == Schedule.BEFORE) {
                        pip.value.put(SECOND);
                        awaitValue(mailbox, SECOND);
                    }
                    // The independent tick pump drives capture while the value pump remains able to publish.
                    audit.armed.set(true);
                    pip.tick.put(TICK);
                    audit.betweenReads.get(5, TimeUnit.SECONDS);
                    if (schedule == Schedule.BETWEEN) {
                        pip.value.put(SECOND);
                        awaitValue(mailbox, SECOND);
                    }
                    audit.resumeCapture.complete(null);
                    // Hold this callback until its first decision passes the public latest-value slot.
                    val firstInputs   = audit.firstComplete.get(5, TimeUnit.SECONDS);
                    val firstDecision = stream.awaitNext(LIMIT);
                    decisions.add(firstDecision);
                    val aliases = firstInputs.entrySet().stream().filter(entry -> isValue(entry.getKey()))
                            .map(Map.Entry::getValue).toList();
                    assertThat(aliases).hasSize(2);
                    assertThat(aliases.getFirst())
                            .as("The first common capture produced public advice %s", firstDecision.advice())
                            .isSameAs(aliases.getLast());
                    audit.resumeEvaluation.complete(null);
                    if (schedule == Schedule.AFTER) {
                        pip.value.put(SECOND);
                        awaitValue(mailbox, SECOND);
                    }
                    if (schedule != Schedule.BEFORE) {
                        decisions.add(stream.awaitNext(LIMIT));
                    }
                    collectRemaining(stream, decisions);
                    assertObservations(audit.rounds, decisions, schedule, usesHead);
                } finally {
                    audit.resumeCapture.complete(null);
                    audit.resumeEvaluation.complete(null);
                }
            }
        }
    }

    @Test
    @DisplayName("a first publication partway through a partial capture reaches both references together")
    void whenASharedPendingMailboxFirstDeliversDuringPartialCaptureThenBothReferencesAreAbsentOrPresent()
            throws Exception {
        val policy = """
                policy "Report first observations together"
                deny <sample.tick>;
                advice [|<sample.value>, <sample.value>]
                """;
        val pip    = new LabelPip();
        val audit  = new RecordingBroker(pip);
        audit.resumeEvaluation.complete(null);
        try (val components = PolicyDecisionPointBuilder.withoutDefaults().withAttributeBroker(audit).build()) {
            components.pdpVoterSource().loadConfiguration(configuration(policy));
            try (val stream = components.pdp().decide(subscription("marker", "read", "label"))) {
                val consumer = audit.opened.get(5, TimeUnit.SECONDS);
                try {
                    pip.tick.put(Value.TRUE);
                    await().atMost(LIMIT).until(() -> pip.valueOpens.get() == 1);
                    val mailbox = installCheckpoint(consumer, audit, pip);
                    assertThat(mailbox.snapshot()).isEmpty();
                    val before = audit.rounds.size();
                    audit.armed.set(true);
                    pip.tick.put(Value.TRUE);
                    audit.betweenReads.get(5, TimeUnit.SECONDS);
                    pip.value.put(FIRST);
                    awaitValue(mailbox, FIRST);
                    audit.resumeCapture.complete(null);
                    val publicResult = stream.awaitNext(LIMIT);
                    assertThat(publicResult).satisfies(decision -> {
                        assertThat(decision.decision()).isEqualTo(Decision.DENY);
                        assertThat(decision.advice()).containsExactly(Value.ofArray(FIRST, FIRST));
                    });
                    val observed = audit.rounds.subList(before, audit.rounds.size()).stream()
                            .map(round -> round.keySet().stream().filter(SharedPipObservationTests::isValue)
                                    .map(SharedPipObservationTests::shortName).sorted().toList())
                            .toList();
                    assertThat(observed).as("Shared mailbox observations across the partial and completed captures")
                            .containsExactly(List.of(), List.of("sample.value", "sample.value head"));
                } finally {
                    audit.resumeCapture.complete(null);
                }
            }
        }
    }

    private static ActiveInvocation installCheckpoint(PolicyInformationPointAttributeBroker.BrokerSubscription consumer,
            RecordingBroker audit, LabelPip pip) throws ReflectiveOperationException {
        val lockField = privateLookupIn(PolicyInformationPointAttributeBroker.class, lookup())
                .findVarHandle(PolicyInformationPointAttributeBroker.class, "lock", ReentrantLock.class);
        val lock      = (ReentrantLock) lockField.get(audit.broker);
        lock.lock();
        try {
            val aliases = consumer.routingTable.entrySet().stream().filter(entry -> isValue(entry.getKey())).toList();
            assertThat(aliases).hasSize(2);
            assertThat(aliases.getFirst().getValue()).isSameAs(aliases.getLast().getValue());
            assertThat(pip.valueOpens).hasValue(1);
            consumer.routingTable = new ReadCheckpoint(consumer.routingTable, audit);
            return aliases.getFirst().getValue();
        } finally {
            lock.unlock();
        }
    }

    private static void awaitValue(ActiveInvocation mailbox, Value expected) {
        await().atMost(LIMIT)
                .until(() -> mailbox.snapshot().map(snapshot -> expected.equals(snapshot.value())).orElse(false));
    }

    private static boolean isValue(SubscriptionKey key) {
        return "sample.value".equals(key.invocation().attributeName());
    }

    private static void collectRemaining(Stream<AuthorizationDecision> stream, List<AuthorizationDecision> decisions)
            throws InterruptedException {
        try {
            while (true) {
                val next = stream.awaitNext(Duration.ofMillis(200));
                if (next == null) {
                    return;
                }
                decisions.add(next);
            }
        } catch (TimeoutException quiet) {
            // No further source publications are scheduled.
        }
    }

    private static void assertObservations(List<Map<SubscriptionKey, AttributeSnapshot>> rounds,
            List<AuthorizationDecision> decisions, Schedule schedule, boolean usesHead) {
        val complete         = rounds.stream()
                .filter(round -> round.keySet().stream().anyMatch(SharedPipObservationTests::isTick)).toList();
        val initial          = complete.getFirst();
        val firstKey         = initial.keySet().stream().filter(SharedPipObservationTests::isValue)
                .filter(key -> usesHead ? key.head() : key.invocation().fresh()).findFirst().orElseThrow();
        val liveKey          = initial.keySet().stream().filter(SharedPipObservationTests::isValue)
                .filter(key -> !key.equals(firstKey)).findFirst().orElseThrow();
        val firstObservation = initial.get(firstKey);
        val observedAdvice   = new ArrayList<ArrayValue>();
        for (val inputs : complete) {
            val first  = usesHead ? firstObservation : inputs.get(firstKey);
            val live   = inputs.get(liveKey);
            val advice = Value.ofArray(first.value(), live.value(), TICK);
            if (observedAdvice.isEmpty() || !observedAdvice.getLast().equals(advice)) {
                observedAdvice.add(advice);
            }
        }
        assertThat(decisions).allSatisfy(decision -> {
            assertThat(decision.decision()).isEqualTo(Decision.DENY);
            assertThat(decision.advice()).hasSize(1);
        }).extracting(decision -> decision.advice().getFirst()).containsExactlyElementsOf(observedAdvice);
        val expectedFirst     = schedule == Schedule.BEFORE ? SECOND : FIRST;
        val expectedFinalHead = usesHead ? expectedFirst : SECOND;
        assertThat(firstObservation.value()).isEqualTo(expectedFirst);
        assertThat(decisions.getLast().advice()).containsExactly(Value.ofArray(expectedFinalHead, SECOND, TICK));
    }

    private static boolean isTick(SubscriptionKey key) {
        return "sample.tick".equals(key.invocation().attributeName());
    }

    private static String shortName(SubscriptionKey key) {
        return key.invocation().attributeName() + (key.head() ? " head" : "")
                + (key.invocation().fresh() ? " fresh" : "");
    }

    private static final class ReadCheckpoint extends AbstractMap<SubscriptionKey, ActiveInvocation> {
        private final Map<SubscriptionKey, ActiveInvocation> routes;
        private final RecordingBroker                        audit;
        private final ActiveInvocation                       tick;
        private int                                          aliasesRead;

        ReadCheckpoint(Map<SubscriptionKey, ActiveInvocation> routes, RecordingBroker audit) {
            this.routes = routes;
            this.audit  = audit;
            this.tick   = routes.entrySet().stream().filter(entry -> isTick(entry.getKey())).findFirst().orElseThrow()
                    .getValue();
        }

        @Override
        public Set<Entry<SubscriptionKey, ActiveInvocation>> entrySet() {
            return routes.entrySet();
        }

        @Override
        public ActiveInvocation put(SubscriptionKey key, ActiveInvocation active) {
            return routes.put(key, active);
        }

        @Override
        public ActiveInvocation get(Object key) {
            if (audit.armed.get() && tick.snapshot().isPresent() && key instanceof SubscriptionKey alias
                    && isValue(alias) && capturingInputs()) {
                aliasesRead++;
                if (aliasesRead == 2) {
                    audit.armed.set(false);
                    audit.betweenReads.complete(null);
                    audit.resumeCapture.orTimeout(5, TimeUnit.SECONDS).join();
                }
            }
            return routes.get(key);
        }

        private static boolean capturingInputs() {
            // Readiness also consults the routing table. Only the second alias lookup during capture is paused.
            return StackWalker.getInstance()
                    .walk(frames -> frames.anyMatch(frame -> "currentSnapshots".equals(frame.getMethodName())));
        }
    }

    private static final class RecordingBroker implements AttributeBroker {
        private final PolicyInformationPointAttributeBroker                                       broker;
        private final AtomicBoolean                                                               armed            = new AtomicBoolean();
        private final CompletableFuture<PolicyInformationPointAttributeBroker.BrokerSubscription> opened           = new CompletableFuture<>();
        private final CompletableFuture<Void>                                                     betweenReads     = new CompletableFuture<>();
        private final CompletableFuture<Void>                                                     resumeCapture    = new CompletableFuture<>();
        private final CompletableFuture<Map<SubscriptionKey, AttributeSnapshot>>                  firstComplete    = new CompletableFuture<>();
        private final CompletableFuture<Void>                                                     resumeEvaluation = new CompletableFuture<>();
        private final List<Map<SubscriptionKey, AttributeSnapshot>>                               rounds           = new CopyOnWriteArrayList<>();

        RecordingBroker(LabelPip pip) {
            this.broker = new PolicyInformationPointAttributeBroker(Duration.ZERO, null,
                    InstantSource.fixed(Instant.EPOCH));
            broker.load(pip);
        }

        @Override
        public Subscription open(String id, Set<SubscriptionKey> initial,
                Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> evaluator) {
            val subscription = (PolicyInformationPointAttributeBroker.BrokerSubscription) broker.open(id, initial,
                    inputs -> {
                        rounds.add(inputs);
                        val next = evaluator.apply(inputs);
                        if (inputs.keySet().stream().anyMatch(SharedPipObservationTests::isTick)
                                && firstComplete.complete(inputs)) {
                            resumeEvaluation.orTimeout(5, TimeUnit.SECONDS).join();
                        }
                        return next;
                    });
            opened.complete(subscription);
            return subscription;
        }

        @Override
        public void close() {
            broker.close();
        }
    }

    @PolicyInformationPoint(name = "sample")
    public static final class LabelPip {
        private final QueueStream<Value>      value      = new QueueStream<>();
        private final QueueStream<Value>      tick       = new QueueStream<>();
        private final AtomicInteger           valueOpens = new AtomicInteger();
        private final CompletableFuture<Void> tickOpened = new CompletableFuture<>();

        @EnvironmentAttribute
        public Stream<Value> value() {
            valueOpens.incrementAndGet();
            return value;
        }

        @EnvironmentAttribute
        public Stream<Value> tick() {
            tickOpened.complete(null);
            return tick;
        }
    }
}
