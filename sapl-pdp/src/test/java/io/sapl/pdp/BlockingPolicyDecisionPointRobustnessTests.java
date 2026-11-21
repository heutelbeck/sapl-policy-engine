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

import static io.sapl.api.test.pdp.PdpTestHelper.configuration;
import static io.sapl.api.test.pdp.PdpTestHelper.subscription;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.IdentifiableAuthorizationSubscription;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.SubscriptionLifecycleListener;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode;
import io.sapl.ast.Outcome;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.attributes.broker.repository.AttributeAssignment.BoundValue;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.attributes.broker.repository.RepositoryKey;
import io.sapl.compiler.document.PureVoter;
import io.sapl.compiler.document.TracedVote;
import io.sapl.compiler.pdp.CompiledPdp;
import io.sapl.compiler.pdp.PdpVoterMetadata;
import io.sapl.pdp.configuration.PdpUpdateEvent;
import io.sapl.pdp.configuration.PdpVoterSource;
import io.sapl.pdp.plugins.PluginsBundle;

import lombok.val;

@ExtendWith(MockitoExtension.class)
@DisplayName("BlockingPolicyDecisionPoint robustness")
class BlockingPolicyDecisionPointRobustnessTests {

    private static final AuthorizationSubscription SUB = subscription("subject", "action", "resource");

    @Mock
    private FunctionBroker functionBroker;

    @Nested
    @DisplayName("listener isolation (L19)")
    class ListenerIsolationTests {

        @Test
        @DisplayName("a listener that throws a runtime exception is isolated and the rest still run")
        void whenListenerThrowsRuntimeExceptionThenIsolatedAndOthersStillRun() {
            val secondRan = new AtomicBoolean(false);
            val throwing  = (SubscriptionLifecycleListener) failingOnSubscribe(new IllegalStateException("boom"));
            val recording = recordingOnSubscribe(secondRan);

            BlockingPolicyDecisionPoint.notifyOnSubscribe(List.of(throwing, recording), "id", SUB, "pdp");

            assertThat(secondRan).isTrue();
        }

        @Test
        @DisplayName("a fatal VirtualMachineError from a listener is never swallowed")
        void whenListenerThrowsVirtualMachineErrorThenPropagates() {
            val fatal     = (SubscriptionLifecycleListener) failingOnSubscribe(new StackOverflowError());
            val listeners = List.of(fatal);

            assertThatThrownBy(() -> BlockingPolicyDecisionPoint.notifyOnSubscribe(listeners, "id", SUB, "pdp"))
                    .isInstanceOf(StackOverflowError.class);
        }

        @Test
        @DisplayName("a fatal VirtualMachineError on unsubscribe is never swallowed")
        void whenUnsubscribeListenerThrowsVirtualMachineErrorThenPropagates() {
            val fatal     = new SubscriptionLifecycleListener() {
                              @Override
                              public void onSubscribe(String subscriptionId, AuthorizationSubscription sub,
                                      String pdpId) {
                                  // no-op
                              }

                              @Override
                              public void onUnsubscribe(String subscriptionId) {
                                  throw new OutOfMemoryError();
                              }
                          };
            val listeners = List.<SubscriptionLifecycleListener>of(fatal);

            assertThatThrownBy(() -> BlockingPolicyDecisionPoint.notifyOnUnsubscribe(listeners, "id"))
                    .isInstanceOf(OutOfMemoryError.class);
        }

        private SubscriptionLifecycleListener failingOnSubscribe(Throwable toThrow) {
            return new SubscriptionLifecycleListener() {
                @Override
                public void onSubscribe(String subscriptionId, AuthorizationSubscription sub, String pdpId) {
                    sneakyThrow(toThrow);
                }

                @Override
                public void onUnsubscribe(String subscriptionId) {
                    // no-op
                }
            };
        }

        private SubscriptionLifecycleListener recordingOnSubscribe(AtomicBoolean flag) {
            return new SubscriptionLifecycleListener() {
                @Override
                public void onSubscribe(String subscriptionId, AuthorizationSubscription sub, String pdpId) {
                    flag.set(true);
                }

                @Override
                public void onUnsubscribe(String subscriptionId) {
                    // no-op
                }
            };
        }

