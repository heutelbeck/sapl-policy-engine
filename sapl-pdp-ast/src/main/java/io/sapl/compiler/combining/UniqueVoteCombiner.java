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

import static io.sapl.compiler.combining.CombiningUtils.appendToList;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.Outcome;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.pdp.Vote;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.List;

/**
 * Combines multiple policy votes using unique (only-one-applicable) semantics.
 * <p>
 * The unique combining algorithm requires exactly one policy to be applicable.
 * This enforces mutual exclusivity in policy configuration:
 * <ul>
 * <li><b>Zero applicable:</b> Returns NOT_APPLICABLE</li>
 * <li><b>One applicable:</b> Returns that policy's vote</li>
 * <li><b>Multiple applicable:</b> Returns INDETERMINATE (configuration
 * error)</li>
 * </ul>
 * <p>
 * A vote is considered "applicable" if it is not NOT_APPLICABLE. This includes
 * INDETERMINATE votes since they represent uncertainty - we cannot confirm the
 * policy would NOT have been the unique match.
 * <p>
 * Unlike priority-based algorithms, outcome tracking does not influence the
 * combining logic here. Outcomes are preserved for tracing only.
 */
@UtilityClass
public class UniqueVoteCombiner {

    private static final String ERROR_MULTIPLE_APPLICABLE = "Multiple applicable policies found during combining votes using the unique combining algorithm.";

    /**
     * Combines multiple votes into a single vote using unique semantics.
     * <p>
     * Short-circuits on INDETERMINATE since uniqueness cannot be determined.
     *
     * @param votes the votes to combine (maybe empty)
     * @param voterMetadata metadata for the combined vote
     * @return combined vote, or abstain if input is empty
     */
    public static Vote combineMultipleVotes(List<Vote> votes, VoterMetadata voterMetadata) {
        if (votes.isEmpty()) {
            return Vote.abstain(voterMetadata);
        }
        var accumulatorVote = accumulatorVoteFrom(votes.getFirst(), voterMetadata);
        for (val vote : votes.subList(1, votes.size())) {
            // Short-circuit on INDETERMINATE - uniqueness cannot be determined
            if (accumulatorVote.authorizationDecision().decision() == Decision.INDETERMINATE) {
                break;
            }
            accumulatorVote = combineVotes(accumulatorVote, vote, voterMetadata);
        }
        return accumulatorVote;
    }

    /**
     * Wraps a single vote as an accumulator for fold operations.
     * <p>
     * The original vote becomes the sole entry in the contributing votes list.
     *
     * @param vote the vote to wrap
     * @param voterMetadata metadata for the accumulator
     * @return accumulator vote containing the original as a contributing vote
     */
    public static Vote accumulatorVoteFrom(Vote vote, VoterMetadata voterMetadata) {
        return new Vote(vote.authorizationDecision(), vote.errors(), vote.contributingAttributes(), List.of(vote),
                voterMetadata, vote.outcome());
    }

    /**
     * Combines two votes according to unique semantics.
     * <p>
     * Any INDETERMINATE immediately propagates. For concrete votes:
     *
     * <pre>
     * Accumulator      | New Vote        | Result
     * -----------------|-----------------|------------------
     * NOT_APPLICABLE   | NOT_APPLICABLE  | NOT_APPLICABLE
     * NOT_APPLICABLE   | PERMIT/DENY     | new vote's decision
     * PERMIT/DENY      | NOT_APPLICABLE  | accumulator's decision
     * PERMIT/DENY      | PERMIT/DENY     | INDETERMINATE (collision)
     * </pre>
     *
     * @param accumulatorVote the accumulated result
     * @param newVote the new vote to incorporate
     * @param voterMetadata metadata for the combined result
     * @return the combined vote
     */
    public static Vote combineVotes(Vote accumulatorVote, Vote newVote, VoterMetadata voterMetadata) {
        val accDec = accumulatorVote.authorizationDecision().decision();
        val newDec = newVote.authorizationDecision().decision();

        val contributingVotes = appendToList(accumulatorVote.contributingVotes(), newVote);

        // INDETERMINATE propagates (first one wins)
        if (accDec == Decision.INDETERMINATE || newDec == Decision.INDETERMINATE) {
            val source = accDec == Decision.INDETERMINATE ? accumulatorVote : newVote;
            return indeterminateResult(source.outcome(), source.errors(), contributingVotes, voterMetadata);
        }

        // Both NOT_APPLICABLE
        if (accDec == Decision.NOT_APPLICABLE && newDec == Decision.NOT_APPLICABLE) {
            return Vote.abstain(voterMetadata, contributingVotes);
        }

        // Exactly one applicable - take it
        if (accDec == Decision.NOT_APPLICABLE) {
            return Vote.combinedVote(newVote.authorizationDecision(), voterMetadata, contributingVotes,
                    newVote.outcome());
        }
        if (newDec == Decision.NOT_APPLICABLE) {
            return Vote.combinedVote(accumulatorVote.authorizationDecision(), voterMetadata, contributingVotes,
                    accumulatorVote.outcome());
        }

        // Both applicable - collision!
        val collisionError = Value.error(ERROR_MULTIPLE_APPLICABLE);
        val outcome        = (accDec == newDec) ? (accDec == Decision.PERMIT ? Outcome.PERMIT : Outcome.DENY)
                : Outcome.PERMIT_OR_DENY;
        return indeterminateResult(outcome, List.of(collisionError), contributingVotes, voterMetadata);
    }

    private static Vote indeterminateResult(Outcome outcome, List<ErrorValue> errors, List<Vote> contributingVotes,
            VoterMetadata voterMetadata) {
        return new Vote(AuthorizationDecision.INDETERMINATE, errors, List.of(), contributingVotes, voterMetadata,
                outcome);
    }

}
