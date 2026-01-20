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
import io.sapl.compiler.pdp.PureVoter;
import io.sapl.compiler.pdp.StreamVoter;
import io.sapl.compiler.pdp.Vote;
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

@DisplayName("UnanimousVoteCompiler")
class UnanimousVoteCompilerTests {

    @Nested
    @DisplayName("Static voter case - all foldable policies")
    class StaticVoterCase {

        @Test
        @DisplayName("all foldable policies return constant Vote")
        void whenAllFoldableThenReturnsVote() {
            val compiled = compilePolicySet("""
                    set "test"
                    unanimous or abstain

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
                    unanimous or abstain

                    policy "p1" %s
                    """.formatted(entitlement));
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(expectedDecision);
        }

        static Stream<Arguments> mergedConstraintsCases() {
            return Stream.of(arguments("permit", Decision.PERMIT), arguments("deny", Decision.DENY));
        }

        @ParameterizedTest(name = "all {0} policies return {1} with merged constraints")
        @MethodSource("mergedConstraintsCases")
        @DisplayName("agreeing policies merge constraints")
        void whenAllAgreeWithConstraintsThenReturnsMergedDecision(String entitlement, Decision expectedDecision) {
            val compiled = compilePolicySet("""
                    set "test"
                    unanimous or abstain

                    policy "p1" %s obligation "log1"
                    policy "p2" %s obligation "log2"
                    """.formatted(entitlement, entitlement));
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision()).satisfies(authz -> {
                assertThat(authz.decision()).isEqualTo(expectedDecision);
                assertThat(authz.obligations()).hasSize(2);
            });
        }