        @SuppressWarnings("unchecked")
        private <E extends Throwable> void sneakyThrow(Throwable t) throws E {
            throw (E) t;
        }
    }

    @Nested
    @DisplayName("multi-subscription lifecycle notification (L21)")
    class MultiSubscriptionLifecycleTests {

        private static final CombiningAlgorithm DENY_UNLESS_PERMIT = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT,
                DefaultDecision.DENY, ErrorHandling.PROPAGATE);

        @Test
        @DisplayName("a pending item withholds the whole round from the trace collector")
        void whenTheLastElementIsPendingThenItemTracesStartWithTheFirstCompleteRound() {
            val                                                           items        = List.of(
                    new IdentifiableAuthorizationSubscription("first", subscription("local", "read", "file")),
                    new IdentifiableAuthorizationSubscription("second", subscription("visitor", "read", "file")));
            val                                                           dependencies = new HashSet<SubscriptionKey>();
            val                                                           reported     = new ArrayList<String>();
            BiConsumer<TracedVote, IdentifiableAuthorizationSubscription> observer     = (trace, item) -> reported
                    .add(item.subscriptionId());
            val                                                           clock        = InstantSource
                    .fixed(Instant.EPOCH);
            try (val components = PolicyDecisionPointBuilder.withoutDefaults().build()) {
                val source = components.pdpVoterSource();
                source.loadConfiguration(configuration(DENY_UNLESS_PERMIT,
                        "policy \"Read access\" permit subject == \"local\" || <approval.granted>;"));
                val pdp        = source.getCurrentConfiguration("default").orElseThrow();
                val functions  = pdp.plugins().functionBroker();
                val incomplete = BlockingPolicyDecisionPoint.evaluateRound(items, pdp, "group", Map.of(), dependencies,
                        functions, clock, observer);
                assertThat(incomplete).isNull();
                assertThat(dependencies).hasSize(1);
                assertThat(reported).isEmpty();

                val snapshot = Map.of(dependencies.iterator().next(), new AttributeSnapshot(Value.TRUE, Instant.EPOCH));
                for (int round = 0; round < 2; round++) {
                    val complete = BlockingPolicyDecisionPoint.evaluateRound(items, pdp, "group", snapshot,
                            new HashSet<>(), functions, clock, observer);
                    assertThat(List.of(complete.getDecisionType("first"), complete.getDecisionType("second")))
                            .containsExactly(Decision.PERMIT, Decision.PERMIT);
                }
                assertThat(reported).containsExactly("first", "second", "first", "second");
            }
        }

        @Test
        @Timeout(15)
        @DisplayName("the client receives whole bundles while the interceptor receives changed elements")
        void whenAdviceOrASharedGrantChangesThenOnlyChangedItemsAreReported() throws Exception {
            val left      = subscription("left", "read", "file");
            val right     = subscription("right", "read", "file");
            val group     = new MultiAuthorizationSubscription().addSubscription("a", left).addSubscription("b", right);
            val granted   = new RepositoryKey(null, "access.granted", List.of(), "default");
            val label     = new RepositoryKey(null, "labels.left", List.of(), "default");
            val callbacks = new CopyOnWriteArrayList<AuthorizationSubscription>();
            val limit     = Duration.ofSeconds(4);
            try (val repository = new InMemoryAttributeRepository();
                    val components = PolicyDecisionPointBuilder.withoutDefaults().withRepository(repository)
                            .withDecisionInterceptor((decision, timestamp, id, sub) -> callbacks.add(sub)).build()) {
                val config = configuration(DENY_UNLESS_PERMIT, """
                        policy "left access" permit subject == "left"; <access.granted>; advice <labels.left>
                        """, """
                        policy "right access" permit subject == "right"; <access.granted>;
                        """);
                components.pdpVoterSource().loadConfiguration(config);
                repository.publish(List.of(new BoundValue(granted, Value.TRUE, null),
                        new BoundValue(label, Value.of("initial"), null)));
                try (val stream = components.pdp().decideAll(group)) {
                    val initial = stream.awaitNext(limit);
                    assertThat(List.of(initial.getDecisionType("a"), initial.getDecisionType("b")))
                            .containsExactly(Decision.PERMIT, Decision.PERMIT);
                    assertThat(callbacks).containsExactlyInAnyOrder(left, right);
                    callbacks.clear();

                    repository.publish(label, Value.of("initial"));
                    repository.publish(label, Value.of("revised"));
                    val revised = stream.awaitNext(limit);
                    assertThat(revised.getDecision("a").advice()).containsExactly(Value.of("revised"));
                    assertThat(revised.getDecision("b")).isEqualTo(initial.getDecision("b"));
                    assertThat(callbacks).containsExactly(left);
                    callbacks.clear();

                    repository.publish(granted, Value.FALSE);
                    val revoked = stream.awaitNext(limit);
                    assertThat(List.of(revoked.getDecisionType("a"), revoked.getDecisionType("b")))
                            .containsExactly(Decision.DENY, Decision.DENY);
                    assertThat(callbacks).containsExactlyInAnyOrder(left, right);
                }
            }
        }

