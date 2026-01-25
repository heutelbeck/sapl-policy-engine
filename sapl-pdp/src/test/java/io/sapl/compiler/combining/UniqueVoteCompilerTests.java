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
package io.sapl.compiler.combining;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.compiler.document.PureVoter;
import io.sapl.compiler.document.StreamVoter;
import io.sapl.compiler.document.Vote;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static io.sapl.util.SaplTesting.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("UniqueVoteCompiler")
class UniqueVoteCompilerTests {

    @Nested
    @DisplayName("Static voter case - all foldable policies")
    class StaticVoterCase {

        @Test
        @DisplayName("all foldable policies return constant Vote")
        void whenAllFoldableThenReturnsVote() {
            val compiled = compilePolicySet("""
                    set "test"
                    unique or abstain

                    policy "p1" permit
                    """);
            assertThat(compiled.applicabilityAndVote()).isInstanceOf(Vote.class);
        }

        static Stream<Arguments> singlePolicyCases() {
            return Stream.of(arguments("permit", Decision.PERMIT), arguments("deny", Decision.DENY));
        }

        @ParameterizedTest(name = "single {0} policy returns {1}")
        @MethodSource("singlePolicyCases")
        @DisplayName("single policy returns its decision")
        void whenSinglePolicyThenReturnsItsDecision(String entitlement, Decision expectedDecision) {
            val compiled = compilePolicySet("""
                    set "test"
                    unique or abstain

                    policy "p1" %s
                    """.formatted(entitlement));
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(expectedDecision);
        }

        static Stream<Arguments> collisionCases() {
            return Stream.of(arguments("permit", "deny", "permit + deny"),
                    arguments("permit", "permit", "permit + permit"), arguments("deny", "deny", "deny + deny"));
        }

