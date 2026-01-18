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

import io.sapl.api.model.BooleanValue;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.Outcome;
import lombok.val;

import java.util.Arrays;
import java.util.List;

/**
 * Utility to print relevance tables for priority-based bucket evaluation.
 * Run main() to see which buckets can be skipped based on accumulated decision.
 */
public class PriorityExperimentTests {

    enum Relevance {
        RELEVANT,
        IRRELEVANT
    }

    public static void main(String[] args) {
        val permitDeny = List.of(Decision.PERMIT, Decision.DENY);
        val trueFalse  = List.of(BooleanValue.TRUE, BooleanValue.FALSE);
        for (val priority : permitDeny) {
            for (val decision : permitDeny) {
                for (val hasConstraints : trueFalse) {
                    val table = relevancesFor(decision, hasConstraints.value(), priority);
                    printRelevanceTable(decision, hasConstraints.value(), priority, table);
                }
            }
        }
    }

    private static void printRelevanceTable(Decision decision, boolean hasConstraints, Decision priority,
            Relevance[][] relevances) {
        val header = String.format("Decision: %s, hasConstraints: %s, priority: %s", decision, hasConstraints,
                priority);
        System.out.println(header);
        System.out.println("| outcome ╲ hasConstraint | false| true  |");
        System.out.println("|-------------------------|------|-------|");

        for (Outcome outcome : Outcome.values()) {
            val row = String.format("| %-23s | %-4s | %-5s |", outcome,
                    formatRelevance(relevances[outcome.ordinal()][0]),
                    formatRelevance(relevances[outcome.ordinal()][1]));
            System.out.println(row);
        }
        System.out.println();
    }

    private static String formatRelevance(Relevance relevance) {
        return relevance == Relevance.RELEVANT ? "✓" : "✗";
    }

    private static Relevance[][] relevancesFor(Decision decision, boolean hasConstraints, Decision priority) {
        Relevance[][] relevances = new Relevance[3][2];

        // First assume all relevant
        for (Relevance[] relevance : relevances) {
            Arrays.fill(relevance, Relevance.RELEVANT);
        }

        // now cross out classifications that we no longer need to look at

        // If the current decision has constraints => no need to look at the same
        // decisions without constraints
        // => if encountered during same outcome classification short circuit
        if (hasConstraints) {
            if (decision == Decision.PERMIT) {
                relevances[Outcome.PERMIT.ordinal()][0] = Relevance.IRRELEVANT;
            } else {
                relevances[Outcome.DENY.ordinal()][0] = Relevance.IRRELEVANT;
            }
        }
        // decision is priority => only outcome = priorities or P_or_D can have an
        // impact => eliminate non prio
        if (decision == priority) {
            if (priority == Decision.PERMIT) {
                val x = Outcome.DENY.ordinal();
                relevances[x][0] = Relevance.IRRELEVANT;
                relevances[x][1] = Relevance.IRRELEVANT;
            } else {
                val x = Outcome.PERMIT.ordinal();
                relevances[x][0] = Relevance.IRRELEVANT;
                relevances[x][1] = Relevance.IRRELEVANT;

            }
        } else {
            // Decision is not prio => the only thing we can exclude is non-constraint
            // classification with same decision.
            // happened in the 1st step above
        }
        return relevances;
    }
}