        @Test
        @Timeout(15)
        @DisplayName("recovering from a missing configuration preserves the group's decision history")
        void whenAConfigurationChangesThenTheGroupReportsOnlyNewDecisions() throws Exception {
            val local     = subscription("local", "read", "file");
            val guest     = subscription("guest", "read", "file");
            val group     = new MultiAuthorizationSubscription().addSubscription("local", local)
                    .addSubscription("guest", guest);
            val callbacks = new CopyOnWriteArrayList<AuthorizationSubscription>();
            val limit     = Duration.ofSeconds(4);
            try (val components = PolicyDecisionPointBuilder.withoutDefaults()
                    .withDecisionInterceptor((decision, timestamp, id, sub) -> callbacks.add(sub)).build();
                    val stream = components.pdp().decideAll(group)) {
                val missing = stream.awaitNext(limit);
                assertThat(List.of(missing.getDecisionType("local"), missing.getDecisionType("guest")))
                        .containsExactly(Decision.INDETERMINATE, Decision.INDETERMINATE);
                assertThat(callbacks).containsExactlyInAnyOrder(local, guest);
                callbacks.clear();

                val source = components.pdpVoterSource();
                source.loadConfiguration(configuration(DENY_UNLESS_PERMIT, "policy \"read access\" permit"));
                val available = stream.awaitNext(limit);
                assertThat(List.of(available.getDecisionType("local"), available.getDecisionType("guest")))
                        .containsExactly(Decision.PERMIT, Decision.PERMIT);
                assertThat(callbacks).containsExactlyInAnyOrder(local, guest);
                callbacks.clear();

                source.loadConfiguration(
                        configuration(DENY_UNLESS_PERMIT, "policy \"local access\" permit subject == \"local\";"));
                val restricted = stream.awaitNext(limit);
                assertThat(List.of(restricted.getDecisionType("local"), restricted.getDecisionType("guest")))
                        .containsExactly(Decision.PERMIT, Decision.DENY);
                assertThat(callbacks).containsExactly(guest);
            }
        }

        @Test
        @DisplayName("independent groups with the same item IDs have distinct, correlated observer lifetimes")
        void whenTwoGroupsReuseClientIdsThenEachDecisionMatchesItsElementLifetime() throws Exception {
            val recording = new RecordingListener(4);
            val decisions = new CopyOnWriteArrayList<ObservedSubscription>();
            val group     = new MultiAuthorizationSubscription()
                    .addSubscription("first", subscription("cultist", "summon", "deep_one"))
                    .addSubscription("second", subscription("investigator", "summon", "deep_one"));
            try (val components = PolicyDecisionPointBuilder.withoutDefaults()
                    .withSubscriptionLifecycleListener(recording).withDecisionInterceptor(
                            (trace, timestamp, id, sub) -> decisions.add(new ObservedSubscription(id, sub)))
                    .build()) {
                components.pdpVoterSource().loadConfiguration(configuration(DENY_UNLESS_PERMIT, """
                        policy "permit cultists" permit subject == "cultist";
                        """));
                try (val first = components.pdp().decideAll(group); val second = components.pdp().decideAll(group)) {
                    for (val stream : List.of(first, second)) {
                        val bundle = stream.awaitNext(Duration.ofSeconds(5));
                        assertThat(List.of(bundle.getDecisionType("first"), bundle.getDecisionType("second")))
                                .containsExactly(Decision.PERMIT, Decision.DENY);
                    }
                    assertThat(recording.subscribed).hasSize(4).extracting(ObservedSubscription::id)
                            .doesNotHaveDuplicates();
                    assertThat(decisions).containsExactlyInAnyOrderElementsOf(recording.subscribed);
                }
                assertThat(recording.unsubscribed.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(recording.closedIds).containsExactlyInAnyOrderElementsOf(
                        recording.subscribed.stream().map(ObservedSubscription::id).toList());
            }
        }
    }