        @Test
        @DisplayName("disagreement (PERMIT vs DENY) returns INDETERMINATE")
        void whenDisagreementThenReturnsIndeterminate() {
            val compiled = compilePolicySet("""
                    set "test"
                    unanimous or abstain errors propagate

                    policy "p1" permit
                    policy "p2" deny
                    """);
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
                    unanimous or abstain

                    policy "skipped" deny where false;
                    policy "active" permit
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            // Only "active" permit policy is applicable
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("all NOT_APPLICABLE returns NOT_APPLICABLE with default abstain")
        void whenAllNotApplicableAndDefaultAbstainThenReturnsNotApplicable() {
            val compiled = compilePolicySet("""
                    set "test"
                    unanimous or abstain

                    policy "never" permit where false;
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("constant ERROR applicability produces error vote")
        void whenConstantErrorApplicabilityThenReturnsIndeterminate() {
            val compiled = compilePolicySet("""
                    set "test"
                    unanimous or abstain errors propagate

                    policy "error-applicable" permit where (1/0) > 0;
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }
    }

    @Nested
    @DisplayName("PureUnanimousVoter evaluation branches")
    class PureUnanimousVoterBranches {

        @Test
        @DisplayName("pure policies with runtime applicability return PureUnanimousVoter")
        void purePoliciesReturnPureVoter() {
            val compiled = compilePolicySet("""
                    set "test"
                    unanimous or abstain

                    policy "p1" permit where subject == "alice";
                    """);
            assertThat(compiled.applicabilityAndVote()).isInstanceOf(PureVoter.class);
        }

        @Test
        @DisplayName("runtime TRUE applicability with constant vote")
        void runtimeTrueWithConstantVote() {
            val compiled = compilePolicySet("""
                    set "test"
                    unanimous or abstain

                    policy "p1" permit where subject == "alice";
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
                    unanimous or abstain

                    policy "p1" deny where subject == "bob";
                    policy "p2" permit
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("runtime ERROR applicability produces error vote")
        void runtimeErrorApplicabilityProducesErrorVote() {
            val compiled = compilePolicySet("""
                    set "test"
                    unanimous or abstain errors propagate

                    policy "p1" permit where subject.missing.field;
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
                    unanimous or abstain

                    policy "p1"
                    permit where subject == "alice";
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
        @DisplayName("disagreement with runtime applicability")
        void disagreementWithRuntimeApplicability() {
            val compiled = compilePolicySet("""
                    set "test"
                    unanimous or abstain errors propagate

                    policy "p1" permit where subject == "alice";
                    policy "p2" deny where subject == "alice";
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("mixed foldable and pure policies - all agree")
        void mixedFoldableAndPurePoliciesAgree() {
            val compiled = compilePolicySet("""
                    set "test"
                    unanimous or abstain

                    policy "foldable" permit
                    policy "pure" permit where subject == "alice";
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("all pure policies NOT_APPLICABLE returns default")
        void allPureNotApplicableReturnsDefault() {
            val compiled = compilePolicySet("""
                    set "test"
                    unanimous or deny

                    policy "p1" permit where subject == "bob";
                    policy "p2" permit where subject == "charlie";
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.DENY);
        }

        @Test
        @DisplayName("short-circuit on terminal INDETERMINATE (disagreement)")
        void shortCircuitOnTerminalIndeterminate() {
            val compiled = compilePolicySet("""
                    set "test"
                    unanimous or abstain errors propagate

                    policy "p1" permit where subject == "alice";
                    policy "p2" deny where subject == "alice";
                    policy "p3" permit where subject == "alice";
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            // Should short-circuit after p1 and p2 disagree
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }
    }

    @Nested
    @DisplayName("StreamUnanimousVoter evaluation branches")
    class StreamUnanimousVoterBranches {

        @Test
        @DisplayName("stream policies return StreamUnanimousVoter")
        void streamPoliciesReturnStreamVoter() {
            val attrBroker = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled   = compilePolicySet("""
                    set "test"
                    unanimous or abstain

                    policy "p1" permit where <test.attr>;
                    """, attrBroker);
            assertThat(compiled.applicabilityAndVote()).isInstanceOf(StreamVoter.class);
        }

        @Test
        @DisplayName("stream voter with constant TRUE applicability")
        void streamVoterWithConstantTrueApplicability() {
            val attrBroker   = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled     = compilePolicySet("""
                    set "test"
                    unanimous or abstain

                    policy "stream" permit where <test.attr>;
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
                    unanimous or abstain

                    policy "stream" permit where subject == "alice" && <test.attr>;
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
                    unanimous or abstain errors propagate

                    policy "stream" permit where subject.missing.field && <test.attr>;
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
                    unanimous or abstain

                    policy "skipped" deny where subject == "bob" && <test.attr>;
                    policy "active" permit
                    """, attrBroker);
            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);
            assertStreamPathEquivalence(compiled, ctx, Decision.PERMIT);
        }

        @Test
        @DisplayName("mixed pure and stream policies - all agree")
        void mixedPureAndStreamPoliciesAgree() {
            val attrBroker   = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled     = compilePolicySet("""
                    set "test"
                    unanimous or abstain

                    policy "pure" permit where subject == "alice";
                    policy "stream" permit where <test.attr>;
                    """, attrBroker);
            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);
            assertStreamPathEquivalence(compiled, ctx, Decision.PERMIT);
        }

        @Test
        @DisplayName("disagreement with stream policies")
        void disagreementWithStreamPolicies() {
            val attrBroker   = attributeBroker(
                    Map.of("test.attr1", new Value[] { Value.TRUE }, "test.attr2", new Value[] { Value.TRUE }));
            val compiled     = compilePolicySet("""
                    set "test"
                    unanimous or abstain errors propagate

                    policy "stream1" permit where <test.attr1>;
                    policy "stream2" deny where <test.attr2>;
                    """, attrBroker);
            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);
            assertStreamPathEquivalence(compiled, ctx, Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("stream voter with foldable accumulator - all agree")
        void streamVoterWithFoldableAccumulatorAgree() {
            val attrBroker   = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled     = compilePolicySet("""
                    set "test"
                    unanimous or abstain

                    policy "foldable" permit
                    policy "stream" permit where <test.attr>;
                    """, attrBroker);
            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);
            assertStreamPathEquivalence(compiled, ctx, Decision.PERMIT);
        }

        @Test
        @DisplayName("stream voter with foldable accumulator - disagree")
        void streamVoterWithFoldableAccumulatorDisagree() {
            val attrBroker   = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled     = compilePolicySet("""
                    set "test"
                    unanimous or abstain errors propagate

                    policy "foldable" deny
                    policy "stream" permit where <test.attr>;
                    """, attrBroker);
            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);
            assertStreamPathEquivalence(compiled, ctx, Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("pure disagreement short-circuits before evaluating streams")
        void pureDisagreementShortCircuitsBeforeStreams() {
            val attrBroker   = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled     = compilePolicySet("""
                    set "test"
                    unanimous or abstain errors propagate

                    policy "pure1" permit where subject == "alice";
                    policy "pure2" deny where subject == "alice";
                    policy "stream" permit where <test.attr>;
                    """, attrBroker);
            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);
            assertStreamPathEquivalence(compiled, ctx, Decision.INDETERMINATE);
        }
    }

    @Nested
    @DisplayName("Strict mode")
    class StrictMode {

        @Test
        @DisplayName("strict mode: equal decisions return that decision")
        void whenStrictAndEqualThenReturnsDecision() {
            val compiled = compilePolicySet("""
                    set "test"
                    unanimous strict or abstain

                    policy "p1" permit obligation "log"
                    policy "p2" permit obligation "log"
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision()).satisfies(authz -> {
                assertThat(authz.decision()).isEqualTo(Decision.PERMIT);
                assertThat(authz.obligations()).hasSize(1); // Not merged, but equal
            });
        }

        static Stream<Arguments> strictModeInequalityCases() {
            return Stream.of(
                    arguments("permit obligation \"log1\"", "permit obligation \"log2\"", "different obligations"),
                    arguments("permit advice \"hint1\"", "permit advice \"hint2\"", "different advice"),
                    arguments("permit", "deny", "different decisions"));
        }

        @ParameterizedTest(name = "strict mode: {2} returns INDETERMINATE")
        @MethodSource("strictModeInequalityCases")
        @DisplayName("strict mode inequality returns INDETERMINATE")
        void whenStrictAndNotEqualThenReturnsIndeterminate(String policy1, String policy2, String description) {
            val compiled = compilePolicySet("""
                    set "test"
                    unanimous strict or abstain errors propagate

                    policy "p1" %s
                    policy "p2" %s
                    """.formatted(policy1, policy2));
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("strict mode: any INDETERMINATE is terminal")
        void whenStrictAnyIndeterminateIsTerminal() {
            val compiled = compilePolicySet("""
                    set "test"
                    unanimous strict or abstain errors propagate

                    policy "error" permit where (1/0) > 0;
                    policy "good" permit
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }
    }

    @Nested
    @DisplayName("Default decision permutations")
    class DefaultDecisionPermutations {

        static Stream<Arguments> defaultDecisionCases() {
            return Stream.of(arguments("unanimous or deny", Decision.DENY),
                    arguments("unanimous or permit", Decision.PERMIT),
                    arguments("unanimous or abstain", Decision.NOT_APPLICABLE));
        }

        @ParameterizedTest(name = "{0}: all NOT_APPLICABLE returns {1}")
        @MethodSource("defaultDecisionCases")
        @DisplayName("default decision when all NOT_APPLICABLE")
        void whenAllNotApplicableThenReturnsDefaultDecision(String algorithm, Decision expectedDecision) {
            val compiled = compilePolicySet("""
                    set "test"
                    %s

                    policy "never" permit where false;
                    """.formatted(algorithm));
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(expectedDecision);
        }

        static Stream<Arguments> strictDefaultDecisionCases() {
            return Stream.of(arguments("unanimous strict or deny", Decision.DENY),
                    arguments("unanimous strict or permit", Decision.PERMIT),
                    arguments("unanimous strict or abstain", Decision.NOT_APPLICABLE));
        }

        @ParameterizedTest(name = "{0}: all NOT_APPLICABLE returns {1}")
        @MethodSource("strictDefaultDecisionCases")
        @DisplayName("strict mode default decision when all NOT_APPLICABLE")
        void whenStrictAllNotApplicableThenReturnsDefaultDecision(String algorithm, Decision expectedDecision) {
            val compiled = compilePolicySet("""
                    set "test"
                    %s

                    policy "never" permit where false;
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

        static Stream<Arguments> errorHandlingOnDisagreementCases() {
            return Stream.of(arguments("unanimous or abstain", Decision.NOT_APPLICABLE, "errors abstain"),
                    arguments("unanimous or abstain errors propagate", Decision.INDETERMINATE, "errors propagate"));
        }

        @ParameterizedTest(name = "{2}: disagreement returns {1}")
        @MethodSource("errorHandlingOnDisagreementCases")
        @DisplayName("error handling affects disagreement result")
        void whenDisagreementThenErrorHandlingDeterminesResult(String algorithm, Decision expected,
                String description) {
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
            return Stream.of(arguments("unanimous or deny", Decision.NOT_APPLICABLE),
                    arguments("unanimous or permit", Decision.NOT_APPLICABLE));
        }

        @ParameterizedTest(name = "{0}: errors abstain overrides default")
        @MethodSource("errorHandlingWithDefaultDecisionCases")
        @DisplayName("errors abstain overrides default decision on disagreement")
        void whenErrorsAbstainThenOverridesDefaultOnDisagreement(String algorithm, Decision expectedDecision) {
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
            // Disagreement -> INDETERMINATE, errors abstain -> NOT_APPLICABLE
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
                    unanimous or abstain

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
        @DisplayName("all agreeing policies are tracked as contributing")
        void whenAllAgreeThenTracksAllPolicies() {
            val compiled = compilePolicySet("""
                    set "test"
                    unanimous or abstain

                    policy "p1" permit
                    policy "p2" permit
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.contributingVotes()).hasSize(2).extracting(v -> v.voter().name()).containsExactly("p1",
                    "p2");
        }

        @Test
        @DisplayName("disagreement tracks both policies")
        void whenDisagreementThenTracksBothPolicies() {
            val compiled = compilePolicySet("""
                    set "test"
                    unanimous or abstain errors propagate

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

    @Nested
    @DisplayName("Transformation uncertainty")
    class TransformationUncertainty {

        @Test
        @DisplayName("multiple resource transformations cause INDETERMINATE")
        void whenMultipleTransformationsThenReturnsIndeterminate() {
            val compiled = compilePolicySet("""
                    set "test"
                    unanimous or abstain errors propagate

                    policy "p1" permit transform "resource1"
                    policy "p2" permit transform "resource2"
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("single transformation is preserved")
        void whenSingleTransformationThenPreserved() {
            val compiled = compilePolicySet("""
                    set "test"
                    unanimous or abstain

                    policy "p1" permit transform "transformed"
                    policy "p2" permit
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision()).satisfies(authz -> {
                assertThat(authz.decision()).isEqualTo(Decision.PERMIT);
                assertThat(authz.resource()).isNotEqualTo(Value.UNDEFINED);
            });
        }
    }
}
