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
package io.sapl.compiler.index.smtdd;

import com.heutelbeck.rustregex4j.RegexLimits;
import com.heutelbeck.rustregex4j.RegexSet;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.IndexPredicate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Groups the predicates that match one shared operand against constant
 * regular expressions with the operator {@code =~}. One search with the set
 * of all patterns of the group replaces one search per predicate.
 * <p>
 * Other than the values of an {@link EqualityGroup}, several patterns may
 * match the same text, so the group does not select one branch but the union
 * of the branches of all matching patterns. A formula that demands several
 * patterns of the group is routed to the branch of the first of them and
 * holds only if all of them match.
 */
@Getter
@RequiredArgsConstructor
class RegexGroup {

    private final PureOperator sharedOperand;
    private final RegexLimits  limits;

    private final List<String>         patterns           = new ArrayList<>();
    private final Map<String, Integer> patternIndexes     = new HashMap<>();
    private final Map<Integer, BitSet> patternsPerFormula = new HashMap<>();
    private final Set<IndexPredicate>  predicates         = new HashSet<>();
    private final BitSet               affectedFormulas   = new BitSet();
    private RegexSet                   set;

    void add(String pattern, int formulaIndex, IndexPredicate predicate) {
        predicates.add(predicate);
        affectedFormulas.set(formulaIndex);
        val patternIndex = patternIndexes.computeIfAbsent(pattern, added -> {
            patterns.add(added);
            return patterns.size() - 1;
        });
        patternsPerFormula.computeIfAbsent(formulaIndex, ignored -> new BitSet()).set(patternIndex);
    }

    int patternCount() {
        return patterns.size();
    }

    /**
     * Compiles the set of all patterns of the group.
     *
     * @return {@code false} if the engine refuses the set, for example because
     * it is too large as a whole; the predicates then stay single predicates
     */
    boolean compileSet() {
        if (RegexSet.compileWholeText(patterns, limits) instanceof RegexSet compiled) {
            set = compiled;
            return true;
        }
        return false;
    }

    /**
     * @param formulasInBucket the formulas to route
     * @return for each pattern the formulas of the bucket whose first demanded
     * pattern it is
     */
    List<BitSet> route(BitSet formulasInBucket) {
        val routed = new ArrayList<BitSet>(patterns.size());
        for (var index = 0; index < patterns.size(); index++) {
            routed.add(new BitSet());
        }
        for (var formula = formulasInBucket.nextSetBit(0); formula >= 0; formula = formulasInBucket
                .nextSetBit(formula + 1)) {
            val demanded = patternsPerFormula.get(formula);
            if (demanded != null) {
                routed.get(demanded.nextSetBit(0)).set(formula);
            }
        }
        return routed;
    }

    /**
     * @param formulasInBucket the formulas of a bucket
     * @return the formulas of the bucket that demand more than one pattern, with
     * the patterns each of them demands
     */
    Map<Integer, BitSet> conjunctions(BitSet formulasInBucket) {
        val conjunctions = new HashMap<Integer, BitSet>();
        patternsPerFormula.forEach((formula, demanded) -> {
            if (demanded.cardinality() > 1 && formulasInBucket.get(formula)) {
                conjunctions.put(formula, demanded);
            }
        });
        return conjunctions;
    }
}
