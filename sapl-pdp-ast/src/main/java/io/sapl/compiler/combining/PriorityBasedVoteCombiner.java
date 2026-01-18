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
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.compiler.pdp.Vote;
import io.sapl.ast.VoterMetadata;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

/**
 * Combines multiple policy votes into a single authorization decision using
 * priority-based logic.
 * <p>
 * Decision priority cascade:
 * <ol>
 * <li>INDETERMINATE always wins (secure-by-default: errors invalidate all other
 * decisions)</li>
 * <li>Any definitive decision (PERMIT/DENY) wins over NOT_APPLICABLE</li>
 * <li>Same decisions (PERMIT/PERMIT or DENY/DENY) merge their constraints</li>
 * <li>Conflicting decisions (PERMIT vs DENY) resolve by the configured priority
 * decision</li>
 * </ol>
 * <p>
 * Transformation uncertainty: If multiple votes define resource
 * transformations, the result
 * is INDETERMINATE since the correct transformation cannot be determined.
 */
@UtilityClass
public class PriorityBasedVoteCombiner {

    /**
     * Combines multiple votes into a single vote using priority-based logic.
     * <p>
     * Short-circuits on INDETERMINATE to avoid unnecessary evaluation.
     *
     * @param foldableVotes the votes to combine (may be empty)
     * @param priorityDecision the decision that wins when PERMIT conflicts with
     * DENY
     * @param voterMetadata metadata for the combined vote
     * @return combined vote, or abstain if input is empty
     */
    public static Vote combineMultipleVotes(ArrayList<Vote> foldableVotes, Decision priorityDecision,
            VoterMetadata voterMetadata) {
        if (foldableVotes.isEmpty()) {
            return Vote.abstain(voterMetadata);
        }
        var accumulatorVote = accumulatorVoteFrom(foldableVotes.getFirst(), voterMetadata);
        for (var i = 1; i < foldableVotes.size(); i++) {
            if (accumulatorVote.authorizationDecision().decision() == Decision.INDETERMINATE) {
                // Short circuit on errors. Don't even bother to look at the next vote.
                break;
            }
            accumulatorVote = combineVotes(accumulatorVote, foldableVotes.get(i), priorityDecision, voterMetadata);
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
    public Vote accumulatorVoteFrom(Vote vote, VoterMetadata voterMetadata) {
        return new Vote(vote.authorizationDecision(), vote.errors(), vote.contributingAttributes(), List.of(vote),
                voterMetadata);
    }

    /**
     * Combines two votes according to priority-based rules.
     *
     * @param accumulatorVote the accumulated result so far
     * @param newVote the new vote to incorporate
     * @param priorityDecision the decision that wins on PERMIT vs DENY conflict
     * @param voterMetadata metadata for the combined result
     * @return the combined vote
     */
    public static Vote combineVotes(Vote accumulatorVote, Vote newVote, Decision priorityDecision,
            VoterMetadata voterMetadata) {
        val accumulatorAuthorizationDecision = accumulatorVote.authorizationDecision();
        val newAuthorizationDecision         = newVote.authorizationDecision();

        val accumulatorDecision = accumulatorAuthorizationDecision.decision();
        val decisionB           = newAuthorizationDecision.decision();

        AuthorizationDecision combinedAuthorizationDecision;
        var                   errors = List.<ErrorValue>of();

        // Error always wins and invalidates all others.
        // It makes no sense to have priority win over error, because you never know if
        // the erroring
        // policies did not have critical obligations. This is the secure approach. In
        // doubt fail.
        if (accumulatorDecision == Decision.INDETERMINATE) {
            // This should not even be called as if the accumulator is INDETERMINATE the
            // calling code should already
            // have short-circuited before getting here! Still lets make it robust!
            return accumulatorVote;
        } else if (decisionB == Decision.INDETERMINATE) {
            combinedAuthorizationDecision = AuthorizationDecision.INDETERMINATE;
            errors                        = newVote.errors();
        } else
        // Anything wins over NOT_APPLICABLE. No need to copy errors. INDETERMINATE took
        // care in the last few cases.
        if (accumulatorDecision == Decision.NOT_APPLICABLE) {
            combinedAuthorizationDecision = newAuthorizationDecision;
        } else if (decisionB == Decision.NOT_APPLICABLE) {
            combinedAuthorizationDecision = accumulatorAuthorizationDecision;
        } else
        // From here both are either PERMIT or DENY
        // Same vote - merge
        if (accumulatorDecision == decisionB) {
            // Transformation uncertainty detection
            val resourceA = accumulatorAuthorizationDecision.resource();
            val resourceB = newAuthorizationDecision.resource();
            if (!Value.UNDEFINED.equals(resourceA) && !Value.UNDEFINED.equals(resourceB)) {
                // Here the uncertainty actually happens
                combinedAuthorizationDecision = AuthorizationDecision.INDETERMINATE;
            } else {
                // now it is safe to merge the two decision's constraints
                combinedAuthorizationDecision = mergeAuthorizationDecisionsConstraints(accumulatorAuthorizationDecision,
                        newAuthorizationDecision);
            }
        } else
        // From here one is PERMIT and one is DENY
        // Thus one of both must be a priority decision which must win
        if (accumulatorDecision == priorityDecision) {
            combinedAuthorizationDecision = accumulatorAuthorizationDecision;
        } else {
            combinedAuthorizationDecision = newAuthorizationDecision;
        }
        val contributingVotes = appendToList(accumulatorVote.contributingVotes(), newVote);
        return new Vote(combinedAuthorizationDecision, errors, List.of(), contributingVotes, voterMetadata);
    }

    private static AuthorizationDecision mergeAuthorizationDecisionsConstraints(AuthorizationDecision authzDecisionA,
            AuthorizationDecision authzDecisionB) {
        // assumes transformation uncertainty has been eliminated before.
        val resourceA   = authzDecisionA.resource();
        val resourceB   = authzDecisionB.resource();
        val resource    = Value.UNDEFINED.equals(resourceA) ? resourceB : resourceA;
        val obligations = authzDecisionA.obligations().append(authzDecisionB.obligations());
        val advice      = authzDecisionA.advice().append(authzDecisionB.advice());
        return new AuthorizationDecision(authzDecisionA.decision(), obligations, advice, resource);
    }

    private static <T> List<T> appendToList(List<T> list, T newElement) {
        if (list.isEmpty())
            return List.of(newElement);
        val result = new ArrayList<T>(list.size() + 1);
        result.addAll(list);
        result.add(newElement);
        return result;
    }

}
