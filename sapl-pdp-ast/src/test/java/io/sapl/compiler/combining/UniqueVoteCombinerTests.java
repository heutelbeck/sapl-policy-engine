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
import io.sapl.ast.Outcome;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.pdp.Vote;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("UniqueVoteCombiner")
class UniqueVoteCombinerTests {

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

    static Vote voteFor(Decision decision) {
        return switch (decision) {
        case PERMIT         -> Vote.tracedVote(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED,
                testMetadata("p", Outcome.PERMIT), List.of());
        case DENY           -> Vote.tracedVote(Decision.DENY, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED,
                testMetadata("p", Outcome.DENY), List.of());
        case NOT_APPLICABLE -> Vote.abstain(testMetadata("p", Outcome.PERMIT));
        case INDETERMINATE  -> Vote.error(Value.error("test error"), testMetadata("p", Outcome.PERMIT));
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

    static Vote notApplicableVote(String name) {
        return Vote.abstain(testMetadata(name, Outcome.PERMIT));
    }

    static Vote indeterminateVote(String name, Outcome outcome) {
        return Vote.error(Value.error("test error"), testMetadata(name, outcome));
    }

    @Nested
    @DisplayName("accumulatorVoteFrom")
    class AccumulatorVoteFromTests {

        @Test
        @DisplayName("preserves decision, outcome, and wraps as contributing vote")
        void preservesStateAndWrapsAsContributingVote() {
            val vote        = permitVote("policy-1");
            val accumulator = UniqueVoteCombiner.accumulatorVoteFrom(vote, TEST_METADATA);
            assertThat(accumulator).satisfies(acc -> {
                assertThat(acc.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
                assertThat(acc.outcome()).isEqualTo(Outcome.PERMIT);
                assertThat(acc.contributingVotes()).hasSize(1).first().isSameAs(vote);
            });
        }

        @Test
        @DisplayName("preserves errors from indeterminate vote")
        void preservesErrors() {
            val vote        = indeterminateVote("policy-1", Outcome.PERMIT);
            val accumulator = UniqueVoteCombiner.accumulatorVoteFrom(vote, TEST_METADATA);
            assertThat(accumulator.errors()).hasSize(1).first()
                    .satisfies(e -> assertThat(e.message()).isEqualTo("test error"));
        }

        @Test
        @DisplayName("uses provided metadata")
        void usesProvidedMetadata() {
            val vote        = permitVote("policy-1");
            val metadata    = testMetadata("accumulator", Outcome.DENY);
            val accumulator = UniqueVoteCombiner.accumulatorVoteFrom(vote, metadata);
            assertThat(accumulator.voter().name()).isEqualTo("accumulator");
        }
    }

    @Nested
    @DisplayName("combineVotes pairwise")
    class CombineVotesPairwiseTests {

        static Stream<Arguments> pairwiseCombinationDecisionTable() {
            return Stream.of(arguments(Decision.NOT_APPLICABLE, Decision.NOT_APPLICABLE, Decision.NOT_APPLICABLE),
                    arguments(Decision.NOT_APPLICABLE, Decision.PERMIT, Decision.PERMIT),
                    arguments(Decision.NOT_APPLICABLE, Decision.DENY, Decision.DENY),
                    arguments(Decision.NOT_APPLICABLE, Decision.INDETERMINATE, Decision.INDETERMINATE),
                    arguments(Decision.PERMIT, Decision.NOT_APPLICABLE, Decision.PERMIT),
                    arguments(Decision.PERMIT, Decision.PERMIT, Decision.INDETERMINATE),
                    arguments(Decision.PERMIT, Decision.DENY, Decision.INDETERMINATE),
                    arguments(Decision.PERMIT, Decision.INDETERMINATE, Decision.INDETERMINATE),
                    arguments(Decision.DENY, Decision.NOT_APPLICABLE, Decision.DENY),
                    arguments(Decision.DENY, Decision.PERMIT, Decision.INDETERMINATE),
                    arguments(Decision.DENY, Decision.DENY, Decision.INDETERMINATE),
                    arguments(Decision.DENY, Decision.INDETERMINATE, Decision.INDETERMINATE),
                    arguments(Decision.INDETERMINATE, Decision.NOT_APPLICABLE, Decision.INDETERMINATE),
                    arguments(Decision.INDETERMINATE, Decision.PERMIT, Decision.INDETERMINATE),
                    arguments(Decision.INDETERMINATE, Decision.DENY, Decision.INDETERMINATE),
                    arguments(Decision.INDETERMINATE, Decision.INDETERMINATE, Decision.INDETERMINATE));
        }

        @ParameterizedTest(name = "{0} + {1} = {2}")
        @MethodSource("pairwiseCombinationDecisionTable")
        @DisplayName("pairwise combination")
        void pairwiseCombination(Decision accDecision, Decision newDecision, Decision expectedDecision) {
            val acc    = UniqueVoteCombiner.accumulatorVoteFrom(voteFor(accDecision), TEST_METADATA);
            val result = UniqueVoteCombiner.combineVotes(acc, voteFor(newDecision), TEST_METADATA);
            assertThat(result.authorizationDecision().decision()).isEqualTo(expectedDecision);
        }

        @Test
        @DisplayName("collision creates error with message")
        void collisionCreatesErrorWithMessage() {
            val acc    = UniqueVoteCombiner.accumulatorVoteFrom(permitVote("p1"), TEST_METADATA);
            val result = UniqueVoteCombiner.combineVotes(acc, permitVote("p2"), TEST_METADATA);
            assertThat(result.errors()).hasSize(1).first()
                    .satisfies(e -> assertThat(e.message()).contains("Multiple applicable"));
        }

        @Test
        @DisplayName("INDETERMINATE preserves outcome and errors from source")
        void indeterminatePreservesOutcomeAndErrors() {
            val acc    = UniqueVoteCombiner.accumulatorVoteFrom(indeterminateVote("p1", Outcome.PERMIT), TEST_METADATA);
            val result = UniqueVoteCombiner.combineVotes(acc, permitVote("p2"), TEST_METADATA);
            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
                assertThat(r.outcome()).isEqualTo(Outcome.PERMIT);
                assertThat(r.errors()).hasSize(1).first()
                        .satisfies(e -> assertThat(e.message()).isEqualTo("test error"));
            });
        }

        @Test
        @DisplayName("new INDETERMINATE preserves its outcome")
        void newIndeterminatePreservesItsOutcome() {
            val acc    = UniqueVoteCombiner.accumulatorVoteFrom(permitVote("p1"), TEST_METADATA);
            val result = UniqueVoteCombiner.combineVotes(acc, indeterminateVote("p2", Outcome.DENY), TEST_METADATA);
            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
                assertThat(r.outcome()).isEqualTo(Outcome.DENY);
            });
        }