    @Nested
    @DisplayName("fail-closed boundary (L20)")
    class FailClosedBoundaryTests {

        @Test
        @DisplayName("decideOnce degrades to INDETERMINATE when configuration lookup throws")
        void whenConfigurationLookupThrowsThenDecideOnceReturnsIndeterminate() {
            val source = mock(PdpVoterSource.class);
            when(source.getPlugins()).thenReturn(new PluginsBundle(functionBroker));
            when(source.getCurrentConfiguration(anyString())).thenThrow(new RuntimeException("boom"));
            val pdp = new BlockingPolicyDecisionPoint(source, mock(AttributeBroker.class),
                    new ThreadLocalRandomIdFactory());

            val decision = pdp.decideOnce(subscription("subject", "action", "resource"));

            assertThat(decision.decision()).isEqualTo(Decision.INDETERMINATE);
        }

        @Test
        @Timeout(10)
        @DisplayName("decide degrades to INDETERMINATE and does not hang when a voter throws")
        void whenVoterThrowsThenDecideEmitsIndeterminateWithoutHanging() throws Exception {
            val pdpId         = "test-pdp";
            val metadata      = new PdpVoterMetadata("t", pdpId, "cfg", null, Outcome.PERMIT_OR_DENY, false);
            val throwingVoter = (PureVoter) ctx -> {
                                  throw new IllegalStateException("voter boom");
                              };
            val compiled      = new CompiledPdp(metadata, throwingVoter, null,
                    new PluginsBundle(mock(FunctionBroker.class)));

            val source = mock(PdpVoterSource.class);
            when(source.getPlugins()).thenReturn(new PluginsBundle(functionBroker));
            doAnswer(invocation -> {
                Consumer<PdpUpdateEvent> listener = invocation.getArgument(1);
                listener.accept(new PdpUpdateEvent.Voter(pdpId, compiled));
                return null;
            }).when(source).subscribeToUpdates(eq(pdpId), any());
            val pdp = new BlockingPolicyDecisionPoint(source, mock(AttributeBroker.class),
                    new ThreadLocalRandomIdFactory());

            try (val stream = pdp.decide(subscription("subject", "action", "resource"), pdpId)) {
                val decision = stream.awaitNext();

                assertThat(decision).isNotNull();
                assertThat(decision.decision()).isEqualTo(Decision.INDETERMINATE);
            }
        }
    }

    @Nested
    @DisplayName("evaluation that overflows the stack")
    class EvaluationOverflowTests {

        // Each variable adds the subject to the one before. The chain has no bracket,
        // compiles, and nests the evaluation once per variable.
        private static final int VARIABLE_CHAIN_LENGTH = 40_000;

        private static final AuthorizationSubscription NUMBER_SUBSCRIPTION = subscription(Value.of(1), Value.of("read"),
                Value.of("tome"), Value.EMPTY_OBJECT);

        private static String variableChainPolicy() {
            val policy = new StringBuilder("policy \"deep\" permit\nvar v0 = subject;\n");
            for (int i = 1; i < VARIABLE_CHAIN_LENGTH; i++) {
                policy.append("var v").append(i).append(" = v").append(i - 1).append(" + subject;\n");
            }
            return policy.append('v').append(VARIABLE_CHAIN_LENGTH - 1).append(" > 0;").toString();
        }

        @Test
        @Timeout(120)
        @DisplayName("decideOnce answers INDETERMINATE when evaluating the policies overflows the stack")
        void whenEvaluationOverflowsThenDecideOnceIsIndeterminate() {
            try (val components = PolicyDecisionPointBuilder.withDefaults().withPolicy(variableChainPolicy()).build()) {
                assertThat(components.pdp().decideOnce(NUMBER_SUBSCRIPTION).decision())
                        .isEqualTo(Decision.INDETERMINATE);
            }
        }

