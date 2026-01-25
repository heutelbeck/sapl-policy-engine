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

                    policy "skipped" deny false;
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

                    policy "never" permit false;
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
    @DisplayName("PureUnanimousVoter evaluation branches")
    class PureUnanimousVoterBranches {

        @Test
        @DisplayName("pure policies with runtime applicability return PureUnanimousVoter")
        void purePoliciesReturnPureVoter() {
            val compiled = compilePolicySet("""
                    set "test"
                    unanimous or abstain

                    policy "p1" permit subject == "alice";
                    """);
            assertThat(compiled.applicabilityAndVote()).isInstanceOf(PureVoter.class);
        }

        // @formatter:off
        static Stream<Arguments> pureVoterDecisionCases() {
            return Stream.of(
                arguments("runtime TRUE applicability with constant vote",
                    """
                    policy "p1" permit subject == "alice";
                    """,
                    "{ \"subject\": \"alice\", \"action\": \"read\", \"resource\": \"data\" }",
                    Decision.PERMIT),
                arguments("runtime FALSE applicability skips policy",
                    """
                    policy "p1" deny subject == "bob";
                    policy "p2" permit
                    """,
                    "{ \"subject\": \"alice\", \"action\": \"read\", \"resource\": \"data\" }",
                    Decision.PERMIT),
                arguments("mixed foldable and pure policies - all agree",
                    """
                    policy "foldable" permit
                    policy "pure" permit subject == "alice";
                    """,
                    "{ \"subject\": \"alice\", \"action\": \"read\", \"resource\": \"data\" }",
                    Decision.PERMIT),
                arguments("all pure policies NOT_APPLICABLE returns default deny",
                    """
                    policy "p1" permit subject == "bob";
                    policy "p2" permit subject == "charlie";
                    """,
                    "{ \"subject\": \"alice\", \"action\": \"read\", \"resource\": \"data\" }",
                    Decision.DENY)
            );
        }
        // @formatter:on

        @ParameterizedTest(name = "{0}")
        @MethodSource("pureVoterDecisionCases")
        @DisplayName("pure voter decision cases")
        void pureVoterDecisionCases(String description, String policies, String subscription,
                Decision expectedDecision) {
            val algorithm = expectedDecision == Decision.DENY ? "unanimous or deny" : "unanimous or abstain";
            val compiled  = compilePolicySet("""
                    set "test"
                    %s

                    %s
                    """.formatted(algorithm, policies));
            val ctx       = subscriptionContext(subscription);
            val result    = evaluatePolicySetWithPathEquivalenceCheck(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(expectedDecision);
        }

        // @formatter:off
        static Stream<Arguments> pureVoterIndeterminateCases() {
            return Stream.of(
                arguments("runtime ERROR applicability produces error vote",
                    """
                    policy "p1" permit subject.missing.field;
                    """,
                    "{ \"subject\": \"simple-string\", \"action\": \"read\", \"resource\": \"data\" }"),
                arguments("disagreement with runtime applicability",
                    """
                    policy "p1" permit subject == "alice";
                    policy "p2" deny subject == "alice";
                    """,
                    "{ \"subject\": \"alice\", \"action\": \"read\", \"resource\": \"data\" }"),
                arguments("short-circuit on terminal INDETERMINATE (disagreement)",
                    """
                    policy "p1" permit subject == "alice";
                    policy "p2" deny subject == "alice";
                    policy "p3" permit subject == "alice";
                    """,
                    "{ \"subject\": \"alice\", \"action\": \"read\", \"resource\": \"data\" }")
            );
        }
        // @formatter:on

        @ParameterizedTest(name = "{0}")
        @MethodSource("pureVoterIndeterminateCases")
        @DisplayName("pure voter INDETERMINATE cases")
        void pureVoterIndeterminateCases(String description, String policies, String subscription) {
            val compiled = compilePolicySet("""
                    set "test"
                    unanimous or abstain errors propagate

                    %s
                    """.formatted(policies));
            val ctx      = subscriptionContext(subscription);
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

                    policy "p1" permit <test.attr>;
                    """, attrBroker);
            assertThat(compiled.applicabilityAndVote()).isInstanceOf(StreamVoter.class);
        }

        // @formatter:off
        static Stream<Arguments> streamVoterPermitCases() {
            return Stream.of(
                arguments("stream voter with constant TRUE applicability",
                    """
                    policy "stream" permit <test.attr>;
                    """,
                    "{ \"subject\": \"alice\", \"action\": \"read\", \"resource\": \"data\" }"),
                arguments("stream voter with runtime applicability",
                    """
                    policy "stream" permit subject == "alice" && <test.attr>;
                    """,
                    "{ \"subject\": \"alice\", \"action\": \"read\", \"resource\": \"data\" }"),
                arguments("stream voter with FALSE applicability skips stream",
                    """
                    policy "skipped" deny subject == "bob" && <test.attr>;
                    policy "active" permit
                    """,
                    "{ \"subject\": \"alice\", \"action\": \"read\", \"resource\": \"data\" }"),
                arguments("mixed pure and stream policies - all agree",
                    """
                    policy "pure" permit subject == "alice";
                    policy "stream" permit <test.attr>;
                    """,
                    "{ \"subject\": \"alice\", \"action\": \"read\", \"resource\": \"data\" }"),
                arguments("stream voter with foldable accumulator - all agree",
                    """
                    policy "foldable" permit
                    policy "stream" permit <test.attr>;
                    """,
                    "{ \"subject\": \"alice\", \"action\": \"read\", \"resource\": \"data\" }")
            );
        }
        // @formatter:on

        @ParameterizedTest(name = "{0}")
        @MethodSource("streamVoterPermitCases")
        @DisplayName("stream voter PERMIT cases")
        void streamVoterPermitCases(String description, String policies, String subscription) {
            val attrBroker = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled   = compilePolicySet("""
                    set "test"
                    unanimous or abstain

                    %s
                    """.formatted(policies), attrBroker);
            val parsedSub  = parseSubscription(subscription);
            val ctx        = evaluationContext(parsedSub, attrBroker);
            assertStreamPathEquivalence(compiled, ctx, Decision.PERMIT);
        }

        // @formatter:off
        static Stream<Arguments> streamVoterIndeterminateCases() {
            return Stream.of(
                arguments("stream voter with error in applicability",
                    """
                    policy "stream" permit subject.missing.field && <test.attr>;
                    """,
                    "{ \"subject\": \"simple-string\", \"action\": \"read\", \"resource\": \"data\" }",
                    Map.of("test.attr", new Value[] { Value.TRUE })),
                arguments("disagreement with stream policies",
                    """
                    policy "stream1" permit <test.attr1>;
                    policy "stream2" deny <test.attr2>;
                    """,
                    "{ \"subject\": \"alice\", \"action\": \"read\", \"resource\": \"data\" }",
                    Map.of("test.attr1", new Value[] { Value.TRUE }, "test.attr2", new Value[] { Value.TRUE })),
                arguments("stream voter with foldable accumulator - disagree",
                    """
                    policy "foldable" deny
                    policy "stream" permit <test.attr>;
                    """,
                    "{ \"subject\": \"alice\", \"action\": \"read\", \"resource\": \"data\" }",
                    Map.of("test.attr", new Value[] { Value.TRUE })),
                arguments("pure disagreement short-circuits before evaluating streams",
                    """
                    policy "pure1" permit subject == "alice";
                    policy "pure2" deny subject == "alice";
                    policy "stream" permit <test.attr>;
                    """,
                    "{ \"subject\": \"alice\", \"action\": \"read\", \"resource\": \"data\" }",
                    Map.of("test.attr", new Value[] { Value.TRUE }))
            );
        }
        // @formatter:on

        @ParameterizedTest(name = "{0}")
        @MethodSource("streamVoterIndeterminateCases")
        @DisplayName("stream voter INDETERMINATE cases")
        void streamVoterIndeterminateCases(String description, String policies, String subscription,
                Map<String, Value[]> attributes) {
            val attrBroker = attributeBroker(attributes);
            val compiled   = compilePolicySet("""
                    set "test"
                    unanimous or abstain errors propagate

                    %s
                    """.formatted(policies), attrBroker);
            val parsedSub  = parseSubscription(subscription);
            val ctx        = evaluationContext(parsedSub, attrBroker);
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

                    policy "error" permit (1/0) > 0;
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

        // @formatter:off
        static Stream<Arguments> defaultDecisionCases() {
            return Stream.of(
                arguments("unanimous or deny", Decision.DENY, false),
                arguments("unanimous or permit", Decision.PERMIT, false),
                arguments("unanimous or abstain", Decision.NOT_APPLICABLE, false),
                arguments("unanimous strict or deny", Decision.DENY, true),
                arguments("unanimous strict or permit", Decision.PERMIT, true),
                arguments("unanimous strict or abstain", Decision.NOT_APPLICABLE, true)
            );
        }
        // @formatter:on

        @ParameterizedTest(name = "{0}: all NOT_APPLICABLE returns {1}")
        @MethodSource("defaultDecisionCases")
        @DisplayName("default decision when all NOT_APPLICABLE")
        void whenAllNotApplicableThenReturnsDefaultDecision(String algorithm, Decision expectedDecision,
                boolean isStrict) {
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
