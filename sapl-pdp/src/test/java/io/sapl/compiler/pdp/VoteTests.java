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
package io.sapl.compiler.pdp;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.Outcome;
import io.sapl.ast.PolicySetVoterMetadata;
import io.sapl.ast.PolicyVoterMetadata;
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

@DisplayName("Vote")
class VoteTests {

    private static final CombiningAlgorithm DENY_OVERRIDES = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.ABSTAIN, ErrorHandling.PROPAGATE);

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("combinedVote creates vote with contributing votes")
        void combinedVoteCreatesVoteWithContributingVotes() {
            val voter      = policySetVoter("test-set");
            val childVoter = policyVoter("child-policy");
            val childVote  = Vote.abstain(childVoter);
            val vote       = Vote.combinedVote(AuthorizationDecision.PERMIT, voter, List.of(childVote), Outcome.PERMIT);

            assertThat(vote).satisfies(v -> {
                assertThat(v.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
                assertThat(v.contributingVotes()).containsExactly(childVote);
                assertThat(v.errors()).isEmpty();
                assertThat(v.contributingAttributes()).isEmpty();
            });
        }

        @Test
        @DisplayName("abstain creates NOT_APPLICABLE vote")
        void abstainCreatesNotApplicableVote() {
            val voter = policyVoter("test-policy");
            val vote  = Vote.abstain(voter);

            assertThat(vote).satisfies(v -> {
                assertThat(v.authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
                assertThat(v.voter()).isEqualTo(voter);
            });
        }

        @Test
        @DisplayName("error creates INDETERMINATE vote with error")
        void errorCreatesIndeterminateVoteWithError() {
            val voter = policyVoter("test-policy");
            val error = Value.error("test error");
            val vote  = Vote.error(error, voter);

            assertThat(vote).satisfies(v -> {
                assertThat(v.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
                assertThat(v.errors()).containsExactly(error);
            });
        }
    }

    @Nested
    @DisplayName("finalizeVote")
    class FinalizeVote {

        static Stream<Arguments> defaultDecisionCases() {
            return Stream.of(arguments(DefaultDecision.DENY, Decision.DENY),
                    arguments(DefaultDecision.PERMIT, Decision.PERMIT),
                    arguments(DefaultDecision.ABSTAIN, Decision.NOT_APPLICABLE));
        }

        @ParameterizedTest(name = "NOT_APPLICABLE with {0} default becomes {1}")
        @MethodSource("defaultDecisionCases")
        void notApplicableWithDefaultDecision(DefaultDecision defaultDecision, Decision expected) {
            val vote      = Vote.abstain(policyVoter("test-policy"));
            val finalized = vote.finalizeVote(defaultDecision, ErrorHandling.PROPAGATE);

            assertThat(finalized.authorizationDecision().decision()).isEqualTo(expected);
        }

        static Stream<Arguments> errorHandlingCases() {
            return Stream.of(arguments(ErrorHandling.ABSTAIN, Decision.NOT_APPLICABLE),
                    arguments(ErrorHandling.PROPAGATE, Decision.INDETERMINATE));
        }

        @ParameterizedTest(name = "INDETERMINATE with {0} error handling becomes {1}")
        @MethodSource("errorHandlingCases")
        void indeterminateWithErrorHandling(ErrorHandling errorHandling, Decision expected) {
            val vote      = Vote.error(Value.error("error"), policyVoter("test-policy"));
            val finalized = vote.finalizeVote(DefaultDecision.ABSTAIN, errorHandling);

            assertThat(finalized.authorizationDecision().decision()).isEqualTo(expected);
        }

        @Test
        @DisplayName("PERMIT vote is unchanged by finalization")
        void permitVoteUnchangedByFinalization() {
            val voter     = policyVoter("test-policy");
            val vote      = Vote.tracedVote(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED,
                    voter, List.of());
            val finalized = vote.finalizeVote(DefaultDecision.DENY, ErrorHandling.PROPAGATE);

            assertThat(finalized.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        }
    }

    @Nested
    @DisplayName("toTrace")
    class ToTrace {

        @Test
        @DisplayName("includes decision and voter info")
        void includesDecisionAndVoterInfo() {
            val voter = policySetVoter("test-set");
            val vote  = Vote.abstain(voter);
            val trace = vote.toTrace();

            assertThat(trace).satisfies(t -> {
                assertThat(t.get("decision")).isEqualTo(Value.of("NOT_APPLICABLE"));
                assertThat(t.get("voter")).isInstanceOf(ObjectValue.class);
                assertThat(t.get("outcome")).isEqualTo(Value.of("PERMIT"));
            });
        }

        @Test
        @DisplayName("includes errors when present")
        void includesErrorsWhenPresent() {
            val vote  = Vote.error(Value.error("test error"), policyVoter("test-policy"));
            val trace = vote.toTrace();

            assertThat(trace.get("errors")).isInstanceOf(ArrayValue.class);
        }

        @Test
        @DisplayName("includes contributing votes recursively")
        void includesContributingVotesRecursively() {
            val childVote = Vote.abstain(policyVoter("child-policy"));
            val vote      = Vote.combinedVote(AuthorizationDecision.PERMIT, policySetVoter("parent-set"),
                    List.of(childVote), Outcome.PERMIT);
            val trace     = vote.toTrace();

            assertThat(trace.get("contributingVotes")).isInstanceOf(ArrayValue.class);
        }

        @Test
        @DisplayName("voter trace includes documentId when present")
        void voterTraceIncludesDocumentIdWhenPresent() {
            val voter     = new PolicyVoterMetadata("test-policy", "pdp", "config", "doc-123", Outcome.PERMIT, false);
            val vote      = Vote.abstain(voter);
            val trace     = vote.toTrace();
            val voterInfo = (ObjectValue) trace.get("voter");

            assertThat(voterInfo.get("documentId")).isEqualTo(Value.of("doc-123"));
        }

        @Test
        @DisplayName("voter trace includes algorithm for policy sets")
        void voterTraceIncludesAlgorithmForPolicySets() {
            val voter     = new PolicySetVoterMetadata("test-set", "pdp", "config", null, DENY_OVERRIDES,
                    Outcome.PERMIT, false);
            val vote      = Vote.abstain(voter);
            val trace     = vote.toTrace();
            val voterInfo = (ObjectValue) trace.get("voter");

            assertThat(voterInfo.get("algorithm")).isInstanceOf(ObjectValue.class);
        }
    }

    @Nested
    @DisplayName("withVote")
    class WithVote {

        @Test
        @DisplayName("adds new contributing vote and preserves immutability")
        void addsNewContributingVoteAndPreservesImmutability() {
            val vote    = Vote.abstain(policySetVoter("test-set"));
            val newVote = Vote.abstain(policyVoter("new-policy"));
            val result  = vote.withVote(newVote);

            assertThat(result.contributingVotes()).containsExactly(newVote);
            assertThat(vote.contributingVotes()).isEmpty();
        }

        @Test
        @DisplayName("preserves existing contributing votes")
        void preservesExistingContributingVotes() {
            val existing = Vote.abstain(policyVoter("existing"));
            val vote     = Vote.combinedVote(AuthorizationDecision.PERMIT, policySetVoter("test-set"),
                    List.of(existing), Outcome.PERMIT);
            val newVote  = Vote.abstain(policyVoter("new"));
            val result   = vote.withVote(newVote);

            assertThat(result.contributingVotes()).containsExactly(existing, newVote);
        }
    }

    @Nested
    @DisplayName("replaceDecision")
    class ReplaceDecision {

        @Test
        @DisplayName("replaces decision while preserving other fields")
        void replacesDecisionWhilePreservingOtherFields() {
            val voter    = policyVoter("test-policy");
            val vote     = Vote.abstain(voter);
            val replaced = vote.replaceDecision(Decision.DENY, Outcome.DENY);

            assertThat(replaced).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.DENY);
                assertThat(r.voter()).isEqualTo(voter);
                assertThat(r.outcome()).isEqualTo(Outcome.DENY);
            });
        }
    }

    private static PolicyVoterMetadata policyVoter(String name) {
        return new PolicyVoterMetadata(name, "pdp", "config", null, Outcome.PERMIT, false);
    }

    private static PolicySetVoterMetadata policySetVoter(String name) {
        return new PolicySetVoterMetadata(name, "pdp", "config", null, DENY_OVERRIDES, Outcome.PERMIT, false);
    }
}
