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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.SubscriptionLifecycleListener;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode;
import io.sapl.ast.Outcome;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.compiler.document.PureVoter;
import io.sapl.compiler.pdp.CompiledPdp;
import io.sapl.compiler.pdp.PdpVoterMetadata;
import io.sapl.pdp.configuration.PdpUpdateEvent;
import io.sapl.pdp.configuration.PdpVoterSource;
import io.sapl.pdp.plugins.PluginsBundle;
import lombok.val;

@DisplayName("BlockingPolicyDecisionPoint robustness")
class BlockingPolicyDecisionPointRobustnessTests {

    private static final AuthorizationSubscription SUB = subscription("subject", "action", "resource");

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
        @DisplayName("decideAll fires one onSubscribe and one onUnsubscribe per contained subscription")
        void whenDecideAllThenListenerNotifiedPerContainedSubscription() throws Exception {
            val recording      = new RecordingListener(2);
            val components     = PolicyDecisionPointBuilder.withoutDefaults()
                    .withSubscriptionLifecycleListener(recording).build();
            val pdpVoterSource = components.pdpVoterSource();
            val pdp            = new BlockingPolicyDecisionPoint(pdpVoterSource, components.attributeBroker(),
                    new ThreadLocalRandomIdFactory());
            pdpVoterSource.loadConfiguration(configuration(DENY_UNLESS_PERMIT, """
                    policy "permit cultists" permit subject == "cultist";
                    """), false);

            val multi = new MultiAuthorizationSubscription();
            multi.addSubscription("first", subscription("cultist", "summon", "deep_one"));
            multi.addSubscription("second", subscription("investigator", "summon", "deep_one"));

            try (val stream = pdp.decideAll(multi)) {
                assertThat(stream.awaitNext()).isNotNull();
                assertThat(recording.subscribed).hasSize(2);
            }

            assertThat(recording.unsubscribed.await(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Nested
    @DisplayName("fail-closed boundary (L20)")
    class FailClosedBoundaryTests {

        @Test
        @DisplayName("decideOnce degrades to INDETERMINATE when configuration lookup throws")
        void whenConfigurationLookupThrowsThenDecideOnceReturnsIndeterminate() {
            val source = mock(PdpVoterSource.class);
            when(source.getPlugins()).thenReturn(new PluginsBundle(mock(FunctionBroker.class)));
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
            when(source.getPlugins()).thenReturn(new PluginsBundle(mock(FunctionBroker.class)));
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

    private static final class RecordingListener implements SubscriptionLifecycleListener {

        private final List<String>   subscribed = new CopyOnWriteArrayList<>();
        private final CountDownLatch unsubscribed;

        private RecordingListener(int expectedUnsubscribes) {
            this.unsubscribed = new CountDownLatch(expectedUnsubscribes);
        }

        @Override
        public void onSubscribe(String subscriptionId, AuthorizationSubscription sub, String pdpId) {
            subscribed.add(subscriptionId);
        }

        @Override
        public void onUnsubscribe(String subscriptionId) {
            unsubscribed.countDown();
        }
    }
}
