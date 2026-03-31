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
package io.sapl.compiler.index.canonical;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import io.sapl.compiler.index.ConjunctiveClause;
import io.sapl.api.model.IndexPredicate;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Orders predicates by evaluation priority using the heuristic from Definition
 * 3.12 of the SACMAT '21 paper.
 * <p>
 * Higher priority predicates are evaluated first. The heuristic balances:
 * <ul>
 * <li>funCount: number of formulas referencing the predicate</li>
 * <li>splitC: ratio of positive to negative occurrences (balanced is
 * better)</li>
 * <li>contrib: average contribution to conjunction fulfillment</li>
 * <li>cost: evaluation cost (currently fixed at 1.0)</li>
 * </ul>
 * <p>
 * {@code priority(p) = funCount(p)^2 * (2 - splitC(p)^2) * contrib(p)^(2-contrib(p)) / cost(p)}
 */
@UtilityClass
class PredicateOrderStrategy {

    private static final double DEFAULT_COST = 1.0;

    /**
     * Orders predicates by descending evaluation priority.
     *
     * @param predicates all unique predicates
     * @param predicateToIndex predicate to index mapping
     * @param conjunctionToIndex conjunction to index mapping
     * @param falseForTruePredicate per-predicate bitmask (Def 3.3)
     * @param falseForFalsePredicate per-predicate bitmask (Def 3.3)
     * @param conjunctionsWithPredicate per-predicate bitmask (Def 3.6)
     * @param relatedFormulas per-predicate formula sets (Def 3.7)
     * @return predicates ordered by descending priority
     */
    static List<IndexPredicate> order(List<IndexPredicate> predicates, Map<IndexPredicate, Integer> predicateToIndex,
            Map<ConjunctiveClause, Integer> conjunctionToIndex, BitSet[] falseForTruePredicate,
            BitSet[] falseForFalsePredicate, BitSet[] conjunctionsWithPredicate, int[][] relatedFormulas) {
        val ordered = new ArrayList<>(predicates);
        ordered.sort(Comparator
                .comparingDouble((IndexPredicate p) -> priority(p, predicateToIndex, conjunctionToIndex,
                        falseForTruePredicate, falseForFalsePredicate, conjunctionsWithPredicate, relatedFormulas))
                .reversed());
        return List.copyOf(ordered);
    }

    private static double priority(IndexPredicate predicate, Map<IndexPredicate, Integer> predicateToIndex,
            Map<ConjunctiveClause, Integer> conjunctionToIndex, BitSet[] falseForTruePredicate,
            BitSet[] falseForFalsePredicate, BitSet[] conjunctionsWithPredicate, int[][] relatedFormulas) {
        val p = predicateToIndex.get(predicate);

        val funCount = relatedFormulas[p].length;
        if (funCount == 0) {
            return 0.0;
        }

        val splitC  = splitC(p, falseForTruePredicate, falseForFalsePredicate);
        val contrib = contrib(p, conjunctionsWithPredicate, conjunctionToIndex);

        val funCountSq  = (double) funCount * funCount;
        val splitPart   = 2.0 - splitC * splitC;
        val contribPart = Math.pow(contrib, 2.0 - contrib);

        return funCountSq * splitPart * contribPart / DEFAULT_COST;
    }

    private static double splitC(int p, BitSet[] falseForTruePredicate, BitSet[] falseForFalsePredicate) {
        val positive = falseForFalsePredicate[p].cardinality();
        val negative = falseForTruePredicate[p].cardinality();
        val total    = positive + negative;
        if (total == 0) {
            return 0.0;
        }
        return (double) (positive - negative) / total;
    }

    private static double contrib(int p, BitSet[] conjunctionsWithPredicate,
            Map<ConjunctiveClause, Integer> conjunctionToIndex) {
        val conjunctions = conjunctionsWithPredicate[p];
        if (conjunctions.isEmpty()) {
            return 0.0;
        }
        var totalContribution = 0.0;
        var count             = 0;
        for (val entry : conjunctionToIndex.entrySet()) {
            if (conjunctions.get(entry.getValue())) {
                val clauseSize = entry.getKey().size();
                if (clauseSize > 0) {
                    totalContribution += 1.0 / clauseSize;
                }
                count++;
            }
        }
        return count > 0 ? totalContribution / count : 0.0;
    }

}
