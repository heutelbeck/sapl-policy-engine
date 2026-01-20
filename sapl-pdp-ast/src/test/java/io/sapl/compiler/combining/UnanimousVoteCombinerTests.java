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

@DisplayName("UnanimousVoteCombiner")
class UnanimousVoteCombinerTests {

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

    static Vote permitVoteWithObligations(String name, Value... obligations) {
        val obligationsArray = ArrayValue.builder();
        for (var o : obligations) {
            obligationsArray.add(o);
        }
        return Vote.tracedVote(Decision.PERMIT, obligationsArray.build(), Value.EMPTY_ARRAY, Value.UNDEFINED,
                testMetadata(name, Outcome.PERMIT), List.of());
    }

    static Vote permitVoteWithAdvice(String name, Value... advice) {
        val adviceArray = ArrayValue.builder();
        for (var a : advice) {
            adviceArray.add(a);
        }
        return Vote.tracedVote(Decision.PERMIT, Value.EMPTY_ARRAY, adviceArray.build(), Value.UNDEFINED,
                testMetadata(name, Outcome.PERMIT), List.of());
    }

    static Vote permitVoteWithResource(String name, Value resource) {
        return Vote.tracedVote(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, resource,
                testMetadata(name, Outcome.PERMIT), List.of());
    }

    static Vote denyVote(String name) {
        return Vote.tracedVote(Decision.DENY, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED,
                testMetadata(name, Outcome.DENY), List.of());
    }

    static Vote denyVoteWithObligations(String name, Value... obligations) {
        val obligationsArray = ArrayValue.builder();
        for (var o : obligations) {
            obligationsArray.add(o);
        }
        return Vote.tracedVote(Decision.DENY, obligationsArray.build(), Value.EMPTY_ARRAY, Value.UNDEFINED,
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
            val accumulator = UnanimousVoteCombiner.accumulatorVoteFrom(vote, TEST_METADATA);
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
            val accumulator = UnanimousVoteCombiner.accumulatorVoteFrom(vote, TEST_METADATA);
            assertThat(accumulator.errors()).hasSize(1).first()
                    .satisfies(e -> assertThat(e.message()).isEqualTo("test error"));
        }
    }

    @Nested
    @DisplayName("combineVotes pairwise - normal mode")
    class CombineVotesPairwiseNormalModeTests {

        static Stream<Arguments> pairwiseCombinationDecisionTable() {
            return Stream.of(arguments(Decision.NOT_APPLICABLE, Decision.NOT_APPLICABLE, Decision.NOT_APPLICABLE),
                    arguments(Decision.NOT_APPLICABLE, Decision.PERMIT, Decision.PERMIT),
                    arguments(Decision.NOT_APPLICABLE, Decision.DENY, Decision.DENY),
                    arguments(Decision.NOT_APPLICABLE, Decision.INDETERMINATE, Decision.INDETERMINATE),
                    arguments(Decision.PERMIT, Decision.NOT_APPLICABLE, Decision.PERMIT),
                    arguments(Decision.PERMIT, Decision.PERMIT, Decision.PERMIT),
                    arguments(Decision.PERMIT, Decision.DENY, Decision.INDETERMINATE),
                    arguments(Decision.PERMIT, Decision.INDETERMINATE, Decision.INDETERMINATE),
                    arguments(Decision.DENY, Decision.NOT_APPLICABLE, Decision.DENY),
                    arguments(Decision.DENY, Decision.PERMIT, Decision.INDETERMINATE),
                    arguments(Decision.DENY, Decision.DENY, Decision.DENY),
                    arguments(Decision.DENY, Decision.INDETERMINATE, Decision.INDETERMINATE),
                    arguments(Decision.INDETERMINATE, Decision.NOT_APPLICABLE, Decision.INDETERMINATE),
                    arguments(Decision.INDETERMINATE, Decision.PERMIT, Decision.INDETERMINATE),
                    arguments(Decision.INDETERMINATE, Decision.DENY, Decision.INDETERMINATE),
                    arguments(Decision.INDETERMINATE, Decision.INDETERMINATE, Decision.INDETERMINATE));
        }

        static Vote voteFor(Decision decision) {
            return switch (decision) {
            case PERMIT         -> permitVote("p");
            case DENY           -> denyVote("p");
            case NOT_APPLICABLE -> notApplicableVote("p");
            case INDETERMINATE  -> indeterminateVote("p", Outcome.PERMIT);
            };
        }

