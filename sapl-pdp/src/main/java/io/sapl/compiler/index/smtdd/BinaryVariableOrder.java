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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.sapl.api.model.IndexPredicate;
import lombok.val;

/**
 * Variable order for the binary (non-equality) predicates in the SMTDD.
 * Predicates are sorted by formula frequency descending.
 */
class BinaryVariableOrder {

    private final List<IndexPredicate>         predicates;
    private final Map<IndexPredicate, Integer> predicateToLevel;
    private final BitSet[]                     erroredFormulasPerPredicate;

    /**
     * Creates a variable order from the remaining (non-equality) predicates.
     * Predicates are sorted by descending formula frequency so that
     * high-discrimination predicates appear near the root, reducing
     * average traversal depth. Ties are broken by semantic hash for
     * deterministic ordering.
     *
     * @param remainingPredicates predicates not handled by equality groups
     * @param formulasPerPredicate formula indices referencing each predicate
     */
    BinaryVariableOrder(List<IndexPredicate> remainingPredicates,
            Map<IndexPredicate, List<Integer>> formulasPerPredicate) {
        val sorted = new ArrayList<>(remainingPredicates);
        sorted.sort(
                Comparator.comparingInt((IndexPredicate p) -> -formulasPerPredicate.getOrDefault(p, List.of()).size())
                        .thenComparingLong(IndexPredicate::semanticHash));

        this.predicates                  = sorted;
        this.predicateToLevel            = new HashMap<>(sorted.size());
        this.erroredFormulasPerPredicate = new BitSet[sorted.size()];

        for (var level = 0; level < sorted.size(); level++) {
            val predicate = sorted.get(level);
            predicateToLevel.put(predicate, level);
            val formulas = formulasPerPredicate.getOrDefault(predicate, List.of());
            val bits     = new BitSet();
            for (val formulaIndex : formulas) {
                bits.set(formulaIndex);
            }
            erroredFormulasPerPredicate[level] = bits;
        }
    }

    /**
     * Returns the level for the given predicate, or -1 if not in this order
     * (meaning it's handled by an equality branch).
     */
    int levelOf(IndexPredicate predicate) {
        val level = predicateToLevel.get(predicate);
        return level != null ? level : -1;
    }

    IndexPredicate predicateAt(int level) {
        return predicates.get(level);
    }

    BitSet erroredFormulas(int level) {
        return erroredFormulasPerPredicate[level];
    }

    int size() {
        return predicates.size();
    }

}
