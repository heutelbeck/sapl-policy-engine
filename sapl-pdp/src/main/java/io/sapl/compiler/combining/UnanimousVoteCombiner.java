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
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.Outcome;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.pdp.Vote;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.List;

import static io.sapl.compiler.combining.CombiningUtils.*;

/**
 * Combines multiple policy votes using unanimous semantics.
 * <p>
 * The unanimous combining algorithm requires all applicable policies to agree:
 * <ul>
 * <li><b>Zero applicable:</b> Returns NOT_APPLICABLE</li>
 * <li><b>All agree:</b> Returns merged decision with combined constraints</li>
 * <li><b>Disagreement:</b> Returns INDETERMINATE(PERMIT_OR_DENY)</li>
 * </ul>
 * <p>
 * Supports two modes:
 * <ul>
 * <li><b>Normal mode:</b> Agreement on entitlement (PERMIT/DENY), constraints
 * merged</li>
 * <li><b>Strict mode:</b> Exact equality required (decision + all
 * constraints)</li>
 * </ul>
 */
@UtilityClass
public class UnanimousVoteCombiner {

    private static final String ERROR_ENTITLEMENT_DISAGREEMENT   = "Policies disagree on entitlement during unanimous combining.";
    private static final String ERROR_NOT_IDENTICAL              = "Policies are not identical during strict unanimous combining.";
    private static final String ERROR_TRANSFORMATION_UNCERTAINTY = "Transformation uncertainty: multiple policies define different resource transformations.";

    /**
     * Combines multiple votes into a single vote using unanimous semantics.
     * <p>
     * Short-circuits on disagreement since unanimity cannot be achieved.
     *
     * @param votes the votes to combine (may be empty)
     * @param voterMetadata metadata for the combined vote
     * @param strictMode if true, requires exact equality; if false, only
     * entitlement agreement
     * @return combined vote, or abstain if input is empty
     */
    public static Vote combineMultipleVotes(List<Vote> votes, VoterMetadata voterMetadata, boolean strictMode) {
        if (votes.isEmpty()) {
            return Vote.abstain(voterMetadata);
        }
        var accumulatorVote = accumulatorVoteFrom(votes.getFirst(), voterMetadata);
        for (val vote : votes.subList(1, votes.size())) {
            if (isTerminal(accumulatorVote, strictMode)) {
                break;
            }
            accumulatorVote = combineVotes(accumulatorVote, vote, voterMetadata, strictMode);
        }
        return accumulatorVote;
    }

    /**
     * Combines votes into an existing accumulator using unanimous semantics.
     * <p>
     * Unlike {@link #combineMultipleVotes(List, VoterMetadata, boolean)}, this
     * method treats the accumulator as a pre-existing collection state. Votes are
     * folded INTO the accumulator without the accumulator itself becoming a
     * contribution.
     *
     * @param accumulator the existing accumulator to fold into
     * @param votes the votes to combine into the accumulator
     * @param voterMetadata metadata for the combined vote
     * @param strictMode if true, requires exact equality; if false, only
     * entitlement agreement
     * @return combined vote
     */
    public static Vote combineMultipleVotes(Vote accumulator, List<Vote> votes, VoterMetadata voterMetadata,
            boolean strictMode) {
        var result = accumulator;
        for (Vote vote : votes) {
            if (isTerminal(result, strictMode)) {
                break;
            }
            result = combineVotes(result, vote, voterMetadata, strictMode);
        }
        return result;
    }

