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
package io.sapl.util;

import io.sapl.api.pdp.Decision;
import io.sapl.ast.Outcome;
import lombok.val;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility to print relevance tables for priority-based bucket evaluation with
 * extended indeterminate semantics.
 * <p>
 * Models the actual behavior of {@code PriorityBasedVoteCombiner}:
 * <ul>
 * <li>Accumulated state includes Decision AND Outcome (for INDETERMINATE)</li>
 * <li>Critical errors (outcome includes priority) cause short-circuit</li>
 * <li>Non-critical errors can be overridden by ANY concrete decision</li>
 * <li>Priority decisions do NOT short-circuit (need to collect
 * constraints)</li>
 * </ul>
 */
public class PriorityExperiment {

    record AccumulatedState(Decision decision, Outcome outcome, boolean hasConstraints) {
        @Override
        public @NonNull String toString() {
            if (decision == Decision.INDETERMINATE) {
                return "INDET(" + outcome + ")" + (hasConstraints ? "+C" : "");
            }
            return decision + (hasConstraints ? "+C" : "");
        }
    }

    record Bucket(Outcome outcome, boolean hasConstraints) {
        @Override
        public @NonNull String toString() {
            return outcome + (hasConstraints ? "+C" : "");
        }
    }

    enum Relevance {
        RELEVANT,
        IRRELEVANT,
        SHORT_CIRCUIT
    }

    public static void main(String[] args) {
        for (val priority : List.of(Decision.PERMIT, Decision.DENY)) {
            System.out.println("=".repeat(80));
            System.out.println("PRIORITY: " + priority + " ("
                    + (priority == Decision.PERMIT ? "permit-overrides" : "deny-overrides") + ")");
            System.out.println("=".repeat(80));
            System.out.println();

            val states = buildAccumulatedStates();
            for (val state : states) {
                printRelevanceTable(state, priority);
            }
        }
    }

    private static List<AccumulatedState> buildAccumulatedStates() {
        val states = new ArrayList<AccumulatedState>();

        // NOT_APPLICABLE (no prior votes)
        states.add(new AccumulatedState(Decision.NOT_APPLICABLE, null, false));

        // Concrete decisions
        for (val decision : List.of(Decision.PERMIT, Decision.DENY)) {
            states.add(
                    new AccumulatedState(decision, decision == Decision.PERMIT ? Outcome.PERMIT : Outcome.DENY, false));
            states.add(
                    new AccumulatedState(decision, decision == Decision.PERMIT ? Outcome.PERMIT : Outcome.DENY, true));
        }

        // INDETERMINATE with different outcomes
        for (val outcome : Outcome.values()) {
            states.add(new AccumulatedState(Decision.INDETERMINATE, outcome, false));
        }

        return states;
    }

    private static void printRelevanceTable(AccumulatedState state, Decision priority) {
        System.out.println("Accumulated: " + state);

        // Check for short-circuit first
        if (state.decision() == Decision.INDETERMINATE && isCritical(state.outcome(), priority)) {
            System.out.println("  => SHORT-CIRCUIT: Critical error, all buckets irrelevant");
            System.out.println();
            return;
        }

        System.out.println("  | Bucket Outcome  | w/o Constraints | w/ Constraints |");
        System.out.println("  |-----------------|-----------------|----------------|");

        for (val outcome : Outcome.values()) {
            val withoutConstraints = computeRelevance(state, new Bucket(outcome, false), priority);
            val withConstraints    = computeRelevance(state, new Bucket(outcome, true), priority);

            System.out.printf("  | %-15s | %-15s | %-14s |%n", outcome, formatRelevance(withoutConstraints),
                    formatRelevance(withConstraints));
        }
        System.out.println();
    }

    private static Relevance computeRelevance(AccumulatedState acc, Bucket bucket, Decision priority) {
        val accDec      = acc.decision();
        val bucketDec   = outcomeToDecision(bucket.outcome());
        val nonPriority = priority == Decision.PERMIT ? Decision.DENY : Decision.PERMIT;

        // NOT_APPLICABLE: everything is relevant
        if (accDec == Decision.NOT_APPLICABLE) {
            return Relevance.RELEVANT;
        }

        // INDETERMINATE accumulated
        if (accDec == Decision.INDETERMINATE) {
            // Critical already handled by short-circuit check
            // Non-critical: any concrete can override, all relevant
            return Relevance.RELEVANT;
        }

        // Accumulated is priority decision
        if (accDec == priority) {
            // Same priority bucket: only relevant if it has constraints we don't have
            if (bucketDec == priority) {
                if (bucket.hasConstraints() && !acc.hasConstraints()) {
                    return Relevance.RELEVANT;
                }
                if (bucket.hasConstraints()) {
                    return Relevance.RELEVANT; // More constraints to merge
                }
                return Relevance.IRRELEVANT; // No new constraints to add
            }
            // Non-priority bucket: irrelevant (priority already won)
            if (bucketDec == nonPriority) {
                return Relevance.IRRELEVANT;
            }
            // PERMIT_OR_DENY bucket: could produce critical error
            return Relevance.RELEVANT;
        }

        // Accumulated is non-priority decision
        if (accDec == nonPriority) {
            // Priority bucket: always relevant (can override)
            if (bucketDec == priority) {
                return Relevance.RELEVANT;
            }
            // Same non-priority bucket: only relevant for constraints
            if (bucketDec == nonPriority) {
                if (bucket.hasConstraints() && !acc.hasConstraints()) {
                    return Relevance.RELEVANT;
                }
                if (bucket.hasConstraints()) {
                    return Relevance.RELEVANT; // More constraints to merge
                }
                return Relevance.IRRELEVANT;
            }
            // PERMIT_OR_DENY bucket: could produce priority or critical error
            return Relevance.RELEVANT;
        }

        return Relevance.RELEVANT; // Default: assume relevant
    }

    private static Decision outcomeToDecision(Outcome outcome) {
        return switch (outcome) {
        case PERMIT         -> Decision.PERMIT;
        case DENY           -> Decision.DENY;
        case PERMIT_OR_DENY -> null; // Mixed, not a single decision
        };
    }

    private static boolean isCritical(Outcome outcome, Decision priority) {
        if (outcome == null) {
            return true; // Unknown = assume critical
        }
        return switch (outcome) {
        case PERMIT         -> priority == Decision.PERMIT;
        case DENY           -> priority == Decision.DENY;
        case PERMIT_OR_DENY -> true; // Always critical
        };
    }

    private static String formatRelevance(Relevance relevance) {
        return switch (relevance) {
        case RELEVANT      -> "RELEVANT";
        case IRRELEVANT    -> "-";
        case SHORT_CIRCUIT -> "SHORT-CIRCUIT";
        };
    }
}
