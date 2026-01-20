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
import io.sapl.ast.Outcome;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.pdp.Vote;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

/**
 * Combines multiple policy votes into a single authorization decision using
 * priority-based logic with extended indeterminate semantics.
 * <p>
 * Extended indeterminate tracking allows smarter error handling. Each
 * INDETERMINATE vote carries an {@link Outcome} indicating what the decision
 * "would have been" without the error:
 * <ul>
 * <li>{@code Outcome.PERMIT} - would have been PERMIT</li>
 * <li>{@code Outcome.DENY} - would have been DENY</li>
 * <li>{@code Outcome.PERMIT_OR_DENY} - could be either</li>
 * </ul>
 * <p>
 * <b>Key principle:</b> The priority decision (PERMIT for permit-overrides,
 * DENY for deny-overrides) wins over everything, including errors. An error
 * only matters if it could have produced the priority decision.
 * <p>
 * Transformation uncertainty: If multiple votes define resource
 * transformations, the result is INDETERMINATE since the correct transformation
 * cannot be determined.
 */
@UtilityClass
public class PriorityBasedVoteCombiner {

    /**
     * Combines multiple votes into a single vote using priority-based logic.
     * <p>
     * Short-circuits only on critical INDETERMINATE (nothing can save it).
     * Does NOT short-circuit on priority decision because we need to collect
     * constraints from all votes with the same decision.
     *
     * @param foldableVotes the votes to combine (may be empty)
     * @param priorityDecision the decision that wins when PERMIT conflicts with
     * DENY
     * @param voterMetadata metadata for the combined vote
     * @return combined vote, or abstain if input is empty
     */
    public static Vote combineMultipleVotes(List<Vote> foldableVotes, Decision priorityDecision,
            VoterMetadata voterMetadata) {
        if (foldableVotes.isEmpty()) {
            return Vote.abstain(voterMetadata);
        }
        var accumulatorVote = accumulatorVoteFrom(foldableVotes.getFirst(), voterMetadata);
        for (var i = 1; i < foldableVotes.size(); i++) {
            // Short-circuit only on critical INDETERMINATE - nothing can override it.
            // Do NOT short-circuit on priority: need to collect constraints from all
            // same-decision votes.
            if (accumulatorVote.authorizationDecision().decision() == Decision.INDETERMINATE
                    && isCritical(accumulatorVote.outcome(), priorityDecision)) {
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
     * The outcome is preserved from the wrapped vote for extended indeterminate
     * tracking.
     *
     * @param vote the vote to wrap
     * @param voterMetadata metadata for the accumulator
     * @return accumulator vote containing the original as a contributing vote
     */
    public Vote accumulatorVoteFrom(Vote vote, VoterMetadata voterMetadata) {
        return new Vote(vote.authorizationDecision(), vote.errors(), vote.contributingAttributes(), List.of(vote),
                voterMetadata, vote.outcome());
    }

    /**
     * Combines two votes according to priority-based rules with extended
     * indeterminate tracking.
     * <p>
     * The accumulator may be NOT_APPLICABLE when starting from an empty
     * foldable votes list. In this case, the new vote replaces the accumulator
     * while preserving contributing votes.
     * <p>
     * Decision table (assuming permit-overrides; swap P/D for deny-overrides):
     *
     * <pre>
     * Accumulator              | New Vote             | Result          | Outcome
     * -------------------------|----------------------|-----------------|------------------
     * DENY                     | PERMIT               | PERMIT          | PERMIT
     * DENY                     | DENY                 | DENY (merged)   | DENY
     * DENY                     | INDET(PERMIT)        | INDETERMINATE   | PERMIT_OR_DENY
     * DENY                     | INDET(DENY)          | DENY            | DENY
     * DENY                     | INDET(PERMIT_OR_DENY)| INDETERMINATE   | PERMIT_OR_DENY
     * INDET(DENY)              | PERMIT               | PERMIT          | PERMIT
     * INDET(PERMIT)            | PERMIT               | INDETERMINATE   | PERMIT
     * INDET(PERMIT_OR_DENY)    | PERMIT               | INDETERMINATE   | PERMIT_OR_DENY
     * INDET(DENY)              | DENY                 | DENY            | DENY
     * INDET(PERMIT)            | DENY                 | INDETERMINATE   | PERMIT_OR_DENY
     * INDET(PERMIT_OR_DENY)    | DENY                 | INDETERMINATE   | PERMIT_OR_DENY
     * INDET(any)               | INDET(any)           | INDETERMINATE   | combined
     * </pre>
     * <p>
     * An error only blocks the priority decision if the error's
     * outcome includes the priority decision. An INDET(DENY) cannot block
     * permit-overrides because even without the error it would have been DENY.
     *
     * @param accumulatorVote the accumulated result (may be NOT_APPLICABLE if
     * starting empty)
     * @param newVote the new vote to incorporate
     * @param priorityDecision the decision that wins on PERMIT vs DENY conflict
     * @param voterMetadata metadata for the combined result
     * @return the combined vote
     */
    public static Vote combineVotes(Vote accumulatorVote, Vote newVote, Decision priorityDecision,
            VoterMetadata voterMetadata) {
        val accAuthz = accumulatorVote.authorizationDecision();
        val newAuthz = newVote.authorizationDecision();
        val accDec   = accAuthz.decision();
        val newDec   = newAuthz.decision();

        val contributingVotes = appendToList(accumulatorVote.contributingVotes(), newVote);

        // NOT_APPLICABLE accumulator: new vote replaces decision but keeps contributing
        // votes
        if (accDec == Decision.NOT_APPLICABLE) {
            return concreteResult(newAuthz, newVote.outcome(), contributingVotes, voterMetadata);
        }

        // Priority decision in new vote always wins
        if (newDec == priorityDecision) {
            // Same decision - check transformation uncertainty and merge
            if (accDec == priorityDecision) {
                return handleSameDecision(accAuthz, newAuthz, contributingVotes, accumulatorVote, newVote,
                        voterMetadata);
            }
            // New priority wins, unless accumulated error is critical
            if (accDec == Decision.INDETERMINATE && isCritical(accumulatorVote.outcome(), priorityDecision)) {
                return indeterminateResult(combineOutcomes(accumulatorVote.outcome(), newVote.outcome()),
                        accumulatorVote.errors(), contributingVotes, voterMetadata);
            }
            // Priority wins over non-priority or non-critical error
            return concreteResult(newAuthz, newVote.outcome(), contributingVotes, voterMetadata);
        }

        // New vote is INDETERMINATE
        if (newDec == Decision.INDETERMINATE) {
            // Non-critical error cannot change outcome - concrete decision wins
            if (!isCritical(newVote.outcome(), priorityDecision)) {
                if (accDec == priorityDecision) {
                    // Accumulated priority wins over non-critical error
                    return concreteResult(accAuthz, accumulatorVote.outcome(), contributingVotes, voterMetadata);
                }
                if (accDec != Decision.INDETERMINATE) {
                    // Accumulated non-priority wins - error would have been same or opposite
                    return concreteResult(accAuthz, combineOutcomes(accumulatorVote.outcome(), newVote.outcome()),
                            contributingVotes, voterMetadata);
                }
            }
            // Critical error or accumulated INDETERMINATE - error dominates
            return indeterminateResult(combineOutcomes(accumulatorVote.outcome(), newVote.outcome()), newVote.errors(),
                    contributingVotes, voterMetadata);
        }

        // New vote is non-priority (PERMIT or DENY)

        // Accumulated INDETERMINATE: check if error is critical
        if (accDec == Decision.INDETERMINATE) {
            if (!isCritical(accumulatorVote.outcome(), priorityDecision)) {
                // Non-critical error loses to concrete non-priority decision
                return concreteResult(newAuthz, combineOutcomes(accumulatorVote.outcome(), newVote.outcome()),
                        contributingVotes, voterMetadata);
            }
            // Critical error blocks - stays INDETERMINATE
            return indeterminateResult(combineOutcomes(accumulatorVote.outcome(), newVote.outcome()),
                    accumulatorVote.errors(), contributingVotes, voterMetadata);
        }

        // Both are concrete PERMIT/DENY - must be same decision
        // (different decisions would mean one is priority, handled above)
        return handleSameDecision(accAuthz, newAuthz, contributingVotes, accumulatorVote, newVote, voterMetadata);
    }

    private static Vote handleSameDecision(AuthorizationDecision accAuthz, AuthorizationDecision newAuthz,
            List<Vote> contributingVotes, Vote accVote, Vote newVote, VoterMetadata voterMetadata) {
        val resourceA = accAuthz.resource();
        val resourceB = newAuthz.resource();
        // Transformation uncertainty: both define different resources
        if (!Value.UNDEFINED.equals(resourceA) && !Value.UNDEFINED.equals(resourceB)) {
            return indeterminateResult(combineOutcomes(accVote.outcome(), newVote.outcome()), List.of(),
                    contributingVotes, voterMetadata);
        }
        val merged = mergeAuthorizationDecisionsConstraints(accAuthz, newAuthz);
        return concreteResult(merged, combineOutcomes(accVote.outcome(), newVote.outcome()), contributingVotes,
                voterMetadata);
    }

    private static Vote concreteResult(AuthorizationDecision authz, Outcome outcome, List<Vote> contributingVotes,
            VoterMetadata voterMetadata) {
        return new Vote(authz, List.of(), List.of(), contributingVotes, voterMetadata, outcome);
    }

    private static Vote indeterminateResult(Outcome outcome, List<ErrorValue> errors, List<Vote> contributingVotes,
            VoterMetadata voterMetadata) {
        return new Vote(AuthorizationDecision.INDETERMINATE, errors, List.of(), contributingVotes, voterMetadata,
                outcome);
    }

    private static boolean isCritical(Outcome outcome, Decision priorityDecision) {
        if (outcome == null)
            return true; // Unknown = assume critical (safe default)
        return switch (outcome) {
        case PERMIT         -> priorityDecision == Decision.PERMIT;
        case DENY           -> priorityDecision == Decision.DENY;
        case PERMIT_OR_DENY -> true; // Always critical
        };
    }

    private static Outcome combineOutcomes(Outcome a, Outcome b) {
        if (a == null)
            return b;
        if (b == null)
            return a;
        if (a == b)
            return a;
        return Outcome.PERMIT_OR_DENY;
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
