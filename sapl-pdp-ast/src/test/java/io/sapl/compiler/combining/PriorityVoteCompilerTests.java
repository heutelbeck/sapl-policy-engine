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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.model.Coverage.PolicySetCoverage;
import io.sapl.compiler.model.Coverage.TargetHit;
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
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

import static io.sapl.util.SaplTesting.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Tests for PriorityVoteCompiler covering priority-based vote combination
 * and coverage collection with systematic branch coverage.
 */
@DisplayName("PriorityVoteCompiler")
class PriorityVoteCompilerTests {

    static final Duration TIMEOUT = Duration.ofSeconds(5);

    static final TargetHit BLANK        = Coverage.BLANK_TARGET_HIT;
    static final TargetHit TARGET_TRUE  = new Coverage.TargetResult(Value.TRUE, null);
    static final TargetHit TARGET_FALSE = new Coverage.TargetResult(Value.FALSE, null);
    static final TargetHit TARGET_ERROR = new Coverage.TargetResult(Value.error(""), null);

    void assertTargetHitMatches(TargetHit actual, TargetHit expected) {
        assertThat(actual.getClass()).isEqualTo(expected.getClass());
        if (actual instanceof Coverage.TargetResult actualResult
                && expected instanceof Coverage.TargetResult expectedResult) {
            if (expectedResult.match() instanceof ErrorValue) {
                assertThat(actualResult.match()).isInstanceOf(ErrorValue.class);
            } else {
                assertThat(actualResult.match()).isEqualTo(expectedResult.match());
            }
        }
    }

    @Nested
    @DisplayName("Voter type selection based on policy classification")
    class VoterTypeSelection {

        @Test
        @DisplayName("all foldable policies return constant Vote")
        void allFoldableReturnsVote() {
            // constant TRUE applicability + constant vote = foldable
            val compiled = compilePolicySet("""
                    set "test"
                    priority permit or abstain

                    policy "p1" permit
                    policy "p2" deny
                    """);
            assertThat(compiled.applicabilityAndVote()).isInstanceOf(Vote.class);
        }

        @Test
        @DisplayName("pure policies with runtime applicability return PurePriorityVoter")
        void purePoliciesReturnPureVoter() {
            // runtime applicability = pure voter needed
            val compiled = compilePolicySet("""
                    set "test"
                    priority permit or abstain

                    policy "p1" permit where subject == "alice";
                    """);
            assertThat(compiled.applicabilityAndVote()).isInstanceOf(PureVoter.class);
        }

        @Test
        @DisplayName("stream policies return StreamPriorityVoter")
        void streamPoliciesReturnStreamVoter() {
            val attrBroker = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled   = compilePolicySet("""
                    set "test"
                    priority permit or abstain

                    policy "p1" permit where <test.attr>;
                    """, attrBroker);
            assertThat(compiled.applicabilityAndVote()).isInstanceOf(StreamVoter.class);
        }