        @Test
        @Timeout(120)
        @DisplayName("a decision stream answers INDETERMINATE for an overflow and stays alive for the next configuration")
        void whenEvaluationOverflowsThenStreamIsIndeterminateAndStaysAlive() throws InterruptedException {
            try (val components = PolicyDecisionPointBuilder.withDefaults().withPolicy(variableChainPolicy()).build();
                    val stream = components.pdp().decide(NUMBER_SUBSCRIPTION)) {
                val overflowed = stream.awaitNext();
                components.pdpVoterSource().loadConfiguration(configuration("policy \"shallow\" permit"));
                val recovered = stream.awaitNext();

                assertThat(List.of(overflowed.decision(), recovered.decision())).containsExactly(Decision.INDETERMINATE,
                        Decision.PERMIT);
            }
        }

        @Test
        @Timeout(120)
        @DisplayName("a multi-subscription stream answers INDETERMINATE for the subscription whose evaluation overflows")
        void whenEvaluationOverflowsThenMultiStreamIsIndeterminateForThatSubscription() throws InterruptedException {
            val multi = new MultiAuthorizationSubscription();
            multi.addSubscription("deep", NUMBER_SUBSCRIPTION);
            try (val components = PolicyDecisionPointBuilder.withDefaults().withPolicy(variableChainPolicy()).build();
                    val stream = components.pdp().decideAll(multi)) {
                val decisions = stream.awaitNext();

                assertThat(decisions.getDecisionType("deep")).isEqualTo(Decision.INDETERMINATE);
            }
        }

        @Test
        @Timeout(120)
        @DisplayName("an evaluation with coverage, as a test run makes it, answers INDETERMINATE when it overflows the stack")
        void whenEvaluationWithCoverageOverflowsThenIndeterminate() {
            try (val components = PolicyDecisionPointBuilder.withDefaults().withPolicy(variableChainPolicy()).build()) {
                assertThat(components.pdp().decideOnceWithCoverage(NUMBER_SUBSCRIPTION).vote().authorizationDecision()
                        .decision()).isEqualTo(Decision.INDETERMINATE);
            }
        }
    }

    private record ObservedSubscription(String id, AuthorizationSubscription subscription) {}

    private static final class RecordingListener implements SubscriptionLifecycleListener {

        private final List<ObservedSubscription> subscribed = new CopyOnWriteArrayList<>();
        private final List<String>               closedIds  = new CopyOnWriteArrayList<>();
        private final CountDownLatch             unsubscribed;

        private RecordingListener(int expectedUnsubscribes) {
            this.unsubscribed = new CountDownLatch(expectedUnsubscribes);
        }

        @Override
        public void onSubscribe(String subscriptionId, AuthorizationSubscription sub, String pdpId) {
            subscribed.add(new ObservedSubscription(subscriptionId, sub));
        }

        @Override
        public void onUnsubscribe(String subscriptionId) {
            closedIds.add(subscriptionId);
            unsubscribed.countDown();
        }
    }

    @Nested
    @DisplayName("configuration-swap generation guard (run2-069/070)")
    class GenerationGuardTests {

        @SuppressWarnings("unchecked")
        private static void latchIfCurrent(ReentrantLock lock, AtomicLong generation, long myGen, Consumer<Object> sink,
                Object value) throws Exception {
            final Method method = BlockingPolicyDecisionPoint.class.getDeclaredMethod("latchIfCurrent",
                    ReentrantLock.class, AtomicLong.class, long.class, Consumer.class, Object.class);
            method.setAccessible(true);
            method.invoke(null, lock, generation, myGen, (Consumer<Object>) sink, value);
        }

        @Test
        @DisplayName("a value from the current configuration generation is latched")
        void whenGenerationIsCurrentThenValueLatched() throws Exception {
            var received = new ArrayList<>();

            latchIfCurrent(new ReentrantLock(), new AtomicLong(3), 3L, received::add, "decision");

            assertThat(received).containsExactly("decision");
        }

        @Test
        @DisplayName("a value from a superseded generation is dropped, so a stale decision never latches")
        void whenGenerationSupersededThenValueDropped() throws Exception {
            var received = new ArrayList<>();

            latchIfCurrent(new ReentrantLock(), new AtomicLong(5), 4L, received::add, "stale-decision");

            assertThat(received).isEmpty();
        }
    }
}