        @ParameterizedTest(name = "{0} + {1} = {2}")
        @MethodSource("pairwiseCombinationDecisionTable")
        @DisplayName("pairwise combination")
        void pairwiseCombination(Decision accDecision, Decision newDecision, Decision expectedDecision) {
            val acc    = UnanimousVoteCombiner.accumulatorVoteFrom(voteFor(accDecision), TEST_METADATA);
            val result = UnanimousVoteCombiner.combineVotes(acc, voteFor(newDecision), TEST_METADATA, false);
            assertThat(result.authorizationDecision().decision()).isEqualTo(expectedDecision);
        }

        @Test
        @DisplayName("disagreement creates error with message")
        void disagreementCreatesErrorWithMessage() {
            val acc    = UnanimousVoteCombiner.accumulatorVoteFrom(permitVote("p1"), TEST_METADATA);
            val result = UnanimousVoteCombiner.combineVotes(acc, denyVote("p2"), TEST_METADATA, false);
            assertThat(result.errors()).hasSize(1).first().satisfies(e -> assertThat(e.message()).contains("disagree"));
        }

        @Test
        @DisplayName("disagreement has PERMIT_OR_DENY outcome")
        void disagreementHasPermitOrDenyOutcome() {
            val acc    = UnanimousVoteCombiner.accumulatorVoteFrom(permitVote("p1"), TEST_METADATA);
            val result = UnanimousVoteCombiner.combineVotes(acc, denyVote("p2"), TEST_METADATA, false);
            assertThat(result.outcome()).isEqualTo(Outcome.PERMIT_OR_DENY);
        }