        @Test
        @DisplayName("constant FALSE applicability policies are skipped entirely")
        void constantFalseApplicabilitySkipped() {
            val compiled = compilePolicySet("""
                    set "test"
                    priority permit or abstain

                    policy "skipped" permit where false;
                    policy "active" deny
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySet(compiled, ctx);
            // Only "active" deny policy contributes
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.DENY);
        }

        @Test
        @DisplayName("constant ERROR applicability is treated as applicable")
        void constantErrorApplicabilityTreatedAsApplicable() {
            val compiled = compilePolicySet("""
                    set "test"
                    priority permit or abstain errors propagate

                    policy "error-applicable" permit where (1/0) > 0;
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySet(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }
    }

    @Nested
    @DisplayName("PurePriorityVoter evaluation branches")
    class PurePriorityVoterBranches {

        @Test
        @DisplayName("constant TRUE applicability with constant vote")
        void constantTrueWithConstantVote() {
            val compiled = compilePolicySet("""
                    set "test"
                    priority permit or abstain

                    policy "p1" permit where subject == "alice";
                    policy "p2" deny
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySet(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("runtime applicability evaluates PureOperator")
        void runtimeApplicabilityEvaluatesPureOperator() {
            val compiled = compilePolicySet("""
                    set "test"
                    priority deny or abstain

                    policy "p1" permit where subject == "alice";
                    policy "p2" deny where subject == "bob";
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySet(compiled, ctx);
            // alice matches p1 (permit), not p2 (deny) - permit wins
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("runtime applicability error produces error vote")
        void runtimeApplicabilityErrorProducesErrorVote() {
            val compiled = compilePolicySet("""
                    set "test"
                    priority permit or abstain errors propagate

                    policy "p1" permit where subject.missing.field;
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "simple-string", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySet(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("runtime FALSE applicability skips policy")
        void runtimeFalseApplicabilitySkipsPolicy() {
            val compiled = compilePolicySet("""
                    set "test"
                    priority permit or abstain

                    policy "p1" permit where subject == "bob";
                    policy "p2" deny
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySet(compiled, ctx);
            // p1 skipped (alice != bob), p2 active
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.DENY);
        }

        @Test
        @DisplayName("voter is PureVoter requiring evaluation")
        void voterIsPureVoterRequiringEvaluation() {
            val compiled = compilePolicySet("""
                    set "test"
                    priority permit or abstain

                    policy "p1"
                    permit where subject == "alice";
                    obligation "log"
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySet(compiled, ctx);
            assertThat(result.authorizationDecision()).satisfies(authz -> {
                assertThat(authz.decision()).isEqualTo(Decision.PERMIT);
                assertThat(authz.obligations()).isNotEmpty();
            });
        }
    }

    @Nested
    @DisplayName("StreamPriorityVoter evaluation branches")
    class StreamPriorityVoterBranches {

        @Test
        @DisplayName("stream voter with constant TRUE applicability")
        void streamVoterWithConstantTrueApplicability() {
            val attrBroker = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled   = compilePolicySet("""
                    set "test"
                    priority permit or abstain

                    policy "stream" permit where <test.attr>;
                    """, attrBroker);

            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);

            StepVerifier
                    .create(((StreamVoter) compiled.applicabilityAndVote()).vote()
                            .contextWrite(c -> c.put(EvaluationContext.class, ctx)))
                    .assertNext(vote -> assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.PERMIT))
                    .expectComplete().verify(TIMEOUT);
        }

        @Test
        @DisplayName("stream voter with runtime applicability evaluated as PureOperator")
        void streamVoterWithRuntimeApplicability() {
            val attrBroker = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled   = compilePolicySet("""
                    set "test"
                    priority permit or abstain

                    policy "stream" permit where subject == "alice" && <test.attr>;
                    """, attrBroker);

            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);

            StepVerifier
                    .create(((StreamVoter) compiled.applicabilityAndVote()).vote()
                            .contextWrite(c -> c.put(EvaluationContext.class, ctx)))
                    .assertNext(vote -> assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.PERMIT))
                    .expectComplete().verify(TIMEOUT);
        }

        @Test
        @DisplayName("stream voter with error in applicability check")
        void streamVoterWithErrorInApplicability() {
            val attrBroker = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled   = compilePolicySet("""
                    set "test"
                    priority permit or abstain errors propagate

                    policy "error" permit where subject.missing && <test.attr>;
                    """, attrBroker);

            val subscription = parseSubscription("""
                    { "subject": "simple-string", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);

            StepVerifier
                    .create(((StreamVoter) compiled.applicabilityAndVote()).vote()
                            .contextWrite(c -> c.put(EvaluationContext.class, ctx)))
                    .assertNext(vote -> assertThat(vote.authorizationDecision().decision())
                            .isEqualTo(Decision.INDETERMINATE))
                    .expectComplete().verify(TIMEOUT);
        }

        @Test
        @DisplayName("stream voter with FALSE applicability skips stream")
        void streamVoterWithFalseApplicabilitySkipsStream() {
            val attrBroker = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled   = compilePolicySet("""
                    set "test"
                    priority deny or abstain

                    policy "skipped" permit where subject == "bob" && <test.attr>;
                    policy "active" deny
                    """, attrBroker);

            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);

            StepVerifier
                    .create(((StreamVoter) compiled.applicabilityAndVote()).vote()
                            .contextWrite(c -> c.put(EvaluationContext.class, ctx)))
                    .assertNext(vote -> assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.DENY))
                    .expectComplete().verify(TIMEOUT);
        }

        @Test
        @DisplayName("mixed pure and stream policies combine correctly")
        void mixedPureAndStreamPolicies() {
            val attrBroker = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled   = compilePolicySet("""
                    set "test"
                    priority permit or abstain

                    policy "pure" deny where subject == "alice";
                    policy "stream" permit where <test.attr>;
                    """, attrBroker);

            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);

            StepVerifier
                    .create(((StreamVoter) compiled.applicabilityAndVote()).vote()
                            .contextWrite(c -> c.put(EvaluationContext.class, ctx)))
                    .assertNext(vote -> assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.PERMIT))
                    .expectComplete().verify(TIMEOUT);
        }

        @Test
        @DisplayName("multiple stream policies combined via combineLatest")
        void multipleStreamPoliciesCombined() {
            val attrBroker = attributeBroker(
                    Map.of("test.attr1", new Value[] { Value.TRUE }, "test.attr2", new Value[] { Value.TRUE }));
            val compiled   = compilePolicySet("""
                    set "test"
                    priority deny or abstain

                    policy "stream1" permit where <test.attr1>;
                    policy "stream2" deny where <test.attr2>;
                    """, attrBroker);

            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);

            StepVerifier
                    .create(((StreamVoter) compiled.applicabilityAndVote()).vote()
                            .contextWrite(c -> c.put(EvaluationContext.class, ctx)))
                    .assertNext(vote -> assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.DENY))
                    .expectComplete().verify(TIMEOUT);
        }

