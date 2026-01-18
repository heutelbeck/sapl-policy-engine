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

    static final VoterMetadata TEST_METADATA = testMetadata("test-voter");

    static VoterMetadata testMetadata(String name) {
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
                return Outcome.PERMIT;
            }

            @Override
            public boolean hasConstraints() {
                return false;
            }
        };
    }

    static Vote permitVote(String name) {
        return Vote.tracedVote(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED,
                testMetadata(name), List.of());
    }

    static Vote denyVote(String name) {
        return Vote.tracedVote(Decision.DENY, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED, testMetadata(name),
                List.of());
    }

    static Vote notApplicableVote(String name) {
        return Vote.abstain(testMetadata(name));
    }

    static Vote indeterminateVote(String name) {
        return Vote.error(Value.error("test error"), testMetadata(name));
    }

    static Vote permitWithResource(String name, Value resource) {
        return Vote.tracedVote(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, resource, testMetadata(name),
                List.of());
    }

    static Vote permitWithObligations(String name, Value... obligations) {
        val obligationsArray = ArrayValue.builder();
        for (var o : obligations) {
            obligationsArray.add(o);
        }
        return Vote.tracedVote(Decision.PERMIT, obligationsArray.build(), Value.EMPTY_ARRAY, Value.UNDEFINED,
                testMetadata(name), List.of());
    }

    static Vote permitWithAdvice(String name, Value... advice) {
        val adviceArray = ArrayValue.builder();
        for (var a : advice) {
            adviceArray.add(a);
        }
        return Vote.tracedVote(Decision.PERMIT, Value.EMPTY_ARRAY, adviceArray.build(), Value.UNDEFINED,
                testMetadata(name), List.of());
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
            val votes  = new ArrayList<>(
                    List.of(notApplicableVote("policy-1"), notApplicableVote("policy-2"), permitVote("policy-3")));
            val result = PriorityBasedVoteCombiner.combineMultipleVotes(votes, Decision.PERMIT, TEST_METADATA);

            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
            assertThat(result.contributingVotes()).hasSize(3);
        }

        @Test
        @DisplayName("short-circuits on INDETERMINATE")
        void whenIndeterminateThenShortCircuits() {
            val votes  = new ArrayList<>(
                    List.of(permitVote("policy-1"), indeterminateVote("policy-2"), denyVote("policy-3")));
            val result = PriorityBasedVoteCombiner.combineMultipleVotes(votes, Decision.PERMIT, TEST_METADATA);

            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
            assertThat(result.contributingVotes()).hasSize(2).extracting(v -> v.voter().name())
                    .containsExactly("policy-1", "policy-2");
        }
    }

    @Nested
    @DisplayName("combineVotes decision priority")
    class CombineVotesDecisionPriorityTests {

        record DecisionPriorityCase(
                String description,
                Decision accumulatorDecision,
                Decision newDecision,
                Decision priorityDecision,
                Decision expectedResult) {

            @Override
            public @NonNull String toString() {
                return description;
            }
        }

        static Stream<Arguments> decisionPriorityCases() {
            return Stream.of(
                    // INDETERMINATE always wins
                    arguments(new DecisionPriorityCase("accumulator INDETERMINATE returns accumulator",
                            Decision.INDETERMINATE, Decision.PERMIT, Decision.PERMIT, Decision.INDETERMINATE)),
                    arguments(new DecisionPriorityCase("new INDETERMINATE returns INDETERMINATE", Decision.PERMIT,
                            Decision.INDETERMINATE, Decision.PERMIT, Decision.INDETERMINATE)),
                    arguments(new DecisionPriorityCase("new INDETERMINATE over DENY returns INDETERMINATE",
                            Decision.DENY, Decision.INDETERMINATE, Decision.DENY, Decision.INDETERMINATE)),

                    // NOT_APPLICABLE loses to everything
                    arguments(new DecisionPriorityCase("accumulator NOT_APPLICABLE loses to PERMIT",
                            Decision.NOT_APPLICABLE, Decision.PERMIT, Decision.PERMIT, Decision.PERMIT)),
                    arguments(new DecisionPriorityCase("accumulator NOT_APPLICABLE loses to DENY",
                            Decision.NOT_APPLICABLE, Decision.DENY, Decision.DENY, Decision.DENY)),
                    arguments(new DecisionPriorityCase("new NOT_APPLICABLE loses to PERMIT", Decision.PERMIT,
                            Decision.NOT_APPLICABLE, Decision.PERMIT, Decision.PERMIT)),
                    arguments(new DecisionPriorityCase("new NOT_APPLICABLE loses to DENY", Decision.DENY,
                            Decision.NOT_APPLICABLE, Decision.DENY, Decision.DENY)),
                    arguments(new DecisionPriorityCase("both NOT_APPLICABLE returns NOT_APPLICABLE",
                            Decision.NOT_APPLICABLE, Decision.NOT_APPLICABLE, Decision.PERMIT,
                            Decision.NOT_APPLICABLE)),

                    // Same decisions merge (no conflict)
                    arguments(new DecisionPriorityCase("both PERMIT merges to PERMIT", Decision.PERMIT, Decision.PERMIT,
                            Decision.PERMIT, Decision.PERMIT)),
                    arguments(new DecisionPriorityCase("both DENY merges to DENY", Decision.DENY, Decision.DENY,
                            Decision.DENY, Decision.DENY)),

                    // Priority resolution for PERMIT vs DENY
                    arguments(new DecisionPriorityCase("PERMIT vs DENY with priority PERMIT returns PERMIT",
                            Decision.PERMIT, Decision.DENY, Decision.PERMIT, Decision.PERMIT)),
                    arguments(new DecisionPriorityCase("PERMIT vs DENY with priority DENY returns DENY",
                            Decision.PERMIT, Decision.DENY, Decision.DENY, Decision.DENY)),
                    arguments(new DecisionPriorityCase("DENY vs PERMIT with priority PERMIT returns PERMIT",
                            Decision.DENY, Decision.PERMIT, Decision.PERMIT, Decision.PERMIT)),
                    arguments(new DecisionPriorityCase("DENY vs PERMIT with priority DENY returns DENY", Decision.DENY,
                            Decision.PERMIT, Decision.DENY, Decision.DENY)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("decisionPriorityCases")
        void decisionPriorityCases(DecisionPriorityCase testCase) {
            val accumulatorVote = createVoteForDecision(testCase.accumulatorDecision(), "accumulator");
            val newVote         = createVoteForDecision(testCase.newDecision(), "new");
            val accumulator     = PriorityBasedVoteCombiner.accumulatorVoteFrom(accumulatorVote, TEST_METADATA);

            val result = PriorityBasedVoteCombiner.combineVotes(accumulator, newVote, testCase.priorityDecision(),
                    TEST_METADATA);

            assertThat(result.authorizationDecision().decision()).isEqualTo(testCase.expectedResult());
        }

        private Vote createVoteForDecision(Decision decision, String name) {
            return switch (decision) {
            case PERMIT         -> permitVote(name);
            case DENY           -> denyVote(name);
            case NOT_APPLICABLE -> notApplicableVote(name);
            case INDETERMINATE  -> indeterminateVote(name);
            };
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

            assertThat(result.authorizationDecision().decision()).isEqualTo(testCase.expectedDecision());
            assertThat(result.authorizationDecision().resource()).isEqualTo(testCase.expectedResource());
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
    @DisplayName("error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("errors from new INDETERMINATE vote are captured")
        void whenNewIsIndeterminateThenErrorsCaptured() {
            val vote1       = permitVote("policy-1");
            val vote2       = indeterminateVote("policy-2");
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(vote1, TEST_METADATA);

            val result = PriorityBasedVoteCombiner.combineVotes(accumulator, vote2, Decision.PERMIT, TEST_METADATA);

            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
            assertThat(result.errors()).isNotEmpty();
        }

        @Test
        @DisplayName("accumulator INDETERMINATE returns unchanged (defensive path)")
        void whenAccumulatorIsIndeterminateThenReturnsAccumulator() {
            val vote1       = indeterminateVote("policy-1");
            val vote2       = permitVote("policy-2");
            val accumulator = PriorityBasedVoteCombiner.accumulatorVoteFrom(vote1, TEST_METADATA);

            val result = PriorityBasedVoteCombiner.combineVotes(accumulator, vote2, Decision.PERMIT, TEST_METADATA);

            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
            assertThat(result.voter().name()).isEqualTo("test-voter");
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
            val vote   = indeterminateVote("original");
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
            val metadata = testMetadata("new-metadata");
            val result   = PriorityBasedVoteCombiner.accumulatorVoteFrom(vote, metadata);

            assertThat(result.voter().name()).isEqualTo("new-metadata");
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
}
