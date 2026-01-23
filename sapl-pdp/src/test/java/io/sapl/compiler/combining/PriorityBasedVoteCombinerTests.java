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

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.compiler.pdp.Vote;
import io.sapl.ast.Outcome;
import io.sapl.ast.VoterMetadata;
import lombok.val;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("PriorityBasedVoteCombiner")
class PriorityBasedVoteCombinerTests {

    static final VoterMetadata TEST_METADATA = testMetadata("test-voter", Outcome.PERMIT_OR_DENY);

    static VoterMetadata testMetadata(String name, Outcome outcome) {
        return new VoterMetadata() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String pdpId() {
                return "test-pdp";
            }

            @Override
            public String configurationId() {
                return "test-config";
            }

            @Override
            public Outcome outcome() {
                return outcome;
            }

            @Override
            public boolean hasConstraints() {
                return false;
            }
        };
    }

    static Vote permitVote(String name) {
        return Vote.tracedVote(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED,
                testMetadata(name, Outcome.PERMIT), List.of());
    }

    static Vote denyVote(String name) {
        return Vote.tracedVote(Decision.DENY, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED,
                testMetadata(name, Outcome.DENY), List.of());
    }

    static Vote indeterminateVote(String name, Outcome outcome) {
        return Vote.error(Value.error("test error"), testMetadata(name, outcome));
    }

    static Vote permitWithResource(String name, Value resource) {
        return Vote.tracedVote(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, resource,
                testMetadata(name, Outcome.PERMIT), List.of());
    }

    static Vote permitWithObligations(String name, Value... obligations) {
        val obligationsArray = ArrayValue.builder();
        for (var o : obligations) {
            obligationsArray.add(o);
        }
        return Vote.tracedVote(Decision.PERMIT, obligationsArray.build(), Value.EMPTY_ARRAY, Value.UNDEFINED,
                testMetadata(name, Outcome.PERMIT), List.of());
    }

    static Vote permitWithAdvice(String name, Value... advice) {
        val adviceArray = ArrayValue.builder();
        for (var a : advice) {
            adviceArray.add(a);
        }
        return Vote.tracedVote(Decision.PERMIT, Value.EMPTY_ARRAY, adviceArray.build(), Value.UNDEFINED,
                testMetadata(name, Outcome.PERMIT), List.of());
    }

    static Vote voteWithNullOutcome(Decision decision, String name) {
        return new Vote(new AuthorizationDecision(decision, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED),
                List.of(), List.of(), List.of(), testMetadata(name, null), null);
    }

    static Vote indeterminateWithNullOutcome(String name) {
        return new Vote(AuthorizationDecision.INDETERMINATE, List.of(Value.error("test error")), List.of(), List.of(),
                testMetadata(name, null), null);
    }

    @Nested
    @DisplayName("combineMultipleVotes")
    class CombineMultipleVotesTests {

        @Test
        @DisplayName("empty list returns abstain")
        void whenEmptyListThenReturnsAbstain() {
            val result = PriorityBasedVoteCombiner.combineMultipleVotes(new ArrayList<>(), Decision.PERMIT,
                    TEST_METADATA);

            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("single vote is wrapped with itself as contributing vote")
        void whenSingleVoteThenWrappedWithContributingVote() {
            val vote   = permitVote("policy-1");
            val votes  = new ArrayList<>(List.of(vote));
            val result = PriorityBasedVoteCombiner.combineMultipleVotes(votes, Decision.PERMIT, TEST_METADATA);

            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
            assertThat(result.contributingVotes()).hasSize(1).first()
                    .satisfies(v -> assertThat(v.voter().name()).isEqualTo("policy-1"));
        }

        @Test
        @DisplayName("multiple votes are combined and all contributing")
        void whenMultipleVotesThenAllContributing() {
            val votes  = new ArrayList<>(List.of(denyVote("policy-1"), denyVote("policy-2"), permitVote("policy-3")));
            val result = PriorityBasedVoteCombiner.combineMultipleVotes(votes, Decision.PERMIT, TEST_METADATA);

            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
                assertThat(r.contributingVotes()).hasSize(3);
            });
        }

        @Test
        @DisplayName("short-circuits on critical INDETERMINATE")
        void whenCriticalIndeterminateThenShortCircuits() {
            // INDET(PERMIT) is critical for permit-overrides - should short-circuit
            val votes  = new ArrayList<>(List.of(denyVote("policy-1"), indeterminateVote("policy-2", Outcome.PERMIT),
                    permitVote("policy-3")));
            val result = PriorityBasedVoteCombiner.combineMultipleVotes(votes, Decision.PERMIT, TEST_METADATA);

            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
                assertThat(r.contributingVotes()).hasSize(2).extracting(v -> v.voter().name())
                        .containsExactly("policy-1", "policy-2");
            });
        }

        @Test
        @DisplayName("does not short-circuit on non-critical INDETERMINATE")
        void whenNonCriticalIndeterminateThenContinues() {
            // INDET(DENY) is non-critical for permit-overrides - priority can still win
            val votes  = new ArrayList<>(
                    List.of(denyVote("policy-1"), indeterminateVote("policy-2", Outcome.DENY), permitVote("policy-3")));
            val result = PriorityBasedVoteCombiner.combineMultipleVotes(votes, Decision.PERMIT, TEST_METADATA);

            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
                assertThat(r.contributingVotes()).hasSize(3);
            });
        }

        @Test
        @DisplayName("non-critical accumulated INDETERMINATE continues loop")
        void whenNonCriticalAccumulatedIndeterminateThenContinuesLoop() {
            // INDET(PERMIT) is non-critical for deny-overrides - should not short-circuit
            val votes  = new ArrayList<>(List.of(indeterminateVote("policy-1", Outcome.PERMIT), denyVote("policy-2")));
            val result = PriorityBasedVoteCombiner.combineMultipleVotes(votes, Decision.DENY, TEST_METADATA);

            // DENY priority wins, all votes considered
            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.DENY);
                assertThat(r.contributingVotes()).hasSize(2);
            });
        }

        @Test
        @DisplayName("does not short-circuit on priority - collects constraints")
        void whenPriorityThenContinuesToCollectConstraints() {
            val obligation1 = Value.of("obligation-1");
            val obligation2 = Value.of("obligation-2");
            val votes       = new ArrayList<>(List.of(permitWithObligations("policy-1", obligation1),
                    denyVote("policy-2"), permitWithObligations("policy-3", obligation2)));
            val result      = PriorityBasedVoteCombiner.combineMultipleVotes(votes, Decision.PERMIT, TEST_METADATA);

            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
                assertThat((List<Value>) r.authorizationDecision().obligations()).containsExactly(obligation1,
                        obligation2);
                assertThat(r.contributingVotes()).hasSize(3);
            });
        }
    }

    @Nested
    @DisplayName("combineVotes basic decision logic")
    class CombineVotesBasicDecisionTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("basicDecisionCases")
        @DisplayName("basic decision combining")
        void basicDecisionCases(String description, Vote accVote, Vote newVote, Decision priority,
                Decision expectedDecision) {
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(accVote, TEST_METADATA);
            val result      = PriorityBasedVoteCombiner.combineVotes(accumulator, newVote, priority, TEST_METADATA);

            assertThat(result.authorizationDecision().decision()).isEqualTo(expectedDecision);
        }

        static Stream<Arguments> basicDecisionCases() {
            return Stream.of(
                    // Same decisions merge
                    arguments("both PERMIT merges to PERMIT", permitVote("a"), permitVote("b"), Decision.PERMIT,
                            Decision.PERMIT),
                    arguments("both DENY merges to DENY", denyVote("a"), denyVote("b"), Decision.DENY, Decision.DENY),

                    // Priority wins in PERMIT vs DENY
                    arguments("DENY vs PERMIT with permit-overrides returns PERMIT", denyVote("a"), permitVote("b"),
                            Decision.PERMIT, Decision.PERMIT),
                    arguments("DENY vs PERMIT with deny-overrides returns DENY", denyVote("a"), permitVote("b"),
                            Decision.DENY, Decision.DENY));
        }
    }

    @Nested
    @DisplayName("combineVotes extended indeterminate logic")
    class CombineVotesExtendedIndeterminateTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("extendedIndeterminateCases")
        @DisplayName("extended indeterminate handling")
        void extendedIndeterminateCases(String description, Vote accVote, Vote newVote, Decision priority,
                Decision expectedDecision, Outcome expectedOutcome) {
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(accVote, TEST_METADATA);
            val result      = PriorityBasedVoteCombiner.combineVotes(accumulator, newVote, priority, TEST_METADATA);

            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).as("decision").isEqualTo(expectedDecision);
                assertThat(r.outcome()).as("outcome").isEqualTo(expectedOutcome);
            });
        }

        static Stream<Arguments> extendedIndeterminateCases() {
            return Stream.of(
                    // Critical error blocks priority (permit-overrides)
                    arguments("DENY + INDET(PERMIT) is critical for permit-overrides", denyVote("a"),
                            indeterminateVote("b", Outcome.PERMIT), Decision.PERMIT, Decision.INDETERMINATE,
                            Outcome.PERMIT_OR_DENY),
                    arguments("DENY + INDET(PERMIT_OR_DENY) is critical for permit-overrides", denyVote("a"),
                            indeterminateVote("b", Outcome.PERMIT_OR_DENY), Decision.PERMIT, Decision.INDETERMINATE,
                            Outcome.PERMIT_OR_DENY),

                    // Non-critical error loses to priority
                    arguments("DENY + INDET(DENY) non-critical for permit-overrides - DENY wins", denyVote("a"),
                            indeterminateVote("b", Outcome.DENY), Decision.PERMIT, Decision.DENY, Outcome.DENY),

                    // Priority wins over non-critical accumulated error
                    arguments("INDET(DENY) + PERMIT: priority wins over non-critical error",
                            indeterminateVote("a", Outcome.DENY), permitVote("b"), Decision.PERMIT, Decision.PERMIT,
                            Outcome.PERMIT),

                    // Accumulated priority wins over non-critical new error
                    arguments("PERMIT + INDET(DENY): priority wins over non-critical error", permitVote("a"),
                            indeterminateVote("b", Outcome.DENY), Decision.PERMIT, Decision.PERMIT, Outcome.PERMIT),

                    // Critical accumulated error blocks priority
                    arguments("INDET(PERMIT) + PERMIT: critical error blocks priority",
                            indeterminateVote("a", Outcome.PERMIT), permitVote("b"), Decision.PERMIT,
                            Decision.INDETERMINATE, Outcome.PERMIT),
                    arguments("INDET(PERMIT_OR_DENY) + PERMIT: critical error blocks priority",
                            indeterminateVote("a", Outcome.PERMIT_OR_DENY), permitVote("b"), Decision.PERMIT,
                            Decision.INDETERMINATE, Outcome.PERMIT_OR_DENY),

                    // Non-priority vs accumulated INDETERMINATE
                    arguments("INDET(DENY) + DENY: non-critical error loses to non-priority",
                            indeterminateVote("a", Outcome.DENY), denyVote("b"), Decision.PERMIT, Decision.DENY,
                            Outcome.DENY),
                    arguments("INDET(PERMIT) + DENY: critical error blocks non-priority",
                            indeterminateVote("a", Outcome.PERMIT), denyVote("b"), Decision.PERMIT,
                            Decision.INDETERMINATE, Outcome.PERMIT_OR_DENY),

                    // Two INDETERMINATEs combine outcomes
                    arguments("INDET(PERMIT) + INDET(DENY) combines to INDET(PERMIT_OR_DENY)",
                            indeterminateVote("a", Outcome.PERMIT), indeterminateVote("b", Outcome.DENY),
                            Decision.PERMIT, Decision.INDETERMINATE, Outcome.PERMIT_OR_DENY),
                    arguments("INDET(DENY) + INDET(DENY) stays INDET(DENY)", indeterminateVote("a", Outcome.DENY),
                            indeterminateVote("b", Outcome.DENY), Decision.PERMIT, Decision.INDETERMINATE,
                            Outcome.DENY),

                    // Deny-overrides scenarios (DENY is priority, PERMIT is non-critical)
                    arguments("PERMIT + INDET(PERMIT): non-critical error for deny-overrides - PERMIT wins",
                            permitVote("a"), indeterminateVote("b", Outcome.PERMIT), Decision.DENY, Decision.PERMIT,
                            Outcome.PERMIT),
                    arguments("PERMIT + INDET(DENY): critical error for deny-overrides blocks", permitVote("a"),
                            indeterminateVote("b", Outcome.DENY), Decision.DENY, Decision.INDETERMINATE,
                            Outcome.PERMIT_OR_DENY),
                    arguments("INDET(PERMIT) + DENY: priority wins over non-critical error (deny-overrides)",
                            indeterminateVote("a", Outcome.PERMIT), denyVote("b"), Decision.DENY, Decision.DENY,
                            Outcome.DENY));
        }
    }

    @Nested
    @DisplayName("transformation uncertainty")
    class TransformationUncertaintyTests {

        record TransformationCase(
                String description,
                Value vote1Resource,
                Value vote2Resource,
                Decision expectedDecision,
                Value expectedResource) {

            @Override
            public @NonNull String toString() {
                return description;
            }
        }

        static Stream<Arguments> transformationCases() {
            return Stream.of(
                    arguments(new TransformationCase("both have resource transformation returns INDETERMINATE",
                            Value.of("resource-1"), Value.of("resource-2"), Decision.INDETERMINATE, Value.UNDEFINED)),
                    arguments(new TransformationCase("only accumulator has resource, merges with that resource",
                            Value.of("resource-1"), Value.UNDEFINED, Decision.PERMIT, Value.of("resource-1"))),
                    arguments(new TransformationCase("only new vote has resource, merges with that resource",
                            Value.UNDEFINED, Value.of("resource-2"), Decision.PERMIT, Value.of("resource-2"))),
                    arguments(new TransformationCase("neither has resource, merges with UNDEFINED", Value.UNDEFINED,
                            Value.UNDEFINED, Decision.PERMIT, Value.UNDEFINED)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("transformationCases")
        void transformationCases(TransformationCase testCase) {
            val vote1       = permitWithResource("policy-1", testCase.vote1Resource());
            val vote2       = permitWithResource("policy-2", testCase.vote2Resource());
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(vote1, TEST_METADATA);

            val result = PriorityBasedVoteCombiner.combineVotes(accumulator, vote2, Decision.PERMIT, TEST_METADATA);

            assertThat(result.authorizationDecision()).satisfies(authz -> {
                assertThat(authz.decision()).isEqualTo(testCase.expectedDecision());
                assertThat(authz.resource()).isEqualTo(testCase.expectedResource());
            });
        }

        @Test
        @DisplayName("transformation uncertainty creates error message")
        void whenTransformationUncertaintyThenCreatesErrorMessage() {
            val vote1       = permitWithResource("policy-1", Value.of("resource-1"));
            val vote2       = permitWithResource("policy-2", Value.of("resource-2"));
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(vote1, TEST_METADATA);

            val result = PriorityBasedVoteCombiner.combineVotes(accumulator, vote2, Decision.PERMIT, TEST_METADATA);

            assertThat(result.errors()).hasSize(1).first()
                    .satisfies(e -> assertThat(e.message()).contains("Transformation uncertainty"));
        }
    }

    @Nested
    @DisplayName("constraint merging")
    class ConstraintMergingTests {

        @Test
        @DisplayName("obligations are appended from both votes")
        void whenBothHaveObligationsThenAppended() {
            val obligation1 = Value.of("obligation-1");
            val obligation2 = Value.of("obligation-2");
            val vote1       = permitWithObligations("policy-1", obligation1);
            val vote2       = permitWithObligations("policy-2", obligation2);
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(vote1, TEST_METADATA);

            val result = PriorityBasedVoteCombiner.combineVotes(accumulator, vote2, Decision.PERMIT, TEST_METADATA);

            assertThat((List<Value>) result.authorizationDecision().obligations()).containsExactly(obligation1,
                    obligation2);
        }

        @Test
        @DisplayName("advice are appended from both votes")
        void whenBothHaveAdviceThenAppended() {
            val advice1     = Value.of("advice-1");
            val advice2     = Value.of("advice-2");
            val vote1       = permitWithAdvice("policy-1", advice1);
            val vote2       = permitWithAdvice("policy-2", advice2);
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(vote1, TEST_METADATA);

            val result = PriorityBasedVoteCombiner.combineVotes(accumulator, vote2, Decision.PERMIT, TEST_METADATA);

            assertThat((List<Value>) result.authorizationDecision().advice()).containsExactly(advice1, advice2);
        }

        @Test
        @DisplayName("empty obligations remain empty when no obligations present")
        void whenNoObligationsThenEmpty() {
            val vote1       = permitVote("policy-1");
            val vote2       = permitVote("policy-2");
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(vote1, TEST_METADATA);

            val result = PriorityBasedVoteCombiner.combineVotes(accumulator, vote2, Decision.PERMIT, TEST_METADATA);

            assertThat((List<Value>) result.authorizationDecision().obligations()).isEmpty();
        }
    }

    @Nested
    @DisplayName("error propagation")
    class ErrorPropagationTests {

        @Test
        @DisplayName("critical error propagates errors to result")
        void whenCriticalErrorThenErrorsPropagated() {
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(denyVote("a"), TEST_METADATA);

            val result = PriorityBasedVoteCombiner.combineVotes(accumulator, indeterminateVote("b", Outcome.PERMIT),
                    Decision.PERMIT, TEST_METADATA);

            assertThat(result.errors()).isNotEmpty();
        }

        @Test
        @DisplayName("non-critical error clears errors from result")
        void whenNonCriticalErrorThenErrorsCleared() {
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(denyVote("a"), TEST_METADATA);

            val result = PriorityBasedVoteCombiner.combineVotes(accumulator, indeterminateVote("b", Outcome.DENY),
                    Decision.PERMIT, TEST_METADATA);

            assertThat(result.errors()).isEmpty();
        }

        @Test
        @DisplayName("critical accumulated error preserves errors when priority arrives")
        void whenCriticalAccumulatedErrorThenErrorsPreserved() {
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(indeterminateVote("a", Outcome.PERMIT),
                    TEST_METADATA);

            val result = PriorityBasedVoteCombiner.combineVotes(accumulator, permitVote("b"), Decision.PERMIT,
                    TEST_METADATA);

            assertThat(result.errors()).isNotEmpty();
        }

        @Test
        @DisplayName("non-critical accumulated error clears errors when priority arrives")
        void whenNonCriticalAccumulatedErrorThenErrorsCleared() {
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(indeterminateVote("a", Outcome.DENY),
                    TEST_METADATA);

            val result = PriorityBasedVoteCombiner.combineVotes(accumulator, permitVote("b"), Decision.PERMIT,
                    TEST_METADATA);

            assertThat(result.errors()).isEmpty();
        }
    }

    @Nested
    @DisplayName("accumulatorVoteFrom")
    class AccumulatorVoteFromTests {

        @Test
        @DisplayName("preserves authorization decision from original vote")
        void whenCalledThenPreservesAuthorizationDecision() {
            val vote   = permitVote("original");
            val result = PriorityBasedVoteCombiner.accumulatorVoteFrom(vote, TEST_METADATA);

            assertThat(result.authorizationDecision()).isEqualTo(vote.authorizationDecision());
        }

        @Test
        @DisplayName("preserves errors from original vote")
        void whenCalledThenPreservesErrors() {
            val vote   = indeterminateVote("original", Outcome.PERMIT);
            val result = PriorityBasedVoteCombiner.accumulatorVoteFrom(vote, TEST_METADATA);

            assertThat(result.errors()).isEqualTo(vote.errors());
        }

        @Test
        @DisplayName("preserves contributing attributes from original vote")
        void whenCalledThenPreservesContributingAttributes() {
            val vote   = permitVote("original");
            val result = PriorityBasedVoteCombiner.accumulatorVoteFrom(vote, TEST_METADATA);

            assertThat(result.contributingAttributes()).isEqualTo(vote.contributingAttributes());
        }

        @Test
        @DisplayName("sets contributing votes to contain the original vote")
        void whenCalledThenSetsContributingVotes() {
            val vote   = permitVote("original");
            val result = PriorityBasedVoteCombiner.accumulatorVoteFrom(vote, TEST_METADATA);

            assertThat(result.contributingVotes()).containsExactly(vote);
        }

        @Test
        @DisplayName("uses provided voter metadata")
        void whenCalledThenUsesProvidedMetadata() {
            val vote     = permitVote("original");
            val metadata = testMetadata("new-metadata", Outcome.PERMIT);
            val result   = PriorityBasedVoteCombiner.accumulatorVoteFrom(vote, metadata);

            assertThat(result.voter().name()).isEqualTo("new-metadata");
        }

        @Test
        @DisplayName("preserves outcome from original vote")
        void whenCalledThenPreservesOutcome() {
            val vote   = indeterminateVote("original", Outcome.DENY);
            val result = PriorityBasedVoteCombiner.accumulatorVoteFrom(vote, TEST_METADATA);

            assertThat(result.outcome()).isEqualTo(Outcome.DENY);
        }
    }

    @Nested
    @DisplayName("null outcome edge cases")
    class NullOutcomeEdgeCasesTests {

        @Test
        @DisplayName("null outcome in new INDETERMINATE vote treated as critical")
        void whenNewIndeterminateHasNullOutcomeThenTreatedAsCritical() {
            val vote1       = denyVote("policy-1");
            val vote2       = indeterminateWithNullOutcome("policy-2");
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(vote1, TEST_METADATA);

            val result = PriorityBasedVoteCombiner.combineVotes(accumulator, vote2, Decision.PERMIT, TEST_METADATA);

            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("null outcome in accumulated vote combined with non-null")
        void whenAccumulatorHasNullOutcomeThenCombinesWithNewOutcome() {
            val vote1       = voteWithNullOutcome(Decision.DENY, "policy-1");
            val vote2       = denyVote("policy-2");
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(vote1, TEST_METADATA);

            val result = PriorityBasedVoteCombiner.combineVotes(accumulator, vote2, Decision.PERMIT, TEST_METADATA);

            assertThat(result.outcome()).isEqualTo(Outcome.DENY);
        }

        @Test
        @DisplayName("non-null outcome combined with null returns non-null")
        void whenNewVoteHasNullOutcomeThenReturnsAccumulatorOutcome() {
            val vote1       = denyVote("policy-1");
            val vote2       = voteWithNullOutcome(Decision.DENY, "policy-2");
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(vote1, TEST_METADATA);

            val result = PriorityBasedVoteCombiner.combineVotes(accumulator, vote2, Decision.PERMIT, TEST_METADATA);

            assertThat(result.outcome()).isEqualTo(Outcome.DENY);
        }
    }

    @Nested
    @DisplayName("empty contributing votes edge case")
    class EmptyContributingVotesTests {

        @Test
        @DisplayName("accumulator with empty contributing votes list")
        void whenAccumulatorHasEmptyContributingVotesThenAppendWorks() {
            val accVote = new Vote(
                    new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED),
                    List.of(), List.of(), List.of(), testMetadata("acc", Outcome.PERMIT), Outcome.PERMIT);
            val newVote = permitVote("policy-2");

            val result = PriorityBasedVoteCombiner.combineVotes(accVote, newVote, Decision.PERMIT, TEST_METADATA);

            assertThat(result.contributingVotes()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("contributing votes tracking")
    class ContributingVotesTrackingTests {

        @Test
        @DisplayName("combined vote contains all contributing votes")
        void whenCombinedThenContainsAllContributingVotes() {
            val vote1       = permitVote("policy-1");
            val vote2       = permitVote("policy-2");
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(vote1, TEST_METADATA);

            val result = PriorityBasedVoteCombiner.combineVotes(accumulator, vote2, Decision.PERMIT, TEST_METADATA);

            assertThat(result.contributingVotes()).hasSize(2).extracting(v -> v.voter().name())
                    .containsExactly("policy-1", "policy-2");
        }

        @Test
        @DisplayName("contributing votes accumulate through multiple combines")
        void whenMultipleCombinesThenVotesAccumulate() {
            val votes = new ArrayList<>(
                    List.of(permitVote("policy-1"), permitVote("policy-2"), permitVote("policy-3")));

            val result = PriorityBasedVoteCombiner.combineMultipleVotes(votes, Decision.PERMIT, TEST_METADATA);

            assertThat(result.contributingVotes()).hasSize(3).extracting(v -> v.voter().name())
                    .containsExactly("policy-1", "policy-2", "policy-3");
        }
    }

    @Nested
    @DisplayName("combineMultipleVotes with accumulator")
    class CombineMultipleVotesWithAccumulatorTests {

        @Test
        @DisplayName("empty list returns accumulator unchanged")
        void whenEmptyListThenReturnsAccumulatorUnchanged() {
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(permitVote("pre-existing"), TEST_METADATA);

            val result = PriorityBasedVoteCombiner.combineMultipleVotes(accumulator, List.of(), Decision.PERMIT,
                    TEST_METADATA);

            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
                assertThat(r.contributingVotes()).hasSize(1).extracting(v -> v.voter().name())
                        .containsExactly("pre-existing");
            });
        }

        @Test
        @DisplayName("accumulator contributing votes are preserved")
        void whenAccumulatorHasContributingVotesThenPreserved() {
            val preExisting = permitVote("pre-existing");
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(preExisting, TEST_METADATA);
            val newVotes    = List.of(denyVote("policy-1"), permitVote("policy-2"));

            val result = PriorityBasedVoteCombiner.combineMultipleVotes(accumulator, newVotes, Decision.PERMIT,
                    TEST_METADATA);

            assertThat(result.contributingVotes()).hasSize(3).extracting(v -> v.voter().name())
                    .containsExactly("pre-existing", "policy-1", "policy-2");
        }

        @Test
        @DisplayName("accumulator itself is not added as contribution")
        void whenAccumulatorUsedThenNotAddedAsContribution() {
            val preExisting = permitVote("pre-existing");
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(preExisting, TEST_METADATA);
            val newVotes    = List.of(permitVote("policy-1"));

            val result = PriorityBasedVoteCombiner.combineMultipleVotes(accumulator, newVotes, Decision.PERMIT,
                    TEST_METADATA);

            assertThat(result.contributingVotes()).hasSize(2).extracting(v -> v.voter().name())
                    .containsExactly("pre-existing", "policy-1");
        }

        @Test
        @DisplayName("priority decision is correctly determined with accumulator")
        void whenPriorityInNewVotesThenWins() {
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(denyVote("pre-existing"), TEST_METADATA);
            val newVotes    = List.of(denyVote("policy-1"), permitVote("policy-2"));

            val result = PriorityBasedVoteCombiner.combineMultipleVotes(accumulator, newVotes, Decision.PERMIT,
                    TEST_METADATA);

            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("short-circuits on critical INDETERMINATE in accumulator")
        void whenAccumulatorHasCriticalIndeterminateThenShortCircuits() {
            val errorVote   = indeterminateVote("pre-existing", Outcome.PERMIT);
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(errorVote, TEST_METADATA);
            val newVotes    = List.of(permitVote("policy-1"), permitVote("policy-2"));

            val result = PriorityBasedVoteCombiner.combineMultipleVotes(accumulator, newVotes, Decision.PERMIT,
                    TEST_METADATA);

            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
                assertThat(r.contributingVotes()).hasSize(1).extracting(v -> v.voter().name())
                        .containsExactly("pre-existing");
            });
        }

        @Test
        @DisplayName("non-critical INDETERMINATE in accumulator continues processing")
        void whenAccumulatorHasNonCriticalIndeterminateThenContinues() {
            val errorVote   = indeterminateVote("pre-existing", Outcome.DENY);
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(errorVote, TEST_METADATA);
            val newVotes    = List.of(permitVote("policy-1"));

            val result = PriorityBasedVoteCombiner.combineMultipleVotes(accumulator, newVotes, Decision.PERMIT,
                    TEST_METADATA);

            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
                assertThat(r.contributingVotes()).hasSize(2);
            });
        }

        @Test
        @DisplayName("constraints from accumulator and new votes are merged")
        void whenBothHaveConstraintsThenMerged() {
            val obligation1 = Value.of("obligation-1");
            val obligation2 = Value.of("obligation-2");
            val accumulator = PriorityBasedVoteCombiner
                    .accumulatorVoteFrom(permitWithObligations("pre-existing", obligation1), TEST_METADATA);
            val newVotes    = List.of(permitWithObligations("policy-1", obligation2));

            val result = PriorityBasedVoteCombiner.combineMultipleVotes(accumulator, newVotes, Decision.PERMIT,
                    TEST_METADATA);

            assertThat((List<Value>) result.authorizationDecision().obligations()).containsExactly(obligation1,
                    obligation2);
        }
    }
}