        @Test
        @DisplayName("stream voter with foldable accumulator vote")
        void streamVoterWithFoldableAccumulator() {
            val attrBroker = attributeBroker(Map.of("test.attr", new Value[] { Value.TRUE }));
            val compiled   = compilePolicySet("""
                    set "test"
                    priority deny or abstain

                    policy "foldable" deny
                    policy "stream" permit where <test.attr>;
                    """, attrBroker);

            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);

            StepVerifier
                    .create(((StreamVoter) compiled.applicabilityAndVote()).vote()
                            .contextWrite(c -> c.put(EvaluationContext.class, ctx)))
                    .assertNext(vote -> assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.DENY))
                    .expectComplete().verify(TIMEOUT);
        }
    }

    @Nested
    @DisplayName("finalizeVote branches")
    class FinalizeVoteBranches {

        static Stream<Arguments> finalizeVoteCases() {
            return Stream.of(
                    // NOT_APPLICABLE + default decision
                    arguments("NOT_APPLICABLE + abstain = NOT_APPLICABLE", "priority permit or abstain", "false",
                            Decision.NOT_APPLICABLE),
                    arguments("NOT_APPLICABLE + deny = DENY", "priority permit or deny", "false", Decision.DENY),
                    arguments("NOT_APPLICABLE + permit = PERMIT", "priority permit or permit", "false",
                            Decision.PERMIT),
                    // INDETERMINATE + error handling
                    arguments("INDETERMINATE + abstain = NOT_APPLICABLE", "priority permit or abstain",
                            "subject.missing", Decision.NOT_APPLICABLE),
                    arguments("INDETERMINATE + propagate = INDETERMINATE",
                            "priority permit or abstain errors propagate", "subject.missing", Decision.INDETERMINATE),
                    // PERMIT/DENY pass through
                    arguments("PERMIT passes through", "priority permit or deny", "true", Decision.PERMIT),
                    arguments("DENY passes through", "priority deny or permit", "true", Decision.DENY));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("finalizeVoteCases")
        void finalizeVoteHandlesAllCases(String description, String algorithm, String whereClause, Decision expected) {
            val entitlement = algorithm.contains("priority permit") ? "permit" : "deny";
            val compiled    = compilePolicySet("""
                    set "test"
                    %s

                    policy "p1" %s where %s;
                    """.formatted(algorithm, entitlement, whereClause));
            val ctx         = subscriptionContext("""
                    { "subject": "simple-string", "action": "read", "resource": "data" }
                    """);
            val result      = evaluatePolicySet(compiled, ctx);
            assertThat(result.authorizationDecision().decision()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Coverage collection")
    class CoverageCollection {

        @Test
        @DisplayName("coverage collects from ALL policies")
        void coverageCollectsAllPolicies() {
            val compiled           = compilePolicySet("""
                    set "all-policies"
                    priority permit or abstain errors propagate

                    policy "first" permit
                    policy "second" deny
                    policy "third" permit
                    """);
            val ctx                = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val resultWithCoverage = evaluatePolicySetWithCoverage(compiled, ctx);

            assertThat(resultWithCoverage.vote().authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
            val psCoverage = (PolicySetCoverage) resultWithCoverage.coverage();
            assertThat(psCoverage.policyCoverages()).hasSize(3);
            assertTargetHitMatches(psCoverage.targetHit(), BLANK);
        }

        @Test
        @DisplayName("coverage includes NOT_APPLICABLE policies")
        void coverageIncludesNotApplicable() {
            val compiled           = compilePolicySet("""
                    set "mixed"
                    priority permit or abstain errors propagate

                    policy "applicable" permit
                    policy "not-applicable" deny where false;
                    """);
            val ctx                = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val resultWithCoverage = evaluatePolicySetWithCoverage(compiled, ctx);

            assertThat(resultWithCoverage.vote().authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
            val psCoverage = (PolicySetCoverage) resultWithCoverage.coverage();
            assertThat(psCoverage.policyCoverages()).hasSize(2);
        }

        @Test
        @DisplayName("streaming coverage collects from all stream policies")
        void streamingCoverageCollectsAll() {
            val attrBroker = attributeBroker(
                    Map.of("test.attr1", new Value[] { Value.TRUE }, "test.attr2", new Value[] { Value.TRUE }));
            val compiled   = compilePolicySet("""
                    set "streaming"
                    priority permit or abstain errors propagate

                    policy "stream1" permit where <test.attr1>;
                    policy "stream2" deny where <test.attr2>;
                    """, attrBroker);

            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);

            StepVerifier.create(compiled.coverage().contextWrite(c -> c.put(EvaluationContext.class, ctx)))
                    .assertNext(resultWithCoverage -> {
                        assertThat(resultWithCoverage.vote().authorizationDecision().decision())
                                .isEqualTo(Decision.PERMIT);
                        assertThat(((PolicySetCoverage) resultWithCoverage.coverage()).policyCoverages()).hasSize(2);
                    }).expectComplete().verify(TIMEOUT);
        }
    }

    @Nested
    @DisplayName("Policy set target with coverage")
    class PolicySetTargetCoverage {

        static Stream<Arguments> targetCases() {
            return Stream.of(
                    arguments("static TRUE evaluates all policies", "for true", "alice", Decision.PERMIT, 2,
                            TARGET_TRUE),
                    arguments("static FALSE skips all policies", "for false", "alice", Decision.NOT_APPLICABLE, 0,
                            TARGET_FALSE),
                    arguments("runtime TRUE evaluates all policies", "for subject == \"alice\"", "alice",
                            Decision.PERMIT, 2, TARGET_TRUE),
                    arguments("runtime FALSE skips all policies", "for subject == \"bob\"", "alice",
                            Decision.NOT_APPLICABLE, 0, TARGET_FALSE),
                    arguments("target error yields INDETERMINATE", "for subject.missing.field", "simple-string",
                            Decision.INDETERMINATE, 0, TARGET_ERROR));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("targetCases")
        void targetEvaluation(String description, String targetClause, String subject, Decision expectedDecision,
                int expectedPolicyCoverageCount, TargetHit expectedTargetHit) {
            val compiled           = compilePolicySet("""
                    set "with-target"
                    priority permit or abstain errors propagate
                    %s

                    policy "p1" permit
                    policy "p2" deny
                    """.formatted(targetClause));
            val ctx                = subscriptionContext("""
                    { "subject": "%s", "action": "read", "resource": "data" }
                    """.formatted(subject));
            val resultWithCoverage = evaluatePolicySetWithCoverage(compiled, ctx);

            assertThat(resultWithCoverage.vote().authorizationDecision().decision()).isEqualTo(expectedDecision);
            val psCoverage = (PolicySetCoverage) resultWithCoverage.coverage();
            assertThat(psCoverage.policyCoverages()).hasSize(expectedPolicyCoverageCount);
            assertTargetHitMatches(psCoverage.targetHit(), expectedTargetHit);
        }
    }

    @Nested
    @DisplayName("Production and coverage path equivalence")
    class ProductionCoverageEquivalence {

        static Stream<Arguments> equivalenceCases() {
            return Stream.of(arguments("priority permit with conflict", """
                    set "test"
                    priority permit or abstain errors propagate

                    policy "p1" permit
                    policy "p2" deny
                    """, "alice"), arguments("priority deny with conflict", """
                    set "test"
                    priority deny or abstain errors propagate

                    policy "p1" permit
                    policy "p2" deny
                    """, "alice"), arguments("all NOT_APPLICABLE with abstain", """
                    set "test"
                    priority permit or abstain

                    policy "p1" permit where false;
                    """, "alice"), arguments("all NOT_APPLICABLE with deny", """
                    set "test"
                    priority permit or deny

                    policy "p1" permit where false;
                    """, "alice"), arguments("all NOT_APPLICABLE with permit", """
                    set "test"
                    priority deny or permit

                    policy "p1" deny where false;
                    """, "alice"), arguments("target TRUE", """
                    set "test"
                    priority permit or abstain errors propagate
                    for true

                    policy "p1" permit
                    policy "p2" deny
                    """, "alice"), arguments("target FALSE", """
                    set "test"
                    priority permit or abstain errors propagate
                    for false

                    policy "p1" permit
                    policy "p2" deny
                    """, "alice"), arguments("runtime target TRUE", """
                    set "test"
                    priority permit or abstain errors propagate
                    for subject == "alice"

                    policy "p1" permit
                    policy "p2" deny
                    """, "alice"), arguments("runtime target FALSE", """
                    set "test"
                    priority permit or abstain errors propagate
                    for subject == "bob"

                    policy "p1" permit
                    policy "p2" deny
                    """, "alice"), arguments("target error", """
                    set "test"
                    priority permit or abstain errors propagate
                    for subject.missing.field

                    policy "p1" permit
                    policy "p2" deny
                    """, "simple-string"), arguments("policy error with abstain", """
                    set "test"
                    priority permit or abstain

                    policy "p1" permit where subject.missing.field;
                    """, "simple-string"), arguments("policy error with propagate", """
                    set "test"
                    priority permit or abstain errors propagate

                    policy "p1" permit where subject.missing.field;
                    """, "simple-string"), arguments("priority wins over error", """
                    set "test"
                    priority permit or abstain errors propagate

                    policy "p1" permit
                    policy "p2" deny where subject.missing.field;
                    """, "simple-string"), arguments("mixed applicable and not applicable", """
                    set "test"
                    priority deny or abstain errors propagate

                    policy "p1" permit
                    policy "p2" deny where false;
                    policy "p3" deny
                    """, "alice"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("equivalenceCases")
        void productionAndCoveragePathsReturnSameDecision(String description, String policySetSource, String subject) {
            val compiled       = compilePolicySet(policySetSource);
            val ctx            = subscriptionContext("""
                    { "subject": "%s", "action": "read", "resource": "data" }
                    """.formatted(subject));
            val productionVote = evaluatePolicySet(compiled, ctx);
            val coverageResult = evaluatePolicySetWithCoverage(compiled, ctx);

            assertThat(coverageResult.vote().authorizationDecision().decision())
                    .as("coverage path decision should match production path decision")
                    .isEqualTo(productionVote.authorizationDecision().decision());
        }

        @Test
        @DisplayName("streaming: production and coverage paths return same decision")
        void streamingProductionAndCoverageMatch() {
            val attrBroker = attributeBroker(
                    Map.of("test.attr1", new Value[] { Value.TRUE }, "test.attr2", new Value[] { Value.TRUE }));
            val compiled   = compilePolicySet("""
                    set "test"
                    priority permit or abstain errors propagate

                    policy "stream1" permit where <test.attr1>;
                    policy "stream2" deny where <test.attr2>;
                    """, attrBroker);

            val subscription   = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx            = evaluationContext(subscription, attrBroker);
            val productionVote = ((StreamVoter) compiled.applicabilityAndVote()).vote()
                    .contextWrite(c -> c.put(EvaluationContext.class, ctx)).blockFirst();
            val coverageResult = compiled.coverage().contextWrite(c -> c.put(EvaluationContext.class, ctx))
                    .blockFirst();

            assertThat(coverageResult.vote().authorizationDecision().decision())
                    .isEqualTo(productionVote.authorizationDecision().decision());
        }
    }

    @Nested
    @DisplayName("Priority decision semantics")
    class PriorityDecisionSemantics {

        static Stream<Arguments> priorityDecisionCases() {
            return Stream.of(arguments("priority permit: PERMIT beats DENY", "priority permit", Decision.PERMIT),
                    arguments("priority deny: DENY beats PERMIT", "priority deny", Decision.DENY));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("priorityDecisionCases")
        void priorityDecisionDeterminesWinner(String description, String algorithm, Decision expected) {
            val compiled = compilePolicySet("""
                    set "conflict"
                    %s or abstain errors propagate

                    policy "permit-policy" permit
                    policy "deny-policy" deny
                    """.formatted(algorithm));
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySet(compiled, ctx);

            assertThat(result.authorizationDecision().decision()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Default decision permutations")
    class DefaultDecisionPermutations {

        static Stream<Arguments> defaultDecisionCases() {
            return Stream.of(arguments("priority permit or deny yields DENY", "priority permit", "deny", Decision.DENY),
                    arguments("priority permit or permit yields PERMIT", "priority permit", "permit", Decision.PERMIT),
                    arguments("priority deny or deny yields DENY", "priority deny", "deny", Decision.DENY),
                    arguments("priority deny or permit yields PERMIT", "priority deny", "permit", Decision.PERMIT),
                    arguments("priority permit or abstain yields NOT_APPLICABLE", "priority permit", "abstain",
                            Decision.NOT_APPLICABLE),
                    arguments("priority deny or abstain yields NOT_APPLICABLE", "priority deny", "abstain",
                            Decision.NOT_APPLICABLE));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("defaultDecisionCases")
        void defaultDecisionWhenAllNotApplicable(String description, String algorithm, String defaultDecision,
                Decision expected) {
            val compiled = compilePolicySet("""
                    set "test"
                    %s or %s

                    policy "never-matches" permit where false;
                    """.formatted(algorithm, defaultDecision));
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySet(compiled, ctx);

            assertThat(result.authorizationDecision().decision()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Error handling permutations")
    class ErrorHandlingPermutations {

        static Stream<Arguments> errorHandlingCases() {
            return Stream.of(
                    arguments("errors abstain yields NOT_APPLICABLE", "priority permit or abstain",
                            Decision.NOT_APPLICABLE),
                    arguments("errors propagate yields INDETERMINATE", "priority permit or abstain errors propagate",
                            Decision.INDETERMINATE));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("errorHandlingCases")
        void errorHandlingDeterminesOutcome(String description, String algorithm, Decision expected) {
            val compiled = compilePolicySet("""
                    set "test"
                    %s

                    policy "errors" permit where subject.missing.field;
                    """.formatted(algorithm));
            val ctx      = subscriptionContext("""
                    { "subject": "simple-string", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySet(compiled, ctx);

            assertThat(result.authorizationDecision().decision()).isEqualTo(expected);
        }

        static Stream<Arguments> priorityOverErrorCases() {
            return Stream.of(
                    arguments("priority permit: PERMIT wins over error", "priority permit", "permit", "deny",
                            Decision.PERMIT),
                    arguments("priority deny: DENY wins over error", "priority deny", "deny", "permit", Decision.DENY));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("priorityOverErrorCases")
        void priorityDecisionWinsOverError(String description, String algorithm, String winningEntitlement,
                String errorEntitlement, Decision expected) {
            val compiled = compilePolicySet("""
                    set "test"
                    %s or abstain errors propagate

                    policy "winning-policy" %s
                    policy "error-policy" %s where subject.missing.field;
                    """.formatted(algorithm, winningEntitlement, errorEntitlement));
            val ctx      = subscriptionContext("""
                    { "subject": "simple-string", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySet(compiled, ctx);

            assertThat(result.authorizationDecision().decision()).isEqualTo(expected);
        }
    }
}
