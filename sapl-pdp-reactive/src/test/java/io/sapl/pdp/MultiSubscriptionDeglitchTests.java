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
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode;
import io.sapl.api.stream.LatestSlotStream;
import io.sapl.api.stream.Stream;
import org.jspecify.annotations.Nullable;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import io.sapl.reactive.pdp.DelegatingReactivePolicyDecisionPoint;
import io.sapl.reactive.pdp.ReactivePolicyDecisionPointBuilder;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

import static io.sapl.api.test.pdp.PdpTestHelper.configuration;
import static io.sapl.api.test.pdp.PdpTestHelper.subscription;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatObject;

/**
 * Locks the de-glitching invariants of the multi-subscription paths
 * in both {@link DelegatingReactivePolicyDecisionPoint} and
 * {@link BlockingPolicyDecisionPoint}.
 * <p>
 * Two contracts are tested for each PDP:
 * <ul>
 * <li><b>distinctness</b>: a snapshot round whose bundle equals the
 * previously emitted bundle does not produce a second emission.</li>
 * <li><b>glitch-free</b>: when N sub-subscriptions share a single
 * attribute and that attribute flips once, the implementation must
 * evaluate all N subs against ONE shared snapshot and emit exactly
 * ONE follow-up bundle, not N. The risk if violated would be a
 * combineLatest-style fanout where each sub emits independently. The
 * shared-snapshot construction in {@code multiVoteFlux} /
 * {@code multiVoteStream} avoids this.</li>
 * </ul>
 * The shared-attribute test PIP {@link FlagPip} exposes one
 * {@code <flag.value>} attribute backed by a controllable
 * {@link LatestSlotStream}; one publish updates every open
 * subscription view atomically from the test's perspective.
 */
@DisplayName("Multi-subscription de-glitch and distinctness")
class MultiSubscriptionDeglitchTests {