        @Test
        @DisplayName("appends to contributing votes")
        void appendsToContributingVotes() {
            val acc    = UnanimousVoteCombiner.accumulatorVoteFrom(permitVote("p1"), TEST_METADATA);
            val result = UnanimousVoteCombiner.combineVotes(acc, notApplicableVote("p2"), TEST_METADATA, false);
            assertThat(result.contributingVotes()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("combineVotes - constraint merging (normal mode)")
    class CombineVotesConstraintMergingTests {

        @Test
        @DisplayName("merges obligations from both votes")
        void mergesObligations() {
            val ob1    = Value.of("{\"action\":\"log\"}");
            val ob2    = Value.of("{\"action\":\"notify\"}");
            val acc    = UnanimousVoteCombiner.accumulatorVoteFrom(permitVoteWithObligations("p1", ob1), TEST_METADATA);
            val result = UnanimousVoteCombiner.combineVotes(acc, permitVoteWithObligations("p2", ob2), TEST_METADATA,
                    false);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
            assertThat(result.authorizationDecision().obligations().size()).isEqualTo(2);
        }

        @Test
        @DisplayName("merges advice from both votes")
        void mergesAdvice() {
            val adv1   = Value.of("{\"info\":\"a\"}");
            val adv2   = Value.of("{\"info\":\"b\"}");
            val acc    = UnanimousVoteCombiner.accumulatorVoteFrom(permitVoteWithAdvice("p1", adv1), TEST_METADATA);
            val result = UnanimousVoteCombiner.combineVotes(acc, permitVoteWithAdvice("p2", adv2), TEST_METADATA,
                    false);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
            assertThat(result.authorizationDecision().advice().size()).isEqualTo(2);
        }

        @Test
        @DisplayName("takes resource from first vote when second is undefined")
        void takesResourceFromFirstWhenSecondUndefined() {
            val resource = Value.of("{\"transformed\":true}");
            val acc      = UnanimousVoteCombiner.accumulatorVoteFrom(permitVoteWithResource("p1", resource),
                    TEST_METADATA);
            val result   = UnanimousVoteCombiner.combineVotes(acc, permitVote("p2"), TEST_METADATA, false);
            assertThat(result.authorizationDecision().resource()).isEqualTo(resource);
        }

        @Test
        @DisplayName("takes resource from second vote when first is undefined")
        void takesResourceFromSecondWhenFirstUndefined() {
            val resource = Value.of("{\"transformed\":true}");
            val acc      = UnanimousVoteCombiner.accumulatorVoteFrom(permitVote("p1"), TEST_METADATA);
            val result   = UnanimousVoteCombiner.combineVotes(acc, permitVoteWithResource("p2", resource),
                    TEST_METADATA, false);
            assertThat(result.authorizationDecision().resource()).isEqualTo(resource);
        }

        @Test
        @DisplayName("transformation uncertainty when both define different resources")
        void transformationUncertaintyWhenBothDefineDifferentResources() {
            val resource1 = Value.of("{\"version\":1}");
            val resource2 = Value.of("{\"version\":2}");
            val acc       = UnanimousVoteCombiner.accumulatorVoteFrom(permitVoteWithResource("p1", resource1),
                    TEST_METADATA);
            val result    = UnanimousVoteCombiner.combineVotes(acc, permitVoteWithResource("p2", resource2),
                    TEST_METADATA, false);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
            assertThat(result.errors()).hasSize(1).first()
                    .satisfies(e -> assertThat(e.message()).contains("Transformation uncertainty"));
        }

        @Test
        @DisplayName("transformation uncertainty preserves outcome based on agreed decision")
        void transformationUncertaintyPreservesOutcome() {
            val resource1 = Value.of("{\"version\":1}");
            val resource2 = Value.of("{\"version\":2}");
            val acc       = UnanimousVoteCombiner.accumulatorVoteFrom(permitVoteWithResource("p1", resource1),
                    TEST_METADATA);
            val result    = UnanimousVoteCombiner.combineVotes(acc, permitVoteWithResource("p2", resource2),
                    TEST_METADATA, false);
            assertThat(result.outcome()).isEqualTo(Outcome.PERMIT);
        }

        @Test
        @DisplayName("identical resources are allowed")
        void identicalResourcesAllowed() {
            val resource = Value.of("{\"version\":1}");
            val acc      = UnanimousVoteCombiner.accumulatorVoteFrom(permitVoteWithResource("p1", resource),
                    TEST_METADATA);
            val result   = UnanimousVoteCombiner.combineVotes(acc, permitVoteWithResource("p2", resource),
                    TEST_METADATA, false);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
            assertThat(result.authorizationDecision().resource()).isEqualTo(resource);
        }
    }

    @Nested
    @DisplayName("combineVotes - strict mode")
    class CombineVotesStrictModeTests {

        @Test
        @DisplayName("identical votes return that vote")
        void identicalVotesReturnThatVote() {
            val acc    = UnanimousVoteCombiner.accumulatorVoteFrom(permitVote("p1"), TEST_METADATA);
            val result = UnanimousVoteCombiner.combineVotes(acc, permitVote("p2"), TEST_METADATA, true);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("different decisions return INDETERMINATE")
        void differentDecisionsReturnIndeterminate() {
            val acc    = UnanimousVoteCombiner.accumulatorVoteFrom(permitVote("p1"), TEST_METADATA);
            val result = UnanimousVoteCombiner.combineVotes(acc, denyVote("p2"), TEST_METADATA, true);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
            assertThat(result.outcome()).isEqualTo(Outcome.PERMIT_OR_DENY);
        }

        @Test
        @DisplayName("different obligations return INDETERMINATE")
        void differentObligationsReturnIndeterminate() {
            val ob1    = Value.of("{\"action\":\"log\"}");
            val ob2    = Value.of("{\"action\":\"notify\"}");
            val acc    = UnanimousVoteCombiner.accumulatorVoteFrom(permitVoteWithObligations("p1", ob1), TEST_METADATA);
            val result = UnanimousVoteCombiner.combineVotes(acc, permitVoteWithObligations("p2", ob2), TEST_METADATA,
                    true);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
            assertThat(result.outcome()).isEqualTo(Outcome.PERMIT);
        }

        @Test
        @DisplayName("different advice return INDETERMINATE")
        void differentAdviceReturnIndeterminate() {
            val adv1   = Value.of("{\"info\":\"a\"}");
            val adv2   = Value.of("{\"info\":\"b\"}");
            val acc    = UnanimousVoteCombiner.accumulatorVoteFrom(permitVoteWithAdvice("p1", adv1), TEST_METADATA);
            val result = UnanimousVoteCombiner.combineVotes(acc, permitVoteWithAdvice("p2", adv2), TEST_METADATA, true);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("different resources return INDETERMINATE")
        void differentResourcesReturnIndeterminate() {
            val resource1 = Value.of("{\"version\":1}");
            val resource2 = Value.of("{\"version\":2}");
            val acc       = UnanimousVoteCombiner.accumulatorVoteFrom(permitVoteWithResource("p1", resource1),
                    TEST_METADATA);
            val result    = UnanimousVoteCombiner.combineVotes(acc, permitVoteWithResource("p2", resource2),
                    TEST_METADATA, true);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("strict mode error message indicates not identical")
        void strictModeErrorMessageIndicatesNotIdentical() {
            val ob1    = Value.of("{\"action\":\"log\"}");
            val acc    = UnanimousVoteCombiner.accumulatorVoteFrom(permitVoteWithObligations("p1", ob1), TEST_METADATA);
            val result = UnanimousVoteCombiner.combineVotes(acc, permitVote("p2"), TEST_METADATA, true);
            assertThat(result.errors()).hasSize(1).first()
                    .satisfies(e -> assertThat(e.message()).contains("not identical"));
        }

        @Test
        @DisplayName("identical votes with same constraints return that vote")
        void identicalVotesWithSameConstraintsReturnThatVote() {
            val ob       = ArrayValue.builder().add(Value.of("{\"action\":\"log\"}")).build();
            val adv      = ArrayValue.builder().add(Value.of("{\"info\":\"a\"}")).build();
            val resource = Value.of("{\"data\":true}");
            val vote1    = Vote.tracedVote(Decision.PERMIT, ob, adv, resource, testMetadata("p1", Outcome.PERMIT),
                    List.of());
            val vote2    = Vote.tracedVote(Decision.PERMIT, ob, adv, resource, testMetadata("p2", Outcome.PERMIT),
                    List.of());
            val acc      = UnanimousVoteCombiner.accumulatorVoteFrom(vote1, TEST_METADATA);
            val result   = UnanimousVoteCombiner.combineVotes(acc, vote2, TEST_METADATA, true);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
            assertThat(result.authorizationDecision().obligations()).isEqualTo(ob);
        }
    }

    @Nested
    @DisplayName("combineMultipleVotes - basic scenarios")
    class CombineMultipleBasicTests {

        @Test
        @DisplayName("empty list returns NOT_APPLICABLE")
        void whenEmptyListThenReturnsNotApplicable() {
            val result = UnanimousVoteCombiner.combineMultipleVotes(List.of(), TEST_METADATA, false);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
            assertThat(result.contributingVotes()).isEmpty();
        }

        @Test
        @DisplayName("single vote returns that vote")
        void whenSingleVoteThenReturnsThatVote() {
            val result = UnanimousVoteCombiner.combineMultipleVotes(List.of(permitVote("p1")), TEST_METADATA, false);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("all NOT_APPLICABLE returns NOT_APPLICABLE")
        void whenAllNotApplicableThenReturnsNotApplicable() {
            val votes  = List.of(notApplicableVote("p1"), notApplicableVote("p2"));
            val result = UnanimousVoteCombiner.combineMultipleVotes(votes, TEST_METADATA, false);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
        }
    }

    @Nested
    @DisplayName("combineMultipleVotes - agreement scenarios (normal mode)")
    class CombineMultipleAgreementTests {

        @Test
        @DisplayName("all PERMIT returns PERMIT with merged constraints")
        void whenAllPermitThenReturnsPermitWithMergedConstraints() {
            val ob1    = Value.of("{\"action\":\"log\"}");
            val ob2    = Value.of("{\"action\":\"notify\"}");
            val votes  = List.of(permitVoteWithObligations("p1", ob1), permitVoteWithObligations("p2", ob2));
            val result = UnanimousVoteCombiner.combineMultipleVotes(votes, TEST_METADATA, false);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
            assertThat(result.authorizationDecision().obligations().size()).isEqualTo(2);
        }

        @Test
        @DisplayName("all DENY returns DENY with merged constraints")
        void whenAllDenyThenReturnsDenyWithMergedConstraints() {
            val ob1    = Value.of("{\"action\":\"log\"}");
            val ob2    = Value.of("{\"action\":\"notify\"}");
            val votes  = List.of(denyVoteWithObligations("p1", ob1), denyVoteWithObligations("p2", ob2));
            val result = UnanimousVoteCombiner.combineMultipleVotes(votes, TEST_METADATA, false);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.DENY);
            assertThat(result.authorizationDecision().obligations().size()).isEqualTo(2);
        }

        @Test
        @DisplayName("PERMIT among NOT_APPLICABLE returns PERMIT")
        void whenPermitAmongNotApplicableThenReturnsPermit() {
            val votes  = List.of(notApplicableVote("p1"), permitVote("p2"), notApplicableVote("p3"));
            val result = UnanimousVoteCombiner.combineMultipleVotes(votes, TEST_METADATA, false);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        }
    }

    @Nested
    @DisplayName("combineMultipleVotes - disagreement scenarios")
    class CombineMultipleDisagreementTests {

        @Test
        @DisplayName("PERMIT and DENY returns INDETERMINATE")
        void whenPermitAndDenyThenReturnsIndeterminate() {
            val votes  = List.of(permitVote("p1"), denyVote("p2"));
            val result = UnanimousVoteCombiner.combineMultipleVotes(votes, TEST_METADATA, false);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
            assertThat(result.outcome()).isEqualTo(Outcome.PERMIT_OR_DENY);
        }

        @Test
        @DisplayName("disagreement among NOT_APPLICABLE returns INDETERMINATE")
        void whenDisagreementAmongNotApplicableThenReturnsIndeterminate() {
            val votes  = List.of(notApplicableVote("p1"), permitVote("p2"), denyVote("p3"));
            val result = UnanimousVoteCombiner.combineMultipleVotes(votes, TEST_METADATA, false);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("short-circuits on disagreement in normal mode")
        void whenDisagreementThenShortCircuitsInNormalMode() {
            val votes  = List.of(permitVote("p1"), denyVote("p2"), permitVote("p3"));
            val result = UnanimousVoteCombiner.combineMultipleVotes(votes, TEST_METADATA, false);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
            // Short-circuit means we stop after disagreement
            assertThat(result.contributingVotes()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("combineMultipleVotes - strict mode")
    class CombineMultipleStrictModeTests {

        @Test
        @DisplayName("identical votes return that vote")
        void whenIdenticalVotesThenReturnsThatVote() {
            val votes  = List.of(permitVote("p1"), permitVote("p2"), permitVote("p3"));
            val result = UnanimousVoteCombiner.combineMultipleVotes(votes, TEST_METADATA, true);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("different constraints return INDETERMINATE")
        void whenDifferentConstraintsThenReturnsIndeterminate() {
            val ob1    = Value.of("{\"action\":\"log\"}");
            val ob2    = Value.of("{\"action\":\"notify\"}");
            val votes  = List.of(permitVoteWithObligations("p1", ob1), permitVoteWithObligations("p2", ob2));
            val result = UnanimousVoteCombiner.combineMultipleVotes(votes, TEST_METADATA, true);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("short-circuits on any difference in strict mode")
        void whenAnyDifferenceThenShortCircuitsInStrictMode() {
            val ob     = Value.of("{\"action\":\"log\"}");
            val votes  = List.of(permitVote("p1"), permitVoteWithObligations("p2", ob), permitVote("p3"));
            val result = UnanimousVoteCombiner.combineMultipleVotes(votes, TEST_METADATA, true);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
            assertThat(result.contributingVotes()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("combineMultipleVotes - INDETERMINATE handling")
    class CombineMultipleIndeterminateTests {

        @Test
        @DisplayName("INDETERMINATE propagates")
        void whenIndeterminateThenPropagates() {
            val votes  = List.of(permitVote("p1"), indeterminateVote("p2", Outcome.PERMIT));
            val result = UnanimousVoteCombiner.combineMultipleVotes(votes, TEST_METADATA, false);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("first INDETERMINATE short-circuits")
        void whenFirstIndeterminateThenShortCircuits() {
            val votes  = List.of(indeterminateVote("p1", Outcome.PERMIT), permitVote("p2"));
            val result = UnanimousVoteCombiner.combineMultipleVotes(votes, TEST_METADATA, false);
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }
    }

    @Nested
    @DisplayName("combineMultipleVotes - contributing votes")
    class CombineMultipleContributingVotesTests {

        @Test
        @DisplayName("all input votes become contributing votes in order")
        void whenMultipleVotesThenAllBecomeContributingVotesInOrder() {
            val votes  = List.of(notApplicableVote("p1"), permitVote("p2"), notApplicableVote("p3"));
            val result = UnanimousVoteCombiner.combineMultipleVotes(votes, TEST_METADATA, false);
            assertThat(result.contributingVotes()).hasSize(3).extracting(v -> v.voter().name()).containsExactly("p1",
                    "p2", "p3");
        }

        @Test
        @DisplayName("uses provided voter metadata")
        void whenProvidedMetadataThenUsesIt() {
            val metadata = testMetadata("combined-result", Outcome.PERMIT);
            val result   = UnanimousVoteCombiner.combineMultipleVotes(List.of(permitVote("p1")), metadata, false);
            assertThat(result.voter().name()).isEqualTo("combined-result");
        }
    }

}