        @Test
        @DisplayName("appends to contributing votes")
        void appendsToContributingVotes() {
            val acc    = UniqueVoteCombiner.accumulatorVoteFrom(permitVote("p1"), TEST_METADATA);
            val result = UniqueVoteCombiner.combineVotes(acc, notApplicableVote("p2"), TEST_METADATA);
            assertThat(result.contributingVotes()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("combineMultipleVotes - zero applicable")
    class CombineMultipleZeroApplicableTests {

        @Test
        @DisplayName("empty list returns NOT_APPLICABLE with no contributing votes")
        void whenEmptyListThenReturnsNotApplicableWithNoContributingVotes() {
            val result = UniqueVoteCombiner.combineMultipleVotes(List.of(), TEST_METADATA);
            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
                assertThat(r.contributingVotes()).isEmpty();
            });
        }

        @Test
        @DisplayName("all NOT_APPLICABLE returns NOT_APPLICABLE and records contributing votes")
        void whenAllNotApplicableThenReturnsNotApplicableAndRecordsContributingVotes() {
            val votes  = List.of(notApplicableVote("policy-1"), notApplicableVote("policy-2"),
                    notApplicableVote("policy-3"));
            val result = UniqueVoteCombiner.combineMultipleVotes(votes, TEST_METADATA);
            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
                assertThat(r.contributingVotes()).hasSize(3);
            });
        }
    }

    @Nested
    @DisplayName("combineMultipleVotes - one applicable")
    class CombineMultipleOneApplicableTests {

        static Stream<Arguments> singleApplicableVotes() {
            return Stream.of(arguments(Decision.PERMIT, Outcome.PERMIT), arguments(Decision.DENY, Outcome.DENY));
        }

        @ParameterizedTest(name = "single {0} returns {0}")
        @MethodSource("singleApplicableVotes")
        @DisplayName("single applicable vote")
        void whenSingleApplicableThenReturnsItsDecisionAndOutcome(Decision decision, Outcome expectedOutcome) {
            val votes  = List.of(voteFor(decision));
            val result = UniqueVoteCombiner.combineMultipleVotes(votes, TEST_METADATA);
            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(decision);
                assertThat(r.outcome()).isEqualTo(expectedOutcome);
            });
        }

        @Test
        @DisplayName("single PERMIT among NOT_APPLICABLE returns PERMIT with all contributing votes")
        void whenSinglePermitAmongNotApplicableThenReturnsPermitWithAllContributingVotes() {
            val votes  = List.of(notApplicableVote("policy-1"), permitVote("policy-2"), notApplicableVote("policy-3"));
            val result = UniqueVoteCombiner.combineMultipleVotes(votes, TEST_METADATA);
            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
                assertThat(r.outcome()).isEqualTo(Outcome.PERMIT);
                assertThat(r.contributingVotes()).hasSize(3);
            });
        }

        @Test
        @DisplayName("single DENY among NOT_APPLICABLE returns DENY")
        void whenSingleDenyAmongNotApplicableThenReturnsDeny() {
            val votes  = List.of(notApplicableVote("policy-1"), notApplicableVote("policy-2"), denyVote("policy-3"));
            val result = UniqueVoteCombiner.combineMultipleVotes(votes, TEST_METADATA);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.DENY);
        }
    }

    @Nested
    @DisplayName("combineMultipleVotes - multiple applicable (collision)")
    class CombineMultipleCollisionTests {

        static Stream<Arguments> collisionScenarios() {
            return Stream.of(arguments("two PERMIT", List.of(voteFor(Decision.PERMIT), voteFor(Decision.PERMIT))),
                    arguments("two DENY", List.of(voteFor(Decision.DENY), voteFor(Decision.DENY))),
                    arguments("PERMIT and DENY", List.of(voteFor(Decision.PERMIT), voteFor(Decision.DENY))));
        }

        @ParameterizedTest(name = "{0} returns INDETERMINATE")
        @MethodSource("collisionScenarios")
        @DisplayName("multiple applicable returns INDETERMINATE")
        void whenMultipleApplicableThenReturnsIndeterminate(String scenario, List<Vote> votes) {
            val result = UniqueVoteCombiner.combineMultipleVotes(votes, TEST_METADATA);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("multiple applicable among NOT_APPLICABLE returns INDETERMINATE")
        void whenMultipleApplicableAmongNotApplicableThenReturnsIndeterminate() {
            val votes  = List.of(notApplicableVote("policy-1"), permitVote("policy-2"), notApplicableVote("policy-3"),
                    denyVote("policy-4"));
            val result = UniqueVoteCombiner.combineMultipleVotes(votes, TEST_METADATA);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("two PERMITs has PERMIT outcome and collision error")
        void whenTwoPermitsThenHasPermitOutcomeAndCollisionError() {
            val votes  = List.of(permitVote("policy-1"), permitVote("policy-2"));
            val result = UniqueVoteCombiner.combineMultipleVotes(votes, TEST_METADATA);
            assertThat(result).satisfies(r -> {
                assertThat(r.outcome()).isEqualTo(Outcome.PERMIT);
                assertThat(r.errors()).hasSize(1).first()
                        .satisfies(e -> assertThat(e.message()).contains("Multiple applicable policies"));
            });
        }

        @Test
        @DisplayName("PERMIT and DENY has PERMIT_OR_DENY outcome")
        void whenPermitAndDenyThenHasPermitOrDenyOutcome() {
            val votes  = List.of(permitVote("policy-1"), denyVote("policy-2"));
            val result = UniqueVoteCombiner.combineMultipleVotes(votes, TEST_METADATA);
            assertThat(result.outcome()).isEqualTo(Outcome.PERMIT_OR_DENY);
        }
    }

    @Nested
    @DisplayName("combineMultipleVotes - INDETERMINATE handling")
    class CombineMultipleIndeterminateTests {

        @Test
        @DisplayName("single INDETERMINATE returns INDETERMINATE preserving errors")
        void whenSingleIndeterminateThenReturnsIndeterminatePreservingErrors() {
            val votes  = List.of(indeterminateVote("policy-1", Outcome.PERMIT));
            val result = UniqueVoteCombiner.combineMultipleVotes(votes, TEST_METADATA);
            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
                assertThat(r.errors()).hasSize(1).first()
                        .satisfies(e -> assertThat(e.message()).isEqualTo("test error"));
            });
        }

        @Test
        @DisplayName("single INDETERMINATE among NOT_APPLICABLE returns INDETERMINATE preserving outcome")
        void whenSingleIndeterminateAmongNotApplicableThenReturnsIndeterminatePreservingOutcome() {
            val votes  = List.of(notApplicableVote("policy-1"), indeterminateVote("policy-2", Outcome.DENY),
                    notApplicableVote("policy-3"));
            val result = UniqueVoteCombiner.combineMultipleVotes(votes, TEST_METADATA);
            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
                assertThat(r.outcome()).isEqualTo(Outcome.DENY);
            });
        }

        @Test
        @DisplayName("INDETERMINATE short-circuits before seeing later votes")
        void whenIndeterminateFirstThenShortCircuits() {
            val votes  = List.of(indeterminateVote("policy-1", Outcome.PERMIT), permitVote("policy-2"));
            val result = UniqueVoteCombiner.combineMultipleVotes(votes, TEST_METADATA);
            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
                assertThat(r.outcome()).isEqualTo(Outcome.PERMIT);
            });
        }

        @Test
        @DisplayName("PERMIT followed by INDETERMINATE short-circuits on INDETERMINATE")
        void whenPermitThenIndeterminateThenShortCircuits() {
            val votes  = List.of(permitVote("policy-1"), indeterminateVote("policy-2", Outcome.DENY));
            val result = UniqueVoteCombiner.combineMultipleVotes(votes, TEST_METADATA);
            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
                assertThat(r.outcome()).isEqualTo(Outcome.DENY);
            });
        }

        @Test
        @DisplayName("first INDETERMINATE short-circuits immediately preserving its outcome")
        void whenFirstIndeterminateThenShortCircuitsImmediately() {
            val votes  = List.of(indeterminateVote("policy-1", Outcome.PERMIT),
                    indeterminateVote("policy-2", Outcome.DENY));
            val result = UniqueVoteCombiner.combineMultipleVotes(votes, TEST_METADATA);
            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
                assertThat(r.outcome()).isEqualTo(Outcome.PERMIT);
            });
        }
    }

    @Nested
    @DisplayName("combineMultipleVotes - contributing votes")
    class CombineMultipleContributingVotesTests {

        @Test
        @DisplayName("all input votes become contributing votes in order")
        void whenMultipleVotesThenAllBecomeContributingVotesInOrder() {
            val votes  = List.of(notApplicableVote("policy-1"), permitVote("policy-2"), notApplicableVote("policy-3"));
            val result = UniqueVoteCombiner.combineMultipleVotes(votes, TEST_METADATA);
            assertThat(result.contributingVotes()).hasSize(3).extracting(v -> v.voter().name())
                    .containsExactly("policy-1", "policy-2", "policy-3");
        }

        @Test
        @DisplayName("uses provided voter metadata")
        void whenProvidedMetadataThenUsesIt() {
            val metadata = testMetadata("combined-result", Outcome.PERMIT);
            val votes    = List.of(permitVote("policy-1"));
            val result   = UniqueVoteCombiner.combineMultipleVotes(votes, metadata);
            assertThat(result.voter().name()).isEqualTo("combined-result");
        }
    }
}