    /**
     * Wraps a single vote as an accumulator for fold operations.
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
     * Combines two votes according to unanimous semantics.
     * <p>
     * Decision table for normal mode:
     *
     * <pre>
     * Accumulator      | New Vote        | Result
     * -----------------|-----------------|------------------
     * NOT_APPLICABLE   | NOT_APPLICABLE  | NOT_APPLICABLE
     * NOT_APPLICABLE   | PERMIT/DENY     | new vote
     * PERMIT           | PERMIT          | PERMIT (merged)
     * PERMIT           | DENY            | INDETERMINATE
     * DENY             | PERMIT          | INDETERMINATE
     * DENY             | DENY            | DENY (merged)
     * INDETERMINATE    | any             | INDETERMINATE
     * any              | INDETERMINATE   | INDETERMINATE
     * </pre>
     *
     * @param accumulatorVote the accumulated result
     * @param newVote the new vote to incorporate
     * @param voterMetadata metadata for the combined result
     * @param strictMode if true, requires exact equality
     * @return the combined vote
     */
    public static Vote combineVotes(Vote accumulatorVote, Vote newVote, VoterMetadata voterMetadata,
            boolean strictMode) {
        val accDec = accumulatorVote.authorizationDecision().decision();
        val newDec = newVote.authorizationDecision().decision();

        val contributingVotes = appendToList(accumulatorVote.contributingVotes(), newVote);

        // INDETERMINATE propagates
        if (accDec == Decision.INDETERMINATE || newDec == Decision.INDETERMINATE) {
            val source = accDec == Decision.INDETERMINATE ? accumulatorVote : newVote;
            return indeterminateResult(combineOutcomes(accumulatorVote.outcome(), newVote.outcome()), source.errors(),
                    contributingVotes, voterMetadata);
        }

        // Both NOT_APPLICABLE
        if (accDec == Decision.NOT_APPLICABLE && newDec == Decision.NOT_APPLICABLE) {
            return Vote.abstain(voterMetadata, contributingVotes);
        }

        // One NOT_APPLICABLE - take the other
        if (accDec == Decision.NOT_APPLICABLE) {
            return Vote.combinedVote(newVote.authorizationDecision(), voterMetadata, contributingVotes,
                    newVote.outcome());
        }
        if (newDec == Decision.NOT_APPLICABLE) {
            return Vote.combinedVote(accumulatorVote.authorizationDecision(), voterMetadata, contributingVotes,
                    accumulatorVote.outcome());
        }

        // Both are concrete PERMIT or DENY
        if (strictMode) {
            return combineStrictMode(accumulatorVote, newVote, voterMetadata, contributingVotes);
        }
        return combineNormalMode(accumulatorVote, newVote, voterMetadata, contributingVotes);
    }

    private static Vote combineNormalMode(Vote accumulatorVote, Vote newVote, VoterMetadata voterMetadata,
            List<Vote> contributingVotes) {
        val accAuthz = accumulatorVote.authorizationDecision();
        val newAuthz = newVote.authorizationDecision();
        val accDec   = accAuthz.decision();
        val newDec   = newAuthz.decision();

        // Disagreement on entitlement
        if (accDec != newDec) {
            val error = Value.error(ERROR_ENTITLEMENT_DISAGREEMENT);
            return indeterminateResult(Outcome.PERMIT_OR_DENY, List.of(error), contributingVotes, voterMetadata);
        }

        // Same entitlement - check transformation uncertainty
        val resourceA = accAuthz.resource();
        val resourceB = newAuthz.resource();
        val outcome   = decisionToOutcome(accDec);
        if (!Value.UNDEFINED.equals(resourceA) && !Value.UNDEFINED.equals(resourceB) && !resourceA.equals(resourceB)) {
            val error = Value.error(ERROR_TRANSFORMATION_UNCERTAINTY);
            return indeterminateResult(outcome, List.of(error), contributingVotes, voterMetadata);
        }

        // Merge constraints
        val merged = mergeConstraints(accAuthz, newAuthz);
        return Vote.combinedVote(merged, voterMetadata, contributingVotes, outcome);
    }

    private static Vote combineStrictMode(Vote accumulatorVote, Vote newVote, VoterMetadata voterMetadata,
            List<Vote> contributingVotes) {
        val accAuthz = accumulatorVote.authorizationDecision();
        val newAuthz = newVote.authorizationDecision();

        if (areIdentical(accAuthz, newAuthz)) {
            return Vote.combinedVote(accAuthz, voterMetadata, contributingVotes,
                    decisionToOutcome(accAuthz.decision()));
        }

        // Not identical
        val error   = Value.error(ERROR_NOT_IDENTICAL);
        val outcome = accAuthz.decision() == newAuthz.decision() ? decisionToOutcome(accAuthz.decision())
                : Outcome.PERMIT_OR_DENY;
        return indeterminateResult(outcome, List.of(error), contributingVotes, voterMetadata);
    }

    private static boolean areIdentical(AuthorizationDecision a, AuthorizationDecision b) {
        return a.decision() == b.decision() && a.obligations().equals(b.obligations()) && a.advice().equals(b.advice())
                && a.resource().equals(b.resource());
    }

    static boolean isTerminal(Vote vote, boolean strictMode) {
        if (vote.authorizationDecision().decision() != Decision.INDETERMINATE) {
            return false;
        }
        // In strict mode, any INDETERMINATE is terminal
        if (strictMode) {
            return true;
        }
        // In normal mode, only PERMIT_OR_DENY outcome is terminal (disagreement)
        return vote.outcome() == Outcome.PERMIT_OR_DENY;
    }

}
