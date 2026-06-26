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

import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.stream.LatestSlotStream;
import io.sapl.api.stream.Stream;
import io.sapl.reactive.pdp.DelegatingReactivePolicyDecisionPoint;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import static io.sapl.api.test.pdp.PdpTestHelper.configuration;
import static io.sapl.api.test.pdp.PdpTestHelper.subscription;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Locks the live-subscription contract for both
 * {@link DelegatingReactivePolicyDecisionPoint} and
 * {@link BlockingPolicyDecisionPoint}: a {@code decide(...)} stream
 * never closes from the server side. The consumer terminates it. In
 * the meantime, every {@link io.sapl.pdp.configuration.PdpVoterSource}
 * configuration change for the bound pdpId triggers a fresh
 * evaluation that publishes onto the same stream. Voter type may flip
 * across reloads (static {@code Vote} or {@code PureVoter} to a
 * streaming attribute-driven voter and back) without losing the
 * subscription.
 * <p>
 * These properties were absent from the suite before the regression
 * surfaced via the directory and remote-bundle hot-reload integration
 * tests. They are now locked here so any future regression in the
 * {@code switchOnConfig}-style wiring fails an in-engine unit test.
 */
@DisplayName("decide() stays alive across configuration changes")
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class PdpHotReloadSemanticsTests {

    private static final CombiningAlgorithm DENY_UNLESS_PERMIT = new CombiningAlgorithm(
            CombiningAlgorithm.VotingMode.PRIORITY_PERMIT, CombiningAlgorithm.DefaultDecision.DENY,
            CombiningAlgorithm.ErrorHandling.ABSTAIN);

    private static final String DENY_ALL = """
            policy "deny-all"
            deny true;
            """;

    private static final String PERMIT_ALL = """
            policy "permit-all"
            permit true;
            """;

    private static final String PERMIT_WHEN_FLAG = """
            policy "permit when flag set"
            permit
                <flag.value> == true;
            """;

    private static final Duration QUIET_WINDOW = Duration.ofMillis(300);

    private static PDPComponents emptyComponents(FlagPip flagPip) {
        return PolicyDecisionPointBuilder.withoutDefaults().withPolicyInformationPoint(flagPip).build();
    }

    private static void load(PDPComponents components, String... policies) {
        components.pdpVoterSource().loadConfiguration(configuration(DENY_UNLESS_PERMIT, policies), false);
    }

    private static MultiAuthorizationSubscription twoSubs() {
        val multi = new MultiAuthorizationSubscription();
        multi.addSubscription("alice-sub", subscription("alice", "read", "doc"));
        multi.addSubscription("bob-sub", subscription("bob", "read", "doc"));
        return multi;
    }

    @Nested
    @DisplayName("DelegatingReactivePolicyDecisionPoint (composition over BlockingPDP)")
    class DelegatingReactive {

        private DelegatingReactivePolicyDecisionPoint delegating(PDPComponents components) {
            return new DelegatingReactivePolicyDecisionPoint(new BlockingPolicyDecisionPoint(
                    components.pdpVoterSource(), components.attributeBroker(), new ThreadLocalRandomIdFactory()));
        }

        @Test
        @DisplayName("decide on a static policy stays alive after the initial emission")
        void whenStaticPolicyEvaluatedThenStreamStaysAliveWithoutSyntheticClose() {
            val flagPip = new FlagPip();
            try (val components = emptyComponents(flagPip)) {
                load(components, DENY_ALL);

                StepVerifier.create(delegating(components).decide(subscription("alice", "read", "doc")))
                        .assertNext(d -> assertThat(d.decision()).isEqualTo(Decision.DENY)).expectNoEvent(QUIET_WINDOW)
                        .thenCancel().verify(Duration.ofSeconds(5));
            }
        }

        @Test
        @DisplayName("decide re-evaluates when the configuration is reloaded with a different decision")
        void whenConfigurationReloadedThenStreamEmitsTheNewDecision() {
            val flagPip = new FlagPip();
            try (val components = emptyComponents(flagPip)) {
                load(components, DENY_ALL);

                StepVerifier.create(delegating(components).decide(subscription("alice", "read", "doc")))
                        .assertNext(d -> assertThat(d.decision()).isEqualTo(Decision.DENY))
                        .then(() -> load(components, PERMIT_ALL))
                        .assertNext(d -> assertThat(d.decision()).isEqualTo(Decision.PERMIT))
                        .expectNoEvent(QUIET_WINDOW).thenCancel().verify(Duration.ofSeconds(5));
            }
        }

        @Test
        @DisplayName("decide carries the subscription across PureVoter to StreamVoter and back")
        void whenVoterTypeChangesThenSubscriptionContinuesAcrossTheTransition() {
            val flagPip = new FlagPip();
            try (val components = emptyComponents(flagPip)) {
                flagPip.publish(Value.of(true));
                load(components, PERMIT_ALL);

                StepVerifier.create(delegating(components).decide(subscription("alice", "read", "doc")))
                        .assertNext(d -> assertThat(d.decision()).isEqualTo(Decision.PERMIT))
                        .then(() -> load(components, PERMIT_WHEN_FLAG)).then(() -> flagPip.publish(Value.of(false)))
                        .assertNext(d -> assertThat(d.decision()).isEqualTo(Decision.DENY))
                        .then(() -> flagPip.publish(Value.of(true)))
                        .assertNext(d -> assertThat(d.decision()).isEqualTo(Decision.PERMIT))
                        .then(() -> load(components, DENY_ALL))
                        .assertNext(d -> assertThat(d.decision()).isEqualTo(Decision.DENY)).expectNoEvent(QUIET_WINDOW)
                        .thenCancel().verify(Duration.ofSeconds(5));
            }
        }

        @Test
        @DisplayName("decide emits INDETERMINATE on configuration removal and re-evaluates on re-load")
        void whenConfigurationRemovedThenIndeterminateAndStreamStaysAliveForReLoad() {
            val flagPip = new FlagPip();
            try (val components = emptyComponents(flagPip)) {
                load(components, PERMIT_ALL);

                StepVerifier.create(delegating(components).decide(subscription("alice", "read", "doc")))
                        .assertNext(d -> assertThat(d.decision()).isEqualTo(Decision.PERMIT))
                        .then(() -> components.pdpVoterSource().removeConfigurationForPdp("default"))
                        .assertNext(d -> assertThat(d.decision()).isEqualTo(Decision.INDETERMINATE))
                        .then(() -> load(components, PERMIT_ALL))
                        .assertNext(d -> assertThat(d.decision()).isEqualTo(Decision.PERMIT))
                        .expectNoEvent(QUIET_WINDOW).thenCancel().verify(Duration.ofSeconds(5));
            }
        }

        @Test
        @DisplayName("decideAll re-evaluates every sub on configuration reload")
        void whenConfigurationReloadedThenDecideAllReevaluatesEveryBundle() {
            val flagPip = new FlagPip();
            try (val components = emptyComponents(flagPip)) {
                load(components, DENY_ALL);

                StepVerifier.create(delegating(components).decideAll(twoSubs())).assertNext(initial -> {
                    assertThat(initial.getDecision("alice-sub").decision()).isEqualTo(Decision.DENY);
                    assertThat(initial.getDecision("bob-sub").decision()).isEqualTo(Decision.DENY);
                }).then(() -> load(components, PERMIT_ALL)).assertNext(reloaded -> {
                    assertThat(reloaded.getDecision("alice-sub").decision()).isEqualTo(Decision.PERMIT);
                    assertThat(reloaded.getDecision("bob-sub").decision()).isEqualTo(Decision.PERMIT);
                }).expectNoEvent(QUIET_WINDOW).thenCancel().verify(Duration.ofSeconds(5));
            }
        }
    }

    @Nested
    @DisplayName("BlockingPolicyDecisionPoint")
    class Blocking {

        @Test
        @DisplayName("decide on a static policy stays alive after the initial emission")
        void whenStaticPolicyEvaluatedThenStreamStaysAliveWithoutSyntheticClose() throws Exception {
            val flagPip = new FlagPip();
            try (val components = emptyComponents(flagPip)) {
                val blocking = new BlockingPolicyDecisionPoint(components.pdpVoterSource(),
                        components.attributeBroker(), new ThreadLocalRandomIdFactory());
                load(components, DENY_ALL);

                try (val stream = blocking.decide(subscription("alice", "read", "doc"))) {
                    assertThat(stream.awaitNext().decision()).isEqualTo(Decision.DENY);
                    assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> stream.awaitNext(QUIET_WINDOW));
                }
            }
        }

        @Test
        @DisplayName("decide re-evaluates when the configuration is reloaded with a different decision")
        void whenConfigurationReloadedThenStreamEmitsTheNewDecision() throws Exception {
            val flagPip = new FlagPip();
            try (val components = emptyComponents(flagPip)) {
                val blocking = new BlockingPolicyDecisionPoint(components.pdpVoterSource(),
                        components.attributeBroker(), new ThreadLocalRandomIdFactory());
                load(components, DENY_ALL);

                try (val stream = blocking.decide(subscription("alice", "read", "doc"))) {
                    assertThat(stream.awaitNext().decision()).isEqualTo(Decision.DENY);

                    load(components, PERMIT_ALL);
                    assertThat(stream.awaitNext().decision()).isEqualTo(Decision.PERMIT);

                    assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> stream.awaitNext(QUIET_WINDOW));
                }
            }
        }

        @Test
        @DisplayName("decide carries the subscription across PureVoter to StreamVoter and back")
        void whenVoterTypeChangesThenSubscriptionContinuesAcrossTheTransition() throws Exception {
            val flagPip = new FlagPip();
            try (val components = emptyComponents(flagPip)) {
                val blocking = new BlockingPolicyDecisionPoint(components.pdpVoterSource(),
                        components.attributeBroker(), new ThreadLocalRandomIdFactory());
                flagPip.publish(Value.of(true));
                load(components, PERMIT_ALL);

                try (val stream = blocking.decide(subscription("alice", "read", "doc"))) {
                    assertThat(stream.awaitNext().decision()).isEqualTo(Decision.PERMIT);

                    load(components, PERMIT_WHEN_FLAG);
                    flagPip.publish(Value.of(false));
                    assertThat(stream.awaitNext().decision()).isEqualTo(Decision.DENY);

                    flagPip.publish(Value.of(true));
                    assertThat(stream.awaitNext().decision()).isEqualTo(Decision.PERMIT);

                    load(components, DENY_ALL);
                    assertThat(stream.awaitNext().decision()).isEqualTo(Decision.DENY);

                    assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> stream.awaitNext(QUIET_WINDOW));
                }
            }
        }

        @Test
        @DisplayName("decide emits INDETERMINATE on configuration removal and re-evaluates on re-load")
        void whenConfigurationRemovedThenIndeterminateAndStreamStaysAliveForReLoad() throws Exception {
            val flagPip = new FlagPip();
            try (val components = emptyComponents(flagPip)) {
                val blocking = new BlockingPolicyDecisionPoint(components.pdpVoterSource(),
                        components.attributeBroker(), new ThreadLocalRandomIdFactory());
                load(components, PERMIT_ALL);

                try (val stream = blocking.decide(subscription("alice", "read", "doc"))) {
                    assertThat(stream.awaitNext().decision()).isEqualTo(Decision.PERMIT);

                    components.pdpVoterSource().removeConfigurationForPdp("default");
                    assertThat(stream.awaitNext().decision()).isEqualTo(Decision.INDETERMINATE);

                    load(components, PERMIT_ALL);
                    assertThat(stream.awaitNext().decision()).isEqualTo(Decision.PERMIT);

                    assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> stream.awaitNext(QUIET_WINDOW));
                }
            }
        }

        @Test
        @DisplayName("decideAll re-evaluates every sub on configuration reload")
        void whenConfigurationReloadedThenDecideAllReevaluatesEveryBundle() throws Exception {
            val flagPip = new FlagPip();
            try (val components = emptyComponents(flagPip)) {
                val blocking = new BlockingPolicyDecisionPoint(components.pdpVoterSource(),
                        components.attributeBroker(), new ThreadLocalRandomIdFactory());
                load(components, DENY_ALL);

                try (val stream = blocking.decideAll(twoSubs())) {
                    val initial = stream.awaitNext();
                    assertThat(initial.getDecision("alice-sub").decision()).isEqualTo(Decision.DENY);
                    assertThat(initial.getDecision("bob-sub").decision()).isEqualTo(Decision.DENY);

                    load(components, PERMIT_ALL);

                    val reloaded = stream.awaitNext();
                    assertThat(reloaded.getDecision("alice-sub").decision()).isEqualTo(Decision.PERMIT);
                    assertThat(reloaded.getDecision("bob-sub").decision()).isEqualTo(Decision.PERMIT);

                    assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> stream.awaitNext(QUIET_WINDOW));
                }
            }
        }

        @Test
        @DisplayName("decideWithCoverage re-evaluates on configuration reload and stays alive")
        void whenConfigurationReloadedThenDecideWithCoverageEmitsTheNewVote() throws Exception {
            val flagPip = new FlagPip();
            try (val components = emptyComponents(flagPip)) {
                val blocking = new BlockingPolicyDecisionPoint(components.pdpVoterSource(),
                        components.attributeBroker(), new ThreadLocalRandomIdFactory());
                load(components, DENY_ALL);

                try (val stream = blocking.decideWithCoverage(subscription("alice", "read", "doc"))) {
                    assertThat(stream.awaitNext().vote().authorizationDecision().decision()).isEqualTo(Decision.DENY);

                    load(components, PERMIT_ALL);
                    assertThat(stream.awaitNext().vote().authorizationDecision().decision()).isEqualTo(Decision.PERMIT);

                    assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> stream.awaitNext(QUIET_WINDOW));
                }
            }
        }
    }

    /**
     * Test PIP exposing one controllable {@code <flag.value>}
     * environment attribute. Used by the PureVoter / StreamVoter
     * transition tests so a single subscription can flip between a
     * static voter shape and an attribute-driven voter shape across
     * configuration reloads.
     */
    @PolicyInformationPoint(name = "flag")
    public static class FlagPip {

        private final ReentrantLock                 lock        = new ReentrantLock();
        private Value                               current     = null;
        private final List<LatestSlotStream<Value>> openStreams = new CopyOnWriteArrayList<>();

        @EnvironmentAttribute
        public Stream<Value> value() {
            val stream = new LatestSlotStream<Value>();
            // Seed and register under the publish lock so a concurrent publish is never
            // lost across the register window.
            lock.lock();
            try {
                val seed = current;
                if (seed != null) {
                    stream.put(seed);
                }
                openStreams.add(stream);
            } finally {
                lock.unlock();
            }
            stream.onClose(() -> openStreams.remove(stream));
            return stream;
        }

        public void publish(Value v) {
            lock.lock();
            try {
                current = v;
                for (val s : openStreams) {
                    s.put(v);
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