    private static final CombiningAlgorithm DENY_UNLESS_PERMIT = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT,
            DefaultDecision.DENY, ErrorHandling.ABSTAIN);

    private static final String SHARED_ATTRIBUTE_POLICY_ALICE = """
            policy "permit alice when flag set"
            permit
                subject == "alice";
                <flag.value> == true;
            """;

    private static final String SHARED_ATTRIBUTE_POLICY_BOB = """
            policy "permit bob when flag set"
            permit
                subject == "bob";
                <flag.value> == true;
            """;

    private static final Duration QUIET_WINDOW = Duration.ofMillis(300);

    private static MultiAuthorizationSubscription twoSubs() {
        val multi = new MultiAuthorizationSubscription();
        multi.addSubscription("alice-sub", subscription("alice", "read", "doc"));
        multi.addSubscription("bob-sub", subscription("bob", "read", "doc"));
        return multi;
    }

    private static PDPComponents buildComponents(FlagPip flagPip, String... policies) {
        val components = PolicyDecisionPointBuilder.withoutDefaults().withPolicyInformationPoint(flagPip).build();
        components.pdpVoterSource().loadConfiguration(configuration(DENY_UNLESS_PERMIT, policies));
        return components;
    }

    private static BlockingPolicyDecisionPoint blockingPdp(PDPComponents components) {
        return new BlockingPolicyDecisionPoint(components.pdpVoterSource(), components.attributeBroker(),
                new ThreadLocalRandomIdFactory());
    }

    private static ReactivePolicyDecisionPoint reactivePdp(PDPComponents components) {
        return ReactivePolicyDecisionPointBuilder.from(components).pdp();
    }

    private static void assertBundleDecisions(MultiAuthorizationDecision bundle, Decision aliceExpected,
            Decision bobExpected) {
        assertThatObject(bundle).isNotNull().satisfies(b -> {
            assertThat(b.getDecision("alice-sub").decision()).as("alice-sub decision").isEqualTo(aliceExpected);
            assertThat(b.getDecision("bob-sub").decision()).as("bob-sub decision").isEqualTo(bobExpected);
        });
    }

    @Nested
    @DisplayName("DelegatingReactivePolicyDecisionPoint")
    class Reactive {

        @Test
        @DisplayName("decideAll: shared-attribute flip emits exactly one follow-up bundle (glitch-free)")
        void whenSharedAttributeFlipsThenDecideAllEmitsExactlyOneFollowupBundle() {
            val flagPip = new FlagPip();
            try (val components = buildComponents(flagPip, SHARED_ATTRIBUTE_POLICY_ALICE,
                    SHARED_ATTRIBUTE_POLICY_BOB)) {
                flagPip.publish(Value.of(false));

                val pdp = reactivePdp(components);
                StepVerifier.create(pdp.decideAll(twoSubs()))
                        .assertNext(initial -> assertBundleDecisions(initial, Decision.DENY, Decision.DENY))
                        .then(() -> flagPip.publish(Value.of(true)))
                        .assertNext(flipped -> assertBundleDecisions(flipped, Decision.PERMIT, Decision.PERMIT))
                        .expectNoEvent(QUIET_WINDOW).thenCancel().verify(Duration.ofSeconds(5));
            }
        }

        @Test
        @DisplayName("decideAll: re-publishing the same value emits no follow-up bundle (distinct)")
        void whenSameValueRepublishedThenDecideAllEmitsNoFollowup() {
            val flagPip = new FlagPip();
            try (val components = buildComponents(flagPip, SHARED_ATTRIBUTE_POLICY_ALICE,
                    SHARED_ATTRIBUTE_POLICY_BOB)) {
                flagPip.publish(Value.of(true));

                val pdp = reactivePdp(components);
                StepVerifier.create(pdp.decideAll(twoSubs()))
                        .assertNext(initial -> assertBundleDecisions(initial, Decision.PERMIT, Decision.PERMIT))
                        .then(() -> {
                            flagPip.publish(Value.of(true));
                            flagPip.publish(Value.of(true));
                        }).expectNoEvent(QUIET_WINDOW).thenCancel().verify(Duration.ofSeconds(5));
            }
        }

        @Test
        @DisplayName("decide(MultiSub): shared-attribute flip emits exactly one per-sub change per affected sub (glitch-free)")
        void whenSharedAttributeFlipsThenDecideEmitsExactlyOnePerSubChange() {
            val flagPip = new FlagPip();
            try (val components = buildComponents(flagPip, SHARED_ATTRIBUTE_POLICY_ALICE,
                    SHARED_ATTRIBUTE_POLICY_BOB)) {
                flagPip.publish(Value.of(false));

                val initialIds = new CopyOnWriteArrayList<String>();
                val flippedIds = new CopyOnWriteArrayList<String>();

                val pdp = reactivePdp(components);
                StepVerifier.create(pdp.decide(twoSubs()))
                        .assertNext(change -> recordWithExpected(change, initialIds, Decision.DENY))
                        .assertNext(change -> recordWithExpected(change, initialIds, Decision.DENY))
                        .then(() -> flagPip.publish(Value.of(true)))
                        .assertNext(change -> recordWithExpected(change, flippedIds, Decision.PERMIT))
                        .assertNext(change -> recordWithExpected(change, flippedIds, Decision.PERMIT))
                        .expectNoEvent(QUIET_WINDOW).thenCancel().verify(Duration.ofSeconds(5));

                assertThat(initialIds).containsExactlyInAnyOrder("alice-sub", "bob-sub");
                assertThat(flippedIds).containsExactlyInAnyOrder("alice-sub", "bob-sub");
            }
        }

        private void recordWithExpected(IdentifiableAuthorizationDecision change, List<String> ids, Decision expected) {
            assertThat(change.decision().decision()).isEqualTo(expected);
            ids.add(change.subscriptionId());
        }
    }

    @Nested
    @DisplayName("BlockingPolicyDecisionPoint")
    class Blocking {

        @Test
        @DisplayName("decideAll: shared-attribute flip emits exactly one follow-up bundle (glitch-free)")
        void whenSharedAttributeFlipsThenDecideAllEmitsExactlyOneFollowupBundle() throws Exception {
            val flagPip = new FlagPip();
            try (val components = buildComponents(flagPip, SHARED_ATTRIBUTE_POLICY_ALICE,
                    SHARED_ATTRIBUTE_POLICY_BOB)) {
                val blocking = blockingPdp(components);
                flagPip.publish(Value.of(false));

                try (val stream = blocking.decideAll(twoSubs())) {
                    assertBundleDecisions(stream.awaitNext(), Decision.DENY, Decision.DENY);

                    flagPip.publish(Value.of(true));
                    assertBundleDecisions(stream.awaitNext(), Decision.PERMIT, Decision.PERMIT);

                    assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> stream.awaitNext(QUIET_WINDOW));
                }
            }
        }

        @Test
        @DisplayName("decideAll: re-publishing the same value emits no follow-up bundle (distinct)")
        void whenSameValueRepublishedThenDecideAllEmitsNoFollowup() throws Exception {
            val flagPip = new FlagPip();
            try (val components = buildComponents(flagPip, SHARED_ATTRIBUTE_POLICY_ALICE,
                    SHARED_ATTRIBUTE_POLICY_BOB)) {
                val blocking = blockingPdp(components);
                flagPip.publish(Value.of(true));

                try (val stream = blocking.decideAll(twoSubs())) {
                    assertBundleDecisions(stream.awaitNext(), Decision.PERMIT, Decision.PERMIT);

                    flagPip.publish(Value.of(true));
                    flagPip.publish(Value.of(true));

                    assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> stream.awaitNext(QUIET_WINDOW));
                }
            }
        }

        @Test
        @DisplayName("decide(MultiSub): shared-attribute flip emits exactly one per-sub change per affected sub (glitch-free)")
        void whenSharedAttributeFlipsThenDecideEmitsExactlyOnePerSubChange() throws Exception {
            val flagPip = new FlagPip();
            try (val components = buildComponents(flagPip, SHARED_ATTRIBUTE_POLICY_ALICE,
                    SHARED_ATTRIBUTE_POLICY_BOB)) {
                val blocking = blockingPdp(components);
                flagPip.publish(Value.of(false));

                try (val stream = blocking.decide(twoSubs())) {
                    val initial = List.of(stream.awaitNext(), stream.awaitNext());
                    assertThat(initial).satisfies(items -> {
                        assertThat(items).extracting(IdentifiableAuthorizationDecision::subscriptionId)
                                .containsExactlyInAnyOrder("alice-sub", "bob-sub");
                        assertThat(items).extracting(c -> c.decision().decision()).containsOnly(Decision.DENY);
                    });

                    flagPip.publish(Value.of(true));
                    val flipped = List.of(stream.awaitNext(), stream.awaitNext());
                    assertThat(flipped).satisfies(items -> {
                        assertThat(items).extracting(IdentifiableAuthorizationDecision::subscriptionId)
                                .containsExactlyInAnyOrder("alice-sub", "bob-sub");
                        assertThat(items).extracting(c -> c.decision().decision()).containsOnly(Decision.PERMIT);
                    });

                    assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> stream.awaitNext(QUIET_WINDOW));
                }
            }
        }

        @Test
        @DisplayName("decide single-sub: identical decision on re-evaluation is deduped")
        void whenSingleSubReEvaluatesIdenticallyThenDeduped() throws Exception {
            val flagPip = new FlagPip();
            try (val components = buildComponents(flagPip, SHARED_ATTRIBUTE_POLICY_ALICE)) {
                val blocking = blockingPdp(components);
                flagPip.publish(Value.of(true));

                try (val stream = blocking.decide(subscription("alice", "read", "doc"))) {
                    val first = stream.awaitNext();
                    assertThat(first.decision()).isEqualTo(Decision.PERMIT);

                    flagPip.publish(Value.of(true));
                    flagPip.publish(Value.of(true));

                    assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> stream.awaitNext(QUIET_WINDOW));
                }
            }
        }
    }

    /**
     * Test PIP exposing one controllable {@code <flag.value>}
     * environment attribute. Each invocation returns a fresh
     * {@link LatestSlotStream}; {@link #publish(Value)} updates every
     * currently open stream and the seed used by future invocations.
     */
    @PolicyInformationPoint(name = "flag")
    public static class FlagPip {

        private volatile @Nullable Value            current;
        private final List<LatestSlotStream<Value>> openStreams = new CopyOnWriteArrayList<>();

        @EnvironmentAttribute
        public Stream<Value> value() {
            val stream = new LatestSlotStream<Value>();
            val seed   = current;
            if (seed != null) {
                stream.put(seed);
            }
            openStreams.add(stream);
            stream.onClose(() -> openStreams.remove(stream));
            return stream;
        }

        void publish(Value v) {
            current = v;
            for (val s : openStreams) {
                s.put(v);
            }
        }
    }
}
