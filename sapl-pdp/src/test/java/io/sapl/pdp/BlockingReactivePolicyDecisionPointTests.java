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

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode;
import io.sapl.pdp.configuration.PdpVoterSource;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static io.sapl.api.test.pdp.PdpTestHelper.configuration;
import static io.sapl.api.test.pdp.PdpTestHelper.subscription;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * End-to-end tests for {@link BlockingPolicyDecisionPoint}. Mirrors
 * {@link DelegatingReactivePolicyDecisionPointTests} scenario shape so
 * behavioural
 * parity between the two PDPs is observable from the test inputs.
 */
@DisplayName("BlockingPolicyDecisionPoint")
class BlockingReactivePolicyDecisionPointTests {

    private static final CombiningAlgorithm DENY_UNLESS_PERMIT  = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT,
            DefaultDecision.DENY, ErrorHandling.ABSTAIN);
    private static final CombiningAlgorithm PERMIT_UNLESS_DENY  = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.PERMIT, ErrorHandling.ABSTAIN);
    private static final CombiningAlgorithm DENY_OVERRIDES      = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.DENY, ErrorHandling.PROPAGATE);
    private static final CombiningAlgorithm PERMIT_OVERRIDES    = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT,
            DefaultDecision.PERMIT, ErrorHandling.PROPAGATE);
    private static final CombiningAlgorithm ONLY_ONE_APPLICABLE = new CombiningAlgorithm(VotingMode.UNIQUE,
            DefaultDecision.DENY, ErrorHandling.PROPAGATE);

    private PdpVoterSource              pdpVoterSource;
    private BlockingPolicyDecisionPoint pdp;

    @BeforeEach
    void setUp() {
        val components = PolicyDecisionPointBuilder.withoutDefaults().build();
        pdpVoterSource = components.pdpVoterSource();
        pdp            = new BlockingPolicyDecisionPoint(pdpVoterSource, components.attributeStore(),
                new ThreadLocalRandomIdFactory());
    }

    @Test
    @DisplayName("no configuration loaded returns INDETERMINATE")
    void whenNoConfigurationLoadedThenReturnsIndeterminate() {
        val subscription = subscription("Nyarlathotep", "invoke", "outer_gods_council");

        val decision = pdp.decideOnce(subscription);

        assertThat(decision.decision()).isEqualTo(Decision.INDETERMINATE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void whenCombiningAlgorithmThenReturnsExpectedDecision(String description, CombiningAlgorithm algorithm,
            List<String> policies, AuthorizationSubscription subscription, Decision expectedDecision) {
        loadConfiguration(algorithm, policies.toArray(new String[0]));

        val decision = pdp.decideOnce(subscription);

        assertThat(decision.decision()).isEqualTo(expectedDecision);
    }

    private static Stream<Arguments> whenCombiningAlgorithmThenReturnsExpectedDecision() {
        val cultistSubscription      = subscription("cultist", "summon", "deep_one");
        val investigatorSubscription = subscription("investigator", "investigate", "innsmouth");

        return Stream.of(arguments("simple permit policy returns PERMIT", DENY_UNLESS_PERMIT, List.of("""
                policy "allow all"
                permit
                """), cultistSubscription, Decision.PERMIT),

                arguments("simple deny policy returns DENY", PERMIT_UNLESS_DENY, List.of("""
                        policy "deny all"
                        deny
                        """), cultistSubscription, Decision.DENY),

                arguments("deny-unless-permit with no matching policies returns DENY", DENY_UNLESS_PERMIT, List.of("""
                        policy "never matches"
                        permit
                            subject == "elder_thing";
                        """), cultistSubscription, Decision.DENY),

                arguments("permit-unless-deny with no matching policies returns PERMIT", PERMIT_UNLESS_DENY, List.of("""
                        policy "never matches"
                        deny
                            subject == "mi_go";
                        """), investigatorSubscription, Decision.PERMIT),

                arguments("deny-overrides with deny and permit returns DENY", DENY_OVERRIDES, List.of("""
                        policy "permit access"
                        permit
                        """, """
                        policy "deny access"
                        deny
                        """), cultistSubscription, Decision.DENY),

                arguments("permit-overrides with deny and permit returns PERMIT", PERMIT_OVERRIDES, List.of("""
                        policy "permit access"
                        permit
                        """, """
                        policy "deny access"
                        deny
                        """), cultistSubscription, Decision.PERMIT),

                arguments("only-one-applicable with single matching policy returns that decision", ONLY_ONE_APPLICABLE,
                        List.of("""
                                policy "permit cultists"
                                permit
                                    subject == "cultist";
                                """, """
                                policy "permit investigators"
                                permit
                                    subject == "investigator";
                                """), cultistSubscription, Decision.PERMIT),

                arguments("only-one-applicable with multiple matching policies returns INDETERMINATE",
                        ONLY_ONE_APPLICABLE, List.of("""
                                policy "permit all"
                                permit
                                """, """
                                policy "also permit all"
                                permit
                                """), cultistSubscription, Decision.INDETERMINATE));
    }

    @Test
    @DisplayName("decide stream emits the current decision and is closeable")
    void whenStreamingDecideThenEmitsCurrentDecisionAndCloses() throws Exception {
        loadConfiguration(DENY_UNLESS_PERMIT, """
                policy "permit cultists"
                permit
                    subject == "cultist";
                """);

        val subscription = subscription("cultist", "summon", "deep_one");

        try (val stream = pdp.decide(subscription)) {
            val first = stream.awaitNext();

            assertThat(first).isNotNull();
            assertThat(first.decision()).isEqualTo(Decision.PERMIT);
        }
    }

    @Test
    @DisplayName("decideOnce on multi-subscription bundle returns indeterminate when empty")
    void whenMultiSubscriptionEmptyThenDecideAllReturnsIndeterminate() throws Exception {
        loadConfiguration(DENY_UNLESS_PERMIT, """
                policy "permit all"
                permit
                """);

        try (val stream = pdp.decideAll(new MultiAuthorizationSubscription())) {
            val first = stream.awaitNext();

            assertThat(first).isNotNull();
            assertThat(first.size()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("decideAll bundles per-subscription decisions and is closeable")
    void whenMultiSubscriptionThenDecideAllEmitsBundle() throws Exception {
        loadConfiguration(DENY_UNLESS_PERMIT, """
                policy "permit cultists"
                permit
                    subject == "cultist";
                """);

        val multi = new MultiAuthorizationSubscription();
        multi.addSubscription("first", subscription("cultist", "summon", "deep_one"));
        multi.addSubscription("second", subscription("investigator", "summon", "deep_one"));

        try (val stream = pdp.decideAll(multi)) {
            val first = stream.awaitNext();

            assertThat(first).isNotNull();
            assertThat(first.getDecision("first").decision()).isEqualTo(Decision.PERMIT);
            assertThat(first.getDecision("second").decision()).isEqualTo(Decision.DENY);
        }
    }

    @Test
    @DisplayName("decide on multi-subscription emits per-id changes")
    void whenMultiSubscriptionThenDecideEmitsPerIdChanges() throws Exception {
        loadConfiguration(DENY_UNLESS_PERMIT, """
                policy "permit cultists"
                permit
                    subject == "cultist";
                """);

        val multi = new MultiAuthorizationSubscription();
        multi.addSubscription("first", subscription("cultist", "summon", "deep_one"));
        multi.addSubscription("second", subscription("investigator", "summon", "deep_one"));

        try (val stream = pdp.decide(multi)) {
            val firstChange  = stream.awaitNext();
            val secondChange = stream.awaitNext();

            assertThat(firstChange).isNotNull();
            assertThat(secondChange).isNotNull();
            assertThat(List.of(firstChange.subscriptionId(), secondChange.subscriptionId()))
                    .containsExactlyInAnyOrder("first", "second");
        }
    }

    @Test
    @DisplayName("decideOnceWithCoverage returns vote with branch coverage")
    void whenDecideOnceWithCoverageThenIncludesCoverage() {
        loadConfiguration(DENY_UNLESS_PERMIT, """
                policy "permit cultists"
                permit
                    subject == "cultist";
                """);

        val subscription = subscription("cultist", "summon", "deep_one");

        val voteWithCoverage = pdp.decideOnceWithCoverage(subscription);

        assertThat(voteWithCoverage.vote().authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        assertThat(voteWithCoverage.coverage()).isNotNull();
    }

    @Test
    @DisplayName("decideWithCoverage emits per-round coverage data")
    void whenDecideWithCoverageThenEmitsPerRound() throws Exception {
        loadConfiguration(DENY_UNLESS_PERMIT, """
                policy "permit cultists"
                permit
                    subject == "cultist";
                """);

        val subscription = subscription("cultist", "summon", "deep_one");

        try (val stream = pdp.decideWithCoverage(subscription)) {
            val first = stream.awaitNext();

            assertThat(first).isNotNull();
            assertThat(first.vote().authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        }
    }

    private void loadConfiguration(CombiningAlgorithm algorithm, String... policies) {
        pdpVoterSource.loadConfiguration(configuration(algorithm, policies), false);
    }
}