        @ParameterizedTest(name = "collision ({2}) returns INDETERMINATE")
        @MethodSource("collisionCases")
        @DisplayName("two applicable policies return INDETERMINATE (collision)")
        void whenTwoApplicablePoliciesThenReturnsIndeterminate(String p1, String p2, String description) {
            val compiled = compilePolicySet("""
                    set "test"
                    unique or abstain errors propagate

                    policy "p1" %s
                    policy "p2" %s
                    """.formatted(p1, p2));
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("constant FALSE applicability skips policy")
        void whenConstantFalseApplicabilityThenSkipsPolicy() {
            val compiled = compilePolicySet("""
                    set "test"
                    unique or abstain

                    policy "skipped" permit false;
                    policy "active" deny
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            // Only "active" deny policy is applicable
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.DENY);
        }

        @Test
        @DisplayName("all NOT_APPLICABLE returns NOT_APPLICABLE with default abstain")
        void whenAllNotApplicableAndDefaultAbstainThenReturnsNotApplicable() {
            val compiled = compilePolicySet("""
                    set "test"
                    unique or abstain

                    policy "never" permit false;
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("constant ERROR applicability treated as applicable (INDETERMINATE)")
        void whenConstantErrorApplicabilityThenReturnsIndeterminate() {
            val compiled = compilePolicySet("""
                    set "test"
                    unique or abstain errors propagate

                    policy "error-applicable" permit (1/0) > 0;
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }
    }

    @Nested
    @DisplayName("PureUniqueVoter evaluation branches")
    class PureUniqueVoterBranches {

        @Test
        @DisplayName("pure policies with runtime applicability return PureUniqueVoter")
        void purePoliciesReturnPureVoter() {
            val compiled = compilePolicySet("""
                    set "test"
                    unique or abstain

                    policy "p1" permit subject == "alice";
                    """);
            assertThat(compiled.applicabilityAndVote()).isInstanceOf(PureVoter.class);
        }

        @Test
        @DisplayName("runtime TRUE applicability with constant vote")
        void runtimeTrueWithConstantVote() {
            val compiled = compilePolicySet("""
                    set "test"
                    unique or abstain

                    policy "p1" permit subject == "alice";
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("runtime FALSE applicability skips policy")
        void runtimeFalseApplicabilitySkipsPolicy() {
            val compiled = compilePolicySet("""
                    set "test"
                    unique or abstain

                    policy "p1" permit subject == "bob";
                    policy "p2" deny
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.DENY);
        }

        @Test
        @DisplayName("runtime ERROR applicability produces error vote")
        void runtimeErrorApplicabilityProducesErrorVote() {
            val compiled = compilePolicySet("""
                    set "test"
                    unique or abstain errors propagate

                    policy "p1" permit subject.missing.field;
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "simple-string", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("voter is PureVoter requiring evaluation (runtime obligation)")
        void voterIsPureVoterRequiringEvaluation() {
            val compiled = compilePolicySet("""
                    set "test"
                    unique or abstain

                    policy "p1"
                    permit subject == "alice";
                    obligation subject
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision()).satisfies(authz -> {
                assertThat(authz.decision()).isEqualTo(Decision.PERMIT);
                assertThat(authz.obligations()).isNotEmpty();
            });
        }

        @Test
        @DisplayName("collision detection with runtime applicability")
        void collisionWithRuntimeApplicability() {
            val compiled = compilePolicySet("""
                    set "test"
                    unique or abstain errors propagate

                    policy "p1" permit subject == "alice";
                    policy "p2" deny subject == "alice";
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("mixed foldable and pure policies")
        void mixedFoldableAndPurePolicies() {
            val compiled = compilePolicySet("""
                    set "test"
                    unique or abstain

                    policy "foldable" permit false;
                    policy "pure" deny subject == "alice";
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.DENY);
        }

        @Test
        @DisplayName("all pure policies NOT_APPLICABLE returns default")
        void allPureNotApplicableReturnsDefault() {
            val compiled = compilePolicySet("""
                    set "test"
                    unique or deny

                    policy "p1" permit subject == "bob";
                    policy "p2" permit subject == "charlie";
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.DENY);
        }

    }

    @Nested
    @DisplayName("StreamUniqueVoter evaluation branches")
    class StreamUniqueVoterBranches {

        @Test
        @DisplayName("stream policies return StreamUniqueVoter")
        void streamPoliciesReturnStreamVoter() {
            val attrBroker = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled   = compilePolicySet("""
                    set "test"
                    unique or abstain

                    policy "p1" permit <test.attr>;
                    """, attrBroker);
            assertThat(compiled.applicabilityAndVote()).isInstanceOf(StreamVoter.class);
        }

        @Test
        @DisplayName("stream voter with constant TRUE applicability")
        void streamVoterWithConstantTrueApplicability() {
            val attrBroker   = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled     = compilePolicySet("""
                    set "test"
                    unique or abstain

                    policy "stream" permit <test.attr>;
                    """, attrBroker);
            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);
            assertStreamPathEquivalence(compiled, ctx, Decision.PERMIT);
        }

        @Test
        @DisplayName("stream voter with runtime applicability")
        void streamVoterWithRuntimeApplicability() {
            val attrBroker   = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled     = compilePolicySet("""
                    set "test"
                    unique or abstain

                    policy "stream" permit subject == "alice" && <test.attr>;
                    """, attrBroker);
            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);
            assertStreamPathEquivalence(compiled, ctx, Decision.PERMIT);
        }

        @Test
        @DisplayName("stream voter with error in applicability")
        void streamVoterWithErrorInApplicability() {
            val attrBroker   = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled     = compilePolicySet("""
                    set "test"
                    unique or abstain errors propagate

                    policy "stream" permit subject.missing.field && <test.attr>;
                    """, attrBroker);
            val subscription = parseSubscription("""
                    { "subject": "simple-string", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);
            assertStreamPathEquivalence(compiled, ctx, Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("stream voter with FALSE applicability skips stream")
        void streamVoterWithFalseApplicabilitySkipsStream() {
            val attrBroker   = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled     = compilePolicySet("""
                    set "test"
                    unique or abstain

                    policy "skipped" permit subject == "bob" && <test.attr>;
                    policy "active" deny
                    """, attrBroker);
            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);
            assertStreamPathEquivalence(compiled, ctx, Decision.DENY);
        }

        @Test
        @DisplayName("mixed pure and stream policies")
        void mixedPureAndStreamPolicies() {
            val attrBroker   = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled     = compilePolicySet("""
                    set "test"
                    unique or abstain

                    policy "pure" deny subject == "bob";
                    policy "stream" permit <test.attr>;
                    """, attrBroker);
            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);
            assertStreamPathEquivalence(compiled, ctx, Decision.PERMIT);
        }

        @Test
        @DisplayName("collision detection with stream policies")
        void collisionWithStreamPolicies() {
            val attrBroker   = attributeBroker(
                    Map.of("test.attr1", new Value[] { Value.TRUE }, "test.attr2", new Value[] { Value.TRUE }));
            val compiled     = compilePolicySet("""
                    set "test"
                    unique or abstain errors propagate

                    policy "stream1" permit <test.attr1>;
                    policy "stream2" deny <test.attr2>;
                    """, attrBroker);
            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);
            assertStreamPathEquivalence(compiled, ctx, Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("stream voter with foldable accumulator collision")
        void streamVoterWithFoldableAccumulatorCollision() {
            val attrBroker   = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled     = compilePolicySet("""
                    set "test"
                    unique or abstain errors propagate

                    policy "foldable" deny
                    policy "stream" permit <test.attr>;
                    """, attrBroker);
            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);
            assertStreamPathEquivalence(compiled, ctx, Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("single stream policy returns its decision")
        void singleStreamPolicyReturnsDecision() {
            val attrBroker   = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled     = compilePolicySet("""
                    set "test"
                    unique or abstain

                    policy "only" deny <test.attr>;
                    """, attrBroker);
            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);
            assertStreamPathEquivalence(compiled, ctx, Decision.DENY);
        }

        @Test
        @DisplayName("pure collision short-circuits before evaluating streams")
        void pureCollisionShortCircuitsBeforeStreams() {
            val attrBroker   = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled     = compilePolicySet("""
                    set "test"
                    unique or abstain errors propagate

                    policy "pure1" permit subject == "alice";
                    policy "pure2" deny subject == "alice";
                    policy "stream" permit <test.attr>;
                    """, attrBroker);
            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);
            assertStreamPathEquivalence(compiled, ctx, Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("error in stream applicability causes short-circuit")
        void errorInStreamApplicabilityCausesShortCircuit() {
            val attrBroker   = attributeBroker(
                    Map.of("test.attr1", new Value[] { Value.TRUE }, "test.attr2", new Value[] { Value.TRUE }));
            val compiled     = compilePolicySet("""
                    set "test"
                    unique or abstain errors propagate

                    policy "error" permit subject.missing && <test.attr1>;
                    policy "stream" deny <test.attr2>;
                    """, attrBroker);
            val subscription = parseSubscription("""
                    { "subject": "simple-string", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);
            assertStreamPathEquivalence(compiled, ctx, Decision.INDETERMINATE);
        }
    }

    @Nested
    @DisplayName("Default decision permutations")
    class DefaultDecisionPermutations {

        static Stream<Arguments> defaultDecisionCases() {
            return Stream.of(arguments("unique or deny", Decision.DENY), arguments("unique or permit", Decision.PERMIT),
                    arguments("unique or abstain", Decision.NOT_APPLICABLE));
        }

        @ParameterizedTest(name = "{0}: all NOT_APPLICABLE returns {1}")
        @MethodSource("defaultDecisionCases")
        @DisplayName("default decision when all NOT_APPLICABLE")
        void whenAllNotApplicableThenReturnsDefaultDecision(String algorithm, Decision expectedDecision) {
            val compiled = compilePolicySet("""
                    set "test"
                    %s

                    policy "never" permit false;
                    """.formatted(algorithm));
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(expectedDecision);
        }
    }

    @Nested
    @DisplayName("Error handling permutations")
    class ErrorHandlingPermutations {

        static Stream<Arguments> errorHandlingOnCollisionCases() {
            return Stream.of(arguments("unique or abstain", Decision.NOT_APPLICABLE, "errors abstain"),
                    arguments("unique or abstain errors propagate", Decision.INDETERMINATE, "errors propagate"));
        }

        @ParameterizedTest(name = "{2}: collision returns {1}")
        @MethodSource("errorHandlingOnCollisionCases")
        @DisplayName("error handling affects collision result")
        void whenCollisionThenErrorHandlingDeterminesResult(String algorithm, Decision expected, String description) {
            val compiled = compilePolicySet("""
                    set "test"
                    %s

                    policy "p1" permit
                    policy "p2" deny
                    """.formatted(algorithm));
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(expected);
        }

        static Stream<Arguments> errorHandlingWithDefaultDecisionCases() {
            return Stream.of(arguments("unique or deny", Decision.NOT_APPLICABLE),
                    arguments("unique or permit", Decision.NOT_APPLICABLE));
        }

        @ParameterizedTest(name = "{0}: errors abstain overrides default")
        @MethodSource("errorHandlingWithDefaultDecisionCases")
        @DisplayName("errors abstain overrides default decision on collision")
        void whenErrorsAbstainThenOverridesDefaultOnCollision(String algorithm, Decision expectedDecision) {
            val compiled = compilePolicySet("""
                    set "test"
                    %s

                    policy "p1" permit
                    policy "p2" permit
                    """.formatted(algorithm));
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            // Collision -> INDETERMINATE, errors abstain -> NOT_APPLICABLE
            assertThat(result.authorizationDecision().decision()).isEqualTo(expectedDecision);
        }
    }

    @Nested
    @DisplayName("Contributing votes tracking")
    class ContributingVotesTracking {

        @Test
        @DisplayName("single applicable policy is tracked as contributing")
        void whenSingleApplicableThenTrackedAsContributing() {
            val compiled = compilePolicySet("""
                    set "test"
                    unique or abstain

                    policy "the-one" permit
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.contributingVotes()).hasSize(1).extracting(v -> v.voter().name())
                    .containsExactly("the-one");
        }

        @Test
        @DisplayName("collision tracks both applicable policies")
        void whenCollisionThenTracksBothPolicies() {
            val compiled = compilePolicySet("""
                    set "test"
                    unique or abstain errors propagate

                    policy "p1" permit
                    policy "p2" deny
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.contributingVotes()).hasSize(2).extracting(v -> v.voter().name()).containsExactly("p1",
                    "p2");
        }
    }
}
